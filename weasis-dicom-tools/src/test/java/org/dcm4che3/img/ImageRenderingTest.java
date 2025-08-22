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

import java.awt.Polygon;
import java.awt.Shape;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Stream;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.UID;
import org.dcm4che3.data.VR;
import org.dcm4che3.image.PhotometricInterpretation;
import org.dcm4che3.img.stream.DicomFileInputStream;
import org.dcm4che3.img.stream.ImageDescriptor;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
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
@DisplayNameGeneration(ReplaceUnderscores.class)
class ImageRenderingTest {

  private static final double DELTA = 0.0001;
  private static final int TEST_IMAGE_WIDTH = 256;
  private static final int TEST_IMAGE_HEIGHT = 256;

  private TestDataFactory testDataFactory;

  @BeforeAll
  static void load_native_lib() {
    new OpenCVNativeLoader().init();
  }

  @BeforeAll
  void setup_test_factory() {
    testDataFactory = new TestDataFactory();
  }

  @Nested
  class Embedded_overlay_processing {

    @Test
    void should_remove_embedded_overlay_from_16_bit_image() {
      // Create test image with embedded overlay data
      var imageWithOverlay = testDataFactory.createImageWithEmbeddedOverlay();
      var descriptor = testDataFactory.createImageDescriptorWithEmbeddedOverlay();

      var result = ImageRendering.getImageWithoutEmbeddedOverlay(imageWithOverlay, descriptor, 0);

      assertNotNull(result);
      assertEquals(imageWithOverlay.width(), result.width());
      assertEquals(imageWithOverlay.height(), result.height());

      // Verify overlay bits are masked out (keeping only 12-bit data)
      verifyOverlayBitsRemoved(imageWithOverlay, result, 0x0FFF);
    }

    @Test
    void should_return_original_image_when_no_embedded_overlays() {
      var originalImage = testDataFactory.createTestImage(CvType.CV_16UC1, 1234.0);
      var descriptor =
          testDataFactory.createBasicImageDescriptor(
              16, 12, 11, false, PhotometricInterpretation.MONOCHROME2);

      var result = ImageRendering.getImageWithoutEmbeddedOverlay(originalImage, descriptor, 0);

      assertSame(originalImage, result);
    }

    @Test
    void should_skip_overlay_removal_for_8_bit_images() {
      var testImage = testDataFactory.createTestImage(CvType.CV_8UC1, 200.0);
      var descriptor = testDataFactory.createImageDescriptorWithEmbeddedOverlay8Bit();

      var result = ImageRendering.getImageWithoutEmbeddedOverlay(testImage, descriptor, 0);

      assertEquals(testImage, result);
    }

    @Test
    void should_throw_exception_for_null_parameters() {
      var descriptor =
          testDataFactory.createBasicImageDescriptor(
              16, 12, 11, false, PhotometricInterpretation.MONOCHROME2);
      var image = testDataFactory.createTestImage(CvType.CV_16UC1, 100.0);

      assertThrows(
          NullPointerException.class,
          () -> ImageRendering.getImageWithoutEmbeddedOverlay(null, descriptor, 0));

      assertThrows(
          NullPointerException.class,
          () -> ImageRendering.getImageWithoutEmbeddedOverlay(image, null, 0));
    }

    private void verifyOverlayBitsRemoved(PlanarImage original, PlanarImage result, int mask) {
      // Sample pixels to verify overlay mask was applied
      for (int row = 0; row < Math.min(5, result.height()); row++) {
        for (int col = 0; col < Math.min(5, result.width()); col++) {
          double[] originalPixel = original.get(row, col);
          double[] resultPixel = result.get(row, col);

          int originalValue = (int) originalPixel[0];
          int resultValue = (int) resultPixel[0];
          int expectedValue = originalValue & mask;

          assertEquals(
              expectedValue,
              resultValue,
              "Pixel at (%d,%d) should have overlay bits removed. Original: %d, Result: %d, Expected: %d"
                  .formatted(row, col, originalValue, resultValue, expectedValue));
        }
      }
    }
  }

  @Nested
  class Modality_lut_processing {

