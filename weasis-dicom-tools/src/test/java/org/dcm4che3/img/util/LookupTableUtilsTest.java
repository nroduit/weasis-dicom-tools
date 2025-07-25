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
import java.util.Optional;
import java.util.stream.Stream;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.VR;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.weasis.opencv.data.LookupTableCV;
import org.weasis.opencv.op.lut.LutShape;

class LookupTableUtilsTest {

  private Attributes lutAttributes;

  @BeforeEach
  void setUp() {
    lutAttributes = new Attributes();
  }

  @Nested
  @DisplayName("VOI LUT Creation")
  class VoiLutCreation {

    @Test
    @DisplayName("Should return null for null LUT shape")
    void shouldReturnNullForNullLutShape() {
      LookupTableCV result =
          LookupTableUtils.createVoiLut(null, 100.0, 50.0, 0, 255, 8, false, false);

      assertNull(result);
    }

    @Test
    @DisplayName("Should create linear VOI LUT for 8-bit unsigned")
    void shouldCreateLinearVoiLutFor8BitUnsigned() {
      LutShape lutShape = createLinearLutShape();

      LookupTableCV result =
          LookupTableUtils.createVoiLut(lutShape, 100.0, 128.0, 0, 255, 8, false, false);

      assertNotNull(result);
      assertEquals(DataBuffer.TYPE_BYTE, result.getDataType());
      assertEquals(0, result.getOffset());

      // Test specific values
      byte[] data = result.getByteData(0);
      assertEquals(256, data.length);

      // Test windowing: center=128, width=100, so window is [78, 178]
      assertEquals(0, data[0] & 0xFF); // Below window should be 0
      assertEquals(255, data[255] & 0xFF); // Above window should be 255
    }

    @Test
    @DisplayName("Should create linear VOI LUT for 8-bit signed")
    void shouldCreateLinearVoiLutFor8BitSigned() {
      LutShape lutShape = createLinearLutShape();

      LookupTableCV result =
          LookupTableUtils.createVoiLut(lutShape, 100.0, 0.0, -128, 127, 8, true, false);

      assertNotNull(result);
      assertEquals(DataBuffer.TYPE_BYTE, result.getDataType());

      byte[] data = result.getByteData(0);
      assertEquals(256, data.length);
    }

    @Test
    @DisplayName("Should create linear VOI LUT for 16-bit unsigned")
    void shouldCreateLinearVoiLutFor16BitUnsigned() {
      LutShape lutShape = createLinearLutShape();

      LookupTableCV result =
          LookupTableUtils.createVoiLut(lutShape, 1000.0, 2048.0, 0, 4095, 12, false, false);

      assertNotNull(result);
      assertEquals(DataBuffer.TYPE_USHORT, result.getDataType());

      short[] data = result.getShortData(0);
      assertEquals(4096, data.length);
    }

    @Test
    @DisplayName("Should create linear VOI LUT for 16-bit signed")
    void shouldCreateLinearVoiLutFor16BitSigned() {
      LutShape lutShape = createLinearLutShape();

      LookupTableCV result =
          LookupTableUtils.createVoiLut(lutShape, 1000.0, 0.0, -2048, 2047, 12, true, false);

      assertNotNull(result);
      assertEquals(DataBuffer.TYPE_SHORT, result.getDataType());

      short[] data = result.getShortData(0);
      assertEquals(4096, data.length);
    }

    @Test
    @DisplayName("Should create sigmoid VOI LUT")
    void shouldCreateSigmoidVoiLut() {
      LutShape lutShape = createSigmoidLutShape();

      LookupTableCV result =
          LookupTableUtils.createVoiLut(lutShape, 100.0, 128.0, 0, 255, 8, false, false);

      assertNotNull(result);
      assertEquals(DataBuffer.TYPE_BYTE, result.getDataType());

      byte[] data = result.getByteData(0);
      assertEquals(256, data.length);

      // Sigmoid should have smooth transition
      int midValue = data[128] & 0xFF;
      assertTrue(
          midValue > 100 && midValue < 200, "Sigmoid midpoint should be in reasonable range");
    }

