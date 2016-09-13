/*
 * this class is to retrieve all the sources and sinks
 * 
 * no matter if it's dangling or contained in a flow
 * 
 * this will be based on the Blueseal information flow analysis results
 * 
 * what we do here is just traversing the whole blueseal results(method summaries from inter-analysis)
 * 
 * and find all the source nodes and sink nodes
 * 
 */
package edu.buffalo.cse.blueseal.BSInfoFlowAnalysis;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import soot.SootMethod;
import edu.buffalo.cse.blueseal.BSG.BlueSealGraph;
import edu.buffalo.cse.blueseal.BSG.SinkNode;
import edu.buffalo.cse.blueseal.BSG.SourceNode;

public class BSAllSourceAndSinkFinder {
	
	private Map BSsummary;
	
	private Set<SourceNode> srs;
	private Set<SinkNode> sink;
	
	public BSAllSourceAndSinkFinder(Map map){
		BSsummary = map;
		srs = new HashSet<SourceNode>();
		sink = new HashSet<SinkNode>();
	}
	
	public void find(){
		collectAllSourcesAndSinkNodes();
	}
	
	/*
	 * collect all the sources and sinks in the summary
	 */
	public void collectAllSourcesAndSinkNodes(){
		for(Iterator it = BSsummary.keySet().iterator(); it.hasNext();){
			SootMethod method = (SootMethod) it.next();
			BlueSealGraph bsg = (BlueSealGraph) BSsummary.get(method);
			
			for(SourceNode source : bsg.getSrcs()){
				if(source.isOriginal()){
					srs.add(source);
				}else{
					SourceNode orig = BSGlobalFlowGraphBuilder.getOriginSourceNode(source);
					srs.add(orig);
				}
			}
			
			sink.addAll(bsg.getSinks());
		}
	}
	
	public Set<SourceNode> getAllSources(){
		return this.srs;
	}
	
	public Set<SinkNode> getAllSinks(){
		return this.sink;
	}
	
}
