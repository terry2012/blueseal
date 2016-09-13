package edu.buffalo.cse.blueseal.BSFlow;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import soot.Hierarchy;
import soot.Local;
import soot.RefType;
import soot.Scene;
import soot.SootClass;
import soot.SootField;
import soot.SootMethod;
import soot.Type;
import soot.Unit;
import soot.Value;
import soot.jimple.AssignStmt;
import soot.jimple.FieldRef;
import soot.jimple.InstanceInvokeExpr;
import soot.jimple.InvokeExpr;
import soot.jimple.Stmt;
import soot.jimple.StringConstant;
import soot.toolkits.scalar.ArraySparseSet;
import edu.buffalo.cse.blueseal.BSG.BlueSealGraph;
import edu.buffalo.cse.blueseal.BSG.CVNode;
import edu.buffalo.cse.blueseal.BSG.Edge;
import edu.buffalo.cse.blueseal.BSG.Node;
import edu.buffalo.cse.blueseal.BSG.SinkNode;
import edu.buffalo.cse.blueseal.BSG.SourceNode;

public class FlowSynthesizer {


	private Map<SootMethod, BlueSealGraph> methodsSum = new HashMap<SootMethod, BlueSealGraph>();
	private BlueSealGraph gbsg = new BlueSealGraph();
	private boolean isForward_;
	private Iterator reachable_;
	// in this map, for each class variable maintain a list of sources that flow
	// into it
	HashMap<String, List<SourceNode>> cvToSource = new HashMap<String, List<SourceNode>>();

	public FlowSynthesizer(Map map, Iterator rechable, boolean isForward){
		this.methodsSum = map;
		this.isForward_ = isForward;
		this.reachable_ = rechable;
		System.out.println(" >>>>>>>>>>>>>>>>>in flow synthesizer");
		synthesise();
}

	/*
	 * this method will do all the work to detect all the flows
	 * first thing is to construct the program global graph
	 * 1. add all the source->sink edges && nodes
	 * 2. add all the source->CV edges && nodes
	 * 3. add all the CV->sink edges && nodes
	 * 4. add all the CV->CV edges && nodes
	 */
	private void synthesise(){
		System.out.println("~~~~~~~~~~flow generation~~~");
		constructGlobalGraph();
		Set<Edge> flows = synthesizeFlows();
		System.out.println("after flows>>>>out");
		Set<Edge> origFlows = flowSourceTraceBack(flows);
		generateFlowPermissions(origFlows);
		//print out all the flows
		displayFlow(origFlows);
	}

	private void displayFlow(Set<Edge> origFlows){
		System.out.println("~~~~~~ in dispaly flow:~~~~~~~~~~~~~");
		int count = 1;
		
		for(Edge flow : origFlows){
			System.out.println("Flow #"+ count+":");
			flow.printFlow();
			count++;
		}
		System.out.println("~~~~~~ in dispaly flow done~~~~~~~~~~~~~");
		
	}

	private Set<Edge> flowSourceTraceBack(Set<Edge> flows){
		Set<Edge> origFlows = new HashSet<Edge>();
		for(Edge flow : flows){
			SourceNode source = (SourceNode) flow.getSrc();
			
			if(source.isOriginal()){
				origFlows.add(flow);
				continue;
			}
			
			List<SourceNode> parents = getOriginalSrc(source, isForward_);
			for(SourceNode parent : parents){
				origFlows.add(new Edge(parent, flow.getTarget()));
			}
		}
		return origFlows;
	}

	/*
	 * synthesize all the blueseal flows from the global graph
	 * iterate all the source node in the global graph
	 * for each source, do a depth first search, if it reaches a sink node,
	 * then a flow is synthesized
	 * return list of flows(only edges from souces to sinks)
	 * NOTE: using list instead of set to allow duplicated flows
	 * this might be used later
	 */
	private Set<Edge> synthesizeFlows(){
		Set<Edge> flows = new HashSet<Edge>();
		for(Iterator it=gbsg.getSrcs().iterator();it.hasNext();){
			SourceNode source = (SourceNode) it.next();
			Set<Node> seen = new HashSet<Node>();
			seen.add(source);
			Set<Node> sinks = visitAllMyChildren(source,seen);
			
			for(Iterator sinkIt= sinks.iterator(); sinkIt.hasNext();){
				Node node = (Node) sinkIt.next();
				if(!(node instanceof SinkNode)){
					System.err.println("something goes wrong, return non");
					System.exit(1);
				}
				
				flows.add(new Edge(source, node));
			}
		}
		return flows;
	}

