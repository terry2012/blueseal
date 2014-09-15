package edu.buffalo.cse.blueseal.BSG;

import edu.buffalo.cse.blueseal.BSFlow.InterProceduralMain;
import edu.buffalo.cse.blueseal.blueseal.Debug;
import soot.jimple.Stmt;

public class RetNode extends Node {
	
	public RetNode(String name){
		super(name);
	}
	
	public RetNode(String name, Stmt stmt){
		super(name, stmt);
	}
	
	public boolean equals(Object o){
		if(!(o instanceof RetNode)) return false;
		
		RetNode r = (RetNode)o;
		
		return stmt.equals(r.getStmt())&&name.equals(r.getName());
	}

	public void print(){
		InterProceduralMain.ps.print("{return node}:"+name+"\n");
	}
}


