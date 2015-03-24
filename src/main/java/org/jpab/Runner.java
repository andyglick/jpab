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
import java.sql.*;
import java.util.*;
import java.util.Date;

import javax.persistence.*;

import org.xml.sax.*;
import org.xml.sax.helpers.*;


/**
 * Runner of one benchmark test using one persistence unit.
 * Note: Invoked indirectly by Main (using Launcher).
 */
public final class Runner {

	//-------------//
	// Entry Point //
	//-------------//

	/**
	 * Runs a specified test on a specified persistence unit.
	 *
	 * @param args see usage message below
	 */
	public static void main(String[] args) throws Exception {
		// Check and get command line arguments:
		if (args.length < 2 || args.length > 3) {
			System.err.println("Usage: org.jpab.Runner "
				+ "<pu-name> <test-code> [<data-path>]");
			System.exit(1);
		}
		String persistenceUnitName = args[0];
		String testCode = args[1];
		String dbPath = (args.length >= 3) ? args[2] : null;

		// Try using JDBC to create a new empty server database:
		if (persistenceUnitName.endsWith("-server")) {
			createDatabase(); // needed for MySQL and PostreSQL
		}

		// Construct the Test instance:
		String className = ConfigHelper.getProperty(testCode + "-class");
		Test test = (Test)Class.forName(className).newInstance();
		test.setThreadCount(
			ConfigHelper.getIntProperty(testCode + "-threads"));
		test.setBatchSize(
			ConfigHelper.getIntProperty(testCode + "-batch-size"));

		// Run the test:
		try {
			new Runner(persistenceUnitName, test, dbPath).run();
		}
		catch (Exception e) {
			e.printStackTrace();
		}
	}

	/**
	 * Creates a new database by connecting to the DBMS server using JDBC.
	 */
	private static void createDatabase() throws Exception {
		// Extract all the persistence unit properties:
		final Properties properties = new Properties();
		XMLReader parser = XMLReaderFactory.createXMLReader();
		parser.setContentHandler(new DefaultHandler() {
			@Override
			public void startElement(String uri, String localName, String name,
					Attributes attributes) throws SAXException {
				if ("property".equals(name)) {
					properties.put(attributes.getValue("name"),
						attributes.getValue("value"));
				}
			}
		});
		parser.parse(new InputSource(Runner.class.getResource(
			"/META-INF/persistence.xml").openStream()));

		// Load the JDBC driver:
		String driver = properties.getProperty("jpab.driver");
		if (driver == null) {
			return; // cannot connect to the server
		}
		Class.forName(driver).newInstance();

		// Build the connection url:
		String url = properties.getProperty("jpab.url");
		int dbNamePos = url.lastIndexOf('/') + 1;
		int dbNameEndPos = url.lastIndexOf(';');
		String dbName = (dbNameEndPos < 0) ?
			url.substring(dbNamePos) : url.substring(dbNamePos, dbNameEndPos);
		url = url.substring(0, dbNamePos - 1);
		url += "?user=" + properties.getProperty("jpab.user");
		url += "&password=" + properties.getProperty("jpab.password");

		// Try to create the database:
		try {
			Connection con = DriverManager.getConnection(url);
			Statement s = con.createStatement();
			s.executeUpdate("CREATE DATABASE " + dbName);
		}
		catch (Exception e) {
			// silently ignored - database may be created automatically
		}
	}

	//-----------//
	// Constants //
	//-----------//

	/** Nano seconds in one second */
	private static final long NANO_PER_SEC = 1000000000L;

	//--------------//
	// Data Members //
	//--------------//

	// General:

	/** Name of the persistence unit */
	private final String persistenceUnitName;

	/** Name of the JPA provider */
	private final String jpaName;

	/** Name and mode of the database */
	private final String dbName;

	/** The test to be run */
	private final Test test;

	/** The database directory for calculating disk space */
	private File dbDir;

	// Persist/Remove Action Scope:

	/** Number of objects to persist/remove during warm up */
	private final int warmupObjectCount;

