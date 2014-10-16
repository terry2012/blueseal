package edu.buffalo.cse.blueseal.BSFlow;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import edu.buffalo.cse.blueseal.blueseal.EntryPointsMapLoader;
import soot.Body;
import soot.G;
import soot.Hierarchy;
import soot.IntType;
import soot.Local;
import soot.MethodOrMethodContext;
import soot.PatchingChain;
import soot.RefType;
import soot.Scene;
import soot.SceneTransformer;
import soot.Singletons;
import soot.SootClass;
import soot.SootMethod;
import soot.SootMethodRef;
import soot.Type;
import soot.Unit;
import soot.Value;
import soot.ValueBox;
import soot.JastAddJ.Signatures;
import soot.jimple.AssignStmt;
import soot.jimple.InstanceInvokeExpr;
import soot.jimple.InterfaceInvokeExpr;
import soot.jimple.InvokeExpr;
import soot.jimple.InvokeStmt;
import soot.jimple.Jimple;
import soot.jimple.NewExpr;
import soot.jimple.NullConstant;
import soot.jimple.SpecialInvokeExpr;
import soot.jimple.StaticFieldRef;
import soot.jimple.StaticInvokeExpr;
import soot.jimple.Stmt;
import soot.jimple.VirtualInvokeExpr;
import soot.jimple.spark.SparkTransformer;
import soot.jimple.toolkits.callgraph.CHATransformer;
import soot.jimple.toolkits.callgraph.CallGraph;
import soot.jimple.toolkits.callgraph.Edge;
import soot.jimple.toolkits.callgraph.ReachableMethods;
import soot.toolkits.graph.ExceptionalUnitGraph;
import soot.toolkits.scalar.ArraySparseSet;
import soot.toolkits.scalar.SimpleLiveLocals;
import soot.toolkits.scalar.SmartLocalDefs;
import soot.util.Chain;
import soot.util.queue.QueueReader;

public class CgTransformer extends SceneTransformer {
	public static Set<SootClass> applicationClasses = 
			new HashSet<SootClass>();
	public static Set<SootClass>
		handlerSootClasses = new HashSet<SootClass>();
	public static Set<SootClass>
		messengerSootClasses = new HashSet<SootClass>();
	public static Set<SootClass>
		intentSootClasses = new HashSet<SootClass>();
	public static Set<SootClass>
		servConnClasses = new HashSet<SootClass>();
	public static Set<SootClass>
		serviceClasses = new HashSet<SootClass>();
	public static Set<SootMethod>
		reachableMethods_  = new HashSet<SootMethod>();
	public static List<SootMethod> entryPoints
						= new LinkedList<SootMethod>();
	public static CallGraph cg = 
			new CallGraph();
	
	Map<SootMethod, Map<Unit, ArraySparseSet>> methodSummary;
	
    private String apkLoc = null;
    private Scene scene = null;
    
    private class MethodAndStmt {
        private SootMethod method;
        private Stmt stmt;
        
        MethodAndStmt(SootMethod m, Stmt s) {
            setMethod(m);
            setStmt(s);
        }

        public SootMethod getMethod() {
            return method;
        }

        public void setMethod(SootMethod method) {
            this.method = method;
        }

        public Stmt getStmt() {
            return stmt;
        }

        public void setStmt(Stmt stmt) {
            this.stmt = stmt;
        }
    }

    public CgTransformer(String al) {
        apkLoc = al;
    }
    
    @Override
    protected void internalTransform(String arg0, Map arg1) {
        //removeAndroidAutoGenClasses();
    		entryPoints = getEntryPoints();
        entryPoints.addAll(getDynamicEntryPoints(entryPoints));
        scene = Scene.v();
        scene.setEntryPoints(entryPoints);
        Scene.v().releaseCallGraph();
        Scene.v().releaseReachableMethods();
        CHATransformer.v().transform();
//        HashMap opt = new HashMap();
//        opt.put("enabled", "true");
//        opt.put("VTA","true");
//        opt.put("verbose","true");
//        opt.put("propagator","worklist");
//        opt.put("simple-edges-bidirectional","false");
//        opt.put("on-fly-cg","true");
//        opt.put("set-impl","hash");
//        opt.put("double-set-old","hybrid");
//        opt.put("double-set-new","hybrid");
//        opt.put("dumpHTML","true");
//        SparkTransformer.v().transform("mySpark",opt);
        cg = Scene.v().getCallGraph();

        Chain<SootClass> appClasses = Scene.v().getApplicationClasses();
        for(Iterator it = appClasses.iterator();it.hasNext();){
        	SootClass newClass = (SootClass) it.next();
        	applicationClasses.add(newClass);
        }
        Set<SootMethod> reachableMethods = new HashSet<SootMethod>();
        
        ReachableMethods rm = Scene.v().getReachableMethods();
        QueueReader<MethodOrMethodContext> rmIt = rm.listener();
        //recalculate reachable methods to make it complete
        while (rmIt.hasNext()) {
            SootMethod method = rmIt.next().method();

            if (!method.hasActiveBody()) {
                continue;
            }
            
            if(!applicationClasses.contains(method.getDeclaringClass())){
            	continue;
            }
            reachableMethods.add(method);
        }
        this.reachableMethods_ = reachableMethods;
        
        List heads = new LinkedList();
        for(SootMethod method : reachableMethods) {
            if (!method.hasActiveBody()
            		//||!entryPoints.contains(method)
            		) {
                continue;
            }
            heads.add(method);
        }
		BSInterproceduralAnalysis inter = 
				new BSInterproceduralAnalysis(cg, new SootMethodFilter(null), 
						entryPoints.iterator(), false);
		methodSummary = inter.getMethodSummary();
		collectAllSubClasses();
		new MessengerResolver(methodSummary,inter.data);
    augmentCallGraph(cg, scene.getReachableMethods());

//        addVirtualInvokes(scene.getReachableMethods(), reachableMethSigs, reachableMethods,
//                scene.getCallGraph());
    }

