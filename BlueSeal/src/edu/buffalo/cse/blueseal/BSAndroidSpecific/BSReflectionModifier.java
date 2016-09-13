/*
 * this class is to handle reflection cases, 
 * insert real reflection methods to callgraph
 * 
 * this takes care of one reflection invoke at one time
 */

package edu.buffalo.cse.blueseal.BSAndroidSpecific;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import edu.buffalo.cse.blueseal.BSCallgraph.BSCallGraphAugmentor;

import soot.Body;
import soot.Local;
import soot.PatchingChain;
import soot.RefType;
import soot.Scene;
import soot.SootClass;
import soot.SootMethod;
import soot.SootMethodRef;
import soot.Type;
import soot.Unit;
import soot.Value;
import soot.jimple.ArrayRef;
import soot.jimple.AssignStmt;
import soot.jimple.ClassConstant;
import soot.jimple.IntConstant;
import soot.jimple.InvokeExpr;
import soot.jimple.InvokeStmt;
import soot.jimple.Jimple;
import soot.jimple.NewArrayExpr;
import soot.jimple.NewExpr;
import soot.jimple.NullConstant;
import soot.jimple.StaticInvokeExpr;
import soot.jimple.Stmt;
import soot.jimple.VirtualInvokeExpr;
import soot.jimple.toolkits.callgraph.CallGraph;
import soot.jimple.toolkits.callgraph.Edge;
import soot.toolkits.graph.ExceptionalUnitGraph;
import soot.toolkits.scalar.ArraySparseSet;
import soot.toolkits.scalar.SimpleLocalDefs;
import soot.util.Chain;

public class BSReflectionModifier {
	private Stmt stmt = null;
	private SootMethod method = null;
	private CallGraph callgraph= null;
	private SimpleLocalDefs sld = null;
	private ExceptionalUnitGraph eug = null;
	private PatchingChain<Unit> units = null;
	private Body body = null;

	public BSReflectionModifier(SootMethod m, Stmt s, CallGraph cg){
		stmt = s;
		method = m;
		callgraph = cg;
		
		body = method.getActiveBody();
  	eug = new ExceptionalUnitGraph(body);
  	units = body.getUnits();
  	sld = new SimpleLocalDefs(eug);
	}
	
	public void modify(){

  	if(!method.hasActiveBody() ||
  			!method.getDeclaringClass().isApplicationClass()) return;
  	
  	System.out.println("[BlueSeal]:resolve reflection invokes @stmt:"+stmt.toString());

  	//first, find the real invoked class and method name
  	ReflectionInfo info = resolveReflectionInfo();
  		
  	//next, we need to track down the real arguments passed to the invoked method
  	List<Value> reflectionRealArguments = resolveReflectionRealArgs();

  	//last, we replace reflection invokes with real method invokes
  	insertReflectionEdges(info, reflectionRealArguments);
	}

	private void insertReflectionEdges(ReflectionInfo info, List<Value> reflectionRealArguments) {
		if(info == null) return;
  	String reflectionClassName = info.getClassName();
  	String reflectionMethodName = info.getMethodName();
  	List<Type> reflectionMethodArgTypes = info.getArgTypes();
  	
		if(reflectionClassName!=null 
  			&&reflectionMethodName!=null){
  		reflectionMethodName = reflectionMethodName.substring(1, 
  				reflectionMethodName.length()-1);
  		reflectionClassName = reflectionClassName.substring(1, 
  				reflectionClassName.length()-1);
  		SootClass reflectClass = null;
  		try{
  			reflectClass = Scene.v().forceResolve(reflectionClassName, SootClass.BODIES);
  		}catch(RuntimeException e){
  			//do nothing
  			return;
  		}
  		
  		//if this class is not a application class, then skip it
  		if(!reflectClass.isApplicationClass()) return;
  		
  		Scene.v().loadClassAndSupport(reflectionClassName);
  		
  		if(Scene.v().containsClass(reflectionClassName)){
  			
  			SootClass sootClass = Scene.v().getSootClass(reflectionClassName);
  			
  			if(sootClass.declaresMethodByName(reflectionMethodName)){

  				SootMethod reflectiveMethod = null;
  				try{    						
  					reflectiveMethod = sootClass.getMethod(reflectionMethodName, reflectionMethodArgTypes);
  				}catch(RuntimeException e){
  					//no method found, do nothing
  					return;
  				}
  				Body testbody = reflectiveMethod.retrieveActiveBody();
  				//create a new class instance
  				Chain<Local> locals = body.getLocals();
  				Local classObject = Jimple.v().newLocal("$r"+locals.size(), 
  						RefType.v(reflectionClassName));
  				locals.add(classObject);
  				//create a NewExpr
  				NewExpr newExpr = Jimple.v().newNewExpr(RefType.v(sootClass));
  				AssignStmt assignStmt = Jimple.v().newAssignStmt(classObject, newExpr);
  				units.insertBefore(assignStmt, stmt);
  				
  				//create a new method invoke expr
  				//first construct the arg list
  				//remove the first one, because it is the reflection instance object
  				InvokeExpr newInvokeExpr;
  				if(reflectiveMethod.isStatic()){
  					StaticInvokeExpr newStaticInvoke =
  							Jimple.v().newStaticInvokeExpr(reflectiveMethod.makeRef(),reflectionRealArguments);
  					newInvokeExpr = newStaticInvoke;
  				}else{
  					VirtualInvokeExpr newVirtualInvoke = 
    						Jimple.v().newVirtualInvokeExpr(classObject, reflectiveMethod.makeRef(),
    								reflectionRealArguments);
  					newInvokeExpr = newVirtualInvoke;
  				}
  				
  				//finally we find the right class and name
  				//create a new invoke method
  				if(stmt instanceof AssignStmt){
  					Value leftOp = ((AssignStmt)stmt).getLeftOp();
  					AssignStmt invokeAssign = Jimple.v().newAssignStmt(leftOp, newInvokeExpr);
  					units.insertAfter(invokeAssign, stmt);
  					callgraph.addEdge(new Edge(method, invokeAssign, reflectiveMethod));
    				System.out.println("[BlueSeal-Reflection:]reflection invoke replaced by:" +
    						invokeAssign.toString());
  				}else{
  					InvokeStmt newInvokeStmt = Jimple.v().newInvokeStmt(newInvokeExpr);
  					units.insertAfter(newInvokeStmt, stmt);
  					callgraph.addEdge(new Edge(method, newInvokeStmt, reflectiveMethod));
    				System.out.println("[BlueSeal-Reflection:]reflection invoke replaced by:" +
    						newInvokeExpr.toString());
  				}

  				BSCallGraphAugmentor.rebuiltReachableMethods.add(reflectiveMethod);
  				units.remove(stmt);
  			}
  		}
  	}
	}

