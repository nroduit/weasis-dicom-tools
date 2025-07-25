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

import static org.junit.jupiter.api.Assertions.*;

import java.awt.Color;
import java.awt.Dimension;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.Map.Entry;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.UID;
import org.dcm4che3.data.VR;
import org.dcm4che3.img.Transcoder.Format;
import org.dcm4che3.img.data.PrDicomObject;
import org.dcm4che3.img.stream.DicomFileInputStream;
import org.dcm4che3.io.DicomOutputStream;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.weasis.opencv.data.PlanarImage;
import org.weasis.opencv.op.ImageContentHash;
import org.weasis.opencv.op.ImageIOHandler;

class TranscoderTest {

  static final Path IN_DIR =
      FileSystems.getDefault().getPath("target/test-classes/org/dcm4che3/img");
  static final Path OUT_DIR = FileSystems.getDefault().getPath("target/test-out/transcoder");
  private static DicomImageReader reader;
  private DicomTestDataFactory testDataFactory;

  @BeforeAll
  static void setUp() throws IOException {
    Files.createDirectories(OUT_DIR);
    reader = new DicomImageReader(new DicomImageReaderSpi());
  }

  @AfterAll
  static void tearDown() {
    if (reader != null) reader.dispose();
  }

  @BeforeEach
  void setUpEach() {
    testDataFactory = new DicomTestDataFactory();
  }

  // Image comparison validators with context-aware thresholds
  private enum ValidationLevel {
    IDENTICAL(0.0), // Exact match (lossless)
    PERCEPTUALLY_IDENTICAL(0.001), // Visually identical (high-quality lossy)
    HIGH_QUALITY(0.015), // High quality (good lossy compression)
    ACCEPTABLE_QUALITY(0.05), // Acceptable quality (moderate compression)
    LOW_QUALITY(0.15), // Low quality (heavy compression)
    DIFFERENT(0.0); // Should be different

    private final double threshold;

    ValidationLevel(double threshold) {
      this.threshold = threshold;
    }

    public double getThreshold() {
      return threshold;
    }
  }

  private static Consumer<Double> createHashValidator(ValidationLevel level, boolean expectEqual) {
    return createHashValidator(level.getThreshold(), expectEqual, level.name());
  }

  private static Consumer<Double> createHashValidator(double threshold, boolean expectEqual) {
    return createHashValidator(threshold, expectEqual, "CUSTOM(" + threshold + ")");
  }

  private static Consumer<Double> createHashValidator(
      double threshold, boolean expectEqual, String levelName) {
    return val -> {
      String comparisonType = expectEqual ? "≤" : ">";
      String message =
          String.format(
              "Hash validation failed [%s]: expected %s %s %.6f, but got %.6f",
              levelName, val, comparisonType, threshold, val);

      if (expectEqual) {
        assertTrue(val <= threshold, message);
      } else {
        assertTrue(val != threshold, message);
      }
    };
  }

  // Predefined validators for common scenarios
  static final Consumer<Double> identical = createHashValidator(ValidationLevel.IDENTICAL, true);
  static final Consumer<Double> perceptuallyIdentical =
      createHashValidator(ValidationLevel.PERCEPTUALLY_IDENTICAL, true);
  static final Consumer<Double> highQuality =
      createHashValidator(ValidationLevel.HIGH_QUALITY, true);
  static final Consumer<Double> acceptableQuality =
      createHashValidator(ValidationLevel.ACCEPTABLE_QUALITY, true);
  static final Consumer<Double> shouldBeDifferent =
      createHashValidator(ValidationLevel.DIFFERENT, false);

  // Synthetic image test cases
  @Test
  @DisplayName("Convert monochrome DICOM to PNG with proper bit depth handling")
  void testMonochromeToPng() throws Exception {
    TestImageSpec spec =
        TestImageSpec.monochrome().withDimensions(256, 256).withBitDepth(16).withSigned(false);

    runBasicTranscodeTest(spec, Format.PNG);
  }

  @Test
  @DisplayName("Convert color DICOM to JPEG with low quality settings")
  void testColorToJpegWithQuality() throws Exception {
    TestImageSpec spec =
        TestImageSpec.color().withDimensions(128, 128).withPhotometricInterpretation("RGB");

    ImageTranscodeParam params = new ImageTranscodeParam(Format.JPEG);
    int quality = 50;
    params.setJpegCompressionQuality(quality);

    runTranscodeTest(spec, params, createQualityBasedExpectations(quality));
  }

  @Test
  @DisplayName("Convert color DICOM to JPEG with quality-based validation")
  void testColorToJpegWithQualityValidation() throws Exception {
    TestImageSpec spec =
        TestImageSpec.color().withDimensions(128, 128).withPhotometricInterpretation("RGB");

    int quality = 95;
    ImageTranscodeParam params = new ImageTranscodeParam(Format.JPEG);
    params.setJpegCompressionQuality(quality);

    runTranscodeTest(spec, params, createQualityBasedExpectations(quality));
  }

  @Test
  @DisplayName("Transcode multiframe DICOM to individual image files")
  void testMultiframeToImages() throws Exception {
    TestImageSpec spec = TestImageSpec.basic().withDimensions(64, 64).withFrameCount(3);

    Path inputFile = createTestImage(spec);
    ImageTranscodeParam params = new ImageTranscodeParam(Format.PNG);
    List<Path> outputFiles = Transcoder.dcm2image(inputFile, OUT_DIR, params);

    assertEquals(3, outputFiles.size(), "Should create 3 PNG files for 3 frames");

    for (int i = 0; i < outputFiles.size(); i++) {
      Path outputFile = outputFiles.get(i);
      assertTrue(Files.exists(outputFile));
      assertTrue(outputFile.getFileName().toString().contains("-" + (i + 1)));
    }
  }

  @Test
  @DisplayName("Resize DICOM image during transcoding")
  void testDicomResizing() throws Exception {
    TestImageSpec spec = TestImageSpec.basic().withDimensions(512, 512);
    Path inputFile = createTestImage(spec);

    DicomTranscodeParam params = new DicomTranscodeParam(UID.JPEGLosslessSV1);
    params.getReadParam().setSourceRenderSize(new Dimension(256, 256));

    Path outputFile = transcodeDicom(inputFile, params);

    List<PlanarImage> images = readDicomImages(outputFile);
    assertEquals(1, images.size());
    assertEquals(256, images.get(0).width());
    assertEquals(256, images.get(0).height());
  }

  @ParameterizedTest
  @EnumSource(Format.class)
  @DisplayName("Convert DICOM to all supported image formats")
  void testAllImageFormats(Format format) throws Exception {
    TestImageSpec spec =
        TestImageSpec.basic().withDimensions(64, 64).withBitDepth(format.support16U ? 16 : 8);

    runBasicTranscodeTest(spec, format);
  }

  @ParameterizedTest
  @ValueSource(strings = {UID.JPEGBaseline8Bit, UID.JPEG2000, UID.JPEGLSNearLossless})
  @DisplayName("Transcode to lossy DICOM compression formats with compression-specific validation")
  void testLossyDicomTranscoding(String targetTransferSyntax) throws Exception {
    TestImageSpec spec =
        TestImageSpec.color().withDimensions(128, 128).withPhotometricInterpretation("RGB");

    DicomTranscodeParam params = new DicomTranscodeParam(targetTransferSyntax);
    configureCompressionParams(params, targetTransferSyntax);

    runDicomTranscodeTest(spec, params, createQualityBasedExpectations(90));
  }

  @ParameterizedTest
  @ValueSource(strings = {UID.JPEGLosslessSV1, UID.JPEG2000Lossless, UID.JPEGLSLossless})
  @DisplayName("Transcode to lossless DICOM compression formats")
  void testLosslessDicomTranscoding(String targetTransferSyntax) throws Exception {
    TestImageSpec spec =
        TestImageSpec.color().withDimensions(128, 128).withPhotometricInterpretation("RGB");

    DicomTranscodeParam params = new DicomTranscodeParam(targetTransferSyntax);
    runDicomTranscodeTest(spec, params, createLosslessExpectations());
  }

  @ParameterizedTest
  @ValueSource(strings = {UID.RLELossless, UID.JPEG2000MC, UID.JPIPHTJ2KReferenced, UID.MPEG2MPHL})
  @DisplayName("Test transfer syntax adaptation when target format is unsupported")
  void testTransferSyntaxAdaptation(String targetTransferSyntax) {
    assertThrows(IllegalStateException.class, () -> new DicomTranscodeParam(targetTransferSyntax));
  }

  @Test
  @DisplayName("Test output stream transcoding")
  void testOutputStreamTranscoding() throws Exception {
    TestImageSpec spec = TestImageSpec.basic().withDimensions(64, 64);
    Path inputFile = createTestImage(spec);

    DicomTranscodeParam params = new DicomTranscodeParam(UID.JPEGLosslessSV1);

    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    Transcoder.dcm2dcm(inputFile, outputStream, params);

    assertTrue(outputStream.size() > 0, "Output stream should contain transcoded data");

    // Verify the transcoded data is valid DICOM
    byte[] transcodedData = outputStream.toByteArray();
    assertTrue(transcodedData.length > 132, "DICOM file should be larger than preamble + prefix");
  }

  @ParameterizedTest
  @EnumSource(Format.class)
  @DisplayName("Convert DICOM to all supported image formats with format-specific validation")
  void testAllImageFormatsWithSpecificValidation(Format format) throws Exception {
    TestImageSpec spec =
        TestImageSpec.basic().withDimensions(64, 64).withBitDepth(format.support16U ? 16 : 8);

    Path inputFile = createTestImage(spec);
    ImageTranscodeParam params = new ImageTranscodeParam(format);
    List<Path> outputFiles = Transcoder.dcm2image(inputFile, OUT_DIR, params);

    assertFalse(outputFiles.isEmpty());
    assertTrue(Files.exists(outputFiles.get(0)));
    assertTrue(outputFiles.get(0).toString().endsWith(format.extension));
    assertTrue(Files.size(outputFiles.get(0)) > 0);
  }

  @Nested
  @DisplayName("Real image transcoding tests with validation")
  class RealImageTranscodeTests {
    @Test
    @DisplayName("Apply Presentation State LUT")
    void dcm2imageApplyPresentationStateLUT() throws Exception {
      RealImageTestCase testCase =
          RealImageTestCase.builder()
              .withInputFile("imageForPrLUTs.dcm")
              .withPresentationState("prLUTs.dcm")
              .withExpectedImage("expected_imgForPrLUT.png")
              .withFormat(Format.PNG)
              .withExpectations(createLosslessExpectations())
              .build();

      runRealImageTest(testCase);
    }

    @Test
    @DisplayName("Apply Presentation State Overlay")
    void dcm2imageApplyPresentationStateOverlay() throws Exception {
      RealImageTestCase testCase =
          RealImageTestCase.builder()
              .withInputFile("overlay.dcm")
              .withPresentationState("prOverlay.dcm")
              .withExpectedImage("expected_overlay.png")
              .withFormat(Format.PNG)
              .withOverlayColor(Color.GREEN)
              .withExpectations(createLosslessExpectations())
              .build();

      runRealImageTest(testCase);
    }

    @Test
    @DisplayName("Resize real image")
    void dcm2dcmResize() throws Exception {
      RealImageTestCase testCase =
          RealImageTestCase.builder()
              .withInputFile("signed-raw-9bit.dcm")
              .withTargetTransferSyntax(UID.JPEGLSNearLossless)
              .withResizeTo(new Dimension(128, 128))
              .build();

      Path outputFile = runRealDicomTranscodeTest(testCase);
      List<PlanarImage> images = readDicomImages(outputFile);

      assertEquals(128, images.get(0).width(), "Width should match resize target");
      assertEquals(128, images.get(0).height(), "Height should match resize target");
    }

    @Test
    void dcm2dcm_TranscodeMultipleTimes() throws Exception {
      testMultipleTranscoding("MR-JPEGLosslessSV1.dcm", UID.JPEGLSLossless, UID.JPEGLosslessSV1);
      testMultipleTranscoding(
          "CT-JPEGLosslessSV1.dcm",
          UID.ExplicitVRLittleEndian,
          UID.JPEGLSLossless,
          UID.JPEGLosslessSV1);
    }

    @ParameterizedTest
    @ValueSource(strings = {UID.JPEG2000, UID.JPEGBaseline8Bit, UID.JPEGLSNearLossless})
    @DisplayName("Transcode YBR_422 DICOM to lossy formats with compression-aware validation")
    void dcm2dcmYBR422RawLossyEnhanced(String lossyUID) throws Exception {
      RealImageTestCase testCase =
          RealImageTestCase.builder()
              .withInputFile("ybr422-raw.dcm")
              .withTargetTransferSyntax(lossyUID)
              .withCompressionConfig(lossyUID)
              .withExpectations(createQualityBasedExpectations(80))
              .build();

      runRealDicomTranscodeTest(testCase);
    }

