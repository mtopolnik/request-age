#!/bin/sh

set -e

mvn clean assembly:assembly -DdescriptorId=jar-with-dependencies
cd lib
jar xvf ../target/*-jar-with-dependencies.jar
