package edu.buffalo.cse.blueseal.BSFlow;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import soot.Body;
import soot.G;
import soot.Hierarchy;
import soot.Local;
import soot.RefType;
import soot.Scene;
import soot.SootClass;
import soot.SootMethod;
import soot.Unit;
import soot.Value;
import soot.ValueBox;
import soot.jimple.ArrayRef;
import soot.jimple.AssignStmt;
import soot.jimple.CaughtExceptionRef;
import soot.jimple.ClassConstant;
import soot.jimple.FieldRef;
import soot.jimple.IdentityStmt;
import soot.jimple.InstanceFieldRef;
import soot.jimple.InstanceInvokeExpr;
import soot.jimple.InvokeExpr;
import soot.jimple.Jimple;
import soot.jimple.ParameterRef;
import soot.jimple.ReturnStmt;
import soot.jimple.Stmt;
import soot.toolkits.graph.DirectedGraph;
import soot.toolkits.graph.ExceptionalUnitGraph;
import soot.toolkits.scalar.ArraySparseSet;
import soot.toolkits.scalar.FlowSet;
import soot.toolkits.scalar.SimpleLiveLocals;
import soot.toolkits.scalar.SimpleLocalUses;
import soot.toolkits.scalar.SmartLocalDefs;
import soot.util.Chain;
import edu.buffalo.cse.blueseal.BSG.ArgNode;
import edu.buffalo.cse.blueseal.BSG.BlueSealGraph;
import edu.buffalo.cse.blueseal.BSG.CVNode;
import edu.buffalo.cse.blueseal.BSG.Edge;
import edu.buffalo.cse.blueseal.BSG.Node;
import edu.buffalo.cse.blueseal.BSG.RetNode;
import edu.buffalo.cse.blueseal.BSG.SinkNode;
import edu.buffalo.cse.blueseal.BSG.SourceNode;

public class BSForwardFlowAnalysis extends ForwardFlowAnalysis {
	private FlowSet emptySet = new ArraySparseSet();
	private AbstractInterproceduralAnalysis inter;
	//this keeps the Map info between units and Graph Nodes
	private Map<Unit, Node> unitToNode = new HashMap<Unit, Node>(); 
	private BlueSealGraph bsg = new BlueSealGraph();
	private Map<Unit, ArraySparseSet> unitToSet = new HashMap<Unit, ArraySparseSet>();
	private SmartLocalDefs localDefs;
	private SootMethod method_;
	public static Map<FieldRef, ArraySparseSet> CVFlowSet = new HashMap<FieldRef, ArraySparseSet>();
	public ExceptionalUnitGraph eug;

	public BSForwardFlowAnalysis(DirectedGraph graph,
			AbstractInterproceduralAnalysis inter, SootMethod method){
		super(graph);
		this.inter = inter;
		this.method_ = method;

		if(!method.hasActiveBody())
			return;

		for(Iterator it = graph.iterator(); it.hasNext();){
			Unit unit = (Unit) it.next();
			unitToSet.put(unit, new ArraySparseSet());
		}

		Body body = method.getActiveBody();
		eug = new ExceptionalUnitGraph(body);
		localDefs = new SmartLocalDefs(eug, new SimpleLiveLocals(eug));
		SimpleLocalUses combinedDefs = new SimpleLocalUses(eug, localDefs);

		doAnalysis();

	}
	
	public void printUnitToSet(){
		for(Unit u : unitToSet.keySet()){
			System.out.println("[BlueSeal]-flowset:"+u.toString());
			System.out.println("--------->");
			System.out.println("		"+unitToSet.get(u).toString());
		}
	}

