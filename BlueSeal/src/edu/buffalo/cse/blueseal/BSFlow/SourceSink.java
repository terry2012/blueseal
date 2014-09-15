package edu.buffalo.cse.blueseal.BSFlow;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

public class SourceSink {

	/*
	 * define constant strings for categorization of sources and sinks
	 */

	// sources, based on permissions
	private static final String SMS = "SMS";
	private static final String READ_STORAGE = "READ_EXTERNAL_STORAGE";
	private static final String READ_HISTORY_BOOKMARKS = "READ_HISTORY_BOOKMARKS";
	private static final String READ_USER_DICTIONARY = "READ_USER_DICTIONARY";
	private static final String FINE_LOCATION = "FINE_LOCATION";
	private static final String READ_CONTACTS = "READ_CONTACTS";
	private static final String MANAGE_ACCOUNTS = "MANAGE_ACCOUNTS";
	private static final String PHONE_STATE = "PHONE_STATE";
	private static final String VOICEMAIL = "VOICEMAIL";
	private static final String COARSE_LOCATION = "COARSE_LOCATION";
	private static final String CALENDAR = "CALENDAR";
	private static final String AUTHENTICATE_ACCOUNTS = "AUTHENTICATE_ACCOUNTS";
	private static final String GET_ACCOUNTS = "GET_ACCOUNTS";
	private static final String CALL_LOG = "CALL_LOG";
	private static final String CONTENTPROVIDER = "contentprovider";

	// sinks, self defined categories
	private static final String WRITE_STROAGE = "STORAGE";
	public static final String NETWORK = "NETWORK";
	private static final String LOG = "LOG";
	private static final String INTENT = "INTENT";

	public static Map<String, ArrayList<String>> sources_ = new HashMap<String, ArrayList<String>>();
	public static Map<String, ArrayList<String>> sinks_ = new HashMap<String, ArrayList<String>>();
	public static Set<String> CPSrcStrings = new HashSet<String>();
	public static Map<String, ArrayList<String>> CPSrcFields = new HashMap<String, ArrayList<String>>();
	public static Map<String, String> categoryMap = new HashMap<String, String>();

	public static void extractSootSourceSink(){
		sources_ = extract("input/bluesealSources_v1.0.txt"); // base blueseal
																													// sources(exact from
																													// pscout permission
																													// mapping)
		Map<String, ArrayList<String>> extraSources = extractBSResource("input/bluesealSources_v1.0_plus.txt");
		for(Iterator it=extraSources.keySet().iterator(); it.hasNext();){
			String classname = (String) it.next();
			if(sources_.containsKey(classname)){
				sources_.get(classname).addAll(extraSources.get(classname));
			}else{
				sources_.put(classname, extraSources.get(classname));
			}
		}
		
		sinks_ = extractBSResource("input/bluesealSinks_v1.0.txt"); 
		extractCP("input/contentprovider.txt");
		extractCategoryMap();
	}

	private static void extractCP(String path){
		try{
			FileInputStream fis = new FileInputStream(path);
			InputStreamReader iReader = new InputStreamReader(fis);
			BufferedReader br = new BufferedReader(iReader);
			String line;
			String category;

			while((line = br.readLine()) != null){
				if(!(line.startsWith("<") || line.startsWith("content://")))
					continue;
				CPSrcStrings.add(line);
			}
		}catch(FileNotFoundException e){
			e.printStackTrace();
		}catch(IOException e){
			e.printStackTrace();
		}
	}

