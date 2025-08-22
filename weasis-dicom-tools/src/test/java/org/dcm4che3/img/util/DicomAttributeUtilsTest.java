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
import static org.mockito.Mockito.*;

import java.io.IOException;
import java.util.stream.Stream;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.VR;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

@DisplayNameGeneration(ReplaceUnderscores.class)
class DicomAttributeUtilsTest {

  private Attributes rootAttributes;
  private Attributes childAttributes;
  private Attributes grandChildAttributes;

  @BeforeEach
  void setUp() {
    createAttributeHierarchy();
  }

  @Nested
  class Modality_Extraction {

    @Test
    void should_return_null_for_null_attributes() {
      assertNull(DicomAttributeUtils.getModality(null));
    }

    @Test
    void should_extract_modality_from_current_attributes() {
      rootAttributes.setString(Tag.Modality, VR.CS, "CT");

      var modality = DicomAttributeUtils.getModality(rootAttributes);

      assertEquals("CT", modality);
    }

    @ParameterizedTest
    @ValueSource(strings = {"CT", "MR", "US", "XA", "RF", "DX", "CR", "MG", "PT", "NM"})
    void should_handle_various_modality_types(String modalityType) {
      rootAttributes.setString(Tag.Modality, VR.CS, modalityType);

      var result = DicomAttributeUtils.getModality(rootAttributes);

      assertEquals(modalityType, result);
    }

    @Test
    void should_find_modality_in_parent_when_not_in_child() {
      rootAttributes.setString(Tag.Modality, VR.CS, "MR");

      var modality = DicomAttributeUtils.getModality(childAttributes);

      assertEquals("MR", modality);
    }

    @Test
    void should_find_modality_in_grandparent_hierarchy() {
      rootAttributes.setString(Tag.Modality, VR.CS, "US");

      var modality = DicomAttributeUtils.getModality(grandChildAttributes);

      assertEquals("US", modality);
    }

    @Test
    void should_prefer_child_modality_over_parent() {
      rootAttributes.setString(Tag.Modality, VR.CS, "CT");
      childAttributes.setString(Tag.Modality, VR.CS, "MR");

      var modality = DicomAttributeUtils.getModality(childAttributes);

      assertEquals("MR", modality);
    }

    @Test
    void should_return_null_when_no_modality_in_hierarchy() {
      var modality = DicomAttributeUtils.getModality(grandChildAttributes);

      assertNull(modality);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "  "})
    void should_handle_empty_or_whitespace_modality_string(String emptyValue) {
      rootAttributes.setString(Tag.Modality, VR.CS, emptyValue);

      var modality = DicomAttributeUtils.getModality(rootAttributes);

      assertNull(modality);
    }

