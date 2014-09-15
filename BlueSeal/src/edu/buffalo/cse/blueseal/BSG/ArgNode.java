package edu.buffalo.cse.blueseal.BSG;

import edu.buffalo.cse.blueseal.BSFlow.InterProceduralMain;
import edu.buffalo.cse.blueseal.blueseal.Debug;
import soot.jimple.Stmt;

public class ArgNode extends Node {
	/*
	 * for each argument node, we need to keep info:
	 * 1. which method it belongs to
	 * 2. Name for this argument
	 */
	int num;//indicate the position in the para list
	public ArgNode(String name){
		super(name);
	}

	public ArgNode(String name, Stmt stmt, int index) {
		 super(name, stmt);
		 num = index;
	}

	public int getParaNum() {
		return num;
	}
	
	public boolean equals(Object o){
		if(!(o instanceof ArgNode)) return false;
		
		ArgNode a = (ArgNode)o;
		
		return stmt.equals(a.getStmt())&&
				name.equals(a.getName())&&
				num==a.getParaNum();
	}
	
	public int hashCode(){
		return stmt.hashCode()+num;
	}
	
	public void print(){
		InterProceduralMain.ps.println("{argument node}:"+name+"\n");
	}
}
