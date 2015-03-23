/*
 * JPA Performance Benchmark - http://www.jpab.org
 * Copyright � ObjectDB Software Ltd. All Rights Reserved.
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
 * Main class of the JPA Benchmark.
 */
public final class Main {

	//-----------//
	// Constants //
	//-----------//

	/** When REPEAT is false - a test with output in result.txt is skipped */
	private static final boolean REPEAT = true;

	/**
	 * All the entity classes in the benchmark tests
	 * (for generating persistence.xml files dynamically)
	 */
	private static Class[] ENTITY_CLASSES = {
		org.jpab.basic.Person.class,
		org.jpab.col.CollectionPerson.class,
		org.jpab.ext.PersonBase.class,
		org.jpab.ext.PersonExt.class,
		org.jpab.ext.PersonExtExt.class,
		org.jpab.index.IndexedPerson.class,
		org.jpab.node.Node.class
	};

	//--------------//
	// Data Members //
	//--------------//

	// General:

	/** Timeout in milliseconds to wait for every run */
	private final long timeout;

	/** Global JAR files (common to all runs) */
	private final File[] globalJarFiles;

	/** Signatures of tests that already have results
 	(e.g. "DataNucleus-PostgreSQL-server-NodeTest-1-100-100000") */
	private final HashSet<String> OldResults = new HashSet<String>(997, 0.5F);

	// JPA (changed in Outer Loop):

	/** Name of the active JPA provider */
	private String jpaName;

	/** Properties of the active JPA provider */
	private Properties jpaProperties;

	/** Path to the JPA provider Java Agent (null - if not available) */
	private String javaAgentPath;

	/** The JPA provider JAR files */
	private File[] jpaJarFiles;

	/** Name of the active persistence unit */
	private String persistenceUnitName;

	// DBMS (changed in Inner Loop)

	/** Name of the active DBMS */
	private String dbmsName;

	/** Active database mode - either "embedded" or "server" */
	private String mode;

	/** Properties of the active DBMS */
	private Properties dbmsProperties;

	/** The DBMS JAR files */
	private File[] dbmsJarFiles;

	/** Dynamic database name (replaces $ in properties paths) */
	private String dbFileName;

	//-------------//
	// Entry Point //
	//-------------//

	/**
	 * The JPA Benchmark entry point.
	 *
	 * @args unused (arguments are specified in properties files)
	 */
	public static void main(String[] args) {
		new Main().run();
	}

	/**
	 * Constructs a Main instance.
	 */
	public Main() {
		// Set global settings:
		timeout = 1000 * ConfigHelper.getIntProperty("timeout");
		globalJarFiles = FileHelper.getJarFiles(
			new File(FileHelper.ROOT_DIR, "lib"));
	}

	/**
	 * Runs the JPA Benchmark tests.
	 */
	private void run() {
		try {
			// Load existing results (to avoid duplicate runs):
			try {
				loadOldResults();
			}
			catch (IOException e) {
				System.err.println("Failed to read from result file");
			}

			// Run the benchmarks:
			runAllCombinations();
		}
		catch (RuntimeException e) {
			// Print error context:
			System.err.println("JPA Provider: " + jpaName);
			System.err.println("DBMS: " + dbmsName);
			System.err.println("Mode: " + mode);

			// Print the stack trace and exit:
			e.printStackTrace();
			System.exit(1);
		}
	}

	//-------------------------//
	// Avoiding Duplicate Runs //
	//-------------------------//

	/**
	 * Loads existing results and stores their signatures.
	 */
	private void loadOldResults() throws IOException {
		if (FileHelper.RESULT_FILE.exists()) {
			BufferedReader reader =
				new BufferedReader(new FileReader(FileHelper.RESULT_FILE));
			String line;
			while ((line = reader.readLine()) != null) {
				try {
					int pos = findBlankPos(line, 1) + 1;
					int endPos = findBlankPos(line, 7);
					String signature =
						line.substring(pos, endPos).replace(' ', '-');
					OldResults.add(signature);
				}
				catch (RuntimeException e) {
					System.err.println("Error reading old result line: " +
						line + ' ' + e.getClass().getName() + ':' + e.getMessage());
				}
			}
			reader.close();
		}
	}

