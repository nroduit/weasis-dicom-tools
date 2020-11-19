# weasis-dicom-tools #

[![License](https://img.shields.io/badge/License-EPL%202.0-blue.svg)](https://opensource.org/licenses/EPL-2.0) ![Maven Build](https://github.com/nroduit/weasis-dicom-tools/workflows/Maven%20Build/badge.svg)  
[![Sonar](https://sonarcloud.io/api/project_badges/measure?project=org.weasis%3Aweasis-dicom-tools&metric=ncloc)](https://sonarcloud.io/component_measures?id=org.weasis%3Aweasis-dicom-tools) [![Sonar](https://sonarcloud.io/api/project_badges/measure?project=org.weasis%3Aweasis-dicom-tools&metric=reliability_rating)](https://sonarcloud.io/component_measures?id=org.weasis%3Aweasis-dicom-tools) [![Sonar](https://sonarcloud.io/api/project_badges/measure?project=org.weasis%3Aweasis-dicom-tools&metric=sqale_rating)](https://sonarcloud.io/component_measures?id=org.weasis%3Aweasis-dicom-tools) [![Sonar](https://sonarcloud.io/api/project_badges/measure?project=org.weasis%3Aweasis-dicom-tools&metric=security_rating)](https://sonarcloud.io/component_measures?id=org.weasis%3Aweasis-dicom-tools) [![Sonar](https://sonarcloud.io/api/project_badges/measure?project=org.weasis%3Aweasis-dicom-tools&metric=alert_status)](https://sonarcloud.io/dashboard?id=org.weasis%3Aweasis-dicom-tools)   

This project provides a DICOM API for [C-Echo](src/main/java/org/weasis/dicom/op/Echo.java), [C-Move](src/main/java/org/weasis/dicom/op/CMove.java), [C-Get](src/main/java/org/weasis/dicom/op/CGet.java), [C-Find](src/main/java/org/weasis/dicom/op/CFind.java) and [C-Store](src/main/java/org/weasis/dicom/op/CStore.java) based on dcm4che3. The implementation allows to follow the progression of an DICOM operation like C-Move and gives its status. It contains also some other classes for worklist SCU, strore SCP, dicomization, DICOM forward with attributes modification on the fly and a DICOM gateway (experimental).

This project replaces [weasis-dicom-operations](https://github.com/nroduit/weasis-dicom-operations) and now this library is used by recent versions of [weasis-pacs-connector](https://github.com/nroduit/weasis-pacs-connector) and in the weasis-dicom-codec module of [Weasis](https://github.com/nroduit/Weasis).

**Getting started**: [see the test classes](https://github.com/nroduit/weasis-dicom-tools/tree/master/src/test/java/org/weasis/dicom)

## [Release History](CHANGELOG.md)

## Build weasis-dicom-tools ##

Prerequisites: JDK 8 and Maven 3

The master branch requires Java 8+ and the 1.0.x branch requires Java 7+.

Execute the maven command `mvn clean install` in the root directory of the project.

Note: the dependencies are not includes in the jar file, see in [pom.xml](pom.xml) which libraries are required (at least dcm4che-core and dcm4che-net).
