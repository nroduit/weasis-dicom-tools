# weasis-dicom-tools #

[![License](https://img.shields.io/badge/License-EPL%202.0-blue.svg)](https://opensource.org/licenses/EPL-2.0) [![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://opensource.org/licenses/Apache-2.0) ![Maven Build](https://github.com/nroduit/weasis-dicom-tools/workflows/Build/badge.svg)  
[![Sonar](https://sonarcloud.io/api/project_badges/measure?project=weasis-dicom-tools&metric=ncloc)](https://sonarcloud.io/component_measures?id=weasis-dicom-tools) [![Sonar](https://sonarcloud.io/api/project_badges/measure?project=weasis-dicom-tools&metric=reliability_rating)](https://sonarcloud.io/component_measures?id=weasis-dicom-tools) [![Sonar](https://sonarcloud.io/api/project_badges/measure?project=weasis-dicom-tools&metric=sqale_rating)](https://sonarcloud.io/component_measures?id=weasis-dicom-tools) [![Sonar](https://sonarcloud.io/api/project_badges/measure?project=weasis-dicom-tools&metric=security_rating)](https://sonarcloud.io/component_measures?id=weasis-dicom-tools) [![Sonar](https://sonarcloud.io/api/project_badges/measure?project=weasis-dicom-tools&metric=alert_status)](https://sonarcloud.io/dashboard?id=weasis-dicom-tools)

This project provides a DICOM API for [C-Echo](src/main/java/org/weasis/dicom/op/Echo.java)
, [C-Move](src/main/java/org/weasis/dicom/op/CMove.java)
, [C-Get](src/main/java/org/weasis/dicom/op/CGet.java)
, [C-Find](src/main/java/org/weasis/dicom/op/CFind.java)
and [C-Store](src/main/java/org/weasis/dicom/op/CStore.java) based on dcm4che3. The implementation
allows to follow the progression of an DICOM operation like C-Move and gives its status. It contains
also some other classes for worklist SCU, strore SCP, dicomization, DICOM forward with attributes
modification on the fly.

This library is used by [Weasis](https://github.com/nroduit/Weasis), [Karnak](https://github.com/OsiriX-Foundation/karnak) and [weasis-pacs-connector](https://github.com/nroduit/weasis-pacs-connector).

**Getting
started**: [see the test classes](https://github.com/nroduit/weasis-dicom-tools/tree/master/src/test/java/org/weasis/dicom)

Code formatter: [google-java-format](https://github.com/google/google-java-format)

# Release History
See [CHANGELOG](CHANGELOG.md)

# Build weasis-dicom-tools ##

Prerequisites: JDK 8 and Maven 3

Execute the maven command `mvn clean install` in the root directory of the project.

The master branch requires Java 11+ and old branches are:
* 5.25.x (Java 8+)
* 1.0.x (Java 7+)

Note: the dependencies are not includes in the jar file, see in [pom.xml](pom.xml).
