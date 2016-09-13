package edu.buffalo.cse.blueseal.BSCallgraph;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import soot.MethodOrMethodContext;
import soot.Scene;
import soot.SootClass;
import soot.SootMethod;
import soot.jimple.toolkits.callgraph.ReachableMethods;
import soot.util.Chain;
import soot.util.queue.QueueReader;

public class ApplicationInfo {
	public Set<SootClass> applicationClasses = new HashSet<SootClass>();
	public Set<SootClass> handlerSootClasses = new HashSet<SootClass>();
	public Set<SootClass> messengerSootClasses = new HashSet<SootClass>();
	public Set<SootClass> intentSootClasses = new HashSet<SootClass>();
	public Set<SootClass> servConnClasses = new HashSet<SootClass>();
	public Set<SootClass> serviceClasses = new HashSet<SootClass>();
	
	public Set<SootMethod> reachableMethods = new HashSet<SootMethod>();
	
	public ApplicationInfo(){
		init();
	}
	
	public Set<SootMethod> getReachableMethods(){
		return this.reachableMethods;
	}
	
	public void setReachableMethods(Set<SootMethod> methods){
		this.reachableMethods = methods;
	}
	public void init(){
		//retrieve all application classes
		retrieveApplicationClasses();
		
		//calculate all reachable methods
		calculateRechableMethods();
		
		//calculate all special classes
		calculateSpecialClasses();
	}

	private void calculateSpecialClasses() {
		Chain<SootClass> classes = Scene.v().getClasses();
		for(SootClass sc : classes){
			List<SootClass> superClasses = getSuperTypes(sc);
			superClasses.add(sc);

			for(SootClass sootClass : superClasses){
				//find all handler classes
				if(sootClass.getName().equals("android.os.Handler")){
					handlerSootClasses.add(sc);
				}
			
				if(sootClass.getName().equals("android.os.Messenger")){
					messengerSootClasses.add(sc);
				}
			
				if(sootClass.getName().equals("android.content.Intent")){
					intentSootClasses.add(sc);
				}
			
				if(sootClass.getName().equals("android.content.ServiceConnection")){
					servConnClasses.add(sc);
				}
			
				if(sootClass.getName().equals("android.app.Service")){
					serviceClasses.add(sc);
				}
			}
		}
	}

	private void calculateRechableMethods() {
    ReachableMethods rm = Scene.v().getReachableMethods();
    QueueReader<MethodOrMethodContext> rmIt = rm.listener();
    
    //recalculate reachable methods to make it complete
    while (rmIt.hasNext()) {
        SootMethod method = rmIt.next().method();

        if (!method.hasActiveBody()) {
            continue;
        }
        
        if(!applicationClasses.contains(method.getDeclaringClass())){
        	continue;
        }
        reachableMethods.add(method);
    }
	}

	private void retrieveApplicationClasses() {
    Chain<SootClass> appClasses = Scene.v().getApplicationClasses();
    
    for(Iterator it = appClasses.iterator();it.hasNext();){
    	SootClass newClass = (SootClass) it.next();
    	applicationClasses.add(newClass);
    }	
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
}
