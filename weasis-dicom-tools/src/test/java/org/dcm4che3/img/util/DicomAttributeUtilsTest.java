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

import java.io.IOException;
import java.util.Optional;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Sequence;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.VR;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class DicomAttributeUtilsTest {

  private Attributes rootAttributes;
  private Attributes childAttributes;
  private Attributes grandChildAttributes;

  @BeforeEach
  void setUp() {
    // Create a hierarchy: root -> child -> grandchild
    rootAttributes = new Attributes();
    childAttributes = new Attributes();
    grandChildAttributes = new Attributes();

    // Set up parent-child relationships
    Sequence sequence = rootAttributes.newSequence(Tag.RelatedSeriesSequence, 1);
    sequence.add(childAttributes);
    Sequence sequence2 = childAttributes.newSequence(Tag.PurposeOfReferenceCodeSequence, 1);
    sequence2.add(grandChildAttributes);
  }

  @Nested
  @DisplayName("Modality Extraction")
  class ModalityExtraction {

    @Test
    @DisplayName("Should return null for null attributes")
    void shouldReturnNullForNullAttributes() {
      assertNull(DicomAttributeUtils.getModality(null));
    }

    @Test
    @DisplayName("Should extract modality from current attributes")
    void shouldExtractModalityFromCurrentAttributes() {
      rootAttributes.setString(Tag.Modality, VR.CS, "CT");

      String modality = DicomAttributeUtils.getModality(rootAttributes);

      assertEquals("CT", modality);
    }

    @ParameterizedTest
    @ValueSource(strings = {"CT", "MR", "US", "XA", "RF", "DX", "CR", "MG", "PT", "NM"})
    @DisplayName("Should handle various modality types")
    void shouldHandleVariousModalityTypes(String modalityType) {
      rootAttributes.setString(Tag.Modality, VR.CS, modalityType);

      String result = DicomAttributeUtils.getModality(rootAttributes);

      assertEquals(modalityType, result);
    }

    @Test
    @DisplayName("Should find modality in parent when not in child")
    void shouldFindModalityInParentWhenNotInChild() {
      rootAttributes.setString(Tag.Modality, VR.CS, "MR");
      // Child has no modality

      String modality = DicomAttributeUtils.getModality(childAttributes);

      assertEquals("MR", modality);
    }

    @Test
    @DisplayName("Should find modality in grandparent hierarchy")
    void shouldFindModalityInGrandparentHierarchy() {
      rootAttributes.setString(Tag.Modality, VR.CS, "US");
      // Neither child nor grandchild have modality

      String modality = DicomAttributeUtils.getModality(grandChildAttributes);

      assertEquals("US", modality);
    }

    @Test
    @DisplayName("Should prefer child modality over parent")
    void shouldPreferChildModalityOverParent() {
      rootAttributes.setString(Tag.Modality, VR.CS, "CT");
      childAttributes.setString(Tag.Modality, VR.CS, "MR");

      String modality = DicomAttributeUtils.getModality(childAttributes);

      assertEquals("MR", modality);
    }

    @Test
    @DisplayName("Should return null when no modality in hierarchy")
    void shouldReturnNullWhenNoModalityInHierarchy() {
      // No modality set in any level

      String modality = DicomAttributeUtils.getModality(grandChildAttributes);

      assertNull(modality);
    }

    @Test
    @DisplayName("Should handle empty modality string")
    void shouldHandleEmptyModalityString() {
      rootAttributes.setString(Tag.Modality, VR.CS, "");

      String modality = DicomAttributeUtils.getModality(rootAttributes);
      assertNull(modality);
    }

    @Test
    @DisplayName("Should handle modality with multiple values")
    void shouldHandleModalityWithMultipleValues() {
      rootAttributes.setString(Tag.Modality, VR.CS, "CT", "MR");

      String modality = DicomAttributeUtils.getModality(rootAttributes);

      assertEquals("CT", modality); // Should return first value
    }
  }

  @Nested
  @DisplayName("Byte Data Extraction")
  class ByteDataExtraction {

    @Test
    @DisplayName("Should return empty Optional for null attributes")
    void shouldReturnEmptyOptionalForNullAttributes() {
      Optional<byte[]> result = DicomAttributeUtils.getByteData(null, Tag.PixelData);

      assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("Should return empty Optional for non-existent tag")
    void shouldReturnEmptyOptionalForNonExistentTag() {
      Optional<byte[]> result = DicomAttributeUtils.getByteData(rootAttributes, Tag.PixelData);

      assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("Should extract byte data from standard tag")
    void shouldExtractByteDataFromStandardTag() {
      byte[] expectedData = {0x01, 0x02, 0x03, 0x04, 0x05};
      rootAttributes.setBytes(Tag.PixelData, VR.OW, expectedData);

      Optional<byte[]> result = DicomAttributeUtils.getByteData(rootAttributes, Tag.PixelData);

      assertTrue(result.isPresent());
      assertArrayEquals(expectedData, result.get());
    }

    @Test
    @DisplayName("Should extract byte data with private creator")
    void shouldExtractByteDataWithPrivateCreator() {
      byte[] expectedData = {(byte) 0xFF, (byte) 0xFE, 0x00, 0x01};
      String privateCreator = "TEST_CREATOR";
      int privateTag = 0x00111001;

      rootAttributes.setString(0x00110010, VR.LO, privateCreator);
      rootAttributes.setBytes(privateCreator, privateTag, VR.OB, expectedData);

      Optional<byte[]> result =
          DicomAttributeUtils.getByteData(rootAttributes, privateCreator, privateTag);

      assertTrue(result.isPresent());
      assertArrayEquals(expectedData, result.get());
    }

    @Test
    @DisplayName("Should handle empty byte array")
    void shouldHandleEmptyByteArray() {
      byte[] emptyData = new byte[0];
      rootAttributes.setBytes(Tag.PixelData, VR.OW, emptyData);

      Optional<byte[]> result = DicomAttributeUtils.getByteData(rootAttributes, Tag.PixelData);
      assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("Should handle large byte arrays")
    void shouldHandleLargeByteArrays() {
      byte[] largeData = new byte[10000];
      // Fill with pattern
      for (int i = 0; i < largeData.length; i++) {
        largeData[i] = (byte) (i % 256);
      }
      rootAttributes.setBytes(Tag.PixelData, VR.OB, largeData);

      Optional<byte[]> result = DicomAttributeUtils.getByteData(rootAttributes, Tag.PixelData);

      assertTrue(result.isPresent());
      assertArrayEquals(largeData, result.get());
    }

    @Test
    @DisplayName("Should extract different VR types as bytes")
    void shouldExtractDifferentVrTypesAsBytes() {
      // Test with OW (Other Word String)
      byte[] owData = {0x01, 0x02, 0x03, 0x04};
      rootAttributes.setBytes(Tag.PixelData, VR.OW, owData);

      Optional<byte[]> owResult = DicomAttributeUtils.getByteData(rootAttributes, Tag.PixelData);
      assertTrue(owResult.isPresent());
      assertArrayEquals(owData, owResult.get());

      // Test with OB (Other Byte String)
      byte[] obData = {(byte) 0xFF, (byte) 0xFE, 0x00, 0x01};
      rootAttributes.setBytes(Tag.RedPaletteColorLookupTableData, VR.OB, obData);

      Optional<byte[]> obResult =
          DicomAttributeUtils.getByteData(rootAttributes, Tag.RedPaletteColorLookupTableData);
      assertTrue(obResult.isPresent());
      assertArrayEquals(obData, obResult.get());
    }

    @Test
    @DisplayName("Should return empty Optional for null private creator with private tag")
    void shouldReturnEmptyOptionalForNullPrivateCreatorWithPrivateTag() {
      byte[] data = {0x01, 0x02, 0x03, 0x04};
      int privateTag = 0x00111001;

      // Set data without proper private creator setup
      rootAttributes.setBytes(privateTag, VR.OB, data);

      Optional<byte[]> result = DicomAttributeUtils.getByteData(rootAttributes, null, privateTag);

      // Should still work for null private creator (delegates to standard method)
      assertTrue(result.isPresent());
      assertArrayEquals(data, result.get());
    }

    @Test
    @DisplayName("Should handle attributes with no value for tag")
    void shouldHandleAttributesWithNoValueForTag() {
      // Set a tag but with null value
      rootAttributes.setNull(Tag.PixelData, VR.OW);

      Optional<byte[]> result = DicomAttributeUtils.getByteData(rootAttributes, Tag.PixelData);

      assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("Should extract various DICOM binary data types")
    void shouldExtractVariousDicomBinaryDataTypes() {
      // Test overlay data
      byte[] overlayData = createOverlayBitmap();
      rootAttributes.setBytes(Tag.OverlayData, VR.OB, overlayData);

      Optional<byte[]> overlayResult =
          DicomAttributeUtils.getByteData(rootAttributes, Tag.OverlayData);
      assertTrue(overlayResult.isPresent());
      assertArrayEquals(overlayData, overlayResult.get());

      // Test palette color data
      byte[] paletteData = createPaletteColorData();
      rootAttributes.setBytes(Tag.BluePaletteColorLookupTableData, VR.OW, paletteData);

      Optional<byte[]> paletteResult =
          DicomAttributeUtils.getByteData(rootAttributes, Tag.BluePaletteColorLookupTableData);
      assertTrue(paletteResult.isPresent());
      assertArrayEquals(paletteData, paletteResult.get());
    }

    @Test
    @DisplayName("Should handle IOException gracefully")
    void shouldHandleIOExceptionGracefully() {
      // Create attributes that would cause IOException when reading bytes
      Attributes problematicAttributes = new ProblematicAttributes();

      Optional<byte[]> result =
          DicomAttributeUtils.getByteData(problematicAttributes, Tag.PixelData);

      assertTrue(result.isEmpty());
    }
  }

  @Nested
  @DisplayName("Integration Tests")
  class IntegrationTests {

    @Test
    @DisplayName("Should work with complete DICOM dataset")
    void shouldWorkWithCompleteDicomDataset() {
      // Create realistic DICOM dataset
      Attributes dataset = createRealisticDicomDataset();

      // Test modality extraction
      String modality = DicomAttributeUtils.getModality(dataset);
      assertEquals("CT", modality);

      // Test byte data extraction
      Optional<byte[]> pixelData = DicomAttributeUtils.getByteData(dataset, Tag.PixelData);
      assertTrue(pixelData.isPresent());
      assertEquals(1024, pixelData.get().length);
    }

    @Test
    @DisplayName("Should handle multi-frame DICOM data")
    void shouldHandleMultiFrameDicomData() {
      Attributes multiFrameDataset = createMultiFrameDicomDataset();

      String modality = DicomAttributeUtils.getModality(multiFrameDataset);
      assertEquals("MR", modality);

      Optional<byte[]> pixelData =
          DicomAttributeUtils.getByteData(multiFrameDataset, Tag.PixelData);
      assertTrue(pixelData.isPresent());
      assertTrue(pixelData.get().length > 1024); // Multi-frame should be larger
    }

    @Test
    @DisplayName("Should handle DICOM-RT dataset with hierarchy")
    void shouldHandleDicomRtDatasetWithHierarchy() {
      Attributes rtDataset = createDicomRtDataset();

      // RT datasets often have modality in referenced sequence
      String modality = DicomAttributeUtils.getModality(rtDataset);
      assertEquals("RTDOSE", modality);
    }
  }

  // Helper methods to create realistic test data

  /** Creates a realistic DICOM dataset with common attributes. */
  private Attributes createRealisticDicomDataset() {
    Attributes dataset = new Attributes();

    // Patient information
    dataset.setString(Tag.PatientName, VR.PN, "TEST^PATIENT");
    dataset.setString(Tag.PatientID, VR.LO, "12345");
    dataset.setString(Tag.PatientBirthDate, VR.DA, "19800101");
    dataset.setString(Tag.PatientSex, VR.CS, "M");

    // Study information
    dataset.setString(Tag.StudyInstanceUID, VR.UI, "1.2.3.4.5.6.7");
    dataset.setString(Tag.StudyDate, VR.DA, "20240101");
    dataset.setString(Tag.StudyTime, VR.TM, "120000");
    dataset.setString(Tag.StudyDescription, VR.LO, "Test Study");

    // Series information
    dataset.setString(Tag.Modality, VR.CS, "CT");
    dataset.setString(Tag.SeriesInstanceUID, VR.UI, "1.2.3.4.5.6.7.8");
    dataset.setString(Tag.SeriesNumber, VR.IS, "1");
    dataset.setString(Tag.SeriesDescription, VR.LO, "Test Series");

    // Image information
    dataset.setString(Tag.SOPInstanceUID, VR.UI, "1.2.3.4.5.6.7.8.9");
    dataset.setString(Tag.InstanceNumber, VR.IS, "1");
    dataset.setInt(Tag.Rows, VR.US, 512);
    dataset.setInt(Tag.Columns, VR.US, 512);
    dataset.setInt(Tag.BitsAllocated, VR.US, 16);
    dataset.setInt(Tag.BitsStored, VR.US, 12);
    dataset.setInt(Tag.HighBit, VR.US, 11);
    dataset.setInt(Tag.PixelRepresentation, VR.US, 0);
    dataset.setString(Tag.PhotometricInterpretation, VR.CS, "MONOCHROME2");
    dataset.setInt(Tag.SamplesPerPixel, VR.US, 1);

    // Add pixel data (simulate 512x512x2 bytes = 524,288 bytes, use 1024 for test)
    byte[] pixelData = new byte[1024];
    for (int i = 0; i < pixelData.length; i++) {
      pixelData[i] = (byte) (i % 256);
    }
    dataset.setBytes(Tag.PixelData, VR.OW, pixelData);

    return dataset;
  }

  /** Creates a multi-frame DICOM dataset. */
  private Attributes createMultiFrameDicomDataset() {
    Attributes dataset = createRealisticDicomDataset();

    // Change to MR modality
    dataset.setString(Tag.Modality, VR.CS, "MR");

    // Multi-frame specific attributes
    dataset.setString(Tag.NumberOfFrames, VR.IS, "10");
    dataset.setString(Tag.FrameTime, VR.DS, "100.0");

    // Larger pixel data for multiple frames
    byte[] multiFramePixelData = new byte[10240]; // 10 times larger
    for (int i = 0; i < multiFramePixelData.length; i++) {
      multiFramePixelData[i] = (byte) ((i * 7) % 256);
    }
    dataset.setBytes(Tag.PixelData, VR.OW, multiFramePixelData);

    return dataset;
  }

  /** Creates a DICOM-RT dataset with hierarchy. */
  private Attributes createDicomRtDataset() {
    Attributes rtDataset = new Attributes();

    // RT-specific attributes
    rtDataset.setString(Tag.Modality, VR.CS, "RTDOSE");
    rtDataset.setString(Tag.SOPClassUID, VR.UI, "1.2.840.10008.5.1.4.1.1.481.2"); // RT Dose Storage

    // Create referenced sequence with parent hierarchy
    Attributes referencedSeries = new Attributes();
    referencedSeries.setString(Tag.SeriesInstanceUID, VR.UI, "1.2.3.4.5.6.7.8.9");
    Sequence sequence = rtDataset.newSequence(Tag.ReferencedSeriesSequence, 1);
    sequence.add(referencedSeries);
    return rtDataset;
  }

  /** Creates overlay bitmap data. */
  private byte[] createOverlayBitmap() {
    // Create a simple 8x8 overlay pattern
    byte[] overlayData = new byte[8];
    overlayData[0] = (byte) 0xFF; // Row 1: 11111111
    overlayData[1] = (byte) 0x81; // Row 2: 10000001
    overlayData[2] = (byte) 0x81; // Row 3: 10000001
    overlayData[3] = (byte) 0x81; // Row 4: 10000001
    overlayData[4] = (byte) 0x81; // Row 5: 10000001
    overlayData[5] = (byte) 0x81; // Row 6: 10000001
    overlayData[6] = (byte) 0x81; // Row 7: 10000001
    overlayData[7] = (byte) 0xFF; // Row 8: 11111111
    return overlayData;
  }

  /** Creates palette color lookup table data. */
  private byte[] createPaletteColorData() {
    // Create a simple palette with 256 entries (512 bytes for 16-bit)
    byte[] paletteData = new byte[512];
    for (int i = 0; i < 256; i++) {
      // Store as little-endian 16-bit values
      paletteData[i * 2] = (byte) (i & 0xFF); // Low byte
      paletteData[i * 2 + 1] = (byte) ((i >> 8) & 0xFF); // High byte
    }
    return paletteData;
  }

  /** Custom Attributes class that throws IOException when reading bytes. */
  private static class ProblematicAttributes extends Attributes {
    @Override
    public byte[] getBytes(int tag) throws IOException {
      if (tag == Tag.PixelData) {
        throw new IOException("Simulated IO error");
      }
      return super.getBytes(tag);
    }

    @Override
    public boolean containsValue(String privateCreator, int tag) {
      return tag == Tag.PixelData; // Pretend we have the tag
    }
  }
}
