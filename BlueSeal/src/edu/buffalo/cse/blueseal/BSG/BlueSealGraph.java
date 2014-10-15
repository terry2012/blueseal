package edu.buffalo.cse.blueseal.BSG;

import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import edu.buffalo.cse.blueseal.BSFlow.InterProceduralMain;

import soot.jimple.Stmt;

public class BlueSealGraph {
	Set<Edge> edges = new HashSet<Edge>();
	Set<ArgNode> argNodes = new HashSet<ArgNode>();
	Set<RetNode> retNodes = new HashSet<RetNode>();
	Set<SourceNode> srcNodes = new HashSet<SourceNode>();
	Set<SinkNode> sinkNodes = new HashSet<SinkNode>();
	Set<CVNode> cvNodes = new HashSet<CVNode>();
	Set<TransitiveNode> trans = new HashSet<TransitiveNode>();
	
	public BlueSealGraph(){}
	
	public BlueSealGraph(BlueSealGraph g){
		if(!this.edges.isEmpty()) this.edges.removeAll(edges);
		if(!this.argNodes.isEmpty()) this.argNodes.removeAll(argNodes);
		if(!this.retNodes.isEmpty()) this.retNodes.removeAll(retNodes);
		if(!this.srcNodes.isEmpty()) this.srcNodes.removeAll(srcNodes);
		if(!this.sinkNodes.isEmpty()) this.sinkNodes.removeAll(sinkNodes);
		if(!this.cvNodes.isEmpty()) this.cvNodes.removeAll(cvNodes);
		this.edges.addAll(g.getEdges());
		this.argNodes.addAll(g.getArgNodes());
		this.retNodes.addAll(g.getRetNodes());
		this.srcNodes.addAll(g.getSrcs());
		this.sinkNodes.addAll(g.getSinks());
		this.cvNodes.addAll(g.getCVNodes());
		
	}
	
	public Set<CVNode> getCVNodes() {
		return this.cvNodes;
	}

	public boolean isEmpty(){
		return this.edges.isEmpty()
				&&this.argNodes.isEmpty()
				&&this.retNodes.isEmpty()
				&&this.srcNodes.isEmpty()
				&&this.sinkNodes.isEmpty()
				&&this.cvNodes.isEmpty();
	}
	
	public boolean addCVNode(CVNode cv) {
		return this.cvNodes.add(cv);
	}
	public boolean addArgNode(ArgNode node){
		return this.argNodes.add(node);
	}
	
	public boolean addRetNode(RetNode node){
		return this.retNodes.add(node);
	}
	
	public boolean addEdge(Node src, Node tgt){
		return this.addEdge(new Edge(src, tgt));
	}
	
	public boolean addSrc(SourceNode src){
		return this.srcNodes.add(src);
	}
	
	public boolean addSink(SinkNode sink){
		return this.sinkNodes.add(sink);
	}
	
	public Set getEdges(){
		return this.edges;
	}
	
	public void setEdges(Set e){
		if(!this.edges.isEmpty()) this.edges.removeAll(this.edges);
		this.edges.addAll(e);
	}
	
	public Set getArgNodes(){
		return this.argNodes;
	}
	
	public void setArgNodes(Set arg){
		if(!this.argNodes.isEmpty()) 
			this.argNodes.removeAll(this.argNodes);
		this.argNodes.addAll(arg);
	}
	
	public Set getRetNodes(){
		return this.retNodes;
	}
	
	public void setRetNodes(Set r){
		if(!this.retNodes.isEmpty())
			this.retNodes.addAll(retNodes);
		this.retNodes.addAll(r);
	}
	
	public Set<SourceNode> getSrcs(){
		return this.srcNodes;
	}
	
	public void setSrc(Set s){
		if(!this.srcNodes.isEmpty())
			this.srcNodes.removeAll(this.srcNodes);
		this.srcNodes.addAll(s);
	}
	
	public Set<SinkNode> getSinks(){
		return this.sinkNodes;
	}
	