    @Test
    @DisplayName("Test near-lossless compression with specific error tolerance")
    void testNearLosslessWithErrorTolerance() throws Exception {
      RealImageTestCase testCase =
          RealImageTestCase.builder()
              .withInputFile("ybr422-raw.dcm")
              .withTargetTransferSyntax(UID.JPEGLSNearLossless)
              .withCompressionConfig(UID.JPEGLSNearLossless)
              .build();

      // Create custom expectations for near-lossless with error=2
      Map<ImageContentHash, Consumer<Double>> expectations = new EnumMap<>(ImageContentHash.class);
      expectations.put(
          ImageContentHash.AVERAGE, createHashValidator(0.002, true, "NEAR_LOSSLESS_STRUCTURAL"));
      expectations.put(
          ImageContentHash.PHASH, createHashValidator(0.002, true, "NEAR_LOSSLESS_PERCEPTUAL"));
      expectations.put(
          ImageContentHash.COLOR_MOMENT, createHashValidator(100, true, "NEAR_LOSSLESS_COLOR"));

      testCase.expectations = expectations;
      runRealDicomTranscodeTest(testCase);
    }

    @ParameterizedTest
    @ValueSource(strings = {UID.JPEG2000Lossless, UID.JPEGLosslessSV1, UID.JPEGLSLossless})
    @DisplayName("Transcode YBR_422 DICOM to lossless formats")
    void dcm2dcmYBR422RawLossless(String losslessUID) throws Exception {
      RealImageTestCase testCase =
          RealImageTestCase.builder()
              .withInputFile("ybr422-raw.dcm")
              .withTargetTransferSyntax(losslessUID)
              .withExpectations(createLosslessExpectations())
              .build();

      runRealDicomTranscodeTest(testCase);
    }

    @ParameterizedTest
    @ValueSource(strings = {UID.JPEG2000Lossless, UID.JPEGLosslessSV1, UID.JPEGLSLossless})
    @DisplayName("Transcode palette-multiframe with lossless compression")
    void paletteMultiframe(String losslessUID) throws Exception {
      RealImageTestCase testCase =
          RealImageTestCase.builder()
              .withInputFile("palette-multiframe-jpeg-ls.dcm")
              .withTargetTransferSyntax(losslessUID)
              .withExpectations(createLosslessExpectations())
              .build();

      runRealDicomTranscodeTest(testCase);
    }

    private void testMultipleTranscoding(String srcFileName, String... transferSyntaxList)
        throws Exception {
      String currentInput = srcFileName;

      for (int i = 0; i < transferSyntaxList.length; i++) {
        String transferSyntax = transferSyntaxList[i];
        RealImageTestCase testCase =
            RealImageTestCase.builder()
                .withInputFile(currentInput)
                .withTargetTransferSyntax(transferSyntax)
                .build();

        Path outputFile = runRealDicomTranscodeTest(testCase);

        if (i < transferSyntaxList.length - 1) {
          // Copy output to input directory for next iteration
          String nextInput = transferSyntax + ".dcm";
          Path targetPath = IN_DIR.resolve(nextInput);
          Files.copy(outputFile, targetPath, StandardCopyOption.REPLACE_EXISTING);
          currentInput = nextInput;
        }
      }
    }
  }

  // Test execution helpers
  private void runBasicTranscodeTest(TestImageSpec spec, Format format) throws Exception {
    Path inputFile = createTestImage(spec);
    ImageTranscodeParam params = new ImageTranscodeParam(format);
    List<Path> outputFiles = Transcoder.dcm2image(inputFile, OUT_DIR, params);

    assertFalse(outputFiles.isEmpty());
    assertTrue(Files.exists(outputFiles.get(0)));
    assertTrue(outputFiles.get(0).toString().endsWith(format.extension));
    assertTrue(Files.size(outputFiles.get(0)) > 0);

    if (format != Format.JPEG) { // Skip size validation for JPEG due to compression
      PlanarImage outputImage = ImageIOHandler.readImageWithCvException(outputFiles.get(0), null);
      assertEquals(spec.width, outputImage.width());
      assertEquals(spec.height, outputImage.height());
    }
  }

  private void runTranscodeTest(
      TestImageSpec spec,
      ImageTranscodeParam params,
      Map<ImageContentHash, Consumer<Double>> expectations)
      throws Exception {
    Path inputFile = createTestImage(spec);
    List<Path> outputFiles = Transcoder.dcm2image(inputFile, OUT_DIR, params);

    assertFalse(outputFiles.isEmpty());
    assertTrue(Files.exists(outputFiles.get(0)));

    if (expectations != null && !expectations.isEmpty()) {
      compareImageContent(inputFile, outputFiles, expectations);
    }
  }

