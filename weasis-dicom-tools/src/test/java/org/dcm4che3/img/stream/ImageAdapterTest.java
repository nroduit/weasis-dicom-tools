/*
 * Copyright (c) 2024 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.dcm4che3.img.stream;

import static org.junit.jupiter.api.Assertions.*;

import java.awt.Color;
import java.awt.Shape;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.UID;
import org.dcm4che3.img.DicomImageReader;
import org.dcm4che3.img.DicomImageReaderSpi;
import org.dcm4che3.img.DicomJpegWriteParam;
import org.dcm4che3.img.DicomMetaData;
import org.dcm4che3.img.DicomOutputData;
import org.dcm4che3.img.op.MaskArea;
import org.dcm4che3.img.stream.ImageAdapter.AdaptTransferSyntax;
import org.dcm4che3.img.util.DicomUtils;
import org.dcm4che3.net.DataWriter;
import org.dcm4che3.net.PDVOutputStream;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.weasis.dicom.param.AttributeEditorContext;
import org.weasis.opencv.data.PlanarImage;

class ImageAdapterTest {
  static final Path IN_DIR =
      FileSystems.getDefault().getPath("target/test-classes/org/dcm4che3/img");
  static final Path OUT_DIR = FileSystems.getDefault().getPath("target/test-out/");
  static DicomImageReader reader;

  @BeforeAll
  static void setUp() throws URISyntaxException {
    reader = new DicomImageReader(new DicomImageReaderSpi());
    try {
      Files.createDirectories(OUT_DIR);
    } catch (IOException e) {
      System.err.println(e.getMessage());
    }
  }

  @AfterAll
  static void tearDown() {
    if (reader != null) reader.dispose();
  }

  private List<PlanarImage> readDicomImage(String filename) throws IOException {
    reader.setInput(
        new DicomFileInputStream(FileSystems.getDefault().getPath(IN_DIR.toString(), filename)));
    return reader.getPlanarImages(null);
  }

  @Test
  @DisplayName("Verify AdaptTransferSyntax constructor sets correct values")
  void shouldSetCorrectValuesInAdaptTransferSyntaxConstructor() {
    AdaptTransferSyntax syntax = new ImageAdapter.AdaptTransferSyntax("original", "requested");
    syntax.setSuitable("suitable");
    assertEquals("original", syntax.getOriginal());
    assertEquals("requested", syntax.getRequested());
    assertEquals("requested", syntax.getSuitable());

    syntax.setSuitable(UID.JPEG2000);
    assertEquals("1.2.840.10008.1.2.4.91", syntax.getSuitable());

    assertThrows(
        IllegalArgumentException.class, () -> new ImageAdapter.AdaptTransferSyntax("original", ""));
  }

  @Test
  @DisplayName("Convert lossy JPEG2000 multiframe with multi-fragments stream to JPEG-LS")
  void jpeg2000LossyMultiframe() throws Exception {
    transcodeImage(
        "jpeg2000-multiframe-multifragments.dcm",
        "convert-jpegls-multiframe-multifragments.dcm",
        UID.JPEGLSNearLossless);
  }

  @Test
  @DisplayName("Convert palette multiframe with JPEG-LS compression to JPEG2000 Lossy")
  void paletteJpegls() throws Exception {
    transcodeImage(
        "palette-multiframe-jpeg-ls.dcm", "convert-palette-multiframe-jpeg-ls.dcm", UID.JPEG2000);
  }

  @Test
  @DisplayName("Convert CT frame with JPEG-Lossless compression to JPEG2000 Lossy")
  void ctMnonoFrame() throws Exception {
    transcodeImage("CT-JPEGLosslessSV1.dcm", "convert-CT-JPEGLosslessSV1.dcm", UID.JPEG2000);
  }

  @Test
  @DisplayName("Convert raw 9-bit image to JPEG-Lossless")
  void ctRawFrameToJpegLossless() throws Exception {
    transcodeImage("signed-raw-9bit.dcm", "convert-signed-raw-9bit.dcm", UID.JPEGLosslessSV1);
  }

  private static void transcodeImage(String inputImg, String outputImg, String outputTsuid)
      throws Exception {
    File output = new File(OUT_DIR.toFile(), outputImg);
    reader.setInput(
        new DicomFileInputStream(FileSystems.getDefault().getPath(IN_DIR.toString(), inputImg)));
    DicomMetaData metaData = reader.getStreamMetadata();
    outputTsuid = getOutputTransferSyntax(false, metaData.getTransferSyntaxUID(), outputTsuid);
    var adaptTransferSyntax = new AdaptTransferSyntax(metaData.getTransferSyntaxUID(), outputTsuid);
    DicomJpegWriteParam params =
        DicomJpegWriteParam.buildDicomImageWriteParam(adaptTransferSyntax.getSuitable());
    adaptTransferSyntax.setJpegQuality(params.getCompressionQuality());
    adaptTransferSyntax.setCompressionRatioFactor(params.getCompressionRatioFactor());
    Attributes attributes = new Attributes(metaData.getDicomObject());
    AttributeEditorContext context =
        new AttributeEditorContext(adaptTransferSyntax.getOriginal(), null, null);
    List<Shape> shapeList = List.of(new java.awt.Rectangle(4, 4, 8, 8));
    context.setMaskArea(new MaskArea(shapeList, Color.MAGENTA));

    if (!ImageAdapter.writeDicomFile(
        attributes, adaptTransferSyntax, context.getEditable(), null, output)) {
      throw new IOException("Failed to write DICOM file: " + output);
    }

    BytesWithImageDescriptor desc =
        ImageAdapter.imageTranscode(attributes, adaptTransferSyntax, context);
    if (!ImageAdapter.writeDicomFile(
        attributes, adaptTransferSyntax, context.getEditable(), desc, output)) {
      throw new IOException("Failed to write DICOM file: " + output);
    }

    context.setMaskArea(null);
    desc = ImageAdapter.imageTranscode(attributes, adaptTransferSyntax, context);
    DataWriter dataWriter =
        ImageAdapter.buildDataWriter(attributes, adaptTransferSyntax, context.getEditable(), desc);
    dataWriterTest(dataWriter, outputTsuid);

    dataWriter =
        ImageAdapter.buildDataWriter(attributes, adaptTransferSyntax, context.getEditable(), null);
    dataWriterTest(dataWriter, outputTsuid);
  }

  private static void dataWriterTest(DataWriter dataWriter, String outputTsuid) throws IOException {
    dataWriter.writeTo(
        new PDVOutputStream() {
          @Override
          public void copyFrom(InputStream inputStream, int i) throws IOException {}

          @Override
          public void copyFrom(InputStream inputStream) throws IOException {}

          @Override
          public void write(int b) throws IOException {}
        },
        outputTsuid);
  }

  private static String getOutputTransferSyntax(
      boolean onlyRaw, String originalTsuid, String outputTsuid) {
    if (outputTsuid == null) {
      return originalTsuid;
    }
    if (onlyRaw && !DicomUtils.isNative(originalTsuid) && !UID.RLELossless.equals(originalTsuid)) {
      return originalTsuid;
    }
    if (DicomOutputData.isSupportedSyntax(outputTsuid)
        && DicomImageReader.isSupportedSyntax(originalTsuid)) {
      return outputTsuid;
    }
    if (UID.RLELossless.equals(originalTsuid)
        || UID.ImplicitVRLittleEndian.equals(originalTsuid)
        || UID.ExplicitVRBigEndian.equals(originalTsuid)) {
      return UID.ExplicitVRLittleEndian;
    }
    return originalTsuid;
  }
}
