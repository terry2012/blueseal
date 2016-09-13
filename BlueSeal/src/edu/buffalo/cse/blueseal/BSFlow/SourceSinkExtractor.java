package edu.buffalo.cse.blueseal.BSFlow;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Map;

public class SourceSinkExtractor {
	
	public static Map<String, String> sources_ = new HashMap<String, String>();
	public static Map<String, String> sinks_ = new HashMap<String, String>();
	
	SourceSinkExtractor()
	{
		try {
			FileInputStream fis = new FileInputStream("sources.txt");
			InputStreamReader iReader = new InputStreamReader(fis);
			BufferedReader br = new BufferedReader(iReader);
			String line;
			String className = null;
	
			while ((line = br.readLine()) != null) {
				if (line.startsWith("#")) {
					/*This means that the line contains class name*/
					className = line.substring(1); /*we can skip the first character and copy the class name*/
				}
				if(className == null) throw new Exception();
				sources_.put(className, line);
			}
			fis.close();
			
			fis = new FileInputStream("sinks.txt");
			iReader = new InputStreamReader(fis);
			br = new BufferedReader(iReader);
			
			className = null;
			while ((line = br.readLine()) != null) {
				if (line.startsWith("#")) {
					/*This means that the line contains class name*/
					className = line.substring(1); /*we can skip the first character and copy the class name*/
				}
				if(className == null) throw new Exception();
				sinks_.put(className, line);
			}
			
		}
		catch (Exception e) {
			e.printStackTrace();
		}
	}

}

