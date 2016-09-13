package edu.buffalo.cse.blueseal.BSFlow;

import edu.buffalo.cse.blueseal.EntryPointCreator.AndroidEntryPointCreator;
import soot.SootMethod;
import soot.jimple.toolkits.callgraph.EdgePredicate;
import soot.jimple.toolkits.callgraph.Filter;

public class SootMethodFilter extends Filter {

	public SootMethodFilter(EdgePredicate pred) {
		super(pred);
	}
	
	public static boolean want(SootMethod m){
		if(m.getName().equals(AndroidEntryPointCreator.DUMMY_MAIN_METHOD_NAME)
				&& m.getDeclaringClass().getName().equals(AndroidEntryPointCreator.DUMMY_MAIN_CLASS_NAME))
			return false;
		return m.getDeclaringClass().isApplicationClass()&&
				m.hasActiveBody();
	}

}
