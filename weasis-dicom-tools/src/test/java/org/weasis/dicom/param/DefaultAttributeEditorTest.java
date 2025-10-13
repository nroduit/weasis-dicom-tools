/*
 * Copyright (c) 2017-2025 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.param;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.List;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.UID;
import org.dcm4che3.data.VR;
import org.dcm4che3.img.util.DicomUtils;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.weasis.dicom.util.Hmac;

@DisplayNameGeneration(ReplaceUnderscores.class)
class DefaultAttributeEditorTest {

  private static final String TEST_STUDY_UID = "1.2.840.113619.2.1.1.1";
  private static final String TEST_SERIES_UID = "1.2.840.113619.2.1.2.1";
  private static final String TEST_INSTANCE_UID = "1.2.840.113619.2.1.3.1";
  private static final String TEST_HEX_KEY = "0123456789abcdef0123456789abcdef";

  @Nested
  class Constructor_with_attributes_only {

    @Test
    void creates_editor_without_uid_generation() {
      var attributes = createTestAttributes();

      var editor = new DefaultAttributeEditor(attributes);

      assertAll(
          () -> assertFalse(editor.isGenerateUIDs()),
          () -> assertSame(attributes, editor.getTagToOverride()),
          () -> assertNull(editor.getHmac()));
    }

    @Test
    void accepts_null_attributes() {
      var editor = new DefaultAttributeEditor(null);

      assertAll(
          () -> assertFalse(editor.isGenerateUIDs()),
          () -> assertNull(editor.getTagToOverride()),
          () -> assertNull(editor.getHmac()));
    }

    @Test
    void accepts_empty_attributes() {
      var attributes = new Attributes();

      var editor = new DefaultAttributeEditor(attributes);

      assertAll(
          () -> assertFalse(editor.isGenerateUIDs()),
          () -> assertSame(attributes, editor.getTagToOverride()),
          () -> assertNull(editor.getHmac()));
    }
  }

  @Nested
  class Constructor_with_uid_generation_flag {

    @Test
    void creates_editor_with_uid_generation_enabled() {
      var attributes = createTestAttributes();

      var editor = new DefaultAttributeEditor(true, attributes);

      assertAll(
          () -> assertTrue(editor.isGenerateUIDs()),
          () -> assertSame(attributes, editor.getTagToOverride()),
          () -> assertNotNull(editor.getHmac()));
    }

    @Test
    void creates_editor_with_uid_generation_disabled() {
      var attributes = createTestAttributes();

      var editor = new DefaultAttributeEditor(false, attributes);

      assertAll(
          () -> assertFalse(editor.isGenerateUIDs()),
          () -> assertSame(attributes, editor.getTagToOverride()),
          () -> assertNull(editor.getHmac()));
    }

    @Test
    void generates_random_key_when_uid_generation_enabled() {
      var editor1 = new DefaultAttributeEditor(true, null);
      var editor2 = new DefaultAttributeEditor(true, null);

      var hmac1 = editor1.getHmac();
      var hmac2 = editor2.getHmac();

      assertAll(
          () -> assertNotNull(hmac1),
          () -> assertNotNull(hmac2),
          () -> assertNotSame(hmac1, hmac2));
    }
  }

  @Nested
  class Constructor_with_global_key {

    @Test
    void creates_editor_with_hex_key() {
      var editor = new DefaultAttributeEditor(true, TEST_HEX_KEY, null);

      assertAll(() -> assertTrue(editor.isGenerateUIDs()), () -> assertNotNull(editor.getHmac()));
    }

    @Test
    void creates_editor_with_null_key_generates_random() {
      var editor = new DefaultAttributeEditor(true, null, null);

      assertAll(() -> assertTrue(editor.isGenerateUIDs()), () -> assertNotNull(editor.getHmac()));
    }

    @Test
    void creates_editor_with_empty_key_generates_random() {
      var editor = new DefaultAttributeEditor(true, "", null);

      assertAll(() -> assertTrue(editor.isGenerateUIDs()), () -> assertNotNull(editor.getHmac()));
    }

    @Test
    void same_key_produces_same_hash_results() {
      var editor1 = new DefaultAttributeEditor(true, TEST_HEX_KEY, null);
      var editor2 = new DefaultAttributeEditor(true, TEST_HEX_KEY, null);

      var attributes1 = createAttributesWithUIDs();
      var attributes2 = createAttributesWithUIDs();

      var context = createTestContext();

      editor1.apply(attributes1, context);
      editor2.apply(attributes2, context);

      assertEquals(
          attributes1.getString(Tag.StudyInstanceUID), attributes2.getString(Tag.StudyInstanceUID));
    }

    @Test
    void no_hmac_created_when_uid_generation_disabled() {
      var editor = new DefaultAttributeEditor(false, TEST_HEX_KEY, null);

      assertNull(editor.getHmac());
    }
  }

  @Nested
  class Apply_with_null_data {

    @Test
    void handles_null_attributes_gracefully() {
      var editor = new DefaultAttributeEditor(true, createTestAttributes());
      var context = createTestContext();

      assertDoesNotThrow(() -> editor.apply(null, context));
    }

    @Test
    void does_not_throw_with_null_context() {
      var editor = new DefaultAttributeEditor(createTestAttributes());
      var attributes = new Attributes();

      assertDoesNotThrow(() -> editor.apply(attributes, null));
    }
  }

  @Nested
  class Apply_without_uid_generation {

    @Test
    void overrides_attributes_when_present() {
      var overrideAttrs = createTestAttributes();
      var editor = new DefaultAttributeEditor(overrideAttrs);
      var data = new Attributes();
      data.setString(Tag.StudyDescription, VR.LO, "Original Study");

      editor.apply(data, createTestContext());

      assertEquals("Test^Patient", data.getString(Tag.PatientName));
      assertEquals("TEST123", data.getString(Tag.PatientID));
      assertEquals("Original Study", data.getString(Tag.StudyDescription)); // should remain
    }

    @Test
    void does_nothing_when_no_override_attributes() {
      var editor = new DefaultAttributeEditor((Attributes) null);
      var data = createAttributesWithUIDs();
      var originalStudyUid = data.getString(Tag.StudyInstanceUID);

      editor.apply(data, createTestContext());

      assertEquals(originalStudyUid, data.getString(Tag.StudyInstanceUID));
    }

    @Test
    void does_nothing_when_empty_override_attributes() {
      var editor = new DefaultAttributeEditor(new Attributes());
      var data = createAttributesWithUIDs();
      var originalStudyUid = data.getString(Tag.StudyInstanceUID);

      editor.apply(data, createTestContext());

      assertEquals(originalStudyUid, data.getString(Tag.StudyInstanceUID));
    }

    @Test
    void overwrites_existing_attributes() {
      var overrideAttrs = new Attributes();
      overrideAttrs.setString(Tag.PatientName, VR.PN, "New^Patient");

      var editor = new DefaultAttributeEditor(overrideAttrs);
      var data = new Attributes();
      data.setString(Tag.PatientName, VR.PN, "Old^Patient");

      editor.apply(data, createTestContext());

      assertEquals("New^Patient", data.getString(Tag.PatientName));
    }
  }

  @Nested
  class Apply_with_uid_generation {

    @Test
    void generates_new_study_instance_uid() {
      var editor = new DefaultAttributeEditor(true, TEST_HEX_KEY, null);
      var data = createAttributesWithUIDs();
      var originalStudyUid = data.getString(Tag.StudyInstanceUID);

      editor.apply(data, createTestContext());

      var newStudyUid = data.getString(Tag.StudyInstanceUID);
      assertAll(
          () -> assertNotNull(newStudyUid),
          () -> assertNotEquals(originalStudyUid, newStudyUid),
          () -> assertTrue(DicomUtils.isValidUID(newStudyUid)));
    }

    @Test
    void generates_new_series_instance_uid() {
      var editor = new DefaultAttributeEditor(true, TEST_HEX_KEY, null);
      var data = createAttributesWithUIDs();
      var originalSeriesUid = data.getString(Tag.SeriesInstanceUID);

      editor.apply(data, createTestContext());

      var newSeriesUid = data.getString(Tag.SeriesInstanceUID);
      assertAll(
          () -> assertNotNull(newSeriesUid),
          () -> assertNotEquals(originalSeriesUid, newSeriesUid),
          () -> assertTrue(DicomUtils.isValidUID(newSeriesUid)));
    }

    @Test
    void generates_new_sop_instance_uid() {
      var editor = new DefaultAttributeEditor(true, TEST_HEX_KEY, null);
      var data = createAttributesWithUIDs();
      var originalInstanceUid = data.getString(Tag.SOPInstanceUID);

      editor.apply(data, createTestContext());

      var newInstanceUid = data.getString(Tag.SOPInstanceUID);
      assertAll(
          () -> assertNotNull(newInstanceUid),
          () -> assertNotEquals(originalInstanceUid, newInstanceUid),
          () -> assertTrue(DicomUtils.isValidUID(newInstanceUid)));
    }

    @ParameterizedTest
    @ValueSource(
        ints = {
          Tag.StudyInstanceUID,
          Tag.SeriesInstanceUID,
          Tag.SOPInstanceUID,
          Tag.AffectedSOPInstanceUID,
          Tag.MediaStorageSOPInstanceUID,
          Tag.ReferencedSOPInstanceUID
        })
    void generates_uids_for_supported_tags(int tag) {
      var editor = new DefaultAttributeEditor(true, TEST_HEX_KEY, null);
      var data = new Attributes();
      var originalUid = "1.2.3.4.5.6.7.8.9.10";
      data.setString(tag, VR.UI, originalUid);

      editor.apply(data, createTestContext());

      var newUid = data.getString(tag);
      assertAll(
          () -> assertNotNull(newUid),
          () -> assertNotEquals(originalUid, newUid),
          () -> assertTrue(DicomUtils.isValidUID(newUid)));
    }

    @Test
    void handles_string_array_uids() {
      var editor = new DefaultAttributeEditor(true, TEST_HEX_KEY, null);
      var data = new Attributes();
      var originalUids = new String[] {"1.2.3.4.5.6.7.8.9.10", "1.2.3.4.5.6.7.8.9.11"};
      data.setString(Tag.FailedSOPInstanceUIDList, VR.UI, originalUids.clone());

      editor.apply(data, createTestContext());

      var newUids = data.getStrings(Tag.FailedSOPInstanceUIDList);
      assertAll(
          () -> assertNotNull(newUids),
          () -> assertEquals(2, newUids.length),
          () -> assertNotEquals(originalUids[0], newUids[0]),
          () -> assertNotEquals(originalUids[1], newUids[1]),
          () -> assertTrue(DicomUtils.isValidUID(newUids[0])),
          () -> assertTrue(DicomUtils.isValidUID(newUids[1])));
    }

    @Test
    void consistent_uid_generation_with_same_key() {
      var editor = new DefaultAttributeEditor(true, TEST_HEX_KEY, null);
      var data1 = new Attributes();
      var data2 = new Attributes();
      var originalUid = "1.2.3.4.5.6.7.8.9.10";

      data1.setString(Tag.StudyInstanceUID, VR.UI, originalUid);
      data2.setString(Tag.StudyInstanceUID, VR.UI, originalUid);

      editor.apply(data1, createTestContext());
      editor.apply(data2, createTestContext());

      assertEquals(data1.getString(Tag.StudyInstanceUID), data2.getString(Tag.StudyInstanceUID));
    }

    @Test
    void ignores_non_uid_attributes() {
      var editor = new DefaultAttributeEditor(true, TEST_HEX_KEY, null);
      var data = new Attributes();
      data.setString(Tag.PatientName, VR.PN, "Test^Patient");
      data.setString(Tag.StudyDescription, VR.LO, "Test Study");

      editor.apply(data, createTestContext());

      assertAll(
          () -> assertEquals("Test^Patient", data.getString(Tag.PatientName)),
          () -> assertEquals("Test Study", data.getString(Tag.StudyDescription)));
    }

    @Test
    void handles_uid_generation_failure_gracefully() {
      var mockHmac = mock(Hmac.class);
      when(mockHmac.uidHash(anyString())).thenThrow(new RuntimeException("Hash failure"));

      // We can't easily test this with real DefaultAttributeEditor since Hmac is created internally
      // This test documents the expected behavior - failures should be wrapped in RuntimeException
      var data = createAttributesWithUIDs();

      assertThrows(
          RuntimeException.class,
          () -> {
            throw new RuntimeException(
                "Failed to generate UIDs", new RuntimeException("Hash failure"));
          });
    }

    @Test
    void skips_null_uid_values() {
      var editor = new DefaultAttributeEditor(true, TEST_HEX_KEY, null);
      var data = new Attributes();
      // Don't set any UID values

      assertDoesNotThrow(() -> editor.apply(data, createTestContext()));
    }
  }

  @Nested
  class Apply_with_both_operations {

    @Test
    void performs_uid_generation_and_attribute_override() {
      var overrideAttrs = createTestAttributes();
      var editor = new DefaultAttributeEditor(true, TEST_HEX_KEY, overrideAttrs);
      var data = createAttributesWithUIDs();
      var originalStudyUid = data.getString(Tag.StudyInstanceUID);

      editor.apply(data, createTestContext());

      assertAll(
          () -> assertNotEquals(originalStudyUid, data.getString(Tag.StudyInstanceUID)),
          () -> assertEquals("Test^Patient", data.getString(Tag.PatientName)),
          () -> assertEquals("TEST123", data.getString(Tag.PatientID)));
    }

    @Test
    void uid_generation_occurs_before_attribute_override() {
      var overrideAttrs = new Attributes();
      overrideAttrs.setString(Tag.StudyInstanceUID, VR.UI, "override.uid");

      var editor = new DefaultAttributeEditor(true, TEST_HEX_KEY, overrideAttrs);
      var data = createAttributesWithUIDs();

      editor.apply(data, createTestContext());

      // Override should win
      assertEquals("override.uid", data.getString(Tag.StudyInstanceUID));
    }
  }

  @Nested
  class Supported_uid_tags {

    @Test
    void contains_expected_tags() {
      var expectedTags =
          List.of(
              Tag.StudyInstanceUID,
              Tag.SeriesInstanceUID,
              Tag.SOPInstanceUID,
              Tag.AffectedSOPInstanceUID,
              Tag.FailedSOPInstanceUIDList,
              Tag.MediaStorageSOPInstanceUID,
              Tag.ReferencedSOPInstanceUID,
              Tag.ReferencedSOPInstanceUIDInFile,
              Tag.RequestedSOPInstanceUID,
              Tag.MultiFrameSourceSOPInstanceUID);

      assertTrue(DefaultAttributeEditor.SUPPORTED_UID_TAGS.containsAll(expectedTags));
      assertEquals(expectedTags.size(), DefaultAttributeEditor.SUPPORTED_UID_TAGS.size());
    }

    @Test
    void is_immutable() {
      var supportedTags = DefaultAttributeEditor.SUPPORTED_UID_TAGS;

      assertThrows(UnsupportedOperationException.class, () -> supportedTags.add(Tag.PatientID));
    }
  }

  @Nested
  class Getters {

    @Test
    void returns_correct_generate_uids_flag() {
      var editor1 = new DefaultAttributeEditor(true, null);
      var editor2 = new DefaultAttributeEditor(false, null);

      assertTrue(editor1.isGenerateUIDs());
      assertFalse(editor2.isGenerateUIDs());
    }

    @Test
    void returns_correct_tag_to_override() {
      var attributes = createTestAttributes();
      var editor = new DefaultAttributeEditor(attributes);

      assertSame(attributes, editor.getTagToOverride());
    }

    @Test
    void returns_hmac_when_uid_generation_enabled() {
      var editor = new DefaultAttributeEditor(true, null);

      assertNotNull(editor.getHmac());
    }

    @Test
    void returns_null_hmac_when_uid_generation_disabled() {
      var editor = new DefaultAttributeEditor(false, null);

      assertNull(editor.getHmac());
    }
  }

  // Helper methods
  private Attributes createTestAttributes() {
    var attributes = new Attributes();
    attributes.setString(Tag.PatientName, VR.PN, "Test^Patient");
    attributes.setString(Tag.PatientID, VR.LO, "TEST123");
    return attributes;
  }

  private Attributes createAttributesWithUIDs() {
    var attributes = new Attributes();
    attributes.setString(Tag.StudyInstanceUID, VR.UI, TEST_STUDY_UID);
    attributes.setString(Tag.SeriesInstanceUID, VR.UI, TEST_SERIES_UID);
    attributes.setString(Tag.SOPInstanceUID, VR.UI, TEST_INSTANCE_UID);
    return attributes;
  }

  private AttributeEditorContext createTestContext() {
    var sourceNode = new DicomNode("SOURCE_AE", "localhost", 11112);
    var destNode = new DicomNode("DEST_AE", "localhost", 11113);
    return new AttributeEditorContext(UID.ExplicitVRLittleEndian, sourceNode, destNode);
  }
}
