package edu.buffalo.cse.blueseal.blueseal;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Scanner;
import java.util.Set;


public class PermMapLoader
{
	public static Map<String, Set<String>> APIPermMap_;
	public static Map<String, String> intentPermMap_;
	public static Map<String, String> cpPermMap_;
	
	public PermMapLoader(){
		APIPermMap_ = new HashMap<String, Set<String>>();
		intentPermMap_ = new HashMap<String, String>();
		cpPermMap_ = new HashMap<String, String>();
		this.loadAPIPermMap("./input/APICalls.txt");
		this.loadIntentPerm("./input/IntentPermMapping.txt");
		this.loadContentProviderPerm("./input/ContentProviderPermMapping.txt");
	}

	public Map<String, String> getContentProviderPermMap(){
		return this.cpPermMap_;
	}
	public Map<String, String> getIntentPermMap(){
	    return intentPermMap_;
	}
	
	public Map<String, Set<String>> getAPIPermMap()
	{
		return APIPermMap_;
	}
	
	private void loadIntentPerm(String mapfile){
	    //load permission mapping into intentPerms  
	    try {
	      BufferedReader in = new BufferedReader(new FileReader(mapfile));
	      String line = null;
	      while((line=in.readLine())!=null){
	        String[] splits = line.split(" ");
	        intentPermMap_.put(splits[0], splits[1]);
	      }   
	    } catch (FileNotFoundException e) {
	      // TODO Auto-generated catch block
	      e.printStackTrace();
	    } catch (IOException e) {
	      // TODO Auto-generated catch block
	      e.printStackTrace();
	    }   
	}

	private void loadAPIPermMap(String fileName)
	{
		try
		{
			BufferedReader in = new BufferedReader(new FileReader(fileName));
			String line;
			while ((line = in.readLine()) != null)
			{
				line = line.trim();
				String[] lineParts = line.split("\t");
				String apiCall = lineParts[0];
				String[] perms = lineParts[1].split(" or | and ");
				Set<String> permSet = APIPermMap_.containsKey(apiCall) ? 
						APIPermMap_.get(apiCall) : new HashSet<String>();
				permSet.addAll(Arrays.asList(perms));
				APIPermMap_.put(apiCall, permSet);
			}
			in.close();
		}
		catch (IOException e) 
		{
			e.printStackTrace();
		}
	}
	
	private void loadContentProviderPerm(String mapfile){
		  
	    try {
	      Scanner fileScanner = new Scanner(new File(mapfile));
	    
	      String contentPath, permission ;

	      while (fileScanner.hasNextLine()) {
	        Scanner wordParser = new Scanner(fileScanner.nextLine());
	        contentPath = wordParser.next();
	        wordParser.next();
	        permission = wordParser.next();
	        cpPermMap_.put(contentPath, permission);
	      }   
	    }catch (Exception e) {
	      e.printStackTrace();
	    } 
	  }
}