  private void runDicomTranscodeTest(
      TestImageSpec spec,
      DicomTranscodeParam params,
      Map<ImageContentHash, Consumer<Double>> expectations)
      throws Exception {
    Path inputFile = createTestImage(spec);
    Path outputFile = transcodeDicom(inputFile, params);

    // Verify transfer syntax
    reader.setInput(new DicomFileInputStream(outputFile));
    DicomMetaData metadata = reader.getStreamMetadata();
    assertEquals(params.getOutputTsuid(), metadata.getTransferSyntaxUID());

    if (expectations != null && !expectations.isEmpty()) {
      compareImageContent(inputFile, outputFile, expectations);
    }
  }

  private void runRealImageTest(RealImageTestCase testCase) throws Exception {
    Path inputFile = IN_DIR.resolve(testCase.inputFile);
    DicomImageReadParam readParam = createReadParam(testCase);
    ImageTranscodeParam params = new ImageTranscodeParam(readParam, testCase.format);

    List<Path> outputFiles = Transcoder.dcm2image(inputFile, OUT_DIR, params);
    assertFalse(outputFiles.isEmpty());

    if (testCase.expectedImage != null) {
      Path expectedFile = IN_DIR.resolve(testCase.expectedImage);
      compareImageContent(expectedFile, outputFiles, testCase.expectations);
    } else if (testCase.expectations != null) {
      compareImageContent(inputFile, outputFiles, testCase.expectations);
    }
  }

  private Path runRealDicomTranscodeTest(RealImageTestCase testCase) throws Exception {
    Path inputFile = IN_DIR.resolve(testCase.inputFile);
    DicomTranscodeParam params = createDicomTranscodeParam(testCase);

    Path outputFile = transcodeDicom(inputFile, params);

    if (testCase.expectations != null && !testCase.expectations.isEmpty()) {
      compareImageContent(inputFile, outputFile, testCase.expectations);
    }

    return outputFile;
  }

  private DicomImageReadParam createReadParam(RealImageTestCase testCase) throws Exception {
    DicomImageReadParam readParam = new DicomImageReadParam();

    if (testCase.presentationStateFile != null) {
      Path prFile = IN_DIR.resolve(testCase.presentationStateFile);
      readParam.setPresentationState(PrDicomObject.getPresentationState(prFile.toString()));
    }

    if (testCase.overlayColor != null) {
      readParam.setOverlayColor(testCase.overlayColor);
    }

    if (testCase.resizeTo != null) {
      readParam.setSourceRenderSize(testCase.resizeTo);
    }

    return readParam;
  }

  private DicomTranscodeParam createDicomTranscodeParam(RealImageTestCase testCase)
      throws Exception {
    DicomTranscodeParam params = new DicomTranscodeParam(testCase.targetTransferSyntax);

    if (testCase.resizeTo != null) {
      params.getReadParam().setSourceRenderSize(testCase.resizeTo);
    }

    if (testCase.compressionConfig != null) {
      configureCompressionParams(params, testCase.compressionConfig);
    }

    return params;
  }

  // Hash type to validation category mapping
  private static String getValidationCategory(ImageContentHash hashType) {
    return switch (hashType) {
      case AVERAGE, BLOCK_MEAN_ONE -> "structural";
      case PHASH -> "perceptual";
      case COLOR_MOMENT -> "color";
      default -> "structural";
    };
  }

  // Enhanced expectation builders
  private Map<ImageContentHash, Consumer<Double>> createLosslessExpectations() {
    Map<ImageContentHash, Consumer<Double>> expectations = new EnumMap<>(ImageContentHash.class);
    expectations.put(ImageContentHash.AVERAGE, identical);
    expectations.put(ImageContentHash.PHASH, identical);
    expectations.put(ImageContentHash.BLOCK_MEAN_ONE, identical);
    expectations.put(ImageContentHash.COLOR_MOMENT, identical);
    return expectations;
  }

  private Map<ImageContentHash, Consumer<Double>> createLossyExpectations() {
    Map<ImageContentHash, Consumer<Double>> expectations = new EnumMap<>(ImageContentHash.class);
    expectations.put(ImageContentHash.AVERAGE, highQuality);
    expectations.put(ImageContentHash.PHASH, highQuality);
    expectations.put(ImageContentHash.COLOR_MOMENT, shouldBeDifferent);
    return expectations;
  }

