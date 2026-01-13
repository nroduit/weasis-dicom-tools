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

import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.VR;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.weasis.opencv.data.ImageCV;
import org.weasis.opencv.data.LookupTableCV;
import org.weasis.opencv.data.PlanarImage;
import org.weasis.opencv.natives.NativeLibrary;

/**
 * Test class for {@link PaletteColorUtils}.
 *
 * <p>This test class validates the functionality of DICOM palette color operations, including
 * lookup table creation and RGB image conversion using real DICOM data structures.
 */
@DisplayNameGeneration(ReplaceUnderscores.class)
class PaletteColorUtilsTest {

  private static final int STANDARD_PALETTE_SIZE = 256;
  private static final int LARGE_PALETTE_SIZE = 65536;
  private static final int TEST_IMAGE_SIZE = 8;

  private TestDataFactory testDataFactory;

  @BeforeAll
  static void initialize_opencv() {
    NativeLibrary.loadLibraryFromLibraryName();
  }

  @BeforeEach
  void setup() {
    testDataFactory = new TestDataFactory();
  }

  @Nested
  class Palette_lookup_table_creation {

    @Test
    void returns_null_for_null_attributes() {
      var result = PaletteColorUtils.getPaletteColorLookupTable(null);
      assertNull(result);
    }

    @Test
    void returns_null_for_empty_attributes() {
      var result = PaletteColorUtils.getPaletteColorLookupTable(new Attributes());
      assertNull(result);
    }

    @Test
    void returns_null_for_incomplete_palette_descriptors() {
      var incompleteAttributes = testDataFactory.createIncompleteAttributes();
      var result = PaletteColorUtils.getPaletteColorLookupTable(incompleteAttributes);
      assertNull(result);
    }

    @Test
    void creates_valid_lookup_table_for_complete_palette() {
      var validAttributes = testDataFactory.createValidPaletteAttributes();
      var result = PaletteColorUtils.getPaletteColorLookupTable(validAttributes);

      assertNotNull(result);
      assertEquals(3, result.getNumBands());
      assertEquals(STANDARD_PALETTE_SIZE, result.getNumEntries());
    }

    @Test
    void handles_segmented_palette_data() {
      var segmentedAttributes = testDataFactory.createSegmentedPaletteAttributes();
      var result = PaletteColorUtils.getPaletteColorLookupTable(segmentedAttributes);

      assertNotNull(result);
      assertEquals(3, result.getNumBands());
      assertTrue(result.getNumEntries() > 0);
    }

    @Test
    void throws_exception_for_invalid_segmented_data() {
      var invalidSegmented = testDataFactory.createInvalidSegmentedAttributes();

      assertThrows(
          IllegalArgumentException.class,
          () -> PaletteColorUtils.getPaletteColorLookupTable(invalidSegmented));
    }

    @Test
    void handles_large_palette_data() {
      var largeAttributes = testDataFactory.createLargePaletteAttributes();
      var result = PaletteColorUtils.getPaletteColorLookupTable(largeAttributes);

      assertNotNull(result);
      assertEquals(3, result.getNumBands());
      assertTrue(result.getNumEntries() >= LARGE_PALETTE_SIZE);
    }

    @Test
    void creates_lookup_table_with_mismatched_component_sizes() {
      var mismatchedAttributes = testDataFactory.createMismatchedPaletteAttributes();
      var result = PaletteColorUtils.getPaletteColorLookupTable(mismatchedAttributes);

      assertNotNull(result);
      assertEquals(3, result.getNumBands());
    }
  }

  @Nested
  class RGB_image_conversion_with_lookup_table {

    @Test
    void returns_original_image_when_lookup_is_null() {
      var sourceImage = testDataFactory.createTestImage(TEST_IMAGE_SIZE, CvType.CV_8UC1);

      var result =
          PaletteColorUtils.getRGBImageFromPaletteColorModel(sourceImage, (LookupTableCV) null);

      assertSame(sourceImage, result);
    }

    @Test
    void applies_optimized_transformation_for_8bit_zero_offset() {
      var sourceImage = testDataFactory.createTestImage(TEST_IMAGE_SIZE, CvType.CV_8UC1);
      var lookup = testDataFactory.createOptimizedLookupTable();

      var result = PaletteColorUtils.getRGBImageFromPaletteColorModel(sourceImage, lookup);

      assertNotNull(result);
      assertNotSame(sourceImage, result);
      assertEquals(3, result.channels());
    }

    @Test
    void applies_general_lookup_for_non_optimal_cases() {
      var sourceImage = testDataFactory.createTestImage(TEST_IMAGE_SIZE, CvType.CV_16UC1);
      var lookup = testDataFactory.createGeneralLookupTable();

      var result = PaletteColorUtils.getRGBImageFromPaletteColorModel(sourceImage, lookup);

      assertNotNull(result);
      assertNotSame(sourceImage, result);
    }

