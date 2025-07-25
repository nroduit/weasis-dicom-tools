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
import org.dcm4che3.imageio.codec.TransferSyntaxType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Test class for {@link DicomJpegWriteParam}.
 *
 * <p>This test class validates DICOM JPEG compression parameters for various transfer syntaxes
 * including baseline, extended, spectral, progressive, lossless, JPEG-LS, and JPEG-2000 formats
 * using real transfer syntax UIDs and configurations.
 */
class DicomJpegWriteParamTest {

  // Standard DICOM Transfer Syntax UIDs for testing
  private static final String JPEG_BASELINE_PROCESS_1 = "1.2.840.10008.1.2.4.50";
  private static final String JPEG_EXTENDED_PROCESS_2_4 = "1.2.840.10008.1.2.4.51";
  private static final String JPEG_SPECTRAL_SELECTION_6_8 = "1.2.840.10008.1.2.4.53";
  private static final String JPEG_PROGRESSIVE_10_12 = "1.2.840.10008.1.2.4.55";
  private static final String JPEG_LOSSLESS_PROCESS_14 = "1.2.840.10008.1.2.4.57";
  private static final String JPEG_LOSSLESS_SV1 = "1.2.840.10008.1.2.4.70";
  private static final String JPEG_LS_LOSSLESS = "1.2.840.10008.1.2.4.80";
  private static final String JPEG_LS_LOSSY = "1.2.840.10008.1.2.4.81";
  private static final String JPEG_2000_LOSSLESS = "1.2.840.10008.1.2.4.90";
  private static final String JPEG_2000_LOSSY = "1.2.840.10008.1.2.4.91";

  // Unsupported transfer syntaxes for testing error conditions
  private static final String IMPLICIT_VR_LITTLE_ENDIAN = "1.2.840.10008.1.2";
  private static final String RLE_LOSSLESS = "1.2.840.10008.1.2.5";
  private static final String MPEG2_MAIN_PROFILE = "1.2.840.10008.1.2.4.100";

  @Test
  @DisplayName(
      "buildDicomImageWriteParam should create properly configured JPEG Baseline parameters")
  void testJpegBaselineConfiguration() {
    DicomJpegWriteParam param =
        DicomJpegWriteParam.buildDicomImageWriteParam(JPEG_BASELINE_PROCESS_1);

    assertNotNull(param);
    assertEquals(JPEG_BASELINE_PROCESS_1, param.getTransferSyntaxUid());
    assertEquals(TransferSyntaxType.JPEG_BASELINE, param.getType());
    assertEquals(0, param.getJpegMode());
    assertFalse(param.isCompressionLossless());

    // Lossy compression defaults
    assertEquals(85, param.getCompressionQuality());
    assertEquals(2, param.getNearLosslessError());
    assertEquals(10, param.getCompressionRatioFactor());
    assertEquals(1, param.getPrediction());
    assertEquals(0, param.getPointTransform());
    assertNull(param.getSourceRegion());
  }

  @Test
  @DisplayName(
      "buildDicomImageWriteParam should create properly configured JPEG Extended parameters")
  void testJpegExtendedConfiguration() {
    DicomJpegWriteParam param =
        DicomJpegWriteParam.buildDicomImageWriteParam(JPEG_EXTENDED_PROCESS_2_4);

    assertNotNull(param);
    assertEquals(JPEG_EXTENDED_PROCESS_2_4, param.getTransferSyntaxUid());
    assertEquals(TransferSyntaxType.JPEG_EXTENDED, param.getType());
    assertEquals(1, param.getJpegMode());
    assertFalse(param.isCompressionLossless());

    // Lossy compression defaults
    assertEquals(85, param.getCompressionQuality());
    assertEquals(2, param.getNearLosslessError());
    assertEquals(10, param.getCompressionRatioFactor());
  }

  @Test
  @DisplayName(
      "buildDicomImageWriteParam should create properly configured JPEG Spectral parameters")
  void testJpegSpectralConfiguration() {
    DicomJpegWriteParam param =
        DicomJpegWriteParam.buildDicomImageWriteParam(JPEG_SPECTRAL_SELECTION_6_8);

    assertNotNull(param);
    assertEquals(JPEG_SPECTRAL_SELECTION_6_8, param.getTransferSyntaxUid());
    assertEquals(TransferSyntaxType.JPEG_SPECTRAL, param.getType());
    assertEquals(2, param.getJpegMode());
    assertFalse(param.isCompressionLossless());
  }

