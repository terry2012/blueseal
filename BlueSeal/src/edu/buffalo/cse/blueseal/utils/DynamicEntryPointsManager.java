package edu.buffalo.cse.blueseal.utils;

import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import soot.Body;
import soot.IntType;
import soot.Local;
import soot.MethodOrMethodContext;
import soot.Scene;
import soot.SootMethod;
import soot.SootMethodRef;
import soot.Unit;
import soot.Value;
import soot.jimple.StaticFieldRef;
import soot.jimple.Stmt;
import soot.jimple.toolkits.callgraph.CHATransformer;
import soot.jimple.toolkits.callgraph.ReachableMethods;
import soot.toolkits.graph.ExceptionalUnitGraph;
import soot.toolkits.scalar.SimpleLiveLocals;
import soot.toolkits.scalar.SmartLocalDefs;
import soot.util.queue.QueueReader;
import edu.buffalo.cse.blueseal.BSFlow.LayoutFileParser;

public class DynamicEntryPointsManager {
	public Set<SootMethod> DynamicEntryPoints	= new HashSet<SootMethod>();
	public LayoutFileParser layoutParser;
	public List<SootMethod> initEntryPoints;
	private String apkLoc = null;
	
	
	public DynamicEntryPointsManager(String apk, List<SootMethod> init){
		apkLoc = apk;
		initEntryPoints = init;
		layoutParser = new LayoutFileParser(apkLoc);
		parseLayoutForDynamicEntryPoints();
	}
	
	
	/*
	 * parse the Android layout files, find user defined entry points in layouts
	*/
	private void parseLayoutForDynamicEntryPoints() {
    Map<String, String> idToFile = layoutParser.getIdToFile();
    Map<String, Set<String>> functionsFromXmlFile = layoutParser.getFunctionsFromXmlFile();

    Scene.v().setEntryPoints(initEntryPoints);
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
            
            if (!methodRef.name().contains("setContentView")||
            		stmt.getInvokeExpr().getArgCount() <= 0)
            	continue;
            
            Value param = stmt.getInvokeExpr().getArg(0);
            
            if (!(param.getType() instanceof IntType))
                continue;

            String fileName = null;
            
            try { 
                int layoutIdInt = Integer.parseInt(stmt.getInvokeExpr().getArg(0).toString());
                fileName = idToFile.get("0x" + Integer.toHexString(layoutIdInt));
            } catch (NumberFormatException e) {
                // TODO: Right now we're only tracing back within the same method.
                ExceptionalUnitGraph eug = new ExceptionalUnitGraph(body);
                SmartLocalDefs localDefs = new SmartLocalDefs(eug, new SimpleLiveLocals(eug));
                List<Unit> defs = localDefs.getDefsOfAt((Local) stmt.getUseBoxes().get(1).getValue(), stmt);   
                Stmt defStmt = (Stmt) defs.get(defs.size() - 1);
                Value rV = defStmt.getUseBoxes().get(0).getValue();
                
                if (rV instanceof StaticFieldRef) {
                    fileName = ((StaticFieldRef) rV).getFieldRef().name();
                } else {
                    // TODO: This requires backward flow analysis to trace back where it's coming from
                    //System.err.println("Warning: DynamicEntryPoint skips " + defStmt.toString());
                }
            }
            
            if(fileName==null ||
            		!layoutParser.getLayoutFilesNameList().contains(fileName)) 
            	continue;
            
            List<String> layouts = layoutParser.getFileToEmbededFiles().get(fileName);
            layouts.add(fileName);
            
            for(String layout : layouts){
            	
            	if (!functionsFromXmlFile.containsKey(layout))
                    // TODO: this means that we might be skipping some layout files
            		continue;
            	
            	for (Iterator<String> it = functionsFromXmlFile.get(layout).iterator();
                        it.hasNext();) {
            		String signature = "<" + methodRef.declaringClass().getName() + ": void "
            				+ it.next() + ">";

            		try {
            			DynamicEntryPoints.add(Scene.v().getMethod(signature));
            		} catch (RuntimeException e) {
            			System.err.println("Warning: DynamicEntryPoint cannot find " + signature + " (signature is perhaps wrong)");
            		}
            	}
            }
        }
    }	
	}

	/*
	 * get all the dynamic entry points of given app
	 * @return: list of dynamic entry points
	 */
	public Set<SootMethod> getDynamicEntryPoints(){
		return DynamicEntryPoints;
	}

}
