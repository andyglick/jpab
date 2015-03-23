/*
 * JPA Performance Benchmark - http://www.jpab.org
 * Copyright © ObjectDB Software Ltd. All Rights Reserved. 
 *
 * This is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published
 * by the Free Software Foundation; either version 2 of the License,
 * or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software Foundation,
 * Inc., 59 Temple Place, Suite 330, Boston, MA 02111-1307 USA.
 */

package org.jpab;

import java.io.*;
import java.util.*;


/**
 * Helper static methods for using benchmark.properties files. 
 */
abstract class ConfigHelper {

	//-----------//
	// Constants //
	//-----------//
	
	/** Name of benchmark properties files */
	private static final String PROPERTIES_FILE_NAME = "benchmark.properties";

	/** Global benchmark properties (from the root directory) */
	private static final Properties globalProperties =
		loadProperties(FileHelper.ROOT_DIR);

	//-----------------//
	// Properties Load //
	//-----------------//

	/**
	 * Loads benchmark properties from a specified directory.
	 * 
	 * Note: In use for loading the global benchmark.properties file
	 * from the root directory, as well as for loading specific
	 * benchmark.properties files from DBMS/JPA Provider directories. 
	 *
	 * @param dir a directory from which to load properties 
	 * @return the loaded properties.
	 */
	static Properties loadProperties(File dir) {
		Properties prop = new Properties();
		File propertiesFile = new File(dir, PROPERTIES_FILE_NAME);
		try {
			prop.load(new FileInputStream(propertiesFile));
			return prop;
		} catch (IOException e) {
			System.err.println("File " + propertiesFile + " is not found");
			return null;
		}
	}

	//-------------------//
	// Global Properties //
	//-------------------//

	/**
	 * Gets the active test codes (from the global properties).
	 *  
	 * @return all the active test codes ordered alphabetically.
	 */
	static String[] getTestCodes() {
		TreeSet<String> testCodeSet = new TreeSet<String>();
		for (String name : globalProperties.stringPropertyNames()) {
			if (name.startsWith("test")) {
				int ix = name.indexOf('-');
				if (ix >= 0) {
					name = name.substring(0, ix);
				}
				testCodeSet.add(name);
			}
		}
		return testCodeSet.toArray(new String[0]);
	}

	/**
	 * Gets a value of a global benchmark property.
	 *  
	 * @param name the property name
	 * @return the property value.
	 */
	static String getProperty(String name) {
		return globalProperties.getProperty(name);
	}

	/**
	 * Gets a value of a global benchmark int property.
	 *  
	 * @param name the property name
	 * @return the property value.
	 */
	static int getIntProperty(String name) {
		String value = getProperty(name);
		try {
			return Integer.parseInt(value);
		}
		catch (NumberFormatException e) {
			System.err.println("Invalid value " + value +
				" for benchmark property " + name);
			throw e;
		}
	}

	//------------------//
	// Local Properties //
	//------------------//

	/**
	 * Gets the value of a "name" property.
	 * 
	 * @param properties either JPA Provider or DBMS properties
	 * @return the value of the "name" property.
	 */
	public static String getName(Properties properties) {
		return properties.getProperty("name").trim();
	}
}