    @Test
    void should_apply_modality_lut_to_8_bit_unsigned_image() {
      var testImage = testDataFactory.createTestImage(CvType.CV_8UC1, 128.0);
      var descriptor =
          testDataFactory.createBasicImageDescriptor(
              8, 8, 7, false, PhotometricInterpretation.MONOCHROME2);
      var params = new DicomImageReadParam();

      var adapter = new DicomImageAdapter(testImage, descriptor, 0);
      var result = ImageRendering.getModalityLutImage(testImage, adapter, params);

      assertNotNull(result);
      assertInstanceOf(ImageCV.class, result);
      assertEquals(testImage.width(), result.width());
      assertEquals(testImage.height(), result.height());
    }

    @Test
    void should_apply_modality_lut_to_16_bit_signed_image() {
      var testImage = testDataFactory.createTestImage(CvType.CV_16SC1, -1024.0);
      var descriptor =
          testDataFactory.createBasicImageDescriptor(
              16, 12, 11, true, PhotometricInterpretation.MONOCHROME2);
      var params = new DicomImageReadParam();

      var adapter = new DicomImageAdapter(testImage, descriptor, 0);
      var result = ImageRendering.getModalityLutImage(testImage, adapter, params);

      assertNotNull(result);
      assertEquals(testImage.width(), result.width());
      assertEquals(testImage.height(), result.height());
    }

    @Test
    void should_skip_modality_lut_for_floating_point_data() {
      var testImage = testDataFactory.createTestImage(CvType.CV_32FC1, 0.5);
      var descriptor =
          testDataFactory.createBasicImageDescriptor(
              32, 32, 31, false, PhotometricInterpretation.MONOCHROME2);
      var params = new DicomImageReadParam();

      var adapter = new DicomImageAdapter(testImage, descriptor, 0);
      var result = ImageRendering.getModalityLutImage(testImage, adapter, params);

      assertEquals(testImage, result);
    }

    @Test
    void should_validate_modality_lut_statistics_with_real_dicom_file() throws Exception {
      var resourcePath =
          Paths.get(
              Objects.requireNonNull(DicomImageReaderTest.class.getResource("mono2-CT-16bit.dcm"))
                  .toURI());
      var readParam = new DicomImageReadParam();

      var polygon = createTestPolygon();
      double[][] statistics = calculateImageStatistics(resourcePath.toString(), readParam, polygon);

      assertNotNull(statistics);
      assertEquals(-202.0, statistics[0][0], DELTA, "Mean should match expected value");
      assertEquals(961.0, statistics[1][0], DELTA, "Max should match expected value");
      assertEquals(
          13.18441744, statistics[2][0], DELTA, "Standard deviation should match expected value");
      assertEquals(146.37268818, statistics[3][0], DELTA, "Variance should match expected value");
    }

    private Polygon createTestPolygon() {
      var polygon = new Polygon();
      polygon.addPoint(150, 200);
      polygon.addPoint(200, 190);
      polygon.addPoint(200, 250);
      polygon.addPoint(140, 240);
      return polygon;
    }
  }

  @Nested
  @TestInstance(Lifecycle.PER_CLASS)
  class Voi_lut_processing {

    @Test
    void should_apply_voi_lut_with_windowing_parameters() {
      var testImage = testDataFactory.createGradientImage(CvType.CV_16UC1);
      var descriptor =
          testDataFactory.createBasicImageDescriptor(
              16, 16, 15, false, PhotometricInterpretation.MONOCHROME2);

      var params = new DicomImageReadParam();
      params.setWindowCenter(32768.0);
      params.setWindowWidth(65536.0);

      var result = ImageRendering.getVoiLutImage(testImage, descriptor, params, 0);

      assertNotNull(result);
      assertInstanceOf(ImageCV.class, result);
      assertEquals(testImage.width(), result.width());
      assertEquals(testImage.height(), result.height());
    }

