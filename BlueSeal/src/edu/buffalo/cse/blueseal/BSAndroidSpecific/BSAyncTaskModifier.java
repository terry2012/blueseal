/*
 * this class is to handle asyncTask cases, 
 * insert real asyncTask methods to callgraph
 * 
 * this takes care of one asyncTask at one time
 */
package edu.buffalo.cse.blueseal.BSAndroidSpecific;

import java.util.ArrayList;
import java.util.List;

import edu.buffalo.cse.blueseal.BSFlow.AsyncTaskResolver;
import soot.Body;
import soot.Local;
import soot.Modifier;
import soot.PatchingChain;
import soot.RefType;
import soot.Scene;
import soot.SootClass;
import soot.SootMethod;
import soot.SootMethodRef;
import soot.Unit;
import soot.Value;
import soot.ValueBox;
import soot.jimple.InstanceInvokeExpr;
import soot.jimple.InvokeExpr;
import soot.jimple.Jimple;
import soot.jimple.NullConstant;
import soot.jimple.SpecialInvokeExpr;
import soot.jimple.Stmt;
import soot.jimple.toolkits.callgraph.CallGraph;
import soot.jimple.toolkits.callgraph.Edge;
import soot.util.Chain;

public class BSAyncTaskModifier {
	
	private Stmt stmt = null;
	private SootMethod method = null;
	private CallGraph callgraph= null;

	public BSAyncTaskModifier(SootMethod m, Stmt s, CallGraph cg){
		stmt = s;
		method = m;
		callgraph = cg;
	}
	
	public void modify(){
    SootClass asyncTaskClass = stmt.getInvokeExpr().getMethodRef().declaringClass();
    SootClass asyncTaskSuperClass = Scene.v().getSootClass("android.os.AsyncTask");
    
    Body body = method.getActiveBody();
    PatchingChain<Unit> units = body.getUnits();
    
    ArrayList<SootClass> classesToConnect = new ArrayList<SootClass>();
    /*
     * testing class hierarchy resolver
     */
    AsyncTaskResolver atr = new AsyncTaskResolver(method, stmt, callgraph);
    classesToConnect = (ArrayList<SootClass>) atr.getAsyncTaskClasses();
    
//    if (!asyncTaskClass.declaresMethod("java.lang.Object doInBackground(java.lang.Object[])")
//            || asyncTaskClass.equals(asyncTaskSuperClass)) {
//        /*
//         * TODO: We need to trace doInBackground properly in the class hierarchy to handle this
//         * case. Right now, it's CHEX-style.
//         */
//        System.err.println("Warning: CgTansformer goes CHEX for " + stmt.toString());
//
//        Hierarchy hierarchy = scene.getActiveHierarchy();
//        classesToConnect.addAll(hierarchy.getSubclassesOf(asyncTaskSuperClass));
//        
//    } else {
//        classesToConnect.add(asyncTaskClass);
//    }
    
    for (SootClass subClass: classesToConnect) {
//        if (subClass.toString().startsWith("android."))
//            continue;
//        if (!subClass.declaresMethod("java.lang.Object doInBackground(java.lang.Object[])"))
//            continue;

    		//filter out framework classes
    		if(!subClass.isApplicationClass()) continue;
    		
        Local retLocal = null;

	      if (subClass.declaresMethod("void onPreExecute()")) {
	          insertOnPreExecute(method, stmt, callgraph, subClass, units);
	      }
	
	      // This should always be true
	      if (subClass.declaresMethod("java.lang.Object doInBackground(java.lang.Object[])")) {
	          retLocal = insertDoInBackground(method, stmt, callgraph, subClass, body, units);
	      }
	
	      if (subClass.declaresMethod("void onProgressUpdate(java.lang.Object[])")) {
	          insertOnProgressUpdate(method, callgraph, subClass);
	      }
	
	      if (subClass.declaresMethod("void onPostExecute(java.lang.Object)")) {
	          insertOnPostExecute(method, stmt, callgraph, subClass, units, retLocal);
	      }
	
	      if (subClass.declaresMethod("void onCancelled(java.lang.Object)")) {
	          insertOnCancelled(method, stmt, callgraph, subClass, units, retLocal);
	      }
    }

    units.remove(stmt);
  
	}

