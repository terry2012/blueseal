package edu.buffalo.cse.blueseal.BSFlow;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.xml.transform.Source;

import edu.buffalo.cse.blueseal.BSG.BlueSealGraph;
import edu.buffalo.cse.blueseal.BSG.Edge;
import edu.buffalo.cse.blueseal.BSG.Node;
import edu.buffalo.cse.blueseal.BSG.SinkNode;
import edu.buffalo.cse.blueseal.BSG.SourceNode;
import edu.buffalo.cse.blueseal.blueseal.Debug;

import soot.Local;
import soot.RefType;
import soot.ResolutionFailedException;
import soot.SootClass;
import soot.SootField;
import soot.SootMethod;
import soot.Type;
import soot.Unit;
import soot.Value;
import soot.baf.Inst;
import soot.jimple.AssignStmt;
import soot.jimple.FieldRef;
import soot.jimple.InstanceFieldRef;
import soot.jimple.InstanceInvokeExpr;
import soot.jimple.InvokeExpr;
import soot.jimple.Stmt;
import soot.jimple.StringConstant;
import soot.toolkits.scalar.ArraySparseSet;

/*
 * this is the class that detect actual flow from
 * a set of method summary in call graph
 */
public class GlobalBSG {

	private Map<SootMethod, BlueSealGraph> methodsSum = new HashMap<SootMethod, BlueSealGraph>();
	private BlueSealGraph gbsg = new BlueSealGraph();
	private boolean isForward_;
	private Iterator entry_;
	// in this map, for each class variable maintain a list of sources that flow
	// into it
	HashMap<String, List<SourceNode>> cvToSource = new HashMap<String, List<SourceNode>>();
	
	public void printMethodSummary(){
		for(Iterator it = CgTransformer.reachableMethods_.iterator(); it.hasNext();){
			SootMethod method = (SootMethod) it.next();
			
			if(!methodsSum.containsKey(method)) continue;
			
			BlueSealGraph bsg = methodsSum.get(method);
			bsg.print();
		}
	}
	
	public void printReachableMethods(){
		Set<SootMethod> reachables = CgTransformer.reachableMethods_;
		
		for(SootMethod method : reachables){
			System.out.println("[BlueSeal]-reachableMethod:"+method.getName());
		}
	}

