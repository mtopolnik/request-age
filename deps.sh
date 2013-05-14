#!/bin/sh

set -e

mvn clean assembly:assembly -DdescriptorId=jar-with-dependencies
cd lib
rm -rf *
jar xvf ../target/*-jar-with-dependencies.jar
