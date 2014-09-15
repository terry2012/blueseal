package edu.buffalo.cse.blueseal.blueseal;

import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import soot.Immediate;
import soot.Local;
import soot.SootMethod;
import soot.Type;
import soot.Unit;
import soot.UnitBox;
import soot.Value;
import soot.ValueBox;
import soot.jimple.AssignStmt;
import soot.jimple.BinopExpr;
import soot.jimple.ClassConstant;
import soot.jimple.ConcreteRef;
import soot.jimple.Constant;
import soot.jimple.FieldRef;
import soot.jimple.InstanceFieldRef;
import soot.jimple.InvokeExpr;
import soot.jimple.StringConstant;
import soot.jimple.internal.ImmediateBox;
import soot.jimple.internal.JAssignStmt;
import soot.jimple.internal.VariableBox;
import soot.jimple.toolkits.callgraph.CallGraph;
import soot.jimple.toolkits.callgraph.Edge;
import soot.jimple.toolkits.pointer.representations.ConstantObject;
import soot.toolkits.graph.ExceptionalUnitGraph;
import soot.toolkits.graph.UnitGraph;

public class MethodAnalysis {
	private static Set<String> contentproviderURI_ = new HashSet<String>();
	private SootMethod method_;
	private Unit unit_;
	private Value value_;
	private ExceptionalUnitGraph ug_;
	private CallGraph cg_;
	public MethodAnalysis(CallGraph cg, SootMethod method, Unit unit, Value value){
		this.method_ = method;
		this.unit_ = unit;
		this.value_ = value;
		this.ug_ = new ExceptionalUnitGraph(method.getActiveBody());
/*		List<Unit> units = ug_.getPredsOf(unit);
		for(Unit u:units){
			Debug.println("test", u.toString());
		}
		Debug.println("ug", ug_.toString());
		List<ValueBox> lists = unit.getUseBoxes();
		if(lists.isEmpty()){
			Debug.printOb("Empty");
		}
		for(ValueBox value1 : lists){
			Debug.println("ValueBox", value1.toString());
		}
		*/
//		find the def of given value
		for(Iterator it = ug_.iterator(); it.hasNext();){
			Unit u = (Unit)it.next();
			List<ValueBox> list = u.getDefBoxes();
			if(list.size()!=1){
				continue;
			}
			for(ValueBox box : list){
				if(!box.getValue().equals(value)){
					continue;
				}

				List<ValueBox> useList = u.getUseBoxes();
				
				if(u instanceof AssignStmt){
					Debug.printOb("assign stmt");
					if(((AssignStmt)u).containsArrayRef()){
						Debug.printOb("array ref");
					}
					if(((AssignStmt)u).containsFieldRef()){
						if(((AssignStmt) u).getFieldRef() instanceof InstanceFieldRef){
							ValueBox valBox = useList.get(1);
							Value val = valBox.getValue();
							Value v = findDefVariable(cg,val);
							if(v!=null){
								contentproviderURI_.add(v.toString());
							}
						}else if(((AssignStmt) u).getFieldRef() instanceof FieldRef){
							Debug.println("not instance", ((AssignStmt)u).getFieldRef().toString());
							contentproviderURI_.add(((AssignStmt)u).getFieldRef().toString());
						}
					}
					if(((AssignStmt)u).containsInvokeExpr()){
						Debug.printOb("invoke expr");
					}
					
				}
				
				
			}
		}
	}
	
	private Value findDefVariable(CallGraph cg, Value value) {
		// TODO Auto-generated method stub
		Value defV=null;
		for(Iterator it = cg.sourceMethods(); it.hasNext();){
			SootMethod method =(SootMethod)it.next();
			ExceptionalUnitGraph ug = new ExceptionalUnitGraph(method.getActiveBody());
			
			for(Iterator ugIt = ug.iterator(); ugIt.hasNext();){

				Unit u = (Unit)ugIt.next();
				List<ValueBox> defBoxes = u.getDefBoxes();
				for(ValueBox v : defBoxes){
					if(!v.getValue().toString().equals(value.toString())){
						continue;
					}
					List<ValueBox> uses = u.getUseBoxes();
					for(ValueBox use: uses){
						Value newV = use.getValue();
						if(newV instanceof Local){
							defV = findLocalVDef(method, newV);
						}else if(newV instanceof FieldRef){
							defV = findLocalVDef(method, newV);
						}else{
							defV = newV;
						}
					}
				}
			}
			
			
		}
		return defV;
	}
	private Value findLocalVDef(SootMethod method, Value value) {
		// TODO Auto-generated method stub
		Value localV = null;
		//find the def of given value
		ExceptionalUnitGraph ug = new ExceptionalUnitGraph(method.getActiveBody());
		for(Iterator it = ug.iterator(); it.hasNext();){

			Unit u = (Unit)it.next();
			List<ValueBox> list = u.getDefBoxes();
			for(ValueBox box : list){
				if(!box.getValue().equals(value)){
					continue;
				}
				
				List<ValueBox> useList = u.getUseBoxes();
				for(ValueBox use: useList){
					Value val = use.getValue();
					if(val instanceof Local){
						Debug.printOb("local");
					}else{
						localV = val;
					}
				}
			}
		}
		return localV;
	}
	public static Set<String> getCPURIs(){
		return contentproviderURI_;
	}
	
	public MethodAnalysis(CallGraph cg, SootMethod method){
		if(!method.hasActiveBody()){
			Debug.printOb("No activeboday");
			return;
		}
		this.ug_ = new ExceptionalUnitGraph(method.getActiveBody());
		for(Iterator it = ug_.iterator(); it.hasNext();){
			Unit u =(Unit)it.next();
			List<ValueBox> list = u.getDefBoxes();
			for(ValueBox box : list){
				Debug.println("printingdef", box.toString());
			}
		}
		
		Iterator<Edge> v = cg.edgesOutOf(method);
		if(!v.hasNext()){
			Debug.printOb("No edges!");
		}
		for(Iterator it = cg.edgesOutOf(method); it.hasNext();){
			Edge e = (Edge) it.next();
			Debug.println("Edge", e.toString());
		}
	}

}
