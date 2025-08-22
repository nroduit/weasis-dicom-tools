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
import static org.mockito.Mockito.*;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Optional;
import java.util.stream.Stream;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.UID;
import org.dcm4che3.data.VR;
import org.dcm4che3.io.DicomInputStream;
import org.dcm4che3.io.DicomOutputStream;
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
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@DisplayNameGeneration(ReplaceUnderscores.class)
class DicomMetaDataTest {

  // Standard DICOM Transfer Syntax UIDs
  private static final String IMPLICIT_VR_LITTLE_ENDIAN = UID.ImplicitVRLittleEndian;
  private static final String EXPLICIT_VR_LITTLE_ENDIAN = UID.ExplicitVRLittleEndian;
  private static final String EXPLICIT_VR_BIG_ENDIAN = UID.ExplicitVRBigEndian;
  private static final String JPEG_BASELINE = UID.JPEGBaseline8Bit;
  private static final String JPEG_LOSSLESS = UID.JPEGLossless;

  // Standard DICOM SOP Class UIDs
  private static final String CT_IMAGE_STORAGE = UID.CTImageStorage;
  private static final String MR_IMAGE_STORAGE = UID.MRImageStorage;
  private static final String US_IMAGE_STORAGE = UID.UltrasoundImageStorage;
  private static final String MEDIA_STORAGE_DIRECTORY = UID.MediaStorageDirectoryStorage;
  private static final String SEGMENTATION_STORAGE = UID.SegmentationStorage;

  // Test instance UIDs
  private static final String TEST_SOP_INSTANCE_UID = "1.2.3.4.5.6.7.8.9.10.11.12.13.14.15";

  private DicomTestDataFactory dataFactory;

  @Mock private DicomInputStream mockDicomInputStream;

  @BeforeEach
  void setUp() throws Exception {
    try (var ignored = MockitoAnnotations.openMocks(this)) {
      dataFactory = new DicomTestDataFactory();
    }
  }

  @Nested
  class Constructor_tests {

    @Test
    void should_create_from_dicom_input_stream_with_file_meta_information() throws IOException {
      var fileMetaInfo =
          dataFactory.createFileMetaInformation(CT_IMAGE_STORAGE, EXPLICIT_VR_LITTLE_ENDIAN);
      var dataset = dataFactory.createBasicImageDataset("CT", 512, 512);
      var dicomData = dataFactory.createCompleteDicomFile(fileMetaInfo, dataset);

      try (var dis = new DicomInputStream(new ByteArrayInputStream(dicomData))) {
        var metadata = new DicomMetaData(dis);

        assertAll(
            () -> assertNotNull(metadata),
            () -> assertTrue(metadata.hasFileMetaInformation()),
            () -> assertNotNull(metadata.getFileMetaInformation()),
            () -> assertNotNull(metadata.getDicomObject()),
            () -> assertNotNull(metadata.getImageDescriptor()),
            () -> assertEquals(EXPLICIT_VR_LITTLE_ENDIAN, metadata.getTransferSyntaxUID()),
            () ->
                assertEquals(Optional.of(CT_IMAGE_STORAGE), metadata.getMediaStorageSOPClassUID()),
            () -> assertFalse(metadata.isVideoTransferSyntaxUID()),
            () -> assertFalse(metadata.isMediaStorageDirectory()),
            () -> assertFalse(metadata.isSegmentationStorage()));
      }
    }

    @Test
    void should_create_from_dicom_input_stream_without_file_meta_information() throws IOException {
      var dataset = dataFactory.createBasicImageDataset("MR", 256, 256);
      var dicomData = dataFactory.createDatasetOnlyDicomFile(dataset, IMPLICIT_VR_LITTLE_ENDIAN);

      try (var dis = new DicomInputStream(new ByteArrayInputStream(dicomData))) {
        var metadata = new DicomMetaData(dis);

        assertAll(
            () -> assertNotNull(metadata),
            () -> assertFalse(metadata.hasFileMetaInformation()),
            () -> assertNull(metadata.getFileMetaInformation()),
            () -> assertNotNull(metadata.getDicomObject()),
            () -> assertNotNull(metadata.getImageDescriptor()),
            () -> assertEquals(IMPLICIT_VR_LITTLE_ENDIAN, metadata.getTransferSyntaxUID()),
            () -> assertEquals(Optional.empty(), metadata.getMediaStorageSOPClassUID()));
      }
    }