	/*
	 * this will find all the children of given node
	 * the purpose is to find all the sinks in the children
	 * perform a depth first search
	 */
	private Set<Node> visitAllMyChildren(Node node, Set<Node> parent){
		Set<Node> sinks = new HashSet<Node>();
		/*
		 * if current node is a sink node, the path ends
		 * there is no out edge from a sink
		 */
		if(node instanceof SinkNode) {
			sinks.add(node);
			return sinks;
		}
		
		/*
		 * otherwise, check all the children of current node
		 */
		Set<Node> children = gbsg.getSuccessorsOf(node);
		Set<Node> myParent = new HashSet<Node>();
		myParent.addAll(parent);
		myParent.add(node);
		for(Iterator it = children.iterator();it.hasNext();){
			Node child = (Node) it.next();
			
			if(myParent.contains(child)) continue;

			sinks.addAll(visitAllMyChildren(child,myParent));
		}
		return sinks;
	}

	/*
	 * global blueseal graph builder
	 */
	private void constructGlobalGraph(){
		for(Iterator it=reachable_; it.hasNext();){
			SootMethod method = (SootMethod) it.next();
			
			if(!methodsSum.containsKey(method)) continue;

			BlueSealGraph summary = methodsSum.get(method);
			gbsg.addEdges(summary.getSrcToSink());
			
			//source to CV
			Set<Edge> srcToCV = new HashSet<Edge>();
			for(Iterator iter = summary.getSrcToCV().iterator(); iter.hasNext();){
				Edge e = (Edge) iter.next();
				SourceNode source = (SourceNode) e.getSrc();
				CVNode cv = (CVNode) e.getTarget();
				CVNode newCV = new CVNode(cv.getName(),
						cv.getSootClass(), cv.getFieldName());
				srcToCV.add(new Edge(source,newCV));
			}
			gbsg.addEdges(srcToCV);
			
			//cv To sink
			Set<Edge> cvToSink = new HashSet<Edge>();
			for(Iterator iter = summary.getCVToSink().iterator(); iter.hasNext();){
				Edge e = (Edge) iter.next();
				SinkNode sink = (SinkNode) e.getTarget();
				CVNode cv = (CVNode) e.getSrc();
				CVNode newCV = new CVNode(cv.getName(),
						cv.getSootClass(), cv.getFieldName());
				cvToSink.add(new Edge(newCV, sink));
			}
			gbsg.addEdges(cvToSink);
			
			//CV to CV
			Set<Edge> cvToCV = new HashSet<Edge>();
			for(Iterator iter = summary.getCVToCV().iterator(); iter.hasNext();){
				Edge e = (Edge) iter.next();
				CVNode cvSrc = (CVNode)e.getSrc();
				CVNode cvTgt = (CVNode)e.getTarget();
//				CVNode newCVSrc = new CVNode(cvSrc.getName(),
//						cvSrc.getSootClass(), cvSrc.getFieldName());
//				CVNode newCVTgt = new CVNode(cvTgt.getName(), 
//						cvTgt.getSootClass(), cvTgt.getFieldName());
				
				if(cvSrc.equals(cvTgt)) continue;
				
				cvToCV.add(e);
			}
			gbsg.addEdges(cvToCV);
		}
		
		//take care of class variable inheritance
		handleCVInheritance();
		
	}
	