	/*
	 * insert AsyncTask OnPreExecute method
	 */
  private void insertOnPreExecute(SootMethod method, Stmt stmt, CallGraph cg,
      SootClass asyncTaskClass, PatchingChain<Unit> units) {
	  // Insert a call to onPreExecute()
	  SootMethod onPreExecuteMethod = asyncTaskClass.getMethod("void onPreExecute()");
	  onPreExecuteMethod.retrieveActiveBody();
	  
	  if(stmt.containsInvokeExpr()) return;
	  
	  InvokeExpr invokeExpr = stmt.getInvokeExpr();
	  
	  if(!(invokeExpr instanceof InstanceInvokeExpr)) return;
	  
	  Value base = ((InstanceInvokeExpr)invokeExpr).getBase();
	  List<ValueBox> vbList = stmt.getUseBoxes();
	  SpecialInvokeExpr newExpr = 
	          Jimple.v().newSpecialInvokeExpr((Local) base,
	                  onPreExecuteMethod.makeRef());
	  Stmt newInvokeStmt = Jimple.v().newInvokeStmt(newExpr);
	  units.insertBefore(newInvokeStmt, stmt);
	  cg.addEdge(new Edge(method, newInvokeStmt, onPreExecuteMethod));
  
  }
  
  /*
   * insert AsyncTask doInBackground method
   */
  private Local insertDoInBackground(SootMethod method, Stmt stmt, CallGraph cg,
      SootClass asyncTaskClass, Body body, PatchingChain<Unit> units) {
	  InvokeExpr invokeExpr = stmt.getInvokeExpr();
	  
	  if(!(invokeExpr instanceof InstanceInvokeExpr)) return null;
	  
	  Value baseValue = ((InstanceInvokeExpr)invokeExpr).getBase();
	  List<Value> args = ((InstanceInvokeExpr)invokeExpr).getArgs();
	  
	  SootMethod doInBackgroundMethod = 
        asyncTaskClass.getMethod("java.lang.Object doInBackground(java.lang.Object[])");
	  if(!doInBackgroundMethod.isConcrete()) return null;
	  	  
	  doInBackgroundMethod.retrieveActiveBody();
	
	  Chain<Local> locals = body.getLocals();
	  Local retLocal = Jimple.v().newLocal("$r" + locals.size(), RefType.v("java.lang.Object"));
	  locals.add(retLocal);
	  
	  SpecialInvokeExpr newExpr = 
	          Jimple.v().newSpecialInvokeExpr((Local) baseValue,
	                  doInBackgroundMethod.makeRef(), args);
	  Stmt newAssignStmt = Jimple.v().newAssignStmt(retLocal, newExpr); 
	  
	  units.insertBefore(newAssignStmt, stmt);
	  cg.addEdge(new Edge(method, newAssignStmt, doInBackgroundMethod));
	  
	  return retLocal;
  }
  
  /*
   * insert AsyncTask onProgressUpdate method
   */
  private void insertOnProgressUpdate(SootMethod method, CallGraph cg, SootClass asyncTaskClass) {
    SootMethod onProgressUpdateMethod = 
        asyncTaskClass.getMethod("void onProgressUpdate(java.lang.Object[])");
    
    if(!onProgressUpdateMethod.isConcrete()) return;
    
    onProgressUpdateMethod.retrieveActiveBody();
    
    if(!asyncTaskClass.declaresMethod("java.lang.Object doInBackground(java.lang.Object[])")) return;
    SootMethod doInBackgroundMethod =
            asyncTaskClass.getMethod("java.lang.Object doInBackground(java.lang.Object[])");
    
    if(!doInBackgroundMethod.isConcrete()) return;

    SootMethod actualDoInBackgroundMethod = null;
    for (Unit unit: doInBackgroundMethod.retrieveActiveBody().getUnits()) {
        Stmt tmpStmt = (Stmt) unit;
        
        if (!tmpStmt.containsInvokeExpr())
            continue;
        InvokeExpr invokeExpr = (InvokeExpr) tmpStmt.getInvokeExpr();
        SootMethodRef methodRef = invokeExpr.getMethodRef();
        
        if (methodRef.name().contains("doInBackground")) {
            actualDoInBackgroundMethod = invokeExpr.getMethod();
            break;
        }
    }

    if (actualDoInBackgroundMethod == null)
        // This should not happen, but nothing can be done anyway.
        return;
    
    if(!actualDoInBackgroundMethod.isConcrete()) return;
    
    for (Unit unit: actualDoInBackgroundMethod.retrieveActiveBody().getUnits()) {
        Stmt tmpStmt = (Stmt) unit;
        
        if (!tmpStmt.containsInvokeExpr())
            continue;
        
        InvokeExpr invokeExpr = (InvokeExpr) tmpStmt.getInvokeExpr();
        SootMethodRef methodRef = invokeExpr.getMethodRef();
        
        if(!(invokeExpr instanceof InstanceInvokeExpr)) continue;
        Value base = ((InstanceInvokeExpr)invokeExpr).getBase();
        List<Value> newValues = ((InstanceInvokeExpr)invokeExpr).getArgs();
        
        if (methodRef.name().contains("publishProgress")) {

            SpecialInvokeExpr newExpr = 
                    Jimple.v().newSpecialInvokeExpr((Local) base,
                            onProgressUpdateMethod.makeRef(), newValues);
            Stmt newInvokeStmt = Jimple.v().newInvokeStmt(newExpr);
            actualDoInBackgroundMethod.getActiveBody().getUnits().swapWith(tmpStmt, newInvokeStmt);
            cg.addEdge(new Edge(method, newInvokeStmt, onProgressUpdateMethod));
            
            break;
        }
    }
  }
  
