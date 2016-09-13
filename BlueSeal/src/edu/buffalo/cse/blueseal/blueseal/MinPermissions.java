package edu.buffalo.cse.blueseal.blueseal;

import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import soot.Body;
import soot.PatchingChain;
import soot.Scene;
import soot.SootMethod;
import soot.Unit;
import soot.Value;
import soot.ValueBox;


public class MinPermissions
{
	private PermMapLoader loader;
	private InfoExtract infoEx;
	private Set<String> declaredPerms_;
	private Set<String> intentPerms_;
	private Set<String> APIPerms_;
	private Set<String> cpPerms_;
	private Set<String> extraPerms;
	
	public MinPermissions(String apkLoc, Set<SootMethod> reachableMethods,
			Set<String> reachableMethSigs)
	{
		this.loader = new PermMapLoader();
		this.infoEx = new InfoExtract(apkLoc);
		this.declaredPerms_ = new HashSet<String>();
		this.intentPerms_ = new HashSet<String>();
		this.APIPerms_ = new HashSet<String>();
		this.cpPerms_ = new HashSet<String>();
		this.extraPerms = new HashSet<String>();
		findExtraPerms(reachableMethods, reachableMethSigs);
	}
	
	private void findExtraPerms(Set<SootMethod> reachableMethods, Set<String> reachableMethSigs) {
		// TODO Auto-generated method stub
		//first get all required perms that blueseal thinks necessary
		APIPerms_ = getAPIPerms(reachableMethSigs);
		intentPerms_ = getIntentPerms();
		cpPerms_ = getContentProviderPerms(reachableMethods);
		
		//now get the permissions declared in manifest
		declaredPerms_ = infoEx.getDeclaredPermissionsInManifest();
		
		extraPerms = declaredPerms_;
		extraPerms.removeAll(APIPerms_);
		extraPerms.removeAll(intentPerms_);
		extraPerms.removeAll(cpPerms_);	
	}
	
	public Set<String> getExtraPerms(){
		return this.extraPerms;
	}
	/*
	 * get all required API permissions blueseal thinks necessary
	 * @para all reachable methods 
	 * @return set of necessary permissions
	 */
	private Set<String> getAPIPerms(Set<String> reachableMethSigs) {
		// TODO Auto-generated method stub
		Map<String, Set<String>> APIPermMap =
				loader.getAPIPermMap();
		Set<String> calls = fixSigFormat(reachableMethSigs);
		
		Set<Set<String>> permsUsed = new HashSet<Set<String>>();
		for (String call : calls)
		{
			Set<String> thisCallsPerms = new HashSet<String>();
			if (!APIPermMap.containsKey(call)) continue;
			for(String perm : APIPermMap.get(call))
			{
				thisCallsPerms.add(perm);
			}
			permsUsed.add(thisCallsPerms);
		}
		Set<String> APIPerms = getMinPermSet(permsUsed);
		return APIPerms;
	}

	/*
	 * get required intent permission set blueseal think necessary
	 */
	private Set<String> getIntentPerms(){
		Set<String> intents = infoEx.getManifestXml();
		Set<String> intentPerms = new HashSet<String>();
		Map<String, String> intentPermMap = loader.getIntentPermMap();
		for(String next : intents){
			if(intentPermMap.containsKey(next)){
				intentPerms.add(intentPermMap.get(next));
			}
		}
		return intentPerms;
	}
	
	/*
	 * get required content provider permissions blueseal think necessary
	 */
	private Set<String> getContentProviderPerms(Set<SootMethod> reachableMethods){
		Map<String, String> cpPermMap = loader.getContentProviderPermMap();
		Set<String> cpPerms = new HashSet<String>();
		for(SootMethod method:reachableMethods){
			if(!method.hasActiveBody()){
				continue;
			}
			Body body = method.getActiveBody();
			PatchingChain<Unit> units = body.getUnits();
			for(Unit unit:units){
				List<ValueBox> useBoxes = unit.getUseBoxes();
			    for(ValueBox useb : useBoxes){
			      Value val = useb.getValue();
			      Pattern useMethod = Pattern.compile("(<.*\\(.*\\)>)");
			      Matcher matcher = useMethod.matcher(val.toString());
			      if (matcher.find()) {
			          SootMethod sMethod = Scene.v().getMethod(matcher.group(1));
			          String unitMethodString = matcher.group(1);
			          if (sMethod.hasActiveBody()) {
			        	  if(cpPermMap.containsKey(val.toString())){
			        		  cpPerms.add(cpPermMap.get(val.toString()));
			        	  }
			          }   
			      }   
			    }
			}
		}
		return cpPerms;
	}
	
	
	private Set<String> fixSigFormat(Set<String> calls)
	{
		Set<String> ret = new HashSet<String>();
		for (String s : calls)
		{
			int leftAnglePos = s.indexOf("<");
			int rightAnglePos = s.lastIndexOf(">");
			s = s.substring(leftAnglePos + 1, rightAnglePos);
			int colon = s.indexOf(":");
			String type = s.substring(0, colon);
			String[] parts = s.split(" ");
			String methodName = parts[parts.length - 1];
			ret.add(type + "." + methodName);
		}
		return ret;
	}
	
	private Set<String> getMinPermSet(Set<Set<String>> perms)
	{
		//later use Dons Algo, for now just flattem
		Set<String> ret = new HashSet<String>();
		for(Set<String> set : perms)
		{
			for(String perm : set)
			{
				ret.add(perm);
			}
		}
		return ret;
	}
	
	private Set<String> removeCustomPerms(Set<String> permissions)
	{
		Set<String> ret = new HashSet<String>();
		for (String p : permissions)
		{
			if (p.startsWith("android.permission.") || 
			    p.startsWith("com.android.browser."))
			{
				ret.add(p);
			}
		}
		return ret;
	}
}
