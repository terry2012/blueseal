package edu.buffalo.cse.blueseal.BSFlow;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import soot.Scene;
import soot.SootClass;
import soot.SootMethod;
import soot.SootMethodRef;
import soot.Value;
import soot.jimple.AssignStmt;
import soot.jimple.InvokeExpr;
import soot.jimple.StaticFieldRef;
import soot.jimple.Stmt;
import soot.jimple.StringConstant;

/*
 * this class determines whether a Jimple statement is a source or a sink or null
 */
public class SourceSinkDetection {
	public static String contentprovider;
	public SourceSinkDetection(){
	}
	
	public static boolean isSource(Stmt stmt){
		if(stmt.containsInvokeExpr()){
			InvokeExpr invokeExpr = stmt.getInvokeExpr();
			SootMethodRef method = invokeExpr.getMethodRef();
//			if(method.getName().equals("openFileInput")){
//				List<SootClass> supers = Scene.v().getActiveHierarchy()
//						.getSuperclassesOf(method.getDeclaringClass());
//				boolean isContextSubClass = false;
//				for(SootClass sc:supers){
//					if(sc.getName().equals("android.content.Context")){
//						isContextSubClass = true;
//					}
//				}
//				
//				if(isContextSubClass){
//					return true;
//				}
//			}
			
			if(method.name().equals("getText")){
				SootClass sootClass = method.declaringClass();
				if(!sootClass.isInterface()){
					List<SootClass> supers = Scene.v().getActiveHierarchy().getSuperclassesOf(sootClass);
					boolean isContextSubClass = false;
					for(SootClass sc:supers){
						if(sc.getName().equals("android.widget.EditText")){
							isContextSubClass = true;
						}
					}
					
					if(isContextSubClass){
						return true;
					}
				}
			}
		}
		return isSourceFromInputSources(stmt)
				|| isCPSource(stmt) || isLogSource(stmt) || isCPQuery(stmt);
	}
	
	public static boolean isSourceFromInputSources(Stmt stmt) {
		if (stmt.containsInvokeExpr()) {
			SootMethodRef methodRef = stmt.getInvokeExpr().getMethodRef();
			String methodRefName = methodRef.name();
			String decClass = methodRef.declaringClass().getName();
			for (String sourceClassStr: SourceSink.sources_.keySet()) {
				Pattern pat = Pattern.compile(sourceClassStr);
				Matcher match = pat.matcher(decClass);
				if (match.find()) {
					ArrayList<String> methodList = SourceSink.sources_.get(sourceClassStr);
					for (String method:methodList) {
						pat = Pattern.compile(method);
						match = pat.matcher(methodRef.name());
						if (match.find()) {
							return true;
						}
					}
				}
			}
		}
		return false;
	}
	
	public static boolean isCPSource(Stmt stmt) {
		if(stmt.containsInvokeExpr()){
			InvokeExpr parseie = stmt.getInvokeExpr();
			 SootClass defClass = parseie.getMethodRef().declaringClass();
			 String mName = parseie.getMethodRef().name();
			 if(defClass.getName().equals("android.net.Uri")&&mName.equals("parse")){
				 //get the argument
				 Value arg = parseie.getArg(0);
				 if(arg instanceof StringConstant){
					 if(SourceSink.CPSrcStrings.contains(arg.toString().substring(1, arg.toString().length()-1))){
						 contentprovider = arg.toString().substring(1, arg.toString().length()-1);
						 return true;
					 }
				 }
			 }
		}
		if(!(stmt instanceof AssignStmt)) return false;
		
		Value value = ((AssignStmt)stmt).getRightOp();
		
		if(value instanceof StaticFieldRef){ 
			if(SourceSink.CPSrcStrings.contains(value.toString())){
				contentprovider = value.toString();
				return true;
			}
		}
		return false;
	}
	
	
	public static boolean isLogSource(Stmt stmt) {
		if(!stmt.containsInvokeExpr()) return false;
		
		InvokeExpr ie = stmt.getInvokeExpr();
		SootMethodRef mref = ie.getMethodRef();
		if(!mref.declaringClass().getName().equals("java.lang.Runtime")
				|| !mref.name().equals("exec")
				|| !stmt.toString().contains("logcat"))
			return false;
		//return stmt.toString().contains("<java.lang.Runtime: java.lang.Process exec(java.lang.String)>(\"logcat");
		return true;
	}
	
	

	public static boolean isSink(Stmt stmt) {
		return isCPSink(stmt)
				||isSinkFromInputSinks(stmt);
	}
	
	public static boolean isCPSink(Stmt stmt){
		if (stmt.containsInvokeExpr()) {
			SootMethodRef methodRef = stmt.getInvokeExpr().getMethodRef();
			
			if (methodRef.declaringClass().getName().contains("ContentResolver")
					|| methodRef.declaringClass().getName().contains("ContentProviderClient")) {
				if (methodRef.name().contains("insert")
						|| methodRef.name().contains("update")) {
					return true;
				}
			}
		}
		
		return false;
	}
	
	public static boolean isSinkFromInputSinks(Stmt stmt) {
		if (stmt.containsInvokeExpr()) {
			SootMethodRef methodRef = stmt.getInvokeExpr().getMethodRef();
			String methodRefName = methodRef.name();
			String decClass = methodRef.declaringClass().getName();
			for (String sourceClassStr: SourceSink.sinks_.keySet()) {
				Pattern pat = Pattern.compile(sourceClassStr);
				Matcher match = pat.matcher(decClass);
				if (match.find()) {
					ArrayList<String> methodList = SourceSink.sinks_.get(sourceClassStr);
					for (String method:methodList) {
						pat = Pattern.compile(method);
						match = pat.matcher(methodRef.name());
						if (match.find()) {
							return true;
						}
					}
				}
			}
		}
		return false;
	}
	

	public static boolean isCPQuery(Stmt stmt) {
		if (stmt.containsInvokeExpr()) {
			SootMethodRef methodRef = stmt.getInvokeExpr().getMethodRef();
			SootClass definedClass = methodRef.declaringClass();
			String methodName = methodRef.name();
			if (definedClass.getName().equals("android.content.ContentResolver")
					|| definedClass.getName().equals("android.content.ContentProviderClient")) {
				if (methodName.equals("query")) {
					return true;
				}
			}
		}
		return false;
	}

}
