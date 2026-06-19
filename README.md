# weasis-dicom-tools

[![License: EPL 2.0](https://img.shields.io/badge/License-EPL%202.0-blue.svg)](https://opensource.org/licenses/EPL-2.0)
[![License: Apache 2.0](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://opensource.org/licenses/Apache-2.0)
![Maven Build](https://github.com/nroduit/weasis-dicom-tools/workflows/Build/badge.svg)

[![Lines of Code](https://sonarcloud.io/api/project_badges/measure?project=weasis-dicom-tools&metric=ncloc)](https://sonarcloud.io/component_measures?id=weasis-dicom-tools)
[![Reliability](https://sonarcloud.io/api/project_badges/measure?project=weasis-dicom-tools&metric=reliability_rating)](https://sonarcloud.io/component_measures?id=weasis-dicom-tools)
[![Maintainability](https://sonarcloud.io/api/project_badges/measure?project=weasis-dicom-tools&metric=sqale_rating)](https://sonarcloud.io/component_measures?id=weasis-dicom-tools)
[![Security](https://sonarcloud.io/api/project_badges/measure?project=weasis-dicom-tools&metric=security_rating)](https://sonarcloud.io/component_measures?id=weasis-dicom-tools)
[![Quality Gate](https://sonarcloud.io/api/project_badges/measure?project=weasis-dicom-tools&metric=alert_status)](https://sonarcloud.io/dashboard?id=weasis-dicom-tools) [![Coverage](https://sonarcloud.io/api/project_badges/measure?project=weasis-dicom-tools&metric=coverage)](https://sonarcloud.io/summary/new_code?id=weasis-dicom-tools)

A Java DICOM toolkit built on top of [dcm4che3](https://github.com/dcm4che/dcm4che) and
[weasis-core-img](https://github.com/nroduit/weasis-core-img) (an OpenCV wrapper). It provides a
high-level DICOM network API, STOW-RS / WADO / QIDO clients, an image transcoder and ImageIO
reader, hanging protocol parsing, and anatomic region code dictionaries.

Used in production by [Weasis](https://github.com/nroduit/Weasis),
[Karnak](https://github.com/OsiriX-Foundation/karnak) and
[ViewerHub](https://github.com/nroduit/viewer-hub).

## Contents

- [Features](#features)
- [Getting started](#getting-started)
- [Building from source](#building-from-source)
- [Release history](#release-history)
- [License](#license)

## Features

### DICOM network (SCU / SCP)

- [C-Echo](weasis-dicom-tools/src/main/java/org/weasis/dicom/op/Echo.java),
  [C-Find](weasis-dicom-tools/src/main/java/org/weasis/dicom/op/CFind.java),
  [C-Get](weasis-dicom-tools/src/main/java/org/weasis/dicom/op/CGet.java),
  [C-Move](weasis-dicom-tools/src/main/java/org/weasis/dicom/op/CMove.java) and
  [C-Store](weasis-dicom-tools/src/main/java/org/weasis/dicom/op/CStore.java) clients. Each call
  returns a `DicomState` you can poll for progress, status and response identifiers.
- Modality Worklist SCU, Store SCP, Modality Worklist SCP.
- DICOM forward service with on-the-fly attribute modification, and a dicomizer.

### Web services

- [STOW-RS](weasis-dicom-tools/src/main/java/org/weasis/dicom/web/DicomStowRS.java) client.
- WADO-URI and QIDO-RS clients.

### Image pipeline

`Transcoder.dcm2dcm`
([Transcoder.java](weasis-dicom-tools/src/main/java/org/dcm4che3/img/Transcoder.java)):

- Equivalent of dcm4che's `dcm2dcm` command — transcoding, compression, decompression.
- The destination can be either a file or a directory (when it is a directory, the source
  filename is reused).
- Writes `LossyImageCompressionMethod` and `LossyImageCompressionRatio` to preserve compression
  history.
- Optional mask area drawn on the image with a specific color (for de-identification), configured
  via `DicomTranscodeParam`.

`Transcoder.dcm2image`
([Transcoder.java](weasis-dicom-tools/src/main/java/org/dcm4che3/img/Transcoder.java)):

- Converts to JPEG, PNG, TIF, JP2, PNM, BMP or HDR.
- Preserves pixel depth where the format allows (e.g. 16-bit TIF, floating-point TIF, HDR).
- Applies Modality, VOI and Presentation LUT to 8-bit outputs.
- Supports multiframe and multi-fragment input (multiframe outputs are indexed).
- Applies a Presentation State (W/L, LUTs, overlay) with configurable overlay color.

Image I/O is also exposed as a standard `ImageReader` via `DicomImageReader` (registered through
`META-INF/javax.imageio.spi.ImageReaderSpi`).

### Other

- [Hanging Protocol](weasis-dicom-tools/src/main/java/org/weasis/dicom/hp) parsing (DICOM HP IODs).
- [Anatomic region code](weasis-dicom-tools/src/main/java/org/weasis/dicom/ref) dictionaries —
  body parts, surface parts, modifiers, localized in EN and FR.

## Getting started

The artifact version tracks the underlying dcm4che release with a trailing `.1` qualifier
(`${dcm4che.version}.1`, e.g. `5.34.3.1`). Releases are published to a GitHub-hosted Maven
repository:

```xml
<repositories>
  <repository>
    <id>nroduit-mvn-repo</id>
    <name>nroduit GitHub Maven Repository</name>
    <url>https://raw.githubusercontent.com/nroduit/mvn-repo/master/</url>
    <snapshots>
      <enabled>true</enabled>
      <updatePolicy>daily</updatePolicy>
    </snapshots>
  </repository>
</repositories>

<dependency>
  <groupId>org.weasis</groupId>
  <artifactId>weasis-dicom-tools</artifactId>
  <version>5.34.3.1</version>
</dependency>
```

For more examples, see the
[test classes](weasis-dicom-tools/src/test/java/org/weasis/dicom).

### Quick examples

Load a DICOM image into a `PlanarImage` (OpenCV-backed matrix):

```java
var reader = new DicomImageReader(new DicomImageReaderSpi());
try {
  reader.setInput(new DicomFileInputStream(Path.of("input.dcm")));
  PlanarImage image = reader.getPlanarImage();         // first frame
  List<PlanarImage> frames = reader.getPlanarImages(); // all frames if multiframe
} finally {
  reader.dispose();
}
```

Convert a DICOM file to JPEG (or PNG, TIF, JP2, …):

```java
var params = new ImageTranscodeParam(Format.JPEG);
params.setJpegCompressionQuality(85);
List<Path> outputs = Transcoder.dcm2image(Path.of("input.dcm"), Path.of("out/"), params);
```

Transcode a DICOM file to another transfer syntax (e.g. JPEG-Lossless):

```java
var params = new DicomTranscodeParam(UID.JPEGLosslessSV1);
Path output = Transcoder.dcm2dcm(Path.of("input.dcm"), Path.of("out/"), params);
```

For both `Transcoder` calls, the source must be a single existing DICOM file (directories are
not iterated). The destination can be either a file or a directory — when a directory is passed,
the source filename is reused with the appropriate extension. Multiframe DICOM inputs are split
into one output file per frame, with a zero-padded `-N` index appended before the extension (e.g.
`input-01.png`, `input-02.png`, …) — this is why `dcm2image` returns a `List<Path>`.

### Native library

The image pipeline depends on the native OpenCV library shipped by
[weasis-core-img](https://github.com/nroduit/weasis-core-img), which must be on
`java.library.path` at runtime (e.g. `-Djava.library.path="path/of/native/lib"`). Prebuilt
binaries for additional systems and architectures are available from
[this Maven repository](https://github.com/nroduit/mvn-repo/tree/master/org/weasis/thirdparty/org/opencv/libopencv_java).

## Building from source

Prerequisites: JDK 17+ and Maven 3.6.3+.

```sh
mvn clean install
```

The `master` branch requires Java 17+. Older branches:

- `5.25.x` — Java 8+
- `1.0.x` — Java 7+

Code is formatted with [google-java-format](https://github.com/google/google-java-format) via
Spotless. Run `mvn spotless:apply` before committing.

## Release history

See [CHANGELOG](CHANGELOG.md).

## License

Dual-licensed under [EPL 2.0](https://opensource.org/licenses/EPL-2.0) and
[Apache 2.0](https://opensource.org/licenses/Apache-2.0). You may choose either license.
