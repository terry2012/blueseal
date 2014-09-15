package edu.buffalo.cse.blueseal.blueseal;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class GlobalData {
	private static final Exception NullPointerException = null;
	private static String apkLoc;
	public static Map<String,String> layoutIdToClassNameMap = new HashMap<String,String>();

	public static void setApkLocation(String apkLocation)
	{
		Debug.println("GlobalData", "Vlaue being passed " + apkLocation);
		apkLoc = apkLocation;
	}	
	
	public static String getApkLocation()
	{
		Debug.println("GlobalData", "inside getApkLocation");
		if( apkLoc != null) 
		{
			Debug.println("GlobalData", "Vlaue being passed in getApkLocation " + apkLoc);
			return apkLoc ;
		}
		else
		{
			try {
				throw NullPointerException;
			} catch (Exception e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			return null;
			
		}
	}
	
	public static void addLayoutClassMap(String LayoutId,String Class)
	{
		layoutIdToClassNameMap.put(LayoutId,Class);
	}
	
	public static Map<String, String> getLayoutClassMap()
	{
		return layoutIdToClassNameMap;
	}

}