    @Test
    void should_create_from_attributes_and_transfer_syntax() {
      var dataset = dataFactory.createBasicImageDataset("US", 640, 480);

      var metadata = new DicomMetaData(dataset, EXPLICIT_VR_BIG_ENDIAN);

      assertAll(
          () -> assertNotNull(metadata),
          () -> assertFalse(metadata.hasFileMetaInformation()),
          () -> assertNull(metadata.getFileMetaInformation()),
          () -> assertEquals(dataset, metadata.getDicomObject()),
          () -> assertNotNull(metadata.getImageDescriptor()),
          () -> assertEquals(EXPLICIT_VR_BIG_ENDIAN, metadata.getTransferSyntaxUID()),
          () -> assertEquals(Optional.empty(), metadata.getMediaStorageSOPClassUID()));
    }

    @Test
    void should_throw_npe_for_null_dicom_input_stream() {
      assertThrows(NullPointerException.class, () -> new DicomMetaData(null));
    }

    @Test
    void should_throw_npe_for_null_attributes() {
      assertThrows(
          NullPointerException.class, () -> new DicomMetaData(null, IMPLICIT_VR_LITTLE_ENDIAN));
    }

    @Test
    void should_throw_npe_for_null_transfer_syntax() {
      var dataset = dataFactory.createBasicImageDataset("CT", 512, 512);
      assertThrows(NullPointerException.class, () -> new DicomMetaData(dataset, null));
    }

    @Test
    void should_resolve_transfer_syntax_from_file_meta_when_present() throws IOException {
      when(mockDicomInputStream.readFileMetaInformation())
          .thenReturn(dataFactory.createFileMetaInformation(CT_IMAGE_STORAGE, JPEG_BASELINE));
      when(mockDicomInputStream.readDataset())
          .thenReturn(dataFactory.createBasicImageDataset("CT", 512, 512));
      when(mockDicomInputStream.getTransferSyntax()).thenReturn(IMPLICIT_VR_LITTLE_ENDIAN);

      var metadata = new DicomMetaData(mockDicomInputStream);

      assertEquals(JPEG_BASELINE, metadata.getTransferSyntaxUID());
      verify(mockDicomInputStream).getTransferSyntax();
    }

    @Test
    void should_use_stream_transfer_syntax_when_no_file_meta() throws IOException {
      when(mockDicomInputStream.readFileMetaInformation()).thenReturn(null);
      when(mockDicomInputStream.readDataset())
          .thenReturn(dataFactory.createBasicImageDataset("MR", 256, 256));
      when(mockDicomInputStream.getTransferSyntax()).thenReturn(EXPLICIT_VR_LITTLE_ENDIAN);

      var metadata = new DicomMetaData(mockDicomInputStream);

      assertEquals(EXPLICIT_VR_LITTLE_ENDIAN, metadata.getTransferSyntaxUID());
    }
  }

  @Nested
  class Media_storage_sop_class_tests {

    @Test
    void should_return_sop_class_from_file_meta_information() throws IOException {
      var fileMetaInfo =
          dataFactory.createFileMetaInformation(MR_IMAGE_STORAGE, EXPLICIT_VR_LITTLE_ENDIAN);
      var dataset = dataFactory.createBasicImageDataset("MR", 256, 256);
      var dicomData = dataFactory.createCompleteDicomFile(fileMetaInfo, dataset);

      try (var dis = new DicomInputStream(new ByteArrayInputStream(dicomData))) {
        var metadata = new DicomMetaData(dis);
        assertEquals(Optional.of(MR_IMAGE_STORAGE), metadata.getMediaStorageSOPClassUID());
      }
    }

