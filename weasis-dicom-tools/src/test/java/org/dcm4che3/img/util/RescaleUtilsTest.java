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
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.VR;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.weasis.opencv.data.LookupTableCV;
import org.weasis.opencv.op.lut.LutParameters;

/**
 * Test class for {@link RescaleUtils}.
 *
 * <p>This test class validates rescale operations, lookup table creation, and pixel value
 * transformations using real test data instead of mocks.
 */
class RescaleUtilsTest {

  @Test
  @DisplayName("createRescaleRampLut with LutParameters should create valid lookup table")
  void testCreateRescaleRampLutWithParameters() {
    LutParameters params =
        new LutParameters(
            100.0, // intercept
            1.5, // slope
            false, // pixelPadding
            null, // paddingMinValue
            null, // paddingMaxValue
            8, // bitsStored
            false, // isSigned
            false, // outputSigned
            8, // bitsOutput
            false // inversePaddingMLUT
            );

    LookupTableCV result = RescaleUtils.createRescaleRampLut(params);

    assertNotNull(result);
    assertEquals(256, result.getNumEntries(), "Should have entries for 8-bit unsigned range");
    assertEquals(0, result.getOffset(), "Offset should be 0 for unsigned 8-bit");
    assertEquals(DataBuffer.TYPE_BYTE, result.getDataType());

    // Test specific values: pixel * slope + intercept
    // For pixel value 0: 0 * 1.5 + 100 = 100
    assertEquals(100, result.lookup(0, 0), 1, "Value at index 0");
    // For pixel value 100: 100 * 1.5 + 100 = 250
    assertEquals(250, result.lookup(0, 100), 1, "Value at index 100");
  }

  @ParameterizedTest
  @CsvSource({
    "0.0, 1.0, 8, false, false, 8", // Identity transform, 8-bit unsigned
    "10.0, 2.0, 8, false, false, 8", // Scale and offset, 8-bit unsigned
    "1000.0, 0.5, 16, false, false, 16", // 16-bit unsigned
    "50.0, 2.0, 12, true, false, 12", // Signed 12-bit, get unsinged lut with an offset
  })
  @DisplayName("createRescaleRampLut should handle various parameter combinations")
  void testCreateRescaleRampLutWithVariousParameters(
      double intercept,
      double slope,
      int bitsStored,
      boolean isSigned,
      boolean outputSigned,
      int bitsOutput) {

    LookupTableCV result =
        RescaleUtils.createRescaleRampLut(
            intercept, slope, bitsStored, isSigned, outputSigned, bitsOutput);

    assertNotNull(result);
    assertTrue(result.getNumEntries() > 0, "Should have positive number of entries");

    // Verify data type based on output bits
    if (bitsOutput <= 8) {
      assertEquals(DataBuffer.TYPE_BYTE, result.getDataType());
    } else {
      assertEquals(DataBuffer.TYPE_USHORT, result.getDataType());
    }

    // Test the rescale formula at offset
    int expectedValue = (int) Math.round(result.getOffset() * slope + intercept);
    int actualValue = result.lookup(0, result.getOffset());

    // Account for clamping in the implementation
    int maxValue = outputSigned ? (bitsOutput <= 8 ? 127 : 32767) : (bitsOutput <= 8 ? 255 : 65535);
    int minValue = outputSigned ? (bitsOutput <= 8 ? -128 : -32768) : 0;

    expectedValue = Math.max(minValue, Math.min(maxValue, expectedValue));
    assertEquals(expectedValue, actualValue, "Rescale formula should be applied correctly");
  }

  @Test
  @DisplayName("createRescaleRampLut with full parameters should handle min/max constraints")
  void testCreateRescaleRampLutWithMinMaxConstraints() {
    double intercept = 50.0;
    double slope = 2.0;
    int minValue = 10;
    int maxValue = 200;
    int bitsStored = 8;
    boolean isSigned = false;
    boolean inverse = false;
    boolean outputSigned = false;
    int bitsOutput = 8;

    LookupTableCV result =
        RescaleUtils.createRescaleRampLut(
            intercept,
            slope,
            minValue,
            maxValue,
            bitsStored,
            isSigned,
            inverse,
            outputSigned,
            bitsOutput);

    assertNotNull(result);

    // The actual range should be constrained by min/max and bit configuration
    assertTrue(result.getNumEntries() > 0);
    assertEquals(DataBuffer.TYPE_BYTE, result.getDataType());
  }

