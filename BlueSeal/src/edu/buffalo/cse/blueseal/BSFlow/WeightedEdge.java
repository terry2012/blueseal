package edu.buffalo.cse.blueseal.BSFlow;

/*
*This is An extension of Soots edge for call graph
*To be used for a weighted directed graph
*/
import soot.Context;
import soot.Kind;
import soot.MethodOrMethodContext;
import soot.SootMethod;
import soot.Unit;
import soot.jimple.InvokeExpr;
import soot.jimple.Stmt;
import soot.jimple.toolkits.callgraph.Edge;

public class WeightedEdge
{
	private int weight;
	
	protected Edge e;
	
	public WeightedEdge(Edge e, int weight)
	{
		this.e = e;
		
		this.weight = weight;
	}
	
	public int getWeight()
	{
		return weight;
	}
	
	public void setWeight(int w)
	{
		weight = w;
	}
	
	public Edge getEdge()
	{
		return e;
	}
	
	public void setEdge(Edge ed)
	{
		e = ed;
	}
	
	public SootMethod src()
	{
		SootMethod m = e.src();
		
		return m;
	}
	
	public Context srcCtxt()
	{
		Context c = e.srcCtxt();
		
		return c;
	}
	
	public MethodOrMethodContext getSrc()
	{
		MethodOrMethodContext m = e.getSrc();
		
		return m;
	}
	
	public Unit srcUnit()
	{
		Unit u = e.srcUnit();
		
		return u;
	}
	
	public Stmt srcStmt()
	{
		Stmt s = e.srcStmt();
		
		return s;
	}
	
	public SootMethod tgt()
	{
		SootMethod m = e.tgt();
		
		return m;
	}
	
	public Context tgtCtxt()
	{
		Context c = e.tgtCtxt();
		
		return c;
	}
	
	public MethodOrMethodContext getTgt()
	{
		MethodOrMethodContext m = e.getTgt();
		
		return m;
	}
	
	public Kind kind()
	{
		Kind k = e.kind();
		
		return k;
	}

	/*public static Kind wIeToKind(InvokeExpr ie)
	{
		Kind k = ieToKind(ie);
		
		return k;
		
	}*/
	
	public boolean isExplicit()
	{
		boolean explicit = e.isExplicit();
		
		return explicit;
	}
	
	public boolean isInstance()
	{
		boolean ins = e.isInstance();
		
		return ins;
	}
	
	public boolean isVirtual()
	{
		boolean virt = e.isVirtual();
		
		return virt;
	}
	
	public boolean isSpecial()
	{
		boolean spec = e.isSpecial();
		
		return spec;
	}
	
	public boolean isClinit()
	{
		boolean cl = e.isClinit();
		
		return cl;
	}
	
	public boolean isStatic()
	{
		boolean s = e.isStatic();
		
		return s;
	}
	
	public boolean isThreadRunCall()
	{
		boolean t = e.isThreadRunCall();
		
		return t;
	}
	
	public boolean passesParameters()
	{
		boolean p = e.passesParameters();
		
		return p;
	}
	
	public int hashCode()
	{
		int h = e.hashCode();
		
		return h;
	}
	
	public boolean equals(Object other)
	{
		boolean equals = e.equals(other);
		
		return equals;
	}
	
	public String toString()
	{
		String s = e.toString();
		
		return s;
	}
	
}