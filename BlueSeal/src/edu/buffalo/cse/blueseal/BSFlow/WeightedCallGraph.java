package edu.buffalo.cse.blueseal.BSFlow;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

import soot.MethodOrMethodContext;
import soot.SootClass;
import soot.SootMethod;
import soot.Unit;
import soot.jimple.toolkits.callgraph.CallGraph;
import soot.jimple.toolkits.callgraph.Edge;

/*
*This is the extension
*For the weighted call graph
*Does not use regular expressions
*but should work.
*If it has android in the package name
*then it has weight two.
*if not weight one.
*/

public class WeightedCallGraph //extends CallGraph
{
	protected CallGraph cg;
	
	protected Set<WeightedEdge> wSet = new HashSet<WeightedEdge>();
	
	protected String[] regex = {"android", "dalvik"};
	
	//protected QueueReader<WeightedEdge> wQ = new QueueReader<WeightedEdge>();
	
	public WeightedCallGraph(CallGraph cg)
	{
		this.cg = cg;//built off already existing callgraph
		
		Iterator<MethodOrMethodContext> mIter = cg.sourceMethods();
		
		while(mIter.hasNext())
		{
			MethodOrMethodContext m = mIter.next();
			
			Iterator<Edge> eIter = cg.edgesOutOf(m);
			
			while(eIter.hasNext())
			{
				Edge e = eIter.next();
				if(!(cg.addEdge(e)))
				{
					
					SootMethod sm = e.src();
					
					SootClass c = sm.getDeclaringClass();
					
					WeightedEdge w = null;
					
					if(c.isJavaLibraryClass())
					{
						w = new WeightedEdge(e,1);//if its in the java library cant be part of framework
						
					}
					else
					{
						String p = c.getJavaPackageName();
						
						if(p.contains(regex[0]) || p.contains(regex[1]))
						{
							w = new WeightedEdge(e,2);//if it has android in package name part of framework.
						}
						else
						{
							w = new WeightedEdge(e,1);//not part of android framework.
						}
						
						
					}
					
					//cg.removeEdge(e);
					wSet.add(w);//adds all edges in the callgraph to the weightedset
				}
			}
			
		}
		
		
	}
	
	public void setCallGraph(CallGraph cgraph)
	{
		cg = cgraph;
		
		Iterator<MethodOrMethodContext> mIter = cg.sourceMethods();
		
		while(mIter.hasNext())
		{
			MethodOrMethodContext m = mIter.next();
			
			Iterator<Edge> eIter = cg.edgesOutOf(m);
			
			while(eIter.hasNext())
			{
				Edge e = eIter.next();
				if(!(cg.addEdge(e)))
				{
					
					SootMethod sm = e.src();
					
					SootClass c = sm.getDeclaringClass();
					
					WeightedEdge w = null;
					
					if(c.isJavaLibraryClass())
					{
						w = new WeightedEdge(e,1);//if its in the java library cant be part of framework
						
					}
					else
					{
						String p = c.getJavaPackageName();
						
						if(p.contains(regex))
						{
							w = new WeightedEdge(e,2);//if it has android in package name part of framework.
						}
						else
						{
							w = new WeightedEdge(e,1);//not part of android framework.
						}
						
						
					}
					
					//cg.removeEdge(e);
					wSet.add(w);//adds all edges in the callgraph to the weightedset
				}
			}
			
		}
	}
	
	public void weighGraph()
	{
		Iterator<WeightedEdge> wIter = wSet.iterator();
		
		while(wIter.hasNext())
		{
			WeightedEdge w = wIter.next(); 
			
			SootMethod m = w.src();
			
			SootClass c = m.getDeclaringClass();
			
			if(c.isJavaLibraryClass())
			{
				w.setWeight(1);//if its in the java library cant be part of framework
				
			}
			else
			{
				String p = c.getJavaPackageName();
				
				if(p.contains(regex))
				{
					w.setWeight(2);//if it has android in package name part of framework.
				}
				else
				{
					w.setWeight(1);//not part of android framework.
				}
				
				
			}
		}
	}
	