	private static Map<String, ArrayList<String>> extract(String path){
		Map<String, ArrayList<String>> map = new HashMap<String, ArrayList<String>>();
		try{
			FileInputStream fis = new FileInputStream(path);
			InputStreamReader iReader = new InputStreamReader(fis);
			BufferedReader br = new BufferedReader(iReader);
			String line;

			while((line = br.readLine()) != null){
				if(!line.startsWith("<"))
					continue;

				int colonIndex = line.indexOf(':');
				String className = line.substring(1, colonIndex);
				int endIndex = line.lastIndexOf('>');
				String remainLine = line.substring(colonIndex + 2, endIndex);
				int spaceIndex = remainLine.indexOf(' ');
				int paraIndex = remainLine.indexOf('(');
				String methodName = remainLine.substring(spaceIndex + 1, paraIndex);
				// remove "<>" from init methods
				if(methodName.startsWith("<")){
					methodName = "init";
				}

				// put the extract class&method into the Map
				if(map.containsKey(className)){
					map.get(className).add(methodName);
				}else{
					ArrayList<String> newList = new ArrayList<String>();
					newList.add(methodName);
					map.put(className, newList);
				}
			}
		}catch(FileNotFoundException e){
			// TODO Auto-generated catch block
			e.printStackTrace();
		}catch(IOException e){
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		return map;

	}

	public static Map<String, ArrayList<String>> extractBSResource(String path){
		Map<String, ArrayList<String>> map = new HashMap<String, ArrayList<String>>();

		try{
			FileInputStream fis = new FileInputStream(path);
			InputStreamReader iReader = new InputStreamReader(fis);
			BufferedReader br = new BufferedReader(iReader);
			String line;
			String className = null;

			while((line = br.readLine()) != null){
				if(line.startsWith("#")){
					continue;
				}
				if(line.startsWith("class: ")){
					/* This means that the line contains class name */
					className = line.substring(7);
					continue;
				}

				if(!map.containsKey(className)){
					ArrayList<String> tempList = new ArrayList<String>();
					tempList.add(line);
					map.put(className, tempList);
				}else{
					map.get(className).add(line);
				}
			}
		}catch(FileNotFoundException e){
			// TODO Auto-generated catch block
			e.printStackTrace();
		}catch(IOException e){
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return map;
	}

	/*
	 * 
	 */
	public static void extractCategoryMap(){
		try{
			/*
			 * sources
			 */
			FileInputStream fis = new FileInputStream(
					"input/bluesealSources_v1.0.txt");
			InputStreamReader iReader = new InputStreamReader(fis);
			BufferedReader br = new BufferedReader(iReader);
			String line;
			String category = null;
			while((line = br.readLine()) != null){
				if(line.startsWith("Permission:")){
					category = checkCategory(line);
				}
				if(!line.startsWith("<"))
					continue;

				int colonIndex = line.indexOf(':');
				String className = line.substring(1, colonIndex);
				int endIndex = line.lastIndexOf('>');
				String remainLine = line.substring(colonIndex + 2, endIndex);
				int spaceIndex = remainLine.indexOf(' ');
				int paraIndex = remainLine.indexOf('(');
				String methodName = remainLine.substring(spaceIndex + 1, paraIndex);
				// remove "<>" from init methods
				if(methodName.startsWith("<")){
					methodName = "init";
				}
				categoryMap.put(className + ":" + methodName, category);
			}

			/*
			 * sources_plus file
			 */
			FileInputStream srcplus = new FileInputStream(
					"input/bluesealSources_v1.0_plus_category.txt");
			InputStreamReader srcReader = new InputStreamReader(srcplus);
			BufferedReader buffreader = new BufferedReader(srcReader);
			String className = null;

			while((line = buffreader.readLine()) != null){
				if(line.startsWith("#")){
					continue;
				}
				if(line.startsWith("class: ")){
					/* This means that the line contains class name */
					className = line.substring(7);
					continue;
				}
				String[] split = line.split(":");
				String methodName = split[0];
				categoryMap.put(className + ":" + methodName, split[1]);
			}

			/*
			 * sinks
			 */
			FileInputStream sinkFile = new FileInputStream(
					"input/categoriedsinks.txt");
			InputStreamReader sinkReader = new InputStreamReader(sinkFile);
			BufferedReader sinkbuf = new BufferedReader(sinkReader);
			line = null;

			while((line = sinkbuf.readLine()) != null){
				String[] splits = line.split(":");
				categoryMap.put(splits[0] + ":" + splits[1], splits[2]);
			}

		}catch(FileNotFoundException e){
			// TODO Auto-generated catch block
			e.printStackTrace();
		}catch(IOException e){
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

	}

	public static String checkCategory(String string){
		String category = null;
		if(string.contains("android.permission.READ_SMS")){
			category = SMS;
		}else if(string.contains("android.permission.READ_EXTERNAL_STORAGE")){
			category = READ_STORAGE;
		}else if(string
				.contains("com.android.browser.permission.READ_HISTORY_BOOKMARKS")){
			category = READ_HISTORY_BOOKMARKS;
		}else if(string.contains("android.permission.READ_USER_DICTIONARY")){
			category = READ_USER_DICTIONARY;
		}else if(string.contains("android.permission.ACCESS_FINE_LOCATION")){
			category = FINE_LOCATION;
		}else if(string.contains("android.permission.READ_CONTACTS")){
			category = READ_CONTACTS;
		}else if(string.contains("android.permission.MANAGE_ACCOUNTS")){
			category = MANAGE_ACCOUNTS;
		}else if(string.contains("android.permission.READ_PHONE_STATE")){
			category = PHONE_STATE;
		}else if(string.contains("com.android.voicemail.permission.ADD_VOICEMAIL")){
			category = VOICEMAIL;
		}else if(string.contains("android.permission.ACCESS_COARSE_LOCATION")){
			category = COARSE_LOCATION;
		}else if(string.contains("android.permission.READ_CALENDAR")){
			category = CALENDAR;
		}else if(string.contains("android.permission.AUTHENTICATE_ACCOUNTS")){
			category = AUTHENTICATE_ACCOUNTS;
		}else if(string.contains("android.permission.GET_ACCOUNTS")){
			category = GET_ACCOUNTS;
		}else if(string.contains("android.permission.READ_CALL_LOG")){
			category = CALL_LOG;
		}else if(string.contains("contentprovider")){
			category = CONTENTPROVIDER;
		}
		return category;
	}
}