	private void handleCVInheritance(){
		Hierarchy hierarchy = Scene.v().getActiveHierarchy();
		Set<CVNode> allCVNodes = gbsg.getCVNodes();
		
		for(Iterator it = allCVNodes.iterator(); it.hasNext();){
			CVNode cvnode = (CVNode) it.next();
			SootClass sootClass = cvnode.getSootClass();
			String field = cvnode.getFieldName();
			List<SootClass> superClasses = hierarchy.getSuperclassesOf(sootClass);
			
			for(SootClass sc : superClasses){
				if(sc.declaresFieldByName(field)){
					//this means this is inherited from super class
					CVNode newcv = new CVNode(sc.getName()+field,
							sc,field);
					
					if(gbsg.contains(newcv)){
						Set out = gbsg.getEdgesOutOf(newcv);
						
						for(Iterator outIt = out.iterator(); outIt.hasNext();){
							Edge edge = (Edge) outIt.next();
							gbsg.addEdge(cvnode, edge.getTarget());
						}
						
						Set in = gbsg.getEdgesInto(newcv);
						
						for(Iterator inIt = in.iterator(); inIt.hasNext();){
							Edge edge = (Edge) inIt.next();
							gbsg.addEdge(edge.getSrc(), cvnode);
						}
					}
				}
			}
		}
	}

