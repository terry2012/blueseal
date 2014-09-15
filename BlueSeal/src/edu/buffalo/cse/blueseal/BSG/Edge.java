package edu.buffalo.cse.blueseal.BSG;

import soot.Value;
import soot.jimple.AssignStmt;
import soot.jimple.FieldRef;
import soot.jimple.InvokeExpr;
import soot.jimple.Stmt;
import soot.jimple.StringConstant;
import edu.buffalo.cse.blueseal.BSFlow.InterProceduralMain;
import edu.buffalo.cse.blueseal.blueseal.Debug;

public class Edge {
	/*
	 * this class represents a edge in BSGraph
	 * we should have a start node and target node
	 * node can be different types, such as argument nodes, return nodes, method call node,
	 * source node, sink node
	 */
	
	Node source;
	Node target;
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
		
		return source.getClass().equals(os.getClass())&&
				target.getClass().equals(ot.getClass())&&
				source.equals(e.getSrc())&&
				target.equals(e.getTarget());
	}
	
	public int hashCode(){
		return source.hashCode()+
				target.hashCode();
	}
	
	public void print(){
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
}