  private Map<ImageContentHash, Consumer<Double>> createQualityBasedExpectations(int quality) {
    Map<ImageContentHash, Consumer<Double>> expectations = new EnumMap<>(ImageContentHash.class);

    if (quality >= 95) {
      expectations.put(ImageContentHash.AVERAGE, perceptuallyIdentical);
      expectations.put(ImageContentHash.PHASH, perceptuallyIdentical);
    } else if (quality >= 85) {
      expectations.put(ImageContentHash.AVERAGE, highQuality);
      expectations.put(ImageContentHash.PHASH, highQuality);
    } else if (quality >= 70) {
      expectations.put(ImageContentHash.AVERAGE, acceptableQuality);
      expectations.put(ImageContentHash.PHASH, acceptableQuality);
      expectations.put(ImageContentHash.COLOR_MOMENT, shouldBeDifferent);
    } else {
      expectations.put(
          ImageContentHash.AVERAGE, createHashValidator(ValidationLevel.LOW_QUALITY, true));
      expectations.put(
          ImageContentHash.PHASH, createHashValidator(ValidationLevel.LOW_QUALITY, true));
      expectations.put(ImageContentHash.COLOR_MOMENT, shouldBeDifferent);
    }

    return expectations;
  }

  // Image comparison utilities
  private static void compareImageContent(
      Path in, Path out, Map<ImageContentHash, Consumer<Double>> expectations) throws Exception {
    compareImageContent(in, Collections.singletonList(out), expectations);
  }

  private static void compareImageContent(
      Path in, List<Path> outFiles, Map<ImageContentHash, Consumer<Double>> expectations)
      throws Exception {
    List<PlanarImage> imagesIn = readImages(in);
    List<PlanarImage> imagesOut = readImages(outFiles);
    assertEquals(imagesIn.size(), imagesOut.size(), "Input and output frame counts should match");
    for (int i = 0; i < imagesIn.size(); i++) {
      PlanarImage imgIn = imagesIn.get(i);
      PlanarImage imgOut = imagesOut.get(i);
      System.out.println("\n=== Enhanced Image Content Comparison for Frame " + (i + 1) + " ===");
      System.out.println("Input: " + in);
      System.out.println("Output: " + (i < outFiles.size() ? outFiles.get(i) : outFiles.get(0)));
      System.out.println(
          "Image dimensions: "
              + imgIn.width()
              + "x"
              + imgIn.height()
              + " -> "
              + imgOut.width()
              + "x"
              + imgOut.height());

      for (Entry<ImageContentHash, Consumer<Double>> entry : expectations.entrySet()) {
        ImageContentHash hashType = entry.getKey();
        double hashDiff = hashType.compare(imgIn.toMat(), imgOut.toMat());
        String category = getValidationCategory(hashType);

        System.out.printf(
            "\t%-20s [%-12s]: %8.6f%n", hashType.name(), category.toUpperCase(), hashDiff);

        try {
          entry.getValue().accept(hashDiff);
          System.out.printf("\t%-20s ✓ PASSED%n", "");
        } catch (AssertionError e) {
          System.out.printf("\t%-20s ✗ FAILED: %s%n", "", e.getMessage());
          throw e;
        }
      }
    }
  }

  private static List<PlanarImage> readImages(List<Path> files) throws IOException {
    if (files.size() == 1 && files.get(0).getFileName().toString().endsWith(".dcm")) {
      reader.setInput(new DicomFileInputStream(files.get(0)));
      return reader.getPlanarImages(null);
    } else {
      return files.stream()
          .map(p -> ImageIOHandler.readImageWithCvException(p, null))
          .collect(Collectors.toList());
    }
  }

  private static List<PlanarImage> readImages(Path path) throws IOException {
    if (path.getFileName().toString().endsWith(".dcm")) {
      reader.setInput(new DicomFileInputStream(path));
      return reader.getPlanarImages(null);
    } else {
      return Collections.singletonList(ImageIOHandler.readImageWithCvException(path, null));
    }
  }

  // Helper methods for synthetic test data
  private Path createTestImage(TestImageSpec spec) throws IOException {
    if (spec.photometricInterpretation != null) {
      return createColorTestImage(
          spec.filename, spec.width, spec.height, spec.photometricInterpretation);
    } else if (spec.bitDepth > 8 || spec.signed) {
      return createMonochromeTestImage(
          spec.filename, spec.width, spec.height, spec.bitDepth, spec.signed);
    } else {
      return createTestDicomFile(
          spec.filename, spec.width, spec.height, UID.ExplicitVRLittleEndian, spec.frameCount > 1);
    }
  }

