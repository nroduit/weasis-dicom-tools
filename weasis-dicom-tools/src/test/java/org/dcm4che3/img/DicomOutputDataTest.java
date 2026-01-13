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
import java.util.stream.Stream;
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
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.Scalar;
import org.weasis.opencv.data.ImageCV;
import org.weasis.opencv.data.PlanarImage;
import org.weasis.opencv.natives.NativeLibrary;

/** Test class for {@link DicomOutputData} functionality. */
@DisplayNameGeneration(ReplaceUnderscores.class)
class DicomOutputDataTest {

  private ImageDescriptor testDescriptor;
  private Attributes testAttributes;

  @BeforeAll
  static void loadNativeLib() {
    NativeLibrary.loadLibraryFromLibraryName();
  }

  @BeforeEach
  void setUp() {
    testAttributes = createBasicDicomAttributes();
    testDescriptor = new ImageDescriptor(testAttributes);
  }

  private static Attributes createBasicDicomAttributes() {
    var attrs = new Attributes();
    attrs.setString(Tag.SOPInstanceUID, VR.UI, UIDUtils.createUID());
    attrs.setString(Tag.SOPClassUID, VR.UI, UID.CTImageStorage);
    attrs.setInt(Tag.Rows, VR.US, 100);
    attrs.setInt(Tag.Columns, VR.US, 100);
    attrs.setInt(Tag.SamplesPerPixel, VR.US, 1);
    attrs.setInt(Tag.BitsAllocated, VR.US, 8);
    attrs.setInt(Tag.BitsStored, VR.US, 8);
    attrs.setInt(Tag.HighBit, VR.US, 7);
    attrs.setInt(Tag.PixelRepresentation, VR.US, 0);
    attrs.setString(
        Tag.PhotometricInterpretation, VR.CS, PhotometricInterpretation.MONOCHROME2.toString());
    return attrs;
  }

  private static PlanarImage createTestImage(int width, int height, int type, double fillValue) {
    var mat = new Mat(height, width, type);
    mat.setTo(Scalar.all(fillValue));
    return ImageCV.fromMat(mat);
  }

  private static ImageDescriptor createTestDescriptor(
      int bitsAllocated, int bitsStored, boolean signed) {
    var attrs = createBasicDicomAttributes();
    attrs.setInt(Tag.Rows, VR.US, 64);
    attrs.setInt(Tag.Columns, VR.US, 64);
    attrs.setInt(Tag.BitsAllocated, VR.US, bitsAllocated);
    attrs.setInt(Tag.BitsStored, VR.US, bitsStored);
    attrs.setInt(Tag.HighBit, VR.US, bitsStored - 1);
    attrs.setInt(Tag.PixelRepresentation, VR.US, signed ? 1 : 0);
    return new ImageDescriptor(attrs);
  }

  private static SupplierEx<PlanarImage, IOException> createImageSupplier(PlanarImage image) {
    return () -> image;
  }

  @Nested
  class Constructor_Tests {

    @Test
    void should_create_with_single_image() throws IOException {
      var image = createTestImage(100, 100, CvType.CV_8UC1, 128.0);

      var outputData = new DicomOutputData(image, testDescriptor, UID.ExplicitVRLittleEndian);

      assertNotNull(outputData);
      assertEquals(UID.ExplicitVRLittleEndian, outputData.getTsuid());
      assertEquals(1, outputData.getImages().size());
      assertEquals(image, outputData.getFirstImage().get());
    }

    @Test
    void should_create_with_image_supplier() throws IOException {
      var image = createTestImage(100, 100, CvType.CV_8UC1, 64.0);
      var supplier = createImageSupplier(image);

      var outputData = new DicomOutputData(supplier, testDescriptor, UID.ExplicitVRLittleEndian);

      assertNotNull(outputData);
      assertEquals(1, outputData.getImages().size());
      assertEquals(image, outputData.getFirstImage().get());
    }

    @Test
    void should_create_with_multiple_images() throws IOException {
      var image1 = createTestImage(100, 100, CvType.CV_8UC1, 50.0);
      var image2 = createTestImage(100, 100, CvType.CV_8UC1, 150.0);
      var images = Arrays.asList(createImageSupplier(image1), createImageSupplier(image2));

      var outputData = new DicomOutputData(images, testDescriptor, UID.ExplicitVRLittleEndian);

      assertNotNull(outputData);
      assertEquals(2, outputData.getImages().size());
      assertEquals(image1, outputData.getFirstImage().get());
    }

    @ParameterizedTest
    @ValueSource(
        strings = {
          UID.ExplicitVRLittleEndian,
          UID.JPEGBaseline8Bit,
          UID.JPEG2000,
          UID.JPEGLossless
        })
    void should_accept_supported_transfer_syntax(String tsuid) {
      try (var image = createTestImage(64, 64, CvType.CV_8UC1, 100.0)) {

        assertDoesNotThrow(() -> new DicomOutputData(image, testDescriptor, tsuid));
      }
    }

    @Test
    void should_throw_for_null_images() {
      assertThrows(
          NullPointerException.class,
          () ->
              new DicomOutputData(
                  (List<SupplierEx<PlanarImage, IOException>>) null,
                  testDescriptor,
                  UID.ExplicitVRLittleEndian));
    }

    @Test
    void should_throw_for_empty_image_list() {
      List<SupplierEx<PlanarImage, IOException>> emptyList = Collections.emptyList();

      assertThrows(
          IllegalStateException.class,
          () -> new DicomOutputData(emptyList, testDescriptor, UID.ExplicitVRLittleEndian));
    }

    @Test
    void should_throw_for_null_descriptor() {
      try (var image = createTestImage(100, 100, CvType.CV_8UC1, 128.0)) {

        assertThrows(
            NullPointerException.class,
            () -> new DicomOutputData(image, null, UID.ExplicitVRLittleEndian));
      }
    }

    @Test
    void should_throw_for_null_tsuid() {
      try (var image = createTestImage(100, 100, CvType.CV_8UC1, 128.0)) {

        assertThrows(
            NullPointerException.class, () -> new DicomOutputData(image, testDescriptor, null));
      }
    }

    @Test
    void should_throw_for_unsupported_syntax() {
      try (var image = createTestImage(100, 100, CvType.CV_8UC1, 128.0)) {
        var unsupportedSyntax = "1.2.3.4.5.6.7.8";

        assertThrows(
            IllegalStateException.class,
            () -> new DicomOutputData(image, testDescriptor, unsupportedSyntax));
      }
    }
  }

  @Nested
  class Transfer_Syntax_Adaptation_Tests {

    @ParameterizedTest
    @CsvSource({
      "8, CV_8U, " + UID.JPEGBaseline8Bit + ", " + UID.JPEGBaseline8Bit,
      "12, CV_16U, " + UID.JPEGBaseline8Bit + ", " + UID.JPEGLosslessSV1,
      "16, CV_16S, " + UID.JPEGBaseline8Bit + ", " + UID.JPEGLosslessSV1,
      "32, CV_32F, " + UID.JPEGBaseline8Bit + ", " + UID.ExplicitVRLittleEndian
    })
    void should_adapt_transfer_syntax_based_on_image_type(
        int bitsStored, String cvTypeStr, String requestedSyntax, String expectedSyntax)
        throws IOException {
      int cvType = getCvTypeFromString(cvTypeStr);
      var image = createTestImage(64, 64, cvType, getDefaultValue(cvType));
      var desc =
          createTestDescriptor(getDefaultBitsAllocated(cvType), bitsStored, isSigned(cvType));

      var outputData = new DicomOutputData(image, desc, requestedSyntax);

      assertEquals(expectedSyntax, outputData.getTsuid());
    }

    private int getCvTypeFromString(String cvTypeStr) {
      return switch (cvTypeStr) {
        case "CV_8U" -> CvType.CV_8UC1;
        case "CV_16U" -> CvType.CV_16UC1;
        case "CV_16S" -> CvType.CV_16SC1;
        case "CV_32F" -> CvType.CV_32FC1;
        default -> throw new IllegalArgumentException("Unknown CV type: " + cvTypeStr);
      };
    }

    private double getDefaultValue(int cvType) {
      return switch (CvType.depth(cvType)) {
        case CvType.CV_8U, CvType.CV_8S -> 128.0;
        case CvType.CV_16U, CvType.CV_16S -> 1000.0;
        case CvType.CV_32F -> 1000.0f;
        default -> 0.0;
      };
    }

    private int getDefaultBitsAllocated(int cvType) {
      return switch (CvType.depth(cvType)) {
        case CvType.CV_16U, CvType.CV_16S -> 16;
        case CvType.CV_32F -> 32;
        default -> 8;
      };
    }

    private boolean isSigned(int cvType) {
      return CvType.depth(cvType) == CvType.CV_16S;
    }

    @Test
    void should_handle_jpeg2000_syntax_correctly() throws IOException {
      var image = createTestImage(64, 64, CvType.CV_16UC1, 1000.0);
      var desc = createTestDescriptor(16, 12, false);

      var outputData = new DicomOutputData(image, desc, UID.JPEG2000);

      assertEquals(UID.JPEG2000, outputData.getTsuid());
    }
  }

  @Nested
  class Raw_Image_Data_Tests {

    @ParameterizedTest
    @CsvSource({"4, 4, CV_8UC1, 100.0", "8, 8, CV_16UC1, 1000.0", "16, 16, CV_32FC1, 500.0"})
    void should_write_raw_image_data_for_different_types(
        int width, int height, String cvTypeStr, double fillValue) throws IOException {
      int cvType = getCvTypeFromString(cvTypeStr);
      var image = createTestImage(width, height, cvType, fillValue);
      var desc = createTestDescriptorForType(cvType);
      var outputData = new DicomOutputData(image, desc, UID.ExplicitVRLittleEndian);

      var result = writeRawImageDataToByteArray(outputData);

      assertTrue(result.length > 0);
      verifyImageDimensions(outputData, width, height);
    }

    private ImageDescriptor createTestDescriptorForType(int cvType) {
      return switch (CvType.depth(cvType)) {
        case CvType.CV_8U, CvType.CV_8S -> createTestDescriptor(8, 8, false);
        case CvType.CV_16U -> createTestDescriptor(16, 12, false);
        case CvType.CV_16S -> createTestDescriptor(16, 12, true);
        case CvType.CV_32F -> createTestDescriptor(32, 32, false);
        default -> testDescriptor;
      };
    }

    private int getCvTypeFromString(String cvTypeStr) {
      return switch (cvTypeStr) {
        case "CV_8UC1" -> CvType.CV_8UC1;
        case "CV_16UC1" -> CvType.CV_16UC1;
        case "CV_32FC1" -> CvType.CV_32FC1;
        default -> throw new IllegalArgumentException("Unknown CV type: " + cvTypeStr);
      };
    }

    private byte[] writeRawImageDataToByteArray(DicomOutputData outputData) throws IOException {
      var baos = new ByteArrayOutputStream();
      try (var dos = new DicomOutputStream(baos, outputData.getTsuid())) {
        var dataSet = new Attributes(testAttributes);
        dos.writeFileMetaInformation(dataSet.createFileMetaInformation(outputData.getTsuid()));
        outputData.writeRawImageData(dos, dataSet);
        return baos.toByteArray();
      }
    }

    private void verifyImageDimensions(
        DicomOutputData outputData, int expectedWidth, int expectedHeight) throws IOException {
      var baos = new ByteArrayOutputStream();
      try (var dos = new DicomOutputStream(baos, outputData.getTsuid())) {
        var dataSet = new Attributes(testAttributes);
        outputData.writeRawImageData(dos, dataSet);

        assertEquals(expectedWidth, dataSet.getInt(Tag.Columns, 0));
        assertEquals(expectedHeight, dataSet.getInt(Tag.Rows, 0));
      }
    }

    @Test
    void should_write_rgb_image_data_correctly() throws IOException {
      var image = createTestImage(4, 4, CvType.CV_8UC3, 128.0);
      var attrs = new Attributes(testAttributes);
      attrs.setInt(Tag.SamplesPerPixel, VR.US, 3);
      var desc = new ImageDescriptor(attrs);
      var outputData = new DicomOutputData(image, desc, UID.ExplicitVRLittleEndian);

      var result = writeRawImageDataToByteArray(outputData);

      assertTrue(result.length > 0);

      // Verify RGB attributes
      var baos = new ByteArrayOutputStream();
      try (var dos = new DicomOutputStream(baos, outputData.getTsuid())) {
        var dataSet = new Attributes(attrs);
        outputData.writeRawImageData(dos, dataSet);

        assertEquals(3, dataSet.getInt(Tag.SamplesPerPixel, 0));
        assertEquals(
            PhotometricInterpretation.RGB.toString(),
            dataSet.getString(Tag.PhotometricInterpretation));
        assertEquals(0, dataSet.getInt(Tag.PlanarConfiguration, 0));
      }
    }

    @Test
    void should_handle_multiple_frames_correctly() throws IOException {
      var image1 = createTestImage(4, 4, CvType.CV_8UC1, 50.0);
      var image2 = createTestImage(4, 4, CvType.CV_8UC1, 150.0);
      var images = Arrays.asList(createImageSupplier(image1), createImageSupplier(image2));
      var outputData = new DicomOutputData(images, testDescriptor, UID.ExplicitVRLittleEndian);

      var result = writeRawImageDataToByteArray(outputData);

      assertTrue(result.length > 0);
      // Should contain data for both frames (header + 2 * 16 pixels)
      assertTrue(result.length > 32);
    }
  }

  @Nested
  class DICOM_Attribute_Adaptation_Tests {

    @ParameterizedTest
    @MethodSource("imageAttributeTestData")
    void should_adapt_tags_for_raw_images(ImageTestData testData) {
      var image =
          createTestImage(testData.width, testData.height, testData.cvType, testData.fillValue);
      var desc = createTestDescriptor(testData.bitsAllocated, testData.bitsStored, testData.signed);
      var data = new Attributes();

      DicomOutputData.adaptTagsToRawImage(data, image, desc);

      assertEquals(testData.width, data.getInt(Tag.Columns, 0));
      assertEquals(testData.height, data.getInt(Tag.Rows, 0));
      assertEquals(testData.expectedChannels, data.getInt(Tag.SamplesPerPixel, 0));
      assertEquals(testData.bitsAllocated, data.getInt(Tag.BitsAllocated, 0));
      assertEquals(testData.bitsStored, data.getInt(Tag.BitsStored, 0));
      assertEquals(testData.bitsStored - 1, data.getInt(Tag.HighBit, 0));
      assertEquals(testData.signed ? 1 : 0, data.getInt(Tag.PixelRepresentation, 0));
    }

    static Stream<Arguments> imageAttributeTestData() {
      return Stream.of(
          Arguments.of(new ImageTestData(64, 64, CvType.CV_8UC1, 128.0, 8, 8, false, 1)),
          Arguments.of(new ImageTestData(32, 32, CvType.CV_16SC1, -1000.0, 16, 12, true, 1)),
          Arguments.of(new ImageTestData(16, 16, CvType.CV_8UC3, 128.0, 8, 8, false, 3)));
    }

    record ImageTestData(
        int width,
        int height,
        int cvType,
        double fillValue,
        int bitsAllocated,
        int bitsStored,
        boolean signed,
        int expectedChannels) {}

    @Test
    void should_set_rgb_photometric_interpretation_for_multi_channel_images() {
      var image = createTestImage(16, 16, CvType.CV_8UC3, 128.0);
      var desc = createTestDescriptor(8, 8, false);
      var data = new Attributes();

      DicomOutputData.adaptTagsToRawImage(data, image, desc);

      assertEquals(
          PhotometricInterpretation.RGB.toString(), data.getString(Tag.PhotometricInterpretation));
      assertEquals(0, data.getInt(Tag.PlanarConfiguration, 0));
    }
  }

  @Nested
  class Static_Utility_Method_Tests {

    @ParameterizedTest
    @ValueSource(
        strings = {
          UID.ExplicitVRLittleEndian,
          UID.ImplicitVRLittleEndian,
          UID.JPEGBaseline8Bit,
          UID.JPEGLossless,
          UID.JPEG2000,
          UID.JPEGLSLossless
        })
    void should_identify_supported_syntax(String syntax) {
      assertTrue(DicomOutputData.isSupportedSyntax(syntax));
    }

    @ParameterizedTest
    @ValueSource(strings = {"1.2.3.4.5.6.7.8", "invalid.uid", ""})
    void should_identify_unsupported_syntax(String syntax) {
      assertFalse(DicomOutputData.isSupportedSyntax(syntax));
    }

    @Test
    void should_throw_for_null_syntax_check() {
      assertThrows(NullPointerException.class, () -> DicomOutputData.isSupportedSyntax(null));
    }

    @ParameterizedTest
    @ValueSource(strings = {UID.ExplicitVRLittleEndian, UID.ImplicitVRLittleEndian})
    void should_identify_native_syntax(String syntax) {
      assertTrue(DicomOutputData.isNativeSyntax(syntax));
    }

    @ParameterizedTest
    @ValueSource(strings = {UID.JPEGBaseline8Bit, UID.JPEG2000})
    void should_identify_non_native_syntax(String syntax) {
      assertFalse(DicomOutputData.isNativeSyntax(syntax));
    }

    @ParameterizedTest
    @ValueSource(
        strings = {
          UID.JPEGBaseline8Bit,
          UID.JPEGExtended12Bit,
          UID.JPEGSpectralSelectionNonHierarchical68
        })
    void should_identify_adaptable_syntax(String syntax) {
      assertTrue(DicomOutputData.isAdaptableSyntax(syntax));
    }

    @ParameterizedTest
    @ValueSource(strings = {UID.ExplicitVRLittleEndian, UID.JPEG2000})
    void should_identify_non_adaptable_syntax(String syntax) {
      assertFalse(DicomOutputData.isAdaptableSyntax(syntax));
    }

    @ParameterizedTest
    @CsvSource({
      "8, CV_8U, " + UID.JPEGBaseline8Bit + ", " + UID.JPEGBaseline8Bit,
      "12, CV_16S, " + UID.JPEGBaseline8Bit + ", " + UID.JPEGLosslessSV1,
      "32, CV_32F, " + UID.JPEGBaseline8Bit + ", " + UID.ExplicitVRLittleEndian,
      "8, CV_8U, " + UID.ImplicitVRLittleEndian + ", " + UID.ExplicitVRLittleEndian
    })
    void should_adapt_suitable_syntax_based_on_characteristics(
        int bitStored, String cvTypeStr, String requestedSyntax, String expectedSyntax) {
      int cvType = getCvTypeValue(cvTypeStr);

      var result = DicomOutputData.adaptSuitableSyntax(bitStored, cvType, requestedSyntax);

      assertEquals(expectedSyntax, result);
    }

    private int getCvTypeValue(String cvTypeStr) {
      return switch (cvTypeStr) {
        case "CV_8U" -> CvType.CV_8U;
        case "CV_16S" -> CvType.CV_16S;
        case "CV_32F" -> CvType.CV_32F;
        default -> throw new IllegalArgumentException("Unknown type: " + cvTypeStr);
      };
    }
  }

  @Nested
  class Error_Handling_Tests {

    @Test
    void should_handle_io_exceptions_during_image_access() {
      SupplierEx<PlanarImage, IOException> failingSupplier =
          () -> {
            throw new IOException("Simulated image access error");
          };

      assertThrows(
          IOException.class,
          () -> new DicomOutputData(failingSupplier, testDescriptor, UID.ExplicitVRLittleEndian));
    }

    @Test
    void should_handle_unusual_image_types_gracefully() throws IOException {
      var image = createTestImage(1, 1, CvType.CV_64FC1, 1.0);
      var outputData = new DicomOutputData(image, testDescriptor, UID.ExplicitVRLittleEndian);

      var baos = new ByteArrayOutputStream();
      try (var dos = new DicomOutputStream(baos, outputData.getTsuid())) {
        var dataSet = new Attributes(testAttributes);
        dos.writeFileMetaInformation(dataSet.createFileMetaInformation(outputData.getTsuid()));

        assertDoesNotThrow(() -> outputData.writeRawImageData(dos, dataSet));
      }
    }
  }

  @Nested
  class Edge_Cases_Tests {

    @ParameterizedTest
    @CsvSource({"1, 1, CV_8UC1, 255.0", "1, 1, CV_16UC1, 65535.0", "1, 1, CV_32FC1, 1.0"})
    void should_handle_minimum_size_images(
        int width, int height, String cvTypeStr, double fillValue) throws IOException {
      int cvType = getCvTypeFromString(cvTypeStr);
      var image = createTestImage(width, height, cvType, fillValue);

      var outputData = new DicomOutputData(image, testDescriptor, UID.ExplicitVRLittleEndian);

      assertNotNull(outputData);
      assertEquals(1, outputData.getImages().size());
    }

    private int getCvTypeFromString(String cvTypeStr) {
      return switch (cvTypeStr) {
        case "CV_8UC1" -> CvType.CV_8UC1;
        case "CV_16UC1" -> CvType.CV_16UC1;
        case "CV_32FC1" -> CvType.CV_32FC1;
        default -> throw new IllegalArgumentException("Unknown type: " + cvTypeStr);
      };
    }

    @Test
    void should_handle_large_images_efficiently() throws IOException {
      var image = createTestImage(512, 512, CvType.CV_16UC1, 32768.0);
      var desc = createTestDescriptor(16, 16, false);

      var outputData = new DicomOutputData(image, desc, UID.ExplicitVRLittleEndian);

      assertNotNull(outputData);
      assertEquals(UID.ExplicitVRLittleEndian, outputData.getTsuid());
    }

    @Test
    void should_handle_zero_pixel_values() throws IOException {
      var image = createTestImage(16, 16, CvType.CV_8UC1, 0.0);
      var outputData = new DicomOutputData(image, testDescriptor, UID.ExplicitVRLittleEndian);

      var baos = new ByteArrayOutputStream();
      try (var dos = new DicomOutputStream(baos, outputData.getTsuid())) {
        var dataSet = new Attributes(testAttributes);
        dos.writeFileMetaInformation(dataSet.createFileMetaInformation(outputData.getTsuid()));

        assertDoesNotThrow(() -> outputData.writeRawImageData(dos, dataSet));
      }
    }

    @Test
    void should_handle_extreme_pixel_values() throws IOException {
      var maxValueImage = createTestImage(8, 8, CvType.CV_8UC1, 255.0);
      var outputData =
          new DicomOutputData(maxValueImage, testDescriptor, UID.ExplicitVRLittleEndian);

      assertDoesNotThrow(
          () -> {
            var baos = new ByteArrayOutputStream();
            try (var dos = new DicomOutputStream(baos, outputData.getTsuid())) {
              var dataSet = new Attributes(testAttributes);
              dos.writeFileMetaInformation(
                  dataSet.createFileMetaInformation(outputData.getTsuid()));
              outputData.writeRawImageData(dos, dataSet);
            }
          });
    }
  }
}