	private void updateBSG(){
		for(Unit unit : unitToSet.keySet()){
			Stmt stmt = (Stmt) unit;

			if(!unitToNode.containsKey(unit))
				continue;

			Node node = unitToNode.get(unit);

			if((!(node instanceof SinkNode)) && (!(node instanceof RetNode)))
				continue;

			for(Iterator it = unitToSet.get(unit).iterator(); it.hasNext();){
				Unit flowInto = (Unit) it.next();

				if(!unitToNode.containsKey(flowInto))
					continue;

				Node flowNode = unitToNode.get(flowInto);

				if(flowNode instanceof ArgNode || flowNode instanceof SourceNode){
					bsg.addEdge(flowNode, node);
				}
			}
		}

	}

	public SootMethod getMethod(){
		return this.method_;
	}

	public Map getUnitToNode(){
		return this.unitToNode;
	}

	public void setUnitToNode(Map map){
		this.unitToNode = map;
	}

	public BlueSealGraph getBSG(){
		return this.bsg;
	}

	public void setBSG(BlueSealGraph bsg){
		this.bsg = bsg;
	}

	public SmartLocalDefs getSmartLocalDefs(){
		return this.localDefs;
	}

	@Override
	protected void flowThrough(Object in, Object callNode, Object out){
		Unit unit = (Unit) callNode;
		Stmt stmt = (Stmt) unit;
		List<ValueBox> uses = unit.getUseBoxes();
		ArraySparseSet uFlowIntoThis = unitToSet.get(unit);
		//take care of throw and catch
		if(stmt instanceof IdentityStmt){
			Value leftOp = ((IdentityStmt)stmt).getLeftOp();
			Value rightOp = ((IdentityStmt)stmt).getRightOp();
			if(rightOp instanceof CaughtExceptionRef){
				List<Unit> throwUnits = eug.getExceptionalPredsOf(stmt);
				for(Unit tu : throwUnits){
					uFlowIntoThis.add(tu);
				}
			}
		}

		// take care of instance invoke
		if(stmt.containsInvokeExpr()){

			InvokeExpr invoke = stmt.getInvokeExpr();
			if(invoke instanceof InstanceInvokeExpr){
				InstanceInvokeExpr iie = (InstanceInvokeExpr) invoke;
				Value base = iie.getBase();
				List<Value> vals = iie.getArgs();

				if(base instanceof Local){
					List<Unit> localDefUs = localDefs.getDefsOfAt((Local) base, stmt);

					for(Unit u : localDefUs){
						ArraySparseSet baseSet;
						if(unitToSet.containsKey(u)){
							baseSet= unitToSet.get(u);
						}else{
							baseSet = (ArraySparseSet) newInitialFlow();
						}
						ArraySparseSet previousBaseSet = new ArraySparseSet();
						baseSet.copy(previousBaseSet);

						for(Value val : vals){
							if(val instanceof ClassConstant){
								Chain<Local> locals = method_.getActiveBody().getLocals();
								Local localConst = Jimple.v().newLocal("$r" + locals.size(),
										RefType.v("java.lang.Object"));
								locals.add(localConst);
								AssignStmt constAssign = Jimple.v().newAssignStmt(localConst,
										val);
								baseSet.add((Unit)constAssign);
							}
							if(!(val instanceof Local))
								continue;

							List<Unit> defUs = localDefs.getDefsOfAt((Local) val, stmt);

							for(Unit valU : defUs){
								baseSet.add(valU);
							}
							
						}
						

						unitToSet.put(u, baseSet);
						
//						if(!baseSet.equals(previousBaseSet)){
//							System.out.println("[BlueSeal]: unit summary changed->"+u.toString());
//							changedUnits.addAll(eug.getSuccsOf(u));
//							System.out.println("[BlueSesal]:"+eug.getSuccsOf(u).toString());
//						}

					}
				}
			}
		}

		// if(!stmt.containsInvokeExpr()){
		// analyze all the use boxes, accumulate the units that
		// can flow into current unit
		for(int i = 0; i < uses.size();i++){
			//skip the last useBox, because it's the stmt itself
			ValueBox vb = uses.get(i);
			
			if(!(vb.getValue() instanceof Local)) continue;

			List<Unit> localDefUs = localDefs.getDefsOfAt((Local) (vb.getValue()),
					stmt);

			for(Unit u : localDefUs){
				if(u == null) continue;
				
				uFlowIntoThis.add(u);

				if(!unitToSet.containsKey(u)) continue;
				
				uFlowIntoThis.union(unitToSet.get(u));
			}
		}

		unitToSet.put(unit, uFlowIntoThis);
		copy(uFlowIntoThis, out);
		//take care of assigning value to instance field ref,
		//in this case, all the units flow into this also affect the base object
		if(stmt instanceof AssignStmt){
			Value leftVal = ((AssignStmt) stmt).getLeftOp();
			if(leftVal instanceof InstanceFieldRef){
				Value baseV = ((InstanceFieldRef)leftVal).getBase();
				
				if(baseV instanceof Local){
					List<Unit> baseDefs = localDefs.getDefsOfAt((Local)baseV, stmt);
					for(Unit baseDef : baseDefs){
						unitToSet.get(baseDef).union(uFlowIntoThis);
					}
				}
			}
		}
		
		/*
		 * create a map for all class variables
		 */
		{
			if(stmt instanceof AssignStmt){
				Value leftV = ((AssignStmt) stmt).getLeftOp();
				Value rightV = ((AssignStmt) stmt).getRightOp();
				if(leftV instanceof FieldRef){
					/*
					 * a value is assigned to class variable add all units flow into this
					 * value to CV's flowInSet, same if rightV is a class variable
					 */
					FieldRef fr = (FieldRef) leftV;
					ArraySparseSet newSet;
					if(CVFlowSet.containsKey(fr)){
						newSet = CVFlowSet.get(fr);
					}else{
						newSet = new ArraySparseSet();
					}
					newSet.union(unitToSet.get(stmt));

					if(rightV instanceof FieldRef){
						// cv1 = cv2
						FieldRef rfr = (FieldRef) rightV;
						if(CVFlowSet.containsKey(rfr))
							newSet.union(CVFlowSet.get(rfr));
					}
					CVFlowSet.put(fr, newSet);
				}
				
				if(rightV instanceof FieldRef){
					/*
					 * a value is assigned to class variable add all units flow into this
					 * value to CV's flowInSet, same if rightV is a class variable
					 */
					FieldRef fr = (FieldRef) rightV;
					ArraySparseSet newSet;
					if(CVFlowSet.containsKey(fr)){
						newSet = CVFlowSet.get(fr);
					}else{
						newSet = new ArraySparseSet();
					}
					newSet.union(unitToSet.get(stmt));

					if(leftV instanceof FieldRef){
						// cv1 = cv2
						FieldRef lfr = (FieldRef) leftV;
						if(CVFlowSet.containsKey(lfr))
							newSet.union(CVFlowSet.get(lfr));
					}
					CVFlowSet.put(fr, newSet);
				}

				if(leftV instanceof ArrayRef){
					// a value is assigned to a array, this also affects the array base
					Value base = ((ArrayRef) leftV).getBase();
					if(base instanceof Local){
						List<Unit> baseDefs = localDefs.getDefsOfAt((Local) base, unit);
						for(Unit baseU : baseDefs){
							if(!(baseU instanceof AssignStmt) || baseU ==null)
								continue;

							if(unitToSet.containsKey(baseU))
								unitToSet.get(baseU).union(uFlowIntoThis);
							else
								unitToSet.put(baseU, uFlowIntoThis);

						}
					}
				}

			}
		}

		/*
		 * update BSG
		 */
		if(SourceSinkDetection.isSource(stmt)){
			SourceNode src = new SourceNode(stmt.toString(), stmt, method_, true);
			bsg.addSrc(src);
			unitToNode.put(stmt, src);
		}

		/*
		 * check if the stmt is a source TODO: fix real sink checking later
		 */
		else if(SourceSinkDetection.isSink(stmt)){
			SinkNode sink = new SinkNode(stmt.toString(), stmt, method_, true);
			bsg.addSink(sink);
			unitToNode.put(unit, sink);

			for(Iterator it = uFlowIntoThis.iterator(); it.hasNext();){
				Unit inUnit = (Unit) it.next();
				
				if(!unitToNode.containsKey(inUnit))
					continue;

				Node unitNode = unitToNode.get(inUnit);

				if(unitNode instanceof ArgNode || unitNode instanceof SourceNode
						|| unitNode instanceof CVNode){
					bsg.addEdge(unitNode, sink);
				}
				
				if(unitNode instanceof CVNode){
					Set<Edge> intoCV = bsg.getEdgesInto(unitNode);
					
					for(Edge edge : intoCV){
						bsg.addEdge(edge.getSrc(), sink);
					}
				}
			}

		}

		/*
		 * detect all the parameters and construct argument nodes
		 */
		else if(stmt instanceof IdentityStmt){
			Value value = ((IdentityStmt) stmt).getRightOp();

			if(value instanceof ParameterRef){
				int para_index = ((ParameterRef) value).getIndex();
				ArgNode arg = new ArgNode(stmt.toString(), stmt, para_index);
				bsg.addArgNode(arg);
				unitToNode.put(unit, arg);
			}
		}

		/*
		 * return stmts, construct return nodes
		 */
		else if(stmt instanceof ReturnStmt){
			RetNode ret = new RetNode(stmt.toString(), stmt);
			bsg.addRetNode(ret);
			unitToNode.put(unit, ret);

			for(Iterator it = uFlowIntoThis.iterator(); it.hasNext();){
				Unit inUnit = (Unit) it.next();
				// for each unit, check if it's a node in BSG
				if(!unitToNode.containsKey(inUnit))
					continue;

				Node unitNode = unitToNode.get(inUnit);

				// only cares about argument Node&SourceNode
				if(unitNode instanceof ArgNode || unitNode instanceof SourceNode
						|| unitNode instanceof CVNode){
					bsg.addEdge(unitNode, ret);
				}
				
				if(unitNode instanceof CVNode){
					Set<Edge> intoCV = bsg.getEdgesInto(unitNode);
					
					for(Edge edge : intoCV){
						bsg.addEdge(edge.getSrc(), ret);
					}
				}
			}
		}

		/*
		 * calls
		 */
		else if(stmt.containsInvokeExpr()){
			/*
			 * for each invoke, we should check if it's a source or a sink Now, just
			 * consider method's name, later, should be more precise
			 */
			ArraySparseSet values = new ArraySparseSet();
			// inter.analyseCall(in, stmt, values, this.bsg, this.method_,
			// unitToNode);
			inter.analyseCall(in, stmt, values, this);

			// the returned values set will contain new CV units from callee method
			// add them all into the out set, so that they can propagate
			if(!values.isEmpty()){
				uFlowIntoThis.union(values);
				unitToSet.put(unit, uFlowIntoThis);
				copy(uFlowIntoThis, out);
			}
			
		}

		else{
			// do nothing
		}
		
		/*
		 * if stmt is instanceInvokeStmt, and the base is a class variable
		 * there might be the case that we are writing into a class variable
		 */
		if(stmt.containsInvokeExpr()){
			//check the base, if it's a class variable reference
			InvokeExpr invokeExpr = stmt.getInvokeExpr();
			if(invokeExpr instanceof InstanceInvokeExpr){
				Value base = ((InstanceInvokeExpr)invokeExpr).getBase();
				
				if(base instanceof Local){
					List<Unit> defs = localDefs.getDefsOfAt((Local) base, stmt);
					
					for(Unit def : defs){
						if(def instanceof AssignStmt){
							Value rightValue = ((AssignStmt)def).getRightOp();
							
							if(rightValue instanceof FieldRef){
								//create a CVNode
								SootClass refClass = ((FieldRef)rightValue).getFieldRef().declaringClass();
								String refFieldName = ((FieldRef)rightValue).getFieldRef().name();
								CVNode cvn = new CVNode(refClass.toString()+refFieldName, refClass, refFieldName);
								
								//check all the units flow into this stmt
								ArraySparseSet flowUnits = unitToSet.get(stmt);
								
								for(Iterator it = flowUnits.iterator(); it.hasNext();){
									Unit flowUnit = (Unit)it.next();
									
									if(unitToNode.containsKey(flowUnit)){
										Node unitNode = unitToNode.get(flowUnit);
										
										if(unitNode instanceof CVNode ||
												unitNode instanceof SourceNode){
											bsg.addEdge(unitNode, cvn);
										}
									}
								}
							}
						}
					}
				}
			}
		}
		

		if(stmt instanceof AssignStmt){
			// if CV is the right operator, we are reading CV
			// create a CVNode, treat it as a source
			Value val = ((AssignStmt) stmt).getRightOp();
			if(val instanceof FieldRef){
				FieldRef ref = (FieldRef) val;
				SootClass definedClass = ref.getFieldRef().declaringClass();
				String definedField = ref.getFieldRef().name();

				if(!SourceSinkDetection.isSource(stmt)){
					CVNode cvn = new CVNode(definedClass.getName() + definedField,
							definedClass, definedField);
					bsg.addCVNode(cvn);
					unitToNode.put(stmt, cvn);
					
					//if there is a source flows into this, this CV might be written
					//check all the units flow into this stmt
					ArraySparseSet flowset = unitToSet.get(stmt);
					for(Iterator it = flowset.iterator();it.hasNext();){
						Unit inUnit = (Unit) it.next();
						if(inUnit.equals(stmt)) continue;
						// for each unit, check if it's a node in BSG
						if(!unitToNode.containsKey(inUnit))
							continue;
						Node unitNode = unitToNode.get(inUnit);

						// only cares about argument Node&SourceNode
						if(unitNode instanceof ArgNode || unitNode instanceof SourceNode
								|| unitNode instanceof CVNode){
							bsg.addEdge(unitNode, cvn);
							
							//here, check if this CV is defined in its own class, oz add more edges
							if(!definedClass.declaresField(definedField, ref.getFieldRef().type())){
								//not defined in current class, find all its parent classes
								Hierarchy hierarchy = Scene.v().getActiveHierarchy();
								List<SootClass> parents = hierarchy.getSuperclassesOf(definedClass);
								for(SootClass sc : parents){
									if(sc.declaresField(definedField,ref.getFieldRef().type())){
										CVNode newCV = new CVNode(sc.getName()+definedField,
												sc, definedField);
										bsg.addEdge(unitNode, newCV);
									}
								}
							}
						}
					
					}
				}
				
			}

			// if CV is the left operator, we are writing into CV
			// create a CVNode, treat it as a sink
			// one special case: the RightOp is a source api call
			// so that the unit is already treated as a sourceNode
			// this would not affect intra analysis, but this would cause problem for
			// inter analysis
			// solution: we create a CVNode and add a dummy edges to the bsg
			Value value = ((AssignStmt) stmt).getLeftOp();
			if(value instanceof FieldRef
					&& !(value instanceof ArrayRef)){
				FieldRef ref = (FieldRef) value;
				SootClass definedClass = ref.getFieldRef().declaringClass();
				String definedField = ref.getFieldRef().name();
				// if(!isSource(stmt)){
				{
					CVNode cvn = new CVNode(definedClass.getName() + definedField,
							definedClass, definedField);
					bsg.addCVNode(cvn);
					if(SourceSinkDetection.isSource(stmt)){
						bsg.addEdge(unitToNode.get(stmt), cvn);
					}else{
						unitToNode.put(stmt, cvn);
						// check if we need to add edges to bsg
						for(Iterator it = uFlowIntoThis.iterator(); it.hasNext();){
							Unit inUnit = (Unit) it.next();
							// for each unit, check if it's a node in BSG
							if(!unitToNode.containsKey(inUnit))
								continue;
							Node unitNode = unitToNode.get(inUnit);

							// only cares about argument Node&SourceNode
							if(unitNode instanceof ArgNode || unitNode instanceof SourceNode
									|| unitNode instanceof CVNode){
								bsg.addEdge(unitNode, cvn);
								
								//here, check if this CV is defined in its own class, oz add more edges
								if(!definedClass.declaresField(definedField, ref.getFieldRef().type())){
									//not defined in current class, find all its parent classes
									Hierarchy hierarchy = Scene.v().getActiveHierarchy();
									List<SootClass> parents = hierarchy.getSuperclassesOf(definedClass);
									for(SootClass sc : parents){
										if(sc.declaresField(definedField,ref.getFieldRef().type())){
											CVNode newCV = new CVNode(sc.getName()+definedField,
													sc, definedField);
											bsg.addEdge(unitNode, newCV);
										}
									}
								}
							}
						}
					}
				}
			}

			// take care of array CV
			if(value instanceof ArrayRef){
				// a value is assigned to a array, this also affects the array base
				Value base = ((ArrayRef) value).getBase();
				if(base instanceof Local){
					List<Unit> baseDefs = localDefs.getDefsOfAt((Local) base, unit);
					for(Unit baseU : baseDefs){
						if(!(baseU instanceof AssignStmt))
							continue;

						Value rightOp = ((AssignStmt) baseU).getRightOp();
						if(rightOp instanceof FieldRef){
							FieldRef cvref = (FieldRef) rightOp;
							SootClass cvclass = cvref.getFieldRef().declaringClass();
							String cvname = cvref.getFieldRef().name();
							CVNode cvnode = new CVNode(cvclass.getName() + cvname, cvclass,
									cvname);
							bsg.addCVNode(cvnode);

							// check if we need to add edges to bsg
							for(Iterator it = uFlowIntoThis.iterator(); it.hasNext();){
								Unit inUnit = (Unit) it.next();
								// for each unit, check if it's a node in BSG
								if(!unitToNode.containsKey(inUnit))
									continue;

								Node unitNode = unitToNode.get(inUnit);

								// only cares about argument Node&SourceNode
								if(unitNode instanceof ArgNode
										|| unitNode instanceof SourceNode
										|| unitNode instanceof CVNode){
									bsg.addEdge(unitNode, cvnode);
								}
							}
						}// end of if

					}// end f
				}
			}
		}

	}

	@Override
	protected void copy(Object src, Object tgt){
		FlowSet s = (FlowSet) src;
		FlowSet t = (FlowSet) tgt;
		s.copy(t);
	}

	@Override
	protected void merge(Object in1, Object in2, Object out){
		FlowSet i1 = (FlowSet) in1, i2 = (FlowSet) in2, o = (FlowSet) out;
		i1.union(i2, o);
	}

	@Override
	protected Object entryInitialFlow(){

		return emptySet.clone();
	}

	@Override
	protected Object newInitialFlow(){

		return emptySet.clone();
	}

	public void copyResult(Object dest){
		BlueSealGraph out = (BlueSealGraph) dest;
		out.setEdges(this.bsg.getEdges());
		out.setArgNodes(this.bsg.getArgNodes());
		out.setRetNodes(this.bsg.getRetNodes());
		out.setSrc(this.bsg.getSrcs());
		out.setSinks(this.bsg.getSinks());
		out.setCVNodes(this.bsg.getCVNodes());

	}

	public Map<Unit, ArraySparseSet> getUnitToSet(){
		return this.unitToSet;
	}

}