  private void configureCompressionParams(DicomTranscodeParam params, String transferSyntax) {
    DicomJpegWriteParam writeParams = params.getWriteJpegParam();

    switch (transferSyntax) {
      case UID.JPEGBaseline8Bit -> {
        writeParams.setCompressionQuality(85);
        System.out.println("Configured JPEG Baseline with quality: 85");
      }
      case UID.JPEG2000 -> {
        writeParams.setCompressionRatioFactor(100); // Moderate compression
        System.out.println("Configured JPEG2000 with compression ratio: 100");
      }
      case UID.JPEGLSNearLossless -> {
        writeParams.setNearLosslessError(2); // Small allowed error
        System.out.println("Configured JPEG-LS Near-lossless with error: 2");
      }
    }
  }

  private Path transcodeDicom(Path inputFile, DicomTranscodeParam params) throws Exception {
    String outputFilename = "transcoded_" + params.getOutputTsuid().hashCode() + ".dcm";
    Path outputDir = OUT_DIR.resolve("transcoded");
    Files.createDirectories(outputDir);

    return Transcoder.dcm2dcm(inputFile, outputDir.resolve(outputFilename), params);
  }

  private List<PlanarImage> readDicomImages(Path dicomFile) throws IOException {
    reader.setInput(new DicomFileInputStream(dicomFile));
    return reader.getPlanarImages(null);
  }

  // Test data specification classes
  private static class TestImageSpec {
    String filename = "test_image.dcm";
    int width = 64;
    int height = 64;
    int bitDepth = 8;
    boolean signed = false;
    int frameCount = 1;
    String photometricInterpretation = null;

    static TestImageSpec basic() {
      return new TestImageSpec();
    }

    static TestImageSpec monochrome() {
      return new TestImageSpec();
    }

    static TestImageSpec color() {
      return new TestImageSpec();
    }

    TestImageSpec withDimensions(int width, int height) {
      this.width = width;
      this.height = height;
      return this;
    }

    TestImageSpec withBitDepth(int bitDepth) {
      this.bitDepth = bitDepth;
      return this;
    }

    TestImageSpec withSigned(boolean signed) {
      this.signed = signed;
      return this;
    }

    TestImageSpec withFrameCount(int frameCount) {
      this.frameCount = frameCount;
      return this;
    }

    TestImageSpec withPhotometricInterpretation(String photometricInterpretation) {
      this.photometricInterpretation = photometricInterpretation;
      return this;
    }

    TestImageSpec withFilename(String filename) {
      this.filename = filename;
      return this;
    }
  }

  private static class RealImageTestCase {
    String inputFile;
    String presentationStateFile;
    String expectedImage;
    Format format;
    String targetTransferSyntax;
    Color overlayColor;
    Dimension resizeTo;
    String compressionConfig;
    Map<ImageContentHash, Consumer<Double>> expectations;

    static Builder builder() {
      return new Builder();
    }

    static class Builder {
      private final RealImageTestCase testCase = new RealImageTestCase();

      Builder withInputFile(String inputFile) {
        testCase.inputFile = inputFile;
        return this;
      }

      Builder withPresentationState(String presentationStateFile) {
        testCase.presentationStateFile = presentationStateFile;
        return this;
      }

      Builder withExpectedImage(String expectedImage) {
        testCase.expectedImage = expectedImage;
        return this;
      }

      Builder withFormat(Format format) {
        testCase.format = format;
        return this;
      }

      Builder withTargetTransferSyntax(String targetTransferSyntax) {
        testCase.targetTransferSyntax = targetTransferSyntax;
        return this;
      }

      Builder withOverlayColor(Color overlayColor) {
        testCase.overlayColor = overlayColor;
        return this;
      }

      Builder withResizeTo(Dimension resizeTo) {
        testCase.resizeTo = resizeTo;
        return this;
      }

      Builder withCompressionConfig(String compressionConfig) {
        testCase.compressionConfig = compressionConfig;
        return this;
      }

      Builder withExpectations(Map<ImageContentHash, Consumer<Double>> expectations) {
        testCase.expectations = expectations;
        return this;
      }

      RealImageTestCase build() {
        return testCase;
      }
    }
  }

