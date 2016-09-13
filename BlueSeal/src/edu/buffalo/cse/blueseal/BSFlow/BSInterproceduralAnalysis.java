package edu.buffalo.cse.blueseal.BSFlow;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import soot.Body;
import soot.Local;
import soot.ResolutionFailedException;
import soot.SootMethod;
import soot.Unit;
import soot.Value;
import soot.jimple.InvokeExpr;
import soot.jimple.Stmt;
import soot.jimple.toolkits.callgraph.CallGraph;
import soot.toolkits.graph.ExceptionalUnitGraph;
import soot.toolkits.scalar.ArraySparseSet;
import soot.toolkits.scalar.SmartLocalDefs;
import edu.buffalo.cse.blueseal.BSG.ArgNode;
import edu.buffalo.cse.blueseal.BSG.BlueSealGraph;
import edu.buffalo.cse.blueseal.BSG.CVNode;
import edu.buffalo.cse.blueseal.BSG.Edge;
import edu.buffalo.cse.blueseal.BSG.Node;
import edu.buffalo.cse.blueseal.BSG.RetNode;
import edu.buffalo.cse.blueseal.BSG.SinkNode;
import edu.buffalo.cse.blueseal.BSG.SourceNode;

public class BSInterproceduralAnalysis extends AbstractInterproceduralAnalysis {

	//the following map maintains method and its intraprocedural analysis result
	private static Map<SootMethod, Map<Unit, ArraySparseSet>> methodSummary = 
			new HashMap<SootMethod, Map<Unit, ArraySparseSet>>();
	private static Map<SootMethod, Map<Unit, BlueSealGraph>> methodBSGSummay = 
			new HashMap<SootMethod, Map<Unit, BlueSealGraph>>();
	
	public BSInterproceduralAnalysis(CallGraph cg, SootMethodFilter filter,
			Iterator heads, boolean verbose) {
		super(cg, filter, heads, verbose);
		methodSummary.clear();
		doAnalysis(false);
	}
	
	public Map getData(){
		return this.data;
	}

	@Override
	protected void analyseMethod(SootMethod method, Object dst) {
		BlueSealGraph summary = (BlueSealGraph)dst;
		Body body = method.retrieveActiveBody();
		ExceptionalUnitGraph eug = new ExceptionalUnitGraph(body);
		//BSBackwardFlowAnalysis res = new BSBackwardFlowAnalysis(eug, method, this);
		BSForwardFlowAnalysis res = new BSForwardFlowAnalysis(eug, this, method);
		res.copyResult(summary);
		
		/*
		 * the following is for modifying callgraph
		 */
		Map<Unit, ArraySparseSet> unitSummary = res.getUnitToSet();
		methodSummary.put(method, unitSummary);
	}
	
	public static Map<SootMethod, Map<Unit, ArraySparseSet>> getMethodSummary(){
		return methodSummary;
	}

	/*
	 * (non-Javadoc)
	 * @see edu.buffalo.cse.blueseal.BSFlow.AbstractInterproceduralAnalysis
	 * #applySummary(java.lang.Object, soot.jimple.Stmt, java.lang.Object, java.lang.Object, java.lang.Object)
	 * @para src: in FlowSet for intraprocedural analysis, in fact we don't care about this
	 * @para summary: interprocedural analysis method summary(BSG)(stmt contained method summary)
	 * @para dst: out FlowSet for intraprocedural analysis(HashSet)
	 * @para summary2: result dest for interprocedural anlysis(BSG)(Graph for current analyzed method) 
	 */
	@Override
	protected void applySummary(Object src, Stmt stmt, Object summary, Object dst, 
			Object summary2, Object callMethod, Map unitToNode) {}

	@Override
	protected void copy(Object source, Object dest) {
		BlueSealGraph src = (BlueSealGraph)source;
		BlueSealGraph dst = (BlueSealGraph)dest;
		dest = new BlueSealGraph(src);
	}

