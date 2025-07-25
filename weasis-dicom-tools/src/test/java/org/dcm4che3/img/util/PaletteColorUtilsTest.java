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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.osgi.OpenCVNativeLoader;
import org.weasis.opencv.data.ImageCV;
import org.weasis.opencv.data.LookupTableCV;
import org.weasis.opencv.data.PlanarImage;

/**
 * Test class for {@link PaletteColorUtils}.
 *
 * <p>This test class validates the functionality of DICOM palette color operations, including
 * lookup table creation and RGB image conversion. Test data is created to simulate real DICOM
 * palette color scenarios.
 */
class PaletteColorUtilsTest {

  private Attributes validPaletteAttributes;
  private Attributes invalidPaletteAttributes;
  private Attributes incompletePaletteAttributes;

  @BeforeAll
  static void loadNativeLib() {
    OpenCVNativeLoader loader = new OpenCVNativeLoader();
    loader.init();
  }

  @BeforeEach
  void setUp() {
    // Create valid palette color attributes with test data
    validPaletteAttributes = createValidPaletteAttributes();

    // Create attributes with missing palette data
    invalidPaletteAttributes = new Attributes();

    // Create attributes with incomplete palette data (missing blue component)
    incompletePaletteAttributes = createIncompletePaletteAttributes();
  }

  @Nested
  @DisplayName("getPaletteColorLookupTable Tests")
  class GetPaletteColorLookupTableTests {

    @Test
    @DisplayName("Should return null for null attributes")
    void shouldReturnNullForNullAttributes() {
      LookupTableCV result = PaletteColorUtils.getPaletteColorLookupTable(null);
      assertNull(result);
    }

    @Test
    @DisplayName("Should return null for empty attributes")
    void shouldReturnNullForEmptyAttributes() {
      LookupTableCV result = PaletteColorUtils.getPaletteColorLookupTable(new Attributes());
      assertNull(result);
    }

    @Test
    @DisplayName("Should return null for incomplete palette descriptors")
    void shouldReturnNullForIncompletePaletteDescriptors() {
      LookupTableCV result =
          PaletteColorUtils.getPaletteColorLookupTable(incompletePaletteAttributes);
      assertNull(result);
    }

    @Test
    @DisplayName("Should create valid lookup table for complete palette attributes")
    void shouldCreateValidLookupTableForCompletePaletteAttributes() {
      LookupTableCV result = PaletteColorUtils.getPaletteColorLookupTable(validPaletteAttributes);

      assertNotNull(result);
      assertEquals(3, result.getNumBands());
      assertTrue(result.getNumEntries() > 0);
    }

    @Test
    @DisplayName("Should handle attributes with segmented palette data")
    void shouldHandleAttributesWithSegmentedPaletteData() {
      Attributes segmentedAttributes = createSegmentedPaletteAttributes();
      LookupTableCV result = PaletteColorUtils.getPaletteColorLookupTable(segmentedAttributes);

      assertNotNull(result);
      assertEquals(3, result.getNumBands());
    }

    @Test
    @DisplayName("Should handle invalid segmented palette data gracefully")
    void shouldHandleInvalidSegmentedPaletteDataGracefully() {
      Attributes invalidSegmentedAttributes = createInvalidSegmentedPaletteAttributes();

      // This should throw an exception due to 8-bit segmented data being invalid
      assertThrows(
          IllegalArgumentException.class,
          () -> {
            PaletteColorUtils.getPaletteColorLookupTable(invalidSegmentedAttributes);
          });
    }
  }

  @Nested
  @DisplayName("getRGBImageFromPaletteColorModel Tests")
  class GetRGBImageFromPaletteColorModelTests {

    @Test
    @DisplayName("Should return original image when lookup table is null")
    void shouldReturnOriginalImageWhenLookupTableIsNull() {
      PlanarImage sourceImage = createTestImage(8, 8, CvType.CV_8UC1);

      PlanarImage result =
          PaletteColorUtils.getRGBImageFromPaletteColorModel(sourceImage, (LookupTableCV) null);

      assertSame(sourceImage, result);
    }

    @Test
    @DisplayName("Should apply palette transformation with optimized path")
    void shouldApplyPaletteTransformationWithOptimizedPath() {
      PlanarImage sourceImage = createTestImage(4, 4, CvType.CV_8UC1);
      LookupTableCV lookup = createOptimizedLookupTable();

      PlanarImage result = PaletteColorUtils.getRGBImageFromPaletteColorModel(sourceImage, lookup);

      assertNotNull(result);
      assertNotSame(sourceImage, result);
      assertEquals(3, result.channels());
    }

    @Test
    @DisplayName("Should apply palette transformation with general lookup")
    void shouldApplyPaletteTransformationWithGeneralLookup() {
      PlanarImage sourceImage = createTestImage(4, 4, CvType.CV_16UC1);
      LookupTableCV lookup = createGeneralLookupTable();

      PlanarImage result = PaletteColorUtils.getRGBImageFromPaletteColorModel(sourceImage, lookup);

      assertNotNull(result);
      assertNotSame(sourceImage, result);
    }
  }

