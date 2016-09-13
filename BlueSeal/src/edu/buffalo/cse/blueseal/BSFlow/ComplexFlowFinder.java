/*
 * this class is used to extract complex flow information after running BSInterprocedural
 * data flow analysis
 */

package edu.buffalo.cse.blueseal.BSFlow;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import soot.SootClass;
import soot.SootMethod;
import soot.jimple.Stmt;
import edu.buffalo.cse.blueseal.BSG.BlueSealGraph;
import edu.buffalo.cse.blueseal.BSG.CVNode;
import edu.buffalo.cse.blueseal.BSG.Edge;
import edu.buffalo.cse.blueseal.BSG.Node;
import edu.buffalo.cse.blueseal.BSG.SinkNode;
import edu.buffalo.cse.blueseal.BSG.SourceNode;
import edu.buffalo.cse.blueseal.BSG.TransitiveNode;

public class ComplexFlowFinder {
	private Map<SootMethod, BlueSealGraph> methodsSum =
			new HashMap<SootMethod, BlueSealGraph>();//map: method->summary
	private BlueSealGraph complexbsg = new BlueSealGraph();
	private boolean isForward_;
	private Set<List> wholeFlowSet = new HashSet<List>();
	private HashMap<SootMethod, Set> sinkToFlows = new HashMap<SootMethod, Set>();
	public ComplexFlowFinder(Map summary, Iterator entry, boolean isForward){
		this.methodsSum = summary;
		this.isForward_ = isForward;
		
		//detects flow crossing method
		for(Iterator it = entry; it.hasNext();){
			SootMethod method = (SootMethod)it.next();
			if(!methodsSum.containsKey(method)) continue;
			
			processSingleMethod(method);
		}
		//process all the class varaibles
		processCV();
		//finally execute finder, basically do a depth traversal from the original sources
		execute();
//		printWholeFlowSet(); 
		findComplexFlows();
		displayComplexFlow();
	}

	private void findComplexFlows(){
		for( Iterator it = wholeFlowSet.iterator(); it.hasNext();){
			List flow = (List) it.next();
			//get the sink node
			Node node = (Node) flow.get(flow.size()-1);
			
			if(!(node instanceof SinkNode)) continue;
			
			SinkNode sink = (SinkNode) node;
			Stmt stmt = sink.getStmt();
			
			//now only take care of API sinks
			if(!stmt.containsInvokeExpr()) continue;
			
			SootMethod sinkM = stmt.getInvokeExpr().getMethod();
			SootClass sinkClass = stmt.getInvokeExpr().getMethod().getDeclaringClass();
			
			if(!sinkToFlows.containsKey(sinkM)){
				HashSet newset = new HashSet();	
				sinkToFlows.put(sinkM, newset);
			}
			sinkToFlows.get(sinkM).add(flow);
		}
	}
	
	private void displayComplexFlow(){
		System.out.println(" ~~~~~~~~~ printing complex flows ~~~~~~~~~~~~~~~~");
		int count = 1;
		for(Iterator it = sinkToFlows.keySet().iterator();it.hasNext();){
			SootMethod sink = (SootMethod) it.next();
			System.out.println("ComplexFlow #"+count+":" + sink.getDeclaringClass().getName() + "::"+sink.getName());
			count++;
			Set flowset = sinkToFlows.get(sink);
			int flowcount = 1;
			for(Iterator itr = flowset.iterator(); itr.hasNext();){
				List list = (List) itr.next();
				System.out.println("Flow #"+flowcount+":");
				printFlowList(list);
				flowcount++;
			}
		}
		System.out.println(" ~~~~~~~~~ printing complex flows done~~~~~~~~~~~~~~~~");
	}

	private void printFlowList(List list){
		for(Iterator listIt = list.iterator();listIt.hasNext();){
			Node node = (Node) listIt.next();
			node.print();
		}
	}

	private void printWholeFlowSet(){
		System.out.println("=============> printing whole flow <===============");
		int count=1;
		for(Iterator it = wholeFlowSet.iterator();it.hasNext();){
			System.out.println(" Whole flow #"+count+":");
			List list = (List)it.next();
			for(Iterator listIt = list.iterator();listIt.hasNext();){
				Node node = (Node) listIt.next();
				node.print();
			}
			count++;
		}
	}

	private void execute() {
		// execute the finder to extract all paths from original source to sink
		//basically do depth traversal
		Set origSources = complexbsg.getSrcs();
		for(Iterator it = origSources.iterator();it.hasNext();){
			SourceNode orig = (SourceNode) it.next();
			List<Node> flowList = new LinkedList<Node>();
			Set<Node> seen = new HashSet<Node>();
			traverse(orig, flowList, seen);
		}
		
	}
	