    @Test
    void should_return_empty_when_no_file_meta_information() {
      var dataset = dataFactory.createBasicImageDataset("CT", 512, 512);
      var metadata = new DicomMetaData(dataset, IMPLICIT_VR_LITTLE_ENDIAN);

      assertEquals(Optional.empty(), metadata.getMediaStorageSOPClassUID());
    }

    @Test
    void should_return_empty_when_file_meta_has_no_sop_class() throws IOException {
      var fileMetaInfo = new Attributes();
      fileMetaInfo.setString(Tag.TransferSyntaxUID, VR.UI, EXPLICIT_VR_LITTLE_ENDIAN);

      when(mockDicomInputStream.readFileMetaInformation()).thenReturn(fileMetaInfo);
      when(mockDicomInputStream.readDataset())
          .thenReturn(dataFactory.createBasicImageDataset("CT", 512, 512));
      when(mockDicomInputStream.getTransferSyntax()).thenReturn(EXPLICIT_VR_LITTLE_ENDIAN);

      var metadata = new DicomMetaData(mockDicomInputStream);

      assertEquals(Optional.empty(), metadata.getMediaStorageSOPClassUID());
    }
  }

  @Nested
  class Video_transfer_syntax_tests {

    @ParameterizedTest
    @ValueSource(
        strings = {
          "1.2.840.10008.1.2.4.100", // MPEG2 Main Profile @ Main Level
          "1.2.840.10008.1.2.4.101", // MPEG2 Main Profile @ High Level
          "1.2.840.10008.1.2.4.102", // MPEG4 AVC/H.264 High Profile / Level 4.1
          "1.2.840.10008.1.2.4.103", // MPEG4 AVC/H.264 BD-compatible High Profile / Level 4.1
          "1.2.840.10008.1.2.4.104", // MPEG4 AVC/H.264 High Profile / Level 4.2 For 2D Video
          "1.2.840.10008.1.2.4.105", // MPEG4 AVC/H.264 High Profile / Level 4.2 For 3D Video
          "1.2.840.10008.1.2.4.106" // MPEG4 AVC/H.264 Stereo High Profile / Level 4.2
        })
    void should_identify_video_transfer_syntaxes(String videoTransferSyntax) {
      var dataset = dataFactory.createBasicImageDataset("XA", 1024, 1024);
      var metadata = new DicomMetaData(dataset, videoTransferSyntax);

      assertTrue(metadata.isVideoTransferSyntaxUID());
    }

    @ParameterizedTest
    @ValueSource(
        strings = {
          UID.ImplicitVRLittleEndian,
          UID.ExplicitVRLittleEndian,
          UID.ExplicitVRBigEndian,
          UID.JPEGBaseline8Bit,
          UID.JPEGLossless,
          UID.JPEG2000Lossless,
          UID.RLELossless
        })
    void should_not_identify_non_video_transfer_syntaxes_as_video(String nonVideoTransferSyntax) {
      var dataset = dataFactory.createBasicImageDataset("CT", 512, 512);
      var metadata = new DicomMetaData(dataset, nonVideoTransferSyntax);

      assertFalse(metadata.isVideoTransferSyntaxUID());
    }
  }

  @Nested
  class Storage_class_detection_tests {

    @Test
    void should_detect_media_storage_directory() throws IOException {
      var fileMetaInfo =
          dataFactory.createFileMetaInformation(MEDIA_STORAGE_DIRECTORY, EXPLICIT_VR_LITTLE_ENDIAN);
      var dataset = dataFactory.createDirectoryDataset();
      var dicomData = dataFactory.createCompleteDicomFile(fileMetaInfo, dataset);

      try (var dis = new DicomInputStream(new ByteArrayInputStream(dicomData))) {
        var metadata = new DicomMetaData(dis);

        assertAll(
            () -> assertTrue(metadata.isMediaStorageDirectory()),
            () ->
                assertEquals(
                    Optional.of(MEDIA_STORAGE_DIRECTORY), metadata.getMediaStorageSOPClassUID()));
      }
    }