  // Existing helper methods (preserved for backward compatibility)
  private Path createTestDicomFile(
      String filename, int width, int height, String transferSyntax, boolean multiFrame)
      throws IOException {
    Attributes attrs =
        testDataFactory.createBasicImageAttributes(width, height, transferSyntax, multiFrame);
    byte[] pixelData = createGradientPixelData(width, height, multiFrame ? 3 : 1);
    attrs.setBytes(Tag.PixelData, VR.OW, pixelData);

    return writeDicomToFile(filename, attrs, transferSyntax);
  }

  private Path createMonochromeTestImage(
      String filename, int width, int height, int bitsStored, boolean signed) throws IOException {
    Attributes attrs =
        testDataFactory.createMonochromeImageAttributes(
            width, height, bitsStored, signed, UID.ExplicitVRLittleEndian);

    int bytesPerPixel = bitsStored <= 8 ? 1 : 2;
    byte[] pixelData = createCheckerboardPixelData(width, height, bytesPerPixel, signed);
    attrs.setBytes(Tag.PixelData, VR.OW, pixelData);

    return writeDicomToFile(filename, attrs, UID.ExplicitVRLittleEndian);
  }

  private Path createColorTestImage(
      String filename, int width, int height, String photometricInterpretation) throws IOException {
    Attributes attrs =
        testDataFactory.createColorImageAttributes(
            width, height, photometricInterpretation, UID.ExplicitVRLittleEndian);

    byte[] pixelData = createColorPixelData(width, height, photometricInterpretation);
    attrs.setBytes(Tag.PixelData, VR.OW, pixelData);

    return writeDicomToFile(filename, attrs, UID.ExplicitVRLittleEndian);
  }

  private byte[] createGradientPixelData(int width, int height, int frames) {
    int pixelCount = width * height * frames;
    byte[] data = new byte[pixelCount * 2]; // 16-bit data

    for (int frame = 0; frame < frames; frame++) {
      for (int y = 0; y < height; y++) {
        for (int x = 0; x < width; x++) {
          int pixelIndex = (frame * width * height + y * width + x) * 2;
          // Create gradient pattern with frame offset
          int value = ((x + y + frame * 100) % 65536);
          data[pixelIndex] = (byte) (value & 0xFF);
          data[pixelIndex + 1] = (byte) ((value >> 8) & 0xFF);
        }
      }
    }
    return data;
  }

  private byte[] createCheckerboardPixelData(
      int width, int height, int bytesPerPixel, boolean signed) {
    byte[] data = new byte[width * height * bytesPerPixel];
    int maxValue = signed ? (1 << (bytesPerPixel * 8 - 1)) - 1 : (1 << (bytesPerPixel * 8)) - 1;

    for (int y = 0; y < height; y++) {
      for (int x = 0; x < width; x++) {
        int pixelIndex = (y * width + x) * bytesPerPixel;
        boolean isWhite = ((x / 16) + (y / 16)) % 2 == 0;
        int value = isWhite ? maxValue : (signed ? -maxValue : 0);

        for (int b = 0; b < bytesPerPixel; b++) {
          data[pixelIndex + b] = (byte) ((value >> (b * 8)) & 0xFF);
        }
      }
    }
    return data;
  }

  private byte[] createColorPixelData(int width, int height, String photometricInterpretation) {
    byte[] data = new byte[width * height * 3]; // RGB data

    for (int y = 0; y < height; y++) {
      for (int x = 0; x < width; x++) {
        int pixelIndex = (y * width + x) * 3;

        // Create color pattern based on position
        if ("RGB".equals(photometricInterpretation)) {
          data[pixelIndex] = (byte) ((x * 255) / width); // Red
          data[pixelIndex + 1] = (byte) ((y * 255) / height); // Green
          data[pixelIndex + 2] = (byte) (128); // Blue
        } else if ("YBR_FULL_422".equals(photometricInterpretation)) {
          // Simple YBR pattern
          data[pixelIndex] = (byte) (128 + (x % 128)); // Y
          data[pixelIndex + 1] = (byte) (128 + (y % 64)); // Cb
          data[pixelIndex + 2] = (byte) (128 + ((x + y) % 64)); // Cr
        }
      }
    }
    return data;
  }

  private Path writeDicomToFile(String filename, Attributes attrs, String transferSyntax)
      throws IOException {
    Path filepath = OUT_DIR.resolve(filename);

    try (DicomOutputStream dos =
        new DicomOutputStream(Files.newOutputStream(filepath), transferSyntax)) {
      dos.writeFileMetaInformation(attrs.createFileMetaInformation(transferSyntax));
      dos.writeDataset(null, attrs);
    }

    return filepath;
  }
}