	public void setSinks(Set sink){
		if(!this.sinkNodes.isEmpty())
			this.sinkNodes.removeAll(sinkNodes);
		this.sinkNodes.addAll(sink);
	}

	public boolean addEdge(Edge edge) {
		if(edge.getSrc().equals(edge.getTarget()))
			return true;
		return this.edges.add(edge)
				&& addNode(edge.getSrc())
				&& addNode(edge.getTarget());
	}

	public void union(BlueSealGraph in) {
		this.edges.addAll(in.getEdges());
		this.argNodes.addAll(in.getArgNodes());
		this.retNodes.addAll(in.getRetNodes());
		this.sinkNodes.addAll(in.getSinks());
		this.srcNodes.addAll(in.getSrcs());
		this.cvNodes.addAll(in.getCVNodes());
		this.trans.addAll(in.getTansNodes());
	}
	
	private Set<TransitiveNode> getTansNodes() {
		return this.trans;
	}

	/*
	 *  test if two given graph are the same
	 */
	public boolean equals(Object o){
		
		if(!(o instanceof BlueSealGraph)) return false;
		
		BlueSealGraph g = (BlueSealGraph)o;
		
		return edges.equals(g.getEdges())
				&&argNodes.equals(g.getArgNodes())
				&&retNodes.equals(g.getRetNodes())
				&&srcNodes.equals(g.getSrcs())
				&&sinkNodes.equals(g.getSinks())
				&&cvNodes.equals(g.getCVNodes());
	}

	public Set<Edge> getArgToRet() {
		Set<Edge> set = new HashSet<Edge>();
		
		for(Edge e:edges){
			if(e.source instanceof ArgNode &&
					e.target instanceof RetNode){
				set.add(e);
			}
		}

		return set;
	}

	public boolean containSrcToRet() {
		for(Edge e : edges){
			if((e.source instanceof SourceNode ||
					e.source instanceof RetNode)&&
					e.target instanceof RetNode){
				return true;
			}
		}
		
		return false;
	}

	public Set<Edge> getArgToSink() {
		Set<Edge> es = new HashSet<Edge>();
		
		for(Edge e : edges){
			if(e.source instanceof ArgNode &&
					e.target instanceof SinkNode){
				es.add(e);
			}
		}
		
		return es;
	}

	public Node getNodeForUnit(Stmt stmt) {
		for(Node node: argNodes){
			if(node.getStmt().equals(stmt)){
				return node;
			}
		}
		
		for(Node node: retNodes){
			if(node.getStmt().equals(stmt)){
				return node;
			}
		}
		
		for(Node node: srcNodes){
			if(node.getStmt().equals(stmt)){
				return node;
			}
		}
		
		for(Node node: sinkNodes){
			if(node.getStmt().equals(stmt)){
				return node;
			}
		}
		return null;
	}
	public void printFlow(){
		int i = 1;
		for(Edge e:this.edges){
			if(!(e.getSrc() instanceof SourceNode)
					|| !(e.getTarget() instanceof SinkNode)){
				continue;
			}
			InterProceduralMain.ps.println("Flow #"+((Integer)i).toString()+":");
			e.printFlow();
			i++;
		}
	}
	public void print(){
		int i = 1;
		for(Edge e:this.edges){
			InterProceduralMain.ps.println("Flow #"+((Integer)i).toString()+":");
			e.print();
			i++;
		}
/*		
		for(SourceNode node:srcNodes){
			node.print();
		}
		
		for(SinkNode node:sinkNodes){
			node.print();
		}
		
		for(ArgNode node:argNodes){
			node.print();
		}
		
		for(RetNode node:retNodes){
			node.print();
		}
		
		for(CVNode node:cvNodes){
			node.print();
		}*/
	}

	/*
	 * for backward flow analysis
	 */
	public Set<Edge> getRetToArg() {
		Set<Edge> set = new HashSet<Edge>();
		
		for(Edge e:edges){
			if(e.source instanceof RetNode &&
					e.target instanceof ArgNode){
				set.add(e);
			}
		}

		return set;
	}

