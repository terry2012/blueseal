package edu.buffalo.cse.blueseal.BSFlow;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import edu.buffalo.cse.blueseal.BSG.ArgNode;
import edu.buffalo.cse.blueseal.BSG.BlueSealGraph;
import edu.buffalo.cse.blueseal.BSG.CVNode;
import edu.buffalo.cse.blueseal.BSG.Node;
import edu.buffalo.cse.blueseal.BSG.RetNode;
import edu.buffalo.cse.blueseal.BSG.SinkNode;
import edu.buffalo.cse.blueseal.BSG.SourceNode;

import soot.Body;
import soot.Local;
import soot.SootClass;
import soot.SootField;
import soot.SootFieldRef;
import soot.SootMethod;
import soot.SootMethodRef;
import soot.Type;
import soot.Unit;
import soot.Value;
import soot.ValueBox;
import soot.JastAddJ.AssignShiftExpr;
import soot.JastAddJ.AssignSimpleExpr;
import soot.jimple.AssignStmt;
import soot.jimple.Constant;
import soot.jimple.IdentityStmt;
import soot.jimple.InstanceFieldRef;
import soot.jimple.InstanceInvokeExpr;
import soot.jimple.InvokeExpr;
import soot.jimple.ParameterRef;
import soot.jimple.ReturnStmt;
import soot.jimple.SpecialInvokeExpr;
import soot.jimple.StaticFieldRef;
import soot.jimple.Stmt;
import soot.jimple.VirtualInvokeExpr;
import soot.jimple.internal.JInstanceFieldRef;
import soot.toDex.instructions.AbstractInsn;
import soot.toolkits.graph.DirectedGraph;
import soot.toolkits.graph.ExceptionalUnitGraph;
import soot.toolkits.scalar.ArraySparseSet;
import soot.toolkits.scalar.FlowSet;
import soot.toolkits.scalar.SimpleLiveLocals;
import soot.toolkits.scalar.SimpleLocalUses;
import soot.toolkits.scalar.SmartLocalDefs;

public class BSBackwardFlowAnalysis extends BackwardFlowAnalysis{
	
	private FlowSet emptySet = new ArraySparseSet();
	
	private AbstractInterproceduralAnalysis inter_;
	private BlueSealGraph bsg = new BlueSealGraph();
	private SmartLocalDefs localDefs;
	private SootMethod method_;
	private Map<Unit, ArraySparseSet> unitToSet = 
						new HashMap<Unit, ArraySparseSet>();
	private Map<Unit, Node> unitToNode = 
						new HashMap<Unit, Node>();
	
	public BSBackwardFlowAnalysis(DirectedGraph graph, SootMethod method,
			AbstractInterproceduralAnalysis inter) {
		super(graph);
		this.inter_ = inter;
		this.method_ = method;
		ExceptionalUnitGraph eug = (ExceptionalUnitGraph)graph;
		localDefs = new SmartLocalDefs(eug, new SimpleLiveLocals(eug));
		
		for(Iterator it = graph.iterator();it.hasNext();){
			Unit unit = (Unit)it.next();
			unitToSet.put(unit, new ArraySparseSet());
		}
		
		doAnalysis();

	}
	
