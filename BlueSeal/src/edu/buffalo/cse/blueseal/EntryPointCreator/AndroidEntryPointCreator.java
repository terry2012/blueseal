package edu.buffalo.cse.blueseal.EntryPointCreator;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import soot.ArrayType;
import soot.Body;
import soot.BooleanType;
import soot.ByteType;
import soot.CharType;
import soot.EntryPoints;
import soot.G;
import soot.IntType;
import soot.Local;
import soot.Modifier;
import soot.PatchingChain;
import soot.RefType;
import soot.Scene;
import soot.ShortType;
import soot.SootClass;
import soot.SootMethod;
import soot.Type;
import soot.Unit;
import soot.Value;
import soot.VoidType;
import soot.dava.internal.javaRep.DIntConstant;
import soot.javaToJimple.LocalGenerator;
import soot.jimple.AssignStmt;
import soot.jimple.DoubleConstant;
import soot.jimple.FloatConstant;
import soot.jimple.IntConstant;
import soot.jimple.InvokeExpr;
import soot.jimple.Jimple;
import soot.jimple.JimpleBody;
import soot.jimple.LongConstant;
import soot.jimple.NewArrayExpr;
import soot.jimple.NewExpr;
import soot.jimple.NullConstant;
import soot.jimple.Stmt;
import soot.jimple.StringConstant;
import soot.jimple.internal.JAssignStmt;

public class AndroidEntryPointCreator {
	
	private static List<SootMethod> initialEntryPoints;
	public static final String DUMMY_MAIN_CLASS_NAME = "dummyMainClass";
	public static final String DUMMY_MAIN_METHOD_NAME = "dummyMainMethod";
	
	private JimpleBody body;
	private LocalGenerator generator;
	private int conditionCounter;
	private Value intCounter;
	private Map<SootClass, Local> localVarsForClasses = new HashMap<SootClass, Local>();
	
	
	public AndroidEntryPointCreator(List<SootMethod> entrypoints){
		this.initialEntryPoints = entrypoints;
	}
	
	public SootMethod createDummnyMain(){
		SootMethod emptySootMethod = createEmptyMainMethod(Jimple.v().newBody());
		return createDummyMainInternal(emptySootMethod);
	}

	private SootMethod createEmptyMainMethod(JimpleBody body) {
		final SootClass mainClass;
		String methodName = DUMMY_MAIN_METHOD_NAME;
		if (Scene.v().containsClass(DUMMY_MAIN_CLASS_NAME)) {
			int methodIndex = 0;
			mainClass = Scene.v().getSootClass(DUMMY_MAIN_CLASS_NAME);
			while (mainClass.declaresMethodByName(methodName))
				methodName = DUMMY_MAIN_METHOD_NAME + "_" + methodIndex++;
		}
		else {
			mainClass = new SootClass(DUMMY_MAIN_CLASS_NAME);
			Scene.v().addClass(mainClass);
		}
		
		SootMethod mainMethod = new SootMethod(methodName, new ArrayList<Type>(), VoidType.v());
		body.setMethod(mainMethod);
		mainMethod.setActiveBody(body);
		mainClass.addMethod(mainMethod);
		// First add class to scene, then make it an application class
		// as addClass contains a call to "setLibraryClass" 
		mainClass.setApplicationClass();
		mainMethod.setModifiers(Modifier.PUBLIC | Modifier.STATIC);
		return mainMethod;
	}

