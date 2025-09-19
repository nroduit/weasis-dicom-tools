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
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.UID;
import org.dcm4che3.data.VR;
import org.dcm4che3.img.stream.BytesWithImageDescriptor;
import org.dcm4che3.img.stream.DicomFileInputStream;
import org.dcm4che3.img.stream.ImageDescriptor;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.weasis.opencv.data.ImageCV;
import org.weasis.opencv.data.PlanarImage;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DisplayNameGeneration(ReplaceUnderscores.class)
class DicomImageReaderTest {

  private static final Path TEST_RESOURCES = Path.of("target/test-classes/org/dcm4che3/img");

  private DicomImageReader reader;

  @BeforeAll
  void setUpAll() {
    reader = new DicomImageReader(new DicomImageReaderSpi());
  }

  @AfterAll
  void tearDownAll() {
    if (reader != null) {
      reader.dispose();
    }
  }

  private List<PlanarImage> readDicomFile(String filename) throws IOException {
    var inputStream = new DicomFileInputStream(TEST_RESOURCES.resolve(filename));
    reader.setInput(inputStream);
    return reader.getPlanarImages();
  }

  @Test
  void read_lossy_jpeg2000_multiframe_with_multi_fragments_stream() throws IOException {
    var images = readDicomFile("jpeg2000-multiframe-multifragments.dcm");

    assertEquals(19, images.size());
    assertEquals(19, reader.getNumImages(true));
    assertEquals(256, reader.getWidth(0));
    assertEquals(256, reader.getHeight(0));

    assertThrows(UnsupportedOperationException.class, () -> reader.getImageTypes(0));
    assertInstanceOf(DicomImageReadParam.class, reader.getDefaultReadParam());
    assertNull(reader.getImageMetadata(0));
    assertTrue(reader.canReadRaster());

    images.forEach(
        image -> {
          assertEquals(256, image.width());
          assertEquals(256, image.height());
        });
  }