    @Test
    void should_skip_voi_lut_for_color_images() {
      var testImage = testDataFactory.createTestImage(CvType.CV_8UC3, 128.0);
      var descriptor =
          testDataFactory.createBasicImageDescriptor(8, 8, 7, false, PhotometricInterpretation.RGB);
      var params = new DicomImageReadParam();

      var result = ImageRendering.getVoiLutImage(testImage, descriptor, params, 0);

      assertNotNull(result);
    }

    @Test
    void should_apply_voi_lut_to_floating_point_data() {
      var testImage = testDataFactory.createFloatingPointGradientImage();
      var descriptor =
          testDataFactory.createBasicImageDescriptor(
              32, 32, 31, false, PhotometricInterpretation.MONOCHROME2);

      var params = new DicomImageReadParam();
      params.setWindowCenter(0.5);
      params.setWindowWidth(1.0);

      var result = ImageRendering.getVoiLutImage(testImage, descriptor, params, 0);

      assertNotNull(result);
      assertInstanceOf(ImageCV.class, result);
      assertEquals(CvType.CV_8UC1, result.type());
    }

    @ParameterizedTest
    @MethodSource("provide_window_level_parameters")
    void should_handle_different_window_level_combinations(
        double windowCenter, double windowWidth, String description) {
      var testImage = testDataFactory.createGradientImage(CvType.CV_16UC1);
      var descriptor =
          testDataFactory.createBasicImageDescriptor(
              16, 16, 15, false, PhotometricInterpretation.MONOCHROME2);

      var params = new DicomImageReadParam();
      params.setWindowCenter(windowCenter);
      params.setWindowWidth(windowWidth);

      var result = ImageRendering.getVoiLutImage(testImage, descriptor, params, 0);

      assertNotNull(result, "VOI LUT should produce result for: " + description);
      assertInstanceOf(ImageCV.class, result);
    }

    Stream<Arguments> provide_window_level_parameters() {
      return Stream.of(
          Arguments.of(32768.0, 65536.0, "Full range 16-bit"),
          Arguments.of(128.0, 256.0, "8-bit range on 16-bit data"),
          Arguments.of(0.0, 100.0, "Narrow window around zero"),
          Arguments.of(65535.0, 1000.0, "High center, narrow window"));
    }
  }

  @Nested
  class Integration_tests {

    @Test
    void should_complete_raw_rendering_pipeline() {
      var testImage = testDataFactory.createGradientImage(CvType.CV_16UC1);
      var descriptor =
          testDataFactory.createBasicImageDescriptor(
              16, 12, 11, false, PhotometricInterpretation.MONOCHROME2);
      var params = new DicomImageReadParam();

      var result = ImageRendering.getRawRenderedImage(testImage, descriptor, params, 0);

      assertNotNull(result);
      assertInstanceOf(ImageCV.class, result);
      assertEquals(testImage.width(), result.width());
      assertEquals(testImage.height(), result.height());
    }

    @Test
    void should_complete_default_rendering_pipeline() {
      var testImage = testDataFactory.createGradientImage(CvType.CV_16UC1);
      var descriptor =
          testDataFactory.createBasicImageDescriptor(
              16, 12, 11, false, PhotometricInterpretation.MONOCHROME2);

      var params = new DicomImageReadParam();
      params.setWindowCenter(2048.0);
      params.setWindowWidth(4096.0);

      var result = ImageRendering.getDefaultRenderedImage(testImage, descriptor, params, 0);

      assertNotNull(result);
      assertEquals(testImage.width(), result.width());
      assertEquals(testImage.height(), result.height());
    }

    @ParameterizedTest
    @ValueSource(ints = {8, 12, 16})
    void should_handle_different_bit_depths(int bitsStored) {
      int bitsAllocated = bitsStored <= 8 ? 8 : 16;
      int cvType = bitsAllocated == 8 ? CvType.CV_8UC1 : CvType.CV_16UC1;
      double fillValue = (1 << (bitsStored - 1)) - 1; // Mid-range value

      var testImage = testDataFactory.createTestImage(cvType, fillValue);
      var descriptor =
          testDataFactory.createBasicImageDescriptor(
              bitsAllocated,
              bitsStored,
              bitsStored - 1,
              false,
              PhotometricInterpretation.MONOCHROME2);
      var params = new DicomImageReadParam();

      var result = ImageRendering.getRawRenderedImage(testImage, descriptor, params, 0);

      assertNotNull(result, "Should handle %d-bit images".formatted(bitsStored));
      assertEquals(testImage.width(), result.width());
      assertEquals(testImage.height(), result.height());
    }
  }

  @Nested
  class Error_handling {

    @Test
    void should_handle_null_parameters_gracefully() {
      var testImage = testDataFactory.createTestImage(CvType.CV_8UC1, 128.0);
      var descriptor =
          testDataFactory.createBasicImageDescriptor(
              8, 8, 7, false, PhotometricInterpretation.MONOCHROME2);
      var params = new DicomImageReadParam();

      assertThrows(
          NullPointerException.class,
          () -> ImageRendering.getRawRenderedImage(null, descriptor, params, 0));

      assertThrows(
          NullPointerException.class,
          () -> ImageRendering.getRawRenderedImage(testImage, null, params, 0));

      testImage.close();
    }

    @Test
    void should_handle_invalid_frame_index_gracefully() {
      var testImage = testDataFactory.createTestImage(CvType.CV_8UC1, 128.0);
      var descriptor =
          testDataFactory.createBasicImageDescriptor(
              8, 8, 7, false, PhotometricInterpretation.MONOCHROME2);
      var params = new DicomImageReadParam();

      // Should not throw exception for out-of-bounds frame index
      assertDoesNotThrow(
          () -> ImageRendering.getRawRenderedImage(testImage, descriptor, params, 99).close());

      testImage.close();
    }
  }

  // ======= Test Data Factory =======

  /** Factory for creating test data using real DICOM structures instead of mocks */
  private static class TestDataFactory {

    PlanarImage createTestImage(int cvType, double fillValue) {
      var mat = new Mat(TEST_IMAGE_HEIGHT, TEST_IMAGE_WIDTH, cvType);
      mat.setTo(new Scalar(fillValue));
      return ImageCV.fromMat(mat);
    }

