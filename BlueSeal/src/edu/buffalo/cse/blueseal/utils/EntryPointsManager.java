/*
 * this class is to manage all the entry points of the analysed apk
 * 1. find all possible static entry points of the given apk
 * 2. parse the layout file and get all dynamic entry points
 */

package edu.buffalo.cse.blueseal.utils;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import soot.SootClass;
import soot.SootMethod;

public class EntryPointsManager {
	
	public static List<SootMethod> entryPoints = new ArrayList<SootMethod>();
	public static AndroidEntryPointsMapLoader androidEPLoader = null;
	
	private String apkLoc = null;
	
	public EntryPointsManager(String apk){
		apkLoc = apk;

		//load the pre-computed Android entry points set into BlueSeal
		androidEPLoader = new AndroidEntryPointsMapLoader();
	}

	/*
	 * find all the entry points of analyzed app
	 * update the entry points list in two steps:
	 * 1. load static entry points
	 * 2. analyze dynamic entry points
	 */
	public void loadApkEntryPoints(){
		StaticEntryPointsManager sepm = new StaticEntryPointsManager(apkLoc);
		entryPoints.addAll(sepm.getStaticEntryPoints());
		
		DynamicEntryPointsManager depm = new DynamicEntryPointsManager(apkLoc, entryPoints);
		entryPoints.addAll(depm.getDynamicEntryPoints());
		
		//prone all entry points from "android.support" library
		Set<SootMethod> removeSet = new HashSet<SootMethod>();
		for(SootMethod method : entryPoints){
			if(method.getDeclaringClass().getName().contains("android.support.")){
				removeSet.add(method);
			}
		}
		entryPoints.removeAll(removeSet);
	}
	
	/*
	 * return entry points methods' list of analyzed app
	 */
	public List<SootMethod> getApkEntryPoints(){
		return entryPoints;
	}
	
	public Set<SootClass> getEntryPointClasses(){
		Set<SootClass> result = new HashSet<SootClass>();
		for(SootMethod method : entryPoints){
			SootClass sootClass = method.getDeclaringClass();
			result.add(sootClass);
		}
		return result;
	}
	
	public void printAppEntryMethods(){
		for(SootMethod method : entryPoints){
			System.out.println("[BlueSeal-EntryPointManager]: found entry point->"+
					method.getName());
		}
	}
	
	/*
	 * resolve all the super types of given class
	 * @param: given class
	 * @return: list of parent Soot Classes of given class
	 */
	public static List<SootClass> retrieveSuperTypesOf(SootClass sc) {
    List<SootClass> superTypes = new ArrayList<SootClass>();
    
    while (sc.hasSuperclass()) {
        superTypes.add(sc);
        superTypes.addAll(sc.getInterfaces());
        sc = sc.getSuperclass();
    }
    
    return superTypes;
	}

}
