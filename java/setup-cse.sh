#!/bin/bash

# This actually runs the typecheckers on Lanfear, Martin's personal laptop.

# To do: make this actually run on a CSE machine.
# Part of that at least will require running something like this command:
# mvn install:install-file -DgroupId=com.amazonaws -DartifactId=aws-crypto-compliance-checker -Dversion=1.0 -Dpackaging=jar -Dfile=`pwd`/aws-crypto-policy-compliance-checker.jar

mvn clean compile