  @Nested
  @DisplayName("Edge Cases and Error Handling")
  class EdgeCasesAndErrorHandlingTests {

    @Test
    @DisplayName("Should handle attributes with zero-length palette data")
    void shouldHandleAttributesWithZeroLengthPaletteData() {
      Attributes zeroLengthAttributes = createZeroLengthPaletteAttributes();

      assertThrows(
          IllegalArgumentException.class,
          () -> {
            PaletteColorUtils.getPaletteColorLookupTable(zeroLengthAttributes);
          });
    }

    @Test
    @DisplayName("Should handle mismatched palette descriptor lengths")
    void shouldHandleMismatchedPaletteDescriptorLengths() {
      Attributes mismatchedAttributes = createMismatchedPaletteAttributes();
      LookupTableCV result = PaletteColorUtils.getPaletteColorLookupTable(mismatchedAttributes);

      // Should still create a lookup table even with mismatched lengths
      assertNotNull(result);
    }

    @Test
    @DisplayName("Should handle large palette data")
    void shouldHandleLargePaletteData() {
      Attributes largePaletteAttributes = createLargePaletteAttributes();
      LookupTableCV result = PaletteColorUtils.getPaletteColorLookupTable(largePaletteAttributes);

      assertNotNull(result);
      assertTrue(result.getNumEntries() > 1000);
    }
  }

  // Helper methods to create test data

  private Attributes createValidPaletteAttributes() {
    Attributes attrs = new Attributes();

    // Create palette descriptors (entries, first mapped value, bits per entry)
    attrs.setInt(Tag.RedPaletteColorLookupTableDescriptor, VR.US, 256, 0, 8);
    attrs.setInt(Tag.GreenPaletteColorLookupTableDescriptor, VR.US, 256, 0, 8);
    attrs.setInt(Tag.BluePaletteColorLookupTableDescriptor, VR.US, 256, 0, 8);

    // Create test palette data (simple gradients)
    byte[] redData = createGradientData(256, 0, 255);
    byte[] greenData = createGradientData(256, 255, 0);
    byte[] blueData = createGradientData(256, 128, 128);

    attrs.setBytes(Tag.RedPaletteColorLookupTableData, VR.OW, redData);
    attrs.setBytes(Tag.GreenPaletteColorLookupTableData, VR.OW, greenData);
    attrs.setBytes(Tag.BluePaletteColorLookupTableData, VR.OW, blueData);

    return attrs;
  }

  private Attributes createIncompletePaletteAttributes() {
    Attributes attrs = new Attributes();

    // Only red and green descriptors, missing blue
    attrs.setInt(Tag.RedPaletteColorLookupTableDescriptor, VR.US, 256, 0, 8);
    attrs.setInt(Tag.GreenPaletteColorLookupTableDescriptor, VR.US, 256, 0, 8);

    byte[] redData = createGradientData(256, 0, 255);
    byte[] greenData = createGradientData(256, 255, 0);

    attrs.setBytes(Tag.RedPaletteColorLookupTableData, VR.OW, redData);
    attrs.setBytes(Tag.GreenPaletteColorLookupTableData, VR.OW, greenData);

    return attrs;
  }

  private Attributes createSegmentedPaletteAttributes() {
    Attributes attrs = new Attributes();

    // Use 16-bit descriptors for segmented data (segmented LUT requires 16 bits)
    attrs.setInt(Tag.RedPaletteColorLookupTableDescriptor, VR.US, 256, 0, 16);
    attrs.setInt(Tag.GreenPaletteColorLookupTableDescriptor, VR.US, 256, 0, 16);
    attrs.setInt(Tag.BluePaletteColorLookupTableDescriptor, VR.US, 256, 0, 16);

    // Use segmented palette data tags instead of regular data tags
    int[] segmentedData = createSegmentedLutData();
    attrs.setInt(Tag.SegmentedRedPaletteColorLookupTableData, VR.OW, segmentedData);
    attrs.setInt(Tag.SegmentedGreenPaletteColorLookupTableData, VR.OW, segmentedData);
    attrs.setInt(Tag.SegmentedBluePaletteColorLookupTableData, VR.OW, segmentedData);

    return attrs;
  }

