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
import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.VR;
import org.junit.jupiter.api.BeforeEach;
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
import org.weasis.opencv.op.lut.LutShape;

@DisplayNameGeneration(ReplaceUnderscores.class)
class LookupTableUtilsTest {

  // Test constants for medical imaging scenarios
  private static final double CT_BONE_WINDOW = 2000.0;
  private static final double CT_BONE_LEVEL = 400.0;
  private static final double CT_SOFT_TISSUE_WINDOW = 400.0;
  private static final double CT_SOFT_TISSUE_LEVEL = 50.0;

  private static final int STANDARD_8BIT_ENTRIES = 256;
  private static final int STANDARD_12BIT_MAX = 4095;
  private static final int LARGE_LUT_ENTRIES = 65536;

  private Attributes lutAttributes;

  @BeforeEach
  void setUp() {
    lutAttributes = new Attributes();
  }

  @Nested
  class Voi_lut_creation {

    @Test
    void should_return_null_for_null_lut_shape() {
      var result = LookupTableUtils.createVoiLut(null, 100.0, 50.0, 0, 255, 8, false, false);

      assertNull(result);
    }

    @Test
    void should_create_linear_voi_lut_for_8_bit_unsigned() {
      var lutShape = LutShape.LINEAR;

      var result = LookupTableUtils.createVoiLut(lutShape, 100.0, 128.0, 0, 255, 8, false, false);

      assertNotNull(result);
      assertEquals(DataBuffer.TYPE_BYTE, result.getDataType());
      assertEquals(0, result.getOffset());

      byte[] data = result.getByteData(0);
      assertEquals(STANDARD_8BIT_ENTRIES, data.length);

      // Test windowing: center=128, width=100, so window is [78, 178]
      assertEquals(0, Byte.toUnsignedInt(data[0])); // Below window should be 0
      assertEquals(255, Byte.toUnsignedInt(data[255])); // Above window should be 255
    }

    @Test
    void should_create_linear_voi_lut_for_8_bit_signed() {
      var lutShape = LutShape.LINEAR;

      var result = LookupTableUtils.createVoiLut(lutShape, 100.0, 0.0, -128, 127, 8, true, false);

      assertNotNull(result);
      assertEquals(DataBuffer.TYPE_BYTE, result.getDataType());

      byte[] data = result.getByteData(0);
      assertEquals(STANDARD_8BIT_ENTRIES, data.length);

      // Verify signed range
      assertTrue(data[0] >= -128 && data[0] <= 127);
      assertTrue(data[data.length - 1] >= -128 && data[data.length - 1] <= 127);
    }

    @Test
    void should_create_linear_voi_lut_for_12_bit_unsigned() {
      var lutShape = LutShape.LINEAR;

      var result =
          LookupTableUtils.createVoiLut(
              lutShape, 1000.0, 2048.0, 0, STANDARD_12BIT_MAX, 12, false, false);

      assertNotNull(result);
      assertEquals(DataBuffer.TYPE_USHORT, result.getDataType());

      short[] data = result.getShortData(0);
      assertEquals(STANDARD_12BIT_MAX + 1, data.length);
    }

    @Test
    void should_create_linear_voi_lut_for_16_bit_signed() {
      var lutShape = LutShape.LINEAR;

      var result =
          LookupTableUtils.createVoiLut(lutShape, 1000.0, 0.0, -2048, 2047, 12, true, false);

      assertNotNull(result);
      assertEquals(DataBuffer.TYPE_SHORT, result.getDataType());

      short[] data = result.getShortData(0);
      assertEquals(4096, data.length);
    }

