package edu.buffalo.cse.blueseal.BSCallgraph;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import soot.Body;
import soot.Hierarchy;
import soot.Local;
import soot.MethodOrMethodContext;
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
import soot.jimple.InterfaceInvokeExpr;
import soot.jimple.InvokeExpr;
import soot.jimple.Jimple;
import soot.jimple.SpecialInvokeExpr;
import soot.jimple.StaticInvokeExpr;
import soot.jimple.Stmt;
import soot.jimple.toolkits.callgraph.CallGraph;
import soot.jimple.toolkits.callgraph.Edge;
import soot.jimple.toolkits.callgraph.ReachableMethods;
import soot.toolkits.scalar.ArraySparseSet;
import soot.util.queue.QueueReader;
import edu.buffalo.cse.blueseal.BSAndroidSpecific.BSActivityFromIntentModifier;
import edu.buffalo.cse.blueseal.BSAndroidSpecific.BSAyncTaskModifier;
import edu.buffalo.cse.blueseal.BSAndroidSpecific.BSReflectionModifier;
import edu.buffalo.cse.blueseal.BSAndroidSpecific.BSServiceStartModifier;
import edu.buffalo.cse.blueseal.BSFlow.AsyncTaskResolver;
import edu.buffalo.cse.blueseal.BSFlow.BSInterproceduralAnalysis;
import edu.buffalo.cse.blueseal.BSFlow.MessengerResolver;
import edu.buffalo.cse.blueseal.BSFlow.SootMethodFilter;

public class BSCallGraphAugmentor {
	
	private CallGraph cg = null;
	private ApplicationInfo appInfo = null;
	private List<SootMethod> entryPoints = null;
	public static Set<SootMethod> rebuiltReachableMethods = null;
	public static Map<SootMethod, Map<Unit, ArraySparseSet>> methodSummary = null;

	public BSCallGraphAugmentor(CallGraph callgraph, ApplicationInfo info, List<SootMethod> entry) {
		cg = callgraph;
		appInfo = info;
		entryPoints = entry;
		rebuiltReachableMethods = appInfo.getReachableMethods();
	}
	
	public Set<SootMethod> getRebuiltReachableMethods(){
		return this.rebuiltReachableMethods;
	}
	
	public void run(){
		methodSummary = runInterAnalysisOnce();
		
		
		augmentCallGraph(cg, Scene.v().getReachableMethods());
	}
	
