package edu.buffalo.cse.blueseal.BSFlow;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import soot.Body;
import soot.Local;
import soot.MethodOrMethodContext;
import soot.PatchingChain;
import soot.Scene;
import soot.SootClass;
import soot.SootFieldRef;
import soot.SootMethod;
import soot.Unit;
import soot.Value;
import soot.jimple.AssignStmt;
import soot.jimple.FieldRef;
import soot.jimple.InvokeExpr;
import soot.jimple.NullConstant;
import soot.jimple.ParameterRef;
import soot.jimple.RetStmt;
import soot.jimple.ReturnStmt;
import soot.jimple.StaticFieldRef;
import soot.jimple.Stmt;
import soot.jimple.ThisRef;
import soot.jimple.VirtualInvokeExpr;
import soot.jimple.internal.JIdentityStmt;
import soot.jimple.internal.JNewExpr;
import soot.jimple.toolkits.callgraph.CallGraph;
import soot.jimple.toolkits.callgraph.Edge;
import soot.jimple.toolkits.callgraph.ReachableMethods;
import soot.toolkits.graph.ExceptionalUnitGraph;
import soot.toolkits.scalar.SimpleLiveLocals;
import soot.toolkits.scalar.SimpleLocalUses;
import soot.toolkits.scalar.SmartLocalDefs;
import soot.util.queue.QueueReader;

public class AsyncTaskResolver {
	
	private static int countControl = 0;
	private SootMethod method_;
	private Stmt stmt_;
	private CallGraph cg_;
	private SootClass asyncTaskClass;
	private List<SootClass> asyncSubClass =  new ArrayList<SootClass>();

	public AsyncTaskResolver(SootMethod method, Stmt stmt, CallGraph cg) {
		this.method_ = method;
		this.stmt_ = stmt;
		this.cg_ = cg;
		resolve();
	}
	
	//this constructor is for handler resolver
	public AsyncTaskResolver(SootMethod method, Stmt stmt, CallGraph cg,
			boolean handlerResolver) {
		this.method_ = method;
		this.stmt_ = stmt;
		this.cg_ = cg;
		if(handlerResolver){
			handlerResolve();
		}else{
			resolve();
		}
	}
	
	//this constructor is used to resolver type of given variable in soot
	public AsyncTaskResolver(Value var, SootMethod method, Stmt stmt, CallGraph cg){
		this.method_ = method;
		this.stmt_ = stmt;
		this.cg_ = cg;
		resolveVarType(var);
	}

	private void resolveVarType(Value var) {
		localCheck(var, stmt_, method_);
		
	}

	private void handlerResolve() {
		InvokeExpr invoke = stmt_.getInvokeExpr();
        SootClass asyncTaskSuperClass = Scene.v().getSootClass("android.os.Handler");
        
		if(invoke instanceof VirtualInvokeExpr){
			VirtualInvokeExpr vie = (VirtualInvokeExpr)invoke;
			Value val = vie.getBase();
			SootClass sc = vie.getMethodRef().declaringClass();

	        if (sc.equals(asyncTaskSuperClass))
	        	localCheck(val, stmt_, method_);
	        else{
	        	asyncTaskClass = sc;
	        	asyncSubClass.add(sc);
	        }
		}
		
	}

	public List<SootClass> getAsyncTaskClasses(){
		return this.asyncSubClass;
	}

	private void resolve() {
		InvokeExpr invoke = stmt_.getInvokeExpr();
        SootClass asyncTaskSuperClass = Scene.v().getSootClass("android.os.AsyncTask");

		if(invoke instanceof VirtualInvokeExpr){
			VirtualInvokeExpr vie = (VirtualInvokeExpr)invoke;
			Value val = vie.getBase();
			SootClass sc = vie.getMethodRef().declaringClass();

	        if (!sc.declaresMethod("java.lang.Object doInBackground(java.lang.Object[])")
	                || sc.equals(asyncTaskSuperClass))
	        	localCheck(val, stmt_, method_);
	        else{
	        	asyncTaskClass = sc;
	        	asyncSubClass.add(sc);
	        }
		}
	}