	private SootMethod createDummyMainInternal(SootMethod emptySootMethod) {
		SootMethod mainMethod = emptySootMethod;
 		body = (JimpleBody) emptySootMethod.getActiveBody();
		generator = new LocalGenerator(body);
		
		// add entrypoint calls
		conditionCounter = 0;
		intCounter = generator.generateLocal(IntType.v());

		JAssignStmt assignStmt = new JAssignStmt(intCounter, IntConstant.v(conditionCounter));
		body.getUnits().add(assignStmt);
		
		for(SootMethod entry : initialEntryPoints){
			boolean instanceNeeded = false;
			if(!entry.isStatic()){
				instanceNeeded = true;
			}

			SootClass sootClass = entry.getDeclaringClass();
			if(instanceNeeded&& !localVarsForClasses.containsKey(sootClass)){
				Set<SootClass> constructionStack = new HashSet<SootClass>();
				Set<SootClass> parentClasses = new HashSet<SootClass>();
				Local localVal = generateClassConstructor(sootClass, body, constructionStack, parentClasses);
				
				if (localVal == null) {
					continue;
				}
				localVarsForClasses.put(sootClass, localVal);
			}
			
			Local classLocal = localVarsForClasses.get(sootClass);
			buildMethodCall(entry, body, classLocal, generator);
		}
		return mainMethod;
	}
	
	private Local generateClassConstructor(SootClass createdClass, Body body,
			Set<SootClass> constructionStack, Set<SootClass> parentClasses) {
		if (createdClass.isPhantom() || createdClass.isPhantomClass()) {
//			logger.warn("Cannot generate constructor for phantom class {}", createdClass.getName());
//			failedClasses.add(createdClass);
			return null;
		}
		// if sootClass is simpleClass:
		if (isSimpleType(createdClass.toString())) {
			Local varLocal =  generator.generateLocal(getSimpleTypeFromType(createdClass.getType()));
			
			AssignStmt aStmt = Jimple.v().newAssignStmt(varLocal, getSimpleDefaultValue(createdClass.toString()));
			body.getUnits().add(aStmt);
			return varLocal;
		}
		
		LocalGenerator generator = new LocalGenerator(body);
		boolean isInnerClass = createdClass.getName().contains("$");
		String outerClass = isInnerClass ? createdClass.getName().substring
				(0, createdClass.getName().lastIndexOf("$")) : "";
				
		// Make sure that we don't run into loops
		if (!constructionStack.add(createdClass)) {
		
			Local tempLocal = generator.generateLocal(RefType.v(createdClass));			
			AssignStmt assignStmt = Jimple.v().newAssignStmt(tempLocal, NullConstant.v());
			body.getUnits().add(assignStmt);
			return tempLocal;
		}
		
		if(createdClass.isInterface() || createdClass.isAbstract()){
//			System.out.println(" the entry is either an interface or abstract. Not handle this now.");
//			System.out.println(createdClass.getName());
			return null;
		}
		for (SootMethod currentMethod : createdClass.getMethods()) {
			if (currentMethod.isPrivate() || !currentMethod.isConstructor())
				continue;
			
			List<Value> params = new LinkedList<Value>();
			for (Type type : currentMethod.getParameterTypes()) {
				// We need to check whether we have a reference to the
				// outer class. In this case, we do not generate a new
				// instance, but use the one we already have.
				String typeName = type.toString().replaceAll("\\[\\]]", "");
				if (type instanceof RefType
						&& isInnerClass && typeName.equals(outerClass)
						&& this.localVarsForClasses.containsKey(typeName))
					params.add(this.localVarsForClasses.get(typeName));
				else
					params.add(getValueForType(body, generator, type, constructionStack, parentClasses));
			}

			// Build the "new" expression
			NewExpr newExpr = Jimple.v().newNewExpr(RefType.v(createdClass));
			Local tempLocal = generator.generateLocal(RefType.v(createdClass));			
			AssignStmt assignStmt = Jimple.v().newAssignStmt(tempLocal, newExpr);
			body.getUnits().add(assignStmt);		

			// Create the constructor invocation
			InvokeExpr vInvokeExpr;
			if (params.isEmpty() || params.contains(null))
				vInvokeExpr = Jimple.v().newSpecialInvokeExpr(tempLocal, currentMethod.makeRef());
			else
				vInvokeExpr = Jimple.v().newSpecialInvokeExpr(tempLocal, currentMethod.makeRef(), params);

			// Make sure to store return values
			if (!(currentMethod.getReturnType() instanceof VoidType)) { 
				Local possibleReturn = generator.generateLocal(currentMethod.getReturnType());
				AssignStmt assignStmt2 = Jimple.v().newAssignStmt(possibleReturn, vInvokeExpr);
				body.getUnits().add(assignStmt2);
			}
			else
				body.getUnits().add(Jimple.v().newInvokeStmt(vInvokeExpr));
				
			return tempLocal;
		}
		return null;
	}
	
