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

It also provides an API to convert and manipulate images from DICOM files.

Here are the main features of [dcm2dcm](src/main/java/org/dcm4che3/img/Transcoder.java#L126-L170):

* Similar as the dcm2dcm command of the dcm4che toolkit: image transcoding, compression or decompression
* Accept folders and files as input parameters
* Write compressed images with LossyImageCompressionMethod and LossyImageCompressionRatio (keep the succession of old compressed values)
* Option add mask area on the image with a specific color (for de-identification). In DicomTranscodeParam add a mask.

Here are the main features of [dcm2image](src/main/java/org/dcm4che3/img/Transcoder.java#L80-L115):

* Convert in several images formats (JPEG, PNG, TIF, JP2, PNM, BMP or HDR)
* Option to preserve the pixel depth with some formats (e.g. 16-bit TIF, double values in TIF or HDR)
* Capabilities to apply Modality, VOI and Presentation LUT with 8-bit output images
* Support multiframe and/or multi-fragments. For multiframe output images are created with an index.
* Apply a Presentation State (W/L, LUTs, overlay).
* Set the color of overlays in image or Presentation State


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
