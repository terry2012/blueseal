package edu.buffalo.cse.blueseal.utils;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import soot.Scene;
import soot.SootClass;
import soot.SootMethod;
import soot.util.Chain;

public class StaticEntryPointsManager {
	
	public Set<SootMethod> StaticEntryPoints = new HashSet<SootMethod>();
	private String apkLoc = null;
	
	public StaticEntryPointsManager(String apk){
		apkLoc = apk;
		load();
	}
	
	/*
	 * find all the static entry points
	 */
	private void load() {
		Chain<SootClass> classes = Scene.v().getApplicationClasses();
    Map<String, Set<String>> epMap = EntryPointsManager.androidEPLoader.getEPMap();
    
    for (SootClass sc : classes) {
        List<SootClass> superTypes = retrieveSuperTypesOf(sc);
        StaticEntryPoints.addAll(getEntryMethods(sc, superTypes, epMap));
    }
	}

	/*
	 * get all the entry points of given class
	 * @param baseClass: the application class checked
	 * @param superTypes: all the pararent classes of the given application class
	 * @param epMap: Android entry points set mapping
	 * @return: list of entry methods defined in given application class
	 */
	private List<SootMethod> getEntryMethods(SootClass baseClass,
			List<SootClass> superTypes, Map<String, Set<String>> epMap) {
    List<SootMethod> entryMethods = new ArrayList<SootMethod>();
    
    for (SootClass c : superTypes) {
        // find which classes are in ep map
        String className = c.getName().replace('$', '.');
        if(className.contains("android.support")) continue;

        if (epMap.containsKey(className)) {
            Set<String> methods = epMap.get(className);

            for (String method : methods) {
                String signature = "<" + baseClass + method + ">";
                try {
                    entryMethods.add(Scene.v().getMethod(signature));
                } catch (Exception e) {
                }
            }
        }
    }
    
    return entryMethods;
	}

	/*
	 * resolve all the super types of given class
	 * @param: given class
	 * @return: list of parent Soot Classes of given class
	 */
	private List<SootClass> retrieveSuperTypesOf(SootClass sc) {
    List<SootClass> superTypes = new ArrayList<SootClass>();
    
    while (sc.hasSuperclass()) {
        superTypes.add(sc);
        superTypes.addAll(sc.getInterfaces());
        sc = sc.getSuperclass();
    }
    
    return superTypes;
	}

	/*
	 * get the method list of static entry points of given app
	 * @return: list of Soot methods
	 */
	public Set<SootMethod> getStaticEntryPoints(){
		return StaticEntryPoints;
	}

}
