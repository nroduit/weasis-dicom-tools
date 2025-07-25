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

import java.util.Optional;
import java.util.OptionalInt;
import org.dcm4che3.img.Transcoder.Format;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Test class for {@link ImageTranscodeParam}.
 *
 * <p>This test class validates DICOM image transcoding parameter functionality including format
 * configuration, JPEG quality settings, raw image preservation, and integration with DICOM image
 * reading parameters using real data structures and comprehensive validation.
 */
class ImageTranscodeParamTest {

  private DicomImageReadParam testReadParam;

  @BeforeEach
  void setUp() {
    testReadParam = createTestReadParam();
  }

  // Constructor Tests

  @Test
  @DisplayName("Constructor with format should create instance with default read parameters")
  void testConstructorWithFormat() {
    ImageTranscodeParam param = new ImageTranscodeParam(Format.PNG);

    assertNotNull(param);
    assertEquals(Format.PNG, param.getFormat());
    assertNotNull(param.getReadParam());
    assertFalse(param.getJpegCompressionQuality().isPresent());
    assertFalse(param.isPreserveRawImage().isPresent());
  }

  @Test
  @DisplayName("Constructor with null format should default to JPEG")
  void testConstructorWithNullFormat() {
    ImageTranscodeParam param = new ImageTranscodeParam((Format) null);

    assertEquals(Format.JPEG, param.getFormat());
    assertNotNull(param.getReadParam());
  }

  @Test
  @DisplayName("Constructor with custom read param and format should use provided values")
  void testConstructorWithCustomReadParam() {
    ImageTranscodeParam param = new ImageTranscodeParam(testReadParam, Format.TIF);

    assertEquals(Format.TIF, param.getFormat());
    assertSame(testReadParam, param.getReadParam());
    assertFalse(param.getJpegCompressionQuality().isPresent());
    assertFalse(param.isPreserveRawImage().isPresent());
  }

  @Test
  @DisplayName("Constructor with null read param should create default")
  void testConstructorWithNullReadParam() {
    ImageTranscodeParam param = new ImageTranscodeParam(null, Format.JPEG);

    assertEquals(Format.JPEG, param.getFormat());
    assertNotNull(param.getReadParam());
    // Should be a different instance than our test param
    assertNotSame(testReadParam, param.getReadParam());
  }

  @Test
  @DisplayName("Constructor with both null parameters should use defaults")
  void testConstructorWithBothNullParams() {
    ImageTranscodeParam param = new ImageTranscodeParam(null, null);

    assertEquals(Format.JPEG, param.getFormat());
    assertNotNull(param.getReadParam());
  }

  // Format Tests

  @ParameterizedTest
  @EnumSource(Format.class)
  @DisplayName("All supported formats should be correctly configured")
  void testAllSupportedFormats(Format format) {
    ImageTranscodeParam param = new ImageTranscodeParam(format);

    assertEquals(format, param.getFormat());
    assertNotNull(param.getReadParam());
  }

  @Test
  @DisplayName("getFormat should return immutable format")
  void testGetFormatImmutable() {
    ImageTranscodeParam param = new ImageTranscodeParam(Format.PNG);
    Format format1 = param.getFormat();
    Format format2 = param.getFormat();

    assertSame(format1, format2);
    assertEquals(Format.PNG, format1);
  }

  // JPEG Quality Tests

  @Test
  @DisplayName("Default JPEG quality should be empty")
  void testDefaultJpegQualityEmpty() {
    ImageTranscodeParam param = new ImageTranscodeParam(Format.JPEG);

    OptionalInt quality = param.getJpegCompressionQuality();
    assertFalse(quality.isPresent());
  }

  @ParameterizedTest
  @ValueSource(ints = {1, 25, 50, 75, 85, 95, 100})
  @DisplayName("Valid JPEG quality values should be set correctly")
  void testValidJpegQualityValues(int quality) {
    ImageTranscodeParam param = new ImageTranscodeParam(Format.JPEG);

    param.setJpegCompressionQuality(quality);

    OptionalInt result = param.getJpegCompressionQuality();
    assertTrue(result.isPresent());
    assertEquals(quality, result.getAsInt());
  }

