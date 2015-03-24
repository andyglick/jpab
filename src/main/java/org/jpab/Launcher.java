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
 * The Launcher class is used to run a JVM in an external process (fork).
 */
public final class Launcher extends Thread {

	//--------------//
	// Data Members //
	//--------------//

	// Static:

	/** All the active sub processes (for cleanup shutdown hook) */
	static HashSet<Process> processSet = new HashSet<Process>();

	// Arguments:

	/** Path to an optional Java Agent jar file */
	private final String javaAgentPath;

	/** Paths to be added to the classpath */
	private final String[] jarPaths;

	/** Entry point class (with a main method) */
	private final Class mainClass;

	/** Program arguments */
	private final List<String> argList;

	// Running:

	/** The running sub process */
	private Process process;

	/** Process exit code (0 - for success; 1 - for error) */
	private int exitCode;

	/** Standard output message lines */
	private final List<String> outMsgList = new ArrayList<String>(8);

	/** Standard error message lines */
	private final List<String> errMsgList = new ArrayList<String>(8);

	//--------------//
	// Construction //
	//--------------//

	/**
	 * Constructs a Launcher instance.
	 *
	 * @param javaAgentPath path to an optional Java Agent jar file (or null)
	 * @param jarPaths paths to be added to the classpath
	 * @param mainClass the entry point class (with a main method)
	 * @param argList list of program arguments
	 */
	Launcher(String javaAgentPath, String[] jarPaths,
			Class mainClass, List<String> argList) {
		this.javaAgentPath = javaAgentPath;
		this.jarPaths = jarPaths;
		this.mainClass = mainClass;
		this.argList = argList;
	}

	//---------//
	// Running //
	//---------//

	/**
	 * Runs the JVM as an external process.
	 */
	@Override
	public void run() {
		try {
			// Prepare the classpath:
			StringBuilder sb = new StringBuilder(1024);
			sb.append(FileHelper.TEMP_DIR.getPath());
			sb.append(File.pathSeparatorChar).append(FileHelper.CLASS_ROOT);
			for (String jarPath : jarPaths) {
				sb.append(File.pathSeparatorChar).append(jarPath);
			}
			String classpath = sb.toString();

			// Prepare the JVM command line arguments:
			List<String> cmdList = new ArrayList<String>(64);
			File jvmFile = new File(new File(
				System.getProperty("java.home"), "bin"), "java");
			cmdList.add(jvmFile.getPath());
			cmdList.add("-server");
			cmdList.add("-Xmx512m");
			if (javaAgentPath != null) {
				cmdList.add("-javaagent:" + javaAgentPath);
			}
			cmdList.add("-cp");
			cmdList.add(classpath);
			cmdList.add(mainClass.getName());
			cmdList.addAll(argList);
			String[] cmd = cmdList.toArray(new String[0]);

			// This log may be required for debugging...
			// System.out.println("Launching:");
			// for(int i = 0; i < cmd.length; i++) {
			//	 System.out.println("cmd[ "+ i + "] = " + cmd[i]);
			// }

			// Start executing the JVM process (asynchronously):
			process = Runtime.getRuntime().exec(cmd, null);

			// Start collecting standard output and error:
			MessageCollector errorListener =
				new MessageCollector(process.getErrorStream(), errMsgList);
			errorListener.start();
			MessageCollector outputListener =
				new MessageCollector(process.getInputStream(), outMsgList);
			outputListener.start();

			// Wait for the process to complete:
			processSet.add(process);
			exitCode = process.waitFor();
			processSet.remove(process);
			process = null;
			errorListener.join();
			outputListener.join();
		}

		// Handle exceptions:
		catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	/**
	 * Kills the subprocess.
	 */
	public void kill() {
		if (process != null) {
			process.destroy();
		}
	}

    //--------------//
    // Cleanup Hook //
    //--------------//

    /**
     * Shutdown Hook for closing open processes.
     */
    static {
        Runtime.getRuntime().addShutdownHook(
            new Thread("Launcher-Cleanup") {
                @Override
                public void run() {
                	for (Process process : Launcher.processSet) {
                		try { process.destroy(); } catch (Throwable e) {}
                    }
                }
            }
        );
    }

	//---------//
	// Results //
	//---------//

	/**
	 * Gets the standard output message.
	 *
	 * @return the standard output message.
	 */
	String getStdOutMessage() {
		StringBuilder sb = new StringBuilder(1024);
		for (String s : outMsgList) {
			sb.append(s).append(FormatHelper.NEW_LINE);
		}
		return sb.toString();
	}

	/**
	 * Gets the standard error message.
	 *
	 * @return the standard error message.
	 */
	String getStdErrMessage() {
		StringBuilder sb = new StringBuilder(1024);
		for (String s : errMsgList)
			sb.append(s).append(FormatHelper.NEW_LINE);
		return sb.toString();
	}

	//------------------//
	// MessageCollector //
	//------------------//

	/**
	 * The MessageCollector class collects external process message lines.
	 */
	final static class MessageCollector extends Thread {

		// Data Members:

		/** Input stream to be read (process standard output or error) */
		private final InputStream m_in;

		/** List to be filled with collected message lines */
		private final List<String> m_messageList;

		// Construction:

		/**
		 * Constructs a MessageCollector instance.
		 *
		 * @param in the input stream to read message lines from
		 * @param messageList to be filled with collected message lines
		 */
		MessageCollector(InputStream in, List<String> messageList) {
			this.m_in = in;
			m_messageList = messageList;
		}

		// Running:

		/**
		 * Collects message lines from the input stream.
		 */
		@Override
		public void run() {
			try {
				BufferedReader reader =
					new BufferedReader(new InputStreamReader(m_in));
				String line;
				while ((line = reader.readLine()) != null) {
					m_messageList.add(line);
				}
			}
			catch (IOException e) {
				e.printStackTrace();
			}
		}
	}
}
