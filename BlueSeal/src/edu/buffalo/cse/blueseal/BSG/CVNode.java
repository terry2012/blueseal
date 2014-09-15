package edu.buffalo.cse.blueseal.BSG;

import edu.buffalo.cse.blueseal.BSFlow.InterProceduralMain;
import soot.SootClass;
import soot.jimple.Stmt;

public class CVNode extends Node {
	
	private SootClass sc_;
	private String field;

	/*
	 * the name for a CVNode is the combination of declaring class name and 
	 * the variable name, so that the name is the unique id for CVNode
	 */
	public CVNode(String name, SootClass sc, String fieldName){
		super(name);
		this.sc_ = sc;
		this.field = fieldName;
	}
	
	public int hashCode(){
		return name.hashCode();
	}
	
	public boolean equals(Object o){
		if(!(o instanceof CVNode)) return false;
		CVNode node = (CVNode)o;
		return 	name.equals(node.getName());
	}

	
	public SootClass getSootClass(){
		return sc_;
	}
	
	public String getFieldName(){
		return field;
	}
	
	public void print(){
		InterProceduralMain.ps.println("{CV node}:"+name);
	}

}
