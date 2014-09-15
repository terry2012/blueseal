package edu.buffalo.cse.blueseal.BSFlow;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import edu.buffalo.cse.blueseal.blueseal.BSStmntSwitch;
import edu.buffalo.cse.blueseal.blueseal.Debug;
import edu.buffalo.cse.blueseal.blueseal.EntryPointsMapLoader;

import soot.Body;
import soot.MethodOrMethodContext;
import soot.PatchingChain;
import soot.Scene;
import soot.SceneTransformer;
import soot.SootClass;
import soot.SootMethod;
import soot.SootMethodRef;
import soot.Unit;
import soot.jimple.InvokeExpr;
import soot.jimple.InvokeStmt;
import soot.jimple.Stmt;
import soot.jimple.VirtualInvokeExpr;
import soot.jimple.toolkits.callgraph.CHATransformer;
import soot.jimple.toolkits.callgraph.CallGraph;
import soot.jimple.toolkits.callgraph.ReachableMethods;
import soot.util.Chain;
import soot.util.queue.QueueReader;

public class FlowCGTransformer extends SceneTransformer {
	public static List<SootMethod> sootEntryPoints_;
	private static String apkLoc;

	public FlowCGTransformer(String al) {
	    apkLoc = al;
	}
	@Override
	protected void internalTransform(String arg0, Map arg1) {
		removeAndroidAutoGenClasses();
        List<SootMethod> entryPoints = getEntryPoints();
        entryPoints.addAll(getDynamicEntryPoints(entryPoints));
        sootEntryPoints_ = entryPoints;
        Scene.v().setEntryPoints(entryPoints);
        CHATransformer.v().transform();	
        
        //TODO: test, remove later
/*        {
	        CallGraph cg = Scene.v().getCallGraph();
	        ReachableMethods rm = Scene.v().getReachableMethods();
	        QueueReader<MethodOrMethodContext> rmIt = rm.listener();
	
	        while (rmIt.hasNext()) {
	            SootMethod method = rmIt.next().method();
	            if (!method.hasActiveBody()) {
	                continue;
	            }
	            InterProceduralMain.ps.println("method:"+method.getName()+"\n");
	        }
        }*/
	}
	
    /*
     * remove all the Anroid auto generated classes
     */
    private void removeAndroidAutoGenClasses() {
        Set<SootClass> classesToRemove = new HashSet<SootClass>();
        for (SootClass clazz : Scene.v().getApplicationClasses()) {
            String name = clazz.getJavaStyleName();
            // BuildConfig.java
            if (name.equals("BuildConfig"))
                classesToRemove.add(clazz);
        }
        for (SootClass clazz : classesToRemove)
            Scene.v().removeClass(clazz);

    }
    
    /*
     * retrieve all the entry points in the application
     */
    private List<SootMethod> getEntryPoints() {
        // TODO Auto-generated method stub
        List<SootMethod> entryPoints = new ArrayList<SootMethod>();
        Chain<SootClass> classes = Scene.v().getApplicationClasses();
        Map<String, Set<String>> epMap = new EntryPointsMapLoader().getEPMap();
        for (SootClass sc : classes) {
            List<SootClass> superTypes = getSuperTypes(sc);
            entryPoints.addAll(getEntryMethods(sc, superTypes, epMap));
        }

        return entryPoints;

    }
    
    /*
     * get class's super classes
     */
    private List<SootClass> getSuperTypes(SootClass sc) {
        List<SootClass> superTypes = new ArrayList<SootClass>();
        while (sc.hasSuperclass()) {
            superTypes.add(sc);
            superTypes.addAll(sc.getInterfaces());
            sc = sc.getSuperclass();
        }
        return superTypes;
    }
    
    /*
     * get all the entry methods in the application
     */
    private List<SootMethod> getEntryMethods(SootClass baseClass, List<SootClass> classes,
            Map<String, Set<String>> epMap) {

        List<SootMethod> entryMethods = new ArrayList<SootMethod>();
        for (SootClass c : classes) {
            // find which classes are in ep map
            String className = c.getName().replace('$', '.');

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

    private List<SootMethod> getDynamicEntryPoints(List<SootMethod> initialEntryPoints) {
        ArrayList<SootMethod> returnList = new ArrayList<SootMethod>();
        LayoutFileParser layoutParser = new LayoutFileParser(apkLoc);
        Map<String, String> idToFile = layoutParser.getIdToFile();
        Map<String, Set<String>> functionsFromXmlFile = layoutParser.getFunctionsFromXmlFile();

        Scene.v().setEntryPoints(initialEntryPoints);
        CHATransformer.v().transform();

        ReachableMethods rm = Scene.v().getReachableMethods();
        QueueReader<MethodOrMethodContext> rmIt = rm.listener();

        while (rmIt.hasNext()) {
            SootMethod method = rmIt.next().method();
            if (!method.hasActiveBody())
                continue;

            Body body = method.getActiveBody();

            for (Iterator<Unit> unitIt = body.getUnits().iterator(); unitIt.hasNext();) {
                Stmt stmt = (Stmt) unitIt.next();

                if (!stmt.containsInvokeExpr())
                    continue;

                SootMethodRef methodRef = stmt.getInvokeExpr().getMethodRef();
                if (!methodRef.name().contains("setContentView"))
                    continue;
                Debug.println("In DynamicEntryPoint function", "Parameter of setContentView :"+ stmt.getInvokeExpr().getArg(0).toString());
                int layoutIdInt = Integer.parseInt(stmt.getInvokeExpr().getArg(0).toString());
                
                String fileName = idToFile.get("0x" + Integer.toHexString(layoutIdInt));

                if (!functionsFromXmlFile.containsKey(fileName))
                    continue;

                for (Iterator<String> it = functionsFromXmlFile.get(fileName).iterator();
                        it.hasNext();) {
                    String signature = "<" + methodRef.declaringClass().getName() + ": void "
                            + it.next() + ">";
                    returnList.add(Scene.v().getMethod(signature));
                }
            }
        }

        return returnList;
    }
}