	/** Total number of persisted objects (including warm up + test) */
	private final int totalObjectCount;

	// All Action Scope:

	/** Time for warming up the JVM before test (in milliseconds) */
	private final long warmupTime;

	/** Time per test run (in milliseconds) */
	private final long totalTime;

	// Running:

	/** Currently tested action */
	private String actionName;

	/** Connection factory for the test database */
	private EntityManagerFactory emf;

	/** Test actions - one per thread (in most runs - one) */
	private TestAction[] actions;

	//--------------//
	// Construction //
	//--------------//

	/**
	 * Constructs a Runner instance.
	 *
	 * @param persistenceUnitName name of the persistence unit
	 * @param test the test to be run
	 * @param dbPath optional database path for calculating disk space
	 */
	private Runner(String persistenceUnitName, Test test, String dbPath) {
		// Prepare test run properties:
		this.warmupObjectCount = ConfigHelper.getIntProperty("warmup-objects");
		this.totalObjectCount = ConfigHelper.getIntProperty("total-objects");
		this.warmupTime = NANO_PER_SEC *
			ConfigHelper.getIntProperty("warmup-time");
		this.totalTime = NANO_PER_SEC *
			ConfigHelper.getIntProperty("total-time");

		// Set the run context:
		this.persistenceUnitName = persistenceUnitName;
		int ix = persistenceUnitName.indexOf('-');
		this.jpaName = persistenceUnitName.substring(0, ix);
		this.dbName = persistenceUnitName.substring(ix + 1);
		this.test = test;
		this.dbDir = (dbPath != null) ? new File(dbPath) : FileHelper.WORK_DIR;
		test.setEntityCount(totalObjectCount);
		test.buildInventory(totalObjectCount * 13 / 10);

		// Delete old databases (if any):
		if (FileHelper.WORK_DIR.exists()) {
			FileHelper.deleteDirContent(FileHelper.WORK_DIR);
		}
	}

	//-----------------------//
	// Running the Benchmark //
	//-----------------------//

	/**
	 * Runs the entire test (all actions) on the persistence unit.
	 */
	private void run() {
		// Mark the test actions as runnable:
		reportResult("started");

		// Print the benchmark title:
		System.out.print(FormatHelper.getShortClassName(test.getClass()));
		System.out.print("(thread=" + test.getThreadCount());
		System.out.print(", batch=" + test.getBatchSize() + ")");
		System.out.println(" results:");

		// Prepare for disk space check:
		if (!dbDir.exists()) {
			dbDir = dbDir.getParentFile();
		}
		long spaceBefore = FileHelper.getDiskSpace(dbDir);

		// Test the persist action (and fill the database):
		if (!handleAction(PersistAction.class)) {
			return; // do not continue to other actions
		}

		// Report the disk space usage:
		long spaceAfter = FileHelper.getDiskSpace(dbDir);
		long diskSpace = spaceAfter - spaceBefore;
		long diskSpaceInMB = diskSpace >> 20;
		System.out.println("Disk Space: " +
			FormatHelper.formatInt(diskSpaceInMB) + "MB");
		reportResult(Long.valueOf(diskSpace), "Space");

		// Test the other actions:
		handleAction(RetrieveAction.class);
		if (test.hasQueries()) {
			handleAction(QueryAction.class);
		}
		handleAction(UpdateAction.class);
		handleAction(RemoveAction.class);
	}