	protected void flowThrough(Object in, Object callNode, Object out) {
		FlowSet i = (FlowSet)in, 
				o = (FlowSet)out;
		/*
		 * start with a set of interesting part we already have,
		 * find all the units flow into these units
		 */
		Unit unit = (Unit)callNode;
		Stmt stmt = (Stmt)unit;
		List<ValueBox> uses = unit.getUseBoxes();
		ArraySparseSet uFlowOut = unitToSet.get(unit);
		
		//take care of instance invoke
		if(stmt.containsInvokeExpr()){
			InvokeExpr invoke = stmt.getInvokeExpr();
			if(invoke instanceof VirtualInvokeExpr
					|| invoke instanceof SpecialInvokeExpr){
				InstanceInvokeExpr iie = (InstanceInvokeExpr)invoke;
				Value base = iie.getBase();
				List<Value> args = iie.getArgs();
				
				if(base instanceof Local){
					List<Unit> baseUs = 
							localDefs.getDefsOfAt((Local) base, stmt); 
					
					for(Value val:args){
						if(!(val instanceof Local)) continue;
						
						List<Unit> uList = 
								localDefs.getDefsOfAt((Local) val, stmt);
						
						for(Unit valU : uList){
							ArraySparseSet valSet = unitToSet.get(valU);
							ArraySparseSet previousSet = unitToSet.get(valU);
							
							for(Iterator it = baseUs.iterator(); it.hasNext();){
								Unit bu = (Unit)it.next();
								valSet.add(bu);
								valSet.union(unitToSet.get(bu));
							}
							unitToSet.put(valU, valSet);
						}
					}
				}
				
			}
		}
		
		{
			for(ValueBox vb:uses){
				if(!(vb.getValue() instanceof Local)) continue;
				
				List<Unit> localDefUs = localDefs.getDefsOfAt((Local)vb.getValue(), unit);
				
				for(Unit u: localDefUs){
					ArraySparseSet set = unitToSet.get(u);
					set.add(unit);
					set.union(uFlowOut);
					unitToSet.put(u, set);
				}
			}
			i.union(uFlowOut, o);
		}
		
		/*
		 *update BSG
		*/ 
		if(isSource(stmt)){
			SourceNode src = new SourceNode(stmt.toString(), 
								stmt, method_, true);
			bsg.addSrc(src);
			unitToNode.put(unit, src);
		}
		
		/*
		 * check if the stmt is a source
		 * TODO: fix real sink checking later
		 */
		else if(isSink(stmt)){			

			SinkNode sink = new SinkNode(stmt.toString(), 
					stmt, method_, true);
			bsg.addSink(sink);
			unitToNode.put(stmt, sink);
			
			//checking all the units that this unit may flow into
			ArraySparseSet flowInto = unitToSet.get(unit);
			for(Iterator it = flowInto.iterator(); it.hasNext();){
				Unit inUnit = (Unit)it.next();
				
				if(!unitToNode.containsKey(inUnit)) continue;
			
				Node unitNode = unitToNode.get(inUnit);
			
				if(unitNode instanceof SourceNode||
						unitNode instanceof RetNode
						|| unitNode instanceof CVNode){
					bsg.addEdge(unitNode, sink);
				}
			}
		}
		
		/*
		 * detect all the parameters and construct argument nodes
		 */
		else if(stmt instanceof IdentityStmt){
			Value value = ((IdentityStmt)stmt).getRightOp();
			
			if(value instanceof ParameterRef){
				int para_index = ((ParameterRef)value).getIndex();
				ArgNode arg = new ArgNode(stmt.toString(), stmt, para_index);
				bsg.addArgNode(arg);
				unitToNode.put(unit, arg);
				
				//checking all the units that this unit may flow into
				ArraySparseSet flowInto = unitToSet.get(unit);
				for(Iterator it = flowInto.iterator(); it.hasNext();){
					Unit inUnit = (Unit)it.next();
					
					if(!unitToNode.containsKey(inUnit)) continue;

					Node unitNode = unitToNode.get(inUnit);

					if(unitNode instanceof SourceNode
							||unitNode instanceof RetNode
							|| unitNode instanceof CVNode)
					{
						bsg.addEdge(unitNode, arg);
					}
				}
			}
		}
		
		/*
		 * return stmts, construct return nodes
		 */
		else if(stmt instanceof ReturnStmt){
			RetNode ret = new RetNode(stmt.toString(), stmt);
			bsg.addRetNode(ret);
			unitToNode.put(unit, ret);	
			
			//TODO: don't handle the return may be alse a source or sink
/*			{
				Value value = ((ReturnStmt)stmt).getOp();
				if(value instanceof Constant) bsg.addEdge(ret, ret);
			}*/
		}
		
		/*
		 * for class variables
		 */
//		else if(stmt instanceof AssignStmt){
//			//if CV is the right operator, we are reading CV
//			//create a CVNode, treat it as a sink
//			Value val = ((AssignStmt)stmt).getRightOp();
//			if(val instanceof InstanceFieldRef){
//				CVNode cvn = new CVNode(((InstanceFieldRef)val).toString(), 
//						((InstanceFieldRef)val).getFieldRef().declaringClass(), stmt);
//				bsg.addCVNode(cvn);
//				unitToNode.put(stmt, cvn);
//				
//				// we check units that may use this CV and 
//				//add edges
//				//check if we need to add edges to bsg
//				for(Iterator it = uFlowOut.iterator(); it.hasNext();){
//					Unit inUnit = (Unit)it.next();
//					//for each unit, check if it's a node in BSG
//					if(!unitToNode.containsKey(inUnit)) continue;
//					
//					Node unitNode = unitToNode.get(inUnit);
//				
//					//only cares about argument Node&SourceNode
//					if(unitNode instanceof RetNode 
//							|| unitNode instanceof SourceNode
//							|| unitNode instanceof CVNode)
//					{
//						bsg.addEdge(unitNode, cvn);
//					}
//				}
//			}
//
//			//if CV is the left operator, we are writing into CV
//			//create a CVNode, treat it as a source
//			Value value = ((AssignStmt)stmt).getLeftOp();
//			if(value instanceof JInstanceFieldRef){
//				CVNode cvn = new CVNode(((InstanceFieldRef)value).toString(), 
//						((JInstanceFieldRef) value).getFieldRef().declaringClass(), stmt);
//				bsg.addCVNode(cvn);
//				unitToNode.put(stmt, cvn);
//			}
//		}
		
		/*
		 * calls
		 *TODO: need to fix this later
		 */
		else if(stmt.containsInvokeExpr()){
			/*
			 * for each invoke, we should check if 
			 * it's a source or a sink
			 * Now, just consider method's name,
			 * later, should be more precise
			 */
			ArraySparseSet values = new ArraySparseSet();
			//inter.analyseCall(in, stmt, values, this.bsg, this.method_, unitToNode);
			inter_.analyseCall(in, stmt, values, this);
			
		}
		
		else {
			//do nothing
		}

	}

