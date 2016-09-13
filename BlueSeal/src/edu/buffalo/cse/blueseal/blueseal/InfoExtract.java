package edu.buffalo.cse.blueseal.blueseal;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


public class InfoExtract {
	
	private Map<String, String> map = new HashMap<String, String>();
	private String apkPath = new String();
	
	public InfoExtract(String apkPath){
		this.apkPath = apkPath;
		map = getInfoMap(apkPath);
	}
	
	public String getPackageName(){
		String result = null;
		if(map.containsKey("package")){
			result = map.get("package").split(" ")[1].split("=")[1].trim();
			result = result.substring(1, result.length()-1);
		}
		return result;
	}
	
	public Map<String, String> getManifestInfoMap(){
		return this.map;
	}
	
	/**
	 * Extracts AndroidManifest.xml from apk 
	 * and returns a map of useful information 
	 * from it. 
	 * @param apkPath
	 * @return
	 */
	private Map<String, String> getInfoMap(String apkPath){
		try {
			Process p = Runtime.getRuntime().exec(Constants.aapt +" d permissions " + apkPath);
			int exitStatus = p.waitFor();
			if (exitStatus == 0) {
				BufferedReader reader = new BufferedReader(
						new InputStreamReader(p.getInputStream()));
				String line = null;
				while ((line = reader.readLine()) != null) {
					if (line.contains(":")) {
						String[] splits = line.split(":");
						if (map.containsKey(splits[0])) {
							map.put(splits[0], map.get(splits[0]) + " "
									+ splits[1]);
						} else {
							map.put(splits[0], splits[1]);
						}
					}
				}
			}
			else{
				System.err.println("Info Extract failed to run");
				System.exit(1);
			}
		} catch (IOException e) {
			e.printStackTrace();
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
		return map;
	}
	
	/**
	 * get the mainfest xml structure of given apk
	 * Return all intents declared in the mainfest
	 * @param apkPath
	 */
	public Set<String> getManifestXml(){
		Set<String> intents = new HashSet<String>();
		try {
			Process p  = Runtime.getRuntime().exec(Constants.aapt + " d xmltree " + apkPath + " AndroidManifest.xml");
			int exitValue = p.waitFor();
			if(exitValue == 0){
				BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()));
				String line=null;
				while((line=reader.readLine())!= null){
					if(line.contains("intent-filter")){
						String next = reader.readLine();
						while(next!=null && next.contains("action")){
							String perm=reader.readLine();
							String[] splits=perm.split("\"");
							for(String split : splits){
								if(split.contains("android.") || split.contains("ndefine")){
									intents.add(split);
								}
							}
							next = reader.readLine();
						}
						if(next==null){
							break;
						}
					}
				}
			}else{
				System.err.println("Info Extract failed to run");
				System.exit(1);
			}
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return intents;
	}
	
	/**
	 * Returns a set of permissions
	 * declared in the manifest.
	 * @return
	 */
	public Set<String> getDeclaredPermissionsInManifest(){
		Set<String> permissionsSet = new HashSet<String>();
		if(map.containsKey("uses-permission")){
			String[] splits = map.get("uses-permission").split(" ");
			for(String perm : splits){
				System.out.println("declared-permissions:"+perm);
				permissionsSet.add(perm);
			}
		}
		return permissionsSet;
	}	
	
	/**
	 * Get the name of the launchable activity
	 * the AndroidManifest.xml
	 * @return
	 */
	public String getLaunchableActivity(){
		if(map.containsKey("launchable-activity")){
			String splits[] = map.get("launchable-activity").trim().split(" ");
			String result = splits[0].split("=")[1];
			return result.substring(1, result.length()-1);
		}
		return null;
	}
}
