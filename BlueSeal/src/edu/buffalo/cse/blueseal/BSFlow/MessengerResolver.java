/*
 * Feng Shen 7/9/2013

 * this class is used to retrieve the real class for messenger objects
 * create the relationship between messenger classes and handler classes
 */
/*
 * The goal of this resolver is to find the connection among messenger to handler
 * Messenger is initialized in Two ways:
 * 1. new Messenger(Handler)
 * 2. new Messenger(IBinder)
 * 
 * For case 1: we can easily connect messenger to corresponding handler
 * 
 * For case 2: This is usually used in Service case
 * (1)A service should include one or more handlers in it
 * (2)A messenger will be defined in ServiceConnection, as new Messenger(IBinder)
 * (3)The IBinder is returned by the Service
 * (4)to bind a service, we need to use ServiceConnection object as following:
 * 		bindService(Intent, ServiceConnection, int flags);
 * (5) In usual, Service is used to create the Intent obj required by Service
 *	To sum up, the flow is:
 *	Handler-->Service Class-->Intent-(bindService)->ServiceConnection-->Messenger
 * 
 */
package edu.buffalo.cse.blueseal.BSFlow;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import edu.buffalo.cse.blueseal.BSG.BlueSealGraph;

import soot.Body;
import soot.Hierarchy;
import soot.Local;
import soot.MethodOrMethodContext;
import soot.PatchingChain;
import soot.RefType;
import soot.ResolutionFailedException;
import soot.Scene;
import soot.SootClass;
import soot.SootField;
import soot.SootMethod;
import soot.Type;
import soot.Unit;
import soot.Value;
import soot.ValueBox;
import soot.JastAddJ.AssignExpr;
import soot.jimple.AnyNewExpr;
import soot.jimple.AssignStmt;
import soot.jimple.ClassConstant;
import soot.jimple.FieldRef;
import soot.jimple.InstanceFieldRef;
import soot.jimple.InvokeExpr;
import soot.jimple.NewExpr;
import soot.jimple.SpecialInvokeExpr;
import soot.jimple.Stmt;
import soot.jimple.toolkits.callgraph.CallGraph;
import soot.jimple.toolkits.callgraph.ReachableMethods;
import soot.toolkits.scalar.ArraySparseSet;
import soot.util.Chain;
import soot.util.queue.QueueReader;

public class MessengerResolver {


	private static Hierarchy hierarchy;
	private SootMethod method_;
	private Stmt stmt_;
	private CallGraph cg_;
	private static Map<SootMethod, Map<Unit, ArraySparseSet>> methodSum = 
			new HashMap<SootMethod, Map<Unit, ArraySparseSet>>();
	private static Map<SootMethod, BlueSealGraph> methodBSG_;
	
	public static Map<SootClass, List<SootClass>> messengerToHandler = 
			new HashMap<SootClass, List<SootClass>>();
	public MessengerResolver(){
		hierarchy = Scene.v().getActiveHierarchy();
		resolve();
	}
	public MessengerResolver(SootMethod method, Stmt stmt, 
			CallGraph cg){
		this.method_ = method;
		this.stmt_ = stmt;
		this.cg_ = cg;
		hierarchy = Scene.v().getActiveHierarchy();
		resolve();
	}