	/*
	 * find out the real list of argments passed to reflection invoke
	 * @return: list of values passed; null if cannot resolve all the arguments
	 */
	private List<Value> resolveReflectionRealArgs() {
		//re-build the list of args passed to the real method
  	//for reflection "invoke", it takes only 2 arguments:1, object instance 2,list of arguments passed to the method
  	//if not, something is wrong, do nothing
		InvokeExpr invokeExpr = stmt.getInvokeExpr();
		
  	if(invokeExpr.getArgCount() > 2){
  		System.out.println("[BlueSeal-Reflection]: too many arguements passed to reflection invoke, skipping reflection resolving");
  		return null;
  	}
  	
  	Value argsList = invokeExpr.getArg(1);
  	//build-up the list of args
  	//create a temp array to hold all the parameter types
		Map<Integer, Value> argsArray = new HashMap<Integer, Value>();
		int argsArraySize = 0;
		
		if(argsList instanceof NullConstant) return new LinkedList<Value>();
		
		List<Unit> reflectArgDefs = sld.getDefsOfAt((Local)argsList, stmt);
		for(Iterator defIt = reflectArgDefs.iterator();defIt.hasNext();){
			//find the definition of new array allocation
			Unit argDef = (Unit)defIt.next();
			
			if(argDef instanceof AssignStmt){
				Value leftV = ((AssignStmt)argDef).getLeftOp();
				Value rightV = ((AssignStmt)argDef).getRightOp();
				if(leftV.equals(argsList)&&
						rightV instanceof NewArrayExpr){
					Value arraySize = ((NewArrayExpr)rightV).getSize();
					if(arraySize instanceof IntConstant){
						argsArraySize = ((IntConstant)arraySize).value;
					}
				}
			}
		}
		
		//Currently, take care of passing all the parameters' types using array
		//TODO: any other cases?
		//check all the elements in the parameter array
		for(Iterator pa = eug.getPredsOf(stmt).iterator(); pa.hasNext();){
			Stmt paramU = (Stmt) pa.next();
			
			//Currently only handle code that specifies all the parameters' type classes
			if(paramU instanceof AssignStmt){
				Value leftV = ((AssignStmt)paramU).getLeftOp();
				Value rightV = ((AssignStmt)paramU).getRightOp();
				
				if(leftV instanceof ArrayRef){
					Value base = ((ArrayRef)leftV).getBase();
					Value index = ((ArrayRef)leftV).getIndex();
					int vIndex = ((IntConstant)index).value;
					if(base.equals(argsList)){
						//find the array index and its type class, put into a temp array
						argsArray.put(vIndex, rightV);
					}
				}
			}
		}
		
		List<Value> reflectionRealArgs = new LinkedList<Value>();
		//after finding all the parameter types, add these into method parameter list in order
		for(int j = 0; j < argsArraySize; j++){
			if(!argsArray.containsKey(j)) return null;
			
			reflectionRealArgs.add(argsArray.get(j));
		}

		return reflectionRealArgs;
	}