  @Test
  @DisplayName("createRescaleRampLut with inverse should invert values")
  void testCreateRescaleRampLutWithInverse() {
    LookupTableCV normal = RescaleUtils.createRescaleRampLut(0.0, 1.0, 8, false, false, 8);

    LookupTableCV inverted =
        RescaleUtils.createRescaleRampLut(
            0.0, 1.0, Integer.MIN_VALUE, Integer.MAX_VALUE, 8, false, true, false, 8);

    assertNotNull(normal);
    assertNotNull(inverted);

    // Check that values are inverted (complement)
    int normalValue = normal.lookup(0, 100);
    int invertedValue = inverted.lookup(0, 100);

    // For unsigned 8-bit, inverted value should be 255 - normal value
    assertEquals(255 - normalValue, invertedValue, 1, "Inverted values should be complemented");
  }

  @Test
  @DisplayName("applyPixelPaddingToModalityLUT should handle null inputs gracefully")
  void testApplyPixelPaddingWithNullInputs() {
    // Create valid LUT
    byte[] data = {0, 50, 100, (byte) 150, (byte) 200, (byte) 255};
    LookupTableCV lut = new LookupTableCV(data, 0);

    // Test with null LUT
    LutParameters params =
        new LutParameters(
            0.0, // intercept
            1.0, // slope
            true, // pixelPadding
            100, // paddingMinValue
            null, // paddingMaxValue
            8, // bitsStored
            false, // isSigned
            false, // outputSigned
            8, // bitsOutput
            false // inversePaddingMLUT
            );

    assertDoesNotThrow(() -> RescaleUtils.applyPixelPaddingToModalityLUT(null, params));

    // Test with null parameters
    assertDoesNotThrow(() -> RescaleUtils.applyPixelPaddingToModalityLUT(lut, null));
  }

  @Test
  @DisplayName("applyPixelPaddingToModalityLUT should apply padding to byte data")
  void testApplyPixelPaddingToByteLUT() {
    // Create a byte lookup table
    byte[] originalData = {10, 20, 30, 40, 50, 60, 70, 80};
    LookupTableCV lut = new LookupTableCV(originalData.clone(), 2); // offset = 2

    // Create padding parameters: padding values 3-5 (indices 1-3 in LUT)
    LutParameters params =
        new LutParameters(
            0.0, // intercept
            1.0, // slope
            true, // pixelPadding
            3, // paddingMinValue
            5, // paddingMaxValue
            8, // bitsStored
            false, // isSigned
            false, // outputSigned
            8, // bitsOutput
            false // inversePaddingMLUT
            );

    RescaleUtils.applyPixelPaddingToModalityLUT(lut, params);

    byte[] modifiedData = lut.getByteData(0);

    // Values at indices 1, 2, 3 should be set to 0 (not inverse)
    assertEquals(originalData[0], modifiedData[0], "Index 0 should be unchanged");
    assertEquals(0, modifiedData[1], "Index 1 should be padded to 0");
    assertEquals(0, modifiedData[2], "Index 2 should be padded to 0");
    assertEquals(0, modifiedData[3], "Index 3 should be padded to 0");
    assertEquals(originalData[4], modifiedData[4], "Index 4 should be unchanged");
  }

  @Test
  @DisplayName("applyPixelPaddingToModalityLUT should apply inverse padding to byte data")
  void testApplyInversePixelPaddingToByteLUT() {
    byte[] originalData = {10, 20, 30, 40, 50, 60, 70, 80};
    LookupTableCV lut = new LookupTableCV(originalData.clone(), 0);

    LutParameters params =
        new LutParameters(
            0.0, // intercept
            1.0, // slope
            true, // pixelPadding
            1, // paddingMinValue
            2, // paddingMaxValue
            8, // bitsStored
            false, // isSigned
            false, // outputSigned
            8, // bitsOutput
            true // inversePaddingMLUT
            );

    RescaleUtils.applyPixelPaddingToModalityLUT(lut, params);

    byte[] modifiedData = lut.getByteData(0);

    // With inverse padding, should fill with 255 (0xFF)
    assertEquals(originalData[0], modifiedData[0], "Index 0 should be unchanged");
    assertEquals((byte) 255, modifiedData[1], "Index 1 should be padded to 255");
    assertEquals((byte) 255, modifiedData[2], "Index 2 should be padded to 255");
    assertEquals(originalData[3], modifiedData[3], "Index 3 should be unchanged");
  }