	private Value getValueForType(Body body, LocalGenerator gen,
			Type tp, Set<SootClass> constructionStack, Set<SootClass> parentClasses) {
		// Depending on the parameter type, we try to find a suitable
		// concrete substitution
		if (isSimpleType(tp.toString()))
			return getSimpleDefaultValue(tp.toString());
		else if (tp instanceof RefType) {
			SootClass classToType = ((RefType) tp).getSootClass();
			
			if(classToType != null){
				// If we have a parent class compatible with this type, we use
				// it before we check any other option
				for (SootClass parent : parentClasses)
					if (isCompatible(parent, classToType)) {
						Value val = this.localVarsForClasses.get(parent.getName());
						if (val != null)
							return val;
					}

				// Create a new instance to plug in here
				Value val = generateClassConstructor(classToType, body, constructionStack, parentClasses);
				
				// If we cannot create a parameter, we try a null reference.
				// Better than not creating the whole invocation...
				if(val == null)
					return NullConstant.v();
				
				return val;
			}
		}
		else if (tp instanceof ArrayType) {
			Value arrVal = buildArrayOfType(body, gen, (ArrayType) tp, constructionStack, parentClasses);
			if (arrVal == null){
				return NullConstant.v();
			}
			return arrVal;
		}
		else {
			return null;
		}
		throw new RuntimeException("Should never see me");
	}
	
	protected static boolean isSimpleType(String t) {
		if (t.equals("java.lang.String")
				|| t.equals("void")
				|| t.equals("char")
				|| t.equals("byte")
				|| t.equals("short")
				|| t.equals("int")
				|| t.equals("float")
				|| t.equals("long")
				|| t.equals("double")
				|| t.equals("boolean")) {
			return true;
		} else {
			return false;
		}
	}
	
	protected Value getSimpleDefaultValue(String t) {
		if (t.equals("java.lang.String"))
			return StringConstant.v("");
		if (t.equals("char"))
			return DIntConstant.v(0, CharType.v());
		if (t.equals("byte"))
			return DIntConstant.v(0, ByteType.v());
		if (t.equals("short"))
			return DIntConstant.v(0, ShortType.v());
		if (t.equals("int"))
			return IntConstant.v(0);
		if (t.equals("float"))
			return FloatConstant.v(0);
		if (t.equals("long"))
			return LongConstant.v(0);
		if (t.equals("double"))
			return DoubleConstant.v(0);
		if (t.equals("boolean"))
			return DIntConstant.v(0, BooleanType.v());

		//also for arrays etc.
		return G.v().soot_jimple_NullConstant();
	}
	
	
	protected boolean isCompatible(SootClass actual, SootClass expected) {
		SootClass act = actual;
		while (true) {
			// Do we have a direct match?
			if (act.getName().equals(expected.getName()))
				return true;
			
			// If we expect an interface, the current class might implement it
			if (expected.isInterface())
				for (SootClass intf : act.getInterfaces())
					if (intf.getName().equals(expected.getName()))
						return true;
			
			// If we cannot continue our search further up the hierarchy, the
			// two types are incompatible
			if (!act.hasSuperclass())
				return false;
			act = act.getSuperclass();
		}
	}
	
	private Value buildArrayOfType(Body body, LocalGenerator gen, ArrayType tp,
			Set<SootClass> constructionStack, Set<SootClass> parentClasses) {
		Local local = gen.generateLocal(tp);

		// Generate a new single-element array
		NewArrayExpr newArrayExpr = Jimple.v().newNewArrayExpr(tp.getElementType(),
				IntConstant.v(1));
		AssignStmt assignArray = Jimple.v().newAssignStmt(local, newArrayExpr);
		body.getUnits().add(assignArray);
		
		// Generate a single element in the array
		AssignStmt assign = Jimple.v().newAssignStmt
				(Jimple.v().newArrayRef(local, IntConstant.v(0)),
				getValueForType(body, gen, tp.getElementType(), constructionStack, parentClasses));
		body.getUnits().add(assign);
		return local;
	}
	