	public void localCheck(Value val, Stmt stmt, SootMethod method) {
		if(!SootMethodFilter.want(method)){
			return;
		}
		countControl++;
		if(countControl > 30) return;
		
		if(!method.hasActiveBody()) return;
		
		Body body = method.retrieveActiveBody();
		ExceptionalUnitGraph eug = new ExceptionalUnitGraph(body);
		SmartLocalDefs localDefs = new SmartLocalDefs(eug, new SimpleLiveLocals(eug));
		SimpleLocalUses combinedDefs = new SimpleLocalUses(eug, localDefs);
		
		//TODO: check if the value is not local, should we check it or not
		if(!(val instanceof Local)) return;
		//get all the local definition for given value
		List<Unit> defs = localDefs.getDefsOfAt((Local)val, stmt);
		for(Unit def : defs){
			if(def instanceof AssignStmt){
				Value declare = ((AssignStmt)def).getRightOp();
				//defined locally
				if(declare instanceof JNewExpr){
					JNewExpr newExpr = (JNewExpr)declare;
					asyncTaskClass = newExpr.getBaseType().getSootClass();
					asyncSubClass.add(newExpr.getBaseType().getSootClass());
				}
				
				//returned by another method
				else if( ((AssignStmt) def).containsInvokeExpr()){
					SootMethod sm = ((AssignStmt)def).getInvokeExpr().getMethod();
					//find the return stmt
					if(!sm.hasActiveBody()) continue;
					Body smBody = sm.getActiveBody();
					PatchingChain<Unit> units = smBody.getUnits();
					for(Unit unit : units){
						if(unit instanceof ReturnStmt){
							Value retVal = ((ReturnStmt)unit).getOp();
							localCheck(retVal, (Stmt)unit, sm);
						}
					}
				}
				
				//last one, assigned from a class variable
				//this one, we might wan to just use chex style, since trace-back approach is similar to CHEX
				else if(declare instanceof FieldRef){
					resovleCV(declare, (Stmt)def, method);
				}
				
				//if the variable is assigned from another local variable
				else if(declare instanceof Local){
					localCheck(declare, (Stmt)def, method);
				}
			}
			
			//assigned from an argument
			else if(def instanceof JIdentityStmt){
				JIdentityStmt ident = (JIdentityStmt)def;
				Value paraVal = ident.getRightOp();
				int index;
				//TODO: this might not be a parameterRef, instead it's thisRef type
				// exceptions thrown out double-check
				if(paraVal instanceof ParameterRef){
					index = ((ParameterRef)paraVal).getIndex();
				}else {
					continue;
				}
					
				Iterator<Edge> edges = cg_.edgesInto(method);
				for(Iterator it = edges;it.hasNext();){
					Edge edge = (Edge)it.next();
					MethodOrMethodContext pred = edge.getSrc();
					SootMethod predMethod = pred.method();
					
					//find the invokeStmt
					Body predBody = predMethod.getActiveBody();
					PatchingChain<Unit> predUnits = predBody.getUnits();
					for(Unit unit : predUnits){
						if(!((Stmt)unit).containsInvokeExpr()) continue;
						
						SootMethod invokeM = ((Stmt)unit).getInvokeExpr().getMethod();
						if(!invokeM.getName().equals(method.getName())
								|| !invokeM.getDeclaringClass().getName().equals(method.getDeclaringClass().getName())
								|| !(invokeM.getParameterCount()==method.getParameterCount()))
							continue;
						//TODO: double-check here, exceptions of getting Argument
						Value param = ((Stmt)unit).getInvokeExpr().getArg(index);
						localCheck(param, (Stmt)unit, predMethod);
					}
				}
			}
			

		}
		
	}

	private void resovleCV(Value cv, Stmt stmt, SootMethod method) {
		if(cv instanceof FieldRef){
			SootFieldRef fr = ((FieldRef) cv).getFieldRef();
			
			ReachableMethods rms = Scene.v().getReachableMethods();
			QueueReader<MethodOrMethodContext> rmIt = rms.listener();
			
			while(rmIt.hasNext()){
				SootMethod sootMethod = rmIt.next().method();
				if(!sootMethod.hasActiveBody()) continue;
				
				Body body = sootMethod.getActiveBody();
				PatchingChain<Unit> units = body.getUnits();
				for(Unit unit : units){
					if(!(unit instanceof AssignStmt)) continue;
					
					Value leftVal = ((AssignStmt)unit).getLeftOp();
					
					if(!(leftVal instanceof FieldRef)) continue;
					
					SootFieldRef sfr = ((FieldRef)leftVal).getFieldRef();
					
					if(!(sfr.declaringClass().getName().equals(fr.declaringClass().getName()))
							||!(sfr.name().equals(fr.name()))) continue;
					
					Value rightVal = ((AssignStmt)unit).getRightOp();
					if(rightVal instanceof JNewExpr){
						JNewExpr newExpr = (JNewExpr)rightVal;
						asyncTaskClass = newExpr.getBaseType().getSootClass();
						asyncSubClass.add(newExpr.getBaseType().getSootClass());
					}else{
						localCheck(rightVal, (Stmt)unit, sootMethod);
					}
				}
			}
		}
		
	}
}
