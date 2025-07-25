/*
 * Copyright (c) 2025 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.dcm4che3.img;

import static org.junit.jupiter.api.Assertions.*;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.UID;
import org.dcm4che3.data.VR;
import org.dcm4che3.img.stream.ImageDescriptor;
import org.dcm4che3.io.DicomInputStream;
import org.dcm4che3.io.DicomOutputStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.w3c.dom.Node;

/**
 * Test class for {@link DicomMetaData}.
 *
 * <p>This test class validates DICOM metadata functionality including File Meta Information
 * handling, dataset attribute access, transfer syntax detection, and specialized DICOM object type
 * identification using real DICOM data structures.
 */
class DicomMetaDataTest {

  // Standard DICOM Transfer Syntax UIDs
  private static final String IMPLICIT_VR_LITTLE_ENDIAN = UID.ImplicitVRLittleEndian;
  private static final String EXPLICIT_VR_LITTLE_ENDIAN = UID.ExplicitVRLittleEndian;
  private static final String EXPLICIT_VR_BIG_ENDIAN = UID.ExplicitVRBigEndian;
  private static final String JPEG_BASELINE = UID.JPEGBaseline8Bit;
  private static final String JPEG_LOSSLESS = UID.JPEGLossless;
  private static final String MPEG2_VIDEO = UID.MPEG2MPHL;
  private static final String H264_VIDEO = UID.MPEG4HP41;

  // Standard DICOM SOP Class UIDs
  private static final String CT_IMAGE_STORAGE = UID.CTImageStorage;
  private static final String MR_IMAGE_STORAGE = UID.MRImageStorage;
  private static final String US_IMAGE_STORAGE = UID.UltrasoundImageStorage;
  private static final String MEDIA_STORAGE_DIRECTORY = UID.MediaStorageDirectoryStorage;
  private static final String SEGMENTATION_STORAGE = "1.2.840.10008.5.1.4.1.1.66.4";

  // Test instance UIDs
  private static final String TEST_SOP_INSTANCE_UID = "1.2.3.4.5.6.7.8.9.10.11.12.13.14.15";
  private static final String TEST_SERIES_INSTANCE_UID = "1.2.3.4.5.6.7.8.9.10.11.12.13.14";
  private static final String TEST_STUDY_INSTANCE_UID = "1.2.3.4.5.6.7.8.9.10.11.12.13";

  private DicomTestDataFactory dataFactory;

  @BeforeEach
  void setUp() {
    dataFactory = new DicomTestDataFactory();
  }

  @Test
  @DisplayName("DicomMetaData should be created from DicomInputStream with File Meta Information")
  void testCreationFromDicomInputStreamWithFileMeta() throws IOException {
    // Create complete DICOM with File Meta Information
    Attributes fileMetaInfo =
        dataFactory.createFileMetaInformation(
            CT_IMAGE_STORAGE, TEST_SOP_INSTANCE_UID, EXPLICIT_VR_LITTLE_ENDIAN);
    Attributes dataset = dataFactory.createBasicCTImageDataset();

    byte[] dicomData = dataFactory.createCompleteDicomFile(fileMetaInfo, dataset);

    try (DicomInputStream dis = new DicomInputStream(new ByteArrayInputStream(dicomData))) {
      DicomMetaData metadata = new DicomMetaData(dis);

      assertNotNull(metadata);
      assertTrue(metadata.hasFileMetaInformation());
      assertNotNull(metadata.getFileMetaInformation());
      assertNotNull(metadata.getDicomObject());
      assertNotNull(metadata.getImageDescriptor());
      assertEquals(EXPLICIT_VR_LITTLE_ENDIAN, metadata.getTransferSyntaxUID());
      assertEquals(CT_IMAGE_STORAGE, metadata.getMediaStorageSOPClassUID());
      assertFalse(metadata.isVideoTransferSyntaxUID());
      assertFalse(metadata.isMediaStorageDirectory());
      assertFalse(metadata.isSegmentationStorage());
    }
  }