	public MessengerResolver(
			Map<SootMethod, Map<Unit, ArraySparseSet>> methodSummary, 
			Map<SootMethod, BlueSealGraph> methodBSG) {
		this.methodSum = methodSummary;
		this.cg_ = Scene.v().getCallGraph();
		this.methodBSG_ = methodBSG;
		hierarchy = Scene.v().getActiveHierarchy();
		resolve();
		
		/*
		 * Feng Shen 09/18/2013 this is used for the new way to resolve the messenger
		 * TODO: I am stuck at resolve ServiceConnection to the ServiceClass through bindService() 
		 * may revisit later to see if i can solve this
		 */
		//connectServiceHandler();
		//testIntent();
	}
	private void resolve() {
		
		ReachableMethods rms = Scene.v().getReachableMethods();
		QueueReader<MethodOrMethodContext> rmIt = rms.listener();
		
		while(rmIt.hasNext()){
			SootMethod sootMethod = rmIt.next().method();
			
			if(!sootMethod.hasActiveBody()) continue;
			
			Body body = sootMethod.retrieveActiveBody();
			PatchingChain<Unit> units = body.getUnits();
			
			for(Unit unit : units){
				
				if(!(unit instanceof AssignStmt)) continue;
				
				Value val = ((AssignStmt)unit).getRightOp();
				
				if(!(val instanceof NewExpr)) continue;

				NewExpr newExpr = (NewExpr)val;
				RefType baseType = newExpr.getBaseType();
				SootClass baseClassType = baseType.getSootClass();
				
				//we only care messenger & its subClass objects
				if(!CgTransformer.messengerSootClasses.contains(baseClassType)) continue;

				//after determine the messenger class, we need to find the unit that calls the 
				//constructor. The stmt should be able to be found locally
				PatchingChain<Unit> localUnits = body.getUnits();
				//retrieve the local variable of the messenger object
				Value messOb = ((AssignStmt)unit).getLeftOp();
				
				for(Unit initUnit : localUnits){
					if(!(((Stmt)initUnit).containsInvokeExpr())) continue;

					InvokeExpr ie = ((Stmt)initUnit).getInvokeExpr();
					if(!(ie instanceof SpecialInvokeExpr)) continue;

					SpecialInvokeExpr sie = (SpecialInvokeExpr)ie;
					Value base = sie.getBase();
					if(!(base.equals(messOb)
							&& sie.getMethod().getName().contains("init")
							) )continue;
					//now we need to extract the handler/binder object that initialize the messenger
					List<Value> args = sie.getArgs();
					int sizeOfArgs = args.size();
					for(int i = 0; i < sizeOfArgs; i++){
						Type type = sie.getMethod().getParameterType(i);
						if(!(type instanceof RefType)) continue;
						RefType refType = (RefType)type;
						//if messenger is initialized by a handler
						if(CgTransformer.handlerSootClasses.contains(refType.getSootClass())){
							//here, we need to resolve the actual variable type
							Value var = sie.getArg(i);
							AsyncTaskResolver varTypeResolver = 
									new AsyncTaskResolver(var, sootMethod, (Stmt)initUnit, cg_);
							List<SootClass> varTypes = varTypeResolver.getAsyncTaskClasses();
							if(!messengerToHandler.containsKey(baseClassType)){
								messengerToHandler.put(baseClassType, varTypes);
							}else{
								List<SootClass> newList = messengerToHandler.get(baseClassType);
								newList.addAll(varTypes);
								messengerToHandler.put(baseClassType, newList);
							}
						}
					}
				}
			}
			
			//next we need to consider that messenger is initialized by a binder
			//in this case, we only need to check all the bindService() method to retrieve the 
			//relation between messenger and handler
			for(Unit unit : units){
				if(!((Stmt)unit).containsInvokeExpr()) continue;
				
				SootMethod invokeMethod = null;
				try{
					invokeMethod = ((Stmt)unit).getInvokeExpr().getMethod();
				}catch(ResolutionFailedException e){
					continue;
				}
				
				if(!invokeMethod.getName().equals("bindService")) continue;
				/*
				 * TODO: here we need to extract the service and serviceConnection
				 * the service contains the Handler, serviceConnection contain the Messenger Type
				 * find this handler and messenger, add to the map
				 */
				if(!methodSum.containsKey(sootMethod)) continue;
				
				ArraySparseSet set = methodSum.get(sootMethod).get(unit);
				List<SootClass> relatedClass = new ArrayList<SootClass>();
				for(Iterator<Unit> it = set.iterator(); it.hasNext();){
					Stmt stmt = (Stmt)it.next();

					if(stmt instanceof AssignStmt){
						Value rightOp = ((AssignStmt)stmt).getRightOp();
						//if this stmt is a new expr
						if(rightOp instanceof NewExpr){
							NewExpr newExpr = (NewExpr)rightOp;
							SootClass sc = newExpr.getBaseType().getSootClass();
							relatedClass.add(sc);
						}
						//taking care of classConstant case
						//because an intent may be initialized by a .class
						else if(rightOp instanceof ClassConstant){
							String className = ((ClassConstant)rightOp).getValue();
							className = className.replace('/', '.');
//							int index = className.lastIndexOf('/');
//							String subString = className.substring(index+1);
							if(Scene.v().containsClass(className)){
								SootClass sc = Scene.v().getSootClass(className);
								relatedClass.add(sc);
							}
						}
					}
				}
//				SootClass serviceSuperClass=null;
//				List<SootClass> newSubClass=null;
//				if(Scene.v().containsClass("android.app.Service")){
//					serviceSuperClass = Scene.v().getSootClass("android.app.Service");
//					newSubClass= hierarchy.getSubclassesOfIncluding(serviceSuperClass);
//				}
//				if(serviceSuperClass == null) continue;
//				
				Set<SootClass> servSubClass = CgTransformer.serviceClasses;
				Set<SootClass>  serviceConnSubClass = CgTransformer.servConnClasses;
				List<SootClass> messClass = new ArrayList<SootClass>();
				List<SootClass> handClass = new ArrayList<SootClass>();
				for(SootClass list : relatedClass){
					List<SootMethod> methods = list.getMethods();
					//find all the sub class of "android.content.ServiceConnection"
					//&& "android.app.service"
					/*
					 * TODO: now we only consider messengers defined in the class
					 *  does not handle assigned from other variables outside the class
					 */
					if(serviceConnSubClass.contains(list)){
						//this one, we are looking for related messenger
						for(SootMethod m : methods){
							if(!m.hasActiveBody()) continue;
							Body mBody = m.retrieveActiveBody();
							PatchingChain<Unit> mUnits = mBody.getUnits();
							for(Unit mU : mUnits){
								if(!(mU instanceof AssignStmt)) continue;
								
								Value rightOp = ((AssignStmt)mU).getRightOp();
								if(!(rightOp instanceof NewExpr)) continue;
								
								SootClass exprBaseClass = ((NewExpr)rightOp).getBaseType().getSootClass();
								if(CgTransformer.messengerSootClasses.contains(exprBaseClass)){
									messClass.add(exprBaseClass);
								}
							}
						}
					}else if(servSubClass.contains(list)){
						//this one, we are looking for related handler
						/*
						 * TODO: same as messenger, only consider defined handlers inside class
						 */
						for(SootMethod m : methods){
							if(!m.hasActiveBody()) continue;
							Body mBody = m.retrieveActiveBody();
							PatchingChain<Unit> mUnits = mBody.getUnits();
							for(Unit mU : mUnits){
								if(!(mU instanceof AssignStmt)) continue;
								
								Value rightOp = ((AssignStmt)mU).getRightOp();
								if(!(rightOp instanceof NewExpr)) continue;
								
								SootClass exprBaseClass = ((NewExpr)rightOp).getBaseType().getSootClass();
								if(CgTransformer.handlerSootClasses.contains(exprBaseClass)){
									messClass.add(exprBaseClass);
								}
							}
						}
					}
					
				}
				
				//after finding all related classes, we add them to the map
				for(SootClass stc : messClass){
					if(messengerToHandler.containsKey(stc)){
						List<SootClass> newSet = messengerToHandler.get(stc);
						newSet.addAll(handClass);
						messengerToHandler.put(stc, newSet);
					}else{
						messengerToHandler.put(stc, handClass);
					}
				}
			}
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
    
    /*
     * the following method tries to find connection between Service Class and Handler Class
     */
    private HashMap<SootClass, List<SootClass>> connectServiceHandler(){
    	Set<SootClass>	handlers = CgTransformer.handlerSootClasses;
    	Set<SootClass>	services = CgTransformer.serviceClasses;
    	HashMap<SootClass, List<SootClass>> map = new HashMap<SootClass, List<SootClass>>();
    	
    	for(SootClass sc : services){
    		List<SootMethod> methods = sc.getMethods();
    		List<SootClass> list = new ArrayList<SootClass>();
    		for(SootMethod method : methods){
    			if(method.hasActiveBody()){
    				Body body = method.retrieveActiveBody();
    				List<ValueBox> defs = body.getDefBoxes();
    				
    				for(ValueBox def : defs){
    					String className = def.getValue().getType().toString();
    					for(SootClass handler : handlers){
    						if(handler.getName().equals(className)){
    							list.add(handler);
    						}
    					}
    				}
    			}
    		}
    		map.put(sc, list);
    	}
		return map;
    	
    }
    
    /*
     * find connection between Service to Intent
     * new Intent()
     */
    private void testIntent(){
    	Set<SootMethod> reachableMethods = CgTransformer.reachableMethods_;
    	for(SootMethod method : reachableMethods){
    		if(!method.hasActiveBody()) continue;
    		
    		Body body = method.retrieveActiveBody();
    		PatchingChain<Unit> units = body.getUnits();
    		for(Unit unit : units){
    			if(!((Stmt)unit).containsInvokeExpr()) continue;
    			
    			InvokeExpr invoke = ((Stmt)unit).getInvokeExpr();
    			SootMethod invokeM = invoke.getMethod();
    			if(invokeM.getName().equals("bindService")
    					&&invokeM.getDeclaringClass().getName().equals("android.content.ContextWrapper")){
    				System.out.println("bindService:"+invokeM.toString());
    				
    				 ArraySparseSet flowsInto = MessengerResolver.methodSum.get(method).get(unit);
    				 System.out.println("before~~~~~	~~~");
    				 for(Iterator it = flowsInto.iterator(); it.hasNext(); ){
    					 Unit flowUnit = (Unit)it.next();
    					 System.out.println("......bind.....unit:"+flowUnit.toString());
    					 if(((Stmt)flowUnit) instanceof AssignStmt){
    						 Value rightOP = ((AssignStmt)flowUnit).getRightOp();
    						 Value leftOP = ((AssignStmt)flowUnit).getLeftOp();
    						 //find all service classes related to this bindService
    						 if(rightOP instanceof ClassConstant){
    							 String classString = ((ClassConstant)rightOP).getValue();
    							 String className = classString.replace('/', '.');
    							 SootClass sc = Scene.v().getSootClass(className);
    							 
    							 if(CgTransformer.serviceClasses.contains(sc)){
    								 //connect this service class with ServiceConnection
    								 System.out.println("~~~~classConstant:"+sc.getName());
    							 }
    						 }
    						 //find all possible serviceConnection to this bindService
    						 if(leftOP.getType() instanceof RefType){
								  
								 SootClass leftClass = ((RefType)leftOP.getType()).getSootClass();
								 if(CgTransformer.servConnClasses.contains(leftClass)){
	    							 System.out.println("service connection");
	    						 }
							 }
    					 }
    				 }
    				 System.out.println("after~~~~~~	~~~~");
    			}
    		}
    	}
    }
}
