package edu.buffalo.cse.blueseal.blueseal;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;


import soot.Body;
import soot.Local;
import soot.MethodOrMethodContext;
import soot.PatchingChain;
import soot.Scene;
import soot.SceneTransformer;
import soot.SootClass;
import soot.SootField;
import soot.SootFieldRef;
import soot.SootMethod;
import soot.SootMethodRef;
import soot.Unit;
import soot.UnitBox;
import soot.Value;
import soot.ValueBox;
import soot.jimple.AssignStmt;
import soot.jimple.ClassConstant;
import soot.jimple.FieldRef;
import soot.jimple.IdentityStmt;
import soot.jimple.InstanceFieldRef;
import soot.jimple.InvokeExpr;
import soot.jimple.InvokeStmt;
import soot.jimple.Stmt;
import soot.jimple.StringConstant;
import soot.jimple.VirtualInvokeExpr;
import soot.jimple.toolkits.annotation.purity.DirectedCallGraph;
import soot.jimple.toolkits.callgraph.CallGraph;
import soot.jimple.toolkits.callgraph.Edge;
import soot.jimple.toolkits.callgraph.ReachableMethods;
import soot.toolkits.graph.ExceptionalUnitGraph;
import soot.toolkits.scalar.SimpleLiveLocals;
import soot.toolkits.scalar.SmartLocalDefs;
import soot.util.Chain;
import soot.util.queue.QueueReader;

public class BSShimpleTransformer extends SceneTransformer {
	private SootMethod method_;
	
	@Override
	protected void internalTransform(String arg0, Map arg1) {
		generateCVSet();
		// TODO Auto-generated method stub
		final CallGraph cg = Scene.v().getCallGraph();
		final ReachableMethods reader = 
				Scene.v().getReachableMethods();
		QueueReader<MethodOrMethodContext> readerIt = 
				reader.listener();
		while(readerIt.hasNext()){
			SootMethod method = readerIt.next().method();
			
			if(!(method.hasActiveBody()))
				continue;
			
			Body body = method.getActiveBody();
			ExceptionalUnitGraph eug = new ExceptionalUnitGraph(body);
			SmartLocalDefs localDefs = new SmartLocalDefs(eug, new SimpleLiveLocals(eug));
			
			for(Iterator unitIt = body.getUnits().iterator(); unitIt.hasNext();){
				Stmt stmt = (Stmt)unitIt.next();
				if(stmt.containsInvokeExpr()){
					SootMethodRef methodRef = stmt.getInvokeExpr().getMethodRef();		        	
					
					if(methodRef.declaringClass().getName().contains("ContentResolver")
							||methodRef.declaringClass().getName().contains("ContentProviderClient")){
						if(methodRef.name().contains("insert")
								||methodRef.name().contains("query")
								||methodRef.name().contains("update")){
							List<Unit> defs = localDefs.getDefsOfAt((Local) stmt.getUseBoxes().get(1).getValue(), stmt);

							if (defs.size() != 1) {
								Debug.println("defs", "more than one definition site!");
							}
							
							Stmt defStmt = (Stmt) defs.get(0);
							List<ValueBox> defBoxes = defStmt.getDefBoxes();

							 for (ValueBox defBox: defBoxes) {
								 System.out.println(defBox);
								 new MethodAnalysis(cg, method, defStmt, defBox.getValue());
							 }
						}
					}
					
				}
			}
		}
	}
	
	/*
	 * generate set of all class variables
	 */
	public void generateCVSet(){
		Map<Value, SootMethod> cvToMethod = new HashMap<Value, SootMethod>();
		Map<Value, Unit> cvToUnit = new HashMap<Value, Unit>();
		final ReachableMethods reader = 
				Scene.v().getReachableMethods();
		QueueReader<MethodOrMethodContext> readerIt = 
				reader.listener();
		
		while(readerIt.hasNext()){
			SootMethod method = (SootMethod) readerIt.next();
			
			if(!method.hasActiveBody()) continue;
			
			Body body = method.getActiveBody();
			ExceptionalUnitGraph eug = new ExceptionalUnitGraph(body);
			SmartLocalDefs localDefs = new SmartLocalDefs(eug, new SimpleLiveLocals(eug));
			
			for(Iterator unitIt = body.getUnits().iterator(); unitIt.hasNext();){
				Stmt stmt = (Stmt)unitIt.next();
				
				if(stmt instanceof AssignStmt){
					List<ValueBox> defBoxes = stmt.getDefBoxes();
				
					if(defBoxes.size()!=1) continue;
					
					if(defBoxes.get(0).getValue() instanceof FieldRef){
						cvToMethod.put(defBoxes.get(0).getValue(), method);
						cvToUnit.put(defBoxes.get(0).getValue(), (Unit)stmt);						
					}
					

				}
				
			}
		}
		//printing out results
		Debug.printMap("Map", cvToMethod);
		Debug.printMap("MapUnit", cvToUnit);
	}

}