	private void augmentCallGraph(CallGraph callgraph, ReachableMethods rm) {
    QueueReader<MethodOrMethodContext> rmIt = rm.listener();
    ArrayList<MethodAndStmt> asyncTaskMethods = new ArrayList<MethodAndStmt>();
    ArrayList<MethodAndStmt> handlerMethods = new ArrayList<MethodAndStmt>();
    ArrayList<MethodAndStmt> messengerMethods = new ArrayList<MethodAndStmt>();
    ArrayList<MethodAndStmt> interfaceMethods = new ArrayList<MethodAndStmt>();
    ArrayList<MethodAndStmt> reflectionMethods = new ArrayList<MethodAndStmt>();
    ArrayList<MethodAndStmt> serviceMethods = new ArrayList<MethodAndStmt>();
    ArrayList<MethodAndStmt> activityFromIntentMethods = new ArrayList<MethodAndStmt>();

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
            
            if(isActivityStartFromIntent(invokeExpr, methodRef, unit)){
            	System.out.println(" activity Intent found:" + stmt.toString());
            	activityFromIntentMethods.add(new MethodAndStmt(method, stmt));
            }
            
            if(isServiceStartMethod(invokeExpr, methodRef, unit))
            	serviceMethods.add(new MethodAndStmt(method, stmt));
            
            if (isAsyncTaskMethod(invokeExpr, methodRef, unit))
                asyncTaskMethods.add(new MethodAndStmt(method, stmt));
            
            if (isHandlerMethod(invokeExpr, methodRef, unit))
                handlerMethods.add(new MethodAndStmt(method, stmt));
            
            if (isMessengerMethod(invokeExpr, methodRef, unit))
                messengerMethods.add(new MethodAndStmt(method, stmt));
            
            if(isInterfaceInvoke(invokeExpr, methodRef, unit))
            	interfaceMethods.add(new MethodAndStmt(method, stmt));
            
            if (isReflectionInvoke(method, methodRef, unit))
            		reflectionMethods.add(new MethodAndStmt(method, stmt));
            	
        }
    }
    
    for (MethodAndStmt methodAndStmt: asyncTaskMethods) {
        BSAyncTaskModifier modifier = new BSAyncTaskModifier(methodAndStmt.getMethod(), 
        		methodAndStmt.getStmt(), callgraph);
        modifier.modify();
    }
    
    for (MethodAndStmt methodAndStmt: handlerMethods) {
        addHandlerEdges(methodAndStmt.getMethod(), methodAndStmt.getStmt(), callgraph);
    }

    for (MethodAndStmt methodAndStmt: messengerMethods) {
        addMessengerEdges(methodAndStmt.getMethod(), methodAndStmt.getStmt(), callgraph);
    }
    
    for(MethodAndStmt methodAndStmt: interfaceMethods){
    	addInterfaceMethodEdges(methodAndStmt.getMethod(), methodAndStmt.getStmt(), callgraph);
    }
    
    for(MethodAndStmt methodAndStmt: reflectionMethods){
    	BSReflectionModifier modifier = new BSReflectionModifier(methodAndStmt.getMethod(),
    			methodAndStmt.getStmt(), callgraph);
    	modifier.modify();
    }		
    
    for(MethodAndStmt methodAndStmt: serviceMethods){
    	BSServiceStartModifier modifier = new BSServiceStartModifier(methodAndStmt.getStmt(),
    			methodAndStmt.getMethod(), callgraph);
    	modifier.modify();
    }
    
    for(MethodAndStmt methodAndStmt : activityFromIntentMethods){
    	BSActivityFromIntentModifier modifier = new BSActivityFromIntentModifier(methodAndStmt.getStmt(),
    			methodAndStmt.getMethod(), callgraph);
    	modifier.modify();
    }
	}

	public static boolean isActivityStartFromIntent(InvokeExpr invokeExpr,
			SootMethodRef methodRef, Unit unit) {
		String sig = methodRef.getSignature();
		return sig.contains("<android.app.Activity void startActivity(android.content.Intent,")
				|| sig.contains("<android.app.Activity void startActivityForResult(android.content.Intent,")
				|| sig.contains("<android.app.Activity void startActivityFromChild")
				|| sig.contains("<android.app.Activity void startActivityFromFragment")
				|| sig.contains("<android.app.Activity boolean startActivityIfNeeded");
	}

	public static boolean isServiceStartMethod(InvokeExpr invokeExpr,
			SootMethodRef methodRef, Unit unit) {
		String sig = methodRef.getSignature();
		
		return sig.equals("<android.content.Context: android.content.ComponentName startService(android.content.Intent)>")
				|| sig.equals("<android.content.Context: boolean bindService(android.content.Intent, android.content.ServiceConnection, int)>");
	}

	private Map<SootMethod, Map<Unit, ArraySparseSet>> runInterAnalysisOnce() {
  	Map<SootMethod, Map<Unit, ArraySparseSet>> methodSum;
    List heads = new LinkedList();
    
    for(SootMethod method : appInfo.getReachableMethods()) {
        if (!method.hasActiveBody()
        		//||!entryPoints.contains(method)
        		) {
            continue;
        }
        heads.add(method);
    }
    
    BSInterproceduralAnalysis inter = new BSInterproceduralAnalysis(cg, 
    		new SootMethodFilter(null),entryPoints.iterator(), false);
    methodSum = inter.getMethodSummary();

		new MessengerResolver(methodSum,inter.getData());
    
		return methodSum;
	}

	public CallGraph getCallGraph(){
		return this.cg;
	}
	
	boolean isAsyncTaskMethod(InvokeExpr invokeExpr, SootMethodRef methodRef, Unit unit) {
    boolean flag = false;
    
    if (!methodRef.name().contains("execute"))
        return flag;

    // TODO: Not handling Runnable to AsyncTask yet
    if (invokeExpr instanceof StaticInvokeExpr)
        return flag;
    
    SootClass asyncTaskClass = methodRef.declaringClass();
//    if (asyncTaskClass.getName().equals("android.os.AsyncTask")){
//        // TODO: This requires backward flow analysis to trace back to the actual class
//        System.err.println("Warning: CgTansformer skips " + unit.toString());
//
//        return flag;
//    }


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
//    if (handlerClass.getName().equals("android.os.Handler")){
//        // TODO: This requires backward flow analysis to trace back to the actual class
//        System.err.println("Warning: CgTansformer skips " + unit.toString());
//
//        return flag;
//    }

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
//    if (messengerClass.getName().equals("android.os.Messenger")){
//        // TODO: This requires backward flow analysis to trace back to the actual class
//        System.err.println("Warning: CgTansformer skips " + unit.toString());
//
//        return flag;
//    }
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
  
  private boolean isInterfaceInvoke(InvokeExpr invokeExpr,
			SootMethodRef methodRef, Unit unit) {
		/*
		 * here only check if this is an interface invoke
		 * and see if it's necessary to handle this
		 * case 1: there is an example observed when running real-world app, even it's an interface invoke, but
		 * soot class hierarchy cannot treat it as an interface
		 */
  	if(!(invokeExpr instanceof InterfaceInvokeExpr)) return false;
  	

		SootClass interfaceClass = invokeExpr.getMethodRef().declaringClass();
		
		if(!interfaceClass.isInterface()) return false;

		return true;
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
  
  private void addHandlerEdges(SootMethod method, Stmt stmt, CallGraph cg) {
    SootClass handlerClass = stmt.getInvokeExpr().getMethodRef().declaringClass();
    SootClass handlerSuperClass = Scene.v().getSootClass("android.os.Handler");
    
    Body body = method.getActiveBody();
    PatchingChain<Unit> units = body.getUnits();
    
    AsyncTaskResolver atr = new AsyncTaskResolver(method, stmt, cg, true); 
    ArrayList<SootClass> classesToConnect = new ArrayList<SootClass>();
    classesToConnect = (ArrayList<SootClass>) atr.getAsyncTaskClasses();
    
//    if (handlerClass.equals(handlerSuperClass)) {
//        /*
//         * TODO: This needs to trace back in order to get the right class. 
//         * Currently, it doesn't do it. It's rather CHEX-like.
//         */
//        System.err.println("Warning: CgTansformer goes CHEX for " + stmt.toString());
//
//        Hierarchy hierarchy = scene.getActiveHierarchy();
//        classesToConnect.addAll(hierarchy.getSubclassesOf(handlerSuperClass));
//    } else {
//        classesToConnect.add(handlerClass);
//    }
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

        if(!(stmt.getInvokeExpr() instanceof InstanceInvokeExpr)) return;
        
        InstanceInvokeExpr expr = (InstanceInvokeExpr) stmt.getInvokeExpr();
        Value baseValue = expr.getBase();
        SpecialInvokeExpr newExpr = 
                Jimple.v().newSpecialInvokeExpr((Local) baseValue,
                        handleMessageMethod.makeRef(), expr.getArgs());
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
//    SootClass handlerClass = scene.getSootClass("android.os.Handler");
//
//    Hierarchy hierarchy = scene.getActiveHierarchy();
//    for (SootClass subClass: hierarchy.getSubclassesOf(handlerClass)) {
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
      
        Value baseValue = messeniie.getBase();
        SpecialInvokeExpr newExpr = 
                Jimple.v().newSpecialInvokeExpr((Local) baseValue,
                        handleMessageMethod.makeRef(), messeniie.getArgs());
        Stmt newInvokeStmt = Jimple.v().newInvokeStmt(newExpr);
        units.insertBefore(newInvokeStmt, stmt);
        cg.addEdge(new Edge(method, newInvokeStmt, handleMessageMethod));

        Edge e = new Edge(method, newInvokeStmt, handleMessageMethod);            
    }
    units.remove(stmt);
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
      Value baseValue = ((InterfaceInvokeExpr)invokeExpr).getBase();
      SpecialInvokeExpr newExpr 
      	= Jimple.v().newSpecialInvokeExpr((Local) baseValue,
            implMethod.makeRef(), invokeExpr.getArgs());
      Stmt newInvokeStmt = Jimple.v().newInvokeStmt(newExpr);
      units.insertBefore(newInvokeStmt, stmt);
      cg.addEdge(new Edge(method, newInvokeStmt, implMethod));
      replaced = true;

		}
		if(replaced)  units.remove(stmt);
		
	}

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