    @ParameterizedTest
    @ValueSource(strings = {"SIGMOID", "LOG", "LOG_INV", "SIGMOID_NORM"})
    void should_create_non_linear_voi_luts(String shapeType) {
      var lutShape =
          switch (shapeType) {
            case "SIGMOID" -> LutShape.SIGMOID;
            case "LOG" -> LutShape.LOG;
            case "LOG_INV" -> LutShape.LOG_INV;
            case "SIGMOID_NORM" -> LutShape.SIGMOID_NORM;
            default -> throw new IllegalArgumentException("Unknown shape: " + shapeType);
          };

      var result = LookupTableUtils.createVoiLut(lutShape, 100.0, 128.0, 0, 255, 8, false, false);

      assertNotNull(result);
      assertEquals(DataBuffer.TYPE_BYTE, result.getDataType());

      byte[] data = result.getByteData(0);
      assertEquals(STANDARD_8BIT_ENTRIES, data.length);

      // Verify non-linear transformation produces different results than linear
      var linearResult =
          LookupTableUtils.createVoiLut(LutShape.LINEAR, 100.0, 128.0, 0, 255, 8, false, false);
      byte[] linearData = linearResult.getByteData(0);

      boolean isDifferent = IntStream.range(0, data.length).anyMatch(i -> data[i] != linearData[i]);
      assertTrue(isDifferent, "Non-linear LUT should differ from linear");
    }

    @Test
    void should_create_inverted_voi_lut() {
      var lutShape = LutShape.LINEAR;

      var normal = LookupTableUtils.createVoiLut(lutShape, 100.0, 128.0, 0, 255, 8, false, false);
      var inverted = LookupTableUtils.createVoiLut(lutShape, 100.0, 128.0, 0, 255, 8, false, true);

      assertNotNull(normal);
      assertNotNull(inverted);

      byte[] normalData = normal.getByteData(0);
      byte[] invertedData = inverted.getByteData(0);

      // Values should be inverted: normal + inverted = max + min
      for (int i = 0; i < normalData.length; i++) {
        int normalValue = Byte.toUnsignedInt(normalData[i]);
        int invertedValue = Byte.toUnsignedInt(invertedData[i]);
        assertEquals(255, normalValue + invertedValue, 2); // Allow small rounding error
      }
    }

    @Test
    void should_create_sequence_based_voi_lut() {
      var sequenceLut = createRealSequenceLut();
      var lutShape = new LutShape(sequenceLut, "Test Sequence");

      var result = LookupTableUtils.createVoiLut(lutShape, 100.0, 128.0, 0, 255, 8, false, false);

      assertNotNull(result);
      assertEquals(DataBuffer.TYPE_BYTE, result.getDataType());

      byte[] data = result.getByteData(0);
      assertEquals(STANDARD_8BIT_ENTRIES, data.length);
    }

    @ParameterizedTest
    @MethodSource("provideVoiLutParameters")
    void should_handle_various_parameter_combinations(
        double window, double level, int minValue, int maxValue, int bitsStored, boolean signed) {
      var lutShape = LutShape.LINEAR;

      var result =
          LookupTableUtils.createVoiLut(
              lutShape, window, level, minValue, maxValue, bitsStored, signed, false);

      assertNotNull(result);
      assertValidLutProperties(result, bitsStored);
    }

    @ParameterizedTest
    @CsvSource({
      "2000.0, 400.0,  0, 4095, 12, false", // CT Bone window
      "400.0,  50.0,   0, 4095, 12, false", // CT Soft tissue
      "1600.0, -600.0, 0, 4095, 12, false", // CT Lung window
      "255.0,  128.0,  0, 255,   8, false", // X-ray window
      "4096.0, 2048.0, 0, 4095, 12, false" // Full range window
    })
    void should_handle_medical_imaging_scenarios(
        double window, double level, int minValue, int maxValue, int bitsStored, boolean signed) {
      var lutShape = LutShape.LINEAR;

      var result =
          LookupTableUtils.createVoiLut(
              lutShape, window, level, minValue, maxValue, bitsStored, signed, false);

      assertNotNull(result, "Should handle medical imaging window/level values");
      assertValidLutProperties(result, bitsStored);
    }

    @Test
    void should_handle_extreme_bit_depths() {
      var lutShape = LutShape.LINEAR;

      // Test 1-bit
      var result1 = LookupTableUtils.createVoiLut(lutShape, 2.0, 1.0, 0, 1, 1, false, false);
      assertNotNull(result1);
      assertEquals(DataBuffer.TYPE_BYTE, result1.getDataType());

      // Test 16-bit (maximum)
      var result16 =
          LookupTableUtils.createVoiLut(lutShape, 65536.0, 32768.0, 0, 65535, 16, false, false);
      assertNotNull(result16);

      // Test beyond 16-bit (should be clamped)
      var result17 =
          LookupTableUtils.createVoiLut(lutShape, 65536.0, 32768.0, 0, 65535, 20, false, false);
      assertNotNull(result17);
    }

    @Test
    void should_handle_swapped_min_max_values() {
      var lutShape = LutShape.LINEAR;

      var result = LookupTableUtils.createVoiLut(lutShape, 100.0, 128.0, 255, 0, 8, false, false);

      assertNotNull(result);
      assertEquals(DataBuffer.TYPE_BYTE, result.getDataType());
      assertEquals(STANDARD_8BIT_ENTRIES, result.getByteData(0).length);
    }

    static Stream<Arguments> provideVoiLutParameters() {
      return Stream.of(
          Arguments.of(50.0, 25.0, 0, 255, 8, false),
          Arguments.of(200.0, 100.0, -128, 127, 8, true),
          Arguments.of(1000.0, 2048.0, 0, 4095, 12, false),
          Arguments.of(2000.0, 0.0, -2048, 2047, 12, true),
          Arguments.of(65536.0, 32768.0, 0, 65535, 16, false),
          Arguments.of(1.0, 128.0, 0, 255, 8, false), // Minimum window width
          Arguments.of(CT_BONE_WINDOW, CT_BONE_LEVEL, 0, 4095, 12, false),
          Arguments.of(CT_SOFT_TISSUE_WINDOW, CT_SOFT_TISSUE_LEVEL, 0, 4095, 12, false));
    }
  }

  @Nested
  class Dicom_lut_creation {

    @Test
    void should_return_empty_optional_for_null_attributes() {
      var result = LookupTableUtils.createLut(null);

      assertTrue(result.isEmpty());
    }

    @Test
    void should_return_empty_optional_for_empty_attributes() {
      var empty = new Attributes();

      var result = LookupTableUtils.createLut(empty);

      assertTrue(result.isEmpty());
    }

    @Test
    void should_create_8_bit_dicom_lut() {
      create8BitLutAttributes();

      var result = LookupTableUtils.createLut(lutAttributes);

      assertTrue(result.isPresent());
      var lut = result.get();
      assertEquals(DataBuffer.TYPE_BYTE, lut.getDataType());
      assertEquals(-100, lut.getOffset()); // From descriptor

      byte[] data = lut.getByteData(0);
      assertEquals(STANDARD_8BIT_ENTRIES, data.length);

      // Verify data integrity - should be a gradient
      for (int i = 1; i < data.length; i++) {
        assertTrue(Byte.toUnsignedInt(data[i]) >= Byte.toUnsignedInt(data[i - 1]));
      }
    }

    @Test
    void should_create_16_bit_dicom_lut() {
      create16BitLutAttributes();

      var result = LookupTableUtils.createLut(lutAttributes);

      assertTrue(result.isPresent());
      var lut = result.get();
      assertTrue(
          lut.getDataType() == DataBuffer.TYPE_SHORT
              || lut.getDataType() == DataBuffer.TYPE_USHORT);
      assertEquals(0, lut.getOffset());
    }

    @Test
    void should_handle_large_dicom_lut() {
      var attributes = new Attributes(false);
      attributes.setInt(Tag.LUTDescriptor, VR.US, 32768, 0, 16);
      attributes.setBytes(Tag.LUTData, VR.OW, create16BitLutData(32768));

      var result = LookupTableUtils.createLut(attributes);

      assertTrue(result.isPresent());
      var lut = result.get();

      // Large LUTs should use 16-bit data type
      assertTrue(
          lut.getDataType() == DataBuffer.TYPE_SHORT
              || lut.getDataType() == DataBuffer.TYPE_USHORT);
    }

    @Test
    void should_handle_big_endian_dicom_lut() {
      var bigEndianAttrs = new Attributes(true);
      bigEndianAttrs.setInt(Tag.LUTDescriptor, VR.US, STANDARD_8BIT_ENTRIES, 0, 8);
      bigEndianAttrs.setBytes(Tag.LUTData, VR.OB, createGradient8BitData());

      var result = LookupTableUtils.createLut(bigEndianAttrs);

      assertTrue(result.isPresent());
      assertNotNull(result.get());
    }

    @Test
    void should_return_empty_optional_for_invalid_descriptor() {
      lutAttributes.setInt(
          Tag.LUTDescriptor, VR.US, STANDARD_8BIT_ENTRIES, 0); // Missing third value

      var result = LookupTableUtils.createLut(lutAttributes);

      assertTrue(result.isEmpty());
    }

    @Test
    void should_return_empty_optional_for_missing_lut_data() {
      lutAttributes.setInt(Tag.LUTDescriptor, VR.US, STANDARD_8BIT_ENTRIES, 0, 8);
      // No LUT data

      var result = LookupTableUtils.createLut(lutAttributes);

      assertTrue(result.isEmpty());
    }

    @Test
    void should_handle_8_bit_lut_with_16_bit_allocation() {
      create8BitLutWith16BitAllocation();

      var result = LookupTableUtils.createLut(lutAttributes);

      assertTrue(result.isPresent());
      var lut = result.get();
      assertEquals(DataBuffer.TYPE_BYTE, lut.getDataType());

      byte[] data = lut.getByteData(0);
      assertEquals(STANDARD_8BIT_ENTRIES, data.length);
    }

    @ParameterizedTest
    @ValueSource(ints = {128, 512, 1024, 2048})
    void should_handle_various_lut_sizes(int size) {
      lutAttributes.setInt(Tag.LUTDescriptor, VR.US, size, 0, 8);
      lutAttributes.setBytes(Tag.LUTData, VR.OB, createGradientData(size));

      var result = LookupTableUtils.createLut(lutAttributes);

      assertTrue(result.isPresent());
      var lut = result.get();
      byte[] data = lut.getByteData(0);
      assertEquals(size, data.length);
    }
  }

  @Nested
  class Lut_descriptor_validation {

    @Test
    void should_extract_valid_lut_descriptor() {
      lutAttributes.setInt(Tag.LUTDescriptor, VR.US, STANDARD_8BIT_ENTRIES, -100, 8);

      int[] descriptor = LookupTableUtils.lutDescriptor(lutAttributes, Tag.LUTDescriptor);

      assertArrayEquals(
          new int[] {STANDARD_8BIT_ENTRIES, 65436, 8},
          descriptor); // -100 becomes 65436 in unsigned short
    }

    @Test
    void should_throw_exception_for_missing_lut_descriptor() {
      var exception =
          assertThrows(
              IllegalArgumentException.class,
              () -> LookupTableUtils.lutDescriptor(lutAttributes, Tag.LUTDescriptor));

      assertEquals("Missing LUT Descriptor!", exception.getMessage());
    }

    @Test
    void should_throw_exception_for_invalid_descriptor_length() {
      lutAttributes.setInt(Tag.LUTDescriptor, VR.US, STANDARD_8BIT_ENTRIES, 0); // Only 2 values

      var exception =
          assertThrows(
              IllegalArgumentException.class,
              () -> LookupTableUtils.lutDescriptor(lutAttributes, Tag.LUTDescriptor));

      assertTrue(exception.getMessage().contains("Illegal number of LUT Descriptor values"));
    }