	/**
	 * Handles a single test action (method).
	 *
	 * @param actionClass wraps a benchmark method (action)
	 * @return true - on success; false - on failure.
	 */
	private boolean handleAction(Class<? extends TestAction> actionClass) {

		try {
			// Prepare the action name:
			actionName = FormatHelper.getShortClassName(actionClass);
			if (actionName.endsWith("Action")) {
				actionName = actionName.substring(0, actionName.length() - 6);
			}

			// Create the database connection factory:
			System.out.println("PU Name -> " + persistenceUnitName);
			emf = Persistence.createEntityManagerFactory(persistenceUnitName);

			// Prepare working threads for the test:
			int threadCount = test.getThreadCount();
			actions = new TestAction[threadCount];
			for (int threadIx = 0; threadIx < threadCount; threadIx++) {
				TestAction action = actionClass.newInstance();
				action.test = test;
				action.em = emf.createEntityManager(); // private per thread
				actions[threadIx] = action;
			}

			// Test the action:
			runAction(actionClass);

			// On success - return true:
			return true;
		}

		// Handle a failure:
		catch (Throwable e) {
			// Capture the exception stack trace as a string:
	        String stackTrace = FormatHelper.formatStackTrace(e);

	        // Print an error message:
			System.out.print(actionName + " failed: " + e.getMessage());
			if ("false".equalsIgnoreCase(ConfigHelper.getProperty("verbose"))) {
				System.out.println(" *** Turn on verbose to print stack trace.");
			}
			else {
				System.out.println();
				e.printStackTrace();
			}

	        // Report the failure in the result output file:
	        if (PersistAction.class.isAssignableFrom(actionClass)) {
	        	reportResult(stackTrace);
	        }
	        else {
	        	reportResult(stackTrace, actionName);
	        }

			// Return false (indicating a failure):
			return false;
		}

		// Cleanup:
		finally {
			// Close the database connections:
			if (actions != null) {
				for (TestAction thread : actions) {
					if (thread != null && thread.em != null) {
						try {
							if (thread.em.getTransaction().isActive())
								thread.em.getTransaction().rollback();
							if (thread.em.isOpen())
								thread.em.close();
						}
						catch (Exception e) {
						}
					}
				}
				actions = null;
			}

			// Close the database connection factory:
			if (emf != null) {
				try {
					emf.close();
					emf = null;
				}
				catch (Exception e) {
				}
			}
		}
	}

	/**
	 * Runs a single test action.
	 *
	 * @param threadClass wraps a benchmark method (action)
	 */
	private void runAction(Class<? extends TestAction> threadClass)
			throws Throwable {
		// Run a warm up:
		test.resetActionCount();
		long testStartTime = System.nanoTime();
		long deadline = testStartTime + warmupTime;
		runThreads(deadline, warmupObjectCount);
		int warmupActionCount = test.getActionCount();

		// Run the real test:
		testStartTime = System.nanoTime();
		deadline = testStartTime + totalTime - warmupTime;
		int maxObjectCount = totalObjectCount;
		runThreads(deadline, maxObjectCount);

		// Calculate the elapsed time:
		long elapsedTime = System.nanoTime() - testStartTime;

		// Verify that the database is not empty:
		// (due to an EclipseLink/HSQLDB problem with ExtTest)
		if (threadClass == PersistAction.class) {
			Query query = actions[0].em.createQuery(
				"SELECT COUNT(o) FROM " + test.getEntityName() + " o");
			if (((Long)query.getSingleResult()).longValue() == 0) {
				throw new RuntimeException("Failed to persist objects");
			}
		}

		// Prepare the action details:
		int actionCount = test.getActionCount() - warmupActionCount;
		double actionsPerSecond = (double) actionCount * NANO_PER_SEC / elapsedTime;

		// Report the result:
		System.out.println(actionName + ": " +
			FormatHelper.formatNumber(actionsPerSecond));
		reportResult(Double.valueOf(actionsPerSecond), actionName);

		// Complete the persist action:
		if (threadClass == PersistAction.class) {
			// All tests must be done on a database of the same size:
			while (test.getActionCount() < totalObjectCount) {
				int batchSize = Math.min(20000,
					totalObjectCount - test.getActionCount());
				test.persist(actions[0].em, batchSize);
			}
			test.clearInventory();
		}
	}

