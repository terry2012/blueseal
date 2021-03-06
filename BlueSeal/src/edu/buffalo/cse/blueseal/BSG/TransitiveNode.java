/*
 * this is used to represent the intermediate source node in bsg
 * this is only a wrapper to distinguish with original source node in bsg
 */
package edu.buffalo.cse.blueseal.BSG;

public class TransitiveNode extends Node {
	SourceNode sn;
	public TransitiveNode(SourceNode sn) {
		super(sn.getName(),sn.getStmt(), sn.getMethod());
		this.sn=sn;
	}

	public boolean equals(Object o){
		if(!(o instanceof TransitiveNode)) return false;
		
		TransitiveNode s = (TransitiveNode)o;
		
		return s.sn.equals(this.sn);
	}
	
	public int hashCode(){
		return sn.hashCode();
	}
	
	public SourceNode getSrcNode(){
		return this.sn;
	}
	
	public void print(){
		sn.print();
	}
}