	@Override
	protected void merge(Object in1, Object in2, Object out) {
		BlueSealGraph i1 = (BlueSealGraph)in1;
		BlueSealGraph i2 = (BlueSealGraph)in2;
		BlueSealGraph o = (BlueSealGraph)out;
		
		if(!o.equals(i1)) o = new BlueSealGraph(i1);
		
		o.union(i2);
	}

	@Override
	protected Object newInitialSummary() {
		return new BlueSealGraph();
	}

	@Override
	protected Object summaryOfUnanalysedMethod(SootMethod method) {
		return new BlueSealGraph();
	}

	@Override
	protected void applySummary(Object src, Stmt stmt, Object summary,
			Object dst, BSForwardFlowAnalysis intra) {
		ArraySparseSet intraOut = (ArraySparseSet)dst;
		BlueSealGraph bsg = intra.getBSG();//summary for caller
		BlueSealGraph sum = (BlueSealGraph)summary;//summary for callee the invoke method
		InvokeExpr invoke = stmt.getInvokeExpr();
		SootMethod callMethod = intra.getMethod();
		Map<Unit, Node> unitToNode = intra.getUnitToNode();
		SmartLocalDefs localDefs = intra.getSmartLocalDefs();
		Map<Unit, ArraySparseSet> unitToSet = intra.getUnitToSet();
		SootMethod method = null;
		try{
			method=invoke.getMethod();//method contained in the InvokeExpr
		}catch(ResolutionFailedException e){
			return;
		}
		
		/*
		 * if there is any argument to Return flow in the summary,
		 * add the argument variable passed to this statement to 
		 * intra procedural analysis out flowSet
		 *  now, this is our default plan, this will improve precision
		 *  take care of it later
		 */
/*		{
			Set<Edge> edges = sum.getArgToRet();
			for(Edge e:edges){ 
				int num = ((ArgNode)e.getSrc()).getParaNum();
				Value value = stmt.getInvokeExpr().getArg(num);
				intraOut.add(value);
			}
		}*/
		
		/*
		 * take care of class variable
		 *  
		 */
		{
			//case1: if there is a cv flow into return, add this cv to intra outflowSet
			// and add CVNode to bsg and unitToNode mapping
			HashSet<Edge> cvToRet = sum.getCVToRet();
			
			for(Edge e : cvToRet){
				CVNode cvnode = (CVNode) e.getSrc();
				bsg.addCVNode(cvnode);
				if(unitToNode.containsKey(stmt)&&
						(unitToNode.get(stmt) instanceof SourceNode))
					continue;
				unitToNode.put(stmt, cvnode);
			}
			
			//case2: if there is an argument flow into CV
			// we add the CVNode to bsg and add proper edge
			// we are only interested in source/argument to this CVNode
			HashSet<Edge> argToCV = sum.getArgToCV();
			
			for(Edge e : argToCV){
				CVNode cvn = (CVNode) e.getTarget();
				int index = ((ArgNode)e.getSrc()).getParaNum();
				if(invoke.getArgCount() < index+1 ) continue;
				Value value = invoke.getArg(index);
				bsg.addCVNode(cvn);
				
				if(!(value instanceof Local)) continue;
				
				List<Unit> localDefUs = localDefs.getDefsOfAt((Local) value, stmt);
				for(Unit u : localDefUs){
					//check the unit itself
					if(unitToNode.containsKey(u)){
						Node node = unitToNode.get(u);
						
						if(node instanceof ArgNode ||
								node instanceof SourceNode || 
								node instanceof CVNode){
							bsg.addEdge(node, cvn);
						}
					}
						
					//check all the units flows into u
					if(!unitToSet.containsKey(u)) continue;
					
					ArraySparseSet uFlowsIntoThis = unitToSet.get(u);

					for(Iterator it=uFlowsIntoThis.iterator();it.hasNext();){
						Unit flowIntoU = (Unit) it.next();
						
						if(!unitToNode.containsKey(flowIntoU)) continue;
						
						Node flowNode = unitToNode.get(flowIntoU);
						
						if(flowNode instanceof ArgNode ||
								flowNode instanceof SourceNode || 
								flowNode instanceof CVNode){
							bsg.addEdge(flowNode, cvn);
						}
					}
				}
			}
			
			//case3: if there is a source flow into CV,
			//if the caller uses this cv, then we need to add all edges to its bsg
			for(Edge e : sum.getSrcToCV()){
				CVNode cvnode = (CVNode) e.getTarget();
				//we need to check if this CV is used in the caller
//				if(!bsg.contains(cvnode)) continue;
				
				//add all srcNode the caller's bsg
				bsg.addEdge(e);
			}
			
			//case4: there is a CV0 to another CV1
			// we need to add all sources/cvs to CV0 to bsg points to CV1
			//this should be fine, because all sources/cvs flows to CV0, already flows into CV1
//			for(Edge e : sum.getCVToCV()){
//				CVNode cvnode0 = (CVNode) e.getSrc();
//				CVNode cvnode1 = (CVNode) e.getTarget();
//				bsg.addEdge(e);
//				
////				if(!bsg.contains(cvnode1)) continue;
//				
//				Set<Edge> flowSetToThis = sum.getEdgesInto(cvnode0);
//				for(Edge edge : flowSetToThis){
//					if(edge.getSrc() instanceof SourceNode 
//							|| edge.getSrc() instanceof CVNode)
//					bsg.addEdge(edge.getSrc(), cvnode0);
//				}
//			}
		}
		/*
		 * if there is any source to return in the summary,
		 * treat this unit as source node, add to the BSG
		 * "generating flow"
		 */
		{
			if(sum.containSrcToRet()){
				SourceNode node = 
						new SourceNode(stmt.toString(), stmt, intra.getMethod(), false);
				//here we need to attach the list of sourceNode that flows into this sourceNode
				//in the list there are only direct parent of the current source node
				Set<Edge> list = sum.getSrcToRet();
				LinkedList newlist = new LinkedList<SourceNode>();
				
				for(Iterator it = list.iterator(); it.hasNext();){
					Edge e = (Edge)it.next();
					SourceNode sn = (SourceNode) e.getSrc();
					newlist.add(sn);
				}
				node.setSrcList(newlist);
				
				bsg.addSrc(node);
				unitToNode.put(stmt, node);
			}
		}
		
		/*
		 * if there is any argument to sink, terminating flow
		 * create sinkNode and add to bsg && related edges
		 */
		{
			Set<Edge> argToSink = sum.getArgToSink();
			
			for(Edge e : argToSink){
				SinkNode sink = (SinkNode) e.getTarget();
				int index = ((ArgNode)e.getSrc()).getParaNum();
				if(invoke.getArgCount() < index+1 ) continue;
				Value value = invoke.getArg(index);
				
				//feng:oct 25 2015, create new sink and add to the current bsg
				SinkNode newSink = new SinkNode(stmt.toString(), stmt, intra.getMethod(), false);
				newSink.getSinkList().add(sink);
				bsg.addSink(newSink);
				
				if(!(value instanceof Local)) continue;
				
				List<Unit> localDefUs = localDefs.getDefsOfAt((Local)value, stmt);
				for(Unit u : localDefUs){
					Node nodeU = unitToNode.get(u);
					
					if(nodeU instanceof ArgNode ||
							nodeU instanceof SourceNode
							|| nodeU instanceof CVNode){
						bsg.addEdge(nodeU, newSink);
					}
					
					ArraySparseSet flowset = unitToSet.get(u);
					
					for(Iterator<Unit> it = flowset.iterator(); it.hasNext();){
						Unit flowU = it.next();
						Node node = unitToNode.get(flowU);
						
						if(node instanceof ArgNode ||
								node instanceof SourceNode
								|| node instanceof CVNode){
							bsg.addEdge(node, newSink);
						}
					}
				}
			}
		}

		intra.setUnitToNode(unitToNode);
		intra.setBSG(bsg);
	}