	public void weighEdge(WeightedEdge w)
	{	
		SootMethod m = w.src();
		
		SootClass c = m.getDeclaringClass();
		
		if(c.isJavaLibraryClass())
		{
			w.setWeight(1);//if its in the java library cant be part of framework
			
		}
		else
		{
			String p = c.getJavaPackageName();
			
			if(p.contains(regex))
			{
				w.setWeight(2);//if it has android in package name part of framework.
			}
			else
			{
				w.setWeight(1);//not part of android framework.
			}
			
			
		}
	}
	
	public boolean addEdge(WeightedEdge w)
	{
		Edge e = w.getEdge();
		
		boolean add = cg.addEdge(e);
		
		if(add == true)
		{
			SootMethod sm = w.src();
			
			SootClass c = sm.getDeclaringClass();
			
			if(c.isJavaLibraryClass())
			{
				w = new WeightedEdge(e,1);//if its in the java library cant be part of framework
				
			}
			else
			{
				String p = c.getJavaPackageName();
				
				if(p.contains(regex))
				{
					w = new WeightedEdge(e,2);//if it has android in package name part of framework.
				}
				else
				{
					w = new WeightedEdge(e,1);//not part of android framework.
				}
				
				
			}
			
			//cg.removeEdge(e);
			wSet.add(w);//adds all edges in the callgraph to the weightedset
		}
		return add;
		
	}
	
	public boolean removeEdge(WeightedEdge w)
	{
		Edge e = w.getEdge();
		
		boolean remove = cg.removeEdge(e);
		
		if(remove == true)
		{
			wSet.remove(w);
		}
		return remove;
		
	}
	
	public Iterator<WeightedEdge> edgesInto(MethodOrMethodContext m)
	{
		Iterator<Edge> eIter = cg.edgesInto(m);
		
		Set<WeightedEdge> iterSet = new HashSet<WeightedEdge>();
		
		Iterator<WeightedEdge> wIter = wSet.iterator();
		
		while(eIter.hasNext())
		{
			WeightedEdge w = wIter.next();
			if(eIter.next().equals(w.getEdge()))
			{
				iterSet.add(w);
			}
		}
		
		Iterator<WeightedEdge> iter = iterSet.iterator();
		
		return iter;
	}
	
	public Iterator<WeightedEdge> edgesOutOf(MethodOrMethodContext m)
	{
		Iterator<Edge> eIter = cg.edgesOutOf(m);
		
		Set<WeightedEdge> iterSet = new HashSet<WeightedEdge>();
		
		Iterator<WeightedEdge> wIter = wSet.iterator();
		
		while(eIter.hasNext())
		{
			WeightedEdge w = wIter.next();
			if(eIter.next().equals(w.getEdge()))
			{
				iterSet.add(w);
			}
		}
		
		Iterator<WeightedEdge> iter = iterSet.iterator();
		
		return iter;
	}
	
	public Iterator<WeightedEdge> edgesOutOf(Unit u)
	{
		Iterator<Edge> eIter = cg.edgesOutOf(u);
		
		Set<WeightedEdge> iterSet = new HashSet<WeightedEdge>();
		
		Iterator<WeightedEdge> wIter = wSet.iterator();
		
		while(eIter.hasNext())
		{
			WeightedEdge w = wIter.next();
			if(eIter.next().equals(w.getEdge()))
			{
				iterSet.add(w);
			}
		}
		
		Iterator<WeightedEdge> iter = iterSet.iterator();
		
		return iter;
	}
	
	public WeightedEdge findEdge(Unit u, SootMethod callee)
	{
		Iterator<WeightedEdge> wIter = wSet.iterator();
		
		WeightedEdge actual = null;
		
		boolean found = false;
		
		while(wIter.hasNext() && found == false)
		{
			WeightedEdge w = wIter.next();
			
			SootMethod m = w.src();
			
			Unit uni = w.srcUnit();
			
			if(m.equals(callee) && uni.equals(u))
			{
				found = true;
				
				actual = w;
			}
		}
		
		return actual;
		
	}
	
	
	
}