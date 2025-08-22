/*
 * Copyright (c) 2025 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.dcm4che3.img;

import static org.junit.jupiter.api.Assertions.*;

import java.awt.Rectangle;
import java.util.List;
import java.util.stream.Stream;
import org.dcm4che3.imageio.codec.TransferSyntaxType;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Test class for {@link DicomJpegWriteParam}.
 *
 * <p>This test class validates DICOM JPEG compression parameters for various transfer syntaxes
 * including baseline, extended, spectral, progressive, lossless, JPEG-LS, and JPEG-2000 formats.
 */
@DisplayNameGeneration(ReplaceUnderscores.class)
class DicomJpegWriteParamTest {

  // DICOM Transfer Syntax UIDs organized by compression type
  record TransferSyntaxConfig(
      String uid,
      TransferSyntaxType type,
      boolean isLossless,
      int jpegMode,
      int expectedPrediction) {}

  private static final List<TransferSyntaxConfig> SUPPORTED_SYNTAXES =
      List.of(
          new TransferSyntaxConfig(
              "1.2.840.10008.1.2.4.50", TransferSyntaxType.JPEG_BASELINE, false, 0, 1),
          new TransferSyntaxConfig(
              "1.2.840.10008.1.2.4.51", TransferSyntaxType.JPEG_EXTENDED, false, 1, 1),
          new TransferSyntaxConfig(
              "1.2.840.10008.1.2.4.53", TransferSyntaxType.JPEG_SPECTRAL, false, 2, 1),
          new TransferSyntaxConfig(
              "1.2.840.10008.1.2.4.55", TransferSyntaxType.JPEG_PROGRESSIVE, false, 3, 1),
          new TransferSyntaxConfig(
              "1.2.840.10008.1.2.4.57", TransferSyntaxType.JPEG_LOSSLESS, true, 4, 6),
          new TransferSyntaxConfig(
              "1.2.840.10008.1.2.4.70", TransferSyntaxType.JPEG_LOSSLESS, true, 4, 1),
          new TransferSyntaxConfig(
              "1.2.840.10008.1.2.4.80", TransferSyntaxType.JPEG_LS, true, 0, 1),
          new TransferSyntaxConfig(
              "1.2.840.10008.1.2.4.81", TransferSyntaxType.JPEG_LS, false, 0, 1),
          new TransferSyntaxConfig(
              "1.2.840.10008.1.2.4.90", TransferSyntaxType.JPEG_2000, true, 0, 1),
          new TransferSyntaxConfig(
              "1.2.840.10008.1.2.4.91", TransferSyntaxType.JPEG_2000, false, 0, 1));

  private static final List<String> UNSUPPORTED_SYNTAXES =
      List.of(
          "1.2.840.10008.1.2", // Implicit VR Little Endian
          "1.2.840.10008.1.2.5", // RLE Lossless
          "1.2.840.10008.1.2.4.100" // MPEG2 Main Profile
          );

  static Stream<TransferSyntaxConfig> supportedTransferSyntaxes() {
    return SUPPORTED_SYNTAXES.stream();
  }

  static Stream<TransferSyntaxConfig> losslessTransferSyntaxes() {
    return SUPPORTED_SYNTAXES.stream().filter(TransferSyntaxConfig::isLossless);
  }

  static Stream<TransferSyntaxConfig> lossyTransferSyntaxes() {
    return SUPPORTED_SYNTAXES.stream().filter(config -> !config.isLossless());
  }

  static Stream<String> unsupportedTransferSyntaxes() {
    return UNSUPPORTED_SYNTAXES.stream();
  }

  static Stream<Arguments> compressionQualityTestData() {
    return Stream.of(
        Arguments.of(1, 1),
        Arguments.of(50, 50),
        Arguments.of(85, 85),
        Arguments.of(100, 100),
        Arguments.of(0, 0),
        Arguments.of(200, 200));
  }

  static Stream<Arguments> compressionRatioTestData() {
    return Stream.of(
        Arguments.of(0),
        Arguments.of(10),
        Arguments.of(20),
        Arguments.of(50),
        Arguments.of(100),
        Arguments.of(1),
        Arguments.of(1000));
  }

  static Stream<Arguments> invalidSourceRegionData() {
    return Stream.of(
        Arguments.of(-1, 0, 100, 100, "negative x"),
        Arguments.of(0, -1, 100, 100, "negative y"),
        Arguments.of(0, 0, 0, 100, "zero width"),
        Arguments.of(0, 0, 100, 0, "zero height"),
        Arguments.of(0, 0, -1, 100, "negative width"),
        Arguments.of(0, 0, 100, -1, "negative height"));
  }

  @ParameterizedTest
  @MethodSource("supportedTransferSyntaxes")
  void should_create_properly_configured_parameters_for_supported_transfer_syntaxes(
      TransferSyntaxConfig config) {
    var param = DicomJpegWriteParam.buildDicomImageWriteParam(config.uid());

    assertAll(
        "Basic configuration",
        () -> assertNotNull(param),
        () -> assertEquals(config.uid(), param.getTransferSyntaxUid()),
        () -> assertEquals(config.type(), param.getType()),
        () -> assertEquals(config.jpegMode(), param.getJpegMode()),
        () -> assertEquals(config.isLossless(), param.isCompressionLossless()),
        () -> assertEquals(config.expectedPrediction(), param.getPrediction()),
        () -> assertEquals(0, param.getPointTransform()),
        () -> assertNull(param.getSourceRegion()));
  }

  @ParameterizedTest
  @MethodSource("losslessTransferSyntaxes")
  void should_configure_lossless_compression_parameters(TransferSyntaxConfig config) {
    var param = DicomJpegWriteParam.buildDicomImageWriteParam(config.uid());

    assertAll(
        "Lossless parameters",
        () -> assertTrue(param.isCompressionLossless()),
        () -> assertEquals(0, param.getCompressionQuality()),
        () -> assertEquals(0, param.getNearLosslessError()),
        () -> assertEquals(0, param.getCompressionRatioFactor()));
  }

  @ParameterizedTest
  @MethodSource("lossyTransferSyntaxes")
  void should_configure_lossy_compression_parameters(TransferSyntaxConfig config) {
    var param = DicomJpegWriteParam.buildDicomImageWriteParam(config.uid());

    assertAll(
        "Lossy parameters",
        () -> assertFalse(param.isCompressionLossless()),
        () -> assertEquals(85, param.getCompressionQuality()),
        () -> assertEquals(2, param.getNearLosslessError()),
        () -> assertEquals(10, param.getCompressionRatioFactor()));
  }

  @ParameterizedTest
  @MethodSource("unsupportedTransferSyntaxes")
  void should_throw_exception_for_unsupported_transfer_syntaxes(String unsupportedTsuid) {
    var exception =
        assertThrows(
            IllegalStateException.class,
            () -> DicomJpegWriteParam.buildDicomImageWriteParam(unsupportedTsuid));

    assertAll(
        "Exception details",
        () -> assertTrue(exception.getMessage().contains("is not supported for compression")),
        () -> assertTrue(exception.getMessage().contains(unsupportedTsuid)));
  }

  @Test
  void should_allow_prediction_parameter_modifications() {
    var param = DicomJpegWriteParam.buildDicomImageWriteParam("1.2.840.10008.1.2.4.57");

    assertEquals(6, param.getPrediction());

    // Test valid prediction modes (1-7)
    for (int prediction = 1; prediction <= 7; prediction++) {
      param.setPrediction(prediction);
      assertEquals(prediction, param.getPrediction());
    }

    // Test edge cases
    param.setPrediction(0);
    assertEquals(0, param.getPrediction());

    param.setPrediction(10);
    assertEquals(10, param.getPrediction());
  }

  @Test
  void should_allow_point_transform_parameter_modifications() {
    var param = DicomJpegWriteParam.buildDicomImageWriteParam("1.2.840.10008.1.2.4.57");

    assertEquals(0, param.getPointTransform());

    // Test valid point transform values (0-15)
    for (int pointTransform = 0; pointTransform <= 15; pointTransform++) {
      param.setPointTransform(pointTransform);
      assertEquals(pointTransform, param.getPointTransform());
    }

    param.setPointTransform(16);
    assertEquals(16, param.getPointTransform());
  }

  @ParameterizedTest
  @CsvSource({"0, 0", "1, 1", "5, 5", "10, 10", "255, 255"})
  void should_accept_valid_near_lossless_error_values(int input, int expected) {
    var param = DicomJpegWriteParam.buildDicomImageWriteParam("1.2.840.10008.1.2.4.81");

    param.setNearLosslessError(input);
    assertEquals(expected, param.getNearLosslessError());
  }