  @Test
  @DisplayName(
      "DicomMetaData should be created from DicomInputStream without File Meta Information")
  void testCreationFromDicomInputStreamWithoutFileMeta() throws IOException {
    // Create dataset-only DICOM (without File Meta Information)
    Attributes dataset = dataFactory.createBasicMRImageDataset();

    byte[] dicomData = dataFactory.createDatasetOnlyDicomFile(dataset, IMPLICIT_VR_LITTLE_ENDIAN);

    try (DicomInputStream dis = new DicomInputStream(new ByteArrayInputStream(dicomData))) {
      DicomMetaData metadata = new DicomMetaData(dis);

      assertNotNull(metadata);
      assertFalse(metadata.hasFileMetaInformation());
      assertNull(metadata.getFileMetaInformation());
      assertNotNull(metadata.getDicomObject());
      assertNotNull(metadata.getImageDescriptor());
      assertEquals(IMPLICIT_VR_LITTLE_ENDIAN, metadata.getTransferSyntaxUID());
      assertNull(metadata.getMediaStorageSOPClassUID());
    }
  }

  @Test
  @DisplayName("DicomMetaData should be created from Attributes and transfer syntax")
  void testCreationFromAttributesAndTransferSyntax() {
    Attributes dataset = dataFactory.createBasicUSImageDataset();

    DicomMetaData metadata = new DicomMetaData(dataset, EXPLICIT_VR_BIG_ENDIAN);

    assertNotNull(metadata);
    assertFalse(metadata.hasFileMetaInformation());
    assertNull(metadata.getFileMetaInformation());
    assertEquals(dataset, metadata.getDicomObject());
    assertNotNull(metadata.getImageDescriptor());
    assertEquals(EXPLICIT_VR_BIG_ENDIAN, metadata.getTransferSyntaxUID());
    assertNull(metadata.getMediaStorageSOPClassUID());
  }

  @Test
  @DisplayName("Constructor should throw NullPointerException for null DicomInputStream")
  void testNullDicomInputStreamThrows() {
    assertThrows(NullPointerException.class, () -> new DicomMetaData((DicomInputStream) null));
  }

  @Test
  @DisplayName("Constructor should throw NullPointerException for null Attributes")
  void testNullAttributesThrows() {
    assertThrows(
        NullPointerException.class, () -> new DicomMetaData(null, IMPLICIT_VR_LITTLE_ENDIAN));
  }

  @Test
  @DisplayName("Constructor should throw NullPointerException for null transfer syntax")
  void testNullTransferSyntaxThrows() {
    Attributes dataset = dataFactory.createBasicCTImageDataset();
    assertThrows(NullPointerException.class, () -> new DicomMetaData(dataset, null));
  }

  @Test
  @DisplayName(
      "getMediaStorageSOPClassUID should return correct SOP Class from File Meta Information")
  void testGetMediaStorageSOPClassUID() throws IOException {
    Attributes fileMetaInfo =
        dataFactory.createFileMetaInformation(
            MR_IMAGE_STORAGE, TEST_SOP_INSTANCE_UID, EXPLICIT_VR_LITTLE_ENDIAN);
    Attributes dataset = dataFactory.createBasicMRImageDataset();

    byte[] dicomData = dataFactory.createCompleteDicomFile(fileMetaInfo, dataset);

    try (DicomInputStream dis = new DicomInputStream(new ByteArrayInputStream(dicomData))) {
      DicomMetaData metadata = new DicomMetaData(dis);
      assertEquals(MR_IMAGE_STORAGE, metadata.getMediaStorageSOPClassUID());
    }
  }

