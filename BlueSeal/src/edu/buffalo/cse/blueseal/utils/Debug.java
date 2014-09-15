package edu.buffalo.cse.blueseal.utils;

import java.io.PrintStream;

public class Debug {
	public static boolean verbose = true;
	public final static PrintStream ps = System.out;

	public static void println(Object o){
		if(verbose)
			ps.println(o);
	}
}
