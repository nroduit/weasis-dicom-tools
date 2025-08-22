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
import java.util.stream.Stream;
import org.dcm4che3.img.Transcoder.Format;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Test class for {@link ImageTranscodeParam}.
 *
 * <p>Validates DICOM image transcoding parameter functionality including format configuration, JPEG
 * quality settings, raw image preservation, and integration with DICOM image reading parameters
 * using real data structures.
 */
@DisplayNameGeneration(ReplaceUnderscores.class)
class ImageTranscodeParamTest {

  @Nested
  class Constructor_tests {

    @Test
    void should_create_instance_with_default_read_parameters_when_format_provided() {
      var param = new ImageTranscodeParam(Format.PNG);

      assertAll(
          "Basic constructor validation",
          () -> assertNotNull(param),
          () -> assertEquals(Format.PNG, param.getFormat()),
          () -> assertNotNull(param.getReadParam()),
          () -> assertFalse(param.getJpegCompressionQuality().isPresent()),
          () -> assertFalse(param.isPreserveRawImage().isPresent()));
    }

    @Test
    void should_default_to_JPEG_when_format_is_null() {
      var param = new ImageTranscodeParam(null);

      assertAll(
          "Null format handling",
          () -> assertEquals(Format.JPEG, param.getFormat()),
          () -> assertNotNull(param.getReadParam()));
    }

    @Test
    void should_use_provided_read_param_and_format() {
      var readParam = createTestReadParam();
      var param = new ImageTranscodeParam(readParam, Format.TIF);

      assertAll(
          "Custom parameters",
          () -> assertEquals(Format.TIF, param.getFormat()),
          () -> assertSame(readParam, param.getReadParam()),
          () -> assertFalse(param.getJpegCompressionQuality().isPresent()),
          () -> assertFalse(param.isPreserveRawImage().isPresent()));
    }

    @Test
    void should_create_default_read_param_when_null() {
      var param = new ImageTranscodeParam(null, Format.JPEG);

      assertAll(
          "Null read param handling",
          () -> assertEquals(Format.JPEG, param.getFormat()),
          () -> assertNotNull(param.getReadParam()));
    }

    @Test
    void should_use_defaults_when_both_parameters_are_null() {
      var param = new ImageTranscodeParam(null, null);

      assertAll(
          "Both null parameters",
          () -> assertEquals(Format.JPEG, param.getFormat()),
          () -> assertNotNull(param.getReadParam()));
    }
  }

  @Nested
  class Format_tests {

    @ParameterizedTest
    @EnumSource(Format.class)
    void should_correctly_configure_all_supported_formats(Format format) {
      var param = new ImageTranscodeParam(format);

      assertAll(
          "Format configuration",
          () -> assertEquals(format, param.getFormat()),
          () -> assertNotNull(param.getReadParam()));
    }

    @Test
    void should_return_same_format_instance_on_multiple_calls() {
      var param = new ImageTranscodeParam(Format.PNG);

      var format1 = param.getFormat();
      var format2 = param.getFormat();

      assertAll(
          "Format immutability",
          () -> assertSame(format1, format2),
          () -> assertEquals(Format.PNG, format1));
    }
  }

  @Nested
  class JPEG_quality_tests {

    @Test
    void should_be_empty_by_default() {
      var param = new ImageTranscodeParam(Format.JPEG);

      assertFalse(param.getJpegCompressionQuality().isPresent());
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 25, 50, 75, 85, 95, 100})
    void should_accept_valid_quality_values(int quality) {
      var param = new ImageTranscodeParam(Format.JPEG);

      param.setJpegCompressionQuality(quality);

      var result = param.getJpegCompressionQuality();
      assertAll(
          "Valid quality validation",
          () -> assertTrue(result.isPresent()),
          () -> assertEquals(quality, result.getAsInt()));
    }

    @ParameterizedTest
    @MethodSource("invalidQualityValues")
    void should_reject_invalid_quality_values(int invalidQuality, String expectedMessagePart) {
      var param = new ImageTranscodeParam(Format.JPEG);

      var exception =
          assertThrows(
              IllegalArgumentException.class,
              () -> param.setJpegCompressionQuality(invalidQuality));

      assertAll(
          "Invalid quality handling",
          () ->
              assertTrue(
                  exception.getMessage().contains("JPEG quality must be between"),
                  expectedMessagePart),
          () -> assertTrue(exception.getMessage().contains("1 and 100"), expectedMessagePart),
          () ->
              assertTrue(
                  exception.getMessage().contains("got: " + invalidQuality), expectedMessagePart),
          () -> assertFalse(param.getJpegCompressionQuality().isPresent(), expectedMessagePart));
    }

    @Test
    void should_be_settable_on_non_JPEG_formats() {
      var param = new ImageTranscodeParam(Format.PNG);

      assertDoesNotThrow(() -> param.setJpegCompressionQuality(80));

      var quality = param.getJpegCompressionQuality();
      assertAll(
          "Non-JPEG format quality",
          () -> assertTrue(quality.isPresent()),
          () -> assertEquals(80, quality.getAsInt()));
    }

