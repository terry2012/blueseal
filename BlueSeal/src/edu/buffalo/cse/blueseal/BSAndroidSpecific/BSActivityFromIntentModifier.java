package edu.buffalo.cse.blueseal.BSAndroidSpecific;

import java.util.Iterator;
import java.util.Map;

import soot.Body;
import soot.Local;
import soot.PatchingChain;
import soot.Scene;
import soot.SootClass;
import soot.SootMethod;
import soot.Unit;
import soot.Value;
import soot.jimple.AssignStmt;
import soot.jimple.ClassConstant;
import soot.jimple.InvokeExpr;
import soot.jimple.InvokeStmt;
import soot.jimple.Jimple;
import soot.jimple.NewExpr;
import soot.jimple.Stmt;
import soot.jimple.toolkits.callgraph.CallGraph;
import soot.jimple.toolkits.callgraph.Edge;
import soot.toolkits.graph.ExceptionalUnitGraph;
import soot.toolkits.scalar.ArraySparseSet;
import soot.toolkits.scalar.SimpleLocalDefs;
import soot.util.Chain;
import edu.buffalo.cse.blueseal.BSCallgraph.BSCallGraphAugmentor;

public class BSActivityFromIntentModifier {
	private Stmt stmt = null;
	private SootMethod method = null;
	private CallGraph callgraph= null;
	private SimpleLocalDefs sld = null;
	private ExceptionalUnitGraph eug = null;
	private PatchingChain<Unit> units = null;
	private Body body = null;
	
	public BSActivityFromIntentModifier(Stmt stmt, SootMethod method, CallGraph cg){
		this.stmt = stmt;
		this.method = method;
		callgraph = cg;
	}
	
	public void modify(){	
		method.retrieveActiveBody();
		
  	if(!method.hasActiveBody() ||
  			!method.getDeclaringClass().isApplicationClass()) return;
  	
		body = method.getActiveBody();
  	eug = new ExceptionalUnitGraph(body);
  	units = body.getUnits();
  	sld = new SimpleLocalDefs(eug);
  	System.out.println("[BlueSeal]:resolve intent started activity invokes @stmt:"+stmt.toString());
  	SootClass activitySootClass = retrieveStartedActivityClass();
  	
  	if(activitySootClass != null){
  		addNewActivityEdges(activitySootClass);
  	}
	}

	private void addNewActivityEdges(SootClass activitySootClass) {
		boolean replaced = false;
		Chain<Local> locals = body.getLocals();
		Local classObject = Jimple.v().newLocal("$r"+locals.size(), 
				activitySootClass.getType());
		locals.add(classObject);		
		NewExpr newExpr = Jimple.v().newNewExpr(activitySootClass.getType());
		AssignStmt assignStmt = Jimple.v().newAssignStmt(classObject, newExpr);
		units.insertBefore(assignStmt, stmt);
		InvokeStmt onCreateInvoke = null;
		InvokeStmt onStartInvoke = null;
		//check two methods, onCreate and onStart
		
		if(activitySootClass.declaresMethod("void onStart()")){
			//TODO: add arguments list
			SootMethod onStartMethod = activitySootClass.getMethod(
					"void onStart()");
			InvokeExpr invokeExpr = Jimple.v().newSpecialInvokeExpr(classObject, 
					onStartMethod.makeRef());
			onStartInvoke = Jimple.v().newInvokeStmt(invokeExpr);
			units.insertAfter(onStartInvoke, assignStmt);
			callgraph.addEdge(new Edge(method, onStartInvoke, onStartMethod));
			replaced = true;
		}
		
		if(activitySootClass.declaresMethod("void onCreate(android.os.Bundle)")){
			SootMethod onCreateMethod = activitySootClass.getMethod("void onCreate(android.os.Bundle)");
			InvokeExpr invokeExpr = Jimple.v().newSpecialInvokeExpr(classObject, 
					onCreateMethod.makeRef());
			onCreateInvoke = Jimple.v().newInvokeStmt(invokeExpr);
			units.insertAfter(onCreateInvoke, assignStmt);
			callgraph.addEdge(new Edge(method, onCreateInvoke, onCreateMethod));
			replaced = true;
		}

		if(replaced)
			units.remove(stmt);
		
	}

	private SootClass retrieveStartedActivityClass() {
		SootClass sootClass = null;
		
		if(!BSCallGraphAugmentor.methodSummary.containsKey(method)) return null;
		
		Map<Unit, ArraySparseSet> summaryFromFirstRoundAnalysis = BSCallGraphAugmentor.methodSummary.get(method);
		
		if(!summaryFromFirstRoundAnalysis.containsKey(stmt)) return null;
		
		ArraySparseSet flowIntoThis = summaryFromFirstRoundAnalysis.get(stmt);
		
		for(Iterator it = flowIntoThis.iterator(); it.hasNext();){
			Stmt flowStmt = (Stmt) it.next();
			
			if(flowStmt instanceof AssignStmt){
				Value rightOp = ((AssignStmt)flowStmt).getRightOp();
				
				if(rightOp instanceof ClassConstant){
					String classString = ((ClassConstant)rightOp).getValue();
					classString = classString.replace('/', '.');
					sootClass = Scene.v().getSootClass(classString);
				}
			}
		}
		return sootClass;
	}
}
