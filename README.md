# JPA Benchmark

"Forked" from jpab.org.

## Main changes

* Use Maven
* Use NoSQL databases (Cassandra/Kundera, ...)
* Removed Ext Tests (not supported by Kundera)

## About Kundera

Issues found so far:
* It does not support extends (I had to remove Ext tests)
* SELECT COUNT(o) FROM Object o, returns an object, not a Number