	public boolean containRetToSink() {
		for(Edge e : edges){
			if(e.source instanceof RetNode 
					&& e.target instanceof SinkNode){
				return true;
			}
		}
		
		return false;
	}

	public Set<Edge> getSrcToArg() {
		Set<Edge> set = new HashSet<Edge>();
		
		for(Edge e:edges){
			if(e.source instanceof SourceNode &&
					e.target instanceof ArgNode){
				set.add(e);
			}
		}
		return set;
	}

	public Set<Edge> getSrcToRet() {
		Set<Edge> set = new HashSet<Edge>();
		
		for(Edge e:edges){
			if(e.source instanceof SourceNode
					&& e.target instanceof RetNode){
				set.add(e);
			}
		}
		return set;
	}

	public Set<Edge> getSrcToSink() {
		Set<Edge> set = new HashSet<Edge>();
		
		for(Edge e:edges){
			if(e.source instanceof SourceNode
					&& e.target instanceof SinkNode){
				set.add(e);
			}
		}
		return set;
	}

	public Set<Edge> getRetToSink() {
		Set<Edge> set = new HashSet<Edge>();
		
		for(Edge e:edges){
			if(e.source instanceof RetNode
					&& e.target instanceof SinkNode){
				set.add(e);
			}
		}
		return set;
	}

	//ForwardFlowA: return edges that from CV to Ret
	public HashSet<Edge> getCVToRet() {
		HashSet<Edge> set = new HashSet<Edge>();
		
		for(Edge e : this.edges){
			if(e.getSrc() instanceof CVNode
				&& e.getTarget() instanceof RetNode){
				set.add(e);
			}
		}
		return set;
	}
	//ForwardFlowA: return set of edges, from argument to CV
	public HashSet<Edge> getArgToCV() {
		HashSet<Edge> set = new HashSet<Edge>();
		
		for(Edge e : this.edges){
			if(e.getSrc() instanceof ArgNode
				&& e.getTarget() instanceof CVNode){
				set.add(e);
			}
		}
		return set;
	}

	//ForwardFlowA: return set of edges, from CV To Sink
	public HashSet<Edge> getCVToSink() {
		HashSet<Edge> set = new HashSet<Edge>();
		
		for(Edge e : this.edges){
			if(e.getSrc() instanceof CVNode
					&& e.getTarget() instanceof SinkNode){
				set.add(e);
			}
		}
		return set;
	}
	//ForwardFlowA: return set of edges, from Src to CV
	public HashSet<Edge> getSrcToCV() {
		HashSet<Edge> set = new HashSet<Edge>();
		
		for(Edge e : this.edges){
			if(e.getSrc() instanceof SourceNode
					&& e.getTarget() instanceof CVNode){
				set.add(e);
			}
		}
		return set;
	}

	//BackwardFLowA: set of edges from Ret to CV
	public HashSet<Edge> getRetToCV() {
		HashSet<Edge> set = new HashSet<Edge>();
		
		for(Edge e : this.edges){
			if(e.getSrc() instanceof RetNode
					&& e.getTarget() instanceof CVNode){
				set.add(e);
			}
		}
		return set;
	}
	//BackwardFLowA: set of edges from CV to Arg
	public HashSet<Edge> getCVToArg() {
		HashSet<Edge> set = new HashSet<Edge>();
		
		for(Edge e : this.edges){
			if(e.getSrc() instanceof CVNode
					&& e.getTarget() instanceof ArgNode){
				set.add(e);
			}
		}
		return set;
	}
	
	public HashSet<Edge> getCVToCV(){
		HashSet<Edge> set = new HashSet<Edge>();
		
		for(Edge e : this.edges){
			if(e.getSrc() instanceof CVNode
					&& e.getTarget() instanceof CVNode){
				set.add(e);
			}
		}
		return set;
	}

