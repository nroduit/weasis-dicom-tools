/*
 * Copyright (c) 2014-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.dcm4che3.tool.common;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.VR;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@DisplayNameGeneration(ReplaceUnderscores.class)
class CLIUtilsTest {

  @Nested
  class Properties_Loading {

    @Test
    void load_properties_with_null_properties_creates_new_instance(@TempDir Path tempDir)
        throws IOException {
      // Given
      var propertiesFile = tempDir.resolve("test.properties");
      var propertiesContent = "key1=value1\nkey2=value2\n";
      Files.writeString(propertiesFile, propertiesContent);

      // When
      var result = CLIUtils.loadProperties(propertiesFile.toString(), null);

      // Then
      assertNotNull(result);
      assertEquals("value1", result.getProperty("key1"));
      assertEquals("value2", result.getProperty("key2"));
    }

    @Test
    void load_properties_with_existing_properties_merges_values(@TempDir Path tempDir)
        throws IOException {
      // Given
      var existingProps = new Properties();
      existingProps.setProperty("existing", "originalValue");

      var propertiesFile = tempDir.resolve("test.properties");
      var propertiesContent = "key1=value1\nexisting=newValue\n";
      Files.writeString(propertiesFile, propertiesContent);

      // When
      var result = CLIUtils.loadProperties(propertiesFile.toString(), existingProps);

      // Then
      assertSame(existingProps, result);
      assertEquals("value1", result.getProperty("key1"));
      assertEquals("newValue", result.getProperty("existing"));
    }

    @Test
    void load_properties_throws_IOException_for_nonexistent_file() {
      // Given
      String nonexistentFile = "/nonexistent/file.properties";

      // When & Then
      assertThrows(IOException.class, () -> CLIUtils.loadProperties(nonexistentFile, null));
    }

    @Test
    void load_properties_handles_empty_file(@TempDir Path tempDir) throws IOException {
      // Given
      var emptyFile = tempDir.resolve("empty.properties");
      Files.writeString(emptyFile, "");

      // When
      var result = CLIUtils.loadProperties(emptyFile.toString(), null);

      // Then
      assertNotNull(result);
      assertTrue(result.isEmpty());
    }
  }

  @Nested
  class Tag_Conversion {

    @Test
    void to_tags_converts_array_of_hex_strings() {
      // Given
      var tagStrings = new String[] {"0008", "0010", "0020"};

      // When
      var result = CLIUtils.toTags(tagStrings);

      // Then
      assertArrayEquals(new int[] {0x0008, 0x0010, 0x0020}, result);
    }

    @Test
    void to_tags_converts_mixed_hex_and_keywords() {
      // Given
      var tagOrKeywords = new String[] {"00080020", "PatientName", "StudyInstanceUID"};

      // When
      var result = CLIUtils.toTags(tagOrKeywords);

      // Then
      assertEquals(3, result.length);
      assertEquals(Tag.StudyDate, result[0]);
      assertEquals(Tag.PatientName, result[1]);
      assertEquals(Tag.StudyInstanceUID, result[2]);
    }

    @Test
    void to_tags_handles_empty_array() {
      // Given
      var emptyArray = new String[0];

      // When
      var result = CLIUtils.toTags(emptyArray);

      // Then
      assertEquals(0, result.length);
    }

    @Test
    void to_tag_converts_hex_string() {
      // When
      var result = CLIUtils.toTag("00100010");

      // Then
      assertEquals(Tag.PatientName, result);
    }

    @Test
    void to_tag_converts_keyword() {
      // When
      var result = CLIUtils.toTag("PatientName");

      // Then
      assertEquals(Tag.PatientName, result);
    }

    @Test
    void to_tag_throws_exception_for_invalid_hex() {
      // When & Then
      var exception = assertThrows(IllegalArgumentException.class, () -> CLIUtils.toTag("ZZZZ"));
      assertTrue(exception.getMessage().contains("Unknown tag or keyword"));
    }

    @Test
    void to_tag_throws_exception_for_unknown_keyword() {
      // When & Then
      var exception =
          assertThrows(IllegalArgumentException.class, () -> CLIUtils.toTag("UnknownKeyword"));
      assertTrue(exception.getMessage().contains("Unknown tag or keyword"));
    }
  }

  @Nested
  class Attribute_Manipulation {

    @Test
    void add_attributes_with_single_tag_and_value() {
      // Given
      var attrs = new Attributes();
      var tags = new int[] {Tag.PatientName};
      var value = "John^Doe";

      // When
      CLIUtils.addAttributes(attrs, tags, value);

      // Then
      assertEquals(value, attrs.getString(Tag.PatientName));
    }

    @Test
    void add_attributes_with_multiple_values() {
      // Given
      var attrs = new Attributes();
      var tags = new int[] {Tag.ImageType};
      var values = new String[] {"ORIGINAL", "PRIMARY"};

      // When
      CLIUtils.addAttributes(attrs, tags, values);

      // Then
      var result = attrs.getStrings(Tag.ImageType);
      assertArrayEquals(values, result);
    }

    @Test
    void add_attributes_with_empty_values_creates_null_attribute() {
      // Given
      var attrs = new Attributes();
      var tags = new int[] {Tag.PatientName};

      // When
      CLIUtils.addAttributes(attrs, tags);

      // Then
      assertTrue(attrs.contains(Tag.PatientName));
      assertNull(attrs.getString(Tag.PatientName));
    }

    @Test
    void add_attributes_with_sequence_path() {
      // Given
      var attrs = new Attributes();
      var tags = new int[] {Tag.ReferencedImageSequence, Tag.ReferencedSOPInstanceUID};
      var value = "1.2.3.4.5";

      // When
      CLIUtils.addAttributes(attrs, tags, value);

      // Then
      var sequence = attrs.getSequence(Tag.ReferencedImageSequence);
      assertNotNull(sequence);
      assertEquals(1, sequence.size());
      assertEquals(value, sequence.get(0).getString(Tag.ReferencedSOPInstanceUID));
    }

    @Test
    void add_attributes_with_empty_tag_array_does_nothing() {
      // Given
      var attrs = new Attributes();
      var emptyTags = new int[0];

      // When
      CLIUtils.addAttributes(attrs, emptyTags, "value");

      // Then
      assertTrue(attrs.isEmpty());
    }

    @Test
    void add_attributes_from_option_values_with_equals_delimiter() {
      // Given
      var attrs = new Attributes();
      var optVals = new String[] {"PatientName=John^Doe", "StudyDate=20231201"};

      // When
      CLIUtils.addAttributes(attrs, optVals);

      // Then
      assertEquals("John^Doe", attrs.getString(Tag.PatientName));
      assertEquals("20231201", attrs.getString(Tag.StudyDate));
    }

    @Test
    void add_attributes_from_option_values_without_equals_creates_empty() {
      // Given
      var attrs = new Attributes();
      var optVals = new String[] {"PatientName", "StudyDate"};

      // When
      CLIUtils.addAttributes(attrs, optVals);

      // Then
      assertTrue(attrs.contains(Tag.PatientName));
      assertTrue(attrs.contains(Tag.StudyDate));
      assertNull(attrs.getString(Tag.PatientName));
      assertNull(attrs.getString(Tag.StudyDate));
    }

    @Test
    void add_attributes_from_null_option_values_does_nothing() {
      // Given
      var attrs = new Attributes();

      // When
      CLIUtils.addAttributes(attrs, (String[]) null);

      // Then
      assertTrue(attrs.isEmpty());
    }

    @Test
    void add_attributes_with_sequence_path_in_option_value() {
      // Given
      var attrs = new Attributes();
      var optVals = new String[] {"ReferencedImageSequence/ReferencedSOPInstanceUID=1.2.3.4.5"};

      // When
      CLIUtils.addAttributes(attrs, optVals);

      // Then
      var sequence = attrs.getSequence(Tag.ReferencedImageSequence);
      assertNotNull(sequence);
      assertEquals("1.2.3.4.5", sequence.get(0).getString(Tag.ReferencedSOPInstanceUID));
    }

    @Test
    void add_empty_attributes_creates_null_values() {
      // Given
      var attrs = new Attributes();
      var optVals = new String[] {"PatientName", "StudyDate"};

      // When
      CLIUtils.addEmptyAttributes(attrs, optVals);

      // Then
      assertTrue(attrs.contains(Tag.PatientName));
      assertTrue(attrs.contains(Tag.StudyDate));
      assertNull(attrs.getString(Tag.PatientName));
      assertNull(attrs.getString(Tag.StudyDate));
    }

    @Test
    void add_empty_attributes_with_null_array_does_nothing() {
      // Given
      var attrs = new Attributes();

      // When
      CLIUtils.addEmptyAttributes(attrs, null);

      // Then
      assertTrue(attrs.isEmpty());
    }
  }

  @Nested
  class Attribute_Updates {

    @Test
    void update_attributes_returns_false_when_no_changes() {
      // Given
      var data = new Attributes();
      var attrs = new Attributes();

      // When
      var result = CLIUtils.updateAttributes(data, attrs, null);

      // Then
      assertFalse(result);
    }

    @Test
    void update_attributes_applies_new_attributes() {
      // Given
      var data = new Attributes();
      data.setString(Tag.PatientName, VR.PN, "Original^Name");

      var attrs = new Attributes();
      attrs.setString(Tag.PatientName, VR.PN, "Updated^Name");
      attrs.setString(Tag.StudyDate, VR.DA, "20231201");

      // When
      var result = CLIUtils.updateAttributes(data, attrs, null);

      // Then
      assertTrue(result);
      assertEquals("Updated^Name", data.getString(Tag.PatientName));
      assertEquals("20231201", data.getString(Tag.StudyDate));
    }

    @Test
    void update_attributes_appends_uid_suffix() {
      // Given
      var data = new Attributes();
      data.setString(Tag.StudyInstanceUID, VR.UI, "1.2.3.4.5");
      data.setString(Tag.SeriesInstanceUID, VR.UI, "1.2.3.4.6");
      data.setString(Tag.SOPInstanceUID, VR.UI, "1.2.3.4.7");

      var attrs = new Attributes();
      var suffix = ".001";

      // When
      var result = CLIUtils.updateAttributes(data, attrs, suffix);

      // Then
      assertTrue(result);
      assertEquals("1.2.3.4.5.001", data.getString(Tag.StudyInstanceUID));
      assertEquals("1.2.3.4.6.001", data.getString(Tag.SeriesInstanceUID));
      assertEquals("1.2.3.4.7.001", data.getString(Tag.SOPInstanceUID));
    }

    @Test
    void update_attributes_handles_missing_uids_gracefully() {
      // Given
      var data = new Attributes();
      data.setString(Tag.StudyInstanceUID, VR.UI, "1.2.3.4.5");
      // Missing SeriesInstanceUID and SOPInstanceUID

      var attrs = new Attributes();
      var suffix = ".001";

      // When
      var result = CLIUtils.updateAttributes(data, attrs, suffix);

      // Then
      assertTrue(result);
      assertEquals("1.2.3.4.5.001", data.getString(Tag.StudyInstanceUID));
      assertNull(data.getString(Tag.SeriesInstanceUID));
      assertNull(data.getString(Tag.SOPInstanceUID));
    }
  }

  @Nested
  class UID_Conversion {

    @Test
    void to_uids_handles_wildcard() {
      // When
      var result = CLIUtils.toUIDs("*");

      // Then
      assertArrayEquals(new String[] {"*"}, result);
    }

    @Test
    void to_uids_splits_comma_separated_values() {
      // Given
      var uidString = "1.2.3.4.5,1.2.3.4.6,1.2.3.4.7";

      // When
      var result = CLIUtils.toUIDs(uidString);

      // Then
      assertEquals(3, result.length);
      assertEquals("1.2.3.4.5", result[0]);
      assertEquals("1.2.3.4.6", result[1]);
      assertEquals("1.2.3.4.7", result[2]);
    }

    @Test
    void to_uids_converts_uid_names() {
      // Given
      var uidString = "ImplicitVRLittleEndian,ExplicitVRLittleEndian";

      // When
      var result = CLIUtils.toUIDs(uidString);

      // Then
      assertEquals(2, result.length);
      assertNotEquals("ImplicitVRLittleEndian", result[0]); // Should be converted to actual UID
      assertNotEquals("ExplicitVRLittleEndian", result[1]); // Should be converted to actual UID
    }

    @ParameterizedTest
    @ValueSource(strings = {"*", "1.2.3.4.5", "1.2.840.10008.1.2"})
    void to_uid_returns_unchanged_for_wildcards_and_numeric_uids(String uid) {
      // When
      var result = CLIUtils.toUID(uid);

      // Then
      assertEquals(uid, result);
    }

    @Test
    void to_uid_converts_name_to_uid() {
      // Given
      var uidName = "ImplicitVRLittleEndian";

      // When
      var result = CLIUtils.toUID(uidName);

      // Then
      assertNotEquals(uidName, result);
      assertTrue(result.startsWith("1.2.840.10008")); // Should be a proper DICOM UID
    }

    @Test
    void to_uid_handles_whitespace() {
      // Given
      var uidWithSpaces = "  1.2.3.4.5  ";

      // When
      var result = CLIUtils.toUID(uidWithSpaces);

      // Then
      assertEquals("1.2.3.4.5", result);
    }

    @Test
    void to_uid_handles_wildcard_with_spaces() {
      // Given
      var wildcardWithSpaces = "  *  ";

      // When
      var result = CLIUtils.toUID(wildcardWithSpaces);

      // Then
      assertEquals("*", result);
    }
  }

  @Nested
  class Edge_Cases_And_Error_Handling {

    @Test
    void add_attributes_creates_sequence_for_sequence_vr_with_empty_values() {
      // Given
      var attrs = new Attributes();
      var tags = new int[] {Tag.ReferencedImageSequence}; // This is a sequence tag

      // When
      CLIUtils.addAttributes(attrs, tags);

      // Then
      var sequence = attrs.getSequence(Tag.ReferencedImageSequence);
      assertNotNull(sequence);
      assertEquals(1, sequence.size());
      assertTrue(sequence.get(0).isEmpty());
    }

    @Test
    void add_attributes_handles_single_empty_string_value() {
      // Given
      var attrs = new Attributes();
      var tags = new int[] {Tag.PatientName};
      var emptyValue = new String[] {""};

      // When
      CLIUtils.addAttributes(attrs, tags, emptyValue);

      // Then
      assertTrue(attrs.contains(Tag.PatientName));
      assertNull(attrs.getString(Tag.PatientName));
    }

    @Test
    void process_option_value_handles_equals_in_value() {
      // Given
      var attrs = new Attributes();
      var optVal = "PatientComments=This=is=a=comment=with=equals";

      // When
      CLIUtils.addAttributes(attrs, new String[] {optVal});

      // Then
      assertEquals("This=is=a=comment=with=equals", attrs.getString(Tag.PatientComments));
    }

    @Test
    void to_uids_handles_single_uid() {
      // Given
      var singleUID = "1.2.3.4.5";

      // When
      var result = CLIUtils.toUIDs(singleUID);

      // Then
      assertEquals(1, result.length);
      assertEquals(singleUID, result[0]);
    }

    @Test
    void add_attributes_with_nested_sequences() {
      // Given
      var attrs = new Attributes();
      var tags =
          new int[] {
            Tag.ReferencedImageSequence, Tag.PurposeOfReferenceCodeSequence, Tag.CodeMeaning
          };
      var value = "Test Code Meaning";

      // When
      CLIUtils.addAttributes(attrs, tags, value);

      // Then
      var outerSequence = attrs.getSequence(Tag.ReferencedImageSequence);
      assertNotNull(outerSequence);
      var outerItem = outerSequence.get(0);
      var innerSequence = outerItem.getSequence(Tag.PurposeOfReferenceCodeSequence);
      assertNotNull(innerSequence);
      var innerItem = innerSequence.get(0);
      assertEquals(value, innerItem.getString(Tag.CodeMeaning));
    }
  }

  @Nested
  class Integration_Tests {

    @Test
    void complete_workflow_with_properties_tags_and_attributes(@TempDir Path tempDir)
        throws IOException {
      // Given
      var propsFile = tempDir.resolve("config.properties");
      Files.writeString(propsFile, "patient.name=Test^Patient\nstudy.date=20231201\n");

      var properties = CLIUtils.loadProperties(propsFile.toString(), null);
      var attrs = new Attributes();

      // When
      var patientName = properties.getProperty("patient.name");
      var studyDate = properties.getProperty("study.date");

      var tags = CLIUtils.toTags(new String[] {"PatientName", "StudyDate"});
      CLIUtils.addAttributes(attrs, new int[] {tags[0]}, patientName);
      CLIUtils.addAttributes(attrs, new int[] {tags[1]}, studyDate);

      // Then
      assertEquals("Test^Patient", attrs.getString(Tag.PatientName));
      assertEquals("20231201", attrs.getString(Tag.StudyDate));
    }

    @Test
    void uid_processing_workflow() {
      // Given
      var uidString = "ImplicitVRLittleEndian,ExplicitVRLittleEndian,*";

      // When
      var uids = CLIUtils.toUIDs(uidString);

      // Then
      assertEquals(3, uids.length);
      assertTrue(uids[0].matches("\\d+(\\.\\d+)*")); // Should be numeric UID
      assertTrue(uids[1].matches("\\d+(\\.\\d+)*")); // Should be numeric UID
      assertEquals("*", uids[2]);
    }

    @Test
    void attribute_update_workflow_with_suffix() {
      // Given
      var originalData = new Attributes();
      originalData.setString(Tag.StudyInstanceUID, VR.UI, "1.2.3.4.5");
      originalData.setString(Tag.PatientName, VR.PN, "Original^Name");

      var updateAttrs = new Attributes();
      updateAttrs.setString(Tag.PatientName, VR.PN, "Updated^Name");
      updateAttrs.setString(Tag.StudyDate, VR.DA, "20231201");

      // When
      var wasUpdated = CLIUtils.updateAttributes(originalData, updateAttrs, ".001");

      // Then
      assertTrue(wasUpdated);
      assertEquals("1.2.3.4.5.001", originalData.getString(Tag.StudyInstanceUID));
      assertEquals("Updated^Name", originalData.getString(Tag.PatientName));
      assertEquals("20231201", originalData.getString(Tag.StudyDate));
    }
  }
}
