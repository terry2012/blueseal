/*
 * this class is to build up a global blueseal graph based on analysis results
 * the purpose is to build up relations among methods, detect flows inside/across methods
 * it contains three essential elements:
 * 1. method summary from inter-procedural analysis
 * 2. the blueseal graph to be constructed
 * 3. the methods that should be taken into accounts
 * 
 * currently, it only deals with forward flow analysis
 * maybe create a separate one for backward flow analysis later
 */

package edu.buffalo.cse.blueseal.BSInfoFlowAnalysis;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import soot.SootMethod;
import edu.buffalo.cse.blueseal.BSFlow.CgTransformer;
import edu.buffalo.cse.blueseal.BSG.BlueSealGraph;
import edu.buffalo.cse.blueseal.BSG.CVNode;
import edu.buffalo.cse.blueseal.BSG.Edge;
import edu.buffalo.cse.blueseal.BSG.SinkNode;
import edu.buffalo.cse.blueseal.BSG.SourceNode;

public class BSGlobalFlowGraphBuilder {
	
	private Map<SootMethod, BlueSealGraph> analysisSummary = new HashMap<SootMethod, BlueSealGraph>();
	private BlueSealGraph indirectFlowGraph = new BlueSealGraph();//this holds flows between methods/through CVs
	private BlueSealGraph directFlowGraph = new BlueSealGraph();//this holds flows can be extracted from a single method
	private Set<SootMethod> methods = null;
	private BlueSealGraph complexBSG = new BlueSealGraph();
	
	// in this map, for each class variable maintain a list of sources that flow
	// into it, this will be used to generate flow permissions
	public static HashMap<String, List<SourceNode>> cvToSource = new HashMap<String, List<SourceNode>>();
	
	public BSGlobalFlowGraphBuilder(Map sum, Set<SootMethod> methodSet){
		analysisSummary = sum;
		methods = methodSet;
	}
	
	public BlueSealGraph getComplexBSG(){
		return complexBSG;
	}
	
	public void buildComplexBSG(){
		for(Iterator it = methods.iterator(); it.hasNext();){
			SootMethod method = (SootMethod) it.next();
			
			if(!analysisSummary.containsKey(method)) continue;
			
			BlueSealGraph bsg = analysisSummary.get(method);
			addComplexGraphEdges(bsg);
		}
		
		unwrapSourcesAndSinks(complexBSG);
	}
	
	private void unwrapSourcesAndSinks(BlueSealGraph bsg) {
		BlueSealGraph tempBSG = new BlueSealGraph();
		tempBSG.merge(bsg);
		unwrapNoneOriginSources(tempBSG, bsg);
		unwrapNoneOriginSinks(tempBSG, bsg);
	}

	private void unwrapNoneOriginSinks(BlueSealGraph tempBSG, BlueSealGraph bsg) {
		for(SinkNode sink : tempBSG.getSinks()){
			unwrapSingleSinkNode(sink, bsg);
		}
	}

	private void unwrapSingleSinkNode(SinkNode sink, BlueSealGraph bsg) {
		for(SinkNode succ : sink.getSinkList()){
			bsg.addEdge(sink, succ);
			unwrapSingleSinkNode(succ, bsg);
		}
		
	}

	private void unwrapNoneOriginSources(BlueSealGraph tempBSG, BlueSealGraph bsg) {
		for(SourceNode src : tempBSG.getSrcs()){
			unwrapSingleSourceNode(src, bsg);
		}
	}

	private void unwrapSingleSourceNode(SourceNode src, BlueSealGraph bsg) {
		List<SourceNode> preds = src.getSrcList();
		
		for(SourceNode pred : preds){
			bsg.addEdge(pred, src);
			unwrapSingleSourceNode(pred, bsg);
		}
	}

	private void addComplexGraphEdges(BlueSealGraph bsg) {
		complexBSG.addEdges(bsg.getSrcToSink());
		complexBSG.addEdges(bsg.getSrcToCV());
		complexBSG.addEdges(bsg.getCVToSink());
		complexBSG.addEdges(bsg.getCVToCV());
	}

	public void printMethodSummary(){
		for(Iterator it = methods.iterator(); it.hasNext();){
			SootMethod method = (SootMethod) it.next();
			
			if(!analysisSummary.containsKey(method)) continue;
			
			BlueSealGraph bsg = analysisSummary.get(method);
			bsg.print();
		}
	}
	
	public BlueSealGraph getDirectFlowGraph(){
		return this.directFlowGraph;
	}
	
	public BlueSealGraph getIndirectFlowGraph(){
		return this.indirectFlowGraph;
	}
	
	public void build(){
		//first build up direct Flow Graph
		buildDirectFlowGraph();
		
		//build up indirect flow graph, flows through CVs
		buildIndirectFlowGraph();

		//build up global bsg for flow complexity
		buildComplexBSG();
	}

	/*
	 * build up indirect flow graph, process each method
	 * and add all edges wanted into global indirect flow graph
	 */
	private void buildIndirectFlowGraph() {
		for(Iterator it = methods.iterator(); it.hasNext();){
			SootMethod method = (SootMethod)it.next();
			
			if(!analysisSummary.containsKey(method)) continue;
			
			BlueSealGraph summary = analysisSummary.get(method);
			addIndirectEdges(summary, indirectFlowGraph);
		}
		
	}

