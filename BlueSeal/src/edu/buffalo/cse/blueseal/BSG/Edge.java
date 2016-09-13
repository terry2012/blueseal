package edu.buffalo.cse.blueseal.BSG;

import soot.Value;
import soot.jimple.AssignStmt;
import soot.jimple.FieldRef;
import soot.jimple.InvokeExpr;
import soot.jimple.Stmt;
import soot.jimple.StringConstant;
import edu.buffalo.cse.blueseal.BSFlow.InterProceduralMain;

public class Edge {
	/*
	 * this class represents a edge in BSGraph
	 * we should have a start node and target node
	 * node can be different types, such as argument nodes, return nodes, method call node,
	 * source node, sink node
	 */
	
	Node source;
	Node target;
	boolean direct = true;
	
	public void setIndirect(){
		this.direct = false;
	}
	public Edge(Node src, Node target){
		this.source = src;
		this.target = target;
	}
	
	public Node getSrc(){
		return this.source;
	}
	
	public Node getTarget(){
		return this.target;
	}
	
	public boolean equals(Object o){
		if(!(o instanceof Edge)) return false;
		
		Edge e = (Edge)o;
		Node os = e.getSrc();
		Node ot = e.getTarget();
		
		return source.equals(e.getSrc())&&
				target.equals(e.getTarget());
	}
	
	public int hashCode(){
		return source.hashCode()+target.hashCode();
	}
	public String toString(){
		return source.getName()+"--->"+target.getName();
	}
	public void print(){
		if(direct){
			System.out.println("Direct flow:");
		}else{
			System.out.println("InDirect flow:");
		}
		source.print();
		System.out.println("------------>");
		target.print();
	}
	
	public void printFlow(){
		Stmt stmt = source.getStmt();
		if(stmt.containsInvokeExpr()){
			InvokeExpr ie = stmt.getInvokeExpr();
			String className = ie.getMethodRef().declaringClass().getName();
			String methodName = ie.getMethodRef().name();
			InterProceduralMain.ps.println("[APISource]:"+className+":"+methodName);
		}else if(stmt instanceof AssignStmt){
			Value value = ((AssignStmt)stmt).getRightOp();
			if( value instanceof StringConstant || value instanceof FieldRef)
				InterProceduralMain.ps.println("[ConstSource]:"+ value.toString());
			else 
				InterProceduralMain.ps.println("[NormalSource]:"+source.getName());
		}else{
			InterProceduralMain.ps.println("[NormalSource]:"+source.getName());
		}
		
		InterProceduralMain.ps.println("--------->");
		Stmt targetStmt = target.getStmt();
		if(targetStmt.containsInvokeExpr()){
			InvokeExpr ie = targetStmt.getInvokeExpr();
			String className = ie.getMethodRef().declaringClass().getName();
			String methodName = ie.getMethodRef().name();
			if(target instanceof SinkNode){
				if(!((SinkNode)target).isOriginal()){
					System.out.println("Not Original!");
				}
				if(((SinkNode)target).isNetworkSink()){
					InterProceduralMain.ps.println("[APISink]:(network)"+className+":"+methodName);
				}else{
					InterProceduralMain.ps.println("[APISink]:"+className+":"+methodName);
				}
			}else{
				InterProceduralMain.ps.println("[APISink]:"+className+":"+methodName);
			}
		}else{
			if(target instanceof SinkNode){
				if(((SinkNode)target).isNetworkSink()){
					InterProceduralMain.ps.println("[NormalSink]:(network)"+target.getName());
				}else{
					InterProceduralMain.ps.println("[NormalSink]:"+target.getName());
				}
			}else{
				InterProceduralMain.ps.println("[NormalSink]:"+target.getName());
			}
		}
	}

	public String generateFlowString() {
		StringBuffer buff = new StringBuffer();
		Stmt stmt = source.getStmt();
		
		if(stmt.containsInvokeExpr()){
			InvokeExpr ie = stmt.getInvokeExpr();
			String className = ie.getMethodRef().declaringClass().getName();
			String methodName = ie.getMethodRef().name();
			buff.append(className + ":" + methodName);
		}else if(stmt instanceof AssignStmt){
			Value value = ((AssignStmt)stmt).getRightOp();
			if( value instanceof StringConstant || value instanceof FieldRef)
				buff.append("[ConstSource]" + value.toString());
			else 
				buff.append(source.getName());
		}else{
			buff.append(source.getName());
		}
		
		buff.append("<->");
		Stmt targetStmt = target.getStmt();
		
		if(targetStmt.containsInvokeExpr()){
			InvokeExpr ie = targetStmt.getInvokeExpr();
			String className = ie.getMethodRef().declaringClass().getName();
			String methodName = ie.getMethodRef().name();
			if(target instanceof SinkNode){
				if(((SinkNode)target).isNetworkSink()){
					buff.append("[network]" + className + ":" + methodName);
				}else{
					buff.append(className + ":" + methodName);
				}
			}else{
				buff.append(className + ":" + methodName);
			}
		}else{
			if(target instanceof SinkNode){
				if(((SinkNode)target).isNetworkSink()){
					buff.append("[network]" + target.getName());
				}else{
					buff.append(target.getName());
				}
			}else{
				buff.append(target.getName());
			}
		}
		return buff.toString();
	}
}
