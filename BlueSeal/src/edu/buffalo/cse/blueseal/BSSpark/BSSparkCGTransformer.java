package edu.buffalo.cse.blueseal.BSSpark;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import soot.Body;
import soot.MethodOrMethodContext;
import soot.PatchingChain;
import soot.Scene;
import soot.SceneTransformer;
import soot.SootMethod;
import soot.Unit;
import soot.jimple.InvokeExpr;
import soot.jimple.Stmt;
import soot.jimple.spark.SparkTransformer;
import soot.jimple.toolkits.callgraph.CallGraph;
import soot.jimple.toolkits.callgraph.Edge;
import soot.jimple.toolkits.callgraph.ReachableMethods;
import soot.util.queue.QueueReader;

public class BSSparkCGTransformer extends SceneTransformer {
	
	public CallGraph CGSpark=null;
	
	//application file location
	public String apkloc = null;
	//options for call graph transformation
	Map<String, String> opt = new HashMap<String, String>();
	
	public BSSparkCGTransformer(String application){
		apkloc = application;
	}
	
	public void initialize(){
		opt.put("enabled", "true");
	  opt.put("VTA","true");
	  opt.put("verbose","true");
	  opt.put("propagator","worklist");
	  opt.put("simple-edges-bidirectional","false");
	  opt.put("on-fly-cg","true");
	  opt.put("set-impl","double");
	  opt.put("double-set-old","hybrid");
	  opt.put("double-set-new","hybrid");
	  opt.put("dumpHTML","false");
	  opt.put("string-constants", "true");
	  opt.put("geom-pta", "true");
	  opt.put("geom-encoding", "geom");
	  opt.put("geom-worklist", "PQ");
	  opt.put("simplify-offline", "false");
	  opt.put("geom-runs", "2");
	}

	@Override
	protected void internalTransform(String arg0, Map<String, String> arg1){
		//set up arguments for transformation
		initialize();

		//do the transform
		SparkTransformer.v().transform("bsSpark", opt);
		
		//iterate all the stmts, add all leave nodes of callgraph
		CGSpark = Scene.v().getCallGraph();
		
		ReachableMethods reachable = Scene.v().getReachableMethods();
		QueueReader<MethodOrMethodContext> listener = reachable.listener();
		while(listener.hasNext()){
			MethodOrMethodContext method = listener.next();
			if(method.method().getDeclaringClass().isApplicationClass()
					&& method.method().isConcrete()){
				try{
					method.method().retrieveActiveBody();
				}catch(Exception e){
					
				}
				
				if(!method.method().hasActiveBody()) continue;
				
				Body body = method.method().getActiveBody();
				PatchingChain<Unit> units = body.getUnits();
				
				for(Iterator<Unit> it = units.iterator(); it.hasNext();){
					Stmt stmt = (Stmt)it.next();
					
					if(stmt.containsInvokeExpr()){
						InvokeExpr invokeExpr = stmt.getInvokeExpr();
						SootMethod invokeM = invokeExpr.getMethod();
						Edge newEdge = new Edge(method, stmt, invokeM);
						CGSpark.addEdge(newEdge);
					}
				}
			}
		}
	}

}