  @ParameterizedTest
  @ValueSource(ints = {0, -1, -10, 101, 150, 1000})
  @DisplayName("Invalid JPEG quality values should throw IllegalArgumentException")
  void testInvalidJpegQualityValues(int invalidQuality) {
    ImageTranscodeParam param = new ImageTranscodeParam(Format.JPEG);

    IllegalArgumentException exception =
        assertThrows(
            IllegalArgumentException.class, () -> param.setJpegCompressionQuality(invalidQuality));

    assertTrue(exception.getMessage().contains("JPEG quality must be between"));
    assertTrue(exception.getMessage().contains("1 and 100"));
    assertTrue(exception.getMessage().contains("got: " + invalidQuality));

    // Quality should remain unset after failed attempt
    assertFalse(param.getJpegCompressionQuality().isPresent());
  }

  @Test
  @DisplayName("JPEG quality should be settable on non-JPEG formats")
  void testJpegQualityOnNonJpegFormat() {
    ImageTranscodeParam param = new ImageTranscodeParam(Format.PNG);

    // Should not throw exception even though format is PNG
    assertDoesNotThrow(() -> param.setJpegCompressionQuality(80));

    OptionalInt quality = param.getJpegCompressionQuality();
    assertTrue(quality.isPresent());
    assertEquals(80, quality.getAsInt());
  }

  @Test
  @DisplayName("JPEG quality should be overwritable")
  void testJpegQualityOverwrite() {
    ImageTranscodeParam param = new ImageTranscodeParam(Format.JPEG);

    param.setJpegCompressionQuality(50);
    assertEquals(50, param.getJpegCompressionQuality().getAsInt());

    param.setJpegCompressionQuality(90);
    assertEquals(90, param.getJpegCompressionQuality().getAsInt());
  }

  // Raw Image Preservation Tests

  @Test
  @DisplayName("Default preserve raw image should be empty")
  void testDefaultPreserveRawImageEmpty() {
    ImageTranscodeParam param = new ImageTranscodeParam(Format.JPEG);

    Optional<Boolean> preserveRaw = param.isPreserveRawImage();
    assertFalse(preserveRaw.isPresent());
  }

  @Test
  @DisplayName("Preserve raw image should be settable to true")
  void testSetPreserveRawImageTrue() {
    ImageTranscodeParam param = new ImageTranscodeParam(Format.TIF);

    param.setPreserveRawImage(true);

    Optional<Boolean> result = param.isPreserveRawImage();
    assertTrue(result.isPresent());
    assertTrue(result.get());
  }

  @Test
  @DisplayName("Preserve raw image should be settable to false")
  void testSetPreserveRawImageFalse() {
    ImageTranscodeParam param = new ImageTranscodeParam(Format.PNG);

    param.setPreserveRawImage(false);

    Optional<Boolean> result = param.isPreserveRawImage();
    assertTrue(result.isPresent());
    assertFalse(result.get());
  }

  @Test
  @DisplayName("Preserve raw image should be settable to null to reset")
  void testSetPreserveRawImageNull() {
    ImageTranscodeParam param = new ImageTranscodeParam(Format.JPEG);

    // Set to true first
    param.setPreserveRawImage(true);
    assertTrue(param.isPreserveRawImage().isPresent());

    // Reset to null
    param.setPreserveRawImage(null);
    assertFalse(param.isPreserveRawImage().isPresent());
  }

  @Test
  @DisplayName("Preserve raw image should be overwritable")
  void testPreserveRawImageOverwrite() {
    ImageTranscodeParam param = new ImageTranscodeParam(Format.TIF);

    param.setPreserveRawImage(true);
    assertTrue(param.isPreserveRawImage().get());

    param.setPreserveRawImage(false);
    assertFalse(param.isPreserveRawImage().get());

    param.setPreserveRawImage(true);
    assertTrue(param.isPreserveRawImage().get());
  }

  // Copy Tests

