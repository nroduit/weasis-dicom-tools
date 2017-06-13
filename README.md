# weasis-dicom-tools #

The master branch requires Java 8+ and the 1.0.x branch requires Java 7+.

This project provides a DICOM API for [C-Echo](src/main/java/org/weasis/dicom/op/Echo.java), [C-Move](src/main/java/org/weasis/dicom/op/CMove.java), [C-Get](src/main/java/org/weasis/dicom/op/CGet.java), [C-Find](src/main/java/org/weasis/dicom/op/CFind.java) and [C-Store](src/main/java/org/weasis/dicom/op/CStore.java) based on dcm4che3. The implementation allows to follow the progression of an DICOM operation like C-Move and gives its status. It contains also some helper classes for worklist, dicomization and stroreSCP.

This project replaces [weasis-dicom-operations](https://github.com/nroduit/weasis-dicom-operations) and now this library is used by recent versions of [weasis-pacs-connector](https://github.com/nroduit/weasis-pacs-connector) and in the weasis-dicom-codec module of [Weasis](https://github.com/nroduit/Weasis).

## Build weasis-dicom-tools ##
[![CircleCI](https://circleci.com/gh/nroduit/weasis-dicom-tools.svg?style=svg&circle-token=574daa639fe437af07cc9abed3bd024d17a56505)](https://circleci.com/gh/nroduit/weasis-dicom-tools) [![Codacy Badge](https://api.codacy.com/project/badge/Grade/59629585bee1428c813a0d5e0c20cd95)](https://www.codacy.com/app/nicolas.roduit/weasis-dicom-tools?utm_source=github.com&amp;utm_medium=referral&amp;utm_content=nroduit/weasis-dicom-tools&amp;utm_campaign=Badge_Grade)

Prerequisites: JDK 8 and Maven 3

Execute the maven command `mvn clean install` in the root directory of the project.

Note: the dependencies are not includes in the jar file, see in [pom.xml](pom.xml) which libraries are required (at least dcm4che-core and dcm4che-net).
