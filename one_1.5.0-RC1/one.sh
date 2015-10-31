#! /bin/sh
java -Xmx512M -cp .:lib/ECLA.jar:lib/DTNConsoleConnection.jar:/usr/local/lib/jni:/usr/local/share/java/shogun.jar:/usr/lib/java/jblas.jar:/usr/local/share/java:/usr/local/lib  core.DTNSim $*