  @Test
  @DisplayName(
      "buildDicomImageWriteParam should create properly configured JPEG Progressive parameters")
  void testJpegProgressiveConfiguration() {
    DicomJpegWriteParam param =
        DicomJpegWriteParam.buildDicomImageWriteParam(JPEG_PROGRESSIVE_10_12);

    assertNotNull(param);
    assertEquals(JPEG_PROGRESSIVE_10_12, param.getTransferSyntaxUid());
    assertEquals(TransferSyntaxType.JPEG_PROGRESSIVE, param.getType());
    assertEquals(3, param.getJpegMode());
    assertFalse(param.isCompressionLossless());
  }

  @Test
  @DisplayName(
      "buildDicomImageWriteParam should create properly configured JPEG Lossless parameters")
  void testJpegLosslessConfiguration() {
    DicomJpegWriteParam param =
        DicomJpegWriteParam.buildDicomImageWriteParam(JPEG_LOSSLESS_PROCESS_14);

    assertNotNull(param);
    assertEquals(JPEG_LOSSLESS_PROCESS_14, param.getTransferSyntaxUid());
    assertEquals(TransferSyntaxType.JPEG_LOSSLESS, param.getType());
    assertEquals(4, param.getJpegMode());
    assertTrue(param.isCompressionLossless());

    // Lossless compression defaults
    assertEquals(0, param.getCompressionQuality());
    assertEquals(0, param.getNearLosslessError());
    assertEquals(0, param.getCompressionRatioFactor());
    assertEquals(6, param.getPrediction()); // Default 1 but 6 for JPEG_LOSSLESS_PROCESS_14
    assertEquals(0, param.getPointTransform());
  }

  @Test
  @DisplayName(
      "buildDicomImageWriteParam should configure JPEG Lossless SV1 with optimal prediction")
  void testJpegLosslessSV1Configuration() {
    DicomJpegWriteParam param = DicomJpegWriteParam.buildDicomImageWriteParam(JPEG_LOSSLESS_SV1);

    assertNotNull(param);
    assertEquals(JPEG_LOSSLESS_SV1, param.getTransferSyntaxUid());
    assertEquals(TransferSyntaxType.JPEG_LOSSLESS, param.getType());
    assertTrue(param.isCompressionLossless());

    // Should use optimal prediction mode for SV1
    assertEquals(1, param.getPrediction());
    assertEquals(0, param.getPointTransform());
  }

  @Test
  @DisplayName(
      "buildDicomImageWriteParam should create properly configured JPEG-LS Lossless parameters")
  void testJpegLSLosslessConfiguration() {
    DicomJpegWriteParam param = DicomJpegWriteParam.buildDicomImageWriteParam(JPEG_LS_LOSSLESS);

    assertNotNull(param);
    assertEquals(JPEG_LS_LOSSLESS, param.getTransferSyntaxUid());
    assertEquals(TransferSyntaxType.JPEG_LS, param.getType());
    assertTrue(param.isCompressionLossless());

    // Lossless JPEG-LS defaults
    assertEquals(0, param.getCompressionQuality());
    assertEquals(0, param.getNearLosslessError());
    assertEquals(0, param.getCompressionRatioFactor());
  }

  @Test
  @DisplayName(
      "buildDicomImageWriteParam should create properly configured JPEG-LS Lossy parameters")
  void testJpegLSLossyConfiguration() {
    DicomJpegWriteParam param = DicomJpegWriteParam.buildDicomImageWriteParam(JPEG_LS_LOSSY);

    assertNotNull(param);
    assertEquals(JPEG_LS_LOSSY, param.getTransferSyntaxUid());
    assertEquals(TransferSyntaxType.JPEG_LS, param.getType());
    assertFalse(param.isCompressionLossless());

    // Lossy JPEG-LS defaults
    assertEquals(85, param.getCompressionQuality());
    assertEquals(2, param.getNearLosslessError());
    assertEquals(10, param.getCompressionRatioFactor());
  }

  @Test
  @DisplayName(
      "buildDicomImageWriteParam should create properly configured JPEG-2000 Lossless parameters")
  void testJpeg2000LosslessConfiguration() {
    DicomJpegWriteParam param = DicomJpegWriteParam.buildDicomImageWriteParam(JPEG_2000_LOSSLESS);

    assertNotNull(param);
    assertEquals(JPEG_2000_LOSSLESS, param.getTransferSyntaxUid());
    assertEquals(TransferSyntaxType.JPEG_2000, param.getType());
    assertTrue(param.isCompressionLossless());

    // Lossless JPEG-2000 defaults
    assertEquals(0, param.getCompressionQuality());
    assertEquals(0, param.getNearLosslessError());
    assertEquals(0, param.getCompressionRatioFactor());
  }