	/*
	 *  do a depth first traversal from current node
	 */
	private void traverse(Node orig, List list, Set<Node> seen) {
		List tempList = new LinkedList<Node>();
		tempList.addAll(list);
		tempList.add(orig);
		//if the current node is a sink node, stop
		if(orig instanceof SinkNode){
			//a complete flow path found, add to the whole flow set
			wholeFlowSet.add(tempList);
			return;
		}
		
		//at this point, it means the path is not finished, check all the children nodes
		for(Iterator iter = complexbsg.getEdgesOutOf(orig).iterator();iter.hasNext();){
			Edge edge = (Edge)iter.next();
			Set<Node> newseen = new HashSet<Node>();
			newseen.addAll(seen);
			newseen.add(orig);
			if(newseen.contains(edge.getTarget())) continue;
			
			traverse(edge.getTarget(), tempList, newseen);
		}
	}

	private void processCV() {
		//grab possible flows through CV
		for(SootMethod sm : methodsSum.keySet()){
			
			BlueSealGraph sum = methodsSum.get(sm);
			
			HashSet<Edge> cvToSink = sum.getCVToSink();
			if(!cvToSink.isEmpty()){
				//iterate all the edges from classVariables to sinks
				for (Edge e : cvToSink){
					CVNode cv = (CVNode) e.getSrc();
					if(!cv.getSootClass().isApplicationClass()) continue;
					
					SinkNode sink = (SinkNode) e.getTarget();
					//add the cv node to the complex bsg
					complexbsg.addCVNode(cv);
					complexbsg.addSink(sink);
					complexbsg.addEdge(e);
				}
			}
			
			//iterate all the edges from sources to classVariables
			HashSet<Edge> srcToCV = sum.getSrcToCV();
			if(!srcToCV.isEmpty()){
				for(Edge e : srcToCV){
					CVNode cv = (CVNode) e.getTarget();
					SourceNode srcNode = (SourceNode) e.getSrc();
					if(!cv.getSootClass().isApplicationClass()) continue;
					//add the cv to the complex bsg
					complexbsg.addCVNode(cv);
					//resolve the srcNode, same as we do in process single method
					if(srcNode.isOriginal()){
						//assume there is no input node to a original source node
						complexbsg.addSrc(srcNode);
						complexbsg.addEdge(srcNode, cv);
					}else{
						//create a new transitive node and add to complex bsg
						TransitiveNode tn = new TransitiveNode(srcNode);
						complexbsg.addTransNode(tn);
						complexbsg.addEdge(tn, cv);
						expandTransNode(tn);
					}
				} // end of iterate all the edges in one method
			}
			
			HashSet<Edge> cvToCV = sum.getCVToCV();
			if(!cvToCV.isEmpty()){
				for(Edge e : cvToCV){
					CVNode cv1 = (CVNode) e.getSrc();
					CVNode cv2 = (CVNode) e.getTarget();
					if(!cv1.getSootClass().isApplicationClass()
							|| !cv2.getSootClass().isApplicationClass())
						continue;
					complexbsg.addEdge(e);
				}
			}
			


		}
		
	}

	private void processSingleMethod(SootMethod method) {
		
		BlueSealGraph bsg = methodsSum.get(method);
		Set<Edge> srcToSink = bsg.getSrcToSink();
		
		for(Edge e : srcToSink){
			SourceNode src = (SourceNode) e.getSrc();
			SinkNode sink = (SinkNode) e.getTarget();
			complexbsg.addSink(sink);	
			if(src.isOriginal()){
				//assume there are no other sourceNode flows into original sourceNode
				complexbsg.addSrc(src);
				complexbsg.addEdge(src, sink);
			}else{
				TransitiveNode tn = new TransitiveNode(src);
				//add to the complex bsg
				complexbsg.addTransNode(tn);
				complexbsg.addEdge(tn,sink);
				expandTransNode(tn);
			}
		}
		
	}

	private void expandTransNode(TransitiveNode tn) {
		// expand all the parent node of current intermeidate sourceNode(transNode)
		List<SourceNode> parents = tn.getSrcNode().getSrcList();
		for(SourceNode parent : parents){
			if(parent.isOriginal()){
				//assume there are no other sourceNode flows into original sourceNode
				complexbsg.addSrc(parent);
				complexbsg.addEdge(parent, tn);
			}else{
				TransitiveNode newTran = new TransitiveNode(parent);
				//add to the complex bsg
				complexbsg.addTransNode(newTran);
				complexbsg.addEdge(newTran,tn);
				expandTransNode(newTran);
			}
		}
	}
	
	
}