	private void generateFlowPermissions(Set<Edge> origFlows){
		Set<String> flowpermissions = new HashSet<String>();
		for(Edge e : origFlows){
			SourceNode src = (SourceNode) e.getSrc();
			SinkNode sink = (SinkNode) e.getTarget();
			String srcCategory = null;
			String sinkCategory = null;

			// skip all the flow that start with a contentprovider uri
			if(SourceSinkDetection.isCPSource(src.getStmt()))
				continue;

			// check category of source node
			// **for API sources
			if(src.getStmt().containsInvokeExpr()){
				Stmt stmt = src.getStmt();
				InvokeExpr ie = stmt.getInvokeExpr();
				String className = ie.getMethodRef().declaringClass().getName();
				String methodName = ie.getMethodRef().name();
				if(SourceSink.categoryMap.containsKey(className + ":" + methodName)){
					srcCategory = SourceSink.categoryMap
							.get(className + ":" + methodName);
				}

				/*
				 * check if it's a Log sources, since we add the Log source separately,
				 * we have to check it separately here
				 */
				if(className.equals("java.lang.Runtime") && methodName.equals("exec")
						&& stmt.toString().contains("logcat")){
					srcCategory = "log";
				}

				/*
				 * check query from a content provider
				 */
				if(srcCategory == null){// if there is already a category, skip
					SourceSinkDetection ssd = new SourceSinkDetection();
					if(ssd.isCPQuery(src.getStmt())){
						// make sure this a content provider query source
						srcCategory = "content provider";
						// fetch the uri argument
						Value uriVal = ie.getArg(0);
						if(uriVal instanceof Local){
							// the URI is defined locally
							// find the content provider flows into this method
							if(BSInterproceduralAnalysis.getMethodSummary().containsKey(
									sink.getMethod())){
								Map<Unit, ArraySparseSet> summary = BSInterproceduralAnalysis
										.getMethodSummary().get(sink.getMethod());
								if(summary.containsKey((Unit) sink.getStmt())){
									// this should always be true, add this just in case
									ArraySparseSet flowset = summary.get((Unit) sink.getStmt());
									for(Iterator it = flowset.iterator(); it.hasNext();){
										Unit unit = (Unit) it.next();
										Stmt flowstmt = (Stmt) unit;
										SourceSinkDetection detectCP = new SourceSinkDetection();
										if(detectCP.isCPSource(flowstmt)){
											srcCategory = detectCP.contentprovider;
										}
										// another case is that URI is passed via a class variable
										// in Soot, the class variable is assigned to a local
										// variable first, then passed to the API
										if(flowstmt instanceof AssignStmt){
											Value rightOp = ((AssignStmt) flowstmt).getRightOp();
											// find the related class variable first
											if(rightOp instanceof FieldRef){
												SootClass definedCalss = ((FieldRef) rightOp)
														.getFieldRef().declaringClass();
												SootField definedField = ((FieldRef) rightOp)
														.getField();
												if(cvToSource.containsKey(definedCalss.getName()
														+ definedField.getName())){
													List<SourceNode> cvsrc = cvToSource.get(definedCalss
															.getName() + definedField.getName());

													// find all the content provider sources
													for(SourceNode srcnode : cvsrc){
														SourceSinkDetection cvssd = new SourceSinkDetection();
														if(cvssd.isCPSource(srcnode.getStmt())){
															srcCategory = cvssd.contentprovider;
														}
													}
												}
											}
											if(flowstmt.containsInvokeExpr()){
												// in this case, the URI is provided by a constant string
												// dont handle this yet
												// TODO: to handle this, we should be able to track
												// building a string in android, URI is built using
												// Uri.builder
												// first way to construct uri using a constant string,
												// android.net.Uri: parse(string)
												InvokeExpr parseie = flowstmt.getInvokeExpr();
												SootClass defClass = parseie.getMethodRef()
														.declaringClass();
												String mName = parseie.getMethodRef().name();
												if(defClass.getName().equals("android.net.Uri")
														&& mName.equals("parse")){
													// get the argument
													Value arg = parseie.getArg(0);
													if(arg instanceof StringConstant){
														srcCategory = arg.toString();
													}
												}
											}
										}
									}
								}
							}
						}
					}

				}
			}
			// **API sinks
			if(sink.getStmt().containsInvokeExpr()){
				Stmt stmt = sink.getStmt();
				InvokeExpr ie = stmt.getInvokeExpr();
				String className = ie.getMethodRef().declaringClass().getName();
				String methodName = ie.getMethodRef().name();
				if(SourceSink.categoryMap.containsKey(className + ":" + methodName)){
					sinkCategory = SourceSink.categoryMap.get(className + ":"
							+ methodName);
				}

				if(sink.isNetworkSink()){
					sinkCategory = "network";
				}
			}

			// //***check ContentProvider source
			// if(srcCategory == null){//not a API source
			// SourceSinkDetection ssd = new SourceSinkDetection();
			// if(ssd.isCPSource(src.getStmt())){//is a CP source
			// srcCategory = ssd.contentprovider;
			// //Problem: we use the real content provider string as a category
			// // problem is that this might be a constant string or a Android field
			// string
			// //TODO: handle the above problem
			// }
			// }

			// ***check ContentProvider Sink
			// TODO: currently only can handle locally assigned URI
			// cannot handle class variable passed URI
			// the reason is that class variable assignment unit is not the flowset of
			// insert or update
			if(sinkCategory == null){
				SourceSinkDetection ssd = new SourceSinkDetection();

				if(ssd.isCPSink(sink.getStmt())){// is a CP sink(insert or update)
					if(!sink.getStmt().containsInvokeExpr()){
						System.err
								.println("System error, CP sink can only be insert() or update()");
						System.exit(1);
					}
					sinkCategory = "content provider";
					// fetch the uri argument
					InvokeExpr ie = sink.getStmt().getInvokeExpr();
					Value uriVal = ie.getArg(0);
					if(uriVal instanceof Local){// the URI is defined locally
						// find the content provider flows into this method
						if(BSInterproceduralAnalysis.getMethodSummary().containsKey(
								sink.getMethod())){
							Map<Unit, ArraySparseSet> summary = BSInterproceduralAnalysis
									.getMethodSummary().get(sink.getMethod());
							if(summary.containsKey((Unit) sink.getStmt())){
								// this should always be true, add this just in case
								ArraySparseSet flowset = summary.get((Unit) sink.getStmt());
								for(Iterator it = flowset.iterator(); it.hasNext();){
									Unit unit = (Unit) it.next();
									Stmt stmt = (Stmt) unit;
									SourceSinkDetection detectCP = new SourceSinkDetection();
									if(detectCP.isCPSource(stmt)){
										sinkCategory = detectCP.contentprovider;
									}
									// another case is that URI is passed via a class variable
									// in Soot, the class variable is assigned to a local variable
									// first, then passed to the API
									if(stmt instanceof AssignStmt){
										Value rightOp = ((AssignStmt) stmt).getRightOp();
										// find the related class variable first
										if(rightOp instanceof FieldRef){
											SootClass definedCalss = ((FieldRef) rightOp)
													.getFieldRef().declaringClass();
											SootField definedField = ((FieldRef) rightOp).getField();
											if(cvToSource.containsKey(definedCalss.getName()
													+ definedField.getName())){
												List<SourceNode> cvsrc = cvToSource.get(definedCalss
														.getName() + definedField.getName());

												// find all the content provider sources
												for(SourceNode srcnode : cvsrc){
													SourceSinkDetection cvssd = new SourceSinkDetection();
													if(cvssd.isCPSource(srcnode.getStmt())){
														sinkCategory = cvssd.contentprovider;
													}
												}
											}
										}
									}
								}
							}
						}
					}

				}
			}

			// generate a flow permissions if neither source category nor sink
			// category is null
			if(srcCategory != null && sinkCategory != null){
				flowpermissions.add(srcCategory + "->" + sinkCategory);
			}

		}

		for(Iterator it = flowpermissions.iterator(); it.hasNext();){
			String fp = (String) it.next();
			System.out.println("flow permission:" + fp);
		}
	}