  @Test
  @DisplayName(
      "buildDicomImageWriteParam should create properly configured JPEG-2000 Lossy parameters")
  void testJpeg2000LossyConfiguration() {
    DicomJpegWriteParam param = DicomJpegWriteParam.buildDicomImageWriteParam(JPEG_2000_LOSSY);

    assertNotNull(param);
    assertEquals(JPEG_2000_LOSSY, param.getTransferSyntaxUid());
    assertEquals(TransferSyntaxType.JPEG_2000, param.getType());
    assertFalse(param.isCompressionLossless());

    // Lossy JPEG-2000 defaults
    assertEquals(85, param.getCompressionQuality());
    assertEquals(2, param.getNearLosslessError());
    assertEquals(10, param.getCompressionRatioFactor());
  }

  @ParameterizedTest
  @ValueSource(strings = {IMPLICIT_VR_LITTLE_ENDIAN, RLE_LOSSLESS, MPEG2_MAIN_PROFILE})
  @DisplayName(
      "buildDicomImageWriteParam should throw IllegalStateException for unsupported transfer syntaxes")
  void testUnsupportedTransferSyntaxThrows(String unsupportedTsuid) {
    IllegalStateException exception =
        assertThrows(
            IllegalStateException.class,
            () -> DicomJpegWriteParam.buildDicomImageWriteParam(unsupportedTsuid));
    assertTrue(exception.getMessage().contains("is not supported for compression"));
    assertTrue(exception.getMessage().contains(unsupportedTsuid));
  }

