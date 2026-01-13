/*
 * Copyright (c) 2025 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.dcm4che3.img.util;

import static org.junit.jupiter.api.Assertions.*;

import java.awt.image.DataBuffer;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.VR;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.weasis.opencv.data.LookupTableCV;
import org.weasis.opencv.op.lut.LutParameters;

/**
 * Test class for {@link RescaleUtils}.
 *
 * <p>This test class validates rescale operations, lookup table creation, and pixel value
 * transformations using real test data instead of mocks.
 */
@DisplayNameGeneration(ReplaceUnderscores.class)
class RescaleUtilsTest {

  // Test data constants
  private static final double DELTA = 0.001;
  private static final int STANDARD_8BIT_ENTRIES = 256;

  @Nested
  class CreateRescaleRampLut {

    @Test
    void should_create_valid_lookup_table_from_lut_parameters() {
      var params = createStandardLutParameters(100.0, 1.5, 8, false, false, 8);

      var result = RescaleUtils.createRescaleRampLut(params);

      assertAll(
          () -> assertNotNull(result),
          () -> assertEquals(STANDARD_8BIT_ENTRIES, result.getNumEntries()),
          () -> assertEquals(0, result.getOffset()),
          () -> assertEquals(DataBuffer.TYPE_BYTE, result.getDataType()),
          () -> assertEquals(100, result.lookup(0, 0), 1), // 0 * 1.5 + 100
          () -> assertEquals(250, result.lookup(0, 100), 1) // 100 * 1.5 + 100
          );
    }

    @ParameterizedTest
    @CsvSource({
      "0.0, 1.0, 8, false, false, 8, 256", // Identity transform
      "10.0, 2.0, 8, false, false, 8, 256", // Scale and offset
      "1000.0, 0.5, 16, false, false, 16, 65536", // 16-bit unsigned
      "50.0, 2.0, 12, true, false, 12, 4096" // Signed 12-bit
    })
    void should_handle_various_parameter_combinations(
        double intercept,
        double slope,
        int bitsStored,
        boolean isSigned,
        boolean outputSigned,
        int bitsOutput,
        int expectedEntries) {

      var result =
          RescaleUtils.createRescaleRampLut(
              intercept, slope, bitsStored, isSigned, outputSigned, bitsOutput);

      var expectedDataType = bitsOutput <= 8 ? DataBuffer.TYPE_BYTE : DataBuffer.TYPE_USHORT;
      var maxOutputValue =
          outputSigned ? (bitsOutput <= 8 ? 127 : 32767) : (bitsOutput <= 8 ? 255 : 65535);
      var minOutputValue = outputSigned ? (bitsOutput <= 8 ? -128 : -32768) : 0;

      var expectedRescaledValue =
          Math.max(
              minOutputValue,
              Math.min(maxOutputValue, (int) Math.round(result.getOffset() * slope + intercept)));

      assertAll(
          () -> assertNotNull(result),
          () -> assertEquals(expectedEntries, result.getNumEntries()),
          () -> assertEquals(expectedDataType, result.getDataType()),
          () -> assertEquals(expectedRescaledValue, result.lookup(0, result.getOffset())));
    }

    @Test
    void should_handle_min_max_constraints() {
      var result = RescaleUtils.createRescaleRampLut(50.0, 2.0, 10, 200, 8, false, false, false, 8);

      assertAll(
          () -> assertNotNull(result),
          () -> assertTrue(result.getNumEntries() > 0),
          () -> assertEquals(DataBuffer.TYPE_BYTE, result.getDataType()));
    }