  @Test
  @DisplayName("applyPixelPaddingToModalityLUT should apply padding to short data")
  void testApplyPixelPaddingToShortLUT() {
    short[] originalData = {100, 200, 300, 400, 500, 600};
    LookupTableCV lut = new LookupTableCV(originalData.clone(), 10, true); // offset=10

    LutParameters params =
        new LutParameters(
            0.0, // intercept
            1.0, // slope
            true, // pixelPadding
            11, // paddingMinValue (index 1)
            12, // paddingMaxValue (index 2)
            16, // bitsStored
            false, // isSigned
            false, // outputSigned
            16, // bitsOutput
            false // inversePaddingMLUT
            );

    RescaleUtils.applyPixelPaddingToModalityLUT(lut, params);

    short[] modifiedData = lut.getShortData(0);

    // Should fill with first value (data[0])
    assertEquals(originalData[0], modifiedData[0], "Index 0 should be unchanged");
    assertEquals(originalData[0], modifiedData[1], "Index 1 should be padded to first value");
    assertEquals(originalData[0], modifiedData[2], "Index 2 should be padded to first value");
    assertEquals(originalData[3], modifiedData[3], "Index 3 should be unchanged");
  }

  @Test
  @DisplayName("pixel2rescale with lookup table should convert values correctly")
  void testPixel2rescaleWithLookupTable() {
    // Create a simple lookup table: data[i] = i * 2
    byte[] data = new byte[10];
    for (int i = 0; i < data.length; i++) {
      data[i] = (byte) (i * 2);
    }
    LookupTableCV lut = new LookupTableCV(data, 5); // offset = 5

    // With offset=5, the lookup maps:
    // input 5 -> data[0] = 0
    // input 6 -> data[1] = 2
    // input 7 -> data[2] = 4
    // etc.

    // Test values within range
    assertEquals(0.0, RescaleUtils.pixel2rescale(lut, 5.0), 0.001, "Value at offset");
    assertEquals(2.0, RescaleUtils.pixel2rescale(lut, 6.0), 0.001, "Value at offset+1");
    assertEquals(8.0, RescaleUtils.pixel2rescale(lut, 9.0), 0.001, "Value in range");
    assertEquals(18.0, RescaleUtils.pixel2rescale(lut, 14.0), 0.001, "Value at max range");

    // Test values outside range - should return original value
    assertEquals(4.0, RescaleUtils.pixel2rescale(lut, 4.0), 0.001, "Value below range");
    assertEquals(15.0, RescaleUtils.pixel2rescale(lut, 15.0), 0.001, "Value above range");

    // Test with null lookup
    assertEquals(
        100.0, RescaleUtils.pixel2rescale((LookupTableCV) null, 100.0), 0.001, "Null lookup");
  }

  @Test
  @DisplayName("pixel2rescale with DICOM attributes should apply rescale formula")
  void testPixel2rescaleWithDicomAttributes() {
    // Create DICOM attributes with rescale slope and intercept
    Attributes dcm = new Attributes();
    dcm.setDouble(Tag.RescaleSlope, VR.DS, 1.5);
    dcm.setDouble(Tag.RescaleIntercept, VR.DS, 100.0);

    // Test rescale formula: value = pixel * slope + intercept
    double result = RescaleUtils.pixel2rescale(dcm, 50.0);
    double expected = 50.0 * 1.5 + 100.0; // = 175.0
    assertEquals(expected, result, 0.001, "Should apply rescale formula");

    // Test with zero pixel value
    result = RescaleUtils.pixel2rescale(dcm, 0.0);
    expected = 0.0 * 1.5 + 100.0; // = 100.0
    assertEquals(expected, result, 0.001, "Should handle zero pixel value");
  }

