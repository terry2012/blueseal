package edu.buffalo.cse.blueseal.BSCallgraph;

import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import soot.Scene;
import soot.SceneTransformer;
import soot.SootMethod;
import soot.jimple.toolkits.callgraph.CHATransformer;    
import soot.jimple.toolkits.callgraph.CallGraph;
import soot.jimple.toolkits.callgraph.Edge;
import soot.util.queue.QueueReader;
import edu.buffalo.cse.blueseal.BSSpark.BSSparkCGTransformer;
import edu.buffalo.cse.blueseal.EntryPointCreator.AndroidEntryPointCreator;
import edu.buffalo.cse.blueseal.utils.EntryPointsManager;

public class BSCallGraphTransformer extends SceneTransformer {
	
	public String apkLoc = null;
	private static boolean cha = true;
	private static boolean spark = false;
	
	public static CallGraph cg = null;
	public static ApplicationInfo appInfo= null;
	public static List<SootMethod> entryPoints = null;
	public static SootMethod dummyMain = null;
	
	public BSCallGraphTransformer(String apk){
		apkLoc = apk;
	}
	
	public static void setEntryPoints(List<SootMethod> list){
		entryPoints = list;
	}

	@Override
	protected void internalTransform(String arg0, Map<String, String> arg1) {
		EntryPointsManager epm = new EntryPointsManager(apkLoc);
		epm.loadApkEntryPoints();
		entryPoints = epm.getApkEntryPoints();
		
		//clear up everything
		if(cha){
			Scene.v().setEntryPoints(entryPoints);
			CHATransformer.v().transform();
			cg = Scene.v().getCallGraph();
		}else if(spark){
			AndroidEntryPointCreator creator = new AndroidEntryPointCreator(entryPoints);
			dummyMain = creator.createDummnyMain();
			List<SootMethod> newEntryPoints = new LinkedList<SootMethod>();
			newEntryPoints.add(dummyMain);
			Scene.v().setEntryPoints(newEntryPoints);
			
			BSSparkCGTransformer SparkCg = new BSSparkCGTransformer(apkLoc);
			SparkCg.transform();
			cg = SparkCg.CGSpark;
		}
		cg = Scene.v().getCallGraph();
		appInfo = new ApplicationInfo();
		//augment classes to handle Android specific components
		BSCallGraphAugmentor cga = new BSCallGraphAugmentor(cg, appInfo, 
				entryPoints);
		cga.run();
		appInfo.setReachableMethods(cga.getRebuiltReachableMethods());
	}

	public static void setSparkOn() {
		spark = true;
		cha = false;
	}
	
	public static void printCG(){
		System.out.println("blueseal cg printing:");
		QueueReader<Edge> listener = cg.listener();
		while(listener.hasNext()){
			Edge edge = listener.next();
			System.out.println("edge:"+edge.toString());
		}
	}

}
