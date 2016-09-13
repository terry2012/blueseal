package edu.buffalo.cse.blueseal.BSInfoFlowAnalysis;

import java.util.List;
import java.util.Map;
import java.util.Set;

import soot.SceneTransformer;
import soot.SootMethod;
import soot.jimple.toolkits.callgraph.CallGraph;
import edu.buffalo.cse.blueseal.BSCallgraph.BSCallGraphTransformer;
import edu.buffalo.cse.blueseal.BSFlow.BSInterproceduralAnalysis;
import edu.buffalo.cse.blueseal.BSFlow.GlobalBSG;
import edu.buffalo.cse.blueseal.BSFlow.SootMethodFilter;
import edu.buffalo.cse.blueseal.blueseal.Complexity.FlowComplexity;

public class BSInterInfoFlowTransformer extends SceneTransformer {


	private static BSInterInfoFlowTransformer instance = new BSInterInfoFlowTransformer();
	

	  public static BSInterInfoFlowTransformer v() {
	    return instance;
	  }

	  @Override
	  protected void internalTransform(String phaseName, Map options) {
	  	List<SootMethod> entryPoints = BSCallGraphTransformer.entryPoints;
	    CallGraph cg = BSCallGraphTransformer.cg;
	    Set<SootMethod> reachableMethods = BSCallGraphTransformer.appInfo.getReachableMethods();
	        
	    //do the analysis
			BSInterproceduralAnalysis inter = new BSInterproceduralAnalysis(cg, new SootMethodFilter(null), 
					reachableMethods.iterator(), false);

			//BlueSeal post-analysis, synthesize all information flows, generate flow permissions
			BSPostAnalysisExecutor executor = new BSPostAnalysisExecutor(inter);
			executor.execute();
			
			//running path complexity
			FlowComplexity flowComplex = new FlowComplexity(executor.getComplexBSG());
			flowComplex.extractFlowPaths();
	  }


}