    @Test
    void should_be_overwritable() {
      var param = new ImageTranscodeParam(Format.JPEG);

      param.setJpegCompressionQuality(50);
      assertEquals(50, param.getJpegCompressionQuality().getAsInt());

      param.setJpegCompressionQuality(90);
      assertEquals(90, param.getJpegCompressionQuality().getAsInt());
    }

    @Test
    void should_handle_boundary_values_correctly() {
      var param = new ImageTranscodeParam(Format.JPEG);

      // Test minimum valid value
      param.setJpegCompressionQuality(1);
      assertEquals(1, param.getJpegCompressionQuality().getAsInt());

      // Test maximum valid value
      param.setJpegCompressionQuality(100);
      assertEquals(100, param.getJpegCompressionQuality().getAsInt());
    }

    static Stream<Arguments> invalidQualityValues() {
      return Stream.of(
          Arguments.of(0, "boundary"),
          Arguments.of(-1, "negative"),
          Arguments.of(-10, "negative"),
          Arguments.of(101, "boundary"),
          Arguments.of(150, "high"),
          Arguments.of(1000, "very high"));
    }
  }

  @Nested
  class Raw_image_preservation_tests {

    @Test
    void should_be_empty_by_default() {
      var param = new ImageTranscodeParam(Format.JPEG);

      assertFalse(param.isPreserveRawImage().isPresent());
    }

    @Test
    void should_be_settable_to_true() {
      var param = new ImageTranscodeParam(Format.TIF);

      param.setPreserveRawImage(true);

      var result = param.isPreserveRawImage();
      assertAll(
          "Set to true", () -> assertTrue(result.isPresent()), () -> assertTrue(result.get()));
    }

    @Test
    void should_be_settable_to_false() {
      var param = new ImageTranscodeParam(Format.PNG);

      param.setPreserveRawImage(false);

      var result = param.isPreserveRawImage();
      assertAll(
          "Set to false", () -> assertTrue(result.isPresent()), () -> assertFalse(result.get()));
    }

    @Test
    void should_be_resettable_to_null() {
      var param = new ImageTranscodeParam(Format.JPEG);

      param.setPreserveRawImage(true);
      assertTrue(param.isPreserveRawImage().isPresent());

      param.setPreserveRawImage(null);
      assertFalse(param.isPreserveRawImage().isPresent());
    }

    @Test
    void should_be_overwritable() {
      var param = new ImageTranscodeParam(Format.TIF);

      param.setPreserveRawImage(true);
      assertTrue(param.isPreserveRawImage().get());

      param.setPreserveRawImage(false);
      assertFalse(param.isPreserveRawImage().get());

      param.setPreserveRawImage(true);
      assertTrue(param.isPreserveRawImage().get());
    }
  }

  @Nested
  class Copy_tests {

    @Test
    void should_create_independent_instance_with_same_values() {
      var readParam = createTestReadParam();
      var original = new ImageTranscodeParam(readParam, Format.PNG);

      var copy = original.copy();

      assertAll(
          "Basic copy validation",
          () -> assertNotSame(original, copy),
          () -> assertEquals(original.getFormat(), copy.getFormat()),
          () -> assertSame(original.getReadParam(), copy.getReadParam()),
          () ->
              assertEquals(original.getJpegCompressionQuality(), copy.getJpegCompressionQuality()),
          () -> assertEquals(original.isPreserveRawImage(), copy.isPreserveRawImage()));
    }

    @Test
    void should_preserve_all_configured_settings() {
      var readParam = createAdvancedReadParam();
      var original = new ImageTranscodeParam(readParam, Format.JPEG);
      original.setJpegCompressionQuality(85);
      original.setPreserveRawImage(true);

      var copy = original.copy();

      assertAll(
          "Complete settings copy",
          () -> assertEquals(Format.JPEG, copy.getFormat()),
          () -> assertSame(readParam, copy.getReadParam()),
          () -> assertEquals(85, copy.getJpegCompressionQuality().getAsInt()),
          () -> assertTrue(copy.isPreserveRawImage().get()));
    }

    @Test
    void should_be_independent_of_original() {
      var original = new ImageTranscodeParam(Format.JPEG);
      original.setJpegCompressionQuality(70);
      original.setPreserveRawImage(false);

      var copy = original.copy();
      copy.setJpegCompressionQuality(90);
      copy.setPreserveRawImage(true);

      assertAll(
          "Independence validation",
          () -> assertEquals(70, original.getJpegCompressionQuality().getAsInt()),
          () -> assertFalse(original.isPreserveRawImage().get()),
          () -> assertEquals(90, copy.getJpegCompressionQuality().getAsInt()),
          () -> assertTrue(copy.isPreserveRawImage().get()));
    }

    @Test
    void should_work_with_default_settings() {
      var original = new ImageTranscodeParam(Format.PNG);

      var copy = original.copy();

      assertAll(
          "Default settings copy",
          () -> assertEquals(Format.PNG, copy.getFormat()),
          () -> assertNotNull(copy.getReadParam()),
          () -> assertFalse(copy.getJpegCompressionQuality().isPresent()),
          () -> assertFalse(copy.isPreserveRawImage().isPresent()));
    }
  }

  @Nested
  class ToString_tests {

    @Test
    void should_provide_meaningful_representation_with_defaults() {
      var param = new ImageTranscodeParam(Format.PNG);

      var result = param.toString();

      assertAll(
          "Default toString",
          () -> assertNotNull(result),
          () -> assertTrue(result.contains("ImageTranscodeParam")),
          () -> assertTrue(result.contains("format=PNG")),
          () -> assertTrue(result.contains("jpegQuality=default")),
          () -> assertTrue(result.contains("preserveRaw=default")));
    }

    @Test
    void should_show_configured_values() {
      var param = new ImageTranscodeParam(Format.JPEG);
      param.setJpegCompressionQuality(75);
      param.setPreserveRawImage(true);

      var result = param.toString();

      assertAll(
          "Configured values toString",
          () -> assertTrue(result.contains("format=JPEG")),
          () -> assertTrue(result.contains("jpegQuality=75")),
          () -> assertTrue(result.contains("preserveRaw=true")));
    }

    @Test
    void should_handle_mixed_configured_and_default_values() {
      var param = new ImageTranscodeParam(Format.TIF);
      param.setJpegCompressionQuality(95);

      var result = param.toString();

      assertAll(
          "Mixed values toString",
          () -> assertTrue(result.contains("format=TIF")),
          () -> assertTrue(result.contains("jpegQuality=95")),
          () -> assertTrue(result.contains("preserveRaw=default")));
    }
  }

  @Nested
  class Integration_tests {

    @Test
    void should_support_complete_configuration_workflow() {
      var readParam = createTestReadParam();
      var param = new ImageTranscodeParam(readParam, Format.JPEG);

      // Configure all settings
      param.setJpegCompressionQuality(80);
      param.setPreserveRawImage(true);

      // Verify configuration
      assertAll(
          "Full configuration",
          () -> assertEquals(Format.JPEG, param.getFormat()),
          () -> assertSame(readParam, param.getReadParam()),
          () -> assertEquals(80, param.getJpegCompressionQuality().getAsInt()),
          () -> assertTrue(param.isPreserveRawImage().get()));

      // Test copy and modification
      var copy = param.copy();
      copy.setJpegCompressionQuality(60);
      copy.setPreserveRawImage(false);

      // Verify independence
      assertAll(
          "Copy independence",
          () -> assertEquals(80, param.getJpegCompressionQuality().getAsInt()),
          () -> assertTrue(param.isPreserveRawImage().get()),
          () -> assertEquals(60, copy.getJpegCompressionQuality().getAsInt()),
          () -> assertFalse(copy.isPreserveRawImage().get()));
    }

    @ParameterizedTest
    @MethodSource("formatConfigurationCombinations")
    void should_handle_multiple_format_configurations_independently(
        Format format, OptionalInt expectedQuality, Optional<Boolean> expectedPreserveRaw) {

      var param = new ImageTranscodeParam(format);

      // Apply test-specific configuration
      expectedQuality.ifPresent(param::setJpegCompressionQuality);
      expectedPreserveRaw.ifPresent(param::setPreserveRawImage);

      assertAll(
          "Format-specific configuration",
          () -> assertEquals(format, param.getFormat()),
          () -> assertEquals(expectedQuality, param.getJpegCompressionQuality()),
          () -> assertEquals(expectedPreserveRaw, param.isPreserveRawImage()));
    }

    @Test
    void should_integrate_properly_with_custom_read_param() {
      var customReadParam = createAdvancedReadParam();
      var param = new ImageTranscodeParam(customReadParam, Format.PNG);

      assertAll(
          "ReadParam integration",
          () -> assertSame(customReadParam, param.getReadParam()),
          () -> assertNotNull(param.getReadParam()),
          () -> assertTrue(param.getReadParam().getReleaseImageAfterProcessing().isPresent()));
    }

    static Stream<Arguments> formatConfigurationCombinations() {
      return Stream.of(
          Arguments.of(Format.JPEG, OptionalInt.of(85), Optional.of(false)),
          Arguments.of(Format.PNG, OptionalInt.of(70), Optional.of(true)),
          Arguments.of(Format.TIF, OptionalInt.empty(), Optional.of(true)));
    }
  }

  // Test data creation methods
  private DicomImageReadParam createTestReadParam() {
    var readParam = new DicomImageReadParam();
    readParam.setReleaseImageAfterProcessing(true);
    return readParam;
  }

  private DicomImageReadParam createAdvancedReadParam() {
    var readParam = new DicomImageReadParam();
    readParam.setReleaseImageAfterProcessing(true);
    // Could configure additional realistic parameters for comprehensive testing
    return readParam;
  }
}