    @Test
    void should_not_detect_media_storage_directory_for_regular_image() throws IOException {
      var fileMetaInfo =
          dataFactory.createFileMetaInformation(CT_IMAGE_STORAGE, EXPLICIT_VR_LITTLE_ENDIAN);
      var dataset = dataFactory.createBasicImageDataset("CT", 512, 512);
      var dicomData = dataFactory.createCompleteDicomFile(fileMetaInfo, dataset);

      try (var dis = new DicomInputStream(new ByteArrayInputStream(dicomData))) {
        var metadata = new DicomMetaData(dis);

        assertAll(
            () -> assertFalse(metadata.isMediaStorageDirectory()),
            () ->
                assertEquals(Optional.of(CT_IMAGE_STORAGE), metadata.getMediaStorageSOPClassUID()));
      }
    }

    @Test
    void should_detect_segmentation_storage() throws IOException {
      var fileMetaInfo =
          dataFactory.createFileMetaInformation(SEGMENTATION_STORAGE, EXPLICIT_VR_LITTLE_ENDIAN);
      var dataset = dataFactory.createSegmentationDataset();
      var dicomData = dataFactory.createCompleteDicomFile(fileMetaInfo, dataset);

      try (var dis = new DicomInputStream(new ByteArrayInputStream(dicomData))) {
        var metadata = new DicomMetaData(dis);

        assertAll(
            () -> assertTrue(metadata.isSegmentationStorage()),
            () ->
                assertEquals(
                    Optional.of(SEGMENTATION_STORAGE), metadata.getMediaStorageSOPClassUID()));
      }
    }

    @Test
    void should_not_detect_segmentation_storage_for_regular_image() throws IOException {
      var fileMetaInfo =
          dataFactory.createFileMetaInformation(MR_IMAGE_STORAGE, EXPLICIT_VR_LITTLE_ENDIAN);
      var dataset = dataFactory.createBasicImageDataset("MR", 256, 256);
      var dicomData = dataFactory.createCompleteDicomFile(fileMetaInfo, dataset);

      try (var dis = new DicomInputStream(new ByteArrayInputStream(dicomData))) {
        var metadata = new DicomMetaData(dis);

        assertAll(
            () -> assertFalse(metadata.isSegmentationStorage()),
            () ->
                assertEquals(Optional.of(MR_IMAGE_STORAGE), metadata.getMediaStorageSOPClassUID()));
      }
    }

    @Test
    void should_return_false_for_storage_detection_without_file_meta() {
      var dataset = dataFactory.createBasicImageDataset("CT", 512, 512);
      var metadata = new DicomMetaData(dataset, IMPLICIT_VR_LITTLE_ENDIAN);

      assertAll(
          () -> assertFalse(metadata.isMediaStorageDirectory()),
          () -> assertFalse(metadata.isSegmentationStorage()));
    }
  }

  @Nested
  class Image_descriptor_and_frames_tests {

    @Test
    void should_return_correct_number_of_frames_for_single_frame_image() {
      var dataset = dataFactory.createBasicImageDataset("CT", 512, 512);
      var metadata = new DicomMetaData(dataset, IMPLICIT_VR_LITTLE_ENDIAN);

      assertEquals(1, metadata.getNumberOfFrames());
    }

    @Test
    void should_return_correct_number_of_frames_for_multi_frame_image() {
      var dataset = dataFactory.createMultiFrameDataset(25);
      var metadata = new DicomMetaData(dataset, IMPLICIT_VR_LITTLE_ENDIAN);

      assertEquals(25, metadata.getNumberOfFrames());
    }

    @Test
    void should_return_same_dicom_object_instance() {
      var dataset = dataFactory.createBasicImageDataset("US", 640, 480);
      var metadata = new DicomMetaData(dataset, IMPLICIT_VR_LITTLE_ENDIAN);

      assertSame(dataset, metadata.getDicomObject());
    }

    @Test
    void should_return_valid_image_descriptor() {
      var dataset = dataFactory.createBasicImageDataset("CT", 512, 512);
      var metadata = new DicomMetaData(dataset, IMPLICIT_VR_LITTLE_ENDIAN);

      var descriptor = metadata.getImageDescriptor();
      assertAll(
          () -> assertNotNull(descriptor),
          () -> assertEquals(512, descriptor.getRows()),
          () -> assertEquals(512, descriptor.getColumns()),
          () -> assertEquals(1, descriptor.getFrames()));
    }