  private Attributes createInvalidSegmentedPaletteAttributes() {
    Attributes attrs = new Attributes();

    // Use 8-bit descriptors with segmented data (this should be invalid)
    attrs.setInt(Tag.RedPaletteColorLookupTableDescriptor, VR.US, 256, 0, 8);
    attrs.setInt(Tag.GreenPaletteColorLookupTableDescriptor, VR.US, 256, 0, 8);
    attrs.setInt(Tag.BluePaletteColorLookupTableDescriptor, VR.US, 256, 0, 8);

    // Use segmented palette data tags with 8-bit descriptors (invalid combination)
    int[] segmentedData = createSegmentedLutData();
    attrs.setInt(Tag.SegmentedRedPaletteColorLookupTableData, VR.OW, segmentedData);
    attrs.setInt(Tag.SegmentedGreenPaletteColorLookupTableData, VR.OW, segmentedData);
    attrs.setInt(Tag.SegmentedBluePaletteColorLookupTableData, VR.OW, segmentedData);

    return attrs;
  }

  private Attributes createZeroLengthPaletteAttributes() {
    Attributes attrs = new Attributes();

    attrs.setInt(Tag.RedPaletteColorLookupTableDescriptor, VR.US, 0, 0, 8);
    attrs.setInt(Tag.GreenPaletteColorLookupTableDescriptor, VR.US, 0, 0, 8);
    attrs.setInt(Tag.BluePaletteColorLookupTableDescriptor, VR.US, 0, 0, 8);

    attrs.setBytes(Tag.RedPaletteColorLookupTableData, VR.OW, new byte[0]);
    attrs.setBytes(Tag.GreenPaletteColorLookupTableData, VR.OW, new byte[0]);
    attrs.setBytes(Tag.BluePaletteColorLookupTableData, VR.OW, new byte[0]);

    return attrs;
  }

  private Attributes createMismatchedPaletteAttributes() {
    Attributes attrs = new Attributes();

    // Different lengths for each component
    attrs.setInt(Tag.RedPaletteColorLookupTableDescriptor, VR.US, 256, 0, 8);
    attrs.setInt(Tag.GreenPaletteColorLookupTableDescriptor, VR.US, 128, 0, 8);
    attrs.setInt(Tag.BluePaletteColorLookupTableDescriptor, VR.US, 64, 0, 8);

    attrs.setBytes(Tag.RedPaletteColorLookupTableData, VR.OW, createGradientData(256, 0, 255));
    attrs.setBytes(Tag.GreenPaletteColorLookupTableData, VR.OW, createGradientData(128, 255, 0));
    attrs.setBytes(Tag.BluePaletteColorLookupTableData, VR.OW, createGradientData(64, 128, 128));

    return attrs;
  }

  private Attributes createLargePaletteAttributes() {
    Attributes attrs = new Attributes();

    int size = 65536; // 16-bit palette
    attrs.setInt(Tag.RedPaletteColorLookupTableDescriptor, VR.US, size, 0, 16);
    attrs.setInt(Tag.GreenPaletteColorLookupTableDescriptor, VR.US, size, 0, 16);
    attrs.setInt(Tag.BluePaletteColorLookupTableDescriptor, VR.US, size, 0, 16);

    attrs.setBytes(
        Tag.RedPaletteColorLookupTableData, VR.OW, createGradientData(size * 2, 0, 65535));
    attrs.setBytes(
        Tag.GreenPaletteColorLookupTableData, VR.OW, createGradientData(size * 2, 65535, 0));
    attrs.setBytes(
        Tag.BluePaletteColorLookupTableData, VR.OW, createGradientData(size * 2, 32768, 32768));

    return attrs;
  }

  private byte[] createGradientData(int length, int startValue, int endValue) {
    byte[] data = new byte[length];
    double step = (double) (endValue - startValue) / (length - 1);

    for (int i = 0; i < length; i++) {
      int value = (int) (startValue + i * step);
      data[i] = (byte) (value & 0xFF);
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

  private PlanarImage createTestImage(int width, int height, int type) {
    Mat mat = new Mat(height, width, type);

    // Fill with test pattern
    for (int row = 0; row < height; row++) {
      for (int col = 0; col < width; col++) {
        double[] values = new double[mat.channels()];
        for (int ch = 0; ch < values.length; ch++) {
          values[ch] = (row * width + col + ch) % 256;
        }
        mat.put(row, col, values);
      }
    }

    return ImageCV.fromMat(mat);
  }

  private LookupTableCV createOptimizedLookupTable() {
    // Create a lookup table with zero offsets (optimized path)
    byte[][] data = new byte[3][256];
    for (int channel = 0; channel < 3; channel++) {
      for (int i = 0; i < 256; i++) {
        data[channel][i] = (byte) ((i + channel * 64) % 256);
      }
    }

    return new LookupTableCV(data, new int[] {0, 0, 0}, true);
  }

  private LookupTableCV createGeneralLookupTable() {
    // Create a lookup table with non-zero offsets (general path)
    byte[][] data = new byte[3][256];
    for (int channel = 0; channel < 3; channel++) {
      for (int i = 0; i < 256; i++) {
        data[channel][i] = (byte) ((i + channel * 32) % 256);
      }
    }

    return new LookupTableCV(data, new int[] {10, 20, 30}, true);
  }
}
