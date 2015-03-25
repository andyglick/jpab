# JPA Benchmark

"Forked" from jpab.org.

## Main changes

* Use Maven
* Use NoSQL databases (Cassandra/Kundera, ...)
* Removed Ext Tests (not supported by Kundera)

## Compile and Run

Traditional maven command:

    mvn package

Create the required key space inside Cassandra:

    cqlsh
    cqlsh> CREATE KEYSPACE kunderatests;

Run the JAR inside the target folder:

    cd target
    java -jar jpab-1.0-SNAPSHOT.jar

## About Kundera

Issues found so far:
* It does not support extends (I had to remove Ext tests)
* SELECT COUNT(o) FROM Object o, returns an object, not a Number