    @Test
    void preserves_image_dimensions() {
      var sourceImage = testDataFactory.createTestImage(TEST_IMAGE_SIZE, CvType.CV_8UC1);
      var lookup = testDataFactory.createOptimizedLookupTable();

      var result = PaletteColorUtils.getRGBImageFromPaletteColorModel(sourceImage, lookup);

      assertEquals(sourceImage.width(), result.width());
      assertEquals(sourceImage.height(), result.height());
    }
  }

  @Nested
  class Error_handling {

    @Test
    void throws_exception_for_zero_length_palette_data() {
      var zeroLengthAttributes = testDataFactory.createZeroLengthAttributes();

      assertThrows(
          IllegalArgumentException.class,
          () -> PaletteColorUtils.getPaletteColorLookupTable(zeroLengthAttributes));
    }
  }

  /** Factory class for creating test data using realistic DICOM structures. */
  private static class TestDataFactory {

    Attributes createValidPaletteAttributes() {
      var attrs = new Attributes();

      // Set descriptors for all three color components
      setPaletteDescriptor(
          attrs, Tag.RedPaletteColorLookupTableDescriptor, STANDARD_PALETTE_SIZE, 0, 8);
      setPaletteDescriptor(
          attrs, Tag.GreenPaletteColorLookupTableDescriptor, STANDARD_PALETTE_SIZE, 0, 8);
      setPaletteDescriptor(
          attrs, Tag.BluePaletteColorLookupTableDescriptor, STANDARD_PALETTE_SIZE, 0, 8);

      // Create gradient data for each color component
      attrs.setBytes(
          Tag.RedPaletteColorLookupTableData,
          VR.OW,
          createGradientData(STANDARD_PALETTE_SIZE, 0, 255));
      attrs.setBytes(
          Tag.GreenPaletteColorLookupTableData,
          VR.OW,
          createGradientData(STANDARD_PALETTE_SIZE, 255, 0));
      attrs.setBytes(
          Tag.BluePaletteColorLookupTableData,
          VR.OW,
          createGradientData(STANDARD_PALETTE_SIZE, 128, 128));

      return attrs;
    }

    Attributes createIncompleteAttributes() {
      var attrs = new Attributes();

      // Only red and green components, missing blue
      setPaletteDescriptor(
          attrs, Tag.RedPaletteColorLookupTableDescriptor, STANDARD_PALETTE_SIZE, 0, 8);
      setPaletteDescriptor(
          attrs, Tag.GreenPaletteColorLookupTableDescriptor, STANDARD_PALETTE_SIZE, 0, 8);

      attrs.setBytes(
          Tag.RedPaletteColorLookupTableData,
          VR.OW,
          createGradientData(STANDARD_PALETTE_SIZE, 0, 255));
      attrs.setBytes(
          Tag.GreenPaletteColorLookupTableData,
          VR.OW,
          createGradientData(STANDARD_PALETTE_SIZE, 255, 0));

      return attrs;
    }

    Attributes createSegmentedPaletteAttributes() {
      var attrs = new Attributes();

      // 16-bit descriptors required for segmented data
      setPaletteDescriptor(
          attrs, Tag.RedPaletteColorLookupTableDescriptor, STANDARD_PALETTE_SIZE, 0, 16);
      setPaletteDescriptor(
          attrs, Tag.GreenPaletteColorLookupTableDescriptor, STANDARD_PALETTE_SIZE, 0, 16);
      setPaletteDescriptor(
          attrs, Tag.BluePaletteColorLookupTableDescriptor, STANDARD_PALETTE_SIZE, 0, 16);

      var segmentedData = createSegmentedLutData();
      attrs.setInt(Tag.SegmentedRedPaletteColorLookupTableData, VR.OW, segmentedData);
      attrs.setInt(Tag.SegmentedGreenPaletteColorLookupTableData, VR.OW, segmentedData);
      attrs.setInt(Tag.SegmentedBluePaletteColorLookupTableData, VR.OW, segmentedData);

      return attrs;
    }

    Attributes createInvalidSegmentedAttributes() {
      var attrs = new Attributes();

      // Invalid: 8-bit descriptors with segmented data
      setPaletteDescriptor(
          attrs, Tag.RedPaletteColorLookupTableDescriptor, STANDARD_PALETTE_SIZE, 0, 8);
      setPaletteDescriptor(
          attrs, Tag.GreenPaletteColorLookupTableDescriptor, STANDARD_PALETTE_SIZE, 0, 8);
      setPaletteDescriptor(
          attrs, Tag.BluePaletteColorLookupTableDescriptor, STANDARD_PALETTE_SIZE, 0, 8);

      var segmentedData = createSegmentedLutData();
      attrs.setInt(Tag.SegmentedRedPaletteColorLookupTableData, VR.OW, segmentedData);
      attrs.setInt(Tag.SegmentedGreenPaletteColorLookupTableData, VR.OW, segmentedData);
      attrs.setInt(Tag.SegmentedBluePaletteColorLookupTableData, VR.OW, segmentedData);

      return attrs;
    }