  @Test
  @DisplayName("pixel2rescale with DICOM attributes missing rescale parameters")
  void testPixel2rescaleWithMissingRescaleParameters() {
    // Create DICOM attributes without rescale parameters
    Attributes dcm = new Attributes();
    dcm.setString(Tag.StudyInstanceUID, VR.UI, "1.2.3.4");

    // Should return original value when no rescale parameters
    double result = RescaleUtils.pixel2rescale(dcm, 123.45);
    assertEquals(123.45, result, 0.001, "Should return original value");

    // Test with null attributes
    result = RescaleUtils.pixel2rescale((Attributes) null, 99.99);
    assertEquals(99.99, result, 0.001, "Should handle null attributes");
  }

  @Test
  @DisplayName("pixel2rescale with partial DICOM rescale parameters")
  void testPixel2rescaleWithPartialRescaleParameters() {
    // Test with only slope
    Attributes dcmSlope = new Attributes();
    dcmSlope.setDouble(Tag.RescaleSlope, VR.DS, 2.0);

    double result = RescaleUtils.pixel2rescale(dcmSlope, 10.0);
    double expected = 10.0 * 2.0 + 0.0; // intercept defaults to 0
    assertEquals(expected, result, 0.001, "Should use default intercept of 0");

    // Test with only intercept
    Attributes dcmIntercept = new Attributes();
    dcmIntercept.setDouble(Tag.RescaleIntercept, VR.DS, 50.0);

    result = RescaleUtils.pixel2rescale(dcmIntercept, 10.0);
    expected = 10.0 * 1.0 + 50.0; // slope defaults to 1
    assertEquals(expected, result, 0.001, "Should use default slope of 1");
  }

  @ParameterizedTest
  @ValueSource(ints = {1, 8, 12, 16})
  @DisplayName("createRescaleRampLut should handle various bit depths")
  void testCreateRescaleRampLutWithVariousBitDepths(int bitsStored) {
    LookupTableCV result = RescaleUtils.createRescaleRampLut(0.0, 1.0, bitsStored, false, false, 8);

    assertNotNull(result);
    assertTrue(result.getNumEntries() > 0, "Should have entries for " + bitsStored + " bits");

    // Verify the expected number of entries for power-of-2 bit depths
    int expectedEntries = 1 << Math.min(bitsStored, 16);
    if (bitsStored <= 16) {
      assertEquals(
          expectedEntries,
          result.getNumEntries(),
          "Should have " + expectedEntries + " entries for " + bitsStored + " bits");
    }
  }

  @Test
  @DisplayName("createRescaleRampLut should handle extreme slope and intercept values")
  void testCreateRescaleRampLutWithExtremeValues() {
    // Test with very large slope
    LookupTableCV result1 = RescaleUtils.createRescaleRampLut(0.0, 1000.0, 8, false, false, 8);
    assertNotNull(result1);

    // Test with very small slope
    LookupTableCV result2 = RescaleUtils.createRescaleRampLut(0.0, 0.001, 8, false, false, 8);
    assertNotNull(result2);

    // Test with negative slope
    LookupTableCV result3 = RescaleUtils.createRescaleRampLut(255.0, -1.0, 8, false, false, 8);
    assertNotNull(result3);

    // Test with large intercept
    LookupTableCV result4 = RescaleUtils.createRescaleRampLut(10000.0, 1.0, 8, false, false, 8);
    assertNotNull(result4);

    // Values should be clamped to valid output range
    assertTrue(
        result4.lookup(0, 0) >= 0 && result4.lookup(0, 0) <= 255,
        "Output should be clamped to valid range");
  }

  @Test
  @DisplayName("createRescaleRampLut should produce monotonic output for positive slope")
  void testCreateRescaleRampLutMonotonicity() {
    LookupTableCV lut = RescaleUtils.createRescaleRampLut(10.0, 2.0, 8, false, false, 8);

    // For positive slope, output should be non-decreasing
    int prevValue = lut.lookup(0, 0);
    for (int i = 1; i < Math.min(lut.getNumEntries(), 10); i++) {
      int currentValue = lut.lookup(0, i + lut.getOffset());
      assertTrue(
          currentValue >= prevValue || currentValue == 255, // unless clamped to max
          "Values should be non-decreasing for positive slope");
      prevValue = currentValue;
    }
  }
}