	/**
	 * Runs all the test threads.
	 *
	 * @param deadline for all the threads to stop
	 * @param maxEntityCount maximum objects to persist/remove
	 */
	private void runThreads(long deadline, int maxEntityCount)
			throws Throwable {
		// Wrap the actions with threads:
		int threadCount = actions.length;
		Thread[] threads = new Thread[threadCount];
		for (int i = 0; i < threadCount; i++) {
			threads[i] = new Thread(actions[i]);
		}

		// Start all the threads:
		for (int i = 0; i < threadCount; i++) {
			actions[i].maxEntityCount = maxEntityCount;
			actions[i].deadline = deadline;
			threads[i].start(); // run asynchronously
		}

		// Wait for all the threads to complete:
		for (int i = 0; i < threadCount; i++) {
			synchronized (threads[i]) {
				threads[i].join();
				if (actions[i].exception != null) {
					throw actions[i].exception;
				}
			}
		}
	}

	// Reporting results:

	/**
	 * Writes result lines for ALL the actions.
	 *
	 * @param result "started", result number or exception string
	 */
	private void reportResult(Object result) {
		reportResult(result, "Persist");
		reportResult(result, "Retrieve");
		reportResult(result, "Update");
		reportResult(result, "Remove");
		if (test.hasQueries()) {
			reportResult(result, "Query");
		}
		reportResult(result, "Space");
	}

	/**
	 * Writes result line for a specified action.
	 *
	 * @param result one of: "started", result number or exception string
	 * @param actionName the name of the action
	 */
	private void reportResult(Object result, String actionName) {
		StringBuilder sb = new StringBuilder(256);
		sb.append(FormatHelper.formatTime(new Date())).append(' ');
		sb.append(jpaName).append(' ');
		sb.append(dbName).append(' ');
		sb.append(test.getName()).append(' ');
		sb.append(test.getThreadCount()).append(' ');
		sb.append(test.getBatchSize()).append(' ');
		sb.append(totalObjectCount).append(' ');
		sb.append(actionName).append(' ');
		sb.append(result);
		FileHelper.writeTextLine(sb.toString(), FileHelper.RESULT_FILE);
	}

	//--------------//
	// Test Actions //
	//--------------//

	/**
	 * Abstract superclass of classes that wrap test actions (for threads).
	 */
	static abstract class TestAction implements Runnable {

		/** The test */
		Test test;

		/** Private connection (per thread) to the test database */
		EntityManager em;

		/** Maximum objects for the persist/remove actions */
		long maxEntityCount;

		/** Deadline for all actions (a System.naonTime value) */
		long deadline;

		/** Exception that has been thrown from the thread */
		Throwable exception;

		/**
		 * Run wrapper.
		 */
		public final void run() {
			try {
				run0();
			}
			catch (Throwable e) {
				exception = e;
			}
		}

		/**
		 * The real (wrapped) run.
		 */
		protected abstract void run0();
	}

	/**
	 * Wrapper of the persist action.
	 */
	static class PersistAction extends TestAction {
		@Override
		public void run0() {
			while (test.getActionCount() < maxEntityCount
					&& System.nanoTime() < deadline) {
				test.persist(em);
			}
		}
	}

	/**
	 * Wrapper of the retrieve action.
	 */
	static class RetrieveAction extends TestAction {
		@Override
		public void run0() {
			while (System.nanoTime() < deadline) {
				test.doAction(em, Test.ActionType.RETRIEVE);
			}
		}
	}

	/**
	 * Wrapper of the query action.
	 */
	static class QueryAction extends TestAction {
		@Override
		public void run0() {
			while (System.nanoTime() < deadline) {
				test.query(em);
			}
		}
	}

	/**
	 * Wrapper of the update action.
	 */
	static class UpdateAction extends TestAction {
		@Override
		public void run0() {
			while (System.nanoTime() < deadline) {
				test.doAction(em, Test.ActionType.UPDATE);
			}
		}
	}

	/**
	 * Wrapper of the remove action.
	 */
	static class RemoveAction extends TestAction {
		@Override
		public void run0() {
			while (test.getActionCount() < maxEntityCount
					&& System.nanoTime() < deadline) {
				test.doAction(em, Test.ActionType.DELETE);
			}
		}
	}
}
