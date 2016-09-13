package edu.buffalo.cse.blueseal.BSG;

import soot.SootMethod;
import soot.jimple.Stmt;

public class Node {
	String name;
	Stmt stmt;
	SootMethod method;

	public Node(Stmt stmt_, SootMethod method_){
		stmt = stmt_;
		method = method_;
	}

	public Node(String name, Stmt stmt, SootMethod method_){
		this.name = name;
		this.stmt = stmt;
		this.method = method_;
	}
	
	public Node(String nodeName) {
		name = nodeName;
	}

	public SootMethod getMethod(){
		return method;
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
	
	public void print(){
		System.out.println(name);
	}
}