    @Test
    void should_handle_modality_with_multiple_values() {
      rootAttributes.setString(Tag.Modality, VR.CS, "CT", "MR");

      var modality = DicomAttributeUtils.getModality(rootAttributes);

      assertEquals("CT", modality);
    }
  }

  @Nested
  class Byte_Data_Extraction {

    @Test
    void should_return_empty_optional_for_null_attributes() {
      var result = DicomAttributeUtils.getByteData(null, Tag.PixelData);

      assertTrue(result.isEmpty());
    }

    @Test
    void should_return_empty_optional_for_non_existent_tag() {
      var result = DicomAttributeUtils.getByteData(rootAttributes, Tag.PixelData);

      assertTrue(result.isEmpty());
    }

    @Test
    void should_extract_byte_data_from_standard_tag() {
      var expectedData = new byte[] {0x01, 0x02, 0x03, 0x04, 0x05};
      rootAttributes.setBytes(Tag.PixelData, VR.OW, expectedData);

      var result = DicomAttributeUtils.getByteData(rootAttributes, Tag.PixelData);

      assertTrue(result.isPresent());
      assertArrayEquals(expectedData, result.get());
    }

    @Test
    void should_extract_byte_data_with_private_creator() {
      var expectedData = new byte[] {(byte) 0xFF, (byte) 0xFE, 0x00, 0x01};
      var privateCreator = "TEST_CREATOR";
      var privateTag = 0x00111001;

      rootAttributes.setString(0x00110010, VR.LO, privateCreator);
      rootAttributes.setBytes(privateCreator, privateTag, VR.OB, expectedData);

      var result = DicomAttributeUtils.getByteData(rootAttributes, privateCreator, privateTag);

      assertTrue(result.isPresent());
      assertArrayEquals(expectedData, result.get());
    }

    @Test
    void should_return_empty_for_empty_byte_array() {
      var emptyData = new byte[0];
      rootAttributes.setBytes(Tag.PixelData, VR.OW, emptyData);

      var result = DicomAttributeUtils.getByteData(rootAttributes, Tag.PixelData);

      assertTrue(result.isEmpty());
    }

    @ParameterizedTest
    @MethodSource("provideLargeByteArrays")
    void should_handle_various_sized_byte_arrays(byte[] testData, int expectedLength) {
      rootAttributes.setBytes(Tag.PixelData, VR.OB, testData);

      var result = DicomAttributeUtils.getByteData(rootAttributes, Tag.PixelData);

      assertTrue(result.isPresent());
      assertEquals(expectedLength, result.get().length);
      assertArrayEquals(testData, result.get());
    }

    @ParameterizedTest
    @MethodSource("provideDifferentVrTypes")
    void should_extract_different_vr_types_as_bytes(int tag, VR vr, byte[] data) {
      rootAttributes.setBytes(tag, vr, data);

      var result = DicomAttributeUtils.getByteData(rootAttributes, tag);

      assertTrue(result.isPresent());
      assertArrayEquals(data, result.get());
    }

    @Test
    void should_handle_null_value_tag() {
      rootAttributes.setNull(Tag.PixelData, VR.OW);

      var result = DicomAttributeUtils.getByteData(rootAttributes, Tag.PixelData);

      assertTrue(result.isEmpty());
    }

    @Test
    void should_handle_io_exception_gracefully() throws IOException {
      var attributes = spy(new Attributes());
      doReturn(true).when(attributes).containsValue(null, Tag.PixelData);
      doThrow(new IOException("Simulated IO error")).when(attributes).getBytes(Tag.PixelData);

      var result = DicomAttributeUtils.getByteData(attributes, Tag.PixelData);

      assertTrue(result.isEmpty());
    }

    @Test
    void should_work_with_null_private_creator() {
      var data = new byte[] {0x01, 0x02, 0x03, 0x04};
      var privateTag = 0x00111001;
      rootAttributes.setBytes(privateTag, VR.OB, data);

      var result = DicomAttributeUtils.getByteData(rootAttributes, null, privateTag);

      assertTrue(result.isPresent());
      assertArrayEquals(data, result.get());
    }

    static Stream<Arguments> provideLargeByteArrays() {
      return Stream.of(
          Arguments.of(createPatternedByteArray(1024), 1024),
          Arguments.of(createPatternedByteArray(10240), 10240),
          Arguments.of(createRandomByteArray(65536), 65536));
    }

    static Stream<Arguments> provideDifferentVrTypes() {
      return Stream.of(
          Arguments.of(Tag.PixelData, VR.OW, new byte[] {0x01, 0x02, 0x03, 0x04}),
          Arguments.of(
              Tag.RedPaletteColorLookupTableData,
              VR.OB,
              new byte[] {(byte) 0xFF, (byte) 0xFE, 0x00, 0x01}),
          Arguments.of(Tag.OverlayData, VR.OB, createOverlayData()),
          Arguments.of(Tag.BluePaletteColorLookupTableData, VR.OW, createPaletteData()));
    }
  }

  @Nested
  class Integration_Tests {

    @Test
    void should_work_with_realistic_ct_dataset() {
      var dataset = createCtDataset();

      var modality = DicomAttributeUtils.getModality(dataset);
      var pixelData = DicomAttributeUtils.getByteData(dataset, Tag.PixelData);

      assertEquals("CT", modality);
      assertTrue(pixelData.isPresent());
      assertEquals(1024, pixelData.get().length);
    }

    @Test
    void should_handle_multi_frame_mr_dataset() {
      var dataset = createMultiFrameMrDataset();

      var modality = DicomAttributeUtils.getModality(dataset);
      var pixelData = DicomAttributeUtils.getByteData(dataset, Tag.PixelData);

      assertEquals("MR", modality);
      assertTrue(pixelData.isPresent());
      assertEquals(10240, pixelData.get().length);
    }

    @Test
    void should_handle_rt_dose_hierarchy() {
      var rtDataset = createRtDoseDataset();

      var modality = DicomAttributeUtils.getModality(rtDataset);

      assertEquals("RTDOSE", modality);
    }

    @Test
    void should_handle_comprehensive_dicom_workflow() {
      var studyDataset = createStudyDataset();
      var seriesDataset = createSeriesDataset();
      var imageDataset = createImageDataset();

      // Test hierarchy traversal
      assertEquals("CT", DicomAttributeUtils.getModality(studyDataset));
      assertEquals("CT", DicomAttributeUtils.getModality(seriesDataset));
      assertEquals("CT", DicomAttributeUtils.getModality(imageDataset));

      // Test data extraction
      var overlayData = DicomAttributeUtils.getByteData(imageDataset, Tag.OverlayData);
      var pixelData = DicomAttributeUtils.getByteData(imageDataset, Tag.PixelData);

      assertTrue(overlayData.isPresent());
      assertTrue(pixelData.isPresent());
    }
  }

  private void createAttributeHierarchy() {
    rootAttributes = new Attributes();
    childAttributes = new Attributes();
    grandChildAttributes = new Attributes();

    var sequence = rootAttributes.newSequence(Tag.RelatedSeriesSequence, 1);
    sequence.add(childAttributes);
    var sequence2 = childAttributes.newSequence(Tag.PurposeOfReferenceCodeSequence, 1);
    sequence2.add(grandChildAttributes);
  }

  private Attributes createCtDataset() {
    var dataset = new Attributes();
    setBasicPatientInfo(dataset);
    setBasicStudyInfo(dataset);
    dataset.setString(Tag.Modality, VR.CS, "CT");
    dataset.setBytes(Tag.PixelData, VR.OW, createPatternedByteArray(1024));
    return dataset;
  }

  private Attributes createMultiFrameMrDataset() {
    var dataset = createCtDataset();
    dataset.setString(Tag.Modality, VR.CS, "MR");
    dataset.setString(Tag.NumberOfFrames, VR.IS, "10");
    dataset.setString(Tag.FrameTime, VR.DS, "100.0");
    dataset.setBytes(Tag.PixelData, VR.OW, createPatternedByteArray(10240));
    return dataset;
  }

  private Attributes createRtDoseDataset() {
    var rtDataset = new Attributes();
    rtDataset.setString(Tag.Modality, VR.CS, "RTDOSE");
    rtDataset.setString(Tag.SOPClassUID, VR.UI, "1.2.840.10008.5.1.4.1.1.481.2");

    var referencedSeries = new Attributes();
    referencedSeries.setString(Tag.SeriesInstanceUID, VR.UI, "1.2.3.4.5.6.7.8.9");
    var sequence = rtDataset.newSequence(Tag.ReferencedSeriesSequence, 1);
    sequence.add(referencedSeries);
    return rtDataset;
  }

  private Attributes createStudyDataset() {
    var dataset = new Attributes();
    setBasicPatientInfo(dataset);
    setBasicStudyInfo(dataset);
    dataset.setString(Tag.Modality, VR.CS, "CT");
    return dataset;
  }

  private Attributes createSeriesDataset() {
    var dataset = createStudyDataset();
    dataset.setString(Tag.SeriesInstanceUID, VR.UI, "1.2.3.4.5.6.7.8");
    dataset.setString(Tag.SeriesNumber, VR.IS, "1");
    return dataset;
  }

  private Attributes createImageDataset() {
    var dataset = createSeriesDataset();
    dataset.setString(Tag.SOPInstanceUID, VR.UI, "1.2.3.4.5.6.7.8.9");
    dataset.setString(Tag.InstanceNumber, VR.IS, "1");
    dataset.setInt(Tag.Rows, VR.US, 512);
    dataset.setInt(Tag.Columns, VR.US, 512);
    dataset.setBytes(Tag.PixelData, VR.OW, createPatternedByteArray(2048));
    dataset.setBytes(Tag.OverlayData, VR.OB, createOverlayData());
    return dataset;
  }

  private void setBasicPatientInfo(Attributes dataset) {
    dataset.setString(Tag.PatientName, VR.PN, "TEST^PATIENT");
    dataset.setString(Tag.PatientID, VR.LO, "12345");
    dataset.setString(Tag.PatientBirthDate, VR.DA, "19800101");
    dataset.setString(Tag.PatientSex, VR.CS, "M");
  }

  private void setBasicStudyInfo(Attributes dataset) {
    dataset.setString(Tag.StudyInstanceUID, VR.UI, "1.2.3.4.5.6.7");
    dataset.setString(Tag.StudyDate, VR.DA, "20240101");
    dataset.setString(Tag.StudyTime, VR.TM, "120000");
    dataset.setString(Tag.StudyDescription, VR.LO, "Test Study");
  }

  private static byte[] createPatternedByteArray(int size) {
    var data = new byte[size];
    for (var i = 0; i < size; i++) {
      data[i] = (byte) (i % 256);
    }
    return data;
  }

  private static byte[] createRandomByteArray(int size) {
    var data = new byte[size];
    for (var i = 0; i < size; i++) {
      data[i] = (byte) ((i * 7 + 13) % 256);
    }
    return data;
  }

  private static byte[] createOverlayData() {
    return new byte[] {
      (byte) 0xFF, (byte) 0x81, (byte) 0x81, (byte) 0x81,
      (byte) 0x81, (byte) 0x81, (byte) 0x81, (byte) 0xFF
    };
  }

  private static byte[] createPaletteData() {
    var paletteData = new byte[512];
    for (var i = 0; i < 256; i++) {
      paletteData[i * 2] = (byte) (i & 0xFF);
      paletteData[i * 2 + 1] = (byte) ((i >> 8) & 0xFF);
    }
    return paletteData;
  }
}