    Attributes createZeroLengthAttributes() {
      var attrs = new Attributes();

      setPaletteDescriptor(attrs, Tag.RedPaletteColorLookupTableDescriptor, 0, 0, 8);
      setPaletteDescriptor(attrs, Tag.GreenPaletteColorLookupTableDescriptor, 0, 0, 8);
      setPaletteDescriptor(attrs, Tag.BluePaletteColorLookupTableDescriptor, 0, 0, 8);

      attrs.setBytes(Tag.RedPaletteColorLookupTableData, VR.OW, new byte[0]);
      attrs.setBytes(Tag.GreenPaletteColorLookupTableData, VR.OW, new byte[0]);
      attrs.setBytes(Tag.BluePaletteColorLookupTableData, VR.OW, new byte[0]);

      return attrs;
    }

    Attributes createMismatchedPaletteAttributes() {
      var attrs = new Attributes();

      // Different sizes for each component
      setPaletteDescriptor(attrs, Tag.RedPaletteColorLookupTableDescriptor, 256, 0, 8);
      setPaletteDescriptor(attrs, Tag.GreenPaletteColorLookupTableDescriptor, 128, 0, 8);
      setPaletteDescriptor(attrs, Tag.BluePaletteColorLookupTableDescriptor, 64, 0, 8);

      attrs.setBytes(Tag.RedPaletteColorLookupTableData, VR.OW, createGradientData(256, 0, 255));
      attrs.setBytes(Tag.GreenPaletteColorLookupTableData, VR.OW, createGradientData(128, 255, 0));
      attrs.setBytes(Tag.BluePaletteColorLookupTableData, VR.OW, createGradientData(64, 128, 128));

      return attrs;
    }

    Attributes createLargePaletteAttributes() {
      var attrs = new Attributes();

      setPaletteDescriptor(
          attrs, Tag.RedPaletteColorLookupTableDescriptor, LARGE_PALETTE_SIZE, 0, 16);
      setPaletteDescriptor(
          attrs, Tag.GreenPaletteColorLookupTableDescriptor, LARGE_PALETTE_SIZE, 0, 16);
      setPaletteDescriptor(
          attrs, Tag.BluePaletteColorLookupTableDescriptor, LARGE_PALETTE_SIZE, 0, 16);

      var dataSize = LARGE_PALETTE_SIZE * 2; // 16-bit data
      attrs.setBytes(
          Tag.RedPaletteColorLookupTableData, VR.OW, createGradientData(dataSize, 0, 65535));
      attrs.setBytes(
          Tag.GreenPaletteColorLookupTableData, VR.OW, createGradientData(dataSize, 65535, 0));
      attrs.setBytes(
          Tag.BluePaletteColorLookupTableData, VR.OW, createGradientData(dataSize, 32768, 32768));

      return attrs;
    }

    PlanarImage createTestImage(int size, int cvType) {
      var mat = new Mat(size, size, cvType);

      // Fill with sequential test pattern
      for (int row = 0; row < size; row++) {
        for (int col = 0; col < size; col++) {
          double[] values = new double[mat.channels()];
          for (int ch = 0; ch < values.length; ch++) {
            values[ch] = (row * size + col + ch) % 256;
          }
          mat.put(row, col, values);
        }
      }

      return ImageCV.fromMat(mat);
    }

    LookupTableCV createOptimizedLookupTable() {
      var data = new byte[3][STANDARD_PALETTE_SIZE];
      for (int channel = 0; channel < 3; channel++) {
        for (int i = 0; i < STANDARD_PALETTE_SIZE; i++) {
          data[channel][i] = (byte) ((i + channel * 64) % 256);
        }
      }
      return new LookupTableCV(data, new int[] {0, 0, 0}, true);
    }

    LookupTableCV createGeneralLookupTable() {
      var data = new byte[3][STANDARD_PALETTE_SIZE];
      for (int channel = 0; channel < 3; channel++) {
        for (int i = 0; i < STANDARD_PALETTE_SIZE; i++) {
          data[channel][i] = (byte) ((i + channel * 32) % 256);
        }
      }
      return new LookupTableCV(data, new int[] {10, 20, 30}, true);
    }

    private void setPaletteDescriptor(
        Attributes attrs, int tag, int entries, int firstMapped, int bits) {
      attrs.setInt(tag, VR.US, entries, firstMapped, bits);
    }

    private byte[] createGradientData(int length, int startValue, int endValue) {
      var data = new byte[length];
      var step = (double) (endValue - startValue) / Math.max(1, length - 1);

      for (int i = 0; i < length; i++) {
        var value = (int) (startValue + i * step);
        data[i] = (byte) (value & 0xFF);
      }
      return data;
    }

    private int[] createSegmentedLutData() {
      // DICOM segmented LUT format: [opcode, length, data...]
      return new int[] {
        0,
        4,
        0,
        64,
        128,
        255, // Discrete segment: 4 values
        1,
        4,
        128, // Linear segment: 4 values from 128
        0,
        2,
        200,
        150 // Another discrete segment: 2 values
      };
    }
  }
}
