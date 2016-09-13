/*
 * this class is to extract blueseal flows from global flow graph
 * 
 */
package edu.buffalo.cse.blueseal.BSInfoFlowAnalysis;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

import soot.RefType;
import soot.SootMethod;
import soot.Type;
import soot.jimple.InstanceInvokeExpr;
import soot.jimple.InvokeExpr;
import soot.jimple.Stmt;
import soot.toolkits.scalar.ArraySparseSet;

import edu.buffalo.cse.blueseal.BSFlow.BSInterproceduralAnalysis;
import edu.buffalo.cse.blueseal.BSFlow.InterProceduralMain;
import edu.buffalo.cse.blueseal.BSG.BlueSealGraph;
import edu.buffalo.cse.blueseal.BSG.Edge;
import edu.buffalo.cse.blueseal.BSG.FlowGraphBreadthFirstWalker;
import edu.buffalo.cse.blueseal.BSG.Node;
import edu.buffalo.cse.blueseal.BSG.SinkNode;
import edu.buffalo.cse.blueseal.BSG.SourceNode;

public class BSFlowGraphResolver {
	
	private BSGlobalFlowGraphBuilder builder = null;
	private BlueSealGraph rebuiltIndirectFlowGraph = null;
	private BlueSealGraph finalFlowGraph = null;
	
	public BSFlowGraphResolver(BSGlobalFlowGraphBuilder flowgraphbuilder){
		builder = flowgraphbuilder;
		finalFlowGraph = new BlueSealGraph();
	}
	
	public BlueSealGraph getFinalFlowGraph(){
		return this.finalFlowGraph;
	}
	
	public void resolve(){
		iterateIndirectFlowGraph();
		constructFinalFlowGraph();
	}
	
	
	/*
	 * since we are mostly interested in network sinks,
	 * after building up the whole flow graph
	 * label the network sink nodes for future query
	 */
	public void labelNetworkSinkNode(){
		for(SinkNode sink : finalFlowGraph.getSinks()){
			if(isNetworkSink(sink)){
				sink.setNetworkSink();
			}
		}
	}
	
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
	
	/*
	 * after rebuilding the indirect flow graph
	 * merge direct flow graph and indirect flow graph to 
	 * construct a single flow graph for given app,
	 * this should only contain information flows
	 */
	
	public void constructFinalFlowGraph(){
		for(Iterator it = builder.getDirectFlowGraph().getEdges().iterator();it.hasNext();){
			Edge e = (Edge) it.next();
			finalFlowGraph.addEdge(e);
		}
		
		for(Iterator it = rebuiltIndirectFlowGraph.getEdges().iterator();it.hasNext();){
			Edge e = (Edge) it.next();
			e.setIndirect();
			finalFlowGraph.addEdge(e);
		}
	}
	
	/*
	 * iterate the indirect flow graph from flow graph builder
	 * find all the indirect flows
	 * rebuild the indirect flow graph of given app
	 */
	public void iterateIndirectFlowGraph(){
		rebuiltIndirectFlowGraph = new BlueSealGraph();
		BlueSealGraph indirectFlowGraph = builder.getIndirectFlowGraph();
		Set<SourceNode> srcs = indirectFlowGraph.getSrcs();
		FlowGraphBreadthFirstWalker walker = new FlowGraphBreadthFirstWalker(
				indirectFlowGraph);
		
		for(SourceNode src : srcs){
			Set<Node> reachableNodes = walker.searchAllChildrenOf(src, new HashSet<Node>());
			
			for(Iterator it = reachableNodes.iterator(); it.hasNext();){
				Node node = (Node) it.next();
				
				if(node instanceof SinkNode){
					rebuiltIndirectFlowGraph.addEdge(src, node);
				}
			}
		}		
	}

	/*
	 *	print out all the direct flows in given app 
	 */
	void printDirectFlows(){
		BlueSealGraph directFlowGraph = builder.getDirectFlowGraph();

		int i = 1;
		for(Iterator it=directFlowGraph.getEdges().iterator(); it.hasNext();){
			Edge e = (Edge)it.next();
			
			if(!(e.getSrc() instanceof SourceNode)
					|| !(e.getTarget() instanceof SinkNode)){
				continue;
			}
			InterProceduralMain.ps.println("Flow #"+((Integer)i).toString()+":");
			e.printFlow();
			i++;
		}
	}
}
