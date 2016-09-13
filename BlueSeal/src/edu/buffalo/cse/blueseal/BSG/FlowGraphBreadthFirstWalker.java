package edu.buffalo.cse.blueseal.BSG;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

public class FlowGraphBreadthFirstWalker {
	
	private BlueSealGraph flowGraph = null;
	
	public FlowGraphBreadthFirstWalker(BlueSealGraph bsg){
		flowGraph = bsg;
	}
	
	public Set<Node> searchAllChildrenOf(Node node, Set<Node> seen){
		Set<Node> children =getDirectChildrenOf(node);
		seen.add(node);
		Set<Node> tempChildren = new HashSet<Node>();
		tempChildren.addAll(children);
		
		for(Node child : tempChildren){
			
			if(!seen.contains(child)){
				seen.add(child);
				Set<Node> childrenOfChild = searchAllChildrenOf(child, seen);
				children.addAll(childrenOfChild);
			}
		}
		
		return children;
	}
	
	public Set<Node> getDirectChildrenOf(Node node){
		Set<Node> children = new HashSet<Node>();
		
		for(Iterator it = flowGraph.getEdges().iterator();it.hasNext();){
			Edge e = (Edge) it.next();
			Node src = e.getSrc();
			Node tgt = e.getTarget();
			
			if(e.getSrc().equals(node)){
				children.add(e.getTarget());
			}
		}
		
		return children;
	}
	public void run(){
		
	}

}