	@Override
	public void applySummary(Object in, Stmt stmt, Object elem,
			Object dst, BSBackwardFlowAnalysis bfa) {
		
		ArraySparseSet intraOut = (ArraySparseSet)dst;
		BlueSealGraph bsg = bfa.getBSG();//summary for caller
		BlueSealGraph sum = (BlueSealGraph)elem;//summary for callee the invoke method
		InvokeExpr invoke = stmt.getInvokeExpr();
		SootMethod method = invoke.getMethod();//method contained in the InvokeExpr
		SootMethod callMethod = bfa.getMethod();
		Map<Unit, Node> unitToNode = bfa.getUnitToNode();
		Map<Unit, ArraySparseSet> unitToSet = bfa.getUnitToSet();
		SmartLocalDefs localDefs = bfa.getSmartLocalDefs();
		
		/*
		 * if there is any argument to Return flow in the summary,
		 * add the argument variable passed to this statement to 
		 * intra procedural analysis out flowSet
		 */
/*		{
			Set<Edge> edges = sum.getRetToArg();
			for(Edge e:edges){ 
				int num = ((ArgNode)e.getTarget()).getParaNum();
				Value value = stmt.getInvokeExpr().getArg(num);
				intraOut.add(value);
			}
		}*/
		
		/*
		 *  handle class variables
		 */
		{
			//case1: if we have a retToCV
			//add this CVNode to bsg, and add proper edges
			HashSet<Edge> retToCV = sum.getRetToCV();
			for(Edge e : retToCV){
				CVNode cvn = (CVNode) e.getTarget();
				bsg.addCVNode(cvn);
								
				ArraySparseSet flowInto = unitToSet.get(stmt);
				for(Iterator it = flowInto.iterator();it.hasNext();){
					Unit unit = (Unit)it.next();
					if(!unitToNode.containsKey(unit)) continue;
					
					Node unitNode = unitToNode.get(unit);
					if(unitNode instanceof SourceNode
							||unitNode instanceof RetNode
							||unitNode instanceof CVNode){
						bsg.addEdge(unitNode, cvn);
					}
				}
			}
			
			//case2: if there is an edge from CV to Argument
			HashSet<Edge> cvToArg = sum.getCVToArg();
			for(Edge e : cvToArg){
				CVNode cvn = (CVNode) e.getSrc();
				bsg.addCVNode(cvn);
				unitToNode.put(cvn.getStmt(), cvn);
				intraOut.add(cvn.getStmt());
				
				int index = ((ArgNode)e.getTarget()).getParaNum();
				if(invoke.getArgCount() < index+1 ) continue;
				Value val = stmt.getInvokeExpr().getArg(index);
				if(!(val instanceof Local)) continue;
				List<Unit> valUs = localDefs.getDefsOfAt((Local) val, stmt);
				
				for(Unit vu : valUs){
					ArraySparseSet ass = unitToSet.get(vu);
					ass.add(cvn.getStmt());
					unitToSet.put(stmt, ass);
				}
			}
			
		}
		/*
		 * if there is any source to return in the summary,
		 * treat this unit as source node, add to the BSG
		 * "generating flow"
		 */
		{
			if(sum.containRetToSink()){
				SinkNode node = 
						new SinkNode(stmt.toString(), stmt, method, false);
				bsg.addSink(node);
				unitToNode.put(stmt, node);
			}
		}
		
		/*
		 * if there is any argument to sink, terminating flows
		 * create sinkNode and add to bsg && related edges
		 */
		{
			Set<Edge> srcToArg = sum.getSrcToArg();
			
			for(Edge e : srcToArg){
				SourceNode src = (SourceNode) e.getSrc();
				SourceNode newSrc = new SourceNode(stmt.toString(), stmt,
						bfa.getMethod(), false);
				
				int index = ((ArgNode)e.getTarget()).getParaNum();
				if(invoke.getArgCount() < index+1 ) continue;
				Value value = invoke.getArg(index);
				if(!(value instanceof Local)) continue;
				List<Unit> valUs = localDefs.getDefsOfAt((Local) value, stmt);
				
				bsg.addSrc(newSrc);
				unitToNode.put(stmt, newSrc);
			}
		}
		
	}

}
