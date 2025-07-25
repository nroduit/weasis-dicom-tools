/*
 * Copyright (c) 2021 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.dcm4che3.img;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Dimension;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.awt.image.Raster;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.util.List;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.UID;
import org.dcm4che3.data.VR;
import org.dcm4che3.img.stream.BytesWithImageDescriptor;
import org.dcm4che3.img.stream.DicomFileInputStream;
import org.dcm4che3.img.stream.ImageDescriptor;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.weasis.opencv.data.ImageCV;
import org.weasis.opencv.data.PlanarImage;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class DicomImageReaderTest {

  static final Path IN_DIR =
      FileSystems.getDefault().getPath("target/test-classes/org/dcm4che3/img");
  static DicomImageReader reader;

  @BeforeAll
  static void setUp() {
    reader = new DicomImageReader(new DicomImageReaderSpi());
  }

  @AfterAll
  static void tearDown() {
    if (reader != null) reader.dispose();
  }

  private List<PlanarImage> readDicomImage(String filename) throws IOException {
    reader.setInput(
        new DicomFileInputStream(FileSystems.getDefault().getPath(IN_DIR.toString(), filename)));
    return reader.getPlanarImages(null);
  }

  @Test
  @DisplayName("Read lossy JPEG2000 multiframe with multi-fragments stream")
  void jpeg2000LossyMultiframe() throws Exception {
    List<PlanarImage> imagesIn = readDicomImage("jpeg2000-multiframe-multifragments.dcm");
    assertEquals(19, imagesIn.size(), "The number of image frames doesn't match");
    assertEquals(19, reader.getNumImages(true));
    assertEquals(256, reader.getWidth(0));
    assertEquals(256, reader.getHeight(0));
    assertThrows(UnsupportedOperationException.class, () -> reader.getImageTypes(0));
    assertInstanceOf(DicomImageReadParam.class, reader.getDefaultReadParam());
    assertNull(reader.getImageMetadata(0));
    assertTrue(reader.canReadRaster());

    for (PlanarImage img : imagesIn) {
      assertEquals(256, img.width(), "The image width doesn't match");
      assertEquals(256, img.height(), "The image height doesn't match");
    }
  }

  @Test
  @DisplayName("Read YBR_FULL with RLE compression")
  void ybrFullRLE() throws Exception {
    List<PlanarImage> imagesIn = readDicomImage("ybrFull-RLE.dcm");
    assertEquals(1, imagesIn.size(), "The number of image frames doesn't match");
    for (PlanarImage img : imagesIn) {
      assertEquals(640, img.width(), "The image width doesn't match");
      assertEquals(480, img.height(), "The image height doesn't match");
    }

    DicomImageReadParam params = new DicomImageReadParam();
    params.setSourceRenderSize(new Dimension(320, 240));
    Raster raster = reader.readRaster(0, params);
    assertEquals(320, raster.getWidth());
    assertEquals(240, raster.getHeight());
    assertNotNull(reader.readRaster(0, null));

    BufferedImage bufferedImage = reader.read(0, null);
    assertEquals(640, bufferedImage.getWidth());
    assertEquals(480, bufferedImage.getHeight());

    assertEquals(1, reader.getPlanarImages().size());
    assertNotNull(reader.getPlanarImage());
    assertEquals(1, reader.getNumImages(true));
    assertEquals(640, reader.getWidth(0));
    assertEquals(480, reader.getHeight(0));
    assertThrows(UnsupportedOperationException.class, () -> reader.getImageTypes(0));
    assertInstanceOf(DicomImageReadParam.class, reader.getDefaultReadParam());
    assertNull(reader.getImageMetadata(0));
  }

  @ParameterizedTest
  @DisplayName("Test reading different DICOM file types")
  @ValueSource(
      strings = {
        "mono2-CT-16bit.dcm",
        "CT-JPEGLosslessSV1.dcm",
        "MR-JPEGLosslessSV1.dcm",
        "signed-raw-9bit.dcm"
      })
  void testDifferentDicomFileTypes(String filename) throws Exception {
    List<PlanarImage> images = readDicomImage(filename);
    assertNotNull(images);
    assertFalse(images.isEmpty());

    PlanarImage firstImage = images.get(0);
    assertTrue(firstImage.width() > 0);
    assertTrue(firstImage.height() > 0);

    // Test that we can read metadata
    assertTrue(reader.getNumImages(true) > 0);
    assertTrue(reader.getWidth(0) > 0);
    assertTrue(reader.getHeight(0) > 0);
  }

  @Test
  @DisplayName("Test DicomImageReadParam functionality")
  void testDicomImageReadParam() throws Exception {
    readDicomImage("ybrFull-RLE.dcm");

    DicomImageReadParam param = new DicomImageReadParam();

    // Test source region cropping
    Rectangle sourceRegion = new Rectangle(100, 100, 200, 200);
    param.setSourceRegion(sourceRegion);
    assertEquals(sourceRegion, param.getSourceRegion());

    // Test scaling
    Dimension renderSize = new Dimension(320, 240);
    param.setSourceRenderSize(renderSize);
    assertEquals(renderSize, param.getSourceRenderSize());

    // Test window/level parameters
    param.setWindowCenter(500.0);
    param.setWindowWidth(1000.0);
    assertTrue(param.getWindowCenter().isPresent());
    assertEquals(500.0, param.getWindowCenter().getAsDouble());
    assertTrue(param.getWindowWidth().isPresent());
    assertEquals(1000.0, param.getWindowWidth().getAsDouble());

    // Test with actual image reading
    PlanarImage image = reader.getPlanarImage(0, param);
    assertNotNull(image);
    assertEquals(320, image.width());
    assertEquals(240, image.height());
  }

  @Test
  @DisplayName("Test error handling with invalid frame index")
  void testInvalidFrameIndex() throws Exception {
    readDicomImage("ybrFull-RLE.dcm");

    // Test with negative frame index
    assertThrows(IndexOutOfBoundsException.class, () -> reader.getWidth(-1));
    assertThrows(IndexOutOfBoundsException.class, () -> reader.getHeight(-1));

    // Test with frame index beyond available frames
    int numFrames = reader.getNumImages(true);
    assertThrows(IndexOutOfBoundsException.class, () -> reader.getWidth(numFrames));
    assertThrows(IndexOutOfBoundsException.class, () -> reader.getHeight(numFrames));
  }

  @Test
  @DisplayName("Test BytesWithImageDescriptor input")
  void testBytesWithImageDescriptorInput() {
    // Create a mock BytesWithImageDescriptor
    BytesWithImageDescriptor mockDescriptor = createMockBytesDescriptor();

    reader.setInput(mockDescriptor);
    assertNotNull(reader.getImageDescriptor());
    assertEquals(256, reader.getImageDescriptor().getColumns());
    assertEquals(256, reader.getImageDescriptor().getRows());
  }

  @Test
  @DisplayName("Test lazy planar image loading")
  void testLazyPlanarImageLoading() throws Exception {
    readDicomImage("ybrFull-RLE.dcm");

    var lazyImages = reader.getLazyPlanarImages(null, null);
    assertNotNull(lazyImages);
    assertEquals(1, lazyImages.size());

    // Test that lazy loading works
    PlanarImage image = lazyImages.get(0).get();
    assertNotNull(image);
    assertEquals(640, image.width());
    assertEquals(480, image.height());
  }

  @Test
  @DisplayName("Test static utility methods")
  void testStaticUtilityMethods() {
    // Test supported syntax check
    assertTrue(DicomImageReader.isSupportedSyntax(UID.ImplicitVRLittleEndian));
    assertTrue(DicomImageReader.isSupportedSyntax(UID.JPEGBaseline8Bit));
    assertFalse(DicomImageReader.isSupportedSyntax("1.2.3.4.5.6.7.8.9"));

    // Test float image conversion settings
    String seriesUID = "1.2.3.4.5.6.7.8.9.10";
    DicomImageReader.addSeriesToFloatImages(seriesUID, true);
    assertTrue(DicomImageReader.getForceToFloatImages(seriesUID));

    DicomImageReader.removeSeriesToFloatImages(seriesUID);
    assertNull(DicomImageReader.getForceToFloatImages(seriesUID));

    // Test global float conversion setting
    DicomImageReader.setAllowFloatImageConversion(true);
    DicomImageReader.setAllowFloatImageConversion(false);
  }

  @Test
  @DisplayName("Test reader disposal and cleanup")
  void testReaderDisposal() throws Exception {
    DicomImageReader testReader = new DicomImageReader(new DicomImageReaderSpi());

    // Set up reader with data
    testReader.setInput(
        new DicomFileInputStream(
            FileSystems.getDefault().getPath(IN_DIR.toString(), "ybrFull-RLE.dcm")));

    // Verify it works
    assertEquals(1, testReader.getNumImages(true));

    // Dispose and verify cleanup
    testReader.dispose();

    // After disposal, reader should handle operations gracefully
    assertThrows(IllegalStateException.class, () -> testReader.getWidth(0));
  }

  @Test
  @DisplayName("Test bulk data descriptor")
  void testBulkDataDescriptor() {
    var descriptor = DicomImageReader.BULK_DATA_DESCRIPTOR;
    assertNotNull(descriptor);

    // Test pixel data is considered bulk data
    assertTrue(descriptor.isBulkData(List.of(), null, Tag.PixelData, VR.OW, 1000));

    // Test small non-bulk data
    assertFalse(descriptor.isBulkData(List.of(), null, Tag.PatientName, VR.PN, 50));

    // Test large private tag
    assertTrue(descriptor.isBulkData(List.of(), null, 0x00091010, VR.LO, 2000));
    assertFalse(descriptor.isBulkData(List.of(), null, 0x00091010, VR.LO, 500));
  }

  @Nested
  @DisplayName("Rescale Operations Tests")
  class RescaleOperationsTests {
    @Test
    @DisplayName("Test rangeOutsideLut with various scenarios")
    void testRangeOutsideLut() throws Exception {
      // Load a test image to work with
      readDicomImage("mono2-CT-16bit.dcm");
      ImageDescriptor desc = reader.getImageDescriptor();
      PlanarImage testImage = reader.getRawImage(0, null);

      try {
        // Test 1: Normal case without forcing float conversion
        PlanarImage result1 = DicomImageReader.rangeOutsideLut(testImage, desc, 0, false);
        assertNotNull(result1, "Result should not be null");

        // Test 2: Force float conversion
        PlanarImage result2 = DicomImageReader.rangeOutsideLut(testImage, desc, 0, true);
        assertNotNull(result2, "Result with forced float should not be null");

        // Test 3: Test with different frame indices
        if (desc.getFrames() > 1) {
          PlanarImage result3 = DicomImageReader.rangeOutsideLut(testImage, desc, 1, false);
          assertNotNull(result3, "Result for frame 1 should not be null");
        }

        // Clean up results if they're different from the original
        if (!result1.equals(testImage)) result1.release();
        if (!result2.equals(testImage)) result2.release();

      } finally {
        testImage.release();
      }
    }

    @Test
    @DisplayName("Test rangeOutsideLut with mock data scenarios")
    void testRangeOutsideLutWithMockData() {
      // Create a mock descriptor with specific attributes for testing
      ImageDescriptor mockDesc = createMockImageDescriptorWithModality();

      // Create a simple test image
      Mat testMat = Mat.zeros(100, 100, CvType.CV_16U);
      // Fill with test pattern
      for (int i = 0; i < 100; i++) {
        for (int j = 0; j < 100; j++) {
          testMat.put(i, j, i * 100 + j);
        }
      }

      PlanarImage testImage = ImageCV.fromMat(testMat);

      try {
        // Test with normal slope and intercept
        PlanarImage result1 = DicomImageReader.rangeOutsideLut(testImage, mockDesc, 0, false);
        assertNotNull(result1, "Result should not be null");

        // Test forcing float conversion
        PlanarImage result2 = DicomImageReader.rangeOutsideLut(testImage, mockDesc, 0, true);
        assertNotNull(result2, "Forced float result should not be null");

        // Verify the results are proper images
        assertTrue(result1.width() > 0, "Result image should have positive width");
        assertTrue(result1.height() > 0, "Result image should have positive height");
        assertTrue(result2.width() > 0, "Forced float result should have positive width");
        assertTrue(result2.height() > 0, "Forced float result should have positive height");

        // Clean up
        if (!result1.equals(testImage)) result1.release();
        if (!result2.equals(testImage)) result2.release();

      } finally {
        testImage.release();
      }
    }

    @Test
    @DisplayName("Test rangeOutsideLut with edge case parameters")
    void testRangeOutsideLutEdgeCases() {
      // Create descriptor with extreme slope values to test edge cases
      ImageDescriptor extremeDesc = createMockImageDescriptorWithExtremeSlope();

      // Create a small test image
      Mat testMat = Mat.ones(50, 50, CvType.CV_8U);
      testMat = testMat.mul(testMat, 128.0); // Fill with mid-range values
      PlanarImage testImage = ImageCV.fromMat(testMat);

      try {
        // Test with very small slope (< 0.5) - should trigger float conversion
        PlanarImage result1 = DicomImageReader.rangeOutsideLut(testImage, extremeDesc, 0, false);
        assertNotNull(result1, "Result with small slope should not be null");

        // Test with negative slope
        ImageDescriptor negativeDesc = createMockImageDescriptorWithNegativeSlope();
        PlanarImage result2 = DicomImageReader.rangeOutsideLut(testImage, negativeDesc, 0, false);
        assertNotNull(result2, "Result with negative slope should not be null");

        // Test with zero frame index
        PlanarImage result3 = DicomImageReader.rangeOutsideLut(testImage, extremeDesc, 0, false);
        assertNotNull(result3, "Result with frame 0 should not be null");

        // Clean up results
        if (!result1.equals(testImage)) result1.release();
        if (!result2.equals(testImage)) result2.release();
        if (!result3.equals(testImage)) result3.release();

      } finally {
        testImage.release();
      }
    }

    @Test
    @DisplayName("Test rangeOutsideLut with different image types")
    void testRangeOutsideLutWithDifferentImageTypes() throws Exception {
      // Test with different DICOM file types that have different characteristics
      String[] testFiles = {"mono2-CT-16bit.dcm", "signed-raw-9bit.dcm"};

      for (String filename : testFiles) {
        readDicomImage(filename);
        ImageDescriptor desc = reader.getImageDescriptor();
        PlanarImage rawImage = reader.getRawImage(0, null);

        // Test normal conversion
        PlanarImage result1 = DicomImageReader.rangeOutsideLut(rawImage, desc, 0, false);
        assertNotNull(result1, "Result for " + filename + " should not be null");

        // Test forced conversion
        PlanarImage result2 = DicomImageReader.rangeOutsideLut(rawImage, desc, 0, true);
        assertNotNull(result2, "Forced result for " + filename + " should not be null");

        // Verify image dimensions are preserved
        assertEquals(
            rawImage.width(), result1.width(), "Width should be preserved for " + filename);
        assertEquals(
            rawImage.height(), result1.height(), "Height should be preserved for " + filename);
        assertEquals(
            rawImage.width(),
            result2.width(),
            "Width should be preserved for forced conversion of " + filename);
        assertEquals(
            rawImage.height(),
            result2.height(),
            "Height should be preserved for forced conversion of " + filename);

        // Clean up
        if (!result1.equals(rawImage)) result1.release();
        if (!result2.equals(rawImage)) result2.release();
        rawImage.release();
      }
    }

    @Test
    @DisplayName("Test rangeOutsideLut with float image conversion settings")
    void testRangeOutsideLutWithFloatConversionSettings() throws Exception {
      // Enable float image conversion globally
      DicomImageReader.setAllowFloatImageConversion(true);

      try {
        readDicomImage("mono2-CT-16bit.dcm");
        ImageDescriptor desc = reader.getImageDescriptor();
        PlanarImage rawImage = reader.getRawImage(0, null);

        try {
          // Test with series marked for float conversion
          String seriesUID = desc.getSeriesInstanceUID();
          if (seriesUID != null) {
            DicomImageReader.addSeriesToFloatImages(seriesUID, true);

            PlanarImage result = DicomImageReader.rangeOutsideLut(rawImage, desc, 0, false);
            assertNotNull(result, "Result with series float setting should not be null");

            // Clean up series setting
            DicomImageReader.removeSeriesToFloatImages(seriesUID);

            if (!result.equals(rawImage)) result.release();
          }

          // Test with series marked against float conversion
          if (seriesUID != null) {
            DicomImageReader.addSeriesToFloatImages(seriesUID, false);

            PlanarImage result = DicomImageReader.rangeOutsideLut(rawImage, desc, 0, false);
            assertNotNull(result, "Result with series non-float setting should not be null");

            // Clean up series setting
            DicomImageReader.removeSeriesToFloatImages(seriesUID);

            if (!result.equals(rawImage)) result.release();
          }

        } finally {
          rawImage.release();
        }

      } finally {
        // Reset global setting
        DicomImageReader.setAllowFloatImageConversion(false);
      }
    }

    /** Creates a mock ImageDescriptor with modality LUT values for testing */
    private ImageDescriptor createMockImageDescriptorWithModality() {
      Attributes attrs = new Attributes();
      attrs.setInt(Tag.Rows, VR.US, 100);
      attrs.setInt(Tag.Columns, VR.US, 100);
      attrs.setInt(Tag.NumberOfFrames, VR.IS, 1);
      attrs.setInt(Tag.SamplesPerPixel, VR.US, 1);
      attrs.setInt(Tag.BitsAllocated, VR.US, 16);
      attrs.setInt(Tag.BitsStored, VR.US, 16);
      attrs.setInt(Tag.HighBit, VR.US, 15);
      attrs.setInt(Tag.PixelRepresentation, VR.US, 0);
      attrs.setString(Tag.PhotometricInterpretation, VR.CS, "MONOCHROME2");

      // Add modality LUT attributes
      attrs.setDouble(Tag.RescaleSlope, VR.DS, 1.0);
      attrs.setDouble(Tag.RescaleIntercept, VR.DS, 0.0);

      return new ImageDescriptor(attrs);
    }

    /** Creates a mock ImageDescriptor with extreme slope for testing edge cases */
    private ImageDescriptor createMockImageDescriptorWithExtremeSlope() {
      Attributes attrs = new Attributes();
      attrs.setInt(Tag.Rows, VR.US, 50);
      attrs.setInt(Tag.Columns, VR.US, 50);
      attrs.setInt(Tag.NumberOfFrames, VR.IS, 1);
      attrs.setInt(Tag.SamplesPerPixel, VR.US, 1);
      attrs.setInt(Tag.BitsAllocated, VR.US, 8);
      attrs.setInt(Tag.BitsStored, VR.US, 8);
      attrs.setInt(Tag.HighBit, VR.US, 7);
      attrs.setInt(Tag.PixelRepresentation, VR.US, 0);
      attrs.setString(Tag.PhotometricInterpretation, VR.CS, "MONOCHROME2");

      // Set very small slope (< 0.5) to trigger float conversion
      attrs.setDouble(Tag.RescaleSlope, VR.DS, 0.1);
      attrs.setDouble(Tag.RescaleIntercept, VR.DS, 100.0);

      return new ImageDescriptor(attrs);
    }

    /** Creates a mock ImageDescriptor with negative slope for testing */
    private ImageDescriptor createMockImageDescriptorWithNegativeSlope() {
      Attributes attrs = new Attributes();
      attrs.setInt(Tag.Rows, VR.US, 50);
      attrs.setInt(Tag.Columns, VR.US, 50);
      attrs.setInt(Tag.NumberOfFrames, VR.IS, 1);
      attrs.setInt(Tag.SamplesPerPixel, VR.US, 1);
      attrs.setInt(Tag.BitsAllocated, VR.US, 16);
      attrs.setInt(Tag.BitsStored, VR.US, 12);
      attrs.setInt(Tag.HighBit, VR.US, 11);
      attrs.setInt(Tag.PixelRepresentation, VR.US, 1);
      attrs.setString(Tag.PhotometricInterpretation, VR.CS, "MONOCHROME1");

      // Set negative slope to test inversion logic
      attrs.setDouble(Tag.RescaleSlope, VR.DS, -0.5);
      attrs.setDouble(Tag.RescaleIntercept, VR.DS, 2048.0);

      return new ImageDescriptor(attrs);
    }
  }

  /** Creates a mock BytesWithImageDescriptor for testing */
  private BytesWithImageDescriptor createMockBytesDescriptor() {
    return new BytesWithImageDescriptor() {
      private final ImageDescriptor imageDesc = createMockImageDescriptor();

      @Override
      public ByteBuffer getBytes(int frame) throws IOException {
        // Create simple test pixel data (grayscale 256x256)
        byte[] pixelData = new byte[256 * 256];
        for (int i = 0; i < pixelData.length; i++) {
          pixelData[i] = (byte) (i % 256);
        }
        return ByteBuffer.wrap(pixelData);
      }

      @Override
      public String getTransferSyntax() {
        return UID.ImplicitVRLittleEndian;
      }

      @Override
      public VR getPixelDataVR() {
        return VR.OW;
      }

      @Override
      public ImageDescriptor getImageDescriptor() {
        return imageDesc;
      }
    };
  }

  /** Creates a mock ImageDescriptor for testing */
  private ImageDescriptor createMockImageDescriptor() {
    Attributes attrs = new Attributes();
    attrs.setInt(Tag.Rows, VR.US, 256);
    attrs.setInt(Tag.Columns, VR.US, 256);
    attrs.setInt(Tag.NumberOfFrames, VR.IS, 1);
    attrs.setInt(Tag.SamplesPerPixel, VR.US, 1);
    attrs.setInt(Tag.BitsAllocated, VR.US, 8);
    attrs.setInt(Tag.BitsStored, VR.US, 8);
    attrs.setInt(Tag.HighBit, VR.US, 7);
    attrs.setInt(Tag.PixelRepresentation, VR.US, 0);
    attrs.setString(Tag.PhotometricInterpretation, VR.CS, "MONOCHROME2");

    // Create a simple byte array for pixel data
    byte[] pixelData = new byte[256 * 256];
    attrs.setBytes(Tag.PixelData, VR.OW, pixelData);

    return new ImageDescriptor(attrs);
  }
}