    /*
     * in this method, we collect all declared subClasses for handler and messenger
     */
    private void collectAllSubClasses() {
    	Chain<SootClass> classes = Scene.v().getClasses();
    	
		for(SootClass sc : classes){
			List<SootClass> superClasses = getSuperTypes(sc);
			superClasses.add(sc);

			for(SootClass sootClass : superClasses){
				//find all handler classes
				if(sootClass.getName().equals("android.os.Handler")){
					handlerSootClasses.add(sc);
				}
				
				if(sootClass.getName().equals("android.os.Messenger")){
					messengerSootClasses.add(sc);
				}
				
				if(sootClass.getName().equals("android.content.Intent")){
					intentSootClasses.add(sc);
				}
				
				if(sootClass.getName().equals("android.content.ServiceConnection")){
					servConnClasses.add(sc);
				}
				
				if(sootClass.getName().equals("android.app.Service")){
					serviceClasses.add(sc);
				}
			}
		}
    	
		
	}

	private void insertOnCancelled(SootMethod method, Stmt stmt, CallGraph cg,
            SootClass asyncTaskClass, PatchingChain<Unit> units, Local retLocal) {
        SootMethod onCancelledMethod = null;

        onCancelledMethod = asyncTaskClass.getMethod("void onCancelled(java.lang.Object)");
        onCancelledMethod.retrieveActiveBody();
        
        Value arg = retLocal;
        if (arg == null) {
            /*
             * TODO: This is probably not necessary if we trace doInBackground properly in the
             * class hierarchy.
             */
            arg = NullConstant.v();
        }
        List<ValueBox> vbList = stmt.getUseBoxes();
        SpecialInvokeExpr newExpr = 
                Jimple.v().newSpecialInvokeExpr((Local) vbList.get(0).getValue(),
                        onCancelledMethod.makeRef(), arg);
        Stmt newInvokeStmt = Jimple.v().newInvokeStmt(newExpr);
        units.insertBefore(newInvokeStmt, stmt);
        cg.addEdge(new Edge(method, newInvokeStmt, onCancelledMethod));
    }

    private void insertOnPostExecute(SootMethod method, Stmt stmt, CallGraph cg,
            SootClass asyncTaskClass, PatchingChain<Unit> units, Local retLocal) {
        SootMethod onPostExecuteMethod = null;

        onPostExecuteMethod = asyncTaskClass.getMethod("void onPostExecute(java.lang.Object)");
        onPostExecuteMethod.retrieveActiveBody();
        
        Value arg = retLocal;
        if (arg == null) {
            /*
             * TODO: This is probably not necessary if we trace doInBackground properly in the
             * class hierarchy.
             */
            arg = NullConstant.v();
        }
        List<ValueBox> vbList = stmt.getUseBoxes();
        SpecialInvokeExpr newExpr =  
                Jimple.v().newSpecialInvokeExpr((Local) vbList.get(0).getValue(),
                        onPostExecuteMethod.makeRef(), arg);
        Stmt newInvokeStmt = Jimple.v().newInvokeStmt(newExpr);
        units.insertBefore(newInvokeStmt, stmt);
        cg.addEdge(new Edge(method, newInvokeStmt, onPostExecuteMethod));

    }

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
            
