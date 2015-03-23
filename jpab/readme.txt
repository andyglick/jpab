The JPA Performance Benchmark - version 1.0.1

To run the benchmark you should follow these instructions.

Product Installation
--------------------
This distribution does not include JPA and DBMS products,
so you have to download and install them separately.
At least one DBMS/JPA combination is needed to run the benchmark.

Each DBMS/JPA product should have its own sub directory:
- DBMS products under the db directory.
- JPA implementations under the jpa directory.

Directories whose name starts with underscore (_) are disabled.
Remove the preceding _ to enable a DBMS/JPA product and copy
its client side jar files to the lib subdirectory.
See the placeholder.txt on the lib directory for more details.

Product Settings
----------------
Besides the lib sub directory - each product directory should
contain a benchmark.properties file with the product properties.

Default setting should usually be fine for embedded databases.
For server mode - the user and password properties must be set.

In the database urls - ^ is automatically replaced with a root
data directory for embedded database files.
$ is replaced with a random database name - in order to use
a new database in every test run.

You can comment server/embedded setting lines in order to test
a DBMS that supports both modes in only one mode.

For JPA implementations - verify that the java-agent specifies
the exact name of the agent JAR file (which should be located
in the product lib sub directory).

Benchmark Settings
------------------
The benchmark.properties file in the root directory defines the
benchmark configuration.

The first lines set the number of objects and time in seconds
for each test run. Notice that total objects/time must always be
larger than warm up objects/time, because warm up activity is
excluded from the results.

Test processes that do not complete execution in time as specified
as the timeout are killed.

Every test is defined using its own properties.
To run the original benchmark tests - do not change test settings.

Benchmark Running
-----------------
If you are testing also DBMS in server mode, start the database
servers before running the benchmark.

You can use the executable JAR to run the benchmark:
> java -jar jpab.jar

Use JDK rather than JRE because the benchmark requires a Server
HotSpot JVM (use absolute path to the JDK's java.exe if necessary).

Results are expected to be printed to the standard output and
also logged to the results.txt file.