    @ParameterizedTest
    @ValueSource(ints = {8, 16})
    void should_accept_valid_bit_depths(int bits) {
      lutAttributes.setInt(Tag.LUTDescriptor, VR.US, STANDARD_8BIT_ENTRIES, 0, bits);

      assertDoesNotThrow(() -> LookupTableUtils.lutDescriptor(lutAttributes, Tag.LUTDescriptor));
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 2, 4, 9, 10, 12, 15, 17, 32})
    void should_throw_exception_for_invalid_bit_depths(int bits) {
      lutAttributes.setInt(Tag.LUTDescriptor, VR.US, STANDARD_8BIT_ENTRIES, 0, bits);

      var exception =
          assertThrows(
              IllegalArgumentException.class,
              () -> LookupTableUtils.lutDescriptor(lutAttributes, Tag.LUTDescriptor));

      assertTrue(exception.getMessage().contains("Illegal LUT Descriptor: bits="));
    }
  }

  @Nested
  class Lut_data_extraction {

    @Test
    void should_extract_8_bit_lut_data() {
      int[] descriptor = {STANDARD_8BIT_ENTRIES, 0, 8};
      byte[] expectedData = createGradient8BitData();
      lutAttributes.setBytes(Tag.LUTData, VR.OB, expectedData);

      byte[] result = LookupTableUtils.lutData(lutAttributes, descriptor, Tag.LUTData, 0);

      assertArrayEquals(expectedData, result);
    }

    @Test
    void should_extract_16_bit_lut_data() {
      int[] descriptor = {STANDARD_8BIT_ENTRIES, 0, 16};
      byte[] lutData = create16BitLutData(STANDARD_8BIT_ENTRIES);
      lutAttributes.setBytes(Tag.LUTData, VR.OW, lutData);

      byte[] result = LookupTableUtils.lutData(lutAttributes, descriptor, Tag.LUTData, 0);

      assertNotNull(result);
      assertEquals(lutData.length / 2, result.length);
    }

    @Test
    void should_handle_zero_length_descriptor_for_65536_entries() {
      int[] descriptor = {0, 0, 16}; // 0 means 65536 entries
      byte[] lutData = create16BitLutData(LARGE_LUT_ENTRIES);
      lutAttributes.setBytes(Tag.LUTData, VR.OW, lutData);

      byte[] result = LookupTableUtils.lutData(lutAttributes, descriptor, Tag.LUTData, 0);

      assertNotNull(result);
      assertTrue(result.length > 0);
    }

    @Test
    void should_handle_big_endian_lut_data() {
      int[] descriptor = {STANDARD_8BIT_ENTRIES, 0, 8};
      byte[] lutData = createGradient8BitData();

      var bigEndianAttrs = new Attributes(true);
      bigEndianAttrs.setBytes(Tag.LUTData, VR.OB, lutData);

      byte[] result = LookupTableUtils.lutData(bigEndianAttrs, descriptor, Tag.LUTData, 0);

      assertNotNull(result);
      assertEquals(lutData.length, result.length);
    }

    @Test
    void should_throw_exception_for_data_length_mismatch() {
      int[] descriptor = {STANDARD_8BIT_ENTRIES, 0, 8};
      byte[] incorrectData = new byte[100]; // Wrong length
      lutAttributes.setBytes(Tag.LUTData, VR.OB, incorrectData);

      var exception =
          assertThrows(
              IllegalArgumentException.class,
              () -> LookupTableUtils.lutData(lutAttributes, descriptor, Tag.LUTData, 0));

      assertTrue(exception.getMessage().contains("mismatch specified value"));
    }

    @Test
    void should_handle_segmented_lut_data() {
      int[] descriptor = {STANDARD_8BIT_ENTRIES, 0, 16};
      int[] segmentedData = createValidSegmentedLutData();
      lutAttributes.setInt(Tag.SegmentedRedPaletteColorLookupTableData, VR.OW, segmentedData);

      byte[] result =
          LookupTableUtils.lutData(
              lutAttributes, descriptor, Tag.LUTData, Tag.SegmentedRedPaletteColorLookupTableData);

      assertNotNull(result);
      assertEquals(STANDARD_8BIT_ENTRIES, result.length);
    }

    @Test
    void should_throw_exception_for_missing_lut_data() {
      int[] descriptor = {STANDARD_8BIT_ENTRIES, 0, 8};

      var exception =
          assertThrows(
              IllegalArgumentException.class,
              () -> LookupTableUtils.lutData(lutAttributes, descriptor, Tag.LUTData, 0));

      assertEquals("Missing LUT Data!", exception.getMessage());
    }

    @Test
    void should_throw_exception_for_segmented_8_bit_data() {
      int[] descriptor = {STANDARD_8BIT_ENTRIES, 0, 8}; // 8-bit
      int[] segmentedData = createValidSegmentedLutData();
      lutAttributes.setInt(Tag.SegmentedRedPaletteColorLookupTableData, VR.OW, segmentedData);

      var exception =
          assertThrows(
              IllegalArgumentException.class,
              () ->
                  LookupTableUtils.lutData(
                      lutAttributes,
                      descriptor,
                      Tag.LUTData,
                      Tag.SegmentedRedPaletteColorLookupTableData));

      assertTrue(exception.getMessage().contains("Segmented LUT Data with LUT Descriptor: bits=8"));
    }
  }

  @Nested
  class Integration_tests {

    @Test
    void should_work_with_ct_bone_window_scenario() {
      var lutShape = LutShape.LINEAR;

      var result =
          LookupTableUtils.createVoiLut(
              lutShape, CT_BONE_WINDOW, CT_BONE_LEVEL, -1024, 3071, 12, false, false);

      assertNotNull(result);
      assertEquals(DataBuffer.TYPE_USHORT, result.getDataType());

      short[] data = result.getShortData(0);
      assertEquals(4096, data.length); // -1024 to 3071 = 4096 values

      // Verify windowing behavior
      assertTrue(Short.toUnsignedInt(data[0]) <= Short.toUnsignedInt(data[data.length - 1]));
    }

    @Test
    void should_handle_complete_dicom_workflow() {
      // Create realistic DICOM LUT attributes
      var dicomLut = createRealisticDicomLut();

      var result = LookupTableUtils.createLut(dicomLut);

      assertTrue(result.isPresent());
      var lut = result.get();
      assertNotNull(lut);
    }

    @Test
    void should_handle_sequence_based_voi_lut_with_interpolation() {
      var customLut = createComplexSequenceLut();
      var lutShape = new LutShape(customLut, "Complex Sine Wave");

      var result = LookupTableUtils.createVoiLut(lutShape, 200.0, 100.0, 0, 511, 9, false, false);

      assertNotNull(result);
      assertEquals(DataBuffer.TYPE_USHORT, result.getDataType());

      short[] data = result.getShortData(0);
      assertEquals(512, data.length);
    }

    @Test
    void should_demonstrate_medical_imaging_presets() {
      var testCases =
          List.of(
              new MedicalPreset("CT Bone", CT_BONE_WINDOW, CT_BONE_LEVEL, 12),
              new MedicalPreset("CT Soft Tissue", CT_SOFT_TISSUE_WINDOW, CT_SOFT_TISSUE_LEVEL, 12),
              new MedicalPreset("CT Lung", 1600.0, -600.0, 12),
              new MedicalPreset("X-Ray", 255.0, 128.0, 8));

      for (var preset : testCases) {
        var result =
            LookupTableUtils.createVoiLut(
                LutShape.LINEAR,
                preset.window(),
                preset.level(),
                0,
                (1 << preset.bitsStored()) - 1,
                preset.bitsStored(),
                false,
                false);

        assertNotNull(result, "Failed for preset: " + preset.name());
        assertValidLutProperties(result, preset.bitsStored());
      }
    }
  }

  // Helper methods for creating realistic test data

  private void assertValidLutProperties(LookupTableCV lut, int bitsStored) {
    assertNotNull(lut);

    if (bitsStored <= 8) {
      assertEquals(DataBuffer.TYPE_BYTE, lut.getDataType());
      byte[] data = lut.getByteData(0);
      assertNotNull(data);
      assertTrue(data.length > 0);
    } else {
      assertTrue(
          lut.getDataType() == DataBuffer.TYPE_SHORT
              || lut.getDataType() == DataBuffer.TYPE_USHORT);
      short[] data = lut.getShortData(0);
      assertNotNull(data);
      assertTrue(data.length > 0);
    }
  }

  private LookupTableCV createRealSequenceLut() {
    byte[] sequenceData = createGradientData(STANDARD_8BIT_ENTRIES);
    return new LookupTableCV(sequenceData, 0);
  }

  private LookupTableCV createComplexSequenceLut() {
    short[] sequenceData = new short[512];
    for (int i = 0; i < sequenceData.length; i++) {
      // Create a sine wave pattern scaled to 16-bit range
      sequenceData[i] = (short) (32767 * Math.sin(2 * Math.PI * i / sequenceData.length));
    }
    return new LookupTableCV(sequenceData, 0, true);
  }

  private void create8BitLutAttributes() {
    lutAttributes.setInt(Tag.LUTDescriptor, VR.US, STANDARD_8BIT_ENTRIES, -100, 8);
    lutAttributes.setBytes(Tag.LUTData, VR.OB, createGradient8BitData());
  }

  private void create16BitLutAttributes() {
    lutAttributes.setInt(Tag.LUTDescriptor, VR.US, STANDARD_12BIT_MAX, 0, 16);
    lutAttributes.setBytes(Tag.LUTData, VR.OW, create16BitLutData(STANDARD_12BIT_MAX));
  }

  private void create8BitLutWith16BitAllocation() {
    lutAttributes.setInt(Tag.LUTDescriptor, VR.US, STANDARD_8BIT_ENTRIES, 0, 8);

    byte[] lutData = new byte[512]; // 16-bit allocation for 8-bit data
    for (int i = 0; i < STANDARD_8BIT_ENTRIES; i++) {
      lutData[i * 2] = (byte) i; // Low byte
      lutData[i * 2 + 1] = 0; // High byte (padding)
    }
    lutAttributes.setBytes(Tag.LUTData, VR.OW, lutData);
  }

  private byte[] createGradient8BitData() {
    return createGradientData(STANDARD_8BIT_ENTRIES);
  }

  private byte[] createGradientData(int size) {
    byte[] data = new byte[size];
    for (int i = 0; i < size; i++) {
      data[i] = (byte) (i * 255 / (size - 1));
    }
    return data;
  }

  private byte[] create16BitLutData(int entries) {
    byte[] data = new byte[entries * 2];
    for (int i = 0; i < entries; i++) {
      int value = i * 65535 / (entries - 1); // Scale to 16-bit range
      data[i * 2] = (byte) (value & 0xFF); // Low byte
      data[i * 2 + 1] = (byte) ((value >> 8) & 0xFF); // High byte
    }
    return data;
  }

  private int[] createValidSegmentedLutData() {
    // Create simple valid segmented LUT data according to DICOM standard
    // Total should generate exactly 256 values for the descriptor {256, 0, 16}

    var data = new ArrayList<Integer>();

    // First discrete segment: 128 values (0-127)
    data.add(0); // opcode: discrete
    data.add(128); // count: 128 values follow
    for (int i = 0; i < 128; i++) {
      data.add(i * 2); // Values 0, 2, 4, 6, ..., 254
    }

    // Linear segment: 128 values interpolating from 254 to 65535
    data.add(1); // opcode: linear
    data.add(128); // count: 128 interpolated values
    data.add(65535); // end value

    return data.stream().mapToInt(Integer::intValue).toArray();
  }

  private Attributes createRealisticDicomLut() {
    var attrs = new Attributes();
    attrs.setInt(Tag.LUTDescriptor, VR.US, 1024, 0, 12);

    // Create realistic 12-bit LUT data
    byte[] lutData = new byte[1024 * 2];
    for (int i = 0; i < 1024; i++) {
      int value = (int) (4095 * (1.0 - Math.exp(-i / 256.0))); // Exponential curve
      lutData[i * 2] = (byte) (value & 0xFF);
      lutData[i * 2 + 1] = (byte) ((value >> 8) & 0xFF);
    }
    attrs.setBytes(Tag.LUTData, VR.OW, lutData);

    return attrs;
  }

  private record MedicalPreset(String name, double window, double level, int bitsStored) {}
}