  /*
   * insert AsyncTask onPostExecute method
   */
  private void insertOnPostExecute(SootMethod method, Stmt stmt, CallGraph cg,
      SootClass asyncTaskClass, PatchingChain<Unit> units, Local retLocal) {
	  SootMethod onPostExecuteMethod = 
	  		asyncTaskClass.getMethod("void onPostExecute(java.lang.Object)");
	  onPostExecuteMethod.retrieveActiveBody();
	  
	  Value arg = retLocal;
	  if (arg == null) {
	      /*
	       * TODO: This is probably not necessary if we trace doInBackground properly in the
	       * class hierarchy.
	       */
	      arg = NullConstant.v();
	  }
	  
	  if(!stmt.containsInvokeExpr()) return;
	  
	  InvokeExpr invokeExpr = stmt.getInvokeExpr();
	  
	  if(!(invokeExpr instanceof InstanceInvokeExpr)) return;
	  
	  Value base = ((InstanceInvokeExpr)invokeExpr).getBase();
	  SpecialInvokeExpr newExpr =  
	          Jimple.v().newSpecialInvokeExpr((Local) base,
	                  onPostExecuteMethod.makeRef(), arg);
	  Stmt newInvokeStmt = Jimple.v().newInvokeStmt(newExpr);
	  units.insertBefore(newInvokeStmt, stmt);
	  cg.addEdge(new Edge(method, newInvokeStmt, onPostExecuteMethod));

  }
  
  /*
   * insert asyncTask onCancelled method
   */
	private void insertOnCancelled(SootMethod method, Stmt stmt, CallGraph cg,
      SootClass asyncTaskClass, PatchingChain<Unit> units, Local retLocal) {
	  SootMethod onCancelledMethod = 
	  		asyncTaskClass.getMethod("void onCancelled(java.lang.Object)");
	  onCancelledMethod.retrieveActiveBody();
	  
	  Value arg = retLocal;
	  if (arg == null) {
	      /*
	       * TODO: This is probably not necessary if we trace doInBackground properly in the
	       * class hierarchy.
	       */
	      arg = NullConstant.v();
	  }
	  if(!stmt.containsInvokeExpr()) return;
	  
	  InvokeExpr invokeExpr = stmt.getInvokeExpr();
	  
	  if(!(invokeExpr instanceof InstanceInvokeExpr)) return;
	  
	  Value base = ((InstanceInvokeExpr)invokeExpr).getBase();
	  SpecialInvokeExpr newExpr = 
	          Jimple.v().newSpecialInvokeExpr((Local) base,
	                  onCancelledMethod.makeRef(), arg);
	  Stmt newInvokeStmt = Jimple.v().newInvokeStmt(newExpr);
	  units.insertBefore(newInvokeStmt, stmt);
	  cg.addEdge(new Edge(method, newInvokeStmt, onCancelledMethod));
	}
}
