package edu.buffalo.cse.blueseal.main;

import java.util.HashSet;
import java.util.Set;

import soot.SootClass;

public class utils {
	
	public static Set<SootClass> getAllSuperTypes(SootClass sc){
		Set<SootClass> result = new HashSet<SootClass>();
		SootClass temp = sc;
		
		while (temp.hasSuperclass()) {
      result.add(temp);
      result.addAll(temp.getInterfaces());
      temp = temp.getSuperclass();
		}

		return result;
	}

}
