package edu.buffalo.cse.blueseal.BSG;

import edu.buffalo.cse.blueseal.BSFlow.InterProceduralMain;
import soot.SootMethod;
import soot.jimple.Stmt;

public class RetNode extends Node {
	
	public RetNode(String name){
		super(name);
	}
	
	public RetNode(String name, Stmt stmt, SootMethod m){
		super(name, stmt, m);
	}
	
	public boolean equals(Object o){
		if(!(o instanceof RetNode)) return false;
		
		RetNode r = (RetNode)o;
		
		return stmt.toString().equals(r.getStmt().toString())
				&&method.getSignature().equals(r.getMethod().getSignature());
	}
	
	public int hashCode(){
		return stmt.toString().hashCode()+method.getSignature().hashCode();
	}

	public void print(){
		InterProceduralMain.ps.print("{return node}:"+name+"\n");
	}
}