    @Test
    @DisplayName("Should create logarithmic VOI LUT")
    void shouldCreateLogarithmicVoiLut() {
      LutShape lutShape = createLogarithmicLutShape();

      LookupTableCV result =
          LookupTableUtils.createVoiLut(lutShape, 100.0, 128.0, 0, 255, 8, false, false);

      assertNotNull(result);
      assertEquals(DataBuffer.TYPE_BYTE, result.getDataType());

      byte[] data = result.getByteData(0);
      assertEquals(256, data.length);
    }

    @Test
    @DisplayName("Should create exponential VOI LUT")
    void shouldCreateExponentialVoiLut() {
      LutShape lutShape = createExponentialLutShape();

      LookupTableCV result =
          LookupTableUtils.createVoiLut(lutShape, 100.0, 128.0, 0, 255, 8, false, false);

      assertNotNull(result);
      assertEquals(DataBuffer.TYPE_BYTE, result.getDataType());

      byte[] data = result.getByteData(0);
      assertEquals(256, data.length);
    }

    @Test
    @DisplayName("Should create inverted VOI LUT")
    void shouldCreateInvertedVoiLut() {
      LutShape lutShape = createLinearLutShape();

      LookupTableCV normal =
          LookupTableUtils.createVoiLut(lutShape, 100.0, 128.0, 0, 255, 8, false, false);
      LookupTableCV inverted =
          LookupTableUtils.createVoiLut(lutShape, 100.0, 128.0, 0, 255, 8, false, true);

      assertNotNull(normal);
      assertNotNull(inverted);

      byte[] normalData = normal.getByteData(0);
      byte[] invertedData = inverted.getByteData(0);

      // Values should be inverted
      assertEquals(normalData[0] & 0xFF, 255 - (invertedData[0] & 0xFF));
      assertEquals(normalData[255] & 0xFF, 255 - (invertedData[255] & 0xFF));
    }

    @Test
    @DisplayName("Should create sequence-based VOI LUT")
    void shouldCreateSequenceBasedVoiLut() {
      // Create a sequence LUT with custom lookup table
      LookupTableCV sequenceLut = createCustomSequenceLut();
      LutShape lutShape = createSequenceLutShape(sequenceLut);

      LookupTableCV result =
          LookupTableUtils.createVoiLut(lutShape, 100.0, 128.0, 0, 255, 8, false, false);

      assertNotNull(result);
      assertEquals(DataBuffer.TYPE_BYTE, result.getDataType());

      byte[] data = result.getByteData(0);
      assertEquals(256, data.length);
    }

    @ParameterizedTest
    @MethodSource("provideVoiLutParameters")
    @DisplayName("Should handle various VOI LUT parameter combinations")
    void shouldHandleVariousVoiLutParameters(
        double window, double level, int minValue, int maxValue, int bitsStored, boolean signed) {
      LutShape lutShape = createLinearLutShape();

      LookupTableCV result =
          LookupTableUtils.createVoiLut(
              lutShape, window, level, minValue, maxValue, bitsStored, signed, false);

      assertNotNull(result);

      if (bitsStored <= 8) {
        assertEquals(DataBuffer.TYPE_BYTE, result.getDataType());
      } else {
        assertTrue(
            result.getDataType() == DataBuffer.TYPE_SHORT
                || result.getDataType() == DataBuffer.TYPE_USHORT);
      }
    }

    static Stream<Arguments> provideVoiLutParameters() {
      return Stream.of(
          Arguments.of(50.0, 25.0, 0, 255, 8, false),
          Arguments.of(200.0, 100.0, -128, 127, 8, true),
          Arguments.of(1000.0, 2048.0, 0, 4095, 12, false),
          Arguments.of(2000.0, 0.0, -2048, 2047, 12, true),
          Arguments.of(65536.0, 32768.0, 0, 65535, 16, false),
          Arguments.of(1.0, 128.0, 0, 255, 8, false), // Minimum window width
          Arguments.of(100.0, 128.0, 100, 50, 8, false) // Swapped min/max
          );
    }

