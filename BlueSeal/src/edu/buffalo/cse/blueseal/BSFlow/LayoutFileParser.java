package edu.buffalo.cse.blueseal.BSFlow;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import edu.buffalo.cse.blueseal.blueseal.Constants;

public class LayoutFileParser {
	private File[] layoutFiles;
	private File[] valuesFile;
	private Map<String, String> idToFile = new HashMap<String, String>();
	private Map<String, String> fileToID = new HashMap<String, String>();
	private Map<String, Set<String>> functionsFromXmlFile = new HashMap<String, Set<String>>();
	private Map<String, List<String>> fileToEmbededFiles = new HashMap<String, List<String>>();
	private List<String> layoutFilesList = new LinkedList<String>();
	private String apkLoc;

	public LayoutFileParser(String al){
		apkLoc = al;
		parseLayoutXmls();
		createLayoutIdAndFileMap();
		getFunctionsFromLayout();
		getEmbededLayoutsForFile();
		try{
			Runtime.getRuntime().exec("rm -rf ./LayoutOutput ");
		}catch(IOException e){
			e.printStackTrace();
		}
	}

	public List<String> getLayoutFilesNameList(){
		return this.layoutFilesList;
	}

	public Map<String, List<String>> getFileToEmbededFiles(){
		return this.fileToEmbededFiles;
	}

	public Map<String, String> getIdToFile(){
		return idToFile;
	}

	public Map<String, Set<String>> getFunctionsFromXmlFile(){
		return functionsFromXmlFile;
	}

	public void parseLayoutXmls(){
		try{
			Runtime.getRuntime().exec("rm -rf ./LayoutOutput ");
			
			Process p = Runtime.getRuntime().exec(
					Constants.apktool + " d " + apkLoc + " ./LayoutOutput ");
			
			int exitValue = p.waitFor();
			if(exitValue == 0){
				File layoutFolder = new File("./LayoutOutput/res/layout/");
				layoutFiles = layoutFolder.listFiles();
				File valuesFolder = new File("./LayoutOutput/res/values/");
				valuesFile = valuesFolder.listFiles();
			}
		}catch(IOException e){
			e.printStackTrace();
		}catch(InterruptedException e){
			e.printStackTrace();
		}
	}

