#! /bin/sh
java -Xmx512M -cp .:lib/ECLA.jar:lib/DTNConsoleConnection.jar:/usr/local/lib/jni:/home/curiousguy13/shogun-java-install/share/java/shogun.jar core.DTNSim $*