            if (methodRef.name().contains("publishProgress")) {
                List<ValueBox> vbList = tmpStmt.getUseBoxes();
                ArrayList<Value> newValues = new ArrayList<Value>();
                for (int i = 1; i < vbList.size() - 1; ++i) {
                    // Skip the first ValueBox because it's the object instance
                    // Skip the last ValueBox because it's the whole expression
                    newValues.add(vbList.get(i).getValue());
                }

                SpecialInvokeExpr newExpr = 
                        Jimple.v().newSpecialInvokeExpr((Local) vbList.get(0).getValue(),
                                onProgressUpdateMethod.makeRef(), newValues);
                Stmt newInvokeStmt = Jimple.v().newInvokeStmt(newExpr);
                actualDoInBackgroundMethod.getActiveBody().getUnits().swapWith(tmpStmt, newInvokeStmt);
                cg.addEdge(new Edge(method, newInvokeStmt, onProgressUpdateMethod));
                
                break;
            }
        }
    }

    private Local insertDoInBackground(SootMethod method, Stmt stmt, CallGraph cg,
            SootClass asyncTaskClass, Body body, PatchingChain<Unit> units) {
        SootMethod doInBackgroundMethod = 
                asyncTaskClass.getMethod("java.lang.Object doInBackground(java.lang.Object[])");
        
        if(!doInBackgroundMethod.isConcrete()) return null;
        
        doInBackgroundMethod.retrieveActiveBody();

        Chain<Local> locals = body.getLocals();
        Local retLocal = Jimple.v().newLocal("$r" + locals.size(), RefType.v("java.lang.Object"));
        locals.add(retLocal);
        
        int startIndex = 1;
        if (stmt.getInvokeExpr().getMethod().getName().contains("executeOnExecutor"))
            startIndex = 2;

        ArrayList<Value> newValues = new ArrayList<Value>();
        List<ValueBox> vbList = stmt.getUseBoxes();
        for (int i = startIndex; i < vbList.size() - 1; ++i) {
            // Skip the first ValueBox because it's the object instance
            // Skip the last ValueBox because it's the whole expression
            newValues.add(vbList.get(i).getValue());
        }
        
        SpecialInvokeExpr newExpr = 
                Jimple.v().newSpecialInvokeExpr((Local) vbList.get(0).getValue(),
                        doInBackgroundMethod.makeRef(), newValues);
        Stmt newAssignStmt = Jimple.v().newAssignStmt(retLocal, newExpr); 
        
        units.insertBefore(newAssignStmt, stmt);
        cg.addEdge(new Edge(method, newAssignStmt, doInBackgroundMethod));
        
        return retLocal;
    }

    private void insertOnPreExecute(SootMethod method, Stmt stmt, CallGraph cg,
            SootClass asyncTaskClass, PatchingChain<Unit> units) {
        // Insert a call to onPreExecute()
        SootMethod onPreExecuteMethod = asyncTaskClass.getMethod("void onPreExecute()");
        onPreExecuteMethod.retrieveActiveBody();
        
        List<ValueBox> vbList = stmt.getUseBoxes();
        SpecialInvokeExpr newExpr = 
                Jimple.v().newSpecialInvokeExpr((Local) vbList.get(0).getValue(),
                        onPreExecuteMethod.makeRef());
        Stmt newInvokeStmt = Jimple.v().newInvokeStmt(newExpr);
        units.insertBefore(newInvokeStmt, stmt);
        cg.addEdge(new Edge(method, newInvokeStmt, onPreExecuteMethod));
        
    }
    
    private void addAsyncTaskEdges(SootMethod method, Stmt stmt, CallGraph cg) {
        SootClass asyncTaskClass = stmt.getInvokeExpr().getMethodRef().declaringClass();
        SootClass asyncTaskSuperClass = scene.getSootClass("android.os.AsyncTask");
        
        Body body = method.getActiveBody();
        PatchingChain<Unit> units = body.getUnits();
        
        ArrayList<SootClass> classesToConnect = new ArrayList<SootClass>();
        /*
         * testing class hierarchy resolver
         */
        AsyncTaskResolver atr = new AsyncTaskResolver(method, stmt, cg);
        classesToConnect = (ArrayList<SootClass>) atr.getAsyncTaskClasses();
        
//        if (!asyncTaskClass.declaresMethod("java.lang.Object doInBackground(java.lang.Object[])")
//                || asyncTaskClass.equals(asyncTaskSuperClass)) {
//            /*
//             * TODO: We need to trace doInBackground properly in the class hierarchy to handle this
//             * case. Right now, it's CHEX-style.
//             */
//            System.err.println("Warning: CgTansformer goes CHEX for " + stmt.toString());
//
//            Hierarchy hierarchy = scene.getActiveHierarchy();
//            classesToConnect.addAll(hierarchy.getSubclassesOf(asyncTaskSuperClass));
//            
//        } else {
//            classesToConnect.add(asyncTaskClass);
//        }
        
        for (SootClass subClass: classesToConnect) {
            if (subClass.toString().startsWith("android."))
                continue;
//            if (!subClass.declaresMethod("java.lang.Object doInBackground(java.lang.Object[])"))
//                continue;

            Local retLocal = null;

            if (subClass.declaresMethod("void onPreExecute()")) {
                insertOnPreExecute(method, stmt, cg, subClass, units);
            }

            // This should always be true
            if (subClass.declaresMethod("java.lang.Object doInBackground(java.lang.Object[])")) {
                retLocal = insertDoInBackground(method, stmt, cg, subClass, body, units);
            }

            if (subClass.declaresMethod("void onProgressUpdate(java.lang.Object[])")) {
                insertOnProgressUpdate(method, cg, subClass);
            }

            if (subClass.declaresMethod("void onPostExecute(java.lang.Object)")) {
                insertOnPostExecute(method, stmt, cg, subClass, units, retLocal);
            }

            if (subClass.declaresMethod("void onCancelled(java.lang.Object)")) {
                insertOnCancelled(method, stmt, cg, subClass, units, retLocal);
            }
        }

        units.remove(stmt);
    }

    private void addHandlerEdges(SootMethod method, Stmt stmt, CallGraph cg) {
        SootClass handlerClass = stmt.getInvokeExpr().getMethodRef().declaringClass();
        SootClass handlerSuperClass = scene.getSootClass("android.os.Handler");
        
        Body body = method.getActiveBody();
        PatchingChain<Unit> units = body.getUnits();
        
        AsyncTaskResolver atr = new AsyncTaskResolver(method, stmt, cg, true); 
        ArrayList<SootClass> classesToConnect = new ArrayList<SootClass>();
        classesToConnect = (ArrayList<SootClass>) atr.getAsyncTaskClasses();
        
//        if (handlerClass.equals(handlerSuperClass)) {
//            /*
//             * TODO: This needs to trace back in order to get the right class. 
//             * Currently, it doesn't do it. It's rather CHEX-like.
//             */
//            System.err.println("Warning: CgTansformer goes CHEX for " + stmt.toString());
//
//            Hierarchy hierarchy = scene.getActiveHierarchy();
//            classesToConnect.addAll(hierarchy.getSubclassesOf(handlerSuperClass));
//        } else {
//            classesToConnect.add(handlerClass);
//        }
//        
        for (SootClass subClass: classesToConnect) {
            if (subClass.toString().startsWith("android."))
                continue;

            boolean hasMethod = false;
            if(subClass.declaresMethod("void handleMessage(android.os.Message)")){
            	hasMethod = true;
            }
            
            if(!hasMethod){
            	continue;
            }
            SootMethod handleMessageMethod = subClass.getMethod("void handleMessage(android.os.Message)");
            
            if(!handleMessageMethod.isConcrete()) continue;
            
            handleMessageMethod.retrieveActiveBody();

            ArrayList<Value> newValues = new ArrayList<Value>();
            List<ValueBox> vbList = stmt.getUseBoxes();
            for (int i = 1; i < vbList.size() - 1; ++i) {
                // Skip the first ValueBox because it's the object instance
                // Skip the last ValueBox because it's the whole expression
                newValues.add(vbList.get(i).getValue());
            }

            SpecialInvokeExpr newExpr = 
                    Jimple.v().newSpecialInvokeExpr((Local) vbList.get(0).getValue(),
                            handleMessageMethod.makeRef(), newValues);
            Stmt newInvokeStmt = Jimple.v().newInvokeStmt(newExpr);
            units.insertAfter(newInvokeStmt, stmt);
            cg.addEdge(new Edge(method, newInvokeStmt, handleMessageMethod));

            //        Edge e = new Edge(method, newInvokeStmt, handleMessageMethod);
            //        System.err.println("HandlerEdge: " + newInvokeStmt.toString());
            //        System.err.println("HandlerEdge: " + e);
        }
        units.remove(stmt);
    }

    private void addMessengerEdges(SootMethod method, Stmt stmt, CallGraph cg) {
        /*
         * TODO: Similar status with addHandlerEdges. Need better support for this.
         * This is CHEX-like because here we find all potential handleMessage()'s.
         */
    	if(!stmt.containsInvokeExpr()) return;
    	InvokeExpr invokeExpr = stmt.getInvokeExpr();
    	
    	if(!(invokeExpr instanceof InstanceInvokeExpr)) return;
    	InstanceInvokeExpr messeniie = (InstanceInvokeExpr)invokeExpr;
    	Value messenBase = messeniie.getBase();
    	if(!(messenBase.getType() instanceof RefType)) return;
    	
    	SootClass messClass = ((RefType)messenBase.getType()).getSootClass();     	
    	List<SootClass> handlers = MessengerResolver.messengerToHandler.get(messClass);
        Body body = method.getActiveBody();
        PatchingChain<Unit> units = body.getUnits();
        
        //TODO: in some cases, no handler is found, revisit and double check the mapping finding
        if(handlers == null) return;
//        
//        SootClass handlerClass = scene.getSootClass("android.os.Handler");
//
//        Hierarchy hierarchy = scene.getActiveHierarchy();
//        for (SootClass subClass: hierarchy.getSubclassesOf(handlerClass)) {
    	for(SootClass subClass: handlers){
            if (subClass.toString().startsWith("android."))
                continue;
            
            if(!subClass.declaresMethod("void handleMessage(android.os.Message)")) continue;
            
            SootMethod handleMessageMethod = subClass.getMethod("void handleMessage(android.os.Message)");
            handleMessageMethod.retrieveActiveBody();
            
            ArrayList<Value> newValues = new ArrayList<Value>();
            List<ValueBox> vbList = stmt.getUseBoxes();
            for (int i = 1; i < vbList.size() - 1; ++i) {
                // Skip the first ValueBox because it's the object instance
                // Skip the last ValueBox because it's the whole expression
                newValues.add(vbList.get(i).getValue());
            }
            
            SpecialInvokeExpr newExpr = 
                    Jimple.v().newSpecialInvokeExpr((Local) vbList.get(0).getValue(),
                            handleMessageMethod.makeRef(), newValues);
            Stmt newInvokeStmt = Jimple.v().newInvokeStmt(newExpr);
            units.insertBefore(newInvokeStmt, stmt);
            cg.addEdge(new Edge(method, newInvokeStmt, handleMessageMethod));

            Edge e = new Edge(method, newInvokeStmt, handleMessageMethod);            
        }
        units.remove(stmt);
    }
   
    private CallGraph augmentCallGraph(CallGraph cg, ReachableMethods rm) {
        QueueReader<MethodOrMethodContext> rmIt = rm.listener();
        ArrayList<MethodAndStmt> asyncTaskMethods = new ArrayList<MethodAndStmt>();
        ArrayList<MethodAndStmt> handlerMethods = new ArrayList<MethodAndStmt>();
        ArrayList<MethodAndStmt> messengerMethods = new ArrayList<MethodAndStmt>();
        ArrayList<MethodAndStmt> interfaceMethods = new ArrayList<MethodAndStmt>();
        ArrayList<MethodAndStmt> reflectionMethods = new ArrayList<MethodAndStmt>();

        while (rmIt.hasNext()) {
            SootMethod method = rmIt.next().method();
            if (!method.hasActiveBody())
                continue;

            Body body = method.getActiveBody();
            PatchingChain<Unit> unitList = body.getUnits();
            for (Unit unit : unitList) {
                Stmt stmt = (Stmt) unit;
                
                if (!stmt.containsInvokeExpr())
                    continue;
                
                InvokeExpr invokeExpr = (InvokeExpr) stmt.getInvokeExpr();
                SootMethodRef methodRef = invokeExpr.getMethodRef();
                
                if (isAsyncTaskMethod(invokeExpr, methodRef, unit))
                    asyncTaskMethods.add(new MethodAndStmt(method, stmt));
                
                if (isHandlerMethod(invokeExpr, methodRef, unit))
                    handlerMethods.add(new MethodAndStmt(method, stmt));
                
                if (isMessengerMethod(invokeExpr, methodRef, unit))
                    messengerMethods.add(new MethodAndStmt(method, stmt));
                
                if (invokeExpr instanceof InterfaceInvokeExpr)
                	interfaceMethods.add(new MethodAndStmt(method, stmt));
                
                if (isReflectionInvoke(method, methodRef, unit))
                		reflectionMethods.add(new MethodAndStmt(method, stmt));
                	
            }
        }
        
        for (MethodAndStmt methodAndStmt: asyncTaskMethods) {
            addAsyncTaskEdges(methodAndStmt.getMethod(), methodAndStmt.getStmt(), cg);
        }
        
        for (MethodAndStmt methodAndStmt: handlerMethods) {
            addHandlerEdges(methodAndStmt.getMethod(), methodAndStmt.getStmt(), cg);
        }

        for (MethodAndStmt methodAndStmt: messengerMethods) {
            addMessengerEdges(methodAndStmt.getMethod(), methodAndStmt.getStmt(), cg);
        }
        
        for(MethodAndStmt methodAndStmt: interfaceMethods){
        	addInterfaceMethodEdges(methodAndStmt.getMethod(), methodAndStmt.getStmt(), cg);
        }
        
        for(MethodAndStmt methodAndStmt: reflectionMethods){
        	addReflectionMethodEdges(methodAndStmt.getMethod(), methodAndStmt.getStmt(), cg);
        }
        
        return cg;
    }
    
    private void addReflectionMethodEdges(SootMethod method, Stmt stmt,
				CallGraph cg2){
    	if(!method.hasActiveBody() ||
    			!method.getDeclaringClass().isApplicationClass()) return;
    	
    	InvokeExpr reflectInvokeExpr = stmt.getInvokeExpr();
    	List<Value> reflectInvokeArgs = reflectInvokeExpr.getArgs();
    	// we need to find out right class name and method for the reflection
    	String reflectiveClassName = null;
    	String reflectiveClassMethodName = null;
    	List<Type> reflectiveMethodArgs = new LinkedList<Type>();
    	
    	Body body = method.getActiveBody();
    	ExceptionalUnitGraph eug = new ExceptionalUnitGraph(body);
    	PatchingChain<Unit> units = body.getUnits();
    	
    	if(!methodSummary.containsKey(method)) return;
    	
    	Map<Unit, ArraySparseSet> summaryFromFirstRoundAnalysis = methodSummary.get(method);
    	
    	if(!summaryFromFirstRoundAnalysis.containsKey(stmt)) return;
    	
    	ArraySparseSet flowIntoThis = summaryFromFirstRoundAnalysis.get(stmt);
    	
    	for(Iterator it = flowIntoThis.iterator(); it.hasNext();){
    		Stmt unit = (Stmt) it.next();
    		
    		if(!unit.containsInvokeExpr()) continue;
    		
    		InvokeExpr invokeExpr = unit.getInvokeExpr();
    		SootMethodRef methodRef = invokeExpr.getMethodRef();
    		String methodName = methodRef.name();
    		String methodClassName = methodRef.declaringClass().getName();
    		String returnType = methodRef.returnType().toString();
    		List<Type> args = methodRef.parameterTypes();
    		
    		//first find the reflection api, forName
    		if(methodName.equals("forName")
    				&& methodClassName.equals("java.lang.Class")
    				&& returnType.equals("java.lang.Class")
    				&& args.size() == 1
    				&& args.get(0).toString().equals("java.lang.String")){
    			reflectiveClassName = invokeExpr.getArg(0).toString();
    		}
    		
    		if(methodName.equals("getMethod")
    				&&methodClassName.equals("java.lang.Class")
    				&&returnType.equals("java.lang.reflect.Method")){
    			reflectiveClassMethodName = invokeExpr.getArg(0).toString();
    			
    			for(int i = 1; i < args.size(); i++){
    				//skip the first arg, because it's method name
    				reflectiveMethodArgs.add(args.get(i));
    			}
    		}
    	}//end of finding reflection class & method information
    	
    	if(reflectiveClassName!=null 
    			&&reflectiveClassMethodName!=null){
    		reflectiveClassMethodName = reflectiveClassMethodName.substring(1, 
    				reflectiveClassMethodName.length()-1);
    		reflectiveClassName = reflectiveClassName.substring(1, 
    				reflectiveClassName.length()-1);
    		SootClass reflectClass = null;
    		try{
    			reflectClass = Scene.v().forceResolve(reflectiveClassName, SootClass.BODIES);
    		}catch(RuntimeException e){
    			//do nothing
    			return;
    		}
    		//if this class is not a application class, then skip it
    		if(reflectClass == null || !reflectClass.isApplicationClass()) return;
    		
    		reflectClass.setApplicationClass();
    		scene.v().loadClassAndSupport(reflectiveClassName);
    		
    		if(Scene.v().containsClass(reflectiveClassName)){
    			SootClass sootClass = Scene.v().getSootClass(reflectiveClassName);
    			
    			if(sootClass.declaresMethodByName(reflectiveClassMethodName)){
    				SootMethod reflectiveMethod = null;
    				try{
    					reflectiveMethod = sootClass.getMethod(reflectiveClassMethodName, reflectiveMethodArgs);
    				}catch(RuntimeException e){
    					//no method found, do nothing
    					return;
    				}
    				Body testbody = reflectiveMethod.retrieveActiveBody();
    				//create a new class instance
    				Chain<Local> locals = body.getLocals();
    				Local classObject = Jimple.v().newLocal("$r"+locals.size(), 
    						RefType.v(reflectiveClassName));
    				locals.add(classObject);
    				//create a NewExpr
    				NewExpr newExpr = Jimple.v().newNewExpr(RefType.v(sootClass));
    				AssignStmt assignStmt = Jimple.v().newAssignStmt(classObject, newExpr);
    				units.insertBefore(assignStmt, stmt);
    				
    				//create a new method invoke expr
    				//first construct the arg list
    				//remove the first one, because it is the reflection instance object
    				reflectInvokeArgs.remove(0);
    				InvokeExpr newInvokeExpr;
    				if(reflectiveMethod.isStatic()){
    					StaticInvokeExpr newStaticInvoke =
    							Jimple.v().newStaticInvokeExpr(reflectiveMethod.makeRef(),reflectInvokeArgs);
    					newInvokeExpr = newStaticInvoke;
    				}else{
    					VirtualInvokeExpr newVirtualInvoke = 
      						Jimple.v().newVirtualInvokeExpr(classObject, reflectiveMethod.makeRef(),
      								reflectInvokeArgs);
    					newInvokeExpr = newVirtualInvoke;
    				}
    				
    				//finally we find the right class and name
    				//create a new invoke method
    				if(stmt instanceof AssignStmt){
    					Value leftOp = ((AssignStmt)stmt).getLeftOp();
    					AssignStmt invokeAssign = Jimple.v().newAssignStmt(leftOp, newInvokeExpr);
    					units.insertAfter(invokeAssign, stmt);
    					 cg2.addEdge(new Edge(method, invokeAssign, reflectiveMethod));
    				}else{
    					InvokeStmt newInvokeStmt = Jimple.v().newInvokeStmt(newInvokeExpr);
    					units.insertAfter(newInvokeStmt, stmt);
    					cg2.addEdge(new Edge(method, newInvokeStmt, reflectiveMethod));
    				}
    				reachableMethods_.add(reflectiveMethod);
    				units.remove(stmt);

    			}
    		}
    	}
			
		}


		private boolean isReflectionInvoke(SootMethod method,
				SootMethodRef methodRef, Unit unit){
			
			String methodClassName = methodRef.declaringClass().getName();
			String returnType = methodRef.returnType().toString();
			String methodName = methodRef.name();
			
			return method.getDeclaringClass().isApplicationClass()
					&&methodClassName.equals("java.lang.reflect.Method")
					&&methodName.equals("invoke")
					&&returnType.equals("java.lang.Object");
		}

		private void addInterfaceMethodEdges(SootMethod method, Stmt stmt, CallGraph cg2) {
		if(!stmt.containsInvokeExpr()) return;
		
		InvokeExpr invokeExpr = stmt.getInvokeExpr();
		
		if(!(invokeExpr instanceof InterfaceInvokeExpr)) return;
		
		Body body = method.getActiveBody();
        PatchingChain<Unit> units = body.getUnits();
		SootClass interfaceClass = invokeExpr.getMethodRef().declaringClass();
		Hierarchy hierarchy = Scene.v().getActiveHierarchy();
		List<SootClass> implClasses = hierarchy.getImplementersOf(interfaceClass);
		SootMethod invokeMethod = invokeExpr.getMethod();
		
		boolean replaced = false;
		for(SootClass impl : implClasses){
			if(!impl.isApplicationClass()) continue;
			
			if(!impl.declaresMethod(invokeMethod.getName(), 
					invokeMethod.getParameterTypes(), invokeMethod.getReturnType()))
					continue;
			
			SootMethod implMethod = impl.getMethod(invokeMethod.getName(), 
					invokeMethod.getParameterTypes(), invokeMethod.getReturnType());
			ArrayList<Value> newValues = new ArrayList<Value>();
            List<ValueBox> vbList = stmt.getUseBoxes();
            for (int i = 1; i < vbList.size() - 1; ++i) {
                // Skip the first ValueBox because it's the object instance
                // Skip the last ValueBox because it's the whole expression
                newValues.add(vbList.get(i).getValue());
            }
            
            SpecialInvokeExpr newExpr = 
                    Jimple.v().newSpecialInvokeExpr((Local) vbList.get(0).getValue(),
                            implMethod.makeRef(), newValues);
            Stmt newInvokeStmt = Jimple.v().newInvokeStmt(newExpr);
            units.insertBefore(newInvokeStmt, stmt);
            cg.addEdge(new Edge(method, newInvokeStmt, implMethod));
            replaced = true;

		}
		if(replaced)  units.remove(stmt);
		
	}

	boolean isAsyncTaskMethod(InvokeExpr invokeExpr, SootMethodRef methodRef, Unit unit) {
        boolean flag = false;
        
        if (!methodRef.name().contains("execute"))
            return flag;

        // TODO: Not handling Runnable to AsyncTask yet
        if (invokeExpr instanceof StaticInvokeExpr)
            return flag;
        
        SootClass asyncTaskClass = methodRef.declaringClass();
//        if (asyncTaskClass.getName().equals("android.os.AsyncTask")){
//            // TODO: This requires backward flow analysis to trace back to the actual class
//            System.err.println("Warning: CgTansformer skips " + unit.toString());
//
//            return flag;
//        }


        for (SootClass superClass: getSuperTypes(asyncTaskClass)) {
            if (superClass.toString().equals("android.os.AsyncTask")) {
                flag = true;
                break;
            }
        }
        
        return flag;
    }
    
    boolean isHandlerMethod(InvokeExpr invokeExpr, SootMethodRef methodRef, Unit unit) {
        boolean flag = false;
        
        if (!methodRef.name().contains("sendMessage"))
            return flag;

        SootClass handlerClass = methodRef.declaringClass();
//        if (handlerClass.getName().equals("android.os.Handler")){
//            // TODO: This requires backward flow analysis to trace back to the actual class
//            System.err.println("Warning: CgTansformer skips " + unit.toString());
//
//            return flag;
//        }

        for (SootClass superClass: getSuperTypes(handlerClass)) {
            if (superClass.toString().equals("android.os.Handler")) {
                flag = true;
                break;
            }
        }
        
        return flag;
    }
    
    boolean isMessengerMethod(InvokeExpr invokeExpr, SootMethodRef methodRef, Unit unit) {
        boolean flag = false;
        
        if (!methodRef.name().contains("send"))
            return flag;
        
        SootClass messengerClass = methodRef.declaringClass();
//        if (messengerClass.getName().equals("android.os.Messenger")){
//            // TODO: This requires backward flow analysis to trace back to the actual class
//            System.err.println("Warning: CgTansformer skips " + unit.toString());
//
//            return flag;
//        }
//
//
        for (SootClass superClass: getSuperTypes(messengerClass)) {
            if (superClass.toString().equals("android.os.Messenger")) {
                flag = true;
                break;
            }
        }
        
        return flag;
    }