    @Test
    @DisplayName("Should handle extreme bit depths")
    void shouldHandleExtremeBitDepths() {
      LutShape lutShape = createLinearLutShape();

      // Test 1-bit
      LookupTableCV result1 =
          LookupTableUtils.createVoiLut(lutShape, 2.0, 1.0, 0, 1, 1, false, false);
      assertNotNull(result1);

      // Test 16-bit (maximum)
      LookupTableCV result16 =
          LookupTableUtils.createVoiLut(lutShape, 65536.0, 32768.0, 0, 65535, 16, false, false);
      assertNotNull(result16);

      // Test beyond 16-bit (should be clamped)
      LookupTableCV result17 =
          LookupTableUtils.createVoiLut(lutShape, 65536.0, 32768.0, 0, 65535, 17, false, false);
      assertNotNull(result17);
    }
  }

  @Nested
  @DisplayName("DICOM LUT Creation")
  class DicomLutCreation {

    @Test
    @DisplayName("Should return empty Optional for null attributes")
    void shouldReturnEmptyOptionalForNullAttributes() {
      Optional<LookupTableCV> result = LookupTableUtils.createLut(null);

      assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("Should return empty Optional for empty attributes")
    void shouldReturnEmptyOptionalForEmptyAttributes() {
      Attributes empty = new Attributes();

      Optional<LookupTableCV> result = LookupTableUtils.createLut(empty);

      assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("Should create 8-bit DICOM LUT")
    void shouldCreate8BitDicomLut() {
      create8BitLutAttributes();

      Optional<LookupTableCV> result = LookupTableUtils.createLut(lutAttributes);

      assertTrue(result.isPresent());
      LookupTableCV lut = result.get();
      assertEquals(DataBuffer.TYPE_BYTE, lut.getDataType());
      assertEquals(-100, lut.getOffset()); // From descriptor

      byte[] data = lut.getByteData(0);
      assertEquals(256, data.length);
    }

    @Test
    @DisplayName("Should create 16-bit DICOM LUT")
    void shouldCreate16BitDicomLut() {
      create16BitLutAttributes();

      Optional<LookupTableCV> result = LookupTableUtils.createLut(lutAttributes);

      assertTrue(result.isPresent());
      LookupTableCV lut = result.get();
      assertTrue(
          lut.getDataType() == DataBuffer.TYPE_SHORT
              || lut.getDataType() == DataBuffer.TYPE_USHORT);
      assertEquals(0, lut.getOffset());
    }

    @Test
    @DisplayName("Should handle large DICOM LUT (65536 entries)")
    void shouldHandleLargeDicomLut() {
      createLargeLutAttributes();

      Optional<LookupTableCV> result = LookupTableUtils.createLut(lutAttributes);

      assertTrue(result.isPresent());
      LookupTableCV lut = result.get();

      // Large LUTs should use appropriate data type
      assertTrue(
          lut.getDataType() == DataBuffer.TYPE_SHORT
              || lut.getDataType() == DataBuffer.TYPE_USHORT);
    }

    @Test
    @DisplayName("Should handle big-endian DICOM LUT")
    void shouldHandleBigEndianDicomLut() {
      createBigEndianLutAttributes();

      Optional<LookupTableCV> result = LookupTableUtils.createLut(lutAttributes);

      assertTrue(result.isPresent());
      LookupTableCV lut = result.get();
      assertNotNull(lut);
    }

    @Test
    @DisplayName("Should return empty Optional for invalid descriptor")
    void shouldReturnEmptyOptionalForInvalidDescriptor() {
      // Create attributes with invalid descriptor
      lutAttributes.setInt(
          Tag.LUTDescriptor, VR.US, new int[] {256, 0}); // Only 2 values instead of 3

      Optional<LookupTableCV> result = LookupTableUtils.createLut(lutAttributes);

      assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("Should return empty Optional for missing LUT data")
    void shouldReturnEmptyOptionalForMissingLutData() {
      // Create attributes with descriptor but no data
      lutAttributes.setInt(Tag.LUTDescriptor, VR.US, new int[] {256, 0, 8});

      Optional<LookupTableCV> result = LookupTableUtils.createLut(lutAttributes);

      assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("Should handle 8-bit LUT with 16-bit allocation")
    void shouldHandle8BitLutWith16BitAllocation() {
      create8BitLutWith16BitAllocation();

      Optional<LookupTableCV> result = LookupTableUtils.createLut(lutAttributes);

      assertTrue(result.isPresent());
      LookupTableCV lut = result.get();
      assertEquals(DataBuffer.TYPE_BYTE, lut.getDataType());
    }
  }

  @Nested
  @DisplayName("LUT Descriptor Validation")
  class LutDescriptorValidation {

    @Test
    @DisplayName("Should extract valid LUT descriptor")
    void shouldExtractValidLutDescriptor() {
      lutAttributes.setInt(Tag.LUTDescriptor, VR.US, 256, -100, 8);

      int[] descriptor = LookupTableUtils.lutDescriptor(lutAttributes, Tag.LUTDescriptor);
      assertArrayEquals(
          new int[] {256, 65436, 8}, descriptor); // -100 becomes 65436 in unsigned short
    }

    @Test
    @DisplayName("Should throw exception for missing LUT descriptor")
    void shouldThrowExceptionForMissingLutDescriptor() {
      IllegalArgumentException exception =
          assertThrows(
              IllegalArgumentException.class,
              () -> LookupTableUtils.lutDescriptor(lutAttributes, Tag.LUTDescriptor));

      assertEquals("Missing LUT Descriptor!", exception.getMessage());
    }

    @Test
    @DisplayName("Should throw exception for invalid descriptor length")
    void shouldThrowExceptionForInvalidDescriptorLength() {
      lutAttributes.setInt(Tag.LUTDescriptor, VR.US, 256, 0); // Only 2 values

      IllegalArgumentException exception =
          assertThrows(
              IllegalArgumentException.class,
              () -> LookupTableUtils.lutDescriptor(lutAttributes, Tag.LUTDescriptor));

      assertTrue(exception.getMessage().contains("Illegal number of LUT Descriptor values"));
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 2, 4, 9, 10, 12, 15, 17, 32})
    @DisplayName("Should throw exception for invalid bit depths")
    void shouldThrowExceptionForInvalidBitDepths(int bits) {
      lutAttributes.setInt(Tag.LUTDescriptor, VR.US, new int[] {256, 0, bits});

      if (bits != 8 && bits != 16) {
        IllegalArgumentException exception =
            assertThrows(
                IllegalArgumentException.class,
                () -> LookupTableUtils.lutDescriptor(lutAttributes, Tag.LUTDescriptor));

        assertTrue(exception.getMessage().contains("Illegal LUT Descriptor: bits="));
      } else {
        // Valid bit depths should not throw
        assertDoesNotThrow(() -> LookupTableUtils.lutDescriptor(lutAttributes, Tag.LUTDescriptor));
      }
    }
  }

  @Nested
  @DisplayName("LUT Data Extraction")
  class LutDataExtraction {

    @Test
    @DisplayName("Should extract 8-bit LUT data")
    void shouldExtract8BitLutData() {
      int[] descriptor = {256, 0, 8};
      byte[] expectedData = create8BitLutData();
      lutAttributes.setBytes(Tag.LUTData, VR.OB, expectedData);

      byte[] result = LookupTableUtils.lutData(lutAttributes, descriptor, Tag.LUTData, 0);

      assertArrayEquals(expectedData, result);
    }

    @Test
    @DisplayName("Should extract 16-bit LUT data")
    void shouldExtract16BitLutData() {
      int[] descriptor = {256, 0, 16};
      byte[] lutData = create16BitLutData();
      lutAttributes.setBytes(Tag.LUTData, VR.OW, lutData);

      byte[] result = LookupTableUtils.lutData(lutAttributes, descriptor, Tag.LUTData, 0);

      assertNotNull(result);
      assertEquals(lutData.length / 2, result.length); // lutData entries are 2 bytes each
    }

    @Test
    @DisplayName("Should handle zero-length descriptor for 65536 entries")
    void shouldHandleZeroLengthDescriptorFor65536Entries() {
      int[] descriptor = {0, 0, 16}; // 0 means 65536 entries
      byte[] lutData = createLargeLutData();
      lutAttributes.setBytes(Tag.LUTData, VR.OW, lutData);

      byte[] result = LookupTableUtils.lutData(lutAttributes, descriptor, Tag.LUTData, 0);

      assertNotNull(result);
    }

    @Test
    @DisplayName("Should handle big-endian LUT data")
    void shouldHandleBigEndianLutData() {
      int[] descriptor = {256, 0, 8};
      byte[] lutData = create8BitLutData();

      // Set big-endian flag
      Attributes bigEndianAttrs = new Attributes(true); // big-endian
      bigEndianAttrs.setBytes(Tag.LUTData, VR.OB, lutData);

      byte[] result = LookupTableUtils.lutData(bigEndianAttrs, descriptor, Tag.LUTData, 0);

      assertNotNull(result);
    }

    @Test
    @DisplayName("Should throw exception for data length mismatch")
    void shouldThrowExceptionForDataLengthMismatch() {
      int[] descriptor = {256, 0, 8};
      byte[] incorrectData = new byte[100]; // Wrong length
      lutAttributes.setBytes(Tag.LUTData, VR.OB, incorrectData);

      IllegalArgumentException exception =
          assertThrows(
              IllegalArgumentException.class,
              () -> LookupTableUtils.lutData(lutAttributes, descriptor, Tag.LUTData, 0));

      assertTrue(exception.getMessage().contains("mismatch specified value"));
    }

    @Test
    @DisplayName("Should handle segmented LUT data")
    void shouldHandleSegmentedLutData() {
      int[] descriptor = {256, 0, 16};
      int[] segmentedData = createSegmentedLutData();
      lutAttributes.setInt(Tag.SegmentedRedPaletteColorLookupTableData, VR.OW, segmentedData);

      byte[] result =
          LookupTableUtils.lutData(
              lutAttributes, descriptor, Tag.LUTData, Tag.SegmentedRedPaletteColorLookupTableData);

      assertNotNull(result);
      assertEquals(256, result.length);
    }

    @Test
    @DisplayName("Should throw exception for missing LUT data")
    void shouldThrowExceptionForMissingLutData() {
      int[] descriptor = {256, 0, 8};

      IllegalArgumentException exception =
          assertThrows(
              IllegalArgumentException.class,
              () -> LookupTableUtils.lutData(lutAttributes, descriptor, Tag.LUTData, 0));

      assertEquals("Missing LUT Data!", exception.getMessage());
    }
  }

  @Nested
  @DisplayName("Integration Tests")
  class IntegrationTests {

    @Test
    @DisplayName("Should work with real DICOM modality LUT")
    void shouldWorkWithRealDicomModalityLut() {
      Attributes modalityLutAttrs = LutTestDataBuilder.createContrastLut12Bit();
      Optional<LookupTableCV> result = LookupTableUtils.createLut(modalityLutAttrs);

      assertTrue(result.isPresent());
      LookupTableCV lut = result.get();
      assertNotNull(lut);
    }

    @Test
    @DisplayName("Should handle VOI LUT with custom sequence")
    void shouldHandleVoiLutWithCustomSequence() {
      // Create a complex sequence-based LUT
      LookupTableCV customLut = createComplexSequenceLut();
      LutShape lutShape = createSequenceLutShape(customLut);

      LookupTableCV result =
          LookupTableUtils.createVoiLut(lutShape, 200.0, 100.0, 0, 511, 9, false, false);

      assertNotNull(result);
      assertEquals(DataBuffer.TYPE_USHORT, result.getDataType());
    }
  }

  // Helper methods to create realistic test data

  private LutShape createLinearLutShape() {
    return LutShape.LINEAR;
  }

  private LutShape createSigmoidLutShape() {
    return LutShape.SIGMOID;
  }

  private LutShape createLogarithmicLutShape() {
    return LutShape.LOG;
  }

  private LutShape createExponentialLutShape() {
    return LutShape.LOG_INV;
  }

  private LutShape createSequenceLutShape(LookupTableCV sequenceLut) {
    return new LutShape(sequenceLut, "Custom shape");
  }

  private LookupTableCV createCustomSequenceLut() {
    // Create a simple ramp sequence
    byte[] sequenceData = new byte[256];
    for (int i = 0; i < sequenceData.length; i++) {
      sequenceData[i] = (byte) i;
    }
    return new LookupTableCV(sequenceData, 0);
  }

  private LookupTableCV createComplexSequenceLut() {
    // Create a more complex sequence with 16-bit data
    short[] sequenceData = new short[512];
    for (int i = 0; i < sequenceData.length; i++) {
      // Create a sine wave pattern
      sequenceData[i] = (short) (32767 * Math.sin(2 * Math.PI * i / sequenceData.length));
    }
    return new LookupTableCV(sequenceData, 0, true);
  }

  private void create8BitLutAttributes() {
    // Create descriptor: 256 entries, offset -100, 8 bits
    lutAttributes.setInt(Tag.LUTDescriptor, VR.US, 256, -100, 8);

    // Create LUT data: simple ramp
    byte[] lutData = create8BitLutData();
    lutAttributes.setBytes(Tag.LUTData, VR.OB, lutData);
  }

  private void create16BitLutAttributes() {
    // Create descriptor: 256 entries, offset 0, 16 bits
    lutAttributes.setInt(Tag.LUTDescriptor, VR.US, 256, 0, 16);

    // Create LUT data: 16-bit values
    byte[] lutData = create16BitLutData();
    lutAttributes.setBytes(Tag.LUTData, VR.OW, lutData);
  }

  private void createLargeLutAttributes() {
    // Create descriptor: 65536 entries (represented as 0), offset 0, 16 bits
    lutAttributes.setInt(Tag.LUTDescriptor, VR.US, 0, 0, 16);

    // Create large LUT data
    byte[] lutData = createLargeLutData();
    lutAttributes.setBytes(Tag.LUTData, VR.OW, lutData);
  }

  private void createBigEndianLutAttributes() {
    Attributes bigEndianAttrs = new Attributes(true); // Create big-endian attributes

    bigEndianAttrs.setInt(Tag.LUTDescriptor, VR.US, 256, 0, 8);
    byte[] lutData = create8BitLutData();
    bigEndianAttrs.setBytes(Tag.LUTData, VR.OB, lutData);

    // Copy to our test attributes
    lutAttributes.addAll(bigEndianAttrs);
  }

  private void create8BitLutWith16BitAllocation() {
    // Create descriptor for 8-bit LUT
    lutAttributes.setInt(Tag.LUTDescriptor, VR.US, 256, 0, 8);

    // Create data with 16-bit allocation (512 bytes for 256 entries)
    byte[] lutData = new byte[512];
    for (int i = 0; i < 256; i++) {
      // Store 8-bit values in 16-bit slots (little-endian)
      lutData[i * 2] = (byte) i; // Low byte
      lutData[i * 2 + 1] = 0; // High byte (padding)
    }
    lutAttributes.setBytes(Tag.LUTData, VR.OW, lutData);
  }

  private byte[] create8BitLutData() {
    byte[] data = new byte[256];
    for (int i = 0; i < data.length; i++) {
      data[i] = (byte) i;
    }
    return data;
  }

  private byte[] create16BitLutData() {
    byte[] data = new byte[512]; // 256 entries * 2 bytes each
    for (int i = 0; i < 256; i++) {
      // Store as little-endian 16-bit values
      int value = i * 257; // Scale 8-bit to 16-bit range
      data[i * 2] = (byte) (value & 0xFF); // Low byte
      data[i * 2 + 1] = (byte) ((value >> 8) & 0xFF); // High byte
    }
    return data;
  }

  private byte[] createLargeLutData() {
    // Create data for 65536 entries
    byte[] data = new byte[65536 * 2];
    for (int i = 0; i < 65536; i++) {
      data[i] = (byte) (i % 256);
      data[i + 1] = (byte) ((i >> 8) & 0xFF); // High byte
    }
    return data;
  }

  private int[] createSegmentedLutData() {
    // Create simple segmented LUT data according to DICOM standard
    // Format: [opcode, length, data...]
    return new int[] {
      0,
      4,
      0,
      64,
      128,
      255, // Discrete segment: 4 values
      1,
      4,
      128, // Linear segment: 4 values from 128 to next value
      0,
      2,
      200,
      150 // Another discrete segment: 2 values
    };
  }
}