	/**
	 * Finds the position of the n-th space character in a line.
	 *
	 * @param line a line to search in
	 * @param ordinal 0 - for the first; 1 - for the second, etc
	 * @return the position of the space character.
	 */
	public static int findBlankPos(String line, int ordinal) {
		int pos = -1;
		while (ordinal-- >= 0)
			pos = line.indexOf(' ', pos + 1);
		return pos;
	}

	/**
	 * Checks if a specified test already has results.
	 *
	 * @param puName the persistence unit name
	 * @param testCode the test code
	 * @return true - if result exists; false - if not.
	 */
	private boolean hasResult(String puName, String testCode) {
		StringBuilder sb = new StringBuilder(64);
		sb.append(puName);
		sb.append('-').append(FormatHelper.getShortClassName(
			ConfigHelper.getProperty(testCode + "-class")));
		sb.append('-').append(ConfigHelper.getProperty(testCode + "-threads"));
		sb.append('-').append(ConfigHelper.getProperty(testCode + "-batch-size"));
		sb.append('-').append(ConfigHelper.getProperty("total-objects"));
		String signature = sb.toString();
		return OldResults.contains(signature);
	}

	//-----------------------//
	// Running the Benchmark //
	//-----------------------//

	/**
	 * Runs all tests on all the JPA Provider / DBMS combinations.
	 */
	private void runAllCombinations() {

		for (File jpaDir : FileHelper.JPA_DIRS) {

			System.out.println("Loading JPA dir: " + jpaDir);

			// Load JPA Provider / ObjectDB properties:
			jpaProperties = ConfigHelper.loadProperties(jpaDir);
			if (jpaDir.getName().startsWith("_") || jpaProperties == null) {
				continue; // no properties or disabled
			}
			jpaName = ConfigHelper.getName(jpaProperties);

			// Prepare the provider JAR files:
			jpaJarFiles = FileHelper.getJarFiles(new File(jpaDir, "lib"));

			// Prepare a Java Agent path:
			javaAgentPath = null;
			String javaAgent = jpaProperties.getProperty("java-agent");
			if (javaAgent != null) {
				File javaAgentFile =
					new File(new File(jpaDir, "lib"), javaAgent);
				if (javaAgentFile.exists()) {
					javaAgentPath = javaAgentFile.getPath();
				}
				else {
					System.err.println(
						"Java Agent is not found at " + javaAgentFile);
				}
			}

			// Test active JPA provider against relevant DBMS implementations:
			runOneJpaProvider();
		}
	}

	/**
	 * Runs all tests on the active JPA Provider and relevant DBMS.
	 */
	private void runOneJpaProvider() {
		// Handle a JPA Object Database:
		if ("false".equals(jpaProperties.getProperty("orm"))) {
			dbmsProperties = jpaProperties; // no separate DBMS
			dbmsJarFiles = new File[0];
			dbmsName = jpaName;
			runOneCombination();
		}

		// Handle an ORM JPA Provider:
		else {
			for (File dbmsDir : FileHelper.DBMS_DIRS) {

				System.out.println("Loading DB dir: " + dbmsDir);

				// Load DBMS properties:
				dbmsProperties = ConfigHelper.loadProperties(dbmsDir);
				if (dbmsDir.getName().startsWith("_") ||
						dbmsProperties == null) {
					continue; // no properties or disabled
				}
				dbmsName = ConfigHelper.getName(dbmsProperties);

				// Prepare the provider JAR files:
				// dbmsJarFiles = FileHelper.getJarFiles(new File(dbmsDir, "lib"));

				// Invoke the Benchmark Runner once per DBMS:
				runOneCombination();
			}
		}
	}

	/**
	 * Runs all tests on a single JPA Provider / DBMS combination.
	 */
	private void runOneCombination() {
		// Exclude invalid combinations:
		if ("false".equals(dbmsProperties.getProperty(jpaName))) {
			return; // skip this combination
		}

		// Run embedded mode (if available):
		if (dbmsProperties.getProperty("embedded-url") != null) {
			mode = "embedded";
			runOneCombinationMode();
		}

		// Run client-server mode (if available):
		if (dbmsProperties.getProperty("server-url") != null) {
			mode = "server";
			runOneCombinationMode();
		}
	}