//    private void addVirtualInvokes(ReachableMethods rm, Set<String> reachableMethSigs,
//            Set<SootMethod> reachableMethods, CallGraph cg) {
//        QueueReader<MethodOrMethodContext> rmIt = rm.listener();
//        
//        while (rmIt.hasNext()) {
//            SootMethod method = rmIt.next().method();
//            reachableMethSigs.add(method.getSignature());
//            reachableMethods.add(method);
//            if (!method.hasActiveBody()) {
//                continue;
//            }
//
//            // take care of virtual invoke methods
//            Body body = method.getActiveBody();
//            PatchingChain<Unit> unitList = body.getUnits();
//            for (Unit unit : unitList) {
//                
//                if (!(method.getName().contains("onCreate"))) {
//                    continue;
//                }
//                BSStmntSwitch stSwitch = new BSStmntSwitch(unit);
//                if (stSwitch.isInvokeStmnt()) {
//                    InvokeExpr unitInvokeStmt = ((InvokeStmt) unit).getInvokeExpr();
//                    /*
//                     * InvokeExpr unitInvokeExpr =
//                     * ((InvokeStmt)unit).getInvokeExpr(); //find all the
//                     * callers to contentprovider SootMethodRef methodRef =
//                     * unitInvokeExpr.getMethodRef(); //Debug.println("Method",
//                     * method.toString());
//                     * if(methodRef.declaringClass().getName(
//                     * ).contains("ContentResolver")||
//                     * methodRef.declaringClass()
//                     * .getName().contains("ContentProviderClient")){
//                     * if(methodRef.name().contains("insert")||
//                     * methodRef.name().contains("query")||
//                     * methodRef.name().contains("update")){
//                     * //Debug.printOb("insert invoke..."); List<Value> values =
//                     * unitInvokeExpr.getArgs(); if(values.size() > 1){ Value
//                     * firstPara = unitInvokeExpr.getArg(1);
//                     * Debug.println("para" ,firstPara.toString()); }else{
//                     * Debug.printOb("no args!"); } } }
//                     */
//
//                    if (unitInvokeStmt instanceof VirtualInvokeExpr) {
//                        SootMethod newMeth = ((VirtualInvokeExpr) unitInvokeStmt).getMethod();
//                        reachableMethods.add(newMeth);
//                        reachableMethSigs.add(newMeth.getSignature());
//                    }
//                }
//            }
//        }
//    }
    
    private List<SootMethod> getDynamicEntryPoints(List<SootMethod> initialEntryPoints) {
        ArrayList<SootMethod> returnList = new ArrayList<SootMethod>();
        LayoutFileParser layoutParser = new LayoutFileParser(apkLoc);
        Map<String, String> idToFile = layoutParser.getIdToFile();
        Map<String, Set<String>> functionsFromXmlFile = layoutParser.getFunctionsFromXmlFile();

        Scene.v().setEntryPoints(initialEntryPoints);
        CHATransformer.v().transform();

        ReachableMethods rm = Scene.v().getReachableMethods();
        QueueReader<MethodOrMethodContext> rmIt = rm.listener();

        while (rmIt.hasNext()) {
            SootMethod method = rmIt.next().method();
            if (!method.hasActiveBody())
                continue;

            Body body = method.getActiveBody();

            for (Iterator<Unit> unitIt = body.getUnits().iterator(); unitIt.hasNext();) {
                Stmt stmt = (Stmt) unitIt.next();
                
                if (!stmt.containsInvokeExpr())
                    continue;

                SootMethodRef methodRef = stmt.getInvokeExpr().getMethodRef();
                if (!methodRef.name().contains("setContentView"))
                    continue;
                if(stmt.getInvokeExpr().getArgCount() <= 0) continue;
                Value param = stmt.getInvokeExpr().getArg(0);
                if (!(param.getType() instanceof IntType))
                    continue;

                String fileName = null;
                try { 
                    int layoutIdInt = Integer.parseInt(stmt.getInvokeExpr().getArg(0).toString());
                
                    fileName = idToFile.get("0x" + Integer.toHexString(layoutIdInt));
                } catch (NumberFormatException e) {
                    // TODO: Right now we're only tracing back within the same method.
                    ExceptionalUnitGraph eug = new ExceptionalUnitGraph(body);
                    SmartLocalDefs localDefs = new SmartLocalDefs(eug, new SimpleLiveLocals(eug));

                    List<Unit> defs = localDefs.getDefsOfAt((Local) stmt.getUseBoxes().get(1).getValue(), stmt);   
                    Stmt defStmt = (Stmt) defs.get(defs.size() - 1);
                    Value rV = defStmt.getUseBoxes().get(0).getValue();
                    if (rV instanceof StaticFieldRef) {
                        fileName = ((StaticFieldRef) rV).getFieldRef().name();
                    } else {
                        // TODO: This requires backward flow analysis to trace back where it's coming from
                        //System.err.println("Warning: DynamicEntryPoint skips " + defStmt.toString());
                    }
                }
                if(fileName==null ||
                		!layoutParser.getLayoutFilesNameList().contains(fileName)) 
                	continue;
                
                List<String> layouts = layoutParser.getFileToEmbededFiles().get(fileName);
                layouts.add(fileName);
                for(String layout : layouts){
                    if (!functionsFromXmlFile.containsKey(layout))
                        // TODO: this means that we might be skipping some layout files
                        continue;
                	for (Iterator<String> it = functionsFromXmlFile.get(layout).iterator();
                            it.hasNext();) {
                        String signature = "<" + methodRef.declaringClass().getName() + ": void "
                                + it.next() + ">";

                        try {
                            returnList.add(Scene.v().getMethod(signature));
                        } catch (RuntimeException e) {
                            System.err.println("Warning: DynamicEntryPoint cannot find " + signature + " (signature is perhaps wrong)");
                        }
                    }//finish checking one single layout file loop
                }//finish checking all layouts loop
                
            }
        }

        //layoutParser.clean();
        return returnList;
    }

    /*
     * retrieve all the entry points in the application
     */
    private List<SootMethod> getEntryPoints() {
        // TODO Auto-generated method stub
        List<SootMethod> entryPoints = new ArrayList<SootMethod>();
        Chain<SootClass> classes = Scene.v().getApplicationClasses();
        Map<String, Set<String>> epMap = new EntryPointsMapLoader().getEPMap();
        for (SootClass sc : classes) {
            List<SootClass> superTypes = getSuperTypes(sc);
            entryPoints.addAll(getEntryMethods(sc, superTypes, epMap));
        }

        return entryPoints;

    }