  @Test
  @DisplayName("Copy should create independent instance with same values")
  void testCopyBasic() {
    ImageTranscodeParam original = new ImageTranscodeParam(testReadParam, Format.PNG);

    ImageTranscodeParam copy = original.copy();

    assertNotSame(original, copy);
    assertEquals(original.getFormat(), copy.getFormat());
    assertSame(original.getReadParam(), copy.getReadParam()); // ReadParam should be same reference
    assertEquals(original.getJpegCompressionQuality(), copy.getJpegCompressionQuality());
    assertEquals(original.isPreserveRawImage(), copy.isPreserveRawImage());
  }

  @Test
  @DisplayName("Copy should preserve all configured settings")
  void testCopyWithAllSettings() {
    ImageTranscodeParam original = new ImageTranscodeParam(testReadParam, Format.JPEG);
    original.setJpegCompressionQuality(85);
    original.setPreserveRawImage(true);

    ImageTranscodeParam copy = original.copy();

    assertEquals(Format.JPEG, copy.getFormat());
    assertSame(testReadParam, copy.getReadParam());
    assertEquals(85, copy.getJpegCompressionQuality().getAsInt());
    assertTrue(copy.isPreserveRawImage().get());
  }

  @Test
  @DisplayName("Copy should be independent - changes to copy should not affect original")
  void testCopyIndependence() {
    ImageTranscodeParam original = new ImageTranscodeParam(Format.JPEG);
    original.setJpegCompressionQuality(70);
    original.setPreserveRawImage(false);

    ImageTranscodeParam copy = original.copy();

    // Modify copy
    copy.setJpegCompressionQuality(90);
    copy.setPreserveRawImage(true);

    // Original should be unchanged
    assertEquals(70, original.getJpegCompressionQuality().getAsInt());
    assertFalse(original.isPreserveRawImage().get());

    // Copy should have new values
    assertEquals(90, copy.getJpegCompressionQuality().getAsInt());
    assertTrue(copy.isPreserveRawImage().get());
  }

  @Test
  @DisplayName("Copy should work with default/empty settings")
  void testCopyWithDefaults() {
    ImageTranscodeParam original = new ImageTranscodeParam(Format.PNG);

    ImageTranscodeParam copy = original.copy();

    assertEquals(Format.PNG, copy.getFormat());
    assertNotNull(copy.getReadParam());
    assertFalse(copy.getJpegCompressionQuality().isPresent());
    assertFalse(copy.isPreserveRawImage().isPresent());
  }

  // ToString Tests

  @Test
  @DisplayName("toString should provide meaningful representation with defaults")
  void testToStringDefaults() {
    ImageTranscodeParam param = new ImageTranscodeParam(Format.PNG);

    String result = param.toString();

    assertNotNull(result);
    assertTrue(result.contains("ImageTranscodeParam"));
    assertTrue(result.contains("format=PNG"));
    assertTrue(result.contains("jpegQuality=default"));
    assertTrue(result.contains("preserveRaw=default"));
  }

  @Test
  @DisplayName("toString should show configured values")
  void testToStringWithValues() {
    ImageTranscodeParam param = new ImageTranscodeParam(Format.JPEG);
    param.setJpegCompressionQuality(75);
    param.setPreserveRawImage(true);

    String result = param.toString();

    assertTrue(result.contains("format=JPEG"));
    assertTrue(result.contains("jpegQuality=75"));
    assertTrue(result.contains("preserveRaw=true"));
  }

  @Test
  @DisplayName("toString should handle mixed configured and default values")
  void testToStringMixed() {
    ImageTranscodeParam param = new ImageTranscodeParam(Format.TIF);
    param.setJpegCompressionQuality(95);
    // preserveRawImage left as default

    String result = param.toString();

    assertTrue(result.contains("format=TIF"));
    assertTrue(result.contains("jpegQuality=95"));
    assertTrue(result.contains("preserveRaw=default"));
  }

  // Integration Tests

