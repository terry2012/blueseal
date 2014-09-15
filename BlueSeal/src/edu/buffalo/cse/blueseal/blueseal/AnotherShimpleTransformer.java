package edu.buffalo.cse.blueseal.blueseal;

import java.util.Iterator;
import java.util.List;
import java.util.Map;

import soot.Body;
import soot.Local;
import soot.MethodOrMethodContext;
import soot.PatchingChain;
import soot.Scene;
import soot.SceneTransformer;
import soot.SootMethod;
import soot.SootMethodRef;
import soot.Unit;
import soot.ValueBox;
import soot.jimple.Stmt;
import soot.jimple.toolkits.callgraph.CallGraph;
import soot.jimple.toolkits.callgraph.Edge;
import soot.jimple.toolkits.callgraph.ReachableMethods;
import soot.toolkits.graph.ExceptionalUnitGraph;
import soot.toolkits.scalar.CombinedAnalysis;
import soot.toolkits.scalar.LocalDefs;
import soot.toolkits.scalar.SimpleLiveLocals;
import soot.toolkits.scalar.SimpleLocalUses;
import soot.toolkits.scalar.SmartLocalDefs;
import soot.toolkits.scalar.UnitValueBoxPair;
import soot.util.queue.QueueReader;

public class AnotherShimpleTransformer extends SceneTransformer {

	@Override
	protected void internalTransform(String arg0, Map arg1) {
		CallGraph cg = Scene.v().getCallGraph();
		ReachableMethods reachableMethods = Scene.v().getReachableMethods();
		QueueReader<MethodOrMethodContext> readerIt = Scene.v().getReachableMethods().listener();
		
		while (readerIt.hasNext()) {
			SootMethod method = readerIt.next().method();

			Debug.println("ReachableMethods", method.toString());
			if (!method.hasActiveBody())
				continue;

			Body body = method.getActiveBody();
			ExceptionalUnitGraph eug = new ExceptionalUnitGraph(body);
			SmartLocalDefs localDefs = new SmartLocalDefs(eug, new SimpleLiveLocals(eug));
			SimpleLocalUses combinedDefs = new SimpleLocalUses(eug, localDefs); // used to find local references of variables
			
			
			for (Iterator<Unit> unitIt = body.getUnits().iterator(); unitIt.hasNext();) {
				Stmt stmt = (Stmt) unitIt.next();
				Debug.println("Debug ", "printing units " + stmt.toString());
				
				if (stmt.containsInvokeExpr()) {
					SootMethodRef methodRef = stmt.getInvokeExpr().getMethodRef();
					
					if (methodRef.declaringClass().getName().contains("ContentResolver")
							|| methodRef.declaringClass().getName().contains("ContentProviderClient")) {
						if (methodRef.name().contains("insert")
								|| methodRef.name().contains("query")
								|| methodRef.name().contains("update")) {
							System.out.println(stmt.getUseBoxes().get(0).getValue());
							System.out.println(stmt.getUseBoxes().get(1).getValue());
							List<Unit> defs = localDefs.getDefsOfAt((Local) stmt.getUseBoxes().get(1).getValue(), stmt);
							
							
							if (defs.size() != 1) {
								Debug.println("defs", "more than one definition site!");
							}
				
							Stmt defStmt = (Stmt) defs.get(0);
							System.out.println(defStmt);
							for (Iterator<ValueBox> valueBoxIt = defStmt.getUseBoxes().iterator(); valueBoxIt.hasNext();) {
								ValueBox valueBox = valueBoxIt.next();
								System.out.println(valueBox);
								Debug.println("Content providers found "," " +  valueBox);
							}
						}
					}
					
					/*Code for handling intents*/
					if (methodRef.declaringClass().getName().contains("android.content.Intent")
							|| methodRef.declaringClass().getName().contains("android.content.Intent")) {
						Debug.println("Intent Debug ", "Intent class" + methodRef.declaringClass().getName().toString());
						Debug.println("Intent Debug ", "Intent method" + methodRef.name().toString());
						
							if (methodRef.name().contains("putExtra")) {
							Debug.println("Intent Debug ", "INSIDE putExtara method");
							Debug.println("Intent Debug ", "arguments "+ stmt.getUseBoxes().get(2).getValue().toString());
							Debug.println("Intent Debug ", "INSIDE putExtara method printing unit " + stmt.toString());
							Debug.println("defs", "DEF BOXES " + stmt.getDefBoxes().toString() + "END");
							if(!stmt.getUseBoxes().get(2).getValue().toString().contains("\""))
							{
								//TODO: setdata, put*Extra, Intent, Replace, Remove
								List<Unit> defs = localDefs.getDefsOfAt((Local) stmt.getUseBoxes().get(2).getValue(), stmt);
										if (defs.size() != 1) {
									Debug.println("defs", "more than one definition site!");
									}
										Stmt defStmt = (Stmt) defs.get(0);
										System.out.println(defStmt);
										for (Iterator<ValueBox> valueBoxIt = defStmt.getUseBoxes().iterator(); valueBoxIt.hasNext();) {
											ValueBox valueBox = valueBoxIt.next();
											//System.out.println(valueBox);
											Debug.println("Intent Debug ","Intent local definition"+  valueBox);
										}
									
										Debug.println("Intent Debug ","*****************************" + stmt.getUseBoxes().get(0).getValue().toString());
										List<Unit> defs1 = localDefs.getDefsOfAt((Local) stmt.getUseBoxes().get(0).getValue(), stmt);
										if (defs1.size() != 1) {
											Debug.println("defs", "more than one definition site!");
										}
										
										
										Stmt defStmt1 = (Stmt) defs1.get(0);
										System.out.println(defStmt1);
										for (Iterator<ValueBox> valueBoxIt = defStmt1.getUseBoxes().iterator(); valueBoxIt.hasNext();) {
											ValueBox valueBox = valueBoxIt.next();
											//System.out.println(valueBox);
											Debug.println("Intent Debug ","Intent local definition"+  valueBox);
										}
										
										/*getting all the local references of intent variable and finding the class to which the intent is being attached*/
										List<UnitValueBoxPair> localReferences  = combinedDefs.getUsesOf(defs1.get(0));
										for(Iterator<UnitValueBoxPair> localRefIterator = localReferences.iterator(); localRefIterator.hasNext();)
										{
											UnitValueBoxPair reference = localRefIterator.next();
											
											Stmt ref = (Stmt)reference.getUnit();
											Debug.println("Intent Debug ","Local references " + ref.toString());
											//Finding constructor
											if (ref.toString().contains("<android.content.Intent: void <init>(android.content.Context,java.lang.Class)>"))
											{
													Debug.println("Intent Debug ","Class to which intent is attached " + 
																  localReferences.get(0).getUnit().getUseBoxes().get(2).toString());
											}
											
										}
										
										
							}
//				

							
						}
					
					
				
					}
	
				}
			}
		}
	}
}