//
//    /*
//     * remove all the Anroid auto generated classes
//     */
//    private void removeAndroidAutoGenClasses() {
//        // TODO Auto-generated method stub
//        Set<SootClass> classesToRemove = new HashSet<SootClass>();
//        for (SootClass clazz : Scene.v().getApplicationClasses()) {
//            String name = clazz.getJavaStyleName();
//            // BuildConfig.java
//            if (name.equals("BuildConfig"))
//                classesToRemove.add(clazz);
//        }
//        for (SootClass clazz : classesToRemove)
//            Scene.v().removeClass(clazz);
//
//    }

    /*
     * get all the entry methods in the application
     */
    private List<SootMethod> getEntryMethods(SootClass baseClass, List<SootClass> classes,
            Map<String, Set<String>> epMap) {

        List<SootMethod> entryMethods = new ArrayList<SootMethod>();
        for (SootClass c : classes) {
            // find which classes are in ep map
            String className = c.getName().replace('$', '.');

            if (epMap.containsKey(className)) {
                Set<String> methods = epMap.get(className);

                for (String method : methods) {
                    String signature = "<" + baseClass + method + ">";
                    try {
                        entryMethods.add(Scene.v().getMethod(signature));
                    } catch (Exception e) {
                    }
                }
            }
        }
        return entryMethods;
    }

    /*
     * get class's super classes
     */
    private List<SootClass> getSuperTypes(SootClass sc) {
        List<SootClass> superTypes = new ArrayList<SootClass>();
        while (sc.hasSuperclass()) {
            superTypes.add(sc);
            superTypes.addAll(sc.getInterfaces());
            sc = sc.getSuperclass();
        }
        return superTypes;
    }

}