    @ParameterizedTest
    @CsvSource({"CT, 512, 512, 1", "MR, 256, 256, 1", "US, 640, 480, 1", "XA, 1024, 1024, 10"})
    void should_reflect_dataset_properties_in_image_descriptor(
        String modality, int rows, int columns, int frames) {
      var dataset = dataFactory.createImageDataset(modality, rows, columns, frames);
      var metadata = new DicomMetaData(dataset, IMPLICIT_VR_LITTLE_ENDIAN);

      var descriptor = metadata.getImageDescriptor();
      assertAll(
          () -> assertEquals(rows, descriptor.getRows()),
          () -> assertEquals(columns, descriptor.getColumns()),
          () -> assertEquals(frames, descriptor.getFrames()));
    }
  }

  @Nested
  class Iio_metadata_implementation_tests {

    private DicomMetaData metadata;

    @BeforeEach
    void setUp() {
      var dataset = dataFactory.createBasicImageDataset("CT", 512, 512);
      metadata = new DicomMetaData(dataset, IMPLICIT_VR_LITTLE_ENDIAN);
    }

    @Test
    void should_be_read_only() {
      assertTrue(metadata.isReadOnly());
    }

    @Test
    void should_throw_unsupported_operation_for_get_as_tree() {
      var exception =
          assertThrows(UnsupportedOperationException.class, () -> metadata.getAsTree("any_format"));
      assertTrue(exception.getMessage().contains("tree representation"));
    }

    @Test
    void should_throw_unsupported_operation_for_merge_tree() {
      var exception =
          assertThrows(
              UnsupportedOperationException.class, () -> metadata.mergeTree("format", null));
      assertTrue(exception.getMessage().contains("read-only"));
    }

    @Test
    void should_throw_unsupported_operation_for_reset() {
      var exception = assertThrows(UnsupportedOperationException.class, metadata::reset);
      assertTrue(exception.getMessage().contains("read-only"));
    }
  }

  @Nested
  class To_string_tests {

    @Test
    void should_provide_meaningful_string_representation_with_file_meta() throws IOException {
      var fileMetaInfo = dataFactory.createFileMetaInformation(CT_IMAGE_STORAGE, JPEG_BASELINE);
      var dataset = dataFactory.createBasicImageDataset("CT", 512, 512);
      var dicomData = dataFactory.createCompleteDicomFile(fileMetaInfo, dataset);

      try (var dis = new DicomInputStream(new ByteArrayInputStream(dicomData))) {
        var metadata = new DicomMetaData(dis);
        var string = metadata.toString();

        assertAll(
            () -> assertNotNull(string),
            () -> assertTrue(string.contains("DicomMetaData")),
            () -> assertTrue(string.contains(JPEG_BASELINE)),
            () -> assertTrue(string.contains(CT_IMAGE_STORAGE)),
            () -> assertTrue(string.contains("frames=1")),
            () -> assertTrue(string.contains("hasFileMetaInfo=true")));
      }
    }

    @Test
    void should_handle_missing_file_meta_information_in_string() {
      var dataset = dataFactory.createBasicImageDataset("MR", 256, 256);
      var metadata = new DicomMetaData(dataset, IMPLICIT_VR_LITTLE_ENDIAN);

      var string = metadata.toString();
      assertAll(
          () -> assertNotNull(string),
          () -> assertTrue(string.contains("DicomMetaData")),
          () -> assertTrue(string.contains(IMPLICIT_VR_LITTLE_ENDIAN)),
          () -> assertTrue(string.contains("sopClass='N/A'")),
          () -> assertTrue(string.contains("hasFileMetaInfo=false")));
    }
  }

  @Nested
  class Complex_scenarios_tests {

