/*
 * this class is used to synthesize all the information flows of given app,
 * generate flow permissions
 * 
 * ---BlueSeal post-analysis
 */

package edu.buffalo.cse.blueseal.BSInfoFlowAnalysis;

import java.util.Set;

import soot.SootMethod;
import soot.toolkits.graph.BlockGraph;
import edu.buffalo.cse.blueseal.BSCallgraph.BSCallGraphTransformer;
import edu.buffalo.cse.blueseal.BSFlow.BSInterproceduralAnalysis;
import edu.buffalo.cse.blueseal.BSG.BlueSealGraph;
import edu.buffalo.cse.blueseal.blueseal.Complexity.FlowComplexity;

public class BSPostAnalysisExecutor {
	
	private BSInterproceduralAnalysis inter = null;
	

	private Set<String> flowpermissions;
	private BlueSealGraph finalBSG;
	private BlueSealGraph complexBSG;

	public BSPostAnalysisExecutor(BSInterproceduralAnalysis analysis){
		inter = analysis;
	}
	
	public BlueSealGraph getComplexBSG(){
		return complexBSG;
	}
	
	public BlueSealGraph getFinalBSG(){
		return this.finalBSG;
	}
	
	public Set<String> getFPs(){
		return this.flowpermissions;
	}
	/*
	 * real method to execute BlueSeal post-analyzer
	 */
	public void execute(){
	    Set<SootMethod> reachableMethods = BSCallGraphTransformer.appInfo.getReachableMethods();
	    
	    //build-up flow graph based on analysis results
		BSGlobalFlowGraphBuilder builder = new BSGlobalFlowGraphBuilder(inter.getData(), reachableMethods);
		builder.build();
		complexBSG = builder.getComplexBSG();
		
		//resolve flow graph to construct a single final flow graph
		BSFlowGraphResolver flowResolver = new BSFlowGraphResolver(builder);
		flowResolver.resolve();
		finalBSG = flowResolver.getFinalFlowGraph();
		
		//finally, generate flow permissions
		BSFlowPermissionGenerator fpGenerator = new BSFlowPermissionGenerator(flowResolver.getFinalFlowGraph());
		fpGenerator.generate();
		flowpermissions = fpGenerator.getFlowPermissions();
		
		//print out result
		fpGenerator.printFlowPermissions();
		BSInfoFlowPrinter printer = new BSInfoFlowPrinter(finalBSG);
		printer.print();
	}
	
	/*
	 * real method to execute BlueSeal post-analyzer
	 * this one is taken specific reachable methods instead of all the reachable methods from the whole 
	 * call grpah
	 */
	public void execute(Set<SootMethod> reachableMethods){
	    
	    //build-up flow graph based on analysis results
		BSGlobalFlowGraphBuilder builder = new BSGlobalFlowGraphBuilder(inter.getData(), reachableMethods);
		builder.build();
		
		//resolve flow graph to construct a single final flow graph
		BSFlowGraphResolver flowResolver = new BSFlowGraphResolver(builder);
		flowResolver.resolve();
		finalBSG = flowResolver.getFinalFlowGraph();
		
		//finally, generate flow permissions
		BSFlowPermissionGenerator fpGenerator = new BSFlowPermissionGenerator(flowResolver.getFinalFlowGraph());
		fpGenerator.generate();
		flowpermissions = fpGenerator.getFlowPermissions();
		
		//print out result
//		fpGenerator.printFlowPermissions();
//		BSInfoFlowPrinter printer = new BSInfoFlowPrinter(flowResolver.getFinalFlowGraph());
//		printer.print();
		
		//run flow struct complexity
		FlowComplexity fc = new FlowComplexity(builder.getComplexBSG());
		fc.extractFlowPaths();
	}
	
	
}