	/*
	 * the real executor to add indirect edges
	 */
	private void addIndirectEdges(BlueSealGraph summary, BlueSealGraph bsg) {
		addCVToSinkEdges(summary, bsg);
		addSourceToCVEdges(summary, bsg);
		bsg.addEdges(summary.getCVToCV());
	}

	/*
	 * add source to CV edges into indirect flow graph
	 */
	private void addSourceToCVEdges(BlueSealGraph summary, BlueSealGraph bsg) {
		HashSet<Edge> srcToCV = summary.getSrcToCV();
			
		for(Edge e : srcToCV){
			CVNode cv = (CVNode) e.getTarget();
			SourceNode src = (SourceNode) e.getSrc();

			//resolve the srcNode, same as we do in process single method
			if(src.isOriginal()){
				bsg.addEdge(src, cv);

				// add sources into the cvToSources map list
				if(cvToSource.containsKey(cv.getName())){
					cvToSource.get(cv.getName()).add(src);
				}else{
					List<SourceNode> cv_list = new LinkedList<SourceNode>();
					cv_list.add(src);
					cvToSource.put(cv.getName(), cv_list);
				}
			}else{
				List<SourceNode> origins = getOriginSourceNodeList(src);
				
				for(SourceNode origin : origins){
					bsg.addEdge(origin, cv);

					if(cvToSource.containsKey(cv.getName())){
						cvToSource.get(cv.getName()).add(src);
					}else{
						List<SourceNode> cv_list = new LinkedList<SourceNode>();
						cv_list.add(origin);
						cvToSource.put(cv.getName(), cv_list);
					}
				}
			}
		} // end of iterate all the edges in one method
	}

	/*
	 * add CV to sink edges into indirect flow graph
	 */
	private void addCVToSinkEdges(BlueSealGraph summary, BlueSealGraph bsg) {
		HashSet<Edge> cvToSink = summary.getCVToSink();
		
		//iterate all the edges from classVariables to sinks
		for (Edge e : cvToSink){
			CVNode cv = (CVNode) e.getSrc();
			SinkNode sink = (SinkNode) e.getTarget();
			List<SinkNode> origs = getOriginSinkNodeList(sink);
			
			for(SinkNode orig : origs){
				bsg.addEdge(cv,orig);
			}
		}
	}

	/*
	 * find all the flows inside method summary
	 * skip all the intermediate nodes, only keep the original source and sink
	 */
	private void buildDirectFlowGraph() {
		//detects flow crossing method
		for(Iterator it = methods.iterator(); it.hasNext();){
			SootMethod method = (SootMethod)it.next();
			
			if(!analysisSummary.containsKey(method)) continue;
			
			processSingleMethod(method);
		}
	}
	
	/*
	 * this will process a single method summary
	 * @param: method-> the method to be processed
	 */
	private void processSingleMethod(SootMethod method) {
		BlueSealGraph bsg = analysisSummary.get(method);
		Set<Edge> srcToSink = bsg.getSrcToSink();
		
		for(Edge e : srcToSink){
			SourceNode src = (SourceNode) e.getSrc();
			SinkNode sink = (SinkNode) e.getTarget();
			
			List<SourceNode> origSrc = getOriginSourceNodeList(src);
			List<SinkNode> origSink = getOriginSinkNodeList(sink);
			
			for(SourceNode sn : origSrc){
				for(SinkNode node : origSink){
					directFlowGraph.addEdge(sn, node);
				}
			}
		}
	}
	
	private List<SinkNode> getOriginSinkNodeList(SinkNode sink) {
		List<SinkNode> origin = new ArrayList<SinkNode>();
		List<SinkNode> parents = sink.getSinkList();
		
		if(sink.isOriginal()){
			origin.add(sink);
		}
		for(SinkNode parent : parents){
			if(parent.isOriginal()){
				origin.add(parent);
			}else{
				origin.addAll(getOriginSinkNodeList(parent));
			}
		}
		return origin;
	}

	/*
	 * this method deals with non-original source node
	 * extract source list and find the original source node
	 * @param: src-> the intermediate source node to be checked
	 */
	public static SourceNode getOriginSourceNode(SourceNode src) {
		List<SourceNode> parents = src.getSrcList();
		
		for(SourceNode parent : parents){
			if(parent.isOriginal()){
				return parent;
			}else{
				return getOriginSourceNode(parent);
			}
		}
		return null;
	}
	
	public static List<SourceNode> getOriginSourceNodeList(SourceNode src) {
		List<SourceNode> parents = src.getSrcList();
		List<SourceNode> origin = new ArrayList<SourceNode>();
		
		if(src.isOriginal()){
			origin.add(src);
		}
		
		for(SourceNode parent : parents){
			if(parent.isOriginal()){
				origin.add(parent);
			}else{
				origin.addAll(getOriginSourceNodeList(parent));
			}
		}
		return origin;
	}

}