	public void createLayoutIdAndFileMap(){

		if(valuesFile == null) return;
		
		// just looping through files in values directory until we get
		// public.xml file
		int i;
		for(i = 0; i < valuesFile.length; i++){
			if(valuesFile[i].toString().contains("public")) break;
		}

		BufferedReader reader;
		try{
			reader = new BufferedReader(new FileReader(valuesFile[i].toString()));
			String line = null;
			
			while((line = reader.readLine()) != null){
				String fields[] = line.split("\"");
				if(fields.length > 5){
					if(fields[1].contains("layout")){
						idToFile.put(fields[5], fields[3]);
						fileToID.put(fields[3], fields[5]);
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

	// public void updateEntryPoints() {
	// /*
	// * now we have two map LayoutIdToClassname and LayoutIdToFile LayoutId
	// * -> ClassName and LayoutId -> xmlFileName Iterator it =
	// * mp.entrySet().iterator(); while (it.hasNext()) { Map.Entry pairs =
	// * (Map.Entry)it.next(); System.out.println(pairs.getKey() + " = " +
	// * pairs.getValue()); it.remove(); // avoids a
	// * ConcurrentModificationException
	// */
	//
	// try {
	// Process fileCopy = Runtime.getRuntime().exec(
	// "cp input/EntryPoints.txt input/EntryPointsUpdated.txt");
	// fileCopy.waitFor();
	//
	// PrintWriter fileOut = new PrintWriter(new BufferedWriter(new FileWriter(
	// "input/EntryPointsUpdated.txt", true)));
	//
	// Iterator IdtoFileIterator = idToFile.entrySet().iterator();
	//
	// while (IdtoFileIterator.hasNext()) {/*
	// * Looping through LayoutId and
	// * files
	// */
	// Map.Entry pairs = (Map.Entry) IdtoFileIterator.next();
	// String LayoutId = (String) pairs.getKey();
	// String xmlFile = (String) pairs.getValue();
	// // GlobalData.epMap_.put(lastClass, new HashSet<String>());
	// // GlobalData.epMap_.get(lastClass).add(line);'
	// String className = GlobalData.layoutIdToClassNameMap.get(LayoutId);
	//
	// Debug.println("updateEntryPoints", "LayoutId " + LayoutId);
	// Debug.println("EntryPoint Map", "Class name in Classmap " + className
	// + "with xml file " + xmlFile);
	//
	// // GlobalData.epMap_.put(className,
	// // new HashSet<String>());
	//
	// fileOut.println(className);
	// Set<String> functions = functionsFromXmlFile.get(xmlFile);
	// Iterator<String> iter = functions.iterator();
	// while (iter.hasNext()) {
	// fileOut.println(": void " + iter.next());
	// }
	// /* adding set of function already extracted */
	// // GlobalData.epMap_.get(className).addAll(functions);
	//
	// }
	//
	// fileOut.close();
	// } catch (IOException e) {
	// // oh noes!
	// } catch (InterruptedException e) {
	// // oh noes!
	// }
	//
	// }

	public void getFunctionsFromLayout(){
		if(layoutFiles == null) return;
		
		for(int i = 0; i < layoutFiles.length; i++){
			try{
				BufferedReader reader = new BufferedReader(new FileReader(
						layoutFiles[i].toString()));
				String filePath = layoutFiles[i].toString();
				String subFields[] = filePath.split("/");
				/* ./LayoutOutput/res/layout/activity_group_messenger.xml */
				/* 4th field will have filename */
				String fullFileName[] = subFields[4].split("[/.]");
				/* 0 th filed will have the filename */
				String filename = fullFileName[0];
				layoutFilesList.add(filename);
				
				String line = null;
				while((line = reader.readLine()) != null){
					if(!line.contains("android:onClick"))
						continue;

					String fields[] = line.split("\"");
					int index = 0;
					for(index = 0; index < fields.length; index++){
						if(fields[index].contains("android:onClick")){
							break;
						}
					}
					if(index >= fields.length - 1) continue;
					
					// this means the next field contains the onclick callback methods
					// the following if makes sure there is a method defined
					String function = fields[index + 1] + "(android.view.View)";
					if(!functionsFromXmlFile.containsKey(filename))
						functionsFromXmlFile.put(filename, new HashSet<String>());
					functionsFromXmlFile.get(filename).add(function);
				}

				reader.close();
			}catch(FileNotFoundException e){
				e.printStackTrace();
			}catch(IOException e){
				e.printStackTrace();
			}
		}
	}

	/*
	 * since layout can contain embeded layouts the following function will return
	 * a list of embeded layout file names for given layout file name
	 * 
	 * Assumption: all the layout files are placed inside layout folder there are
	 * no two different layout files with the same name, correct?
	 */
	public void getEmbededLayoutsForFile(){
		if(layoutFiles == null) return;
		
		for(int i = 0; i < layoutFiles.length; i++){
			List<String> embededList = new LinkedList<String>();
			// parse layout file
			try{
				String filePath = layoutFiles[i].toString();
				String subFields[] = filePath.split("/");
				/* ./LayoutOutput/res/layout/activity_group_messenger.xml */
				/* 4th field will have filename */
				String fullFileName[] = subFields[4].split("[/.]");
				/* 0 th filed will have the filename */
				String filename = fullFileName[0];
				
				BufferedReader reader = new BufferedReader(new FileReader(
						layoutFiles[i]));

				String line = null;
				while((line = reader.readLine()) != null){
					// the embeded layout will use <include layout=""/> structure
					if(line.contains("include") && line.contains("@layout/")){
						String[] fields = line.split("\"");
						int index = 0;
						for(index = 0; index < fields.length; index++){
							if(fields[index].contains("@layout/")) break;
						}

						// this means we find the embeded layout, retrieve the filename
						int nameIndex = fields[index].indexOf("@layout/") + 8;
						String layoutFileName = fields[index].substring(nameIndex);
						embededList.add(layoutFileName);
					}
				}

				fileToEmbededFiles.put(filename, embededList);
			}catch(FileNotFoundException e){
				// TODO Auto-generated catch block
				e.printStackTrace();
			}catch(IOException e){
				// TODO Auto-generated catch block
				e.printStackTrace();
			}//end of try clause
		}//end of for loop
	}//end of method
}
