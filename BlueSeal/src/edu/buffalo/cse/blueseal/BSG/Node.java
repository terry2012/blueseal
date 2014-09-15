package edu.buffalo.cse.blueseal.BSG;

import soot.jimple.Stmt;

public class Node {
	String name;
	Stmt stmt;
	
	public Node(String name){
		this.name = name;
	}
	
	public Node(String name, Stmt stmt){
		this.name = name;
		this.stmt = stmt;
	}
	
	public String getName(){
		return this.name;
	}
	
	public Stmt getStmt(){
		return this.stmt;
	}
	
	public boolean equals(Object o){
		if(!(o instanceof Node)) return false;
		
		Node node = (Node)o;
		
		if(this.name.equals(node.getName())) 
			return true;
		
		return false;
	}
	
	public int hashCode(){
		return name.hashCode();
	}
	
	public void print(){}
}