	protected Stmt buildMethodCall(SootMethod methodToCall, Body body,
			Local classLocal, LocalGenerator gen) {
		return buildMethodCall(methodToCall, body, classLocal, gen,
				Collections.<SootClass>emptySet());
	}

	protected Stmt buildMethodCall(SootMethod methodToCall, Body body,
			Local classLocal, LocalGenerator gen,
			Set<SootClass> parentClasses){
		assert methodToCall != null : "Current method was null";
		assert body != null : "Body was null";
		assert gen != null : "Local generator was null";
		
		if (classLocal == null && !methodToCall.isStatic()) {
			return null;
		}
		
		final InvokeExpr invokeExpr;
		List<Value> args = new LinkedList<Value>();
		if(methodToCall.getParameterCount()>0){
			for (Type tp : methodToCall.getParameterTypes())
				args.add(getValueForType(body, gen, tp, new HashSet<SootClass>(), parentClasses));
			
			if(methodToCall.isStatic())
				invokeExpr = Jimple.v().newStaticInvokeExpr(methodToCall.makeRef(), args);
			else {
				assert classLocal != null : "Class local method was null for non-static method call";
				if (methodToCall.isConstructor())
					invokeExpr = Jimple.v().newSpecialInvokeExpr(classLocal, methodToCall.makeRef(),args);
				else
					invokeExpr = Jimple.v().newVirtualInvokeExpr(classLocal, methodToCall.makeRef(),args);
			}
		}else{
			if(methodToCall.isStatic()){
				invokeExpr = Jimple.v().newStaticInvokeExpr(methodToCall.makeRef());
			}else{
				assert classLocal != null : "Class local method was null for non-static method call";
				if (methodToCall.isConstructor())
					invokeExpr = Jimple.v().newSpecialInvokeExpr(classLocal, methodToCall.makeRef());
				else
					invokeExpr = Jimple.v().newVirtualInvokeExpr(classLocal, methodToCall.makeRef());
			}
		}
		 
		Stmt stmt;
		if (!(methodToCall.getReturnType() instanceof VoidType)) {
			Local returnLocal = gen.generateLocal(methodToCall.getReturnType());
			stmt = Jimple.v().newAssignStmt(returnLocal, invokeExpr);
			
		} else {
			stmt = Jimple.v().newInvokeStmt(invokeExpr);
		}
		body.getUnits().add(stmt);
		
		// Clean up
		for (Object val : args)
			if (val instanceof Local && ((Value) val).getType() instanceof RefType)
				body.getUnits().add(Jimple.v().newAssignStmt((Value) val, NullConstant.v()));
		
		return stmt;
	}
	
	private Type getSimpleTypeFromType(Type type) {
		if (type.toString().equals("java.lang.String")) {
			assert type instanceof RefType;
			return RefType.v(((RefType) type).getSootClass());
		}
		if (type.toString().equals("void"))
			return soot.VoidType.v();
		if (type.toString().equals("char"))
			return soot.CharType.v();
		if (type.toString().equals("byte"))
			return soot.ByteType.v();
		if (type.toString().equals("short"))
			return soot.ShortType.v();
		if (type.toString().equals("int"))
			return soot.IntType.v();
		if (type.toString().equals("float"))
			return soot.FloatType.v();
		if (type.toString().equals("long"))
			return soot.LongType.v();
		if (type.toString().equals("double"))
			return soot.DoubleType.v();
		if (type.toString().equals("boolean"))
			return soot.BooleanType.v();
		throw new RuntimeException("Unknown simple type: " + type);
	}


	// for debug purpose
	public void printDummyMain(){
		PatchingChain<Unit> units = body.getUnits();
		for(Unit u : units){
			System.out.println(u.toString());
		}
	}

}
