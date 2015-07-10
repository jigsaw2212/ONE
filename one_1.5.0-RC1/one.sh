#! /bin/sh
java -Xmx512M -cp .:lib/ECLA.jar:lib/DTNConsoleConnection.jar:/usr/local/lib/gni:/usr/share/java/jblas.jar:/usr/local/share/java/shogun.jar:/usr/local/lib:/usr/local/share/java core.DTNSim $*