  @Test
  @DisplayName("Prediction parameter should be settable and retrievable")
  void testPredictionParameter() {
    DicomJpegWriteParam param =
        DicomJpegWriteParam.buildDicomImageWriteParam(JPEG_LOSSLESS_PROCESS_14);

    // Test default value
    assertEquals(6, param.getPrediction()); // Default for JPEG_LOSSLESS_PROCESS_14

    // Test setting various prediction modes (1-7)
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
  @DisplayName("Point transform parameter should be settable and retrievable")
  void testPointTransformParameter() {
    DicomJpegWriteParam param =
        DicomJpegWriteParam.buildDicomImageWriteParam(JPEG_LOSSLESS_PROCESS_14);

    // Test default value
    assertEquals(0, param.getPointTransform());

    // Test setting valid point transform values (0-15)
    for (int pointTransform = 0; pointTransform <= 15; pointTransform++) {
      param.setPointTransform(pointTransform);
      assertEquals(pointTransform, param.getPointTransform());
    }

    // Test edge cases
    param.setPointTransform(16);
    assertEquals(16, param.getPointTransform());
  }

  @ParameterizedTest
  @CsvSource({"0, 0", "1, 1", "5, 5", "10, 10", "255, 255"})
  @DisplayName("Near-lossless error should be settable for valid values")
  void testNearLosslessErrorValidValues(int input, int expected) {
    DicomJpegWriteParam param = DicomJpegWriteParam.buildDicomImageWriteParam(JPEG_LS_LOSSY);

    param.setNearLosslessError(input);
    assertEquals(expected, param.getNearLosslessError());
  }

  @ParameterizedTest
  @ValueSource(ints = {-1, -5, -100})
  @DisplayName("Near-lossless error should throw IllegalArgumentException for negative values")
  void testNearLosslessErrorNegativeValues(int negativeValue) {
    DicomJpegWriteParam param = DicomJpegWriteParam.buildDicomImageWriteParam(JPEG_LS_LOSSY);

    IllegalArgumentException exception =
        assertThrows(
            IllegalArgumentException.class, () -> param.setNearLosslessError(negativeValue));
    assertTrue(exception.getMessage().contains("nearLossless invalid value"));
    assertTrue(exception.getMessage().contains(String.valueOf(negativeValue)));
  }

  @ParameterizedTest
  @CsvSource({"1, 1", "50, 50", "85, 85", "100, 100"})
  @DisplayName("Compression quality should be settable and retrievable")
  void testCompressionQuality(int input, int expected) {
    DicomJpegWriteParam param =
        DicomJpegWriteParam.buildDicomImageWriteParam(JPEG_BASELINE_PROCESS_1);

    param.setCompressionQuality(input);
    assertEquals(expected, param.getCompressionQuality());
  }

  @Test
  @DisplayName("Compression quality should handle edge values")
  void testCompressionQualityEdgeValues() {
    DicomJpegWriteParam param =
        DicomJpegWriteParam.buildDicomImageWriteParam(JPEG_BASELINE_PROCESS_1);

    // Test minimum quality
    param.setCompressionQuality(1);
    assertEquals(1, param.getCompressionQuality());

    // Test maximum quality
    param.setCompressionQuality(100);
    assertEquals(100, param.getCompressionQuality());

    // Test values outside typical range
    param.setCompressionQuality(0);
    assertEquals(0, param.getCompressionQuality());

    param.setCompressionQuality(200);
    assertEquals(200, param.getCompressionQuality());
  }

  @Test
  @DisplayName("Compression ratio factor should be settable and retrievable")
  void testCompressionRatioFactor() {
    DicomJpegWriteParam param = DicomJpegWriteParam.buildDicomImageWriteParam(JPEG_2000_LOSSY);

    // Test recommended values
    int[] recommendedValues = {0, 10, 20, 50, 100};
    for (int ratio : recommendedValues) {
      param.setCompressionRatioFactor(ratio);
      assertEquals(ratio, param.getCompressionRatioFactor());
    }

    // Test edge cases
    param.setCompressionRatioFactor(1);
    assertEquals(1, param.getCompressionRatioFactor());

    param.setCompressionRatioFactor(1000);
    assertEquals(1000, param.getCompressionRatioFactor());
  }

  @Test
  @DisplayName("Source region should be settable and retrievable")
  void testSourceRegion() {
    DicomJpegWriteParam param =
        DicomJpegWriteParam.buildDicomImageWriteParam(JPEG_BASELINE_PROCESS_1);

    // Initially null
    assertNull(param.getSourceRegion());

    // Set valid region
    Rectangle region = new Rectangle(10, 20, 100, 200);
    param.setSourceRegion(region);
    assertEquals(region, param.getSourceRegion());

    // Set to null
    param.setSourceRegion(null);
    assertNull(param.getSourceRegion());

    // Set different valid region
    Rectangle region2 = new Rectangle(0, 0, 512, 512);
    param.setSourceRegion(region2);
    assertEquals(region2, param.getSourceRegion());
  }

  @ParameterizedTest
  @CsvSource({
    "-1, 0, 100, 100", // Negative x
    "0, -1, 100, 100", // Negative y
    "0, 0, 0, 100", // Zero width
    "0, 0, 100, 0", // Zero height
    "0, 0, -1, 100", // Negative width
    "0, 0, 100, -1" // Negative height
  })
  @DisplayName("Source region should throw IllegalArgumentException for invalid dimensions")
  void testSourceRegionInvalidDimensions(int x, int y, int width, int height) {
    DicomJpegWriteParam param =
        DicomJpegWriteParam.buildDicomImageWriteParam(JPEG_BASELINE_PROCESS_1);

    Rectangle invalidRegion = new Rectangle(x, y, width, height);

    IllegalArgumentException exception =
        assertThrows(IllegalArgumentException.class, () -> param.setSourceRegion(invalidRegion));
    assertTrue(exception.getMessage().contains("sourceRegion has illegal values"));
  }

  @Test
  @DisplayName("JPEG mode should return correct values for different transfer syntax types")
  void testJpegModeMapping() {
    // Test all JPEG mode mappings
    assertEquals(
        0, DicomJpegWriteParam.buildDicomImageWriteParam(JPEG_BASELINE_PROCESS_1).getJpegMode());
    assertEquals(
        1, DicomJpegWriteParam.buildDicomImageWriteParam(JPEG_EXTENDED_PROCESS_2_4).getJpegMode());
    assertEquals(
        2,
        DicomJpegWriteParam.buildDicomImageWriteParam(JPEG_SPECTRAL_SELECTION_6_8).getJpegMode());
    assertEquals(
        3, DicomJpegWriteParam.buildDicomImageWriteParam(JPEG_PROGRESSIVE_10_12).getJpegMode());
    assertEquals(
        4, DicomJpegWriteParam.buildDicomImageWriteParam(JPEG_LOSSLESS_PROCESS_14).getJpegMode());

    // Non-JPEG types should return 0
    assertEquals(0, DicomJpegWriteParam.buildDicomImageWriteParam(JPEG_LS_LOSSLESS).getJpegMode());
    assertEquals(
        0, DicomJpegWriteParam.buildDicomImageWriteParam(JPEG_2000_LOSSLESS).getJpegMode());
  }

  @Test
  @DisplayName("Parameters should be modifiable after creation")
  void testParameterModifiability() {
    DicomJpegWriteParam param =
        DicomJpegWriteParam.buildDicomImageWriteParam(JPEG_BASELINE_PROCESS_1);

    // Modify all parameters
    param.setPrediction(3);
    param.setPointTransform(2);
    param.setNearLosslessError(5);
    param.setCompressionQuality(75);
    param.setCompressionRatioFactor(15);
    param.setSourceRegion(new Rectangle(50, 50, 200, 150));

    // Verify all modifications
    assertEquals(3, param.getPrediction());
    assertEquals(2, param.getPointTransform());
    assertEquals(5, param.getNearLosslessError());
    assertEquals(75, param.getCompressionQuality());
    assertEquals(15, param.getCompressionRatioFactor());
    assertEquals(new Rectangle(50, 50, 200, 150), param.getSourceRegion());

    // Original immutable properties should remain unchanged
    assertEquals(JPEG_BASELINE_PROCESS_1, param.getTransferSyntaxUid());
    assertEquals(TransferSyntaxType.JPEG_BASELINE, param.getType());
    assertFalse(param.isCompressionLossless());
  }

  @Test
  @DisplayName("Lossless vs lossy compression should be correctly determined")
  void testLosslessVsLossyCompression() {
    // Lossless transfer syntaxes
    String[] losslessSyntaxes = {
      JPEG_LOSSLESS_PROCESS_14, JPEG_LOSSLESS_SV1, JPEG_LS_LOSSLESS, JPEG_2000_LOSSLESS
    };

    for (String tsuid : losslessSyntaxes) {
      DicomJpegWriteParam param = DicomJpegWriteParam.buildDicomImageWriteParam(tsuid);
      assertTrue(param.isCompressionLossless(), "Should be lossless: " + tsuid);
      assertEquals(0, param.getCompressionQuality(), "Quality should be 0 for lossless: " + tsuid);
      assertEquals(0, param.getNearLosslessError(), "Near-lossless error should be 0: " + tsuid);
      assertEquals(
          0, param.getCompressionRatioFactor(), "Ratio should be 0 for lossless: " + tsuid);
    }

    // Lossy transfer syntaxes
    String[] lossySyntaxes = {
      JPEG_BASELINE_PROCESS_1,
      JPEG_EXTENDED_PROCESS_2_4,
      JPEG_SPECTRAL_SELECTION_6_8,
      JPEG_PROGRESSIVE_10_12,
      JPEG_LS_LOSSY,
      JPEG_2000_LOSSY
    };

    for (String tsuid : lossySyntaxes) {
      DicomJpegWriteParam param = DicomJpegWriteParam.buildDicomImageWriteParam(tsuid);
      assertFalse(param.isCompressionLossless(), "Should be lossy: " + tsuid);
      assertEquals(85, param.getCompressionQuality(), "Quality should be 85 for lossy: " + tsuid);
      assertEquals(2, param.getNearLosslessError(), "Near-lossless error should be 2: " + tsuid);
      assertEquals(10, param.getCompressionRatioFactor(), "Ratio should be 10 for lossy: " + tsuid);
    }
  }

  @Test
  @DisplayName("Parameter objects should be independent")
  void testParameterIndependence() {
    DicomJpegWriteParam param1 =
        DicomJpegWriteParam.buildDicomImageWriteParam(JPEG_BASELINE_PROCESS_1);
    DicomJpegWriteParam param2 =
        DicomJpegWriteParam.buildDicomImageWriteParam(JPEG_BASELINE_PROCESS_1);

    // Modify param1
    param1.setPrediction(5);
    param1.setCompressionQuality(60);
    param1.setSourceRegion(new Rectangle(10, 10, 100, 100));

    // param2 should remain unchanged
    assertEquals(1, param2.getPrediction());
    assertEquals(85, param2.getCompressionQuality());
    assertNull(param2.getSourceRegion());

    // Modify param2
    param2.setPrediction(7);
    param2.setCompressionQuality(90);
    param2.setSourceRegion(new Rectangle(20, 20, 200, 200));

    // param1 should remain unchanged from its modifications
    assertEquals(5, param1.getPrediction());
    assertEquals(60, param1.getCompressionQuality());
    assertEquals(new Rectangle(10, 10, 100, 100), param1.getSourceRegion());
  }

  @Test
  @DisplayName("Complex parameter combinations should work correctly")
  void testComplexParameterCombinations() {
    // Test JPEG-LS near-lossless configuration
    DicomJpegWriteParam jpegLSParam = DicomJpegWriteParam.buildDicomImageWriteParam(JPEG_LS_LOSSY);
    jpegLSParam.setNearLosslessError(3);
    jpegLSParam.setCompressionQuality(90);
    jpegLSParam.setSourceRegion(new Rectangle(64, 64, 256, 256));

    assertEquals(TransferSyntaxType.JPEG_LS, jpegLSParam.getType());
    assertFalse(jpegLSParam.isCompressionLossless());
    assertEquals(3, jpegLSParam.getNearLosslessError());
    assertEquals(90, jpegLSParam.getCompressionQuality());
    assertEquals(new Rectangle(64, 64, 256, 256), jpegLSParam.getSourceRegion());

    // Test JPEG-2000 with high compression ratio
    DicomJpegWriteParam jpeg2000Param =
        DicomJpegWriteParam.buildDicomImageWriteParam(JPEG_2000_LOSSY);
    jpeg2000Param.setCompressionRatioFactor(50);
    jpeg2000Param.setCompressionQuality(70);

    assertEquals(TransferSyntaxType.JPEG_2000, jpeg2000Param.getType());
    assertFalse(jpeg2000Param.isCompressionLossless());
    assertEquals(50, jpeg2000Param.getCompressionRatioFactor());
    assertEquals(70, jpeg2000Param.getCompressionQuality());

    // Test lossless JPEG with specific prediction mode
    DicomJpegWriteParam losslessParam =
        DicomJpegWriteParam.buildDicomImageWriteParam(JPEG_LOSSLESS_PROCESS_14);
    losslessParam.setPrediction(4);
    losslessParam.setPointTransform(1);

    assertEquals(TransferSyntaxType.JPEG_LOSSLESS, losslessParam.getType());
    assertTrue(losslessParam.isCompressionLossless());
    assertEquals(4, losslessParam.getPrediction());
    assertEquals(1, losslessParam.getPointTransform());
    assertEquals(4, losslessParam.getJpegMode());
  }

  @Test
  @DisplayName("Real-world DICOM image compression scenarios")
  void testRealWorldScenarios() {
    // CT scan compression (typically JPEG baseline or lossless)
    DicomJpegWriteParam ctParam =
        DicomJpegWriteParam.buildDicomImageWriteParam(JPEG_BASELINE_PROCESS_1);
    ctParam.setCompressionQuality(95); // High quality for CT
    ctParam.setSourceRegion(new Rectangle(0, 0, 512, 512)); // Typical CT matrix

    assertEquals(95, ctParam.getCompressionQuality());
    assertEquals(new Rectangle(0, 0, 512, 512), ctParam.getSourceRegion());
    assertFalse(ctParam.isCompressionLossless());

    // MR scan lossless compression
    DicomJpegWriteParam mrParam = DicomJpegWriteParam.buildDicomImageWriteParam(JPEG_LOSSLESS_SV1);
    mrParam.setSourceRegion(new Rectangle(0, 0, 256, 256)); // Typical MR matrix

    assertTrue(mrParam.isCompressionLossless());
    assertEquals(1, mrParam.getPrediction()); // Default prediction for SV1
    assertEquals(new Rectangle(0, 0, 256, 256), mrParam.getSourceRegion());

    // Digital radiography with JPEG-2000
    DicomJpegWriteParam drParam = DicomJpegWriteParam.buildDicomImageWriteParam(JPEG_2000_LOSSY);
    drParam.setCompressionRatioFactor(20); // Visually near-lossless
    drParam.setSourceRegion(new Rectangle(0, 0, 2048, 2048)); // High-res DR

    assertEquals(20, drParam.getCompressionRatioFactor());
    assertEquals(new Rectangle(0, 0, 2048, 2048), drParam.getSourceRegion());
    assertFalse(drParam.isCompressionLossless());

    // Pathology slide with region of interest
    DicomJpegWriteParam pathoParam =
        DicomJpegWriteParam.buildDicomImageWriteParam(JPEG_2000_LOSSLESS);
    pathoParam.setSourceRegion(new Rectangle(1000, 1000, 2000, 2000)); // ROI in large slide

    assertTrue(pathoParam.isCompressionLossless());
    assertEquals(new Rectangle(1000, 1000, 2000, 2000), pathoParam.getSourceRegion());
    assertEquals(0, pathoParam.getCompressionRatioFactor());
  }
}