  @ParameterizedTest
  @ValueSource(ints = {-1, -5, -100})
  void should_reject_negative_near_lossless_error_values(int negativeValue) {
    var param = DicomJpegWriteParam.buildDicomImageWriteParam("1.2.840.10008.1.2.4.81");

    var exception =
        assertThrows(
            IllegalArgumentException.class, () -> param.setNearLosslessError(negativeValue));

    assertAll(
        "Error validation",
        () -> assertTrue(exception.getMessage().contains("nearLossless invalid value")),
        () -> assertTrue(exception.getMessage().contains(String.valueOf(negativeValue))));
  }

  @ParameterizedTest
  @MethodSource("compressionQualityTestData")
  void should_handle_compression_quality_values(int input, int expected) {
    var param = DicomJpegWriteParam.buildDicomImageWriteParam("1.2.840.10008.1.2.4.50");

    param.setCompressionQuality(input);
    assertEquals(expected, param.getCompressionQuality());
  }

  @ParameterizedTest
  @MethodSource("compressionRatioTestData")
  void should_handle_compression_ratio_factor_values(int ratio) {
    var param = DicomJpegWriteParam.buildDicomImageWriteParam("1.2.840.10008.1.2.4.91");

    param.setCompressionRatioFactor(ratio);
    assertEquals(ratio, param.getCompressionRatioFactor());
  }

  @Test
  void should_handle_source_region_modifications() {
    var param = DicomJpegWriteParam.buildDicomImageWriteParam("1.2.840.10008.1.2.4.50");

    assertNull(param.getSourceRegion());

    var region1 = new Rectangle(10, 20, 100, 200);
    param.setSourceRegion(region1);
    assertEquals(region1, param.getSourceRegion());

    param.setSourceRegion(null);
    assertNull(param.getSourceRegion());

    var region2 = new Rectangle(0, 0, 512, 512);
    param.setSourceRegion(region2);
    assertEquals(region2, param.getSourceRegion());
  }

  @ParameterizedTest
  @MethodSource("invalidSourceRegionData")
  void should_reject_invalid_source_region_dimensions(
      int x, int y, int width, int height, String description) {
    var param = DicomJpegWriteParam.buildDicomImageWriteParam("1.2.840.10008.1.2.4.50");
    var invalidRegion = new Rectangle(x, y, width, height);

    var exception =
        assertThrows(
            IllegalArgumentException.class,
            () -> param.setSourceRegion(invalidRegion),
            "Should reject " + description);

    assertTrue(exception.getMessage().contains("sourceRegion has illegal values"));
  }

  @Test
  void should_maintain_parameter_independence_between_instances() {
    var param1 = DicomJpegWriteParam.buildDicomImageWriteParam("1.2.840.10008.1.2.4.50");
    var param2 = DicomJpegWriteParam.buildDicomImageWriteParam("1.2.840.10008.1.2.4.50");

    param1.setPrediction(5);
    param1.setCompressionQuality(60);
    param1.setSourceRegion(new Rectangle(10, 10, 100, 100));

    assertAll(
        "param2 should remain unchanged",
        () -> assertEquals(1, param2.getPrediction()),
        () -> assertEquals(85, param2.getCompressionQuality()),
        () -> assertNull(param2.getSourceRegion()));

    param2.setPrediction(7);
    param2.setCompressionQuality(90);
    param2.setSourceRegion(new Rectangle(20, 20, 200, 200));

    assertAll(
        "param1 should retain its modifications",
        () -> assertEquals(5, param1.getPrediction()),
        () -> assertEquals(60, param1.getCompressionQuality()),
        () -> assertEquals(new Rectangle(10, 10, 100, 100), param1.getSourceRegion()));
  }

