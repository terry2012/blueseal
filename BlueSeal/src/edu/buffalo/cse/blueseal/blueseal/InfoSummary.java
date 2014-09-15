package edu.buffalo.cse.blueseal.blueseal;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import soot.Body;
import soot.MethodOrMethodContext;
import soot.PatchingChain;
import soot.Scene;
import soot.SceneTransformer;
import soot.SootMethod;
import soot.SootMethodRef;
import soot.Unit;
import soot.Value;
import soot.jimple.InvokeExpr;
import soot.jimple.InvokeStmt;
import soot.jimple.VirtualInvokeExpr;
import soot.jimple.toolkits.callgraph.ReachableMethods;
import soot.util.queue.QueueReader;

public class InfoSummary extends SceneTransformer {

	private Set<SootMethod> contentProviderMeths_ = new HashSet<SootMethod>();

	
	public InfoSummary(){
		
	}
	
	public Set<SootMethod> getContentProviderMeth(){
		return this.contentProviderMeths_;
	}
	
	private void fetchContentProviderMeth(Set<SootMethod> methods){
		Set<SootMethod> reachableMethods = methods;
		for(SootMethod method:reachableMethods){
			
		}
	}
	public void getAllCPUnits(){
		Debug.printOb("In testing...");
		final ReachableMethods reader = 
				Scene.v().getReachableMethods();
		QueueReader<MethodOrMethodContext> readerIt = 
				reader.listener();
		while(readerIt.hasNext()){
			SootMethod method = readerIt.next().method();
			if(!(method.hasActiveBody())){
				continue;
			}
			Body body = method.getActiveBody();
			if(method.getName().contains("onCreate")){
			Debug.println("Body", body.toString());}
			
			PatchingChain<Unit> units = body.getUnits();
			for(Unit unit : units){
				Debug.println("UnitBox", unit.toString());
			}
		}

		
/*		
		final ReachableMethods rm = Scene.v().getReachableMethods();
	    QueueReader<MethodOrMethodContext> rmIt = rm.listener();
	    while (rmIt.hasNext()) 
	    {   
	      SootMethod method = rmIt.next().method();
	      if(!method.hasActiveBody()){
	    	  continue;
	      }
	      
	      //take care of virtual invoke methods
	      Body body = method.getActiveBody();
	      if(method.getName().contains("onCreate")){
	      Debug.println("SSSS", body.toString());}
	      PatchingChain<Unit> unitList = body.getUnits();
	      for(Unit unit: unitList) {
	    	  if(!(method.getName().contains("onCreate"))){
	    		  continue;
	    	  }
	    	Debug.println("unit" ,unit.toString());
	        BSStmntSwitch stSwitch = new BSStmntSwitch(unit);
	        if(stSwitch.isInvokeStmnt()){
	        	InvokeExpr unitInvokeExpr = ((InvokeStmt)unit).getInvokeExpr();
	        	SootMethodRef methodRef = unitInvokeExpr.getMethodRef();
	        	//Debug.println("Method", method.toString());
	        	if(methodRef.declaringClass().getName().contains("ContentResolver")||
						methodRef.declaringClass().getName().contains("ContentProviderClient")){
					if(methodRef.name().contains("insert")||
							methodRef.name().contains("query")||
							methodRef.name().contains("update")){
						Debug.printOb("insert invoke...");
						List<Value> values = unitInvokeExpr.getArgs();
						if(values.size() > 1){
							Value firstPara = unitInvokeExpr.getArg(1);
							Debug.printOb(firstPara.toString());
						}else{
							Debug.printOb("no args!");
						}
					}
				}
	        }
	      }
	    }*/
		
		
		
		/*for(SootMethod m: methods){
			if(!m.hasActiveBody()){
		    	  continue;
		      }
			Debug.printOb(m.toString());
			Body body = m.getActiveBody();
		      PatchingChain<Unit> unitList = body.getUnits();
		      for(Unit unit: unitList) {
		    	  if(!(m.getName().contains("onCreate"))){
		    		  continue;
		    	  }
		    	  Debug.println("Unit", unit.toString());
		        BSStmntSwitch stSwitch = new BSStmntSwitch(unit);
		        if(stSwitch.isInvokeStmnt()){
		        	InvokeExpr unitInvokeExpr = ((InvokeStmt)unit).getInvokeExpr();
		        	//Debug.printOb(unitInvokeExpr.toString());
		        	SootMethodRef method = unitInvokeExpr.getMethodRef();
		        	//Debug.println("Method", method.toString());
		        	if(method.declaringClass().getName().contains("ContentResolver")||
							method.declaringClass().getName().contains("ContentProviderClient")){
						if(method.name().contains("insert")||
								method.name().contains("query")||
								method.name().contains("update")){
							//Debug.printOb("insert invoke...");
							List<Value> values = unitInvokeExpr.getArgs();
							if(values.size() > 1){
								Value firstPara = unitInvokeExpr.getArg(1);
								Debug.printOb(firstPara.toString());
							}else{
								Debug.printOb("no args!");
							}
							}
						}
					}
		        }
		}*/
		
	}

	@Override
	protected void internalTransform(String arg0, Map arg1) {
		// TODO Auto-generated method stub
	}
	
}