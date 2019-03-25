#!/bin/bash

# Part of that at least will require running something like this command:
mvn install:install-file -DgroupId=com.amazonaws -DartifactId=aws-crypto-compliance-checker -Dversion=1.0 -Dpackaging=jar -Dfile=`pwd`/aws-crypto-policy-compliance-checker.jar

mvn install:install-file -DgroupId=com.amazonaws -DartifactId=kms-compliance-checker -Dversion=1.0 -Dpackaging=jar -Dfile=`pwd`/aws-kms-compliance-checker.jar

mvn clean compile