	public GlobalBSG(Map map, Iterator entry, boolean isForward){
		this.methodsSum = map;
		this.isForward_ = isForward;
		this.entry_=entry;
		

		// detects flow crossing method
		for(Iterator it = entry; it.hasNext();){
			SootMethod method = (SootMethod) it.next();

			if(!methodsSum.containsKey(method))
				continue;

			BlueSealGraph bsg = methodsSum.get(method);
			Set<Edge> srcToSink = bsg.getSrcToSink();
			for(Edge e : srcToSink){
				SourceNode src = (SourceNode) e.getSrc();
				SinkNode sink = (SinkNode) e.getTarget();
				Set<SourceNode> srcList = getOriginalSrc(src, isForward_);
				gbsg.addSink(sink);
				for(SourceNode node : srcList){
					if(node.isOriginal()){

						// skip all the sourcesNode with a contentprovider URI
						if(SourceSinkDetection.isCPSource(node.getStmt()))
							continue;

						gbsg.addSrc(node);
						gbsg.addEdge(node, sink);
					}
				}
			}
		}

		// grab possible flows through CV

		for(SootMethod sm : methodsSum.keySet()){
			HashSet<Edge> cvToSink = methodsSum.get(sm).getCVToSink();

			if(cvToSink.isEmpty()) continue;

			// create a Map from cv => list of SinkNodes
			// use variableName+declaringClassName to identify the CV
			HashMap<String, List<SinkNode>> cvMap = new HashMap<String, List<SinkNode>>();
			for(Edge e : cvToSink){
				String cvname = e.getSrc().getName();
				SinkNode sink = (SinkNode) e.getTarget();
				List<SinkNode> list;

				if(!cvMap.containsKey(cvname)){
					list = new LinkedList<SinkNode>();
				}else{
					list = cvMap.get(cvname);
				}
				list.add(sink);
				cvMap.put(cvname, list);
			}

			// check methods that has written into this CV
			for(SootMethod m : methodsSum.keySet()){
				HashSet<Edge> srcToCV = methodsSum.get(m).getSrcToCV();

				if(srcToCV.isEmpty())
					continue;
				for(Edge e : srcToCV){

					String src_cvname = e.getTarget().getName();
					SourceNode srcNode = (SourceNode) e.getSrc();
					if(!cvMap.containsKey(src_cvname))
						continue;

					List<SinkNode> sinks = cvMap.get(src_cvname);
					// found a flow, add into global bsg
					Set<SourceNode> srcList = getOriginalSrc(srcNode, isForward_);
					for(SourceNode node : srcList){
						if(node.isOriginal()){

							// add sources into the cvToSources map list
							if(cvToSource.containsKey(src_cvname)){
								cvToSource.get(src_cvname).add(node);
							}else{
								List<SourceNode> cv_list = new LinkedList<SourceNode>();
								cv_list.add(node);
								cvToSource.put(src_cvname, cv_list);
							}

							// skip all the sourcesNode with a contentprovider URI
							if(SourceSinkDetection.isCPSource(node.getStmt()))
								continue;

							gbsg.addSrc(node);
							for(SinkNode s : sinks){
								gbsg.addSink(s);
								gbsg.addEdge(node, s);
							}

						}
					}
				}
			}
		}
		
		//TODO: take care of cases CV to CV
		//processCV2CV();

		for(SinkNode sink : gbsg.getSinks()){
			if(isNetworkSink(sink)){
				sink.setNetworkSink();
			}
		}

		// at this point, we have synthesized all the flows in the app,
		// now calculate the flow permissions
		generateFlowPermissions();
	}

	
	private void processCV2CV() {
		BlueSealGraph bsg = new BlueSealGraph();
		BlueSealGraph newBsg = new BlueSealGraph();
		
		for(SootMethod method : methodsSum.keySet()){
			BlueSealGraph mBsg = methodsSum.get(method);
			bsg.addEdges(mBsg.getSrcToCV());
			bsg.addEdges(mBsg.getCVToCV());
			bsg.addEdges(mBsg.getCVToSink());
		}
		Set<SourceNode> sources = bsg.getSrcs();
		for(Iterator it = sources.iterator();it.hasNext();){
			SourceNode orig = (SourceNode) it.next();
			List<Node> flowList = new LinkedList<Node>();
			Set<Node> seen = new HashSet<Node>();
			int depth = 0;
			traverse(orig, flowList, seen, bsg, depth);
			
			//get all the sinks that can reach
			for(Node node : flowList){
				newBsg.addEdge(orig, node);
			}
		}
		gbsg.addEdges(newBsg.getSrcToSink());
		
	}
	
	/*
	 *  do a depth first traversal from current node
	 */
	private void traverse(Node orig, List list, Set<Node> seen, BlueSealGraph bsg,
			int depth) {
		if(depth >= 10) return;
		depth++;
		//if the current node is a sink node, stop
		if(orig instanceof SinkNode){
			//a complete flow path found, add to the whole flow set
			list.add(orig);
			return;
		}
		
		//at this point, it means the path is not finished, check all the children nodes
		for(Iterator iter = bsg.getEdgesOutOf(orig).iterator();iter.hasNext();){
			Edge edge = (Edge)iter.next();
			Set<Node> newseen = new HashSet<Node>();
			newseen.addAll(seen);
			newseen.add(orig);
			if(newseen.contains(edge.getTarget())) continue;
			
			traverse(edge.getTarget(), list, newseen, bsg, depth);
		}
	}

	private void reBuildSummary() {
		for(Iterator it = methodsSum.keySet().iterator(); it.hasNext();){
			SootMethod method = (SootMethod)it.next();
			methodsSum.get(method).rebuildGraph();	
		}
		
	}

	private void generateFlowPermissions(){
		Set<String> flowpermissions = new HashSet<String>();
		Set<Edge> flows = gbsg.getSrcToSink();
		for(Edge e : flows){
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
												SootField definedField = null;
												try{
													definedField = ((FieldRef) rightOp)
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
												}catch(ResolutionFailedException fielde){
													//do nothing
												}
											}//end of checking fieldref case
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

	private Set<SourceNode> getOriginalSrc(SourceNode src, boolean isForward){
		Set<SourceNode> list = new HashSet<SourceNode>();
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
		Set<SourceNode> parents = new HashSet<SourceNode>();
		parents.addAll(src.getSrcList());
		while(!parents.isEmpty()){
			Set<SourceNode> tempList = new HashSet<SourceNode>();
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