    PlanarImage createGradientImage(int cvType) {
      var mat = Mat.zeros(TEST_IMAGE_HEIGHT, TEST_IMAGE_WIDTH, cvType);

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

    PlanarImage createFloatingPointGradientImage() {
      var mat = Mat.zeros(TEST_IMAGE_HEIGHT, TEST_IMAGE_WIDTH, CvType.CV_32FC1);

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

    PlanarImage createImageWithEmbeddedOverlay() {
      var mat = Mat.zeros(TEST_IMAGE_HEIGHT, TEST_IMAGE_WIDTH, CvType.CV_16UC1);

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

    ImageDescriptor createBasicImageDescriptor(
        int bitsAllocated,
        int bitsStored,
        int highBit,
        boolean signed,
        PhotometricInterpretation photometric) {
      var attrs = new Attributes();
      attrs.setInt(Tag.Rows, VR.US, TEST_IMAGE_HEIGHT);
      attrs.setInt(Tag.Columns, VR.US, TEST_IMAGE_WIDTH);
      attrs.setInt(Tag.NumberOfFrames, VR.IS, 1);
      attrs.setInt(
          Tag.SamplesPerPixel, VR.US, photometric == PhotometricInterpretation.RGB ? 3 : 1);
      attrs.setInt(Tag.BitsAllocated, VR.US, bitsAllocated);
      attrs.setInt(Tag.BitsStored, VR.US, bitsStored);
      attrs.setInt(Tag.HighBit, VR.US, highBit);
      attrs.setInt(Tag.PixelRepresentation, VR.US, signed ? 1 : 0);
      attrs.setString(Tag.PhotometricInterpretation, VR.CS, photometric.toString());
      attrs.setString(Tag.TransferSyntaxUID, VR.UI, UID.ExplicitVRLittleEndian);

      return new ImageDescriptor(attrs);
    }

    ImageDescriptor createImageDescriptorWithEmbeddedOverlay() {
      var attrs = new Attributes();
      attrs.setInt(Tag.Rows, VR.US, TEST_IMAGE_HEIGHT);
      attrs.setInt(Tag.Columns, VR.US, TEST_IMAGE_WIDTH);
      attrs.setInt(Tag.NumberOfFrames, VR.IS, 1);
      attrs.setInt(Tag.SamplesPerPixel, VR.US, 1);
      attrs.setInt(Tag.BitsAllocated, VR.US, 16);
      attrs.setInt(Tag.BitsStored, VR.US, 12);
      attrs.setInt(Tag.HighBit, VR.US, 11);
      attrs.setInt(Tag.PixelRepresentation, VR.US, 0);
      attrs.setString(Tag.PhotometricInterpretation, VR.CS, "MONOCHROME2");
      attrs.setString(Tag.TransferSyntaxUID, VR.UI, UID.ExplicitVRLittleEndian);

      // Add embedded overlay information
      attrs.setInt(Tag.OverlayRows | 0x60000000, VR.US, TEST_IMAGE_HEIGHT);
      attrs.setInt(Tag.OverlayColumns | 0x60000000, VR.US, TEST_IMAGE_WIDTH);
      int groupOffset = 1 << 17;
      attrs.setString(Tag.OverlayType | groupOffset, VR.CS, "G");
      attrs.setInt(Tag.OverlayOrigin | groupOffset, VR.SS, 1, 1);
      attrs.setInt(Tag.OverlayBitsAllocated | groupOffset, VR.US, 16);
      attrs.setInt(Tag.OverlayBitPosition | groupOffset, VR.US, 15);

      return new ImageDescriptor(attrs);
    }

    ImageDescriptor createImageDescriptorWithEmbeddedOverlay8Bit() {
      var attrs = new Attributes();
      attrs.setInt(Tag.Rows, VR.US, TEST_IMAGE_HEIGHT);
      attrs.setInt(Tag.Columns, VR.US, TEST_IMAGE_WIDTH);
      attrs.setInt(Tag.NumberOfFrames, VR.IS, 1);
      attrs.setInt(Tag.SamplesPerPixel, VR.US, 1);
      attrs.setInt(Tag.BitsAllocated, VR.US, 8);
      attrs.setInt(Tag.BitsStored, VR.US, 8);
      attrs.setInt(Tag.HighBit, VR.US, 7);
      attrs.setInt(Tag.PixelRepresentation, VR.US, 0);
      attrs.setString(Tag.PhotometricInterpretation, VR.CS, "MONOCHROME2");
      attrs.setString(Tag.TransferSyntaxUID, VR.UI, UID.ExplicitVRLittleEndian);

      // Add embedded overlay information (should be ignored for 8-bit)
      attrs.setInt(Tag.OverlayRows | 0x60000000, VR.US, TEST_IMAGE_HEIGHT);
      attrs.setInt(Tag.OverlayColumns | 0x60000000, VR.US, TEST_IMAGE_WIDTH);
      attrs.setInt(Tag.OverlayOrigin | 0x60000000, VR.SS, 1, 1);
      attrs.setInt(Tag.OverlayBitsAllocated | 0x60000000, VR.US, 8);
      attrs.setInt(Tag.OverlayBitPosition | 0x60000000, VR.US, 7);

      return new ImageDescriptor(attrs);
    }
  }

  // ======= Utility Methods =======

  static double[][] calculateImageStatistics(
      String srcPath, DicomImageReadParam params, Shape shape) throws Exception {
    if (!StringUtil.hasText(srcPath)) {
      throw new IllegalStateException("Path cannot be empty");
    }

    var reader = new DicomImageReader(Transcoder.dicomImageReaderSpi);
    try {
      reader.setInput(new DicomFileInputStream(srcPath));
      var descriptor = reader.getImageDescriptor();
      var img = reader.getPlanarImage(0, params);
      img = ImageRendering.getRawRenderedImage(img, descriptor, params, 0);

      return ImageAnalyzer.meanStdDev(
          img.toMat(),
          shape,
          descriptor.getPixelPaddingValue().orElse(null),
          descriptor.getPixelPaddingRangeLimit().orElse(null));
    } finally {
      reader.dispose();
    }
  }
}
