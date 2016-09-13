package edu.buffalo.cse.blueseal.BSG;

import java.util.LinkedList;
import java.util.List;

import edu.buffalo.cse.blueseal.BSFlow.InterProceduralMain;
import soot.SootMethod;
import soot.jimple.InvokeExpr;
import soot.jimple.Stmt;

public class SourceNode extends Node {
	
	private boolean original_ = false;
	private List<SootMethod> list_;
	private List<SourceNode> srcList = new LinkedList<SourceNode>();
	private String category;
	private String androidSourceMethodName = null;
	private String androidSourceMethodClassName = null;
	
	public String getSrsAPI(){
		return this.androidSourceMethodClassName+":"+this.androidSourceMethodName;
	}

	public SourceNode(String name, Stmt stmt, SootMethod m, 
			boolean isOriginal){
		super(name, stmt, m);
		method = m;
		this.original_ = isOriginal;
		if(this.original_){
			if(stmt.containsInvokeExpr()){
				InvokeExpr invoke = stmt.getInvokeExpr();
				
				androidSourceMethodName = invoke.getMethodRef().name();
				androidSourceMethodClassName = invoke.getMethodRef().declaringClass().getName();
			}
		}
	}
	
	public void setCategory(String category){
		this.category = category;
	}
	public String getCategory(){
		return this.category;
	}
	public List<SourceNode> getSrcList(){
		return this.srcList;
	}
	public void setSrcList(List<SourceNode> list){
		this.srcList = list;
	}
	public List<SootMethod> getList(){
		return this.list_;
	}
	
	public boolean isOriginal(){
		return original_;
	}
	
	public boolean equals(Object o){
		if(!(o instanceof SourceNode)) return false;
		
		SourceNode s = (SourceNode)o;
		
		return stmt.toString().equals(s.getStmt().toString())&&
				method.getSignature().equals(s.getMethod().getSignature())
				&&srcList.equals(s.getSrcList());
	}
	
	public int hashCode(){
		return stmt.toString().hashCode() + method.getSignature().hashCode();
	}
	
	public void print(){
		InterProceduralMain.ps.println("{source node}:"+name+"=>method:"
				+ method.getName()+"=>class:" + method.getDeclaringClass().getName()+ hashCode());
	}
	
}