    @Test
    void should_invert_values_when_inverse_is_true() {
      var normal = RescaleUtils.createRescaleRampLut(0.0, 1.0, 8, false, false, 8);
      var inverted =
          RescaleUtils.createRescaleRampLut(
              0.0, 1.0, Integer.MIN_VALUE, Integer.MAX_VALUE, 8, false, true, false, 8);

      var normalValue = normal.lookup(0, 100);
      var invertedValue = inverted.lookup(0, 100);

      assertAll(
          () -> assertNotNull(normal),
          () -> assertNotNull(inverted),
          () -> assertEquals(255 - normalValue, invertedValue, 1));
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 8, 12, 16})
    void should_handle_various_bit_depths(int bitsStored) {
      var result = RescaleUtils.createRescaleRampLut(0.0, 1.0, bitsStored, false, false, 8);

      var expectedEntries = 1 << Math.min(bitsStored, 16);

      assertAll(
          () -> assertNotNull(result),
          () -> assertTrue(result.getNumEntries() > 0),
          () -> {
            if (bitsStored <= 16) {
              assertEquals(expectedEntries, result.getNumEntries());
            }
          });
    }

    @ParameterizedTest
    @MethodSource("extremeValuesProvider")
    void should_handle_extreme_slope_and_intercept_values(double intercept, double slope) {
      var result = RescaleUtils.createRescaleRampLut(intercept, slope, 8, false, false, 8);

      assertAll(
          () -> assertNotNull(result),
          () -> assertTrue(result.lookup(0, 0) >= 0 && result.lookup(0, 0) <= 255));
    }

    static Stream<Arguments> extremeValuesProvider() {
      return Stream.of(
          Arguments.of(0.0, 1000.0), // Very large slope
          Arguments.of(0.0, 0.001), // Very small slope
          Arguments.of(255.0, -1.0), // Negative slope
          Arguments.of(10000.0, 1.0) // Large intercept
          );
    }

    @Test
    void should_produce_monotonic_output_for_positive_slope() {
      var lut = RescaleUtils.createRescaleRampLut(10.0, 2.0, 8, false, false, 8);
      var testRange = Math.min(lut.getNumEntries(), 10);

      var prevValue = lut.lookup(0, 0);
      for (int i = 1; i < testRange; i++) {
        var currentValue = lut.lookup(0, i + lut.getOffset());
        assertTrue(
            currentValue >= prevValue || currentValue == 255,
            "Value at index " + i + " should be non-decreasing");
        prevValue = currentValue;
      }
    }
  }

  @Nested
  class ApplyPixelPadding {

    @Test
    void should_handle_null_inputs_gracefully() {
      var lut = createByteLookupTable(new byte[] {0, 50, 100, -106, -56, -1}, 0);
      var params = createPaddingParameters(100, null);

      assertAll(
          () -> assertDoesNotThrow(() -> RescaleUtils.applyPixelPaddingToModalityLUT(null, params)),
          () -> assertDoesNotThrow(() -> RescaleUtils.applyPixelPaddingToModalityLUT(lut, null)));
    }

    @Test
    void should_apply_padding_to_byte_data() {
      var originalData = new byte[] {10, 20, 30, 40, 50, 60, 70, 80};
      var lut = createByteLookupTable(originalData.clone(), 2);
      var params = createPaddingParameters(3, 5); // padding range 3-5 maps to indices 1-3

      RescaleUtils.applyPixelPaddingToModalityLUT(lut, params);
      var modifiedData = lut.getByteData(0);

      assertAll(
          () -> assertEquals(originalData[0], modifiedData[0]),
          () -> assertEquals(0, modifiedData[1]),
          () -> assertEquals(0, modifiedData[2]),
          () -> assertEquals(0, modifiedData[3]),
          () -> assertEquals(originalData[4], modifiedData[4]));
    }

    @Test
    void should_apply_inverse_padding_to_byte_data() {
      var originalData = new byte[] {10, 20, 30, 40, 50, 60, 70, 80};
      var lut = createByteLookupTable(originalData.clone(), 0);
      var params = createInversePaddingParameters(1, 2);

      RescaleUtils.applyPixelPaddingToModalityLUT(lut, params);
      var modifiedData = lut.getByteData(0);

      assertAll(
          () -> assertEquals(originalData[0], modifiedData[0]),
          () -> assertEquals((byte) 255, modifiedData[1]),
          () -> assertEquals((byte) 255, modifiedData[2]),
          () -> assertEquals(originalData[3], modifiedData[3]));
    }

    @Test
    void should_apply_padding_to_short_data() {
      var originalData = new short[] {100, 200, 300, 400, 500, 600};
      var lut = createShortLookupTable(originalData.clone(), 10);
      var params = createPaddingParameters(11, 12); // padding range 11-12 maps to indices 1-2

      RescaleUtils.applyPixelPaddingToModalityLUT(lut, params);
      var modifiedData = lut.getShortData(0);

      assertAll(
          () -> assertEquals(originalData[0], modifiedData[0]),
          () -> assertEquals(originalData[0], modifiedData[1]), // filled with first value
          () -> assertEquals(originalData[0], modifiedData[2]), // filled with first value
          () -> assertEquals(originalData[3], modifiedData[3]));
    }

    @Test
    void should_not_apply_padding_when_not_applicable() {
      var originalData = new byte[] {10, 20, 30, 40};
      var lut = createByteLookupTable(originalData.clone(), 0);
      var params = createStandardLutParameters(0.0, 1.0, 8, false, false, 8); // no padding

      RescaleUtils.applyPixelPaddingToModalityLUT(lut, params);
      var modifiedData = lut.getByteData(0);

      IntStream.range(0, originalData.length)
          .forEach(i -> assertEquals(originalData[i], modifiedData[i]));
    }
  }

  @Nested
  class Pixel2Rescale {

    @Test
    void should_convert_values_correctly_using_lookup_table() {
      // Create a simple lookup table: data[i] = i * 2
      byte[] data = new byte[10];
      for (int i = 0; i < data.length; i++) {
        data[i] = (byte) (i * 2);
      }
      LookupTableCV lut = new LookupTableCV(data, 5); // offset = 5

      assertAll(
          () -> assertEquals(0.0, RescaleUtils.pixel2rescale(lut, 5.0), DELTA),
          () -> assertEquals(2.0, RescaleUtils.pixel2rescale(lut, 6.0), DELTA),
          () -> assertEquals(8.0, RescaleUtils.pixel2rescale(lut, 9.0), DELTA),
          () -> assertEquals(18.0, RescaleUtils.pixel2rescale(lut, 14.0), DELTA),
          // Out of range values should return original
          () -> assertEquals(4.0, RescaleUtils.pixel2rescale(lut, 4.0), DELTA),
          () -> assertEquals(15.0, RescaleUtils.pixel2rescale(lut, 15.0), DELTA),
          // Null lookup should return original
          () ->
              assertEquals(100.0, RescaleUtils.pixel2rescale((LookupTableCV) null, 100.0), DELTA));
    }

    @Test
    void should_apply_rescale_formula_from_dicom_attributes() {
      var dcm = createDicomAttributesWithRescale(1.5, 100.0);

      assertAll(
          () -> assertEquals(175.0, RescaleUtils.pixel2rescale(dcm, 50.0), DELTA), // 50*1.5+100
          () -> assertEquals(100.0, RescaleUtils.pixel2rescale(dcm, 0.0), DELTA), // 0*1.5+100
          () -> assertEquals(250.0, RescaleUtils.pixel2rescale(dcm, 100.0), DELTA) // 100*1.5+100
          );
    }

    @Test
    void should_return_original_value_when_rescale_parameters_missing() {
      var dcm = createDicomAttributesWithoutRescale();

      assertAll(
          () -> assertEquals(123.45, RescaleUtils.pixel2rescale(dcm, 123.45), DELTA),
          () -> assertEquals(99.99, RescaleUtils.pixel2rescale((Attributes) null, 99.99), DELTA));
    }

    @ParameterizedTest
    @CsvSource({
      "2.0, , 10.0, 20.0", // Only slope: 10*2+0=20
      ", 50.0, 10.0, 60.0" // Only intercept: 10*1+50=60
    })
    void should_use_default_values_for_partial_rescale_parameters(
        String slopeStr, String interceptStr, double pixelValue, double expected) {
      var dcm = new Attributes();

      if (slopeStr != null && !slopeStr.isEmpty()) {
        dcm.setDouble(Tag.RescaleSlope, VR.DS, Double.parseDouble(slopeStr));
      }
      if (interceptStr != null && !interceptStr.isEmpty()) {
        dcm.setDouble(Tag.RescaleIntercept, VR.DS, Double.parseDouble(interceptStr));
      }

      assertEquals(expected, RescaleUtils.pixel2rescale(dcm, pixelValue), DELTA);
    }

    @ParameterizedTest
    @MethodSource("rescaleTestCases")
    void should_handle_various_rescale_scenarios(
        double slope, double intercept, double pixelValue, double expected) {
      var dcm = createDicomAttributesWithRescale(slope, intercept);
      assertEquals(expected, RescaleUtils.pixel2rescale(dcm, pixelValue), DELTA);
    }

    static Stream<Arguments> rescaleTestCases() {
      return Stream.of(
          Arguments.of(1.0, 0.0, 100.0, 100.0), // Identity
          Arguments.of(2.0, 10.0, 5.0, 20.0), // Simple transform
          Arguments.of(-1.0, 255.0, 100.0, 155.0), // Negative slope
          Arguments.of(0.5, -50.0, 200.0, 50.0) // Fractional slope with negative intercept
          );
    }
  }

  @Nested
  class EdgeCases {

    @Test
    void should_handle_zero_bit_configuration() {
      var lut = RescaleUtils.createRescaleRampLut(0.0, 1.0, 0, false, false, 8);
      assertEquals(0, lut.getOffset());
      assertEquals(2, lut.getNumEntries()); // Binary value
    }

    @Test
    void should_handle_large_bit_configuration() {
      var result = RescaleUtils.createRescaleRampLut(0.0, 1.0, 20, false, false, 16);

      assertAll(() -> assertNotNull(result), () -> assertTrue(result.getNumEntries() > 0));
    }

    @Test
    void should_handle_padding_out_of_bounds() {
      var lut = createByteLookupTable(new byte[] {1, 2, 3, 4}, 10);
      var params = createPaddingParameters(20, 25); // completely out of range

      assertDoesNotThrow(() -> RescaleUtils.applyPixelPaddingToModalityLUT(lut, params));

      // Verify data unchanged
      var data = lut.getByteData(0);
      assertArrayEquals(new byte[] {1, 2, 3, 4}, data);
    }
  }

  // Helper methods for creating test data

  private static LutParameters createStandardLutParameters(
      double intercept,
      double slope,
      int bitsStored,
      boolean isSigned,
      boolean outputSigned,
      int bitsOutput) {
    return new LutParameters(
        intercept, slope, false, null, null, bitsStored, isSigned, outputSigned, bitsOutput, false);
  }

  private static LutParameters createPaddingParameters(int paddingMin, Integer paddingMax) {
    return new LutParameters(0.0, 1.0, true, paddingMin, paddingMax, 8, false, false, 8, false);
  }

  private static LutParameters createInversePaddingParameters(int paddingMin, Integer paddingMax) {
    return new LutParameters(0.0, 1.0, true, paddingMin, paddingMax, 8, false, false, 8, true);
  }

  private static LookupTableCV createByteLookupTable(byte[] data, int offset) {
    return new LookupTableCV(data, offset);
  }

  private static LookupTableCV createShortLookupTable(short[] data, int offset) {
    return new LookupTableCV(data, offset, true);
  }

  private static Attributes createDicomAttributesWithRescale(double slope, double intercept) {
    var attributes = new Attributes();
    attributes.setDouble(Tag.RescaleSlope, VR.DS, slope);
    attributes.setDouble(Tag.RescaleIntercept, VR.DS, intercept);
    return attributes;
  }

  private static Attributes createDicomAttributesWithoutRescale() {
    var attributes = new Attributes();
    attributes.setString(Tag.StudyInstanceUID, VR.UI, "1.2.3.4");
    return attributes;
  }
}
