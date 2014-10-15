package edu.buffalo.cse.blueseal.BSFlow;

import java.util.List;
import java.util.Map;
import soot.SceneTransformer;
import soot.SootMethod;
import soot.jimple.toolkits.callgraph.CallGraph;

public class BSInterproceduralTransformer extends SceneTransformer {
	private static BSInterproceduralTransformer instance = new BSInterproceduralTransformer();
	
	
	  public BSInterproceduralTransformer() {}

	  public static BSInterproceduralTransformer v() {
	    return instance;
	  }

	    @Override
		protected void internalTransform(String phaseName, Map options) {  
	        List<SootMethod> entryPoints = CgTransformer.entryPoints;
	        CallGraph cg = CgTransformer.cg;
	        
			BSInterproceduralAnalysis inter = 
					new BSInterproceduralAnalysis(cg, new SootMethodFilter(null), 
							CgTransformer.reachableMethods_.iterator(), false);
			
			GlobalBSG gBSG = new GlobalBSG(inter.data,
					CgTransformer.reachableMethods_.iterator(), true);
			gBSG.print();
//			ComplexFlowFinder cff = new ComplexFlowFinder(inter.data, CgTransformer.reachableMethods_.iterator(), true);
//				FlowSynthesizer synthesizer = new FlowSynthesizer(inter.data, 
//						CgTransformer.reachableMethods_.iterator(), true);
	    }

}