	public boolean isSink(Stmt stmt) {
		return isSourceFromInputSources(stmt) 
				|| isCPSource(stmt)
				||isTestSource(stmt);
	}
	private boolean isSourceFromInputSources(Stmt stmt) {
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
	private boolean isCPSource(Stmt stmt) {
		if(!(stmt instanceof AssignStmt)) return false;
		
		Value value = ((AssignStmt)stmt).getRightOp();
		
		if(!(value instanceof StaticFieldRef)) return false;
			
		Type type = value.getType();
		SootFieldRef sf = ((StaticFieldRef) value).getFieldRef();
		SootClass sc= sf.declaringClass();
		
		return type.toString().equals("android.net.Uri")
				&& value instanceof StaticFieldRef
				&& (sc.getName().startsWith("android.provider")
						||sc.getName().startsWith("com.android."));
	}
	private boolean isTestSource(Stmt stmt){

		if(!stmt.containsInvokeExpr()) return false;
		
		SootMethod in = stmt.getInvokeExpr().getMethod();
		String className = in.getDeclaringClass().getName();
		String mName = in.getName();
		
		return (className.contains("android.accounts.AccountManager")
				&& mName.contains("getAccounts"))
				|| (className.contains("android.telephony.TelephonyManager")
						&& mName.contains("getDeviceId"))
				||(className.contains("android.telephony.TelephonyManager")
						&& mName.contains("getSimSerialNumber"))
				||mName.contains("onLocationChanged");
	}

	public boolean isSource(Stmt stmt) {
		return isCPSink(stmt)
				||isIntentSink(stmt)
				||isTestSink(stmt);
	}
	private boolean isTestSink(Stmt stmt){
		if(!stmt.containsInvokeExpr()) return false;
		
		SootMethod in = stmt.getInvokeExpr().getMethod();
		String className = in.getDeclaringClass().getName();
		String mName = in.getName();
		
		return (className.contains("org.apache.http.client.entity.UrlEncodedFormEntity")
				&& mName.contains("init"))
				||mName.contains("write");
	}
	
	private boolean isCPSink(Stmt stmt){
		if (stmt.containsInvokeExpr()) {
			SootMethodRef methodRef = stmt.getInvokeExpr().getMethodRef();
			
			if (methodRef.declaringClass().getName().contains("ContentResolver")
					|| methodRef.declaringClass().getName().contains("ContentProviderClient")) {
				if (methodRef.name().contains("insert")
						|| methodRef.name().contains("query")
						|| methodRef.name().contains("update")) {
					return true;
				}
			}
		}
		
		return false;
	}
	private boolean isIntentSink(Stmt stmt)
	{
		if (!stmt.containsInvokeExpr()) return false;
		
		SootMethod method = stmt.getInvokeExpr().getMethod();

		if (!method.getDeclaringClass().getName().contains("android.content.Intent")) return false;
		
		String name = method.getName();
		if( name.contains("putExtra") ||
			name.contains("setdata") ||
			name.contains("setdata") ||
			name.contains("Replace") ||
			name.contains("Remove") ||
			name.contains("putCharSequenceArrayListExtra") ||
			name.contains("putIntegerArrayListExtra") ||
			name.contains("putParcelableArrayListExtra") ||
			name.contains("putStringArrayListExtra") ||
			name.contains("setData") ||
			name.contains("startActivityForResult")
			){
			return true;
		}
				
		return false;
	}
	
	private boolean isCPSrc(Stmt stmt){
		if (stmt.containsInvokeExpr()) {
			SootMethodRef methodRef = stmt.getInvokeExpr().getMethodRef();
			
			if (methodRef.declaringClass().getName().contains("ContentResolver")
					|| methodRef.declaringClass().getName().contains("ContentProviderClient")) {
				if (methodRef.name().contains("insert")
						|| methodRef.name().contains("query")
						|| methodRef.name().contains("update")) {
					return true;
				}
			}
		}
		return false;
	}

	private boolean isContentProvider(Stmt stmt) {
		if(!(stmt instanceof AssignStmt)) return false;
		
		Value value = ((AssignStmt)stmt).getRightOp();
		Type type = value.getType();

		return type.toString().equals("android.net.Uri");
	}

	@Override
	protected void copy(Object src, Object tgt) {
		FlowSet s = (FlowSet)src, 
				t = (FlowSet)tgt;
		s.copy(t);
	}

	@Override
	protected Object entryInitialFlow() {
		return emptySet.clone();
	}

	@Override
	protected void merge(Object in1, Object in2, Object out) {
		FlowSet i1 = (FlowSet)in1,
				i2 = (FlowSet)in2,
				o = (FlowSet)out;
		i1.union(i2, o);
	}

	@Override
	protected Object newInitialFlow() {

		return emptySet.clone();
	}

	public void copyResult(Object summary) {
		BlueSealGraph out = (BlueSealGraph)summary;
		out.setEdges(this.bsg.getEdges());
		out.setArgNodes(this.bsg.getArgNodes());
		out.setRetNodes(this.bsg.getRetNodes());
		out.setSrc(this.bsg.getSrcs());
		out.setSinks(this.bsg.getSinks());	
		out.setCVNodes(this.bsg.getCVNodes());
	}

	public SootMethod getMethod(){
		return this.method_;
	}
	
	public Map getUnitToNode(){
		return this.unitToNode;
	}
	
	public BlueSealGraph getBSG(){
		return this.bsg;
	}
	
	public SmartLocalDefs getSmartLocalDefs(){
		return this.localDefs;
	}

	public Map getUnitToSet() {
		return unitToSet;
	}

	public void setUnitToSet(Map unitToSet) {
		this.unitToSet = unitToSet;
	}
}
