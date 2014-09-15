package edu.buffalo.cse.blueseal.BSG;

import edu.buffalo.cse.blueseal.BSFlow.InterProceduralMain;
import edu.buffalo.cse.blueseal.blueseal.Debug;
import soot.SootMethod;
import soot.jimple.Stmt;

public class SinkNode extends Node {
	
	private SootMethod method_;//stores info about where the sink is defined
	
	private boolean original_;
	private boolean isNetworkSink = false;

	public SinkNode(String name) {
		super(name);
	}
	
	public SinkNode(String name, Stmt stmt, SootMethod m, boolean original){
		super(name, stmt);
		this.method_ = m;
		this.original_ = original;
	}
	public boolean isNetworkSink(){
		return isNetworkSink;
	}
	public void setNetworkSink(){
		isNetworkSink = true;
	}
	public boolean isOriginal(){
		return original_;
	}
	
	public void setMethod(SootMethod method){
		this.method_ = method;
	}
	
	public SootMethod getMethod(){
		return this.method_;
	}

	public boolean equals(Object o){
		if(!(o instanceof SinkNode)) return false;
		
		SinkNode s = (SinkNode)o;
		
		return stmt.equals(s.getStmt())&&name.equals(s.getName())
				&& method_.equals(s.getMethod())
				&&method_.getDeclaringClass().equals(s.getMethod().getDeclaringClass());
	}
	
	public void print(){
		InterProceduralMain.ps.println("{Sink}:"+name+"=>method:"
				+ method_.getName()+"=>class:" + method_.getDeclaringClass().getName());
	}
}
