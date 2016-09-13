package edu.buffalo.cse.blueseal.BSG;

import edu.buffalo.cse.blueseal.BSFlow.InterProceduralMain;
import soot.SootMethod;
import soot.jimple.Stmt;

public class ArgNode extends Node {
	/*
	 * for each argument node, we need to keep info:
	 * 1. which method it belongs to
	 * 2. Name for this argument
	 */
	int num;//indicate the position in the para list

	public ArgNode(String name, Stmt stmt, SootMethod m, int index) {
		 super(name, stmt, m);
		 num = index;
	}

	public int getParaNum() {
		return num;
	}
	
	public boolean equals(Object o){
		if(!(o instanceof ArgNode)) return false;
		
		ArgNode a = (ArgNode)o;
		
		return stmt.toString().equals(a.getStmt().toString())&&
				method.getSignature().equals(a.getMethod().getSignature())&&
				num==a.getParaNum();
	}
	
	public int hashCode(){
		return stmt.toString().hashCode()+method.getSignature().hashCode()+num;
	}
	
	public void print(){
		InterProceduralMain.ps.println("{argument node}:"+name+"\n");
	}
}