	/**
	 * Runs all tests on a single JPA Provider / DBMS combination mode.
	 */
	private void runOneCombinationMode() {
		// Collect the JAR file paths:
		List<String> jarFilePathList = new ArrayList<String>(16);
		FileHelper.addJarFiles(globalJarFiles, jarFilePathList);
		FileHelper.addJarFiles(jpaJarFiles, jarFilePathList);
		FileHelper.addJarFiles(dbmsJarFiles, jarFilePathList);
		String[] jarFilePaths = jarFilePathList.toArray(new String[0]);

		// Prepare a persistence unit name and a database file name:
		persistenceUnitName = jpaName + "-" + dbmsName + "-" + mode;

		System.out.println("Persistence Unit Name: " + persistenceUnitName);


		for (String testCode : ConfigHelper.getTestCodes()) {
			if (REPEAT || !hasResult(persistenceUnitName, testCode)) {
				// Generate a dynamic persistence unit:
				dbFileName = "jpab" + Randomizer.randomNumString(10, 10);
				FileHelper.writeTextFile(
					buildPersistenceXml(), FileHelper.PU_XML_FILE);

				// Run the benchmark in a new process:
				runTest(testCode, jarFilePaths);
			}
		}
	}

	/**
	 * Runs ONE test on a single JPA Provider / DBMS combination mode.
	 */
	private void runTest(String testCode, String[] jarFilePaths) {
		// Prepare the test name:
		String testName = FormatHelper.getShortClassName(
			ConfigHelper.getProperty(testCode + "-class"));

		// Exclude tests from some providers:
		String exclude = ConfigHelper.getProperty(testCode + "-exclude");
		if (exclude != null && persistenceUnitName.startsWith(exclude)) {
			System.out.println("Skipped " +
				persistenceUnitName + " -> " + testName);
			return;
		}

		// Start the launcher:
		List<String> argList = new ArrayList<String>(3);
		argList.add(persistenceUnitName);
		argList.add(testCode);
		String dataPath = dbmsProperties.getProperty(mode + "-data");
		if (dataPath != null) {
			String dbPath = dataPath.replace("$", dbFileName);
			argList.add(dbPath);
		}
		Launcher launcher = new Launcher(
			javaAgentPath, jarFilePaths, Runner.class, argList);
		long startTime = System.currentTimeMillis();
		launcher.start();

		// Print a message:
		System.out.println("Starting " +
			persistenceUnitName + " -> " + testName);

		// Wait for the launcher's and its subprocess:
		try {
			launcher.join(timeout);
		}
		catch (InterruptedException e) {
		}

		// Kill the sub process if still running:
		if (launcher.isAlive()) {
			launcher.kill();
			try {
				launcher.join();
			}
			catch (InterruptedException e) {
			}
		}

		// Print messages:
		long elapsedTime = (System.currentTimeMillis() - startTime) / 1000;
		System.out.print(launcher.getStdOutMessage());
		System.out.println("Completed in " + elapsedTime + " seconds.");
		System.out.println(launcher.getStdErrMessage());
	}

	//----------------------------//
	// Building a persistence.xml //
	//----------------------------//