  @Test
  void should_support_complex_parameter_combinations() {
    // JPEG-LS near-lossless configuration
    var jpegLSParam = DicomJpegWriteParam.buildDicomImageWriteParam("1.2.840.10008.1.2.4.81");
    jpegLSParam.setNearLosslessError(3);
    jpegLSParam.setCompressionQuality(90);
    jpegLSParam.setSourceRegion(new Rectangle(64, 64, 256, 256));

    assertAll(
        "JPEG-LS configuration",
        () -> assertEquals(TransferSyntaxType.JPEG_LS, jpegLSParam.getType()),
        () -> assertFalse(jpegLSParam.isCompressionLossless()),
        () -> assertEquals(3, jpegLSParam.getNearLosslessError()),
        () -> assertEquals(90, jpegLSParam.getCompressionQuality()),
        () -> assertEquals(new Rectangle(64, 64, 256, 256), jpegLSParam.getSourceRegion()));

    // JPEG-2000 with high compression ratio
    var jpeg2000Param = DicomJpegWriteParam.buildDicomImageWriteParam("1.2.840.10008.1.2.4.91");
    jpeg2000Param.setCompressionRatioFactor(50);
    jpeg2000Param.setCompressionQuality(70);

    assertAll(
        "JPEG-2000 configuration",
        () -> assertEquals(TransferSyntaxType.JPEG_2000, jpeg2000Param.getType()),
        () -> assertFalse(jpeg2000Param.isCompressionLossless()),
        () -> assertEquals(50, jpeg2000Param.getCompressionRatioFactor()),
        () -> assertEquals(70, jpeg2000Param.getCompressionQuality()));
  }

  @Test
  void should_support_real_world_medical_imaging_scenarios() {
    // CT scan compression scenario
    var ctParam = DicomJpegWriteParam.buildDicomImageWriteParam("1.2.840.10008.1.2.4.50");
    ctParam.setCompressionQuality(95);
    ctParam.setSourceRegion(new Rectangle(0, 0, 512, 512));

    assertAll(
        "CT scan parameters",
        () -> assertEquals(95, ctParam.getCompressionQuality()),
        () -> assertEquals(new Rectangle(0, 0, 512, 512), ctParam.getSourceRegion()),
        () -> assertFalse(ctParam.isCompressionLossless()));

    // MR scan lossless compression scenario
    var mrParam = DicomJpegWriteParam.buildDicomImageWriteParam("1.2.840.10008.1.2.4.70");
    mrParam.setSourceRegion(new Rectangle(0, 0, 256, 256));

    assertAll(
        "MR scan parameters",
        () -> assertTrue(mrParam.isCompressionLossless()),
        () -> assertEquals(1, mrParam.getPrediction()),
        () -> assertEquals(new Rectangle(0, 0, 256, 256), mrParam.getSourceRegion()));

    // High-resolution digital radiography scenario
    var drParam = DicomJpegWriteParam.buildDicomImageWriteParam("1.2.840.10008.1.2.4.91");
    drParam.setCompressionRatioFactor(20);
    drParam.setSourceRegion(new Rectangle(0, 0, 2048, 2048));

    assertAll(
        "Digital radiography parameters",
        () -> assertEquals(20, drParam.getCompressionRatioFactor()),
        () -> assertEquals(new Rectangle(0, 0, 2048, 2048), drParam.getSourceRegion()),
        () -> assertFalse(drParam.isCompressionLossless()));
  }

  @Test
  void should_preserve_immutable_properties_after_parameter_modifications() {
    var param = DicomJpegWriteParam.buildDicomImageWriteParam("1.2.840.10008.1.2.4.50");

    // Modify all mutable parameters
    param.setPrediction(3);
    param.setPointTransform(2);
    param.setNearLosslessError(5);
    param.setCompressionQuality(75);
    param.setCompressionRatioFactor(15);
    param.setSourceRegion(new Rectangle(50, 50, 200, 150));

    assertAll(
        "Mutable parameters changed",
        () -> assertEquals(3, param.getPrediction()),
        () -> assertEquals(2, param.getPointTransform()),
        () -> assertEquals(5, param.getNearLosslessError()),
        () -> assertEquals(75, param.getCompressionQuality()),
        () -> assertEquals(15, param.getCompressionRatioFactor()),
        () -> assertEquals(new Rectangle(50, 50, 200, 150), param.getSourceRegion()));

    assertAll(
        "Immutable properties preserved",
        () -> assertEquals("1.2.840.10008.1.2.4.50", param.getTransferSyntaxUid()),
        () -> assertEquals(TransferSyntaxType.JPEG_BASELINE, param.getType()),
        () -> assertFalse(param.isCompressionLossless()));
  }
}