    @Test
    void should_handle_comprehensive_dicom_with_multiple_attributes() throws IOException {
      var fileMetaInfo = dataFactory.createFileMetaInformation(MR_IMAGE_STORAGE, JPEG_LOSSLESS);
      var dataset = dataFactory.createComprehensiveImageDataset("MR", 256, 256);

      dataset.setString(Tag.PatientName, VR.PN, "Test^Patient");
      dataset.setString(Tag.StudyDescription, VR.LO, "Test MR Study");
      dataset.setString(Tag.SeriesDescription, VR.LO, "Test MR Series");
      dataset.setString(Tag.Manufacturer, VR.LO, "Test Manufacturer");
      dataset.setString(Tag.StationName, VR.SH, "TEST_STATION");

      var dicomData = dataFactory.createCompleteDicomFile(fileMetaInfo, dataset);

      try (var dis = new DicomInputStream(new ByteArrayInputStream(dicomData))) {
        var metadata = new DicomMetaData(dis);

        assertAll(
            () ->
                assertEquals(Optional.of(MR_IMAGE_STORAGE), metadata.getMediaStorageSOPClassUID()),
            () -> assertEquals(JPEG_LOSSLESS, metadata.getTransferSyntaxUID()),
            () -> assertTrue(metadata.hasFileMetaInformation()),
            () -> assertFalse(metadata.isVideoTransferSyntaxUID()),
            () -> assertFalse(metadata.isMediaStorageDirectory()),
            () -> assertFalse(metadata.isSegmentationStorage()));

        // Verify dataset content preservation
        var dcm = metadata.getDicomObject();
        assertAll(
            () -> assertEquals("Test^Patient", dcm.getString(Tag.PatientName)),
            () -> assertEquals("Test MR Study", dcm.getString(Tag.StudyDescription)),
            () -> assertEquals("TEST_STATION", dcm.getString(Tag.StationName)));
      }
    }

    @ParameterizedTest
    @MethodSource("provideTransferSyntaxes")
    void should_determine_transfer_syntax_correctly_from_file_meta_information(
        String transferSyntax) throws IOException {
      var fileMetaInfo = dataFactory.createFileMetaInformation(CT_IMAGE_STORAGE, transferSyntax);
      var dataset = dataFactory.createBasicImageDataset("CT", 512, 512);
      var dicomData = dataFactory.createCompleteDicomFile(fileMetaInfo, dataset);

      try (var dis = new DicomInputStream(new ByteArrayInputStream(dicomData))) {
        var metadata = new DicomMetaData(dis);
        assertEquals(transferSyntax, metadata.getTransferSyntaxUID());
      }
    }

    private static Stream<Arguments> provideTransferSyntaxes() {
      return Stream.of(
          Arguments.of(IMPLICIT_VR_LITTLE_ENDIAN),
          Arguments.of(EXPLICIT_VR_LITTLE_ENDIAN),
          Arguments.of(EXPLICIT_VR_BIG_ENDIAN),
          Arguments.of(JPEG_BASELINE),
          Arguments.of(JPEG_LOSSLESS));
    }
  }

  /** Factory for creating test DICOM data structures using real DICOM attributes. */
  private static class DicomTestDataFactory {

    Attributes createFileMetaInformation(String sopClassUID, String transferSyntaxUID) {
      var fileMetaInfo = new Attributes();
      fileMetaInfo.setBytes(Tag.FileMetaInformationVersion, VR.OB, new byte[] {0, 1});
      fileMetaInfo.setString(Tag.MediaStorageSOPClassUID, VR.UI, sopClassUID);
      fileMetaInfo.setString(Tag.MediaStorageSOPInstanceUID, VR.UI, TEST_SOP_INSTANCE_UID);
      fileMetaInfo.setString(Tag.TransferSyntaxUID, VR.UI, transferSyntaxUID);
      fileMetaInfo.setString(Tag.ImplementationClassUID, VR.UI, "1.2.3.4.5.6.7.8.9.test");
      fileMetaInfo.setString(Tag.ImplementationVersionName, VR.SH, "TEST_IMPL_1.0");
      return fileMetaInfo;
    }

    Attributes createBasicImageDataset(String modality, int rows, int columns) {
      return createImageDataset(modality, rows, columns, 1);
    }

