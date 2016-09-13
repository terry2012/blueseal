package edu.buffalo.cse.blueseal.BSG;

import java.util.LinkedList;
import java.util.List;

import edu.buffalo.cse.blueseal.BSFlow.InterProceduralMain;
import soot.SootMethod;
import soot.jimple.InvokeExpr;
import soot.jimple.Stmt;

public class SinkNode extends Node {
	
	private boolean original_=false;
	private boolean isNetworkSink = false;
	String androidSinkMethodName = null;
	String androidSinkMethodClassName = null;
	private List<SinkNode> sinkList = new LinkedList<SinkNode>();


	public List<SinkNode> getSinkList(){
		return this.sinkList;
	}
	
	public void setSinkList(List<SinkNode> list){
		this.sinkList = list;
	}
	
	public String getSinkAPI(){
		return this.androidSinkMethodClassName+":"+this.androidSinkMethodName;
	}
	
	public SinkNode(String name, Stmt stmt, SootMethod m, boolean original){
		super(name, stmt, m);
		this.method = m;
		this.original_ = original;
		
		if(stmt.containsInvokeExpr()){
			InvokeExpr invoke = stmt.getInvokeExpr();
			
			androidSinkMethodName = invoke.getMethodRef().name();
			androidSinkMethodClassName = invoke.getMethodRef().declaringClass().getName();
		}
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

	public boolean equals(Object o){
		if(!(o instanceof SinkNode)) return false;
		
		SinkNode s = (SinkNode)o;
		
		return stmt.toString().equals(s.getStmt().toString())
				&& method.getSignature().equals(s.getMethod().getSignature())
				&&sinkList.equals(s.getSinkList());
	}
	
	public int hashCode(){
		return stmt.toString().hashCode() + method.getSignature().hashCode();
	}
	
	public void print(){
		InterProceduralMain.ps.println("{Sink}:"+name+"=>method:"
				+ method.getName()+"=>class:" + method.getDeclaringClass().getName()+ hashCode());
	}
}
