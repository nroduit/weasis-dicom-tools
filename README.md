# weasis-dicom-tools #

This project provides a DICOM API for [C-Echo](src/main/java/org/weasis/dicom/op/Echo.java), [C-Move](src/main/java/org/weasis/dicom/op/CMove.java), [C-Get](src/main/java/org/weasis/dicom/op/CGet.java), [C-Find](src/main/java/org/weasis/dicom/op/CFind.java) and [C-Store](src/main/java/org/weasis/dicom/op/CStore.java) based on dcm4che3. The implementation allows to follow the progression of an DICOM operation like C-Move and gives its status. It contains also some helper classes for worklist, dicomization and stroreSCP.

This project replaces [weasis-dicom-operations](https://github.com/nroduit/weasis-dicom-operations) and now this library is used by recent versions of [weasis-pacs-connector](https://github.com/nroduit/weasis-pacs-connector) and in the weasis-dicom-codec module of [Weasis](https://github.com/nroduit/Weasis).

## Build weasis-dicom-tools ##

Prerequisites: JDK 7 and Maven

Execute the maven command `mvn clean install` in the root directory of the project.

Note: the dependencies are not includes in the jar file, see in [pom.xml](pom.xml) which libraries are required (at least dcm4che-core and dcm4che-net).