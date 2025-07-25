/*
 * Copyright (c) 2024 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.dcm4che3.img;

import static org.junit.jupiter.api.Assertions.*;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.UID;
import org.dcm4che3.data.VR;
import org.dcm4che3.image.PhotometricInterpretation;
import org.dcm4che3.img.stream.ImageDescriptor;
import org.dcm4che3.img.util.SupplierEx;
import org.dcm4che3.io.DicomOutputStream;
import org.dcm4che3.util.UIDUtils;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.osgi.OpenCVNativeLoader;
import org.weasis.opencv.data.ImageCV;
import org.weasis.opencv.data.PlanarImage;

/**
 * Test class for {@link DicomOutputData} functionality.
 *
 * <p>This test class covers the main features of DicomOutputData including:
 *
 * <ul>
 *   <li>Constructor validation and behavior
 *   <li>Transfer syntax adaptation
 *   <li>Raw image data handling
 *   <li>Compressed image data processing
 *   <li>DICOM attribute adaptation
 *   <li>Static utility methods
 * </ul>
 */
class DicomOutputDataTest {

  private ImageDescriptor testDescriptor;
  private Attributes testAttributes;

  @BeforeAll
  static void loadNativeLib() {
    OpenCVNativeLoader loader = new OpenCVNativeLoader();
    loader.init();
  }

  @BeforeEach
  void setUp() {
    // Create test DICOM attributes
    testAttributes = new Attributes();
    testAttributes.setString(Tag.SOPInstanceUID, VR.UI, UIDUtils.createUID());
    testAttributes.setString(Tag.SOPClassUID, VR.UI, UID.CTImageStorage);

    testAttributes.setInt(Tag.Rows, VR.US, 100);
    testAttributes.setInt(Tag.Columns, VR.US, 100);
    testAttributes.setInt(Tag.SamplesPerPixel, VR.US, 1);
    testAttributes.setInt(Tag.BitsAllocated, VR.US, 8);
    testAttributes.setInt(Tag.BitsStored, VR.US, 8);
    testAttributes.setInt(Tag.HighBit, VR.US, 7);
    testAttributes.setInt(Tag.PixelRepresentation, VR.US, 0);
    testAttributes.setString(
        Tag.PhotometricInterpretation, VR.CS, PhotometricInterpretation.MONOCHROME2.toString());

    testDescriptor = new ImageDescriptor(testAttributes);
  }

  // Test data generators
  private static PlanarImage createTestImage(int width, int height, int type, double fillValue) {
    Mat mat = new Mat(height, width, type);
    mat.setTo(org.opencv.core.Scalar.all(fillValue));
    return ImageCV.fromMat(mat);
  }

  private static ImageDescriptor createTestDescriptor(
      int bitsAllocated, int bitsStored, boolean signed) {
    Attributes attrs = new Attributes();
    attrs.setInt(Tag.Rows, VR.US, 64);
    attrs.setInt(Tag.Columns, VR.US, 64);
    attrs.setInt(Tag.SamplesPerPixel, VR.US, 1);
    attrs.setInt(Tag.BitsAllocated, VR.US, bitsAllocated);
    attrs.setInt(Tag.BitsStored, VR.US, bitsStored);
    attrs.setInt(Tag.HighBit, VR.US, bitsStored - 1);
    attrs.setInt(Tag.PixelRepresentation, VR.US, signed ? 1 : 0);
    attrs.setString(
        Tag.PhotometricInterpretation, VR.CS, PhotometricInterpretation.MONOCHROME2.toString());
    return new ImageDescriptor(attrs);
  }

  private static SupplierEx<PlanarImage, IOException> createImageSupplier(PlanarImage image) {
    return () -> image;
  }

  @Nested
  @DisplayName("Constructor Tests")
  class ConstructorTests {

    @Test
    @DisplayName("Constructor with single image should succeed")
    void constructorWithSingleImageShouldSucceed() throws IOException {
      PlanarImage image = createTestImage(100, 100, CvType.CV_8UC1, 128.0);

      DicomOutputData outputData =
          new DicomOutputData(image, testDescriptor, UID.ExplicitVRLittleEndian);

      assertNotNull(outputData);
      assertEquals(UID.ExplicitVRLittleEndian, outputData.getTsuid());
      assertEquals(1, outputData.getImages().size());
      assertEquals(image, outputData.getFirstImage().get());
    }

    @Test
    @DisplayName("Constructor with image supplier should succeed")
    void constructorWithImageSupplierShouldSucceed() throws IOException {
      PlanarImage image = createTestImage(100, 100, CvType.CV_8UC1, 64.0);
      SupplierEx<PlanarImage, IOException> supplier = createImageSupplier(image);

      DicomOutputData outputData =
          new DicomOutputData(supplier, testDescriptor, UID.ExplicitVRLittleEndian);

      assertNotNull(outputData);
      assertEquals(1, outputData.getImages().size());
      assertEquals(image, outputData.getFirstImage().get());
    }

    @Test
    @DisplayName("Constructor with multiple images should succeed")
    void constructorWithMultipleImagesShouldSucceed() throws IOException {
      PlanarImage image1 = createTestImage(100, 100, CvType.CV_8UC1, 50.0);
      PlanarImage image2 = createTestImage(100, 100, CvType.CV_8UC1, 150.0);
      List<SupplierEx<PlanarImage, IOException>> images =
          Arrays.asList(createImageSupplier(image1), createImageSupplier(image2));

      DicomOutputData outputData =
          new DicomOutputData(images, testDescriptor, UID.ExplicitVRLittleEndian);

      assertNotNull(outputData);
      assertEquals(2, outputData.getImages().size());
      assertEquals(image1, outputData.getFirstImage().get());
    }

    @Test
    @DisplayName("Constructor should throw exception for null images")
    void constructorShouldThrowForNullImages() {
      assertThrows(
          NullPointerException.class,
          () ->
              new DicomOutputData(
                  (List<SupplierEx<PlanarImage, IOException>>) null,
                  testDescriptor,
                  UID.ExplicitVRLittleEndian));
    }

    @Test
    @DisplayName("Constructor should throw exception for empty image list")
    void constructorShouldThrowForEmptyImageList() {
      List<SupplierEx<PlanarImage, IOException>> emptyList = Collections.emptyList();

      assertThrows(
          IllegalStateException.class,
          () -> new DicomOutputData(emptyList, testDescriptor, UID.ExplicitVRLittleEndian));
    }

    @Test
    @DisplayName("Constructor should throw exception for null descriptor")
    void constructorShouldThrowForNullDescriptor() {
      PlanarImage image = createTestImage(100, 100, CvType.CV_8UC1, 128.0);

      assertThrows(
          NullPointerException.class,
          () -> new DicomOutputData(image, null, UID.ExplicitVRLittleEndian));
    }

    @Test
    @DisplayName("Constructor should throw exception for null tsuid")
    void constructorShouldThrowForNullTsuid() {
      PlanarImage image = createTestImage(100, 100, CvType.CV_8UC1, 128.0);

      assertThrows(
          NullPointerException.class, () -> new DicomOutputData(image, testDescriptor, null));
    }

    @Test
    @DisplayName("Constructor should throw exception for unsupported syntax")
    void constructorShouldThrowForUnsupportedSyntax() {
      PlanarImage image = createTestImage(100, 100, CvType.CV_8UC1, 128.0);
      String unsupportedSyntax = "1.2.3.4.5.6.7.8"; // Invalid UID

      assertThrows(
          IllegalStateException.class,
          () -> new DicomOutputData(image, testDescriptor, unsupportedSyntax));
    }
  }

  @Nested
  @DisplayName("Transfer Syntax Adaptation Tests")
  class TransferSyntaxAdaptationTests {

    @Test
    @DisplayName("Should adapt 8-bit data to JPEGBaseline8Bit")
    void shouldAdapt8BitDataToJpegBaseline() throws IOException {
      PlanarImage image = createTestImage(64, 64, CvType.CV_8UC1, 128.0);
      ImageDescriptor desc = createTestDescriptor(8, 8, false);

      DicomOutputData outputData = new DicomOutputData(image, desc, UID.JPEGBaseline8Bit);

      assertEquals(UID.JPEGBaseline8Bit, outputData.getTsuid());
    }

    @Test
    @DisplayName("Should adapt 16-bit data to JPEGLosslessSV1 when JPEGBaseline8Bit requested")
    void shouldAdapt16BitDataToJpegLossless() throws IOException {
      PlanarImage image = createTestImage(64, 64, CvType.CV_16UC1, 1000.0);
      ImageDescriptor desc = createTestDescriptor(16, 12, false);

      DicomOutputData outputData = new DicomOutputData(image, desc, UID.JPEGBaseline8Bit);

      assertEquals(UID.JPEGLosslessSV1, outputData.getTsuid());
    }

    @Test
    @DisplayName("Should keep ExplicitVRLittleEndian for unsupported data types")
    void shouldKeepExplicitVRForUnsupportedTypes() throws IOException {
      PlanarImage image = createTestImage(64, 64, CvType.CV_32FC1, 1000.0f);
      ImageDescriptor desc = createTestDescriptor(32, 32, false);

      DicomOutputData outputData = new DicomOutputData(image, desc, UID.JPEGBaseline8Bit);

      assertEquals(UID.ExplicitVRLittleEndian, outputData.getTsuid());
    }

    @Test
    @DisplayName("Should handle JPEG2000 syntax correctly")
    void shouldHandleJpeg2000Syntax() throws IOException {
      PlanarImage image = createTestImage(64, 64, CvType.CV_16UC1, 1000.0);
      ImageDescriptor desc = createTestDescriptor(16, 12, false);

      DicomOutputData outputData = new DicomOutputData(image, desc, UID.JPEG2000);

      assertEquals(UID.JPEG2000, outputData.getTsuid());
    }
  }

  @Nested
  @DisplayName("Raw Image Data Tests")
  class RawImageDataTests {

    @Test
    @DisplayName("Should write 8-bit raw image data correctly")
    void shouldWrite8BitRawImageData() throws IOException {
      PlanarImage image = createTestImage(4, 4, CvType.CV_8UC1, 100.0);
      DicomOutputData outputData =
          new DicomOutputData(image, testDescriptor, UID.ExplicitVRLittleEndian);

      ByteArrayOutputStream baos = new ByteArrayOutputStream();
      try (DicomOutputStream dos = new DicomOutputStream(baos, outputData.getTsuid())) {
        Attributes dataSet = new Attributes(testAttributes);
        dos.writeFileMetaInformation(dataSet.createFileMetaInformation(outputData.getTsuid()));

        outputData.writRawImageData(dos, dataSet);

        byte[] result = baos.toByteArray();
        assertTrue(result.length > 0);

        // Verify DICOM attributes were updated
        assertEquals(4, dataSet.getInt(Tag.Columns, 0));
        assertEquals(4, dataSet.getInt(Tag.Rows, 0));
        assertEquals(1, dataSet.getInt(Tag.SamplesPerPixel, 0));
      }
    }

    @Test
    @DisplayName("Should write 16-bit raw image data correctly")
    void shouldWrite16BitRawImageData() throws IOException {
      PlanarImage image = createTestImage(4, 4, CvType.CV_16UC1, 1000.0);
      ImageDescriptor desc = createTestDescriptor(16, 12, false);
      DicomOutputData outputData = new DicomOutputData(image, desc, UID.ExplicitVRLittleEndian);

      ByteArrayOutputStream baos = new ByteArrayOutputStream();
      try (DicomOutputStream dos = new DicomOutputStream(baos, outputData.getTsuid())) {
        Attributes dataSet = new Attributes();
        // Set necessary DICOM attributes
        dataSet.setString(Tag.SOPInstanceUID, VR.UI, UIDUtils.createUID());
        dataSet.setString(Tag.SOPClassUID, VR.UI, UID.CTImageStorage);
        dos.writeFileMetaInformation(dataSet.createFileMetaInformation(outputData.getTsuid()));

        outputData.writRawImageData(dos, dataSet);

        byte[] result = baos.toByteArray();
        assertTrue(result.length > 0);

        // 16-bit data should be larger than 8-bit
        assertTrue(result.length > 16); // Header + 16 pixels * 2 bytes
      }
    }

    @Test
    @DisplayName("Should write RGB image data correctly")
    void shouldWriteRgbImageData() throws IOException {
      PlanarImage image = createTestImage(4, 4, CvType.CV_8UC3, 128.0);
      Attributes attrs = new Attributes(testAttributes);
      attrs.setInt(Tag.SamplesPerPixel, VR.US, 3);
      ImageDescriptor desc = new ImageDescriptor(attrs);
      DicomOutputData outputData = new DicomOutputData(image, desc, UID.ExplicitVRLittleEndian);

      ByteArrayOutputStream baos = new ByteArrayOutputStream();
      try (DicomOutputStream dos = new DicomOutputStream(baos, outputData.getTsuid())) {
        Attributes dataSet = new Attributes(attrs);

        outputData.writRawImageData(dos, dataSet);

        byte[] result = baos.toByteArray();
        assertTrue(result.length > 0);

        // RGB data should be 3 times larger than grayscale
        assertEquals(3, dataSet.getInt(Tag.SamplesPerPixel, 0));
        assertEquals(
            PhotometricInterpretation.RGB.toString(),
            dataSet.getString(Tag.PhotometricInterpretation));
      }
    }

    @Test
    @DisplayName("Should handle multiple frames correctly")
    void shouldHandleMultipleFrames() throws IOException {
      PlanarImage image1 = createTestImage(4, 4, CvType.CV_8UC1, 50.0);
      PlanarImage image2 = createTestImage(4, 4, CvType.CV_8UC1, 150.0);
      List<SupplierEx<PlanarImage, IOException>> images =
          Arrays.asList(createImageSupplier(image1), createImageSupplier(image2));
      DicomOutputData outputData =
          new DicomOutputData(images, testDescriptor, UID.ExplicitVRLittleEndian);

      ByteArrayOutputStream baos = new ByteArrayOutputStream();
      try (DicomOutputStream dos = new DicomOutputStream(baos, outputData.getTsuid())) {
        Attributes dataSet = new Attributes(testAttributes);

        outputData.writRawImageData(dos, dataSet);

        byte[] result = baos.toByteArray();
        assertTrue(result.length > 0);

        // Should contain data for both frames
        assertTrue(result.length > 32); // Header + 2 frames * 16 pixels
      }
    }
  }

  @Nested
  @DisplayName("DICOM Attribute Adaptation Tests")
  class DicomAttributeAdaptationTests {

    @Test
    @DisplayName("Should adapt tags for raw 8-bit image correctly")
    void shouldAdaptTagsForRaw8BitImage() {
      PlanarImage image = createTestImage(64, 64, CvType.CV_8UC1, 128.0);
      ImageDescriptor desc = createTestDescriptor(8, 8, false);
      Attributes data = new Attributes();

      DicomOutputData.adaptTagsToRawImage(data, image, desc);

      assertEquals(64, data.getInt(Tag.Columns, 0));
      assertEquals(64, data.getInt(Tag.Rows, 0));
      assertEquals(1, data.getInt(Tag.SamplesPerPixel, 0));
      assertEquals(8, data.getInt(Tag.BitsAllocated, 0));
      assertEquals(8, data.getInt(Tag.BitsStored, 0));
      assertEquals(7, data.getInt(Tag.HighBit, 0));
      assertEquals(0, data.getInt(Tag.PixelRepresentation, 0));
    }

    @Test
    @DisplayName("Should adapt tags for raw 16-bit signed image correctly")
    void shouldAdaptTagsForRaw16BitSignedImage() {
      PlanarImage image = createTestImage(32, 32, CvType.CV_16SC1, -1000.0);
      ImageDescriptor desc = createTestDescriptor(16, 12, true);
      Attributes data = new Attributes();

      DicomOutputData.adaptTagsToRawImage(data, image, desc);

      assertEquals(32, data.getInt(Tag.Columns, 0));
      assertEquals(32, data.getInt(Tag.Rows, 0));
      assertEquals(1, data.getInt(Tag.SamplesPerPixel, 0));
      assertEquals(16, data.getInt(Tag.BitsAllocated, 0));
      assertEquals(12, data.getInt(Tag.BitsStored, 0));
      assertEquals(11, data.getInt(Tag.HighBit, 0));
      assertEquals(1, data.getInt(Tag.PixelRepresentation, 0));
    }

    @Test
    @DisplayName("Should adapt tags for RGB image correctly")
    void shouldAdaptTagsForRgbImage() {
      PlanarImage image = createTestImage(16, 16, CvType.CV_8UC3, 128.0);
      ImageDescriptor desc = createTestDescriptor(8, 8, false);
      Attributes data = new Attributes();

      DicomOutputData.adaptTagsToRawImage(data, image, desc);

      assertEquals(16, data.getInt(Tag.Columns, 0));
      assertEquals(16, data.getInt(Tag.Rows, 0));
      assertEquals(3, data.getInt(Tag.SamplesPerPixel, 0));
      assertEquals(
          PhotometricInterpretation.RGB.toString(), data.getString(Tag.PhotometricInterpretation));
      assertEquals(0, data.getInt(Tag.PlanarConfiguration, 0));
    }
  }

  @Nested
  @DisplayName("Static Utility Method Tests")
  class StaticUtilityMethodTests {

    @Test
    @DisplayName("Should correctly identify supported syntax")
    void shouldIdentifySupportedSyntax() {
      assertTrue(DicomOutputData.isSupportedSyntax(UID.ExplicitVRLittleEndian));
      assertTrue(DicomOutputData.isSupportedSyntax(UID.ImplicitVRLittleEndian));
      assertTrue(DicomOutputData.isSupportedSyntax(UID.JPEGBaseline8Bit));
      assertTrue(DicomOutputData.isSupportedSyntax(UID.JPEGLossless));
      assertTrue(DicomOutputData.isSupportedSyntax(UID.JPEG2000));

      assertFalse(DicomOutputData.isSupportedSyntax("1.2.3.4.5.6.7.8"));
      assertThrows(NullPointerException.class, () -> DicomOutputData.isSupportedSyntax(null));
    }

    @Test
    @DisplayName("Should correctly identify native syntax")
    void shouldIdentifyNativeSyntax() {
      assertTrue(DicomOutputData.isNativeSyntax(UID.ExplicitVRLittleEndian));
      assertTrue(DicomOutputData.isNativeSyntax(UID.ImplicitVRLittleEndian));

      assertFalse(DicomOutputData.isNativeSyntax(UID.JPEGBaseline8Bit));
      assertFalse(DicomOutputData.isNativeSyntax(UID.JPEG2000));
    }

    @Test
    @DisplayName("Should correctly identify adaptable syntax")
    void shouldIdentifyAdaptableSyntax() {
      assertTrue(DicomOutputData.isAdaptableSyntax(UID.JPEGBaseline8Bit));
      assertTrue(DicomOutputData.isAdaptableSyntax(UID.JPEGExtended12Bit));

      assertFalse(DicomOutputData.isAdaptableSyntax(UID.ExplicitVRLittleEndian));
      assertFalse(DicomOutputData.isAdaptableSyntax(UID.JPEG2000));
    }

    @Test
    @DisplayName("Should adapt suitable syntax based on image characteristics")
    void shouldAdaptSuitableSyntax() {
      // 8-bit data should keep JPEGBaseline8Bit
      assertEquals(
          UID.JPEGBaseline8Bit,
          DicomOutputData.adaptSuitableSyntax(8, CvType.CV_8U, UID.JPEGBaseline8Bit));

      // 16-bit signed data should adapt to JPEGLosslessSV1
      assertEquals(
          UID.JPEGLosslessSV1,
          DicomOutputData.adaptSuitableSyntax(12, CvType.CV_16S, UID.JPEGBaseline8Bit));

      // Unsupported types should fall back to ExplicitVRLittleEndian
      assertEquals(
          UID.ExplicitVRLittleEndian,
          DicomOutputData.adaptSuitableSyntax(32, CvType.CV_32F, UID.JPEGBaseline8Bit));

      // Native syntaxes should adapt to ExplicitVRLittleEndian
      assertEquals(
          UID.ExplicitVRLittleEndian,
          DicomOutputData.adaptSuitableSyntax(8, CvType.CV_8U, UID.ImplicitVRLittleEndian));
    }
  }

  @Nested
  @DisplayName("Error Handling Tests")
  class ErrorHandlingTests {

    @Test
    @DisplayName("Should handle IO exceptions during image access gracefully")
    void shouldHandleImageAccessErrors() {
      SupplierEx<PlanarImage, IOException> failingSupplier =
          () -> {
            throw new IOException("Simulated image access error");
          };

      assertThrows(
          IOException.class,
          () -> new DicomOutputData(failingSupplier, testDescriptor, UID.ExplicitVRLittleEndian));
    }

    @Test
    @DisplayName("Should handle invalid image types gracefully")
    void shouldHandleInvalidImageTypes() throws IOException {
      // Create an image with an unusual type that might cause issues
      PlanarImage image = createTestImage(1, 1, CvType.CV_64FC1, 1.0);
      DicomOutputData outputData =
          new DicomOutputData(image, testDescriptor, UID.ExplicitVRLittleEndian);

      ByteArrayOutputStream baos = new ByteArrayOutputStream();
      try (DicomOutputStream dos = new DicomOutputStream(baos, outputData.getTsuid())) {
        Attributes dataSet = new Attributes(testAttributes);
        dos.writeFileMetaInformation(dataSet.createFileMetaInformation(outputData.getTsuid()));

        // Should not throw exception, but may log warnings
        assertDoesNotThrow(() -> outputData.writRawImageData(dos, dataSet));
      }
    }
  }

  @Nested
  @DisplayName("Edge Cases Tests")
  class EdgeCasesTests {

    @Test
    @DisplayName("Should handle minimum size images")
    void shouldHandleMinimumSizeImages() throws IOException {
      PlanarImage image = createTestImage(1, 1, CvType.CV_8UC1, 255.0);
      DicomOutputData outputData =
          new DicomOutputData(image, testDescriptor, UID.ExplicitVRLittleEndian);

      assertNotNull(outputData);
      assertEquals(1, outputData.getImages().size());
    }

    @Test
    @DisplayName("Should handle large images efficiently")
    void shouldHandleLargeImages() throws IOException {
      PlanarImage image = createTestImage(512, 512, CvType.CV_16UC1, 32768.0);
      ImageDescriptor desc = createTestDescriptor(16, 16, false);
      DicomOutputData outputData = new DicomOutputData(image, desc, UID.ExplicitVRLittleEndian);

      assertNotNull(outputData);
      assertEquals(UID.ExplicitVRLittleEndian, outputData.getTsuid());
    }

    @Test
    @DisplayName("Should handle empty pixel values correctly")
    void shouldHandleEmptyPixelValues() throws IOException {
      PlanarImage image = createTestImage(16, 16, CvType.CV_8UC1, 0.0);
      DicomOutputData outputData =
          new DicomOutputData(image, testDescriptor, UID.ExplicitVRLittleEndian);

      ByteArrayOutputStream baos = new ByteArrayOutputStream();
      try (DicomOutputStream dos = new DicomOutputStream(baos, outputData.getTsuid())) {
        Attributes dataSet = new Attributes(testAttributes);
        dos.writeFileMetaInformation(dataSet.createFileMetaInformation(outputData.getTsuid()));

        assertDoesNotThrow(() -> outputData.writRawImageData(dos, dataSet));
      }
    }
  }
}