  @Test
  @DisplayName("Full configuration workflow should work correctly")
  void testFullConfigurationWorkflow() {
    // Create with custom read param
    ImageTranscodeParam param = new ImageTranscodeParam(testReadParam, Format.JPEG);

    // Configure all settings
    param.setJpegCompressionQuality(80);
    param.setPreserveRawImage(true);

    // Verify all settings
    assertEquals(Format.JPEG, param.getFormat());
    assertSame(testReadParam, param.getReadParam());
    assertEquals(80, param.getJpegCompressionQuality().getAsInt());
    assertTrue(param.isPreserveRawImage().get());

    // Create copy and modify
    ImageTranscodeParam copy = param.copy();
    copy.setJpegCompressionQuality(60);
    copy.setPreserveRawImage(false);

    // Verify independence
    assertEquals(80, param.getJpegCompressionQuality().getAsInt());
    assertTrue(param.isPreserveRawImage().get());
    assertEquals(60, copy.getJpegCompressionQuality().getAsInt());
    assertFalse(copy.isPreserveRawImage().get());
  }

  @Test
  @DisplayName("Multiple format configurations should work independently")
  void testMultipleFormatConfigurations() {
    ImageTranscodeParam jpegParam = new ImageTranscodeParam(Format.JPEG);
    ImageTranscodeParam pngParam = new ImageTranscodeParam(Format.PNG);
    ImageTranscodeParam tiffParam = new ImageTranscodeParam(Format.TIF);

    // Configure each differently
    jpegParam.setJpegCompressionQuality(85);
    jpegParam.setPreserveRawImage(false);

    pngParam.setJpegCompressionQuality(70); // Even though PNG doesn't use JPEG compression
    pngParam.setPreserveRawImage(true);

    tiffParam.setPreserveRawImage(true);
    // No JPEG quality for TIFF

    // Verify each maintains its configuration
    assertEquals(Format.JPEG, jpegParam.getFormat());
    assertEquals(85, jpegParam.getJpegCompressionQuality().getAsInt());
    assertFalse(jpegParam.isPreserveRawImage().get());

    assertEquals(Format.PNG, pngParam.getFormat());
    assertEquals(70, pngParam.getJpegCompressionQuality().getAsInt());
    assertTrue(pngParam.isPreserveRawImage().get());

    assertEquals(Format.TIF, tiffParam.getFormat());
    assertFalse(tiffParam.getJpegCompressionQuality().isPresent());
    assertTrue(tiffParam.isPreserveRawImage().get());
  }

  @Test
  @DisplayName("Edge case quality values should work correctly")
  void testEdgeCaseQualityValues() {
    ImageTranscodeParam param = new ImageTranscodeParam(Format.JPEG);

    // Test minimum valid value
    param.setJpegCompressionQuality(1);
    assertEquals(1, param.getJpegCompressionQuality().getAsInt());

    // Test maximum valid value
    param.setJpegCompressionQuality(100);
    assertEquals(100, param.getJpegCompressionQuality().getAsInt());
  }

  @Test
  @DisplayName("Read param integration should work correctly")
  void testReadParamIntegration() {
    DicomImageReadParam customReadParam = createAdvancedReadParam();
    ImageTranscodeParam param = new ImageTranscodeParam(customReadParam, Format.PNG);

    assertSame(customReadParam, param.getReadParam());

    // Verify the read param is properly integrated
    DicomImageReadParam retrievedParam = param.getReadParam();
    assertNotNull(retrievedParam);
    // The read param should maintain its configuration
    assertTrue(retrievedParam.getReleaseImageAfterProcessing().isPresent());
  }

  // Helper methods for creating test data

  private DicomImageReadParam createTestReadParam() {
    DicomImageReadParam readParam = new DicomImageReadParam();
    readParam.setReleaseImageAfterProcessing(true);
    return readParam;
  }

  private DicomImageReadParam createAdvancedReadParam() {
    DicomImageReadParam readParam = new DicomImageReadParam();
    readParam.setReleaseImageAfterProcessing(true);
    // Configure additional parameters as needed for comprehensive testing
    return readParam;
  }
}