	/*
	 * the following code is detecting network sinks one way in Android to send
	 * data to network is using httpURLConnection but in httpURLConnection, the
	 * data is written into the outputStream returned by getOutputStream() the
	 * outputStream can vary, there are many different outputStream in Android the
	 * outputStream sink can be either written into network or written into a file
	 * step1. determine the sink is one of the outputSteam sink step2. look up its
	 * flowSet to see if its returned by getOutputSteam()
	 * 
	 * this should be called after getting global flow graph
	 */
	private boolean isNetworkSink(SinkNode sink){
		SootMethod method = sink.getMethod();
		Stmt stmt = sink.getStmt();
		if(!(stmt.containsInvokeExpr()))
			return false;

		InvokeExpr ie = stmt.getInvokeExpr();
		if(!(ie instanceof InstanceInvokeExpr))
			return false;

		InstanceInvokeExpr iie = (InstanceInvokeExpr) ie;
		Type type = iie.getBase().getType();

		if(!(type instanceof RefType))
			return false;

		String className = ((RefType) type).getClassName();
		if(className.equals("java.io.OutputStream")
				|| className.equals("java.io.ByteArrayOutputStream")
				|| className.equals("org.apache.http.impl.io.ChunkedOutputStream")
				|| className
						.equals("org.apache.http.impl.io.ContentLengthOutputStream")
				|| className.equals("java.io.FileOutputStream")
				|| className.equals("java.io.FilterOutputStream")
				|| className.equals("org.apache.http.impl.io.IdentityOutputStream")
				|| className.equals("java.io.ObjectOutputStream")
				|| className.equals("java.io.PipedOutputStream")
				|| className
						.equals("android.content.res.AssetFileDescriptor.AutoCloseOutputStream")
				|| className.equals("android.util.Base64OutputStream")
				|| className.equals("java.io.BufferedOutputStream")
				|| className.equals("java.util.zip.CheckedOutputStream")
				|| className.equals("javax.crypto.CipherOutputStream")
				|| className.equals("java.io.DataOutputStream")
				|| className.equals("java.util.zip.DeflaterOutputStream")
				|| className.equals("java.security.DigestOutputStream")
				|| className.equals("java.util.zip.GZIPOutputStream")
				|| className.equals("java.util.zip.InflaterOutputStream")
				|| className.equals("java.util.jar.JarOutputStream")
				|| className.equals("java.io.PrintStream")
				|| className.equals("java.util.zip.ZipOutputStream")
				|| className.equals("java.io.Writer")
				|| className.equals("java.io.OutputStream")){
			ArraySparseSet flowSet = BSInterproceduralAnalysis.getMethodSummary()
					.get(method).get(stmt);
			for(Iterator it = flowSet.iterator(); it.hasNext();){
				Stmt flowStmt = (Stmt) it.next();
				if(!(flowStmt.containsInvokeExpr()))
					continue;

				InvokeExpr invoke = flowStmt.getInvokeExpr();
				if(!(invoke instanceof InstanceInvokeExpr))
					continue;

				InstanceInvokeExpr instinvoke = (InstanceInvokeExpr) invoke;
				Type refType = instinvoke.getBase().getType();

				if(!(refType instanceof RefType))
					continue;

				String refClassName = ((RefType) refType).getClassName();

				if(refClassName.equals("java.net.URLConnection")
						|| refClassName.equals("java.net.HttpURLConnection")
						|| refClassName.equals("javax.net.ssl.HttpsURLConnection")
						|| refClassName.equals("java.net.JarURLConnection")){
					if(instinvoke.getMethod().getName().equals("getOutputStream")){
						return true;
					}
				}
			}
		}

		return false;
	}

