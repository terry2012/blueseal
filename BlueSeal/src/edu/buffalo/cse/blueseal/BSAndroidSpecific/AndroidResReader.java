/*
 * this class is to extract Android resources
 * example:
 * Strings : map the unique id to concrete string values, which can be queried later
 */
package edu.buffalo.cse.blueseal.BSAndroidSpecific;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class AndroidResReader {
	public static Map<String, String> stringsIDToValue = 
			new HashMap<String, String>();
	
	/*
	 * to extract string values, we need two files
	 * @para: public.xml and strings.xml
	 */
	public void extractStringResources(File pubXml, File strings){
		Map<String, String> varToID = new HashMap<String, String>();
		BufferedReader reader;
		try{
			//read public.xml first
			reader = new BufferedReader(new FileReader(pubXml));
			String line = null;
			
			while((line = reader.readLine()) != null){
				String fields[] = line.split("\"");
				if(fields.length > 5){
					if(fields[1].contains("string")){
						varToID.put(fields[3], fields[5]);
					}
				}
			}
			reader.close();
			
			//read strings.xml
			reader = new BufferedReader(new FileReader(strings));
			line = null;
			while((line = reader.readLine()) != null){
				String fields[] = line.split("\"");
				if(fields.length > 2){
					if(fields[0].contains("string")){
						String var = fields[1];
						String value = fields[2].replace("</string>", "");
						value = value.substring(1);
						int id = Integer.parseInt(varToID.get(var).substring(2), 16);
						stringsIDToValue.put(Integer.toString(id), value);
					}
				}
			}
			reader.close();
		}catch(FileNotFoundException e){
			e.printStackTrace();
		}catch(IOException e){
			e.printStackTrace();
		}
		
	}


}
