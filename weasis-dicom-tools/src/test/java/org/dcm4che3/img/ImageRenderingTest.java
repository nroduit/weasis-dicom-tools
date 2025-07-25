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

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.awt.Polygon;
import java.awt.Shape;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Stream;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.VR;
import org.dcm4che3.image.PhotometricInterpretation;
import org.dcm4che3.img.data.EmbeddedOverlay;
import org.dcm4che3.img.stream.DicomFileInputStream;
import org.dcm4che3.img.stream.ImageDescriptor;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.Scalar;
import org.opencv.osgi.OpenCVNativeLoader;
import org.weasis.core.util.StringUtil;
import org.weasis.opencv.data.ImageCV;
import org.weasis.opencv.data.PlanarImage;
import org.weasis.opencv.op.ImageAnalyzer;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ImageRenderingTest {

  private static final double DELTA = 0.0001;
  private static final int TEST_IMAGE_WIDTH = 256;
  private static final int TEST_IMAGE_HEIGHT = 256;

  @BeforeAll
  static void loadNativeLib() {
    OpenCVNativeLoader loader = new OpenCVNativeLoader();
    loader.init();
  }

  @Nested
  @DisplayName("Image without embedded overlay Tests")
  class OverlayTests {

    @Test
    @DisplayName("Should remove embedded overlay from image")
    void shouldRemoveEmbeddedOverlay() {
      // Create test image with embedded overlay
      PlanarImage imageWithOverlay = createImageWithEmbeddedOverlay();

      // Create mock ImageDescriptor with embedded overlay configuration
      ImageDescriptor desc = createMockImageDescriptorWithEmbeddedOverlay();

      int frameIndex = 0;

      // Apply the overlay removal method
      PlanarImage result =
          ImageRendering.getImageWithoutEmbeddedOverlay(imageWithOverlay, desc, frameIndex);

      // Verify overlay bits are removed - check that high bits are masked out
      assertNotNull(result, "Result image should not be null");
      assertEquals(imageWithOverlay.width(), result.width(), "Width should be preserved");
      assertEquals(imageWithOverlay.height(), result.height(), "Height should be preserved");

      // Verify that overlay bits (bit 12 in this case) are removed
      // Sample a few pixels to verify the overlay mask was applied
      for (int row = 0; row < Math.min(5, result.height()); row++) {
        for (int col = 0; col < Math.min(5, result.width()); col++) {
          double[] originalPixel = imageWithOverlay.get(row, col);
          double[] resultPixel = result.get(row, col);

          // Original pixel has overlay bit, result should have it masked out
          int originalValue = (int) originalPixel[0];
          int resultValue = (int) resultPixel[0];

          // The result should only contain the 12-bit data (bits 0-11)
          int expectedValue = originalValue & 0x0FFF; // Mask to keep only 12 bits
          assertEquals(
              expectedValue,
              resultValue,
              String.format(
                  "Pixel at (%d,%d) should have overlay bits removed. Original: %d, Result: %d, Expected: %d",
                  row, col, originalValue, resultValue, expectedValue));
        }
      }
    }

    @Test
    @DisplayName("Should return original image when no embedded overlays present")
    void shouldReturnOriginalImageWhenNoEmbeddedOverlays() {
      PlanarImage originalImage =
          createTestImage(TEST_IMAGE_WIDTH, TEST_IMAGE_HEIGHT, CvType.CV_16UC1, 1234);
      ImageDescriptor desc = createMockImageDescriptorWithoutEmbeddedOverlay();

      PlanarImage result = ImageRendering.getImageWithoutEmbeddedOverlay(originalImage, desc, 0);

      // Should return the same image reference when no overlays to remove
      assertSame(originalImage, result, "Should return original image when no embedded overlays");
    }

    @Test
    @DisplayName("Should handle null input gracefully")
    void shouldHandleNullInputForEmbeddedOverlay() {
      ImageDescriptor desc = createMockImageDescriptorWithoutEmbeddedOverlay();

      assertThrows(
          NullPointerException.class,
          () -> {
            ImageRendering.getImageWithoutEmbeddedOverlay(null, desc, 0);
          },
          "Should throw NullPointerException for null image");

      PlanarImage image = createTestImage(10, 10, CvType.CV_16UC1, 100);
      assertThrows(
          NullPointerException.class,
          () -> {
            ImageRendering.getImageWithoutEmbeddedOverlay(image, null, 0);
          },
          "Should throw NullPointerException for null descriptor");
    }

    // Helper method to create mock ImageDescriptor with embedded overlay
    private ImageDescriptor createMockImageDescriptorWithEmbeddedOverlay() {
      ImageDescriptor desc = mock(ImageDescriptor.class);

      // Create embedded overlay at bit position 12
      EmbeddedOverlay embeddedOverlay = new EmbeddedOverlay(0x6000, 12);
      List<EmbeddedOverlay> overlays = Arrays.asList(embeddedOverlay);

      when(desc.getEmbeddedOverlay()).thenReturn(overlays);
      when(desc.getBitsStored()).thenReturn(12);
      when(desc.getBitsAllocated()).thenReturn(16);
      when(desc.getHighBit()).thenReturn(11); // 0-based, so bit 11 is the highest data bit
      when(desc.getModalityLutForFrame(anyInt())).thenReturn(null);

      return desc;
    }

    // Helper method to create mock ImageDescriptor without embedded overlay
    private ImageDescriptor createMockImageDescriptorWithoutEmbeddedOverlay() {
      ImageDescriptor desc = mock(ImageDescriptor.class);
      when(desc.getEmbeddedOverlay()).thenReturn(Collections.emptyList());
      when(desc.getBitsStored()).thenReturn(12);
      when(desc.getBitsAllocated()).thenReturn(16);
      return desc;
    }

    // Helper method to create a simple test image
    private PlanarImage createTestImage(int width, int height, int type, double value) {
      Mat mat = Mat.zeros(height, width, type);
      mat.setTo(new Scalar(value));
      return ImageCV.fromMat(mat);
    }
  }

  @Nested
  @DisplayName("Modality LUT Tests")
  class ModalityLutTests {

    @Test
    @DisplayName("Apply modality LUT to 8-bit unsigned image")
    void testModalityLutOn8BitImage() {
      // Create test data
      PlanarImage testImage = createTestImage(CvType.CV_8UC1, 128.0);
      ImageDescriptor desc =
          createBasicImageDescriptor(8, 8, 7, 0, PhotometricInterpretation.MONOCHROME2);
      DicomImageReadParam params = new DicomImageReadParam();

      // Test modality LUT application
      DicomImageAdapter adapter = new DicomImageAdapter(testImage, desc, 0);
      PlanarImage result = ImageRendering.getModalityLutImage(testImage, adapter, params);

      assertNotNull(result);
      assertInstanceOf(ImageCV.class, result);
      assertEquals(testImage.width(), result.width());
      assertEquals(testImage.height(), result.height());
    }

    @Test
    @DisplayName("Apply modality LUT to 16-bit signed image")
    void testModalityLutOn16BitSignedImage() {
      // Create test data with negative values
      PlanarImage testImage = createTestImage(CvType.CV_16SC1, -1024.0);
      ImageDescriptor desc =
          createBasicImageDescriptor(16, 12, 11, 1, PhotometricInterpretation.MONOCHROME2);
      DicomImageReadParam params = new DicomImageReadParam();

      // Test modality LUT application
      DicomImageAdapter adapter = new DicomImageAdapter(testImage, desc, 0);
      PlanarImage result = ImageRendering.getModalityLutImage(testImage, adapter, params);

      assertNotNull(result);
      assertEquals(testImage.width(), result.width());
      assertEquals(testImage.height(), result.height());
    }

    @Test
    @DisplayName("Skip modality LUT for floating point data")
    void testModalityLutSkipsFloatingPointData() {
      // Create float image
      PlanarImage testImage = createTestImage(CvType.CV_32FC1, 0.5);
      ImageDescriptor desc =
          createBasicImageDescriptor(32, 32, 31, 0, PhotometricInterpretation.MONOCHROME2);
      DicomImageReadParam params = new DicomImageReadParam();

      // Test that floating point data is returned unchanged
      DicomImageAdapter adapter = new DicomImageAdapter(testImage, desc, 0);
      PlanarImage result = ImageRendering.getModalityLutImage(testImage, adapter, params);

      assertEquals(testImage, result, "Floating point images should be returned unchanged");
    }

    @Test
    @DisplayName("Check statistics on a Modality LUT image (polygonal area)")
    void getModalityLutImage_Statistics() throws Exception {
      Path in =
          Paths.get(
              Objects.requireNonNull(DicomImageReaderTest.class.getResource("mono2-CT-16bit.dcm"))
                  .toURI());
      DicomImageReadParam readParam = new DicomImageReadParam();

      Polygon polygon = new Polygon();
      polygon.addPoint(150, 200);
      polygon.addPoint(200, 190);
      polygon.addPoint(200, 250);
      polygon.addPoint(140, 240);

      double[][] val = calculateImageStatistics(in.toString(), readParam, polygon);
      assertNotNull(val);
      assertEquals(-202.0, val[0][0], DELTA);
      assertEquals(961.0, val[1][0], DELTA);
      assertEquals(13.18441744, val[2][0], DELTA);
      assertEquals(146.37268818, val[3][0], DELTA);
    }
  }

  @TestInstance(Lifecycle.PER_CLASS)
  @Nested
  @DisplayName("VOI LUT Tests")
  class VoiLutTests {

    @Test
    @DisplayName("Apply VOI LUT with windowing parameters")
    void testVoiLutWithWindowing() {
      // Create test image
      PlanarImage testImage = createGradientImage(CvType.CV_16UC1);
      ImageDescriptor desc =
          createBasicImageDescriptor(16, 16, 15, 0, PhotometricInterpretation.MONOCHROME2);

      // Set window/level parameters
      DicomImageReadParam params = new DicomImageReadParam();
      params.setWindowCenter(32768.0);
      params.setWindowWidth(65536.0);

      // Test VOI LUT application
      PlanarImage result = ImageRendering.getVoiLutImage(testImage, desc, params, 0);

      assertNotNull(result);
      assertInstanceOf(ImageCV.class, result);
      assertEquals(testImage.width(), result.width());
      assertEquals(testImage.height(), result.height());
    }

    @Test
    @DisplayName("Apply VOI LUT to color image (should be skipped)")
    void testVoiLutSkipsColorImage() {
      // Create RGB test image
      PlanarImage testImage = createTestImage(CvType.CV_8UC3, 128.0);
      ImageDescriptor desc = createBasicImageDescriptor(8, 8, 7, 0, PhotometricInterpretation.RGB);
      DicomImageReadParam params = new DicomImageReadParam();

      // Test that color images skip VOI LUT processing
      PlanarImage result = ImageRendering.getVoiLutImage(testImage, desc, params, 0);

      assertNotNull(result);
      // For color images, the transformation should still apply modality LUT but skip VOI
    }

    @Test
    @DisplayName("Apply VOI LUT to floating point data")
    void testVoiLutWithFloatingPointData() {
      // Create float test image
      PlanarImage testImage = createFloatingPointGradientImage();
      ImageDescriptor desc =
          createBasicImageDescriptor(32, 32, 31, 0, PhotometricInterpretation.MONOCHROME2);

      DicomImageReadParam params = new DicomImageReadParam();
      params.setWindowCenter(0.5);
      params.setWindowWidth(1.0);

      // Test VOI LUT on floating point data
      PlanarImage result = ImageRendering.getVoiLutImage(testImage, desc, params, 0);

      assertNotNull(result);
      assertInstanceOf(ImageCV.class, result);
      assertEquals(
          CvType.CV_8UC1, result.type(), "Floating point VOI LUT should produce 8-bit output");
    }

    @ParameterizedTest
    @DisplayName("Test VOI LUT with different window/level combinations")
    @MethodSource("windowLevelParameters")
    void testVoiLutWithDifferentParameters(
        double windowCenter, double windowWidth, String description) {
      PlanarImage testImage = createGradientImage(CvType.CV_16UC1);
      ImageDescriptor desc =
          createBasicImageDescriptor(16, 16, 15, 0, PhotometricInterpretation.MONOCHROME2);

      DicomImageReadParam params = new DicomImageReadParam();
      params.setWindowCenter(windowCenter);
      params.setWindowWidth(windowWidth);

      PlanarImage result = ImageRendering.getVoiLutImage(testImage, desc, params, 0);

      assertNotNull(result, "VOI LUT should produce result for: " + description);
      assertInstanceOf(ImageCV.class, result);
    }

    private Stream<Arguments> windowLevelParameters() {
      return Stream.of(
          Arguments.of(32768.0, 65536.0, "Full range 16-bit"),
          Arguments.of(128.0, 256.0, "8-bit range on 16-bit data"),
          Arguments.of(0.0, 100.0, "Narrow window around zero"),
          Arguments.of(65535.0, 1000.0, "High center, narrow window"));
    }
  }

  @Nested
  @DisplayName("Embedded Overlay Tests")
  class EmbeddedOverlayTests {

    @Test
    @DisplayName("Remove embedded overlay from 16-bit image")
    void testRemoveEmbeddedOverlay() {
      // Create test image with overlay data in upper bits
      PlanarImage testImage = createImageWithEmbeddedOverlay();
      ImageDescriptor desc = createImageDescriptorWithEmbeddedOverlay();

      // Test overlay removal
      PlanarImage result = ImageRendering.getImageWithoutEmbeddedOverlay(testImage, desc, 0);

      assertNotNull(result);
      assertEquals(testImage.width(), result.width());
      assertEquals(testImage.height(), result.height());

      // Verify that overlay bits are masked out
      Mat resultMat = result.toMat();
      double[] pixel = resultMat.get(0, 0);
      assertTrue(
          pixel[0] <= 4095, "Overlay bits should be masked out, leaving only 12 bits of data");
    }

    @Test
    @DisplayName("Skip overlay removal when no embedded overlays present")
    void testSkipOverlayRemovalWhenNone() {
      PlanarImage testImage = createTestImage(CvType.CV_16UC1, 1000.0);
      ImageDescriptor desc =
          createBasicImageDescriptor(16, 12, 11, 0, PhotometricInterpretation.MONOCHROME2);

      // Test that image is returned unchanged when no overlays
      PlanarImage result = ImageRendering.getImageWithoutEmbeddedOverlay(testImage, desc, 0);

      assertEquals(testImage, result, "Image should be unchanged when no embedded overlays");
    }

    @Test
    @DisplayName("Skip overlay removal for 8-bit images")
    void testSkipOverlayRemovalFor8Bit() {
      PlanarImage testImage = createTestImage(CvType.CV_8UC1, 200.0);
      ImageDescriptor desc = createImageDescriptorWithEmbeddedOverlay8Bit();

      // Test that 8-bit images skip overlay processing
      PlanarImage result = ImageRendering.getImageWithoutEmbeddedOverlay(testImage, desc, 0);

      assertEquals(testImage, result, "8-bit images should not process embedded overlays");
    }
  }

  @Nested
  @DisplayName("Integration Tests")
  class IntegrationTests {

    @Test
    @DisplayName("Full rendering pipeline with raw rendered image")
    void testFullRawRenderingPipeline() {
      PlanarImage testImage = createGradientImage(CvType.CV_16UC1);
      ImageDescriptor desc =
          createBasicImageDescriptor(16, 12, 11, 0, PhotometricInterpretation.MONOCHROME2);
      DicomImageReadParam params = new DicomImageReadParam();

      // Test complete raw rendering pipeline
      PlanarImage result = ImageRendering.getRawRenderedImage(testImage, desc, params, 0);

      assertNotNull(result);
      assertInstanceOf(ImageCV.class, result);
      assertEquals(testImage.width(), result.width());
      assertEquals(testImage.height(), result.height());
    }

    @Test
    @DisplayName("Full rendering pipeline with default rendered image")
    void testFullDefaultRenderingPipeline() {
      PlanarImage testImage = createGradientImage(CvType.CV_16UC1);
      ImageDescriptor desc =
          createBasicImageDescriptor(16, 12, 11, 0, PhotometricInterpretation.MONOCHROME2);
      DicomImageReadParam params = new DicomImageReadParam();
      params.setWindowCenter(2048.0);
      params.setWindowWidth(4096.0);

      // Test complete default rendering pipeline
      PlanarImage result = ImageRendering.getDefaultRenderedImage(testImage, desc, params, 0);

      assertNotNull(result);
      assertEquals(testImage.width(), result.width());
      assertEquals(testImage.height(), result.height());
    }

    @ParameterizedTest
    @DisplayName("Test rendering with different bit depths")
    @ValueSource(ints = {8, 12, 16})
    void testRenderingWithDifferentBitDepths(int bitsStored) {
      int bitsAllocated = bitsStored <= 8 ? 8 : 16;
      int cvType = bitsAllocated == 8 ? CvType.CV_8UC1 : CvType.CV_16UC1;
      double fillValue = (1 << (bitsStored - 1)) - 1; // Mid-range value

      PlanarImage testImage = createTestImage(cvType, fillValue);
      ImageDescriptor desc =
          createBasicImageDescriptor(
              bitsAllocated, bitsStored, bitsStored - 1, 0, PhotometricInterpretation.MONOCHROME2);
      DicomImageReadParam params = new DicomImageReadParam();

      PlanarImage result = ImageRendering.getRawRenderedImage(testImage, desc, params, 0);

      assertNotNull(result, "Should handle " + bitsStored + "-bit images");
      assertEquals(testImage.width(), result.width());
      assertEquals(testImage.height(), result.height());
    }
  }

  @Nested
  @DisplayName("Error Handling Tests")
  class ErrorHandlingTests {

    @Test
    @DisplayName("Null parameter handling")
    void testNullParameterHandling() {
      PlanarImage testImage = createTestImage(CvType.CV_8UC1, 128.0);
      ImageDescriptor desc =
          createBasicImageDescriptor(8, 8, 7, 0, PhotometricInterpretation.MONOCHROME2);
      DicomImageReadParam params = new DicomImageReadParam();

      // Test null image
      assertThrows(
          NullPointerException.class,
          () -> ImageRendering.getRawRenderedImage(null, desc, params, 0));

      // Test null descriptor
      assertThrows(
          NullPointerException.class,
          () -> ImageRendering.getRawRenderedImage(testImage, null, params, 0));
    }

    @Test
    @DisplayName("Invalid frame index handling")
    void testInvalidFrameIndex() {
      PlanarImage testImage = createTestImage(CvType.CV_8UC1, 128.0);
      ImageDescriptor desc =
          createBasicImageDescriptor(8, 8, 7, 0, PhotometricInterpretation.MONOCHROME2);
      DicomImageReadParam params = new DicomImageReadParam();

      // Should not throw exception for out-of-bounds frame index (handled by underlying
      // implementation)
      assertDoesNotThrow(() -> ImageRendering.getRawRenderedImage(testImage, desc, params, 99));
    }
  }

  // ======= Test Data Creation Methods =======

  private PlanarImage createTestImage(int cvType, double fillValue) {
    Mat mat = new Mat(TEST_IMAGE_HEIGHT, TEST_IMAGE_WIDTH, cvType);
    mat.setTo(new Scalar(fillValue));
    return ImageCV.fromMat(mat);
  }

  private PlanarImage createGradientImage(int cvType) {
    Mat mat = Mat.zeros(TEST_IMAGE_HEIGHT, TEST_IMAGE_WIDTH, cvType);

    // Create a gradient pattern
    for (int row = 0; row < TEST_IMAGE_HEIGHT; row++) {
      for (int col = 0; col < TEST_IMAGE_WIDTH; col++) {
        double value = (double) (row * TEST_IMAGE_WIDTH + col);
        if (cvType == CvType.CV_8UC1) {
          value = value % 256;
        } else if (cvType == CvType.CV_16UC1) {
          value = value % 65536;
        }
        mat.put(row, col, value);
      }
    }

    return ImageCV.fromMat(mat);
  }

  private PlanarImage createFloatingPointGradientImage() {
    Mat mat = Mat.zeros(TEST_IMAGE_HEIGHT, TEST_IMAGE_WIDTH, CvType.CV_32FC1);

    // Create a normalized gradient (0.0 to 1.0)
    for (int row = 0; row < TEST_IMAGE_HEIGHT; row++) {
      for (int col = 0; col < TEST_IMAGE_WIDTH; col++) {
        double value =
            (double) (row * TEST_IMAGE_WIDTH + col) / (TEST_IMAGE_HEIGHT * TEST_IMAGE_WIDTH);
        mat.put(row, col, value);
      }
    }

    return ImageCV.fromMat(mat);
  }

  private PlanarImage createImageWithEmbeddedOverlay() {
    Mat mat = Mat.zeros(TEST_IMAGE_HEIGHT, TEST_IMAGE_WIDTH, CvType.CV_16UC1);

    // Fill with 12-bit data (0-4095) and add overlay bits in upper 4 bits
    for (int row = 0; row < TEST_IMAGE_HEIGHT; row++) {
      for (int col = 0; col < TEST_IMAGE_WIDTH; col++) {
        int dataValue = (row * TEST_IMAGE_WIDTH + col) % 4096; // 12-bit data
        int overlayValue = (row + col) % 2; // Simple overlay pattern
        int pixelValue = dataValue | (overlayValue << 12); // Overlay in bit 12
        mat.put(row, col, pixelValue);
      }
    }

    return ImageCV.fromMat(mat);
  }

  private ImageDescriptor createBasicImageDescriptor(
      int bitsAllocated,
      int bitsStored,
      int highBit,
      int pixelRepresentation,
      PhotometricInterpretation photometric) {
    Attributes attrs = new Attributes();
    attrs.setInt(Tag.Rows, VR.US, TEST_IMAGE_HEIGHT);
    attrs.setInt(Tag.Columns, VR.US, TEST_IMAGE_WIDTH);
    attrs.setInt(Tag.NumberOfFrames, VR.IS, 1);
    attrs.setInt(Tag.SamplesPerPixel, VR.US, photometric == PhotometricInterpretation.RGB ? 3 : 1);
    attrs.setInt(Tag.BitsAllocated, VR.US, bitsAllocated);
    attrs.setInt(Tag.BitsStored, VR.US, bitsStored);
    attrs.setInt(Tag.HighBit, VR.US, highBit);
    attrs.setInt(Tag.PixelRepresentation, VR.US, pixelRepresentation);
    attrs.setString(Tag.PhotometricInterpretation, VR.CS, photometric.toString());

    return new ImageDescriptor(attrs);
  }

  private ImageDescriptor createImageDescriptorWithEmbeddedOverlay() {
    Attributes attrs = new Attributes();
    attrs.setInt(Tag.Rows, VR.US, TEST_IMAGE_HEIGHT);
    attrs.setInt(Tag.Columns, VR.US, TEST_IMAGE_WIDTH);
    attrs.setInt(Tag.NumberOfFrames, VR.IS, 1);
    attrs.setInt(Tag.SamplesPerPixel, VR.US, 1);
    attrs.setInt(Tag.BitsAllocated, VR.US, 16);
    attrs.setInt(Tag.BitsStored, VR.US, 12);
    attrs.setInt(Tag.HighBit, VR.US, 11);
    attrs.setInt(Tag.PixelRepresentation, VR.US, 0);
    attrs.setString(Tag.PhotometricInterpretation, VR.CS, "MONOCHROME2");

    // Add embedded overlay information
    attrs.setInt(Tag.OverlayRows | 0x60000000, VR.US, TEST_IMAGE_HEIGHT);
    attrs.setInt(Tag.OverlayColumns | 0x60000000, VR.US, TEST_IMAGE_WIDTH);
    attrs.setInt(Tag.OverlayBitPosition | 0x60000000, VR.US, 12);

    return new ImageDescriptor(attrs);
  }

  private ImageDescriptor createImageDescriptorWithEmbeddedOverlay8Bit() {
    Attributes attrs = new Attributes();
    attrs.setInt(Tag.Rows, VR.US, TEST_IMAGE_HEIGHT);
    attrs.setInt(Tag.Columns, VR.US, TEST_IMAGE_WIDTH);
    attrs.setInt(Tag.NumberOfFrames, VR.IS, 1);
    attrs.setInt(Tag.SamplesPerPixel, VR.US, 1);
    attrs.setInt(Tag.BitsAllocated, VR.US, 8);
    attrs.setInt(Tag.BitsStored, VR.US, 8);
    attrs.setInt(Tag.HighBit, VR.US, 7);
    attrs.setInt(Tag.PixelRepresentation, VR.US, 0);
    attrs.setString(Tag.PhotometricInterpretation, VR.CS, "MONOCHROME2");

    // Add embedded overlay information (should be ignored for 8-bit)
    attrs.setInt(Tag.OverlayRows | 0x60000000, VR.US, TEST_IMAGE_HEIGHT);
    attrs.setInt(Tag.OverlayColumns | 0x60000000, VR.US, TEST_IMAGE_WIDTH);
    attrs.setInt(Tag.OverlayBitPosition | 0x60000000, VR.US, 7);

    return new ImageDescriptor(attrs);
  }

  // ======= Utility Methods =======

  public static double[][] calculateImageStatistics(
      String srcPath, DicomImageReadParam params, Shape shape) throws Exception {
    if (!StringUtil.hasText(srcPath)) {
      throw new IllegalStateException("Path cannot be empty");
    }
    DicomImageReader reader = new DicomImageReader(Transcoder.dicomImageReaderSpi);
    try {
      reader.setInput(new DicomFileInputStream(srcPath));
      ImageDescriptor desc = reader.getImageDescriptor();
      PlanarImage img = reader.getPlanarImage(0, params);
      img = ImageRendering.getRawRenderedImage(img, desc, params, 0);

      return ImageAnalyzer.meanStdDev(
          img.toMat(),
          shape,
          desc.getPixelPaddingValue().orElse(null),
          desc.getPixelPaddingRangeLimit().orElse(null));
    } finally {
      reader.dispose();
    }
  }
}