	private List<SourceNode> getOriginalSrc(SourceNode src, boolean isForward){
		List<SourceNode> list = new LinkedList<SourceNode>();
		if(src.isOriginal()){
			list.add(src);
		}

		if(src.getSrcList().isEmpty()){
			return list;
		}

		// since the sourceNode is not original, we check the list of SourceNode
		// and find all the original source node that flows into the current node
		// for(SourceNode node : src.getSrcList()){
		// if(node.isOriginal()){
		// list.add(src);
		// }
		// }
		// --feng 2014/03/24
		List<SourceNode> parents = src.getSrcList();
		while(!parents.isEmpty()){
			List<SourceNode> tempList = new LinkedList<SourceNode>();
			for(SourceNode node : parents){
				if(node.isOriginal()){
					list.add(node);
				}else{
					tempList.addAll(node.getSrcList());
				}
			}
			parents = tempList;
		}

		// SourceNode node = src;
		// {
		// Stmt stmt = node.getStmt();
		//
		// if(!stmt.containsInvokeExpr()){
		// System.err.print("Something wrong with BSG summary!\n");
		// System.exit(1);
		// }
		//
		// InvokeExpr invoke = stmt.getInvokeExpr();
		// SootMethod method = invoke.getMethod();
		//
		// if(!methodsSum.containsKey(method)) return list;
		// BlueSealGraph bsg = methodsSum.get(method);
		//
		// /*
		// * the following part is for forward flow
		// */
		// if(isForward){
		// Set<Edge> srcToRet = bsg.getSrcToRet();
		//
		// for(Edge e : srcToRet){
		// SourceNode eSrc = (SourceNode) e.getSrc();
		// List<SourceNode> esList = getOriginalSrc(eSrc, isForward);
		//
		// if(!esList.isEmpty()) list.addAll(esList);
		// }
		// }else{
		// /*
		// * the following part is for backward flow
		// */
		// Set<Edge> srcToArg = bsg.getSrcToArg();
		// for(Edge e : srcToArg){
		// SourceNode eSrc = (SourceNode) e.getSrc();
		// List<SourceNode> esList = getOriginalSrc(eSrc, isForward);
		//
		// if(!esList.isEmpty()) list.addAll(esList);
		// }
		// }
		// }
		return list;
	}

	public void print(){
		InterProceduralMain.ps
				.println("~~~~~~~~printing final results~~~~~~~~~~~~~~~~~~~~~~~\n");
		gbsg.printFlow();
		InterProceduralMain.ps
				.println("~~~~~~~~printing final results done~~~~~~~~~~~~~~~~~~~~\n");
	}

	/*
	 * print out all the source node in the graph, for debug purpose
	 */

	public void printAllSrcNodes(){
		Set sns = this.gbsg.getSrcs();
		for(Iterator it = sns.iterator(); it.hasNext();){
			SourceNode sn = (SourceNode) it.next();
			System.out.println("~~~gbsg source node:" + sn.getName()
					+ "<=>in method:" + sn.getMethod().getName());
		}
	}

	/*
	 * print out all the sink node in the graph, for debug purpose
	 */

	public void printAllSinkNodes(){
		Set sns = this.gbsg.getSinks();
		for(Iterator it = sns.iterator(); it.hasNext();){
			SinkNode sn = (SinkNode) it.next();
			System.out.println("~~~gbsg sink node:" + sn.getName() + "<=>in method:"
					+ sn.getMethod().getName());
		}
	}

	public void printAllEdges(){
		Set edges = this.gbsg.getEdges();
		System.out.println("print all edges:");
		for(Iterator it = edges.iterator(); it.hasNext();){
			Edge e = (Edge) it.next();
			e.printFlow();
		}
	}

}