    Attributes createImageDataset(String modality, int rows, int columns, int frames) {
      var dataset = new Attributes();
      dataset.setString(Tag.SOPClassUID, VR.UI, getSOPClassForModality(modality));
      dataset.setString(Tag.SOPInstanceUID, VR.UI, TEST_SOP_INSTANCE_UID);
      dataset.setString(Tag.Modality, VR.CS, modality);
      dataset.setInt(Tag.Rows, VR.US, rows);
      dataset.setInt(Tag.Columns, VR.US, columns);

      if (frames > 1) {
        dataset.setInt(Tag.NumberOfFrames, VR.IS, frames);
      }

      dataset.setInt(Tag.SamplesPerPixel, VR.US, 1);
      dataset.setString(Tag.PhotometricInterpretation, VR.CS, "MONOCHROME2");
      dataset.setInt(Tag.BitsAllocated, VR.US, 16);
      dataset.setInt(Tag.BitsStored, VR.US, 12);
      dataset.setInt(Tag.HighBit, VR.US, 11);
      dataset.setInt(Tag.PixelRepresentation, VR.US, 0);

      // Add minimal pixel data placeholder
      var pixelData = new byte[rows * columns * 2 * frames];
      dataset.setBytes(Tag.PixelData, VR.OW, pixelData);

      return dataset;
    }

    Attributes createMultiFrameDataset(int frames) {
      return createImageDataset("XA", 1024, 1024, frames);
    }

    Attributes createDirectoryDataset() {
      var dataset = new Attributes();
      dataset.setString(Tag.SOPClassUID, VR.UI, MEDIA_STORAGE_DIRECTORY);
      dataset.setString(Tag.SOPInstanceUID, VR.UI, TEST_SOP_INSTANCE_UID);
      dataset.setString(Tag.FileSetID, VR.CS, "DISC001");
      dataset.setString(Tag.FileSetDescriptorFileID, VR.CS, "README");
      dataset.newSequence(Tag.DirectoryRecordSequence, 0);
      return dataset;
    }

    Attributes createSegmentationDataset() {
      var dataset = createImageDataset("SEG", 512, 512, 1);
      dataset.setString(Tag.SOPClassUID, VR.UI, SEGMENTATION_STORAGE);
      return dataset;
    }

    Attributes createComprehensiveImageDataset(String modality, int rows, int columns) {
      var dataset = createBasicImageDataset(modality, rows, columns);
      dataset.setString(Tag.ImageType, VR.CS, "ORIGINAL\\PRIMARY\\T1\\FFE");
      dataset.setString(Tag.AcquisitionDateTime, VR.DT, "20250129120000");
      dataset.setString(Tag.ContentDate, VR.DA, "20250129");
      dataset.setString(Tag.ContentTime, VR.TM, "120500");
      dataset.setDouble(Tag.SliceThickness, VR.DS, 5.0);
      return dataset;
    }

    private String getSOPClassForModality(String modality) {
      return switch (modality) {
        case "CT" -> CT_IMAGE_STORAGE;
        case "MR" -> MR_IMAGE_STORAGE;
        case "US" -> US_IMAGE_STORAGE;
        case "XA" -> UID.XRayAngiographicImageStorage;
        case "SEG" -> SEGMENTATION_STORAGE;
        default -> UID.SecondaryCaptureImageStorage;
      };
    }

    byte[] createCompleteDicomFile(Attributes fileMetaInfo, Attributes dataset) throws IOException {
      var baos = new ByteArrayOutputStream();
      try (var dos = new DicomOutputStream(baos, UID.ExplicitVRLittleEndian)) {
        dos.writeDataset(fileMetaInfo, dataset);
        dos.flush();
      }
      return baos.toByteArray();
    }

    byte[] createDatasetOnlyDicomFile(Attributes dataset, String transferSyntaxUID)
        throws IOException {
      var baos = new ByteArrayOutputStream();
      try (var dos = new DicomOutputStream(baos, transferSyntaxUID)) {
        dos.writeDataset(null, dataset);
      }
      return baos.toByteArray();
    }
  }
}
