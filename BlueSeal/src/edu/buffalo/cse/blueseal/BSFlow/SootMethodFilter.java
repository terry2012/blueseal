package edu.buffalo.cse.blueseal.BSFlow;

import soot.SootMethod;
import soot.jimple.toolkits.callgraph.EdgePredicate;
import soot.jimple.toolkits.callgraph.Filter;

public class SootMethodFilter extends Filter {

	public SootMethodFilter(EdgePredicate pred) {
		super(pred);
	}
	
	public boolean want(SootMethod m){
		return m.getDeclaringClass().isApplicationClass()&&
				m.hasActiveBody();
	}

}
