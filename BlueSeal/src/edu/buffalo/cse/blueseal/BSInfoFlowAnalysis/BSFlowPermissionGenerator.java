/*
 * 
 * this class is used to generate flow Permissions based on given flow graph
 */

package edu.buffalo.cse.blueseal.BSInfoFlowAnalysis;

import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import soot.Local;
import soot.ResolutionFailedException;
import soot.SootClass;
import soot.SootField;
import soot.Unit;
import soot.Value;
import soot.jimple.AssignStmt;
import soot.jimple.FieldRef;
import soot.jimple.InvokeExpr;
import soot.jimple.Stmt;
import soot.jimple.StringConstant;
import soot.toolkits.scalar.ArraySparseSet;
import edu.buffalo.cse.blueseal.BSFlow.BSInterproceduralAnalysis;
import edu.buffalo.cse.blueseal.BSFlow.SourceSink;
import edu.buffalo.cse.blueseal.BSFlow.SourceSinkDetection;
import edu.buffalo.cse.blueseal.BSG.BlueSealGraph;
import edu.buffalo.cse.blueseal.BSG.Edge;
import edu.buffalo.cse.blueseal.BSG.SinkNode;
import edu.buffalo.cse.blueseal.BSG.SourceNode;

public class BSFlowPermissionGenerator {

	private BlueSealGraph flowGraph = null;
	private Set<String> flowpermissions = new HashSet<String>();
	
	public BSFlowPermissionGenerator(BlueSealGraph bsg){
		flowGraph = bsg;
	}
	
	public void generate(){
		generateFlowPermissions();
	}
	
	/*
	 * print out all the flow permissions
	 */
	public void printFlowPermissions(){
		for(Iterator it = flowpermissions.iterator(); it.hasNext();){
			String fp = (String) it.next();
			System.out.println("flow permission:" + fp);
		}
	}

	/*
	 * generate flow permissions
	 */
	private void generateFlowPermissions() {
		Set<Edge> flows = flowGraph.getSrcToSink();
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
													if(BSGlobalFlowGraphBuilder.cvToSource.containsKey(definedCalss.getName()
															+ definedField.getName())){
														List<SourceNode> cvsrc = BSGlobalFlowGraphBuilder.cvToSource.get(definedCalss
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
											if(BSGlobalFlowGraphBuilder.cvToSource.containsKey(definedCalss.getName()
													+ definedField.getName())){
												List<SourceNode> cvsrc = BSGlobalFlowGraphBuilder.cvToSource.get(definedCalss
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
	}

	public Set<String> getFlowPermissions() {
		return this.flowpermissions;
	}
	
	
}