	/**
	 * Builds a dynamic persistence.xml with a single persistence unit.
	 *
	 * @return the persistence.xml content as a string.
	 */
	private String buildPersistenceXml() {
		// Start creating a dynamic persistence.xml:
		StringBuilder sb = new StringBuilder(1024);
		sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>");
		sb.append(FormatHelper.NEW_LINE);
		sb.append("<persistence xmlns=\"http://java.sun.com/xml/ns/persistence\" ");
		sb.append("xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" ");
		sb.append("xsi:schemaLocation=\"http://java.sun.com/xml/ns/persistence ");
		sb.append("http://java.sun.com/xml/ns/persistence/persistence_2_0.xsd\" ");
		sb.append("version=\"2.0\">").append(FormatHelper.NEW_LINE);

		// Write the persistence unit opening tag:
		sb.append("  <persistence-unit name=\"");
		sb.append(persistenceUnitName);
		sb.append("\">").append(FormatHelper.NEW_LINE);

		// Write the provider factory class:
		sb.append("    <provider>");
		sb.append(jpaProperties.getProperty("provider"));
		sb.append("</provider>").append(FormatHelper.NEW_LINE);

		// Write the entity classes:
		for (Class entityClass : ENTITY_CLASSES) {
			sb.append("    <class>");
			sb.append(entityClass.getName());
			sb.append("</class>").append(FormatHelper.NEW_LINE);
		}

		// Write the properties opening tag:
		sb.append("    <properties>").append(FormatHelper.NEW_LINE);

		// Prepare connection properties:
		String driver = dbmsProperties.getProperty(mode + "-driver");
		String url = dbmsProperties.getProperty(mode + "-url");
		url = url.replace("^", FileHelper.WORK_DIR.getPath().replace('\\', '/'));
		url = url.replace("$", dbFileName);
		String user = dbmsProperties.getProperty(mode + "-user");
		String password = dbmsProperties.getProperty(mode + "-password");
		String customConnectionProperty = jpaProperties.getProperty(mode + "-connection");

		// Write connection properties:
		if (customConnectionProperty == null) {
			// Write standard JPA connection properties:
			if (driver != null) {
				appendProperty(sb, "javax.persistence.jdbc.driver", driver);
			}
			appendProperty(sb, "javax.persistence.jdbc.url", url);
			if (user != null) {
				appendProperty(sb, "javax.persistence.jdbc.user", user);
			}
			if (password != null) {
				appendProperty(sb, "javax.persistence.jdbc.password", password);
			}
		}
		else {
			// Write a JPA vendor specific connection property:
			customConnectionProperty = customConnectionProperty.replace("$url", url);
			customConnectionProperty = customConnectionProperty.replace("$driver", driver);
			if (user != null) {
				customConnectionProperty = customConnectionProperty.replace("$user", user);
			}
			if (password != null) {
				customConnectionProperty = customConnectionProperty.replace("$password", password);
			}
			appendProperty(sb, customConnectionProperty);
		}

		// Write connection properties for creating the database with JDBC:
		if (driver != null) {
			appendProperty(sb, "jpab.driver", driver);
		}
		appendProperty(sb, "jpab.url", url);
		if (user != null) {
			appendProperty(sb, "jpab.user", user);
		}
		if (password != null) {
			appendProperty(sb, "jpab.password", password);
		}

		// Write additional properties:
		String dbmsName = dbmsProperties.getProperty("name").trim();
		for (String propertyName : jpaProperties.stringPropertyNames()) {
			if (propertyName.startsWith("property")
					|| propertyName.equals(dbmsName)) {
				appendProperty(sb, jpaProperties.getProperty(propertyName));
			}
		}

		// Write the closing properties tag:
		sb.append("    </properties>").append(FormatHelper.NEW_LINE);

		// Write the closing persistence unit tag:
		sb.append("  </persistence-unit>").append(FormatHelper.NEW_LINE);

		// Write the closing persistence.xml tag:
		sb.append("</persistence>").append(FormatHelper.NEW_LINE);
		String str = sb.toString();
		return str;
	}

	/**
	 * Appends a property to a persistence unit definition.
	 *
	 * @param sb a StringBuilder to append the property to
	 * @param name the property name
	 * @param value the property value
	 */
	private static void appendProperty(
			StringBuilder sb, String name, String value) {
		appendProperty(sb, "<property name=\"" +
			name + "\" value=\"" + value + "\"/>");
	}

	/**
	 * Appends a property to a persistence unit definition.
	 *
	 * @param sb a StringBuilder to append the property to
	 * @param property the full property XML element
	 */
	private static void appendProperty(StringBuilder sb, String property) {
		sb.append("      ").append(property).append(FormatHelper.NEW_LINE);
	}
}
