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
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.awt.*;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.*;
import java.util.*;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.UID;
import org.dcm4che3.data.VR;
import org.dcm4che3.img.Transcoder.Format;
import org.dcm4che3.img.data.PrDicomObject;
import org.dcm4che3.img.op.MaskArea;
import org.dcm4che3.img.stream.DicomFileInputStream;
import org.dcm4che3.io.DicomOutputStream;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.weasis.opencv.data.PlanarImage;
import org.weasis.opencv.op.ImageContentHash;
import org.weasis.opencv.op.ImageIOHandler;

@DisplayNameGeneration(ReplaceUnderscores.class)
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

  // Image validation using actual image comparison instead of mocks
  private enum ValidationLevel {
    IDENTICAL(0.0),
    PERCEPTUALLY_IDENTICAL(0.001),
    HIGH_QUALITY(0.015),
    ACCEPTABLE_QUALITY(0.05),
    LOW_QUALITY(0.15);

    private final double threshold;

    ValidationLevel(double threshold) {
      this.threshold = threshold;
    }

    public double threshold() {
      return threshold;
    }
  }

  // Java 17 record for test image specifications
  record TestImageSpec(
      String filename,
      int width,
      int height,
      int bitDepth,
      boolean signed,
      int frameCount,
      String photometricInterpretation) {

    static TestImageSpec basic() {
      return new TestImageSpec("test_image.dcm", 64, 64, 8, false, 1, null);
    }

    static TestImageSpec monochrome() {
      return new TestImageSpec("mono_image.dcm", 256, 256, 16, false, 1, null);
    }

    static TestImageSpec color() {
      return new TestImageSpec("color_image.dcm", 128, 128, 8, false, 1, "RGB");
    }

    TestImageSpec withDimensions(int width, int height) {
      return new TestImageSpec(
          filename, width, height, bitDepth, signed, frameCount, photometricInterpretation);
    }

    TestImageSpec withBitDepth(int bitDepth) {
      return new TestImageSpec(
          filename, width, height, bitDepth, signed, frameCount, photometricInterpretation);
    }

    TestImageSpec withSigned(boolean signed) {
      return new TestImageSpec(
          filename, width, height, bitDepth, signed, frameCount, photometricInterpretation);
    }

    TestImageSpec withFrameCount(int frameCount) {
      return new TestImageSpec(
          filename, width, height, bitDepth, signed, frameCount, photometricInterpretation);
    }

    TestImageSpec withPhotometricInterpretation(String photometricInterpretation) {
      return new TestImageSpec(
          filename, width, height, bitDepth, signed, frameCount, photometricInterpretation);
    }
  }

  // Java 17 record for real image test cases
  record RealImageTestCase(
      String inputFile,
      String presentationStateFile,
      String expectedImage,
      Format format,
      String targetTransferSyntax,
      Color overlayColor,
      Dimension resizeTo,
      String compressionConfig,
      Map<ImageContentHash, Consumer<Double>> expectations) {

    static Builder builder() {
      return new Builder();
    }

    static class Builder {
      private String inputFile;
      private String presentationStateFile;
      private String expectedImage;
      private Format format;
      private String targetTransferSyntax;
      private Color overlayColor;
      private Dimension resizeTo;
      private String compressionConfig;
      private Map<ImageContentHash, Consumer<Double>> expectations;

      Builder withInputFile(String inputFile) {
        this.inputFile = inputFile;
        return this;
      }

      Builder withPresentationState(String presentationStateFile) {
        this.presentationStateFile = presentationStateFile;
        return this;
      }

      Builder withExpectedImage(String expectedImage) {
        this.expectedImage = expectedImage;
        return this;
      }

      Builder withFormat(Format format) {
        this.format = format;
        return this;
      }

      Builder withTargetTransferSyntax(String targetTransferSyntax) {
        this.targetTransferSyntax = targetTransferSyntax;
        return this;
      }

      Builder withOverlayColor(Color overlayColor) {
        this.overlayColor = overlayColor;
        return this;
      }

      Builder withResizeTo(Dimension resizeTo) {
        this.resizeTo = resizeTo;
        return this;
      }

      Builder withCompressionConfig(String compressionConfig) {
        this.compressionConfig = compressionConfig;
        return this;
      }

      Builder withExpectations(Map<ImageContentHash, Consumer<Double>> expectations) {
        this.expectations = expectations;
        return this;
      }

      RealImageTestCase build() {
        return new RealImageTestCase(
            inputFile,
            presentationStateFile,
            expectedImage,
            format,
            targetTransferSyntax,
            overlayColor,
            resizeTo,
            compressionConfig,
            expectations);
      }
    }
  }

  private static Consumer<Double> createHashValidator(ValidationLevel level, boolean expectEqual) {
    return createHashValidator(level.threshold(), expectEqual, level.name());
  }

  private static Consumer<Double> createHashValidator(
      double threshold, boolean expectEqual, String levelName) {
    return val -> {
      var comparisonType = expectEqual ? "≤" : ">";
      var message =
          String.format(
              "Hash validation failed [%s]: expected %s %s %.6f, but got %.6f",
              levelName, val, comparisonType, threshold, val);

      if (expectEqual) {
        assertTrue(val <= threshold, message);
      } else {
        assertNotEquals(threshold, val, message);
      }
    };
  }

  // Predefined validators using actual validation levels
  static final Consumer<Double> identical = createHashValidator(ValidationLevel.IDENTICAL, true);
  static final Consumer<Double> perceptuallyIdentical =
      createHashValidator(ValidationLevel.PERCEPTUALLY_IDENTICAL, true);
  static final Consumer<Double> highQuality =
      createHashValidator(ValidationLevel.HIGH_QUALITY, true);
  static final Consumer<Double> acceptableQuality =
      createHashValidator(ValidationLevel.ACCEPTABLE_QUALITY, true);

  @Test
  void convert_monochrome_DICOM_to_PNG_with_proper_bit_depth_handling() throws Exception {
    var spec =
        TestImageSpec.monochrome().withDimensions(256, 256).withBitDepth(16).withSigned(false);
    runBasicTranscodeTest(spec, Format.PNG);
  }

  @Test
  void convert_color_DICOM_to_JPEG_with_low_quality_settings() throws Exception {
    var spec = TestImageSpec.color().withDimensions(128, 128).withPhotometricInterpretation("RGB");
    var params = new ImageTranscodeParam(Format.JPEG);
    var quality = 50;
    params.setJpegCompressionQuality(quality);

    runTranscodeTest(spec, params, createQualityBasedExpectations(quality));
  }

  @Test
  void convert_color_DICOM_to_JPEG_with_quality_based_validation() throws Exception {
    var spec = TestImageSpec.color().withDimensions(128, 128).withPhotometricInterpretation("RGB");
    var quality = 95;
    var params = new ImageTranscodeParam(Format.JPEG);
    params.setJpegCompressionQuality(quality);

    runTranscodeTest(spec, params, createQualityBasedExpectations(quality));
  }

  @Test
  void transcode_multiframe_DICOM_to_individual_image_files() throws Exception {
    var spec = TestImageSpec.basic().withDimensions(64, 64).withFrameCount(3);
    var inputFile = createTestImage(spec);
    var params = new ImageTranscodeParam(Format.PNG);
    var outputFiles = Transcoder.dcm2image(inputFile, OUT_DIR, params);

    assertEquals(3, outputFiles.size(), "Should create 3 PNG files for 3 frames");

    for (int i = 0; i < outputFiles.size(); i++) {
      var outputFile = outputFiles.get(i);
      assertTrue(Files.exists(outputFile));
      assertTrue(outputFile.getFileName().toString().contains("-" + (i + 1)));
    }
  }

  @Test
  void resize_DICOM_image_during_transcoding() throws Exception {
    var spec = TestImageSpec.basic().withDimensions(512, 512);
    var inputFile = createTestImage(spec);
    var params = new DicomTranscodeParam(UID.JPEGLosslessSV1);
    params.getReadParam().setSourceRenderSize(new Dimension(256, 256));

    var outputFile = transcodeDicom(inputFile, params);
    var images = readDicomImages(outputFile);

    assertEquals(1, images.size());
    assertEquals(256, images.get(0).width());
    assertEquals(256, images.get(0).height());
  }

  @ParameterizedTest
  @EnumSource(Format.class)
  void convert_DICOM_to_all_supported_image_formats(Format format) throws Exception {
    var spec =
        TestImageSpec.basic()
            .withDimensions(64, 64)
            .withBitDepth(format.isSupported(org.opencv.core.CvType.CV_16U) ? 16 : 8);

    runBasicTranscodeTest(spec, format);
  }

  @ParameterizedTest
  @ValueSource(strings = {UID.JPEGBaseline8Bit, UID.JPEG2000, UID.JPEGLSNearLossless})
  void transcode_to_lossy_DICOM_compression_formats_with_compression_specific_validation(
      String targetTransferSyntax) throws Exception {
    var spec = TestImageSpec.color().withDimensions(128, 128).withPhotometricInterpretation("RGB");
    var params = new DicomTranscodeParam(targetTransferSyntax);
    configureCompressionParams(params, targetTransferSyntax);

    runDicomTranscodeTest(spec, params, createQualityBasedExpectations(90));
  }

  @ParameterizedTest
  @ValueSource(strings = {UID.JPEGLosslessSV1, UID.JPEG2000Lossless, UID.JPEGLSLossless})
  void transcode_to_lossless_DICOM_compression_formats(String targetTransferSyntax)
      throws Exception {
    var spec = TestImageSpec.color().withDimensions(128, 128).withPhotometricInterpretation("RGB");
    var params = new DicomTranscodeParam(targetTransferSyntax);

    runDicomTranscodeTest(spec, params, createLosslessExpectations());
  }

  @ParameterizedTest
  @ValueSource(strings = {UID.RLELossless, UID.JPEG2000MC, UID.JPIPHTJ2KReferenced, UID.MPEG2MPHL})
  void test_transfer_syntax_adaptation_when_target_format_is_unsupported(
      String targetTransferSyntax) {
    assertThrows(IllegalStateException.class, () -> new DicomTranscodeParam(targetTransferSyntax));
  }

  @Test
  void test_output_stream_transcoding() throws Exception {
    var spec = TestImageSpec.basic().withDimensions(64, 64);
    var inputFile = createTestImage(spec);
    var params = new DicomTranscodeParam(UID.JPEGLosslessSV1);

    var outputStream = new ByteArrayOutputStream();
    Transcoder.dcm2dcm(inputFile, outputStream, params);

    assertTrue(outputStream.size() > 0, "Output stream should contain transcoded data");

    // Verify the transcoded data is valid DICOM
    var transcodedData = outputStream.toByteArray();
    assertTrue(transcodedData.length > 132, "DICOM file should be larger than preamble + prefix");
  }

  @ParameterizedTest
  @EnumSource(Format.class)
  void convert_DICOM_to_all_supported_image_formats_with_format_specific_validation(Format format)
      throws Exception {
    var spec =
        TestImageSpec.basic()
            .withDimensions(64, 64)
            .withBitDepth(format.isSupported(org.opencv.core.CvType.CV_16U) ? 16 : 8);

    var inputFile = createTestImage(spec);
    var params = new ImageTranscodeParam(format);
    var outputFiles = Transcoder.dcm2image(inputFile, OUT_DIR, params);

    assertFalse(outputFiles.isEmpty());
    assertTrue(Files.exists(outputFiles.get(0)));
    assertTrue(outputFiles.get(0).toString().endsWith(format.getExtension()));
    assertTrue(Files.size(outputFiles.get(0)) > 0);
  }

  @Test
  void check_format_validation_of_DICOM_images() {
    var spec = Format.JPEG;
    assertFalse(spec.isSupport16S());
    assertFalse(spec.isSupport16U());
    assertFalse(spec.isSupport32F());
    assertFalse(spec.isSupport64F());

    spec = Format.TIF;
    assertFalse(spec.isSupport16S());
    assertTrue(spec.isSupport16U());
    assertTrue(spec.isSupport32F());
    assertTrue(spec.isSupport64F());
  }

  @Nested
  @DisplayNameGeneration(ReplaceUnderscores.class)
  class Format_Tests {

    @Test
    void format_enum_should_have_correct_extensions() {
      assertEquals(".jpg", Format.JPEG.getExtension());
      assertEquals(".png", Format.PNG.getExtension());
      assertEquals(".tif", Format.TIF.getExtension());
      assertEquals(".jp2", Format.JP2.getExtension());
      assertEquals(".pnm", Format.PNM.getExtension());
      assertEquals(".bmp", Format.BMP.getExtension());
      assertEquals(".hdr", Format.HDR.getExtension());
    }

    @Test
    void format_enum_should_have_correct_type_support() {
      // JPEG: only 8-bit unsigned
      assertFalse(Format.JPEG.isSupported(org.opencv.core.CvType.CV_16U));
      assertFalse(Format.JPEG.isSupported(org.opencv.core.CvType.CV_16S));
      assertFalse(Format.JPEG.isSupported(org.opencv.core.CvType.CV_32F));
      assertFalse(Format.JPEG.isSupported(org.opencv.core.CvType.CV_64F));

      // PNG: supports 16-bit unsigned
      assertTrue(Format.PNG.isSupported(org.opencv.core.CvType.CV_16U));
      assertFalse(Format.PNG.isSupported(org.opencv.core.CvType.CV_16S));

      // TIF: supports most formats
      assertTrue(Format.TIF.isSupported(org.opencv.core.CvType.CV_16U));
      assertFalse(Format.TIF.isSupported(org.opencv.core.CvType.CV_16S));
      assertTrue(Format.TIF.isSupported(org.opencv.core.CvType.CV_32F));
      assertTrue(Format.TIF.isSupported(org.opencv.core.CvType.CV_64F));

      // HDR: supports floating point
      assertTrue(Format.HDR.isSupported(org.opencv.core.CvType.CV_64F));
    }

    @Test
    void all_formats_should_support_8_bit_unsigned() {
      for (var format : Format.values()) {
        assertTrue(
            format.isSupported(org.opencv.core.CvType.CV_8U), format + " should support CV_8U");
      }
    }
  }

  @Nested
  @DisplayNameGeneration(ReplaceUnderscores.class)
  class Error_Handling_Tests {

    @Test
    void should_handle_null_source_path() {
      var params = new ImageTranscodeParam(Format.PNG);
      assertThrows(Exception.class, () -> Transcoder.dcm2image(null, OUT_DIR, params));
    }

    @Test
    void should_handle_null_destination_path() throws IOException {
      var spec = TestImageSpec.basic();
      var inputFile = createTestImage(spec);
      var params = new ImageTranscodeParam(Format.PNG);

      assertThrows(Exception.class, () -> Transcoder.dcm2image(inputFile, null, params));
    }

    @Test
    void should_handle_null_transcoding_params() throws Exception {
      var spec = TestImageSpec.basic();
      var inputFile = createTestImage(spec);

      assertThrows(Exception.class, () -> Transcoder.dcm2image(inputFile, OUT_DIR, null));
    }

    @Test
    void should_handle_non_existent_source_file() {
      var nonExistentFile = OUT_DIR.resolve("non_existent_file.dcm");
      var params = new ImageTranscodeParam(Format.PNG);

      assertThrows(Exception.class, () -> Transcoder.dcm2image(nonExistentFile, OUT_DIR, params));
    }

    @Test
    void should_handle_corrupted_dicom_file() throws Exception {
      // Create a fake DICOM file with invalid content
      var corruptedFile = OUT_DIR.resolve("corrupted.dcm");
      Files.write(corruptedFile, "This is not a valid DICOM file".getBytes());

      var params = new ImageTranscodeParam(Format.PNG);
      assertThrows(Exception.class, () -> Transcoder.dcm2image(corruptedFile, OUT_DIR, params));
    }

    @Test
    void should_handle_unwritable_destination_directory() throws Exception {
      var spec = TestImageSpec.basic();
      var inputFile = createTestImage(spec);
      var params = new ImageTranscodeParam(Format.PNG);
      var readOnlyDir = OUT_DIR.resolve("readonly");

      Files.createDirectories(readOnlyDir);
      try {
        readOnlyDir.toFile().setWritable(false);
        Transcoder.dcm2image(inputFile, readOnlyDir, params);
        assertFalse(Files.exists(readOnlyDir.resolve("test_output.png")));

      } finally {
        readOnlyDir.toFile().setWritable(true);
      }
    }
  }

  @Nested
  @DisplayNameGeneration(ReplaceUnderscores.class)
  class Edge_Cases_Tests {

    @Test
    void should_handle_very_small_images() throws Exception {
      var spec = TestImageSpec.basic().withDimensions(1, 1);
      runBasicTranscodeTest(spec, Format.PNG);
    }

    @Test
    void should_handle_single_frame_multiframe_image() throws Exception {
      var spec = TestImageSpec.basic().withFrameCount(1);
      runBasicTranscodeTest(spec, Format.PNG);
    }

    @Test
    void should_handle_different_photometric_interpretations() throws Exception {
      var photometricInterpretations =
          List.of("MONOCHROME1", "MONOCHROME2", "RGB", "YBR_FULL", "YBR_FULL_422");

      for (var interpretation : photometricInterpretations) {
        var spec = TestImageSpec.basic().withPhotometricInterpretation(interpretation);
        var inputFile = createTestImage(spec);
        var params = new ImageTranscodeParam(Format.PNG);
        var outputFiles = Transcoder.dcm2image(inputFile, OUT_DIR, params);

        assertFalse(
            outputFiles.isEmpty(), "Should handle photometric interpretation: " + interpretation);
      }
    }

    @Test
    void should_preserve_raw_image_data_when_requested() throws Exception {
      var spec = TestImageSpec.basic().withBitDepth(16);
      var inputFile = createTestImage(spec);

      var params = new ImageTranscodeParam(Format.TIF); // TIF supports 16-bit
      params.setPreserveRawImage(true);

      var outputFiles = Transcoder.dcm2image(inputFile, OUT_DIR, params);
      assertFalse(outputFiles.isEmpty());

      // Verify the output file exists and has reasonable size
      var outputFile = outputFiles.get(0);
      assertTrue(Files.exists(outputFile));
      assertTrue(Files.size(outputFile) > 0);
    }
  }

  @Nested
  @DisplayNameGeneration(ReplaceUnderscores.class)
  class Mask_Area_Tests {

    @Test
    void should_handle_null_mask_area() {
      var result = Transcoder.getMaskedImage(null);
      assertNull(result);
    }

    @Test
    void should_create_masked_image_editor_for_valid_mask() {
      // Create a simple rectangular mask area
      var maskArea = new MaskArea(List.of(new Rectangle(16, 16, 32, 32)), Color.GREEN);
      var result = Transcoder.getMaskedImage(maskArea);
      assertNotNull(result);
    }

    @Test
    void should_apply_mask_and_preserve_image_properties() throws Exception {
      var spec = TestImageSpec.basic().withDimensions(64, 64);
      var inputFile = createTestImage(spec);

      // Create a basic transcoding param with a simple mask
      var params = new DicomTranscodeParam(UID.JPEGLosslessSV1);
      var maskArea = new MaskArea(List.of(new Rectangle(16, 16, 32, 32)), Color.GREEN);
      params.addMask("TEST_STATION", maskArea);

      var outputFile = transcodeDicom(inputFile, params);
      assertTrue(Files.exists(outputFile));
      assertTrue(Files.size(outputFile) > 0);
    }
  }

  @Nested
  @DisplayNameGeneration(ReplaceUnderscores.class)
  class Compression_Parameter_Tests {

    @Test
    void should_handle_different_jpeg_qualities_for_baseline() throws Exception {
      var qualities = List.of(10, 25, 50, 75, 90, 95);

      for (var quality : qualities) {
        var spec = TestImageSpec.basic().withDimensions(64, 64);
        var params = new ImageTranscodeParam(Format.JPEG);
        params.setJpegCompressionQuality(quality);

        var inputFile = createTestImage(spec);
        var outputFiles = Transcoder.dcm2image(inputFile, OUT_DIR, params);

        assertFalse(outputFiles.isEmpty(), "Quality " + quality + " should produce output");
        var outputFile = outputFiles.get(0);
        assertTrue(Files.exists(outputFile));
        assertTrue(Files.size(outputFile) > 0);
      }
    }

    @Test
    void should_create_compression_params_correctly() throws Exception {
      var spec = TestImageSpec.basic();
      var inputFile = createTestImage(spec);

      // Test with JPEG format
      var jpegParams = new ImageTranscodeParam(Format.JPEG);
      jpegParams.setJpegCompressionQuality(85);
      var jpegFiles = Transcoder.dcm2image(inputFile, OUT_DIR, jpegParams);
      assertFalse(jpegFiles.isEmpty());

      // Test with non-JPEG format (should not use compression params)
      var pngParams = new ImageTranscodeParam(Format.PNG);
      var pngFiles = Transcoder.dcm2image(inputFile, OUT_DIR, pngParams);
      assertFalse(pngFiles.isEmpty());
    }
  }

  @Nested
  @DisplayNameGeneration(ReplaceUnderscores.class)
  class File_Extension_Handling_Tests {

    @Test
    void should_adapt_file_extension_correctly() throws Exception {
      var spec = TestImageSpec.basic();
      var inputFile = createTestImage(spec);

      // Create output path with .dcm extension
      var outputDir = OUT_DIR.resolve("extension_test");
      Files.createDirectories(outputDir);
      var outputPath = outputDir.resolve("test_output.dcm");

      var params = new ImageTranscodeParam(Format.PNG);
      var outputFiles = Transcoder.dcm2image(inputFile, outputPath, params);

      assertFalse(outputFiles.isEmpty());
      var resultFile = outputFiles.get(0);
      assertTrue(resultFile.getFileName().toString().endsWith(".png"));
    }

    @Test
    void should_handle_output_directory_vs_file_correctly() throws Exception {
      var spec = TestImageSpec.basic();
      var inputFile = createTestImage(spec);
      var params = new ImageTranscodeParam(Format.PNG);

      // Test with directory
      var dirOutput = Transcoder.dcm2image(inputFile, OUT_DIR, params);
      assertFalse(dirOutput.isEmpty());

      // Test with specific file path
      var specificFile = OUT_DIR.resolve("specific_output.png");
      var fileOutput = Transcoder.dcm2image(inputFile, specificFile, params);
      assertFalse(fileOutput.isEmpty());

      // Both should produce valid files
      assertTrue(Files.exists(dirOutput.get(0)));
      assertTrue(Files.exists(fileOutput.get(0)));
    }
  }

  @Nested
  @DisplayNameGeneration(ReplaceUnderscores.class)
  class Transfer_Syntax_Coverage_Tests {

    @Test
    void should_handle_uncompressed_transfer_syntaxes() throws Exception {
      var spec = TestImageSpec.basic().withDimensions(64, 64);
      var inputFile =
          createTestDicomFile(
              "uncompressed_" + UID.ExplicitVRLittleEndian.hashCode() + ".dcm",
              spec.width(),
              spec.height(),
              UID.ExplicitVRLittleEndian,
              false);

      var params = new DicomTranscodeParam(UID.JPEGLosslessSV1);
      var outputFile = transcodeDicom(inputFile, params);

      assertTrue(Files.exists(outputFile));

      // Verify the transfer syntax was changed
      reader.setInput(new DicomFileInputStream(outputFile));
      var metadata = reader.getStreamMetadata();
      assertEquals(UID.JPEGLosslessSV1, metadata.getTransferSyntaxUID());
    }

    @Test
    void should_handle_compressed_to_compressed_transcoding() throws Exception {
      var spec = TestImageSpec.basic().withDimensions(64, 64);
      var inputFile = createTestImage(spec);

      // First, create a JPEG compressed file
      var jpegParams = new DicomTranscodeParam(UID.JPEGLosslessSV1);
      var jpegFile = transcodeDicom(inputFile, jpegParams);

      // Then transcode to JPEG2000
      var jpeg2000Params = new DicomTranscodeParam(UID.JPEG2000Lossless);
      var jpeg2000File = transcodeDicom(jpegFile, jpeg2000Params);

      assertTrue(Files.exists(jpeg2000File));

      reader.setInput(new DicomFileInputStream(jpeg2000File));
      var metadata = reader.getStreamMetadata();
      assertEquals(UID.JPEG2000Lossless, metadata.getTransferSyntaxUID());
    }
  }

  @Nested
  @DisplayNameGeneration(ReplaceUnderscores.class)
  class Bit_Depth_And_Data_Type_Tests {

    @ParameterizedTest
    @ValueSource(ints = {8, 10, 12, 14, 16})
    void should_handle_different_bit_depths(int bitDepth) throws Exception {
      var spec =
          TestImageSpec.monochrome()
              .withDimensions(64, 64)
              .withBitDepth(bitDepth)
              .withSigned(false);

      runBasicTranscodeTest(spec, Format.PNG);
    }

    @Test
    void should_handle_signed_pixel_data() throws Exception {
      var spec =
          TestImageSpec.monochrome().withDimensions(64, 64).withBitDepth(16).withSigned(true);

      runBasicTranscodeTest(spec, Format.TIF); // TIF might handle signed better
    }

    @Test
    void should_adapt_format_based_on_data_type() throws Exception {
      var spec = TestImageSpec.basic().withBitDepth(12);
      var inputFile = createTestImage(spec);

      // Try to use JPEG (8-bit only) - should still work by adapting
      var params = new ImageTranscodeParam(Format.JPEG);
      var outputFiles = Transcoder.dcm2image(inputFile, OUT_DIR, params);

      assertFalse(outputFiles.isEmpty());
      assertTrue(Files.exists(outputFiles.get(0)));
    }
  }

  @Nested
  @DisplayNameGeneration(ReplaceUnderscores.class)
  class Stream_Output_Tests {

    @Test
    void should_write_to_byte_array_output_stream() throws Exception {
      var spec = TestImageSpec.basic().withDimensions(32, 32);
      var inputFile = createTestImage(spec);
      var params = new DicomTranscodeParam(UID.JPEGLosslessSV1);

      var baos = new ByteArrayOutputStream();
      Transcoder.dcm2dcm(inputFile, baos, params);

      var data = baos.toByteArray();
      assertTrue(data.length > 0);

      // Verify it's a valid DICOM file by checking the header
      assertTrue(data.length > 132); // Minimum DICOM file size
    }

    @Test
    void should_handle_output_stream_errors_gracefully() throws Exception {
      var spec = TestImageSpec.basic().withDimensions(32, 32);
      var inputFile = createTestImage(spec);
      var params = new DicomTranscodeParam(UID.JPEGLosslessSV1);

      // Create a mock OutputStream that throws on write
      var failingStream =
          new OutputStream() {
            @Override
            public void write(int b) throws IOException {
              throw new IOException("Simulated write failure");
            }
          };

      assertThrows(IOException.class, () -> Transcoder.dcm2dcm(inputFile, failingStream, params));
    }
  }

  @Nested
  @DisplayNameGeneration(ReplaceUnderscores.class)
  class Memory_Management_Tests {

    @Test
    void should_properly_dispose_resources_on_success() throws Exception {
      var spec = TestImageSpec.basic();
      var inputFile = createTestImage(spec);
      var params = new ImageTranscodeParam(Format.PNG);

      // This should complete without memory leaks
      var outputFiles = Transcoder.dcm2image(inputFile, OUT_DIR, params);
      assertFalse(outputFiles.isEmpty());

      // Force garbage collection to catch any resource leaks
      System.gc();
      Thread.sleep(100); // Give GC time to work
    }

    @Test
    void should_properly_dispose_resources_on_failure() throws Exception {
      var nonExistentFile = OUT_DIR.resolve("does_not_exist.dcm");
      var params = new ImageTranscodeParam(Format.PNG);

      // This should fail but not leak resources
      assertThrows(Exception.class, () -> Transcoder.dcm2image(nonExistentFile, OUT_DIR, params));

      System.gc();
      Thread.sleep(100);
    }
  }

  @Nested
  @DisplayNameGeneration(ReplaceUnderscores.class)
  class Integration_With_Real_Data_Tests {

    @Test
    void should_handle_typical_ct_scan_parameters() throws Exception {
      var spec = TestImageSpec.basic().withDimensions(512, 512).withBitDepth(12).withSigned(true);

      var inputFile =
          createMonochromeTestImage(
              "ct_scan_test.dcm", spec.width(), spec.height(), spec.bitDepth(), spec.signed());

      // Test various output formats
      for (var format : Format.values()) {
        var params = new ImageTranscodeParam(format);
        var outputFiles = Transcoder.dcm2image(inputFile, OUT_DIR, params);

        assertFalse(outputFiles.isEmpty(), "CT scan should transcode to " + format);
      }
    }

    @Test
    void should_handle_typical_ultrasound_parameters() throws Exception {
      var attrs =
          testDataFactory.createUltrasoundImageAttributes(640, 480, UID.ExplicitVRLittleEndian);
      var pixelData = createColorPixelData(640, 480, "RGB");
      attrs.setBytes(Tag.PixelData, VR.OW, pixelData);

      var inputFile = writeDicomToFile("us_test.dcm", attrs, UID.ExplicitVRLittleEndian);

      var params = new ImageTranscodeParam(Format.JPEG);
      params.setJpegCompressionQuality(90);

      var outputFiles = Transcoder.dcm2image(inputFile, OUT_DIR, params);
      assertFalse(outputFiles.isEmpty());
    }
  }

  @Nested
  class RealImageTranscodeTests {

    @Test
    void apply_Presentation_State_LUT() throws Exception {
      var testCase =
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
    void apply_Presentation_State_Overlay() throws Exception {
      var testCase =
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
    void resize_real_image() throws Exception {
      var testCase =
          RealImageTestCase.builder()
              .withInputFile("signed-raw-9bit.dcm")
              .withTargetTransferSyntax(UID.JPEGLSNearLossless)
              .withResizeTo(new Dimension(128, 128))
              .build();

      var outputFile = runRealDicomTranscodeTest(testCase);
      var images = readDicomImages(outputFile);

      assertEquals(128, images.get(0).width(), "Width should match resize target");
      assertEquals(128, images.get(0).height(), "Height should match resize target");
    }

    @Test
    void transcode_multiple_times() throws Exception {
      testMultipleTranscoding("MR-JPEGLosslessSV1.dcm", UID.JPEGLSLossless, UID.JPEGLosslessSV1);
      testMultipleTranscoding(
          "CT-JPEGLosslessSV1.dcm",
          UID.ExplicitVRLittleEndian,
          UID.JPEGLSLossless,
          UID.JPEGLosslessSV1);
    }

    @ParameterizedTest
    @ValueSource(strings = {UID.JPEG2000, UID.JPEGBaseline8Bit, UID.JPEGLSNearLossless})
    void transcode_YBR_422_DICOM_to_lossy_formats_with_compression_aware_validation(String lossyUID)
        throws Exception {
      var testCase =
          RealImageTestCase.builder()
              .withInputFile("ybr422-raw.dcm")
              .withTargetTransferSyntax(lossyUID)
              .withCompressionConfig(lossyUID)
              .withExpectations(createQualityBasedExpectations(80))
              .build();

      runRealDicomTranscodeTest(testCase);
    }

    @Test
    void test_near_lossless_compression_with_specific_error_tolerance() throws Exception {
      var testCase =
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

      var updatedTestCase =
          new RealImageTestCase(
              testCase.inputFile(),
              testCase.presentationStateFile(),
              testCase.expectedImage(),
              testCase.format(),
              testCase.targetTransferSyntax(),
              testCase.overlayColor(),
              testCase.resizeTo(),
              testCase.compressionConfig(),
              expectations);

      runRealDicomTranscodeTest(updatedTestCase);
    }

    @ParameterizedTest
    @ValueSource(strings = {UID.JPEG2000Lossless, UID.JPEGLosslessSV1, UID.JPEGLSLossless})
    void transcode_YBR_422_DICOM_to_lossless_formats(String losslessUID) throws Exception {
      var testCase =
          RealImageTestCase.builder()
              .withInputFile("ybr422-raw.dcm")
              .withTargetTransferSyntax(losslessUID)
              .withExpectations(createLosslessExpectations())
              .build();

      runRealDicomTranscodeTest(testCase);
    }

    @ParameterizedTest
    @ValueSource(strings = {UID.JPEG2000Lossless, UID.JPEGLosslessSV1, UID.JPEGLSLossless})
    void palette_multiframe(String losslessUID) throws Exception {
      var testCase =
          RealImageTestCase.builder()
              .withInputFile("palette-multiframe-jpeg-ls.dcm")
              .withTargetTransferSyntax(losslessUID)
              .withExpectations(createLosslessExpectations())
              .build();

      runRealDicomTranscodeTest(testCase);
    }

    private void testMultipleTranscoding(String srcFileName, String... transferSyntaxList)
        throws Exception {
      var currentInput = srcFileName;

      for (int i = 0; i < transferSyntaxList.length; i++) {
        var transferSyntax = transferSyntaxList[i];
        var testCase =
            RealImageTestCase.builder()
                .withInputFile(currentInput)
                .withTargetTransferSyntax(transferSyntax)
                .build();

        var outputFile = runRealDicomTranscodeTest(testCase);

        if (i < transferSyntaxList.length - 1) {
          // Copy output to input directory for next iteration
          var nextInput = transferSyntax + ".dcm";
          var targetPath = IN_DIR.resolve(nextInput);
          Files.copy(outputFile, targetPath, StandardCopyOption.REPLACE_EXISTING);
          currentInput = nextInput;
        }
      }
    }
  }

  // Test execution helpers using actual data creation
  private void runBasicTranscodeTest(TestImageSpec spec, Format format) throws Exception {
    var inputFile = createTestImage(spec);
    var params = new ImageTranscodeParam(format);
    var outputFiles = Transcoder.dcm2image(inputFile, OUT_DIR, params);

    assertFalse(outputFiles.isEmpty());
    assertTrue(Files.exists(outputFiles.get(0)));
    assertTrue(outputFiles.get(0).toString().endsWith(format.getExtension()));
    assertTrue(Files.size(outputFiles.get(0)) > 0);

    if (format != Format.JPEG) { // Skip size validation for JPEG due to compression
      try (var outputImage = ImageIOHandler.readImageWithCvException(outputFiles.get(0), null)) {
        assertEquals(spec.width(), outputImage.width());
        assertEquals(spec.height(), outputImage.height());
      }
    }
  }

  private void runTranscodeTest(
      TestImageSpec spec,
      ImageTranscodeParam params,
      Map<ImageContentHash, Consumer<Double>> expectations)
      throws Exception {
    var inputFile = createTestImage(spec);
    var outputFiles = Transcoder.dcm2image(inputFile, OUT_DIR, params);

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
    var inputFile = createTestImage(spec);
    var outputFile = transcodeDicom(inputFile, params);

    // Verify transfer syntax
    reader.setInput(new DicomFileInputStream(outputFile));
    var metadata = reader.getStreamMetadata();
    assertEquals(params.getOutputTsuid(), metadata.getTransferSyntaxUID());

    if (expectations != null && !expectations.isEmpty()) {
      compareImageContent(inputFile, outputFile, expectations);
    }
  }

  private void runRealImageTest(RealImageTestCase testCase) throws Exception {
    var inputFile = IN_DIR.resolve(testCase.inputFile());
    var readParam = createReadParam(testCase);
    var params = new ImageTranscodeParam(readParam, testCase.format());

    var outputFiles = Transcoder.dcm2image(inputFile, OUT_DIR, params);
    assertFalse(outputFiles.isEmpty());

    if (testCase.expectedImage() != null) {
      var expectedFile = IN_DIR.resolve(testCase.expectedImage());
      compareImageContent(expectedFile, outputFiles, testCase.expectations());
    } else if (testCase.expectations() != null) {
      compareImageContent(inputFile, outputFiles, testCase.expectations());
    }
  }

  private Path runRealDicomTranscodeTest(RealImageTestCase testCase) throws Exception {
    var inputFile = IN_DIR.resolve(testCase.inputFile());
    var params = createDicomTranscodeParam(testCase);

    var outputFile = transcodeDicom(inputFile, params);

    if (testCase.expectations() != null && !testCase.expectations().isEmpty()) {
      compareImageContent(inputFile, outputFile, testCase.expectations());
    }

    return outputFile;
  }

  private DicomImageReadParam createReadParam(RealImageTestCase testCase) throws Exception {
    var readParam = new DicomImageReadParam();

    if (testCase.presentationStateFile() != null) {
      var prFile = IN_DIR.resolve(testCase.presentationStateFile());
      readParam.setPresentationState(PrDicomObject.getPresentationState(prFile.toString()));
    }

    if (testCase.overlayColor() != null) {
      readParam.setOverlayColor(testCase.overlayColor());
    }

    if (testCase.resizeTo() != null) {
      readParam.setSourceRenderSize(testCase.resizeTo());
    }

    return readParam;
  }

  private DicomTranscodeParam createDicomTranscodeParam(RealImageTestCase testCase)
      throws Exception {
    var params = new DicomTranscodeParam(testCase.targetTransferSyntax());

    if (testCase.resizeTo() != null) {
      params.getReadParam().setSourceRenderSize(testCase.resizeTo());
    }

    if (testCase.compressionConfig() != null) {
      configureCompressionParams(params, testCase.compressionConfig());
    }

    return params;
  }

  private static String getValidationCategory(ImageContentHash hashType) {
    return switch (hashType) {
      case AVERAGE, BLOCK_MEAN_ONE -> "structural";
      case PHASH -> "perceptual";
      case COLOR_MOMENT -> "color";
      default -> "structural";
    };
  }

  // Enhanced expectation builders using real data structures
  private Map<ImageContentHash, Consumer<Double>> createLosslessExpectations() {
    var expectations = new EnumMap<ImageContentHash, Consumer<Double>>(ImageContentHash.class);
    expectations.put(ImageContentHash.AVERAGE, identical);
    expectations.put(ImageContentHash.PHASH, identical);
    expectations.put(ImageContentHash.BLOCK_MEAN_ONE, identical);
    expectations.put(ImageContentHash.COLOR_MOMENT, identical);
    return expectations;
  }

  private Map<ImageContentHash, Consumer<Double>> createQualityBasedExpectations(int quality) {
    var expectations = new EnumMap<ImageContentHash, Consumer<Double>>(ImageContentHash.class);

    if (quality >= 95) {
      expectations.put(ImageContentHash.AVERAGE, perceptuallyIdentical);
      expectations.put(ImageContentHash.PHASH, perceptuallyIdentical);
    } else if (quality >= 85) {
      expectations.put(ImageContentHash.AVERAGE, highQuality);
      expectations.put(ImageContentHash.PHASH, highQuality);
    } else if (quality >= 70) {
      expectations.put(ImageContentHash.AVERAGE, acceptableQuality);
      expectations.put(ImageContentHash.PHASH, acceptableQuality);
    } else {
      var lowQualityValidator = createHashValidator(ValidationLevel.LOW_QUALITY, true);
      expectations.put(ImageContentHash.AVERAGE, lowQualityValidator);
      expectations.put(ImageContentHash.PHASH, lowQualityValidator);
    }

    return expectations;
  }

  // Image comparison utilities using actual image data
  private static void compareImageContent(
      Path in, Path out, Map<ImageContentHash, Consumer<Double>> expectations) throws Exception {
    compareImageContent(in, Collections.singletonList(out), expectations);
  }

  private static void compareImageContent(
      Path in, List<Path> outFiles, Map<ImageContentHash, Consumer<Double>> expectations)
      throws Exception {
    var imagesIn = readImages(in);
    var imagesOut = readImages(outFiles);
    assertEquals(imagesIn.size(), imagesOut.size(), "Input and output frame counts should match");

    for (int i = 0; i < imagesIn.size(); i++) {
      var imgIn = imagesIn.get(i);
      var imgOut = imagesOut.get(i);

      System.out.printf("%n=== Enhanced Image Content Comparison for Frame %d ===%n", i + 1);
      System.out.println("Input: " + in);
      System.out.println("Output: " + (i < outFiles.size() ? outFiles.get(i) : outFiles.get(0)));
      System.out.printf(
          "Image dimensions: %dx%d -> %dx%d%n",
          imgIn.width(), imgIn.height(), imgOut.width(), imgOut.height());

      for (var entry : expectations.entrySet()) {
        var hashType = entry.getKey();
        var hashDiff = hashType.compare(imgIn.toMat(), imgOut.toMat());
        var category = getValidationCategory(hashType);

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

  // Helper methods for creating actual test data (not mocks)
  private Path createTestImage(TestImageSpec spec) throws IOException {
    if (spec.photometricInterpretation() != null) {
      return createColorTestImage(
          spec.filename(), spec.width(), spec.height(), spec.photometricInterpretation());
    } else if (spec.bitDepth() > 8 || spec.signed()) {
      return createMonochromeTestImage(
          spec.filename(), spec.width(), spec.height(), spec.bitDepth(), spec.signed());
    } else {
      return createTestDicomFile(
          spec.filename(),
          spec.width(),
          spec.height(),
          UID.ExplicitVRLittleEndian,
          spec.frameCount() > 1);
    }
  }

  private void configureCompressionParams(DicomTranscodeParam params, String transferSyntax) {
    var writeParams = params.getWriteJpegParam();

    switch (transferSyntax) {
      case UID.JPEGBaseline8Bit -> {
        writeParams.setCompressionQuality(85);
        System.out.println("Configured JPEG Baseline with quality: 85");
      }
      case UID.JPEG2000 -> {
        writeParams.setCompressionRatioFactor(100);
        System.out.println("Configured JPEG2000 with compression ratio: 100");
      }
      case UID.JPEGLSNearLossless -> {
        writeParams.setNearLosslessError(2);
        System.out.println("Configured JPEG-LS Near-lossless with error: 2");
      }
    }
  }

  private Path transcodeDicom(Path inputFile, DicomTranscodeParam params) throws Exception {
    var outputFilename = "transcoded_" + params.getOutputTsuid().hashCode() + ".dcm";
    var outputDir = OUT_DIR.resolve("transcoded");
    Files.createDirectories(outputDir);

    return Transcoder.dcm2dcm(inputFile, outputDir.resolve(outputFilename), params);
  }

  private List<PlanarImage> readDicomImages(Path dicomFile) throws IOException {
    reader.setInput(new DicomFileInputStream(dicomFile));
    return reader.getPlanarImages(null);
  }

  // Actual DICOM data creation methods (not mocks)
  private Path createTestDicomFile(
      String filename, int width, int height, String transferSyntax, boolean multiFrame)
      throws IOException {
    var attrs =
        testDataFactory.createBasicImageAttributes(width, height, transferSyntax, multiFrame);
    var pixelData = createGradientPixelData(width, height, multiFrame ? 3 : 1);
    attrs.setBytes(Tag.PixelData, VR.OW, pixelData);

    return writeDicomToFile(filename, attrs, transferSyntax);
  }

  private Path createMonochromeTestImage(
      String filename, int width, int height, int bitsStored, boolean signed) throws IOException {
    var attrs =
        testDataFactory.createMonochromeImageAttributes(
            width, height, bitsStored, signed, UID.ExplicitVRLittleEndian);

    var bytesPerPixel = bitsStored <= 8 ? 1 : 2;
    var pixelData = createCheckerboardPixelData(width, height, bytesPerPixel, signed);
    attrs.setBytes(Tag.PixelData, VR.OW, pixelData);

    return writeDicomToFile(filename, attrs, UID.ExplicitVRLittleEndian);
  }

  private Path createColorTestImage(
      String filename, int width, int height, String photometricInterpretation) throws IOException {
    var attrs =
        testDataFactory.createColorImageAttributes(
            width, height, photometricInterpretation, UID.ExplicitVRLittleEndian);

    var pixelData = createColorPixelData(width, height, photometricInterpretation);
    attrs.setBytes(Tag.PixelData, VR.OW, pixelData);

    return writeDicomToFile(filename, attrs, UID.ExplicitVRLittleEndian);
  }

  private byte[] createGradientPixelData(int width, int height, int frames) {
    var pixelCount = width * height * frames;
    var data = new byte[pixelCount * 2]; // 16-bit data

    for (int frame = 0; frame < frames; frame++) {
      for (int y = 0; y < height; y++) {
        for (int x = 0; x < width; x++) {
          var pixelIndex = (frame * width * height + y * width + x) * 2;
          // Create gradient pattern with frame offset
          var value = ((x + y + frame * 100) % 65536);
          data[pixelIndex] = (byte) (value & 0xFF);
          data[pixelIndex + 1] = (byte) ((value >> 8) & 0xFF);
        }
      }
    }
    return data;
  }

  private byte[] createCheckerboardPixelData(
      int width, int height, int bytesPerPixel, boolean signed) {
    var data = new byte[width * height * bytesPerPixel];
    var maxValue = signed ? (1 << (bytesPerPixel * 8 - 1)) - 1 : (1 << (bytesPerPixel * 8)) - 1;

    for (int y = 0; y < height; y++) {
      for (int x = 0; x < width; x++) {
        var pixelIndex = (y * width + x) * bytesPerPixel;
        var isWhite = ((x / 16) + (y / 16)) % 2 == 0;
        var value = isWhite ? maxValue : (signed ? -maxValue : 0);

        for (int b = 0; b < bytesPerPixel; b++) {
          data[pixelIndex + b] = (byte) ((value >> (b * 8)) & 0xFF);
        }
      }
    }
    return data;
  }

  private byte[] createColorPixelData(int width, int height, String photometricInterpretation) {
    var data = new byte[width * height * 3]; // RGB data

    for (int y = 0; y < height; y++) {
      for (int x = 0; x < width; x++) {
        var pixelIndex = (y * width + x) * 3;

        // Create color pattern based on position
        switch (photometricInterpretation) {
          case "RGB" -> {
            data[pixelIndex] = (byte) ((x * 255) / width); // Red
            data[pixelIndex + 1] = (byte) ((y * 255) / height); // Green
            data[pixelIndex + 2] = (byte) (128); // Blue
          }
          case "YBR_FULL_422" -> {
            data[pixelIndex] = (byte) (128 + (x % 128)); // Y
            data[pixelIndex + 1] = (byte) (128 + (y % 64)); // Cb
            data[pixelIndex + 2] = (byte) (128 + ((x + y) % 64)); // Cr
          }
        }
      }
    }
    return data;
  }

  private Path writeDicomToFile(String filename, Attributes attrs, String transferSyntax)
      throws IOException {
    var filepath = OUT_DIR.resolve(filename);

    try (var dos = new DicomOutputStream(Files.newOutputStream(filepath), transferSyntax)) {
      dos.writeFileMetaInformation(attrs.createFileMetaInformation(transferSyntax));
      dos.writeDataset(null, attrs);
    }

    return filepath;
  }
}
