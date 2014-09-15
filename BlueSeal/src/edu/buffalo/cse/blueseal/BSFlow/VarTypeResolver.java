package edu.buffalo.cse.blueseal.BSFlow;

import java.util.List;

import soot.Body;
import soot.Local;
import soot.SootMethod;
import soot.Unit;
import soot.Value;
import soot.jimple.Stmt;
import soot.jimple.toolkits.callgraph.CallGraph;
import soot.toolkits.graph.ExceptionalUnitGraph;
import soot.toolkits.scalar.SimpleLiveLocals;
import soot.toolkits.scalar.SmartLocalDefs;

public class VarTypeResolver {
	private Value var_;
	private SootMethod method_;
	private Stmt stmt_;
	private CallGraph cg_;

	public VarTypeResolver(Value var, SootMethod method, Stmt stmt, CallGraph cg){
		this.var_ = var;
		this.method_ = method;
		this.stmt_ = stmt;
		this.cg_ = cg;
		resolve();
	}
	
	public void resolve(){
		if(!method_.hasActiveBody()) return;
		
		if(var_ instanceof Local){
			resolveLocalVar((Local)var_, method_, stmt_);
		}

	}

	private void resolveLocalVar(Local var, SootMethod method, Stmt stmt) {
		if(!method_.hasActiveBody()) return;
		
		Body body = method_.retrieveActiveBody();
		ExceptionalUnitGraph eug = new ExceptionalUnitGraph(body);
		SmartLocalDefs localDefs = new SmartLocalDefs(eug, new SimpleLiveLocals(eug));
		
		List<Unit> defs = localDefs.getDefsOfAt(var, stmt);
		
		
	}
}