	public ReflectionInfo resolveReflectionInfo(){
		
		if(!BSCallGraphAugmentor.methodSummary.containsKey(method)) return null;
	
		Map<Unit, ArraySparseSet> summaryFromFirstRoundAnalysis = BSCallGraphAugmentor.methodSummary.get(method);
		
		if(!summaryFromFirstRoundAnalysis.containsKey(stmt)) return null;
		
		String reflectionClassName = null;
		String reflectionMethodName = null;
		//create a temp array to hold all the parameter types
		List<Type> argTypes = null;
		
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
				reflectionClassName = invokeExpr.getArg(0).toString();
			}
			
			if(methodName.equals("getMethod")
					&&methodClassName.equals("java.lang.Class")
					&&returnType.equals("java.lang.reflect.Method")){
				reflectionMethodName = invokeExpr.getArg(0).toString();
				
				//there should be only tow arguments passed: 1. method name 2. array of parameterTypes
				if(invokeExpr.getArgCount() > 2){//there must be something wrong here
					System.out.println("[BlueSeal]:Reflection getMethod parameters error! Skip resolving this reflection!");
					continue;
				}
				
				argTypes = getMethodArgTypes(invokeExpr, unit);
			}
		}
		
		return new ReflectionInfo(reflectionClassName, reflectionMethodName, argTypes);
		
	}
	private List<Type> getMethodArgTypes(InvokeExpr invokeExpr, Unit unit) {
		List<Type> reflectionMethodArgTypes = new LinkedList<Type>();
		Map<Integer, Type> paramTypeMap = new HashMap<Integer, Type>();
		
		for(int i = 1; i < invokeExpr.getArgCount(); i++){
			int reflectiveMethodArgsSize = 0;
			//skip the first arg, because it's method name
			//find all the types for reflection getMethod
			Value argument = invokeExpr.getArg(i);
			
			if(argument instanceof NullConstant) continue;
			
			List<Unit> argDefs = sld.getDefsOfAt((Local) argument, unit);
			for(Iterator defIt = argDefs.iterator();defIt.hasNext();){
				//find the definition of new array allocation
				Unit argDef = (Unit)defIt.next();
				
				if(argDef instanceof AssignStmt){
					Value leftV = ((AssignStmt)argDef).getLeftOp();
					Value rightV = ((AssignStmt)argDef).getRightOp();
					
					if(leftV.equals(argument)&&
							rightV instanceof NewArrayExpr){
						Value arraySize = ((NewArrayExpr)rightV).getSize();
						if(arraySize instanceof IntConstant){
							reflectiveMethodArgsSize = ((IntConstant)arraySize).value;
						}
					}
				}
			}
			
			//Currently, take care of passing all the parameters' types using array
			//TODO: any other cases?
			//check all the elements in the parameter array
			for(Iterator pa = eug.getPredsOf(unit).iterator(); pa.hasNext();){
				Stmt paramU = (Stmt) pa.next();
				//Currently only handle code that specifies all the parameters' type classes
				if(paramU instanceof AssignStmt){
					Value leftV = ((AssignStmt)paramU).getLeftOp();
					Value rightV = ((AssignStmt)paramU).getRightOp();
					
					if(leftV instanceof ArrayRef){
						Value base = ((ArrayRef)leftV).getBase();
						Value index = ((ArrayRef)leftV).getIndex();
						int vIndex = ((IntConstant)index).value;
						if(base.equals(argument)
								&& rightV instanceof ClassConstant){

							System.out.println("reflection checking stmt:" +
									paramU.toString());
							//find the array index and its type class, put into a temp array
							String valueType = ((ClassConstant)rightV).value;
							valueType = valueType.replace('/', '.');
							valueType = valueType.replace("[", "");
							RefType refType = null;
							try{
								refType = RefType.v(valueType);
							}catch(Exception e){
								e.printStackTrace();
							}
							paramTypeMap.put(vIndex, refType);
						}
					}
				}
				
			}
			
			//after finding all the parameter types, add these into method parameter list in order
			for(int j = 0; j < reflectiveMethodArgsSize; j++){
				if(!paramTypeMap.containsKey(j)) return null;
				
				reflectionMethodArgTypes.add(paramTypeMap.get(j));
			}
		}
		return reflectionMethodArgTypes;
	}
	/*
	 * create a inner class to hold reflection invoke informations
	 */
	public class ReflectionInfo{
		
		String reflectionClassName = null;
		String reflectionMethodName = null;
		List<Type> reflectionMethodArgTypes = null;
		
		public ReflectionInfo(String className, String methodName, List argTypes){
			reflectionClassName = className;
			reflectionMethodName = methodName;
			reflectionMethodArgTypes = argTypes;
		}
		
		public String getClassName(){
			return this.reflectionClassName;
		}
		
		public String getMethodName(){
			return this.reflectionMethodName;
		}
		
		public List<Type> getArgTypes(){
			return this.reflectionMethodArgTypes;
		}
		
	}

}
