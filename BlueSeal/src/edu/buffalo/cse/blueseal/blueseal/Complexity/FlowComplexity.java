/*
 * this class is used to extract the flow complexity info
 * based on the flows found
 * after analysis, build the global bsg for complexity
 * @bsg: the complete flow path graph
 */

package edu.buffalo.cse.blueseal.blueseal.Complexity;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import soot.SootMethod;

import edu.buffalo.cse.blueseal.BSG.BlueSealGraph;
import edu.buffalo.cse.blueseal.BSG.CVNode;
import edu.buffalo.cse.blueseal.BSG.Node;
import edu.buffalo.cse.blueseal.BSG.SinkNode;
import edu.buffalo.cse.blueseal.BSG.SourceNode;

public class FlowComplexity {
	
	private BlueSealGraph bsg = new BlueSealGraph();

	public static Set<List<Node>> flowPaths = new HashSet<List<Node>>();
	public static HashMap<SinkNode, Set<SourceNode>> srsToSameSink = 
			new HashMap<SinkNode, Set<SourceNode>>();
	public static HashMap<SourceNode, Set<SinkNode>> srcToMultiSink = 
			new HashMap<SourceNode, Set<SinkNode>>();
	public static HashMap<SourceNode, Set<List<Node>>> srcToMultiFlow = 
			new HashMap<SourceNode, Set<List<Node>>>();
	public static HashMap<SinkNode, Set<List<Node>>> sinkToMultiFlow = 
			new HashMap<SinkNode, Set<List<Node>>>();
	
	public FlowComplexity(BlueSealGraph complexBSG){
		bsg = complexBSG;
	}

	public static Set<SootMethod> getFlowMethods(List<Node> flow) {
		Set<SootMethod> result = new HashSet<SootMethod>();
		for(Node node : flow){
			if(node instanceof SourceNode){
				result.add(((SourceNode)node).getMethod());
			}else if(node instanceof SinkNode){
				result.add(((SinkNode)node).getMethod());
			}
		}
		return result;
	}	

	public void printAllOriginalSources(){
		List<SourceNode> srcs = getAllOriginalSourceNodes(bsg);
		
		for(SourceNode src : srcs){
			src.print();
		}
	}
	
	public void printAllOriginalSinks(){
		List<SinkNode> sinks = getAllOriginalSinkNodes(bsg);
		
		for(SinkNode sink : sinks){
			sink.print();
		}
	}
	
	public void extractFlowPaths(){
		
		System.out.println("global BlueSeal graph has "+ bsg.size() + "nodes");
		List<SourceNode> srcs = getAllOriginalSourceNodes(bsg);
		
		for(SourceNode src : srcs){
			Set<List<Node>> paths = DepthFirstSearchForFlowPaths(src);
			flowPaths.addAll(paths);
		}
		System.out.println("total:"+flowPaths.size());
//		checkSrcsFlowIntoSameSink();
//		checkSrcFlowIntoMultiSink();
		checkFlowsInMultiFlows();
	}
	
	private void checkFlowsInMultiFlows(){
		List<SourceNode> srcs = getAllOriginalSourceNodes(bsg);
		List<SinkNode> sinks = getAllOriginalSinkNodes(bsg);

		for(SourceNode src : srcs){
			Set<List<Node>> multiFlows = new HashSet<List<Node>>();
			for(List<Node> path : flowPaths){
				if(path.contains(src)){
					multiFlows.add(path);
				}
			}
			if(multiFlows.size() > 1){
				srcToMultiFlow.put(src, multiFlows);
			}
		}
		
		for(SinkNode sink : sinks){
			Set<List<Node>> multiFlows = new HashSet<List<Node>>();
			for(List<Node> path : flowPaths){
				if(path.contains(sink)){
					multiFlows.add(path);
				}
			}
			if(multiFlows.size() > 1){
				sinkToMultiFlow.put(sink, multiFlows);
			}
		}
	}
	
	private void checkSrcFlowIntoMultiSink() {
		List<SinkNode> sinks = getAllOriginalSinkNodes(bsg);
		List<SourceNode> srcs = getAllOriginalSourceNodes(bsg);
		
		for(SourceNode src : srcs){
			Set<SinkNode> multiSinks = new HashSet<SinkNode>();
			
			for(SinkNode sink : sinks){
				for(List<Node> path : flowPaths){
					if(path.contains(sink)&&path.contains(src)){
						multiSinks.add(sink);
					}
				}
			}
			srcToMultiSink.put(src, multiSinks);
		}
		
	}

	public void checkSrcsFlowIntoSameSink(){
		List<SinkNode> sinks = getAllOriginalSinkNodes(bsg);
		List<SourceNode> srcs = getAllOriginalSourceNodes(bsg);
		
		for(SinkNode sink : sinks){
			Set<SourceNode> sameSinks = new HashSet<SourceNode>();
			
			for(SourceNode src : srcs){
				for(List<Node> path : flowPaths){
					if(path.contains(sink)&&path.contains(src)){
						sameSinks.add(src);
					}
				}
			}
			srsToSameSink.put(sink, sameSinks);
		}
		
	}
	
	public void printFlowPaths(){
		for(List<Node> path : flowPaths){
			System.out.println("Path starts:");
			
			for(Node node : path){
				node.print();
				System.out.println("--->");
			}
			
			System.out.println("Path ends!");
		}
	}
	public static void printFlowPath(List<Node> path){
		System.out.println("Path starts:");
		
		for(Node node : path){
			node.print();
			System.out.println("--->");
		}
		
		System.out.println("Path ends!");
	}
	private Set<List<Node>> DepthFirstSearchForFlowPaths(SourceNode src) {
		Set<List<Node>> paths = new HashSet<List<Node>>();
		List<Node> curList = new LinkedList<Node>();
		Set<Node> curSeen = new HashSet<Node>();
		
		Set<List<Node>> newPaths = DFSForFlowPaths(src, curList, curSeen);
		paths.addAll(newPaths);
		return paths;
	}

	private Set<List<Node>> DFSForFlowPaths(Node node, List<Node> list,
			Set<Node> seen) {
		Set<List<Node>> paths = new HashSet<List<Node>>();
		
		if(node instanceof SinkNode){
			if(((SinkNode)node).isOriginal()){
				List<Node> path = new LinkedList<Node>();
				path.addAll(list);
				path.add(node);
				List<Node> newPath = new LinkedList<Node>();
				for(Node listNode : path){
//					if(!(listNode instanceof CVNode)){
						newPath.add(listNode);
//					}
				}
				paths.add(newPath);
			}
		}
		
		List<Node> curList = new LinkedList<Node>();
		curList.addAll(list);
		curList.add(node);
		seen.add(node);
		Set<Node> succs = bsg.getSuccessorsOf(node);
		
		for(Node succ : succs){
			if(seen.contains(succ)) continue;
			
			paths.addAll(DFSForFlowPaths(succ, curList, seen));
		}
		return paths;
	}

	private List<SourceNode> getAllOriginalSourceNodes(BlueSealGraph graph) {
		List<SourceNode> origins = new ArrayList<SourceNode>();
		
		for(SourceNode src : graph.getSrcs()){
			if(src.isOriginal()){
				origins.add(src);
			}
		}
		return origins;
	}
	
	private List<SinkNode> getAllOriginalSinkNodes(BlueSealGraph graph) {
		List<SinkNode> origins = new ArrayList<SinkNode>();
		
		for(SinkNode sink : graph.getSinks()){
			if(sink.isOriginal()){
				origins.add(sink);
			}
		}
		return origins;
	}
	
	
}
