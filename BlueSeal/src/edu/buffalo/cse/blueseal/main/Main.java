package edu.buffalo.cse.blueseal.main;

import soot.Pack;
import soot.PackManager;
import soot.Transform;
import edu.buffalo.cse.blueseal.BSCallgraph.BSCallGraphTransformer;
import edu.buffalo.cse.blueseal.BSFlow.SourceSink;
import edu.buffalo.cse.blueseal.BSInfoFlowAnalysis.BSInterInfoFlowTransformer;
import edu.buffalo.cse.blueseal.blueseal.Constants;

public class Main {
	public static void main(String[] args){
		
		if(args.length == 0){
		  System.err.print("Missing apk path. Exit!\n");
		  System.exit(1);
		}
		
		String apkpath = args[0];
		System.out.println("Analyzing:"+apkpath);
		
		apkpath = args[0];
		System.out.println("analyzing apk:"+apkpath);
		
		int index = apkpath.lastIndexOf("/");
		String apkName = "APKNAME";
		if(index!=-1)
			apkName = apkpath.substring(index+1);
		Constants.apkName = apkName;
		
		SourceSink.extractSootSourceSink();
		BSCallGraphTransformer.setSparkOn();
		
		//start soot
		BSCallGraphTransformer CgTransformer = new BSCallGraphTransformer(apkpath);
		
		Pack pack = PackManager.v().getPack("wjtp");
		pack.add(new Transform("wjtp.bscg", CgTransformer));
		pack.add(new Transform("wjtp.inter", BSInterInfoFlowTransformer.v()));
		
		String[] sootArgs = {"-w","-f", "n", "-allow-phantom-refs", "-x",
				"android.support.", "-x", "android.annotation.", 
				"-process-dir", args[0],
				"-android-jars", Constants.ANDROID_JARS, 
				"-src-prec", "apk",
				"-no-bodies-for-excluded"
				};
		soot.Main.main(sootArgs);
	}
}