	public void setCVNodes(Set cvNodes2) {
		if(!this.cvNodes.isEmpty()) this.cvNodes.removeAll(this.cvNodes);
		this.cvNodes.addAll(cvNodes2);
	}

	public void addTransNode(TransitiveNode tn) {
		trans.add(tn);
	}

	public Set getEdgesOutOf(Node orig) {
		// find all the edges out of the curren node
		Set<Edge> out = new HashSet<Edge>();
		for(Iterator it=edges.iterator();it.hasNext();){
			Edge edge = (Edge)it.next();
			if(edge.getSrc().equals(orig)){
				out.add(edge);
			}
		}
		return out;
	}
	
	public Set getEdgesInto(Node orig) {
		// find all the edges out of the curren node
		Set<Edge> out = new HashSet<Edge>();
		for(Iterator it=edges.iterator();it.hasNext();){
			Edge edge = (Edge)it.next();
			if(edge.getTarget().equals(orig)){
				out.add(edge);
			}
		}
		return out;
	}

	public void clear() {
		this.edges.clear();
		this.argNodes.clear();
		this.retNodes.clear();
		this.srcNodes.clear();
		this.sinkNodes.clear();
		this.cvNodes.clear();
		this.trans.clear();
		
	}
	
	/*
	 * this is used to rebuild graph
	 * resolve CVToCV edge
	 */
	public void rebuildGraph(){
		for(Iterator it=edges.iterator();it.hasNext();){
			Edge edge = (Edge)it.next();
			if(edge.getTarget() instanceof CVNode
					&& edge.getSrc() instanceof CVNode){
				//put new edges
				Set inSet = getEdgesInto(edge.getSrc());
				for(Iterator<Edge> itIn = inSet.iterator();itIn.hasNext();){
					Edge inEdge = (Edge)itIn.next();
					this.addEdge(inEdge.getSrc(), edge.getTarget());
				}
				
				Set outSet = getEdgesOutOf(edge.getTarget());
				for(Iterator<Edge> itOut = outSet.iterator();itOut.hasNext();){
					Edge outEdge = (Edge)itOut.next();
					this.addEdge(edge.getSrc(), outEdge.getTarget());
				}
			}
		}
	}

	public void addEdges(Set<Edge> edges){
		for(Iterator it=edges.iterator();it.hasNext();){
			Edge e = (Edge) it.next();
			this.edges.add(e);
			Node source = e.getSrc();
			Node target = e.getTarget();
			addNode(source);
			addNode(target);
		}
		
	}

	private boolean addNode(Node node){
		if(node instanceof SourceNode){
			return srcNodes.add((SourceNode) node);
		}else if(node instanceof SinkNode){
			return sinkNodes.add((SinkNode) node);
		}else if(node instanceof ArgNode){
			return argNodes.add((ArgNode) node);
		}else if(node instanceof TransitiveNode){
			return trans.add((TransitiveNode) node);
		}else if(node instanceof RetNode){
			return retNodes.add((RetNode) node);
		}else if(node instanceof CVNode){
			return cvNodes.add((CVNode) node);
		}else{
			System.err.println("Unknow type of node!");
		}
		
		return false;
	}

	public Set<Node> getMyChildren(Node node){
		Set<Node> children = new HashSet<Node>();
		for(Iterator it=edges.iterator();it.hasNext();){
			Edge e = (Edge) it.next();
			if(node.equals(e.getSrc())){
				children.add(e.getTarget());
			}
		}
		return children;
	}

	public boolean contains(Node node){
		if(node instanceof SourceNode){
			return this.srcNodes.contains(node);
		}else if(node instanceof SinkNode){
			return this.sinkNodes.contains(node);
		}else if(node instanceof CVNode){
			return this.cvNodes.contains(node);
		}else if(node instanceof RetNode){
			return this.retNodes.contains(node);
		}else if(node instanceof TransitiveNode){
			return this.trans.contains(node);
		}else if(node instanceof ArgNode){
			return this.argNodes.contains(node);
		}
		return false;
	}

}
