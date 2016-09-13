/*
 * this class is used to print all the information flows
 * 
 * no additional operation needed
 */
package edu.buffalo.cse.blueseal.BSInfoFlowAnalysis;

import edu.buffalo.cse.blueseal.BSG.BlueSealGraph;

public class BSInfoFlowPrinter {

	private BlueSealGraph flowGraph = null;
	
	
	public BSInfoFlowPrinter(BlueSealGraph bsg) {
		flowGraph = bsg;
	}
	
	public void print(){
		System.out.println(" ----------printing BlueSeal results---------------------");
		flowGraph.printFlow();
		System.out.println(" ----------printing BlueSeal results done----------------");
	}
}