  @Test
  void read_ybr_full_with_rle_compression() throws IOException {
    var images = readDicomFile("ybrFull-RLE.dcm");

    assertEquals(1, images.size());
    var image = images.get(0);
    assertEquals(640, image.width());
    assertEquals(480, image.height());

    // Test scaling with DicomImageReadParam
    var param = new DicomImageReadParam();
    param.setSourceRenderSize(new Dimension(320, 240));

    var raster = reader.readRaster(0, param);
    assertEquals(320, raster.getWidth());
    assertEquals(240, raster.getHeight());

    var bufferedImage = reader.read(0, null);
    assertEquals(640, bufferedImage.getWidth());
    assertEquals(480, bufferedImage.getHeight());

    // Test convenience methods
    assertEquals(1, reader.getPlanarImages().size());
    assertNotNull(reader.getPlanarImage());
    assertNotNull(reader.readRaster(0, null));
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "mono2-CT-16bit.dcm",
        "CT-JPEGLosslessSV1.dcm",
        "MR-JPEGLosslessSV1.dcm",
        "signed-raw-9bit.dcm"
      })
  void read_different_dicom_file_types(String filename) throws IOException {
    var images = readDicomFile(filename);

    assertNotNull(images);
    assertFalse(images.isEmpty());

    var firstImage = images.get(0);
    assertTrue(firstImage.width() > 0);
    assertTrue(firstImage.height() > 0);

    assertTrue(reader.getNumImages(true) > 0);
    assertTrue(reader.getWidth(0) > 0);
    assertTrue(reader.getHeight(0) > 0);
  }

  @Test
  void test_dicom_image_read_param_functionality() throws IOException {
    readDicomFile("ybrFull-RLE.dcm");

    var param = new DicomImageReadParam();

    // Test source region cropping
    var sourceRegion = new Rectangle(100, 100, 200, 200);
    param.setSourceRegion(sourceRegion);
    assertEquals(sourceRegion, param.getSourceRegion());

    // Test scaling
    var renderSize = new Dimension(320, 240);
    param.setSourceRenderSize(renderSize);
    assertEquals(renderSize, param.getSourceRenderSize());

    // Test window/level parameters
    param.setWindowCenter(500.0);
    param.setWindowWidth(1000.0);

    assertTrue(param.getWindowCenter().isPresent());
    assertEquals(500.0, param.getWindowCenter().orElse(0.0));
    assertTrue(param.getWindowWidth().isPresent());
    assertEquals(1000.0, param.getWindowWidth().orElse(0.0));

    // Test with actual image reading
    var processedImage = reader.getPlanarImage(0, param);
    assertNotNull(processedImage);
    assertEquals(320, processedImage.width());
    assertEquals(240, processedImage.height());
  }

  @Test
  void handle_invalid_frame_indices() throws IOException {
    readDicomFile("ybrFull-RLE.dcm");

    // Test negative frame index
    assertThrows(IndexOutOfBoundsException.class, () -> reader.getWidth(-1));
    assertThrows(IndexOutOfBoundsException.class, () -> reader.getHeight(-1));

    // Test frame index beyond available frames
    int numFrames = reader.getNumImages(true);
    assertThrows(IndexOutOfBoundsException.class, () -> reader.getWidth(numFrames));
    assertThrows(IndexOutOfBoundsException.class, () -> reader.getHeight(numFrames));
  }

  @Test
  void handle_bytes_with_image_descriptor_input() {
    var testDescriptor = createTestBytesDescriptor(256, 256, 1);

    reader.setInput(testDescriptor);
    var descriptor = reader.getImageDescriptor();

    assertNotNull(descriptor);
    assertEquals(256, descriptor.getColumns());
    assertEquals(256, descriptor.getRows());
    assertEquals(1, descriptor.getFrames());
  }

  @Test
  void create_lazy_planar_image_suppliers() throws IOException {
    readDicomFile("ybrFull-RLE.dcm");

    var lazyImages = reader.getLazyPlanarImages(null, null);

    assertNotNull(lazyImages);
    assertEquals(1, lazyImages.size());

    // Test lazy loading
    var imageSupplier = lazyImages.get(0);
    var image = imageSupplier.get();

    assertNotNull(image);
    assertEquals(640, image.width());
    assertEquals(480, image.height());

    // Test that calling get() again returns the same cached instance
    var sameImage = imageSupplier.get();
    assertEquals(image, sameImage);
  }

  @Test
  void test_static_utility_methods() {
    // Test transfer syntax support
    assertTrue(DicomImageReader.isSupportedSyntax(UID.ImplicitVRLittleEndian));
    assertTrue(DicomImageReader.isSupportedSyntax(UID.JPEGBaseline8Bit));
    assertTrue(DicomImageReader.isSupportedSyntax(UID.JPEG2000Lossless));
    assertFalse(DicomImageReader.isSupportedSyntax("1.2.3.4.5.6.7.8.9"));

    // Test float image conversion cache
    var seriesUID = "1.2.3.4.5.6.7.8.9.10";

    DicomImageReader.addSeriesToFloatImages(seriesUID, true);
    assertTrue(DicomImageReader.getForceToFloatImages(seriesUID));

    DicomImageReader.removeSeriesToFloatImages(seriesUID);
    assertNull(DicomImageReader.getForceToFloatImages(seriesUID));
  }

  @Test
  void handle_reader_disposal_and_cleanup() throws IOException {
    var testReader = new DicomImageReader(new DicomImageReaderSpi());

    var inputStream = new DicomFileInputStream(TEST_RESOURCES.resolve("ybrFull-RLE.dcm"));
    testReader.setInput(inputStream);

    assertEquals(1, testReader.getNumImages(true));

    testReader.dispose();
    assertThrows(IllegalStateException.class, testReader::getImageDescriptor);
  }

  @Test
  void test_bulk_data_descriptor() {
    var descriptor = DicomImageReader.BULK_DATA_DESCRIPTOR;
    assertNotNull(descriptor);

    // Test pixel data is bulk data
    assertTrue(descriptor.isBulkData(List.of(), null, Tag.PixelData, VR.OW, 1000));

    // Test non-bulk data
    assertFalse(descriptor.isBulkData(List.of(), null, Tag.PatientName, VR.PN, 50));

    // Test private tags
    assertTrue(descriptor.isBulkData(List.of(), null, 0x00091010, VR.LO, 2000));
    assertFalse(descriptor.isBulkData(List.of(), null, 0x00091010, VR.LO, 500));

    // Test large standard VRs
    assertTrue(descriptor.isBulkData(List.of(), null, Tag.StudyDescription, VR.OB, 100));
    assertFalse(descriptor.isBulkData(List.of(), null, Tag.StudyDescription, VR.LO, 30));
  }

  @Nested
  class Rescale_Operations_Tests {

    @ParameterizedTest
    @MethodSource("modalityTestCases")
    void range_outside_lut_with_modality_data(ModalityTestCase testCase) throws IOException {
      readDicomFile("mono2-CT-16bit.dcm");
      var descriptor = createTestDescriptor(testCase);
      var testImage = createTestImage(100, 100, CvType.CV_16U);

      try {
        var result =
            DicomImageReader.rangeOutsideLut(testImage, descriptor, 0, testCase.forceFloat);

        assertNotNull(result);
        assertEquals(testImage.width(), result.width());
        assertEquals(testImage.height(), result.height());

        if (testCase.shouldBeFloat) {
          assertEquals(CvType.CV_32F, CvType.depth(result.type()));
        }

        if (!result.equals(testImage)) {
          result.release();
        }
      } finally {
        testImage.release();
      }
    }

    @Test
    void range_outside_lut_with_real_dicom_files() throws IOException {
      var testFiles = List.of("mono2-CT-16bit.dcm", "signed-raw-9bit.dcm");

      for (var filename : testFiles) {
        readDicomFile(filename);
        var descriptor = reader.getImageDescriptor();
        var rawImage = reader.getRawImage(0, null);

        try {
          // Test normal conversion
          var result1 = DicomImageReader.rangeOutsideLut(rawImage, descriptor, 0, false);
          assertNotNull(result1, "Result for " + filename + " should not be null");

          // Test forced conversion
          var result2 = DicomImageReader.rangeOutsideLut(rawImage, descriptor, 0, true);
          assertNotNull(result2, "Forced result for " + filename + " should not be null");

          // Verify dimensions preserved
          assertEquals(rawImage.width(), result1.width());
          assertEquals(rawImage.height(), result1.height());
          assertEquals(rawImage.width(), result2.width());
          assertEquals(rawImage.height(), result2.height());

          // Clean up
          if (!result1.equals(rawImage)) result1.release();
          if (!result2.equals(rawImage)) result2.release();

        } finally {
          rawImage.release();
        }
      }
    }

    @Test
    void range_outside_lut_with_float_conversion_settings() throws IOException {
        readDicomFile("mono2-CT-16bit.dcm");
        var descriptor = reader.getImageDescriptor();
        var rawImage = reader.getRawImage(0, null);

        try {
          var seriesUID = descriptor.getSeriesInstanceUID();
          if (seriesUID != null) {
            // Test with series marked for float conversion
            DicomImageReader.addSeriesToFloatImages(seriesUID, true);

            var result = DicomImageReader.rangeOutsideLut(rawImage, descriptor, 0, false);
            assertNotNull(result);

            DicomImageReader.removeSeriesToFloatImages(seriesUID);

            if (!result.equals(rawImage)) {
              result.release();
            }
          }
        } finally {
          rawImage.release();
        }
    }

    static Stream<Arguments> modalityTestCases() {
      return Stream.of(
          Arguments.of(new ModalityTestCase("Normal slope", 1.0, 0.0, false, false)),
          Arguments.of(new ModalityTestCase("Small slope", 0.3, 100.0, false, true)),
          Arguments.of(new ModalityTestCase("Negative slope", -0.5, 2048.0, false, true)),
          Arguments.of(new ModalityTestCase("Forced float", 1.0, 0.0, true, true)));
    }

    record ModalityTestCase(
        String name, double slope, double intercept, boolean forceFloat, boolean shouldBeFloat) {
      @Override
      public String toString() {
        return name;
      }
    }

    private ImageDescriptor createTestDescriptor(ModalityTestCase testCase) {
      var attrs = new Attributes();
      attrs.setInt(Tag.Rows, VR.US, 100);
      attrs.setInt(Tag.Columns, VR.US, 100);
      attrs.setInt(Tag.NumberOfFrames, VR.IS, 1);
      attrs.setInt(Tag.SamplesPerPixel, VR.US, 1);
      attrs.setInt(Tag.BitsAllocated, VR.US, 16);
      attrs.setInt(Tag.BitsStored, VR.US, 16);
      attrs.setInt(Tag.HighBit, VR.US, 15);
      attrs.setInt(Tag.PixelRepresentation, VR.US, testCase.slope < 0 ? 1 : 0);
      attrs.setString(
          Tag.PhotometricInterpretation, VR.CS, testCase.slope < 0 ? "MONOCHROME1" : "MONOCHROME2");
      attrs.setDouble(Tag.RescaleSlope, VR.DS, testCase.slope);
      attrs.setDouble(Tag.RescaleIntercept, VR.DS, testCase.intercept);

      return new ImageDescriptor(attrs);
    }
  }

  @Nested
  class Input_Validation_Tests {

    @Test
    void reject_null_input() {
      assertThrows(NullPointerException.class, () -> reader.setInput(null, false, false));
    }

    @Test
    void reject_unsupported_input_type() {
      assertThrows(
          IllegalArgumentException.class, () -> reader.setInput("unsupported", false, false));
    }

    @Test
    void handle_invalid_frame_operations() throws IOException {
      readDicomFile("ybrFull-RLE.dcm");

      assertNull(reader.getPlanarImage(-1, null));
      assertNull(reader.getPlanarImage(999, null));
    }
  }

  // Helper methods using real data structures

  private BytesWithImageDescriptor createTestBytesDescriptor(int width, int height, int frames) {
    return new TestBytesDescriptor(width, height, frames);
  }

  private PlanarImage createTestImage(int width, int height, int type) {
    var mat = Mat.zeros(height, width, type);

    // Fill with test pattern
    var random = ThreadLocalRandom.current();
    for (int row = 0; row < height; row++) {
      for (int col = 0; col < width; col++) {
        double value =
            switch (CvType.depth(type)) {
              case CvType.CV_8U -> random.nextInt(256);
              case CvType.CV_16U -> random.nextInt(65536);
              case CvType.CV_32F -> random.nextDouble() * 1000.0;
              default -> row * width + col;
            };
        mat.put(row, col, value);
      }
    }

    return ImageCV.fromMat(mat);
  }

  /** Test implementation of BytesWithImageDescriptor using real data structures */
  private static class TestBytesDescriptor implements BytesWithImageDescriptor {
    private final ImageDescriptor descriptor;
    private final int pixelDataSize;

    TestBytesDescriptor(int width, int height, int frames) {
      this.descriptor = createImageDescriptor(width, height, frames);
      this.pixelDataSize = width * height * frames;
    }

    @Override
    public ByteBuffer getBytes(int frame) {
      // Generate realistic test pixel data
      var pixelData = new byte[pixelDataSize];
      var random = ThreadLocalRandom.current();

      // Create gradient pattern with some noise
      IntStream.range(0, pixelDataSize)
          .parallel()
          .forEach(
              i -> {
                int baseValue = (i * 255) / pixelDataSize;
                int noise = random.nextInt(-10, 11);
                pixelData[i] = (byte) Math.max(0, Math.min(255, baseValue + noise));
              });

      return ByteBuffer.wrap(pixelData);
    }

    @Override
    public String getTransferSyntax() {
      return UID.ImplicitVRLittleEndian;
    }

    @Override
    public VR getPixelDataVR() {
      return VR.OB;
    }

    @Override
    public ImageDescriptor getImageDescriptor() {
      return descriptor;
    }

    private static ImageDescriptor createImageDescriptor(int width, int height, int frames) {
      var attrs = new Attributes();
      attrs.setInt(Tag.Rows, VR.US, height);
      attrs.setInt(Tag.Columns, VR.US, width);
      attrs.setInt(Tag.NumberOfFrames, VR.IS, frames);
      attrs.setInt(Tag.SamplesPerPixel, VR.US, 1);
      attrs.setInt(Tag.BitsAllocated, VR.US, 8);
      attrs.setInt(Tag.BitsStored, VR.US, 8);
      attrs.setInt(Tag.HighBit, VR.US, 7);
      attrs.setInt(Tag.PixelRepresentation, VR.US, 0);
      attrs.setString(Tag.PhotometricInterpretation, VR.CS, "MONOCHROME2");
      attrs.setString(Tag.SeriesInstanceUID, VR.UI, "1.2.3.4.5.6.7.8.9.10");

      return new ImageDescriptor(attrs);
    }
  }
}