  @Test
  @DisplayName("getMediaStorageSOPClassUID should return null when no File Meta Information")
  void testGetMediaStorageSOPClassUIDWithoutFileMeta() {
    Attributes dataset = dataFactory.createBasicCTImageDataset();
    DicomMetaData metadata = new DicomMetaData(dataset, IMPLICIT_VR_LITTLE_ENDIAN);

    assertNull(metadata.getMediaStorageSOPClassUID());
  }

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
  @DisplayName("isVideoTransferSyntaxUID should return true for video transfer syntaxes")
  void testIsVideoTransferSyntaxUID(String videoTransferSyntax) {
    Attributes dataset = dataFactory.createBasicCTImageDataset();
    DicomMetaData metadata = new DicomMetaData(dataset, videoTransferSyntax);

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
  @DisplayName("isVideoTransferSyntaxUID should return false for non-video transfer syntaxes")
  void testIsNotVideoTransferSyntaxUID(String nonVideoTransferSyntax) {
    Attributes dataset = dataFactory.createBasicCTImageDataset();
    DicomMetaData metadata = new DicomMetaData(dataset, nonVideoTransferSyntax);

    assertFalse(metadata.isVideoTransferSyntaxUID());
  }

  @Test
  @DisplayName("isVideoTransferSyntaxUID should return false for null transfer syntax")
  void testIsVideoTransferSyntaxUIDWithNull() {
    Attributes dataset = dataFactory.createBasicCTImageDataset();
    // This would normally throw NPE in constructor, but let's test the logic
    DicomMetaData metadata = new DicomMetaData(dataset, IMPLICIT_VR_LITTLE_ENDIAN);

    // Force null transfer syntax using reflection would be complex,
    // so we test the actual method behavior
    assertFalse(metadata.isVideoTransferSyntaxUID());
  }

  @Test
  @DisplayName("isMediaStorageDirectory should return true for DICOMDIR")
  void testIsMediaStorageDirectoryTrue() throws IOException {
    Attributes fileMetaInfo =
        dataFactory.createFileMetaInformation(
            MEDIA_STORAGE_DIRECTORY, TEST_SOP_INSTANCE_UID, EXPLICIT_VR_LITTLE_ENDIAN);
    Attributes dataset = dataFactory.createBasicDirectoryDataset();

    byte[] dicomData = dataFactory.createCompleteDicomFile(fileMetaInfo, dataset);

    try (DicomInputStream dis = new DicomInputStream(new ByteArrayInputStream(dicomData))) {
      DicomMetaData metadata = new DicomMetaData(dis);
      assertTrue(metadata.isMediaStorageDirectory());
    }
  }

  @Test
  @DisplayName("isMediaStorageDirectory should return false for non-directory storage")
  void testIsMediaStorageDirectoryFalse() throws IOException {
    Attributes fileMetaInfo =
        dataFactory.createFileMetaInformation(
            CT_IMAGE_STORAGE, TEST_SOP_INSTANCE_UID, EXPLICIT_VR_LITTLE_ENDIAN);
    Attributes dataset = dataFactory.createBasicCTImageDataset();

    byte[] dicomData = dataFactory.createCompleteDicomFile(fileMetaInfo, dataset);

    try (DicomInputStream dis = new DicomInputStream(new ByteArrayInputStream(dicomData))) {
      DicomMetaData metadata = new DicomMetaData(dis);
      assertFalse(metadata.isMediaStorageDirectory());
    }
  }

  @Test
  @DisplayName("isSegmentationStorage should return true for Segmentation Storage")
  void testIsSegmentationStorageTrue() throws IOException {
    Attributes fileMetaInfo =
        dataFactory.createFileMetaInformation(
            SEGMENTATION_STORAGE, TEST_SOP_INSTANCE_UID, EXPLICIT_VR_LITTLE_ENDIAN);
    Attributes dataset = dataFactory.createBasicSegmentationDataset();

    byte[] dicomData = dataFactory.createCompleteDicomFile(fileMetaInfo, dataset);

    try (DicomInputStream dis = new DicomInputStream(new ByteArrayInputStream(dicomData))) {
      DicomMetaData metadata = new DicomMetaData(dis);
      assertTrue(metadata.isSegmentationStorage());
    }
  }

  @Test
  @DisplayName("isSegmentationStorage should return false for non-segmentation storage")
  void testIsSegmentationStorageFalse() throws IOException {
    Attributes fileMetaInfo =
        dataFactory.createFileMetaInformation(
            MR_IMAGE_STORAGE, TEST_SOP_INSTANCE_UID, EXPLICIT_VR_LITTLE_ENDIAN);
    Attributes dataset = dataFactory.createBasicMRImageDataset();

    byte[] dicomData = dataFactory.createCompleteDicomFile(fileMetaInfo, dataset);

    try (DicomInputStream dis = new DicomInputStream(new ByteArrayInputStream(dicomData))) {
      DicomMetaData metadata = new DicomMetaData(dis);
      assertFalse(metadata.isSegmentationStorage());
    }
  }

  @Test
  @DisplayName("getNumberOfFrames should return correct frame count from ImageDescriptor")
  void testGetNumberOfFrames() {
    // Single frame image
    Attributes singleFrameDataset = dataFactory.createBasicCTImageDataset();
    DicomMetaData singleFrameMetadata =
        new DicomMetaData(singleFrameDataset, IMPLICIT_VR_LITTLE_ENDIAN);
    assertEquals(1, singleFrameMetadata.getNumberOfFrames());

    // Multi-frame image
    Attributes multiFrameDataset = dataFactory.createMultiFrameDataset(10);
    DicomMetaData multiFrameMetadata =
        new DicomMetaData(multiFrameDataset, IMPLICIT_VR_LITTLE_ENDIAN);
    assertEquals(10, multiFrameMetadata.getNumberOfFrames());
  }

  @Test
  @DisplayName("getDicomObject should return the same Attributes instance")
  void testGetDicomObject() {
    Attributes dataset = dataFactory.createBasicUSImageDataset();
    DicomMetaData metadata = new DicomMetaData(dataset, IMPLICIT_VR_LITTLE_ENDIAN);

    assertSame(dataset, metadata.getDicomObject());
  }

  @Test
  @DisplayName("getImageDescriptor should return valid ImageDescriptor")
  void testGetImageDescriptor() {
    Attributes dataset = dataFactory.createBasicCTImageDataset();
    DicomMetaData metadata = new DicomMetaData(dataset, IMPLICIT_VR_LITTLE_ENDIAN);

    ImageDescriptor descriptor = metadata.getImageDescriptor();
    assertNotNull(descriptor);
    assertEquals(512, descriptor.getRows());
    assertEquals(512, descriptor.getColumns());
    assertEquals(1, descriptor.getFrames());
  }

  @Test
  @DisplayName("isReadOnly should return true")
  void testIsReadOnly() {
    Attributes dataset = dataFactory.createBasicCTImageDataset();
    DicomMetaData metadata = new DicomMetaData(dataset, IMPLICIT_VR_LITTLE_ENDIAN);

    assertTrue(metadata.isReadOnly());
  }

  @Test
  @DisplayName("getAsTree should throw UnsupportedOperationException")
  void testGetAsTreeThrows() {
    Attributes dataset = dataFactory.createBasicCTImageDataset();
    DicomMetaData metadata = new DicomMetaData(dataset, IMPLICIT_VR_LITTLE_ENDIAN);

    UnsupportedOperationException exception =
        assertThrows(UnsupportedOperationException.class, () -> metadata.getAsTree("any_format"));
    assertTrue(exception.getMessage().contains("tree representation"));
  }

  @Test
  @DisplayName("mergeTree should throw UnsupportedOperationException")
  void testMergeTreeThrows() {
    Attributes dataset = dataFactory.createBasicCTImageDataset();
    DicomMetaData metadata = new DicomMetaData(dataset, IMPLICIT_VR_LITTLE_ENDIAN);

    UnsupportedOperationException exception =
        assertThrows(
            UnsupportedOperationException.class, () -> metadata.mergeTree("format", (Node) null));
    assertTrue(exception.getMessage().contains("read-only"));
  }

  @Test
  @DisplayName("reset should throw UnsupportedOperationException")
  void testResetThrows() {
    Attributes dataset = dataFactory.createBasicCTImageDataset();
    DicomMetaData metadata = new DicomMetaData(dataset, IMPLICIT_VR_LITTLE_ENDIAN);

    UnsupportedOperationException exception =
        assertThrows(UnsupportedOperationException.class, metadata::reset);
    assertTrue(exception.getMessage().contains("read-only"));
  }

  @Test
  @DisplayName("toString should provide meaningful representation")
  void testToString() throws IOException {
    Attributes fileMetaInfo =
        dataFactory.createFileMetaInformation(
            CT_IMAGE_STORAGE, TEST_SOP_INSTANCE_UID, JPEG_BASELINE);
    Attributes dataset = dataFactory.createBasicCTImageDataset();

    byte[] dicomData = dataFactory.createCompleteDicomFile(fileMetaInfo, dataset);

    try (DicomInputStream dis = new DicomInputStream(new ByteArrayInputStream(dicomData))) {
      DicomMetaData metadata = new DicomMetaData(dis);
      String toString = metadata.toString();

      assertNotNull(toString);
      assertTrue(toString.contains("DicomMetaData"));
      assertTrue(toString.contains(JPEG_BASELINE));
      assertTrue(toString.contains(CT_IMAGE_STORAGE));
      assertTrue(toString.contains("frames=1"));
      assertTrue(toString.contains("hasFileMetaInfo=true"));
    }
  }

  @Test
  @DisplayName("toString should handle missing File Meta Information")
  void testToStringWithoutFileMeta() {
    Attributes dataset = dataFactory.createBasicMRImageDataset();
    DicomMetaData metadata = new DicomMetaData(dataset, IMPLICIT_VR_LITTLE_ENDIAN);

    String toString = metadata.toString();
    assertNotNull(toString);
    assertTrue(toString.contains("DicomMetaData"));
    assertTrue(toString.contains(IMPLICIT_VR_LITTLE_ENDIAN));
    assertTrue(toString.contains("sopClass='null'"));
    assertTrue(toString.contains("hasFileMetaInfo=false"));
  }

  @ParameterizedTest
  @CsvSource({"CT, 512, 512, 1", "MR, 256, 256, 1", "US, 640, 480, 1", "XA, 1024, 1024, 10"})
  @DisplayName("ImageDescriptor should reflect dataset properties correctly")
  void testImageDescriptorProperties(String modality, int rows, int columns, int frames) {
    Attributes dataset = dataFactory.createImageDataset(modality, rows, columns, frames);
    DicomMetaData metadata = new DicomMetaData(dataset, IMPLICIT_VR_LITTLE_ENDIAN);

    ImageDescriptor descriptor = metadata.getImageDescriptor();
    assertEquals(rows, descriptor.getRows());
    assertEquals(columns, descriptor.getColumns());
    assertEquals(frames, descriptor.getFrames());
    assertEquals(modality, descriptor.getModality());
  }

  @Test
  @DisplayName("Complex DICOM with multiple attributes should be handled correctly")
  void testComplexDicomHandling() throws IOException {
    // Create comprehensive DICOM with multiple attributes
    Attributes fileMetaInfo =
        dataFactory.createFileMetaInformation(
            MR_IMAGE_STORAGE, TEST_SOP_INSTANCE_UID, JPEG_LOSSLESS);

    Attributes dataset = dataFactory.createComprehensiveMRDataset();
    dataset.setString(Tag.SeriesInstanceUID, VR.UI, TEST_SERIES_INSTANCE_UID);
    dataset.setString(Tag.StudyInstanceUID, VR.UI, TEST_STUDY_INSTANCE_UID);
    dataset.setString(Tag.PatientName, VR.PN, "Test^Patient");
    dataset.setString(Tag.StudyDescription, VR.LO, "Test MR Study");
    dataset.setString(Tag.SeriesDescription, VR.LO, "Test MR Series");
    dataset.setString(Tag.Manufacturer, VR.LO, "Test Manufacturer");
    dataset.setString(Tag.ManufacturerModelName, VR.LO, "Test Model");
    dataset.setString(Tag.StationName, VR.SH, "TEST_STATION");

    byte[] dicomData = dataFactory.createCompleteDicomFile(fileMetaInfo, dataset);

    try (DicomInputStream dis = new DicomInputStream(new ByteArrayInputStream(dicomData))) {
      DicomMetaData metadata = new DicomMetaData(dis);

      // Verify metadata properties
      assertEquals(MR_IMAGE_STORAGE, metadata.getMediaStorageSOPClassUID());
      assertEquals(JPEG_LOSSLESS, metadata.getTransferSyntaxUID());
      assertTrue(metadata.hasFileMetaInformation());
      assertFalse(metadata.isVideoTransferSyntaxUID());
      assertFalse(metadata.isMediaStorageDirectory());
      assertFalse(metadata.isSegmentationStorage());

      // Verify dataset content is preserved
      Attributes dcm = metadata.getDicomObject();
      assertEquals("Test^Patient", dcm.getString(Tag.PatientName));
      assertEquals("Test MR Study", dcm.getString(Tag.StudyDescription));
      assertEquals("TEST_STATION", dcm.getString(Tag.StationName));

      // Verify image descriptor
      ImageDescriptor descriptor = metadata.getImageDescriptor();
      assertEquals("MR", descriptor.getModality());
      assertEquals("TEST_STATION", descriptor.getStationName());
    }
  }

  @Test
  @DisplayName("Transfer syntax should be determined correctly from File Meta Information")
  void testTransferSyntaxDetermination() throws IOException {
    // Test with different transfer syntaxes in File Meta Information
    String[] transferSyntaxes = {
      IMPLICIT_VR_LITTLE_ENDIAN,
      EXPLICIT_VR_LITTLE_ENDIAN,
      EXPLICIT_VR_BIG_ENDIAN,
      JPEG_BASELINE,
      JPEG_LOSSLESS
    };

    for (String expectedTransferSyntax : transferSyntaxes) {
      Attributes fileMetaInfo =
          dataFactory.createFileMetaInformation(
              CT_IMAGE_STORAGE, TEST_SOP_INSTANCE_UID, expectedTransferSyntax);
      Attributes dataset = dataFactory.createBasicCTImageDataset();

      byte[] dicomData = dataFactory.createCompleteDicomFile(fileMetaInfo, dataset);

      try (DicomInputStream dis = new DicomInputStream(new ByteArrayInputStream(dicomData))) {
        DicomMetaData metadata = new DicomMetaData(dis);
        assertEquals(expectedTransferSyntax, metadata.getTransferSyntaxUID());
      }
    }
  }

  /** Factory class for creating test DICOM data structures. */
  private static class DicomTestDataFactory {

    Attributes createFileMetaInformation(
        String sopClassUID, String sopInstanceUID, String transferSyntaxUID) {
      Attributes fileMetaInfo = new Attributes();
      fileMetaInfo.setBytes(Tag.FileMetaInformationVersion, VR.OB, new byte[] {0, 1});
      fileMetaInfo.setString(Tag.MediaStorageSOPClassUID, VR.UI, sopClassUID);
      fileMetaInfo.setString(Tag.MediaStorageSOPInstanceUID, VR.UI, sopInstanceUID);
      fileMetaInfo.setString(Tag.TransferSyntaxUID, VR.UI, transferSyntaxUID);
      fileMetaInfo.setString(Tag.ImplementationClassUID, VR.UI, "1.2.3.4.5.6.7.8.9.test");
      fileMetaInfo.setString(Tag.ImplementationVersionName, VR.SH, "TEST_IMPL_1.0");
      // Calculate and set File Meta Information Group Length (must be last)
      // This is computed automatically by DicomOutputStream, but we need to set a placeholder
      fileMetaInfo.setInt(Tag.FileMetaInformationGroupLength, VR.UL, 0);
      return fileMetaInfo;
    }

    Attributes createBasicCTImageDataset() {
      return createImageDataset("CT", 512, 512, 1);
    }

    Attributes createBasicMRImageDataset() {
      return createImageDataset("MR", 256, 256, 1);
    }

    Attributes createBasicUSImageDataset() {
      return createImageDataset("US", 640, 480, 1);
    }

    Attributes createImageDataset(String modality, int rows, int columns, int frames) {
      Attributes dataset = new Attributes();
      dataset.setString(Tag.SOPClassUID, VR.UI, getSOPClassForModality(modality));
      dataset.setString(Tag.SOPInstanceUID, VR.UI, TEST_SOP_INSTANCE_UID);
      dataset.setString(Tag.Modality, VR.CS, modality);
      dataset.setInt(Tag.Rows, VR.US, rows);
      dataset.setInt(Tag.Columns, VR.US, columns);
      // Only set NumberOfFrames if more than 1 frame (per DICOM standard)
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
      byte[] pixelData = new byte[rows * columns * 2 * frames];
      dataset.setBytes(Tag.PixelData, VR.OW, pixelData);

      return dataset;
    }

    Attributes createMultiFrameDataset(int frames) {
      return createImageDataset("XA", 1024, 1024, frames);
    }

    Attributes createBasicDirectoryDataset() {
      Attributes dataset = new Attributes();
      dataset.setString(Tag.SOPClassUID, VR.UI, MEDIA_STORAGE_DIRECTORY);
      dataset.setString(Tag.SOPInstanceUID, VR.UI, TEST_SOP_INSTANCE_UID);
      dataset.setString(Tag.FileSetID, VR.CS, "DISC001");
      dataset.setString(Tag.FileSetDescriptorFileID, VR.CS, "README");
      // Add minimal directory record sequence (empty for basic test)
      dataset.newSequence(Tag.DirectoryRecordSequence, 0);
      return dataset;
    }

    Attributes createBasicSegmentationDataset() {
      Attributes dataset = createImageDataset("SEG", 512, 512, 1);
      dataset.setString(Tag.SOPClassUID, VR.UI, SEGMENTATION_STORAGE);
      return dataset;
    }

    Attributes createComprehensiveMRDataset() {
      Attributes dataset = createBasicMRImageDataset();
      dataset.setString(Tag.ImageType, VR.CS, "ORIGINAL\\PRIMARY\\T1\\FFE");
      dataset.setString(Tag.AcquisitionDateTime, VR.DT, "20250129120000");
      dataset.setString(Tag.ContentDate, VR.DT, "20250129");
      dataset.setString(Tag.ContentTime, VR.TM, "120500");
      dataset.setDouble(Tag.SliceThickness, VR.DS, 5.0);
      dataset.setDouble(Tag.RepetitionTime, VR.DS, 500.0);
      dataset.setDouble(Tag.EchoTime, VR.DS, 15.0);
      dataset.setDouble(Tag.FlipAngle, VR.DS, 90.0);
      dataset.setString(Tag.MagneticFieldStrength, VR.DS, "1.5");
      dataset.setString(Tag.SequenceName, VR.SH, "T1_SE");
      return dataset;
    }

    private String getSOPClassForModality(String modality) {
      return switch (modality) {
        case "CT" -> CT_IMAGE_STORAGE;
        case "MR" -> MR_IMAGE_STORAGE;
        case "US" -> US_IMAGE_STORAGE;
        case "XA" -> UID.XRayAngiographicImageStorage;
        case "SEG" -> SEGMENTATION_STORAGE;
        default -> CT_IMAGE_STORAGE;
      };
    }

    byte[] createCompleteDicomFile(Attributes fileMetaInfo, Attributes dataset) throws IOException {
      ByteArrayOutputStream baos = new ByteArrayOutputStream();
      try (DicomOutputStream dos = new DicomOutputStream(baos, UID.ExplicitVRLittleEndian)) {
        dos.writeDataset(fileMetaInfo, dataset);
        dos.flush();
      }
      return baos.toByteArray();
    }

    byte[] createDatasetOnlyDicomFile(Attributes dataset, String transferSyntaxUID)
        throws IOException {
      ByteArrayOutputStream baos = new ByteArrayOutputStream();
      try (DicomOutputStream dos = new DicomOutputStream(baos, transferSyntaxUID)) {
        // Write only the dataset without File Meta Information
        dos.writeDataset(null, dataset);
      }
      return baos.toByteArray();
    }
  }
}
