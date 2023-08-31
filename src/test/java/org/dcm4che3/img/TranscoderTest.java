/*
 * Copyright (c) 2021 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.dcm4che3.img;

import java.awt.Color;
import java.awt.Dimension;
import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.nio.file.*;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import org.dcm4che3.data.UID;
import org.dcm4che3.img.Transcoder.Format;
import org.dcm4che3.img.data.ImageContentHash;
import org.dcm4che3.img.data.PrDicomObject;
import org.dcm4che3.img.stream.DicomFileInputStream;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.weasis.core.util.FileUtil;
import org.weasis.opencv.data.PlanarImage;
import org.weasis.opencv.op.ImageProcessor;

@DisplayName("Transcoder")
class TranscoderTest {

  static Path IN_DIR = FileSystems.getDefault().getPath("target/test-classes/org/dcm4che3/img");

  static final Path OUT_DIR = FileSystems.getDefault().getPath("target/test-out/");

  private static final DicomImageReader reader = new DicomImageReader(new DicomImageReaderSpi());

  static {
    FileUtil.delete(OUT_DIR);
    try {
      Files.createDirectories(OUT_DIR);
    } catch (IOException e) {
      System.err.println(e.getMessage());
    }
  }

  static final Consumer<Double> zeroDiff =
      val ->
          Assertions.assertEquals(
              0.0,
              val,
              "The hash result of the image input is not exactly the same as the output image");

  static final Consumer<Double> hasDiff =
      val ->
          Assertions.assertNotEquals(
              0.0,
              val,
              "The hash result of the image input is exactly the same as the output image");

  @Test
  @DisplayName("Check the rendering of an image where we hava applied a Presentation State LUT")
  void dcm2imageApplyPresentationStateLUT() throws Exception {
    Path in = FileSystems.getDefault().getPath(IN_DIR.toString(), "imageForPrLUTs.dcm");
    Path inPr = FileSystems.getDefault().getPath(IN_DIR.toString(), "prLUTs.dcm");
    DicomImageReadParam readParam = new DicomImageReadParam();
    readParam.setPresentationState(PrDicomObject.getPresentationState(inPr.toString()));
    ImageTranscodeParam params = new ImageTranscodeParam(readParam, Format.PNG);
    List<Path> outFiles = Transcoder.dcm2image(in, OUT_DIR, params);
    Assertions.assertFalse(outFiles.isEmpty());
    Map<ImageContentHash, Consumer<Double>> enumMap = new EnumMap<>(ImageContentHash.class);
    enumMap.put(ImageContentHash.AVERAGE, zeroDiff);
    enumMap.put(ImageContentHash.PHASH, zeroDiff);
    enumMap.put(ImageContentHash.BLOCK_MEAN_ONE, zeroDiff);
    enumMap.put(ImageContentHash.COLOR_MOMENT, zeroDiff);
    compareImageContent(
        FileSystems.getDefault().getPath(IN_DIR.toString(), "expected_imgForPrLUT.png"),
        outFiles.get(0),
        enumMap);
  }

  @Test
  @DisplayName("Check the rendering of an image where we hava applied a Presentation State Overlay")
  void dcm2imageApplyPresentationStateOverlay() throws Exception {
    Path in = FileSystems.getDefault().getPath(IN_DIR.toString(), "overlay.dcm");
    Path inPr = FileSystems.getDefault().getPath(IN_DIR.toString(), "prOverlay.dcm");
    DicomImageReadParam readParam = new DicomImageReadParam();
    readParam.setPresentationState(PrDicomObject.getPresentationState(inPr.toString()));
    readParam.setOverlayColor(Color.GREEN);
    ImageTranscodeParam params = new ImageTranscodeParam(readParam, Format.PNG);
    List<Path> outFiles = Transcoder.dcm2image(in, OUT_DIR, params);
    Assertions.assertFalse(outFiles.isEmpty());
    Map<ImageContentHash, Consumer<Double>> enumMap = new EnumMap<>(ImageContentHash.class);
    enumMap.put(ImageContentHash.AVERAGE, zeroDiff);
    enumMap.put(ImageContentHash.PHASH, zeroDiff);
    enumMap.put(ImageContentHash.BLOCK_MEAN_ONE, zeroDiff);
    enumMap.put(ImageContentHash.COLOR_MOMENT, zeroDiff);
    compareImageContent(
        FileSystems.getDefault().getPath(IN_DIR.toString(), "expected_overlay.png"),
        outFiles.get(0),
        enumMap);
  }

  @Test
  @DisplayName("Change the size of image")
  void dcm2dcmResize() throws Exception {
    DicomTranscodeParam params = new DicomTranscodeParam(UID.JPEGLSNearLossless);
    params.getReadParam().setSourceRenderSize(new Dimension(128, 128));
    Path out = transcodeDicom("signed-raw-9bit.dcm", params, null);
    List<PlanarImage> images = readImages(out);
    Assertions.assertEquals(128, images.get(0).width(), "The width of image doesn't match");
    Assertions.assertEquals(128, images.get(0).height(), "The height of image doesn't match");
  }


  @Test
  void dcm2dcm_TranscodeMultipleTimes() throws Exception {
    test("MR-JPEGLosslessSV1.dcm", UID.JPEGLSLossless, UID.JPEGLosslessSV1);
    test("CT-JPEGLosslessSV1.dcm", UID.ExplicitVRLittleEndian, UID.JPEGLSLossless, UID.JPEGLosslessSV1);
  }

  void test(String srcFileName, String... transferSyntaxList) throws Exception {
    String newSrcFileName = null;

    for (int i = 0; i < transferSyntaxList.length; i++) {
      String transferSyntax = transferSyntaxList[i];
      String dstFileName = transferSyntax + ".dcm";
      DicomTranscodeParam params = new DicomTranscodeParam(transferSyntax);
      Map<ImageContentHash, Consumer<Double>> enumMap = new EnumMap<>(ImageContentHash.class);

      if (i == 0) {
        Path artifact = transcodeDicom(srcFileName, params, enumMap);
        Path target = FileSystems.getDefault().getPath(IN_DIR.toString(), dstFileName);
        Files.copy(artifact, target, StandardCopyOption.REPLACE_EXISTING);
      } else {
        transcodeDicom(newSrcFileName, params, enumMap);
      }

      newSrcFileName = dstFileName;
    }
  }

  @ParameterizedTest
  @EnumSource(Format.class)
  @DisplayName("Export YBR_422 DICOM to all the image formats")
  void dcm2imageYBR422Raw(Format format) throws Exception {
    Path in = FileSystems.getDefault().getPath(IN_DIR.toString(), "ybr422-raw.dcm");
    ImageTranscodeParam params = new ImageTranscodeParam(format);
    List<Path> outFiles = Transcoder.dcm2image(in, OUT_DIR, params);
    Assertions.assertFalse(outFiles.isEmpty());
    Map<ImageContentHash, Consumer<Double>> enumMap = new EnumMap<>(ImageContentHash.class);
    enumMap.put(ImageContentHash.AVERAGE, zeroDiff);
    enumMap.put(ImageContentHash.PHASH, zeroDiff);
    compareImageContent(in, outFiles, enumMap);
  }

  @ParameterizedTest
  @ValueSource(strings = {UID.JPEG2000, UID.JPEGBaseline8Bit, UID.JPEGLSNearLossless})
  @DisplayName("Transcode YBR_422 DICOM into several DICOM lossy syntax's")
  void dcm2dcmYBR422RawLossy(String lossyUID) throws Exception {
    Map<ImageContentHash, Consumer<Double>> enumMap = new EnumMap<>(ImageContentHash.class);
    enumMap.put(ImageContentHash.AVERAGE, zeroDiff);
    enumMap.put(ImageContentHash.PHASH, zeroDiff);
    // JPEG compression mainly reduce the color information
    enumMap.put(ImageContentHash.COLOR_MOMENT, hasDiff);
    DicomTranscodeParam params = new DicomTranscodeParam(lossyUID);
    if (lossyUID.equals(UID.JPEGLSNearLossless)) {
      params.getWriteJpegParam().setNearLosslessError(3);
    } else {
      params.getWriteJpegParam().setCompressionQuality(80);
    }
    transcodeDicom("ybr422-raw.dcm", params, enumMap);
  }

  @ParameterizedTest
  @ValueSource(strings = {UID.JPEG2000Lossless, UID.JPEGLosslessSV1, UID.JPEGLSLossless})
  @DisplayName("Transcode YBR_422 DICOM into several DICOM Lossless syntax's")
  void dcm2dcmYBR422RawLossless(String losslessUID) throws Exception {
    Map<ImageContentHash, Consumer<Double>> enumMap = new EnumMap<>(ImageContentHash.class);
    // The image content must be fully preserved with lossless compression
    enumMap.put(ImageContentHash.AVERAGE, zeroDiff);
    enumMap.put(ImageContentHash.PHASH, zeroDiff);
    enumMap.put(ImageContentHash.BLOCK_MEAN_ONE, zeroDiff);
    enumMap.put(ImageContentHash.COLOR_MOMENT, zeroDiff);
    DicomTranscodeParam params = new DicomTranscodeParam(losslessUID);
    transcodeDicom("ybr422-raw.dcm", params, enumMap);
  }

  private static void compareImageContent(
      Path in, Path out, Map<ImageContentHash, Consumer<Double>> enumMap) throws Exception {
    compareImageContent(in, Collections.singletonList(out), enumMap);
  }

  private static void compareImageContent(
      Path in, List<Path> outFiles, Map<ImageContentHash, Consumer<Double>> enumMap)
      throws Exception {
    List<PlanarImage> imagesIn = readImages(in);
    List<PlanarImage> imagesOut = readImages(outFiles);
    Assertions.assertEquals(
        imagesIn.size(),
        imagesOut.size(),
        "The number of image frames of the input file is different of the output file");
    for (int i = 0; i < imagesIn.size(); i++) {
      PlanarImage imgIn = imagesIn.get(i);
      PlanarImage imgOut = imagesOut.get(i);
      System.out.println();
      System.out.println("=== Image content diff of image " + (i + 1));
      System.out.println("=== Input: " + in);
      System.out.println(
          "=== Output: " + (i >= outFiles.size() ? outFiles.get(0) : outFiles.get(i)));
      for (Entry<ImageContentHash, Consumer<Double>> map : enumMap.entrySet()) {
        // Hash content comparison
        // http://qtandopencv.blogspot.com/2016/06/introduction-to-image-hash-module-of.html
        double val = map.getKey().compare(imgIn.toMat(), imgOut.toMat());
        System.out.println("\t" + map.getKey().name() + ": " + val);
        map.getValue().accept(val);
      }
    }
  }

  private static List<PlanarImage> readImages(List<Path> files) throws IOException {
    if (files.size() == 1 && files.get(0).getFileName().toString().endsWith(".dcm")) {
      reader.setInput(new DicomFileInputStream(files.get(0)));
      return reader.getPlanarImages(null);
    } else {
      return files.stream()
          .map(p -> ImageProcessor.readImageWithCvException(p.toFile(), null))
          .collect(Collectors.toList());
    }
  }

  private static List<PlanarImage> readImages(Path path) throws IOException {
    if (path.getFileName().toString().endsWith(".dcm")) {
      reader.setInput(new DicomFileInputStream(path));
      return reader.getPlanarImages(null);
    } else {
      return Collections.singletonList(
          ImageProcessor.readImageWithCvException(path.toFile(), null));
    }
  }

  private static Path transcodeDicom(
      String inName, DicomTranscodeParam params, Map<ImageContentHash, Consumer<Double>> enumMap)
      throws Exception {
    Path in = FileSystems.getDefault().getPath(IN_DIR.toString(), inName);
    Path out = FileSystems.getDefault().getPath(OUT_DIR.toString(), params.getOutputTsuid());
    Files.createDirectories(out);
    out = Transcoder.dcm2dcm(in, out, params);
    Assertions.assertTrue(out != null && Files.size(out) > 0, "The output image is empty");
    if (enumMap != null) {
      compareImageContent(in, out, enumMap);
    }
    return out;
  }
}
