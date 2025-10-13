/*
 * Copyright (c) 2025 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.dcm4che3.tool.common;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

@DisplayNameGeneration(ReplaceUnderscores.class)
class FilesetInfoTest {

  @TempDir Path tempDir;

  private static final String TEST_UID = "1.2.840.10008.1.1";
  private static final String TEST_ID = "TESTFILESET";
  private static final String TEST_CHARSET = "UTF-8";
  private static final String DICOMDIR_FILENAME = "DICOMDIR";

  private Path testDescriptorFile;
  private FilesetInfo basicFilesetInfo;
  private FilesetInfo completeFilesetInfo;

  @BeforeEach
  void setUp() throws IOException {
    testDescriptorFile = createTestDescriptorFile();
    basicFilesetInfo = new FilesetInfo(TEST_UID, TEST_ID);
    completeFilesetInfo = new FilesetInfo(TEST_UID, TEST_ID, testDescriptorFile, TEST_CHARSET);
  }

  // Constructor tests

  @Test
  void constructor_with_uid_and_id_sets_basic_properties() {
    var filesetInfo = new FilesetInfo("1.2.3.4", "BASIC_SET");

    assertAll(
        () -> assertEquals("1.2.3.4", filesetInfo.getFilesetUID()),
        () -> assertEquals("BASIC_SET", filesetInfo.getFilesetID()),
        () -> assertTrue(filesetInfo.getDescriptorFile().isEmpty()),
        () -> assertTrue(filesetInfo.getDescriptorFileCharset().isEmpty()));
  }

  @Test
  void constructor_with_all_parameters_sets_complete_properties() throws IOException {
    var descriptorFile = createTestDescriptorFile();
    var filesetInfo = new FilesetInfo("1.2.3.4", "COMPLETE_SET", descriptorFile, "ISO-8859-1");

    assertAll(
        () -> assertEquals("1.2.3.4", filesetInfo.getFilesetUID()),
        () -> assertEquals("COMPLETE_SET", filesetInfo.getFilesetID()),
        () -> assertTrue(filesetInfo.getDescriptorFile().isPresent()),
        () -> assertEquals(descriptorFile, filesetInfo.getDescriptorFile().get()),
        () -> assertTrue(filesetInfo.getDescriptorFileCharset().isPresent()),
        () -> assertEquals("ISO-8859-1", filesetInfo.getDescriptorFileCharset().get()));
  }

  @Test
  void constructor_with_null_values_handles_gracefully() {
    var filesetInfo = new FilesetInfo(null, null);

    assertAll(
        () -> assertNull(filesetInfo.getFilesetUID()),
        () -> assertNull(filesetInfo.getFilesetID()),
        () -> assertTrue(filesetInfo.getDescriptorFile().isEmpty()),
        () -> assertTrue(filesetInfo.getDescriptorFileCharset().isEmpty()));
  }

  @Test
  void constructor_with_null_path_and_charset_handles_gracefully() {
    var filesetInfo = new FilesetInfo(TEST_UID, TEST_ID, null, null);

    assertAll(
        () -> assertEquals(TEST_UID, filesetInfo.getFilesetUID()),
        () -> assertEquals(TEST_ID, filesetInfo.getFilesetID()),
        () -> assertTrue(filesetInfo.getDescriptorFile().isEmpty()),
        () -> assertTrue(filesetInfo.getDescriptorFileCharset().isEmpty()));
  }

  // Getter and setter tests

  @Test
  void getFilesetUID_returns_correct_value() {
    assertEquals(TEST_UID, basicFilesetInfo.getFilesetUID());
  }

  @Test
  void setFilesetUID_updates_value_and_returns_instance() {
    var newUid = "1.2.840.10008.2.2";
    var result = basicFilesetInfo.setFilesetUID(newUid);

    assertAll(
        () -> assertEquals(newUid, basicFilesetInfo.getFilesetUID()),
        () -> assertSame(basicFilesetInfo, result));
  }

  @Test
  void setFilesetUID_with_null_sets_null_value() {
    var result = basicFilesetInfo.setFilesetUID(null);

    assertAll(
        () -> assertNull(basicFilesetInfo.getFilesetUID()),
        () -> assertSame(basicFilesetInfo, result));
  }

  @Test
  void getFilesetID_returns_correct_value() {
    assertEquals(TEST_ID, basicFilesetInfo.getFilesetID());
  }

  @Test
  void setFilesetID_updates_value_and_returns_instance() {
    var newId = "NEWFILESET";
    var result = basicFilesetInfo.setFilesetID(newId);

    assertAll(
        () -> assertEquals(newId, basicFilesetInfo.getFilesetID()),
        () -> assertSame(basicFilesetInfo, result));
  }

  @Test
  void getDescriptorFile_returns_empty_when_not_set() {
    assertTrue(basicFilesetInfo.getDescriptorFile().isEmpty());
  }

  @Test
  void getDescriptorFile_returns_present_when_set() {
    assertTrue(completeFilesetInfo.getDescriptorFile().isPresent());
    assertEquals(testDescriptorFile, completeFilesetInfo.getDescriptorFile().get());
  }

  @Test
  void setDescriptorFile_updates_value_and_returns_instance() throws IOException {
    var newDescriptorFile = createTestDescriptorFile("DICOMDIR2");
    var result = basicFilesetInfo.setDescriptorFile(newDescriptorFile);

    assertAll(
        () -> assertTrue(basicFilesetInfo.getDescriptorFile().isPresent()),
        () -> assertEquals(newDescriptorFile, basicFilesetInfo.getDescriptorFile().get()),
        () -> assertSame(basicFilesetInfo, result));
  }

  @Test
  void setDescriptorFile_with_null_clears_value() {
    var result = completeFilesetInfo.setDescriptorFile(null);

    assertAll(
        () -> assertTrue(completeFilesetInfo.getDescriptorFile().isEmpty()),
        () -> assertSame(completeFilesetInfo, result));
  }

  @Test
  void getDescriptorFileCharset_returns_empty_when_not_set() {
    assertTrue(basicFilesetInfo.getDescriptorFileCharset().isEmpty());
  }

  @Test
  void getDescriptorFileCharset_returns_present_when_set() {
    assertTrue(completeFilesetInfo.getDescriptorFileCharset().isPresent());
    assertEquals(TEST_CHARSET, completeFilesetInfo.getDescriptorFileCharset().get());
  }

  @Test
  void setDescriptorFileCharset_updates_value_and_returns_instance() {
    var newCharset = "ISO-8859-1";
    var result = basicFilesetInfo.setDescriptorFileCharset(newCharset);

    assertAll(
        () -> assertTrue(basicFilesetInfo.getDescriptorFileCharset().isPresent()),
        () -> assertEquals(newCharset, basicFilesetInfo.getDescriptorFileCharset().get()),
        () -> assertSame(basicFilesetInfo, result));
  }

  @Test
  void setDescriptorFileCharset_with_null_clears_value() {
    var result = completeFilesetInfo.setDescriptorFileCharset(null);

    assertAll(
        () -> assertTrue(completeFilesetInfo.getDescriptorFileCharset().isEmpty()),
        () -> assertSame(completeFilesetInfo, result));
  }

  // Method chaining tests

  @Test
  void method_chaining_works_correctly() throws IOException {
    var descriptorFile = createTestDescriptorFile("CHAINDIR");
    var filesetInfo =
        new FilesetInfo(null, null)
            .setFilesetUID("1.2.3.4.5")
            .setFilesetID("CHAIN_SET")
            .setDescriptorFile(descriptorFile)
            .setDescriptorFileCharset("US-ASCII");

    assertAll(
        () -> assertEquals("1.2.3.4.5", filesetInfo.getFilesetUID()),
        () -> assertEquals("CHAIN_SET", filesetInfo.getFilesetID()),
        () -> assertEquals(descriptorFile, filesetInfo.getDescriptorFile().get()),
        () -> assertEquals("US-ASCII", filesetInfo.getDescriptorFileCharset().get()));
  }

  // Utility method tests

  @Test
  void isComplete_returns_true_when_all_required_fields_present() {
    assertTrue(completeFilesetInfo.isComplete());
  }

  @Test
  void isComplete_returns_false_when_uid_missing() {
    var filesetInfo = new FilesetInfo(null, TEST_ID, testDescriptorFile, TEST_CHARSET);
    assertFalse(filesetInfo.isComplete());
  }

  @Test
  void isComplete_returns_false_when_id_missing() {
    var filesetInfo = new FilesetInfo(TEST_UID, null, testDescriptorFile, TEST_CHARSET);
    assertFalse(filesetInfo.isComplete());
  }

  @Test
  void isComplete_returns_false_when_descriptor_file_missing() {
    var filesetInfo = new FilesetInfo(TEST_UID, TEST_ID, null, TEST_CHARSET);
    assertFalse(filesetInfo.isComplete());
  }

  @Test
  void isComplete_returns_false_when_uid_empty() {
    var filesetInfo = new FilesetInfo("", TEST_ID, testDescriptorFile, TEST_CHARSET);
    assertFalse(filesetInfo.isComplete());
  }

  @Test
  void isComplete_returns_false_when_uid_whitespace_only() {
    var filesetInfo = new FilesetInfo("   ", TEST_ID, testDescriptorFile, TEST_CHARSET);
    assertFalse(filesetInfo.isComplete());
  }

  @Test
  void isComplete_returns_false_when_id_empty() {
    var filesetInfo = new FilesetInfo(TEST_UID, "", testDescriptorFile, TEST_CHARSET);
    assertFalse(filesetInfo.isComplete());
  }

  @Test
  void isComplete_returns_false_when_id_whitespace_only() {
    var filesetInfo = new FilesetInfo(TEST_UID, "   ", testDescriptorFile, TEST_CHARSET);
    assertFalse(filesetInfo.isComplete());
  }

  @Test
  void hasValidIdentifiers_returns_true_when_both_identifiers_present() {
    assertTrue(basicFilesetInfo.hasValidIdentifiers());
    assertTrue(completeFilesetInfo.hasValidIdentifiers());
  }

  @Test
  void hasValidIdentifiers_returns_false_when_uid_missing() {
    var filesetInfo = new FilesetInfo(null, TEST_ID);
    assertFalse(filesetInfo.hasValidIdentifiers());
  }

  @Test
  void hasValidIdentifiers_returns_false_when_id_missing() {
    var filesetInfo = new FilesetInfo(TEST_UID, null);
    assertFalse(filesetInfo.hasValidIdentifiers());
  }

  @Test
  void hasValidIdentifiers_returns_false_when_both_missing() {
    var filesetInfo = new FilesetInfo(null, null);
    assertFalse(filesetInfo.hasValidIdentifiers());
  }

  @Test
  void hasValidIdentifiers_returns_false_when_uid_empty() {
    var filesetInfo = new FilesetInfo("", TEST_ID);
    assertFalse(filesetInfo.hasValidIdentifiers());
  }

  @Test
  void hasValidIdentifiers_returns_false_when_id_empty() {
    var filesetInfo = new FilesetInfo(TEST_UID, "");
    assertFalse(filesetInfo.hasValidIdentifiers());
  }

  @Test
  void hasValidIdentifiers_returns_false_when_uid_whitespace_only() {
    var filesetInfo = new FilesetInfo("   ", TEST_ID);
    assertFalse(filesetInfo.hasValidIdentifiers());
  }

  @Test
  void hasValidIdentifiers_returns_false_when_id_whitespace_only() {
    var filesetInfo = new FilesetInfo(TEST_UID, "   ");
    assertFalse(filesetInfo.hasValidIdentifiers());
  }

  // equals and hashCode tests

  @Test
  void equals_returns_true_for_same_instance() {
    assertEquals(basicFilesetInfo, basicFilesetInfo);
  }

  @Test
  void equals_returns_true_for_equivalent_instances() {
    var other = new FilesetInfo(TEST_UID, TEST_ID);
    assertEquals(basicFilesetInfo, other);
  }

  @Test
  void equals_returns_true_for_complete_equivalent_instances() {
    var other = new FilesetInfo(TEST_UID, TEST_ID, testDescriptorFile, TEST_CHARSET);
    assertEquals(completeFilesetInfo, other);
  }

  @Test
  void equals_returns_false_for_different_uid() {
    var other = new FilesetInfo("DIFFERENT_UID", TEST_ID);
    assertNotEquals(basicFilesetInfo, other);
  }

  @Test
  void equals_returns_false_for_different_id() {
    var other = new FilesetInfo(TEST_UID, "DIFFERENT_ID");
    assertNotEquals(basicFilesetInfo, other);
  }

  @Test
  void equals_returns_false_for_different_descriptor_file() throws IOException {
    var otherDescriptor = createTestDescriptorFile("OTHERDIR");
    var other = new FilesetInfo(TEST_UID, TEST_ID, otherDescriptor, TEST_CHARSET);
    assertNotEquals(completeFilesetInfo, other);
  }

  @Test
  void equals_returns_false_for_different_charset() {
    var other = new FilesetInfo(TEST_UID, TEST_ID, testDescriptorFile, "ISO-8859-1");
    assertNotEquals(completeFilesetInfo, other);
  }

  @Test
  void equals_returns_false_for_null() {
    assertNotEquals(null, basicFilesetInfo);
  }

  @Test
  void hashCode_is_consistent() {
    int hash1 = basicFilesetInfo.hashCode();
    int hash2 = basicFilesetInfo.hashCode();
    assertEquals(hash1, hash2);
  }

  @Test
  void hashCode_is_equal_for_equivalent_instances() {
    var other = new FilesetInfo(TEST_UID, TEST_ID);
    assertEquals(basicFilesetInfo.hashCode(), other.hashCode());
  }

  @Test
  void hashCode_handles_null_values() {
    var filesetInfo = new FilesetInfo(null, null, null, null);
    assertDoesNotThrow(filesetInfo::hashCode);
  }

  // toString tests

  @Test
  void toString_contains_all_field_information() {
    var result = completeFilesetInfo.toString();

    assertAll(
        () -> assertTrue(result.contains("FilesetInfo{")),
        () -> assertTrue(result.contains("uid='" + TEST_UID + "'")),
        () -> assertTrue(result.contains("id='" + TEST_ID + "'")),
        () -> assertTrue(result.contains("descriptorFile=" + testDescriptorFile)),
        () -> assertTrue(result.contains("descriptorFileCharset='" + TEST_CHARSET + "'")));
  }

  @Test
  void toString_handles_null_values() {
    var filesetInfo = new FilesetInfo(null, null, null, null);
    var result = filesetInfo.toString();

    assertAll(
        () -> assertTrue(result.contains("FilesetInfo{")),
        () -> assertTrue(result.contains("uid='null'")),
        () -> assertTrue(result.contains("id='null'")),
        () -> assertTrue(result.contains("descriptorFile=null")),
        () -> assertTrue(result.contains("descriptorFileCharset='null'")));
  }

  // Edge cases and error handling

  @Test
  void works_with_very_long_identifiers() {
    var longUid = "1.2.3.4.5.6.7.8.9.10.11.12.13.14.15.16.17.18.19.20.21.22.23.24.25";
    var longId = "VERY_LONG_FILESET_IDENTIFIER_THAT_EXCEEDS_NORMAL_LENGTH";

    var filesetInfo = new FilesetInfo(longUid, longId);

    assertAll(
        () -> assertEquals(longUid, filesetInfo.getFilesetUID()),
        () -> assertEquals(longId, filesetInfo.getFilesetID()),
        () -> assertTrue(filesetInfo.hasValidIdentifiers()));
  }

  @Test
  void works_with_special_characters_in_identifiers() {
    var uidWithDots = "1.2.3.4.5.6.7.8.9.10.11.12";
    var idWithUnderscores = "TEST_FILESET_WITH_UNDERSCORES";

    var filesetInfo = new FilesetInfo(uidWithDots, idWithUnderscores);

    assertAll(
        () -> assertEquals(uidWithDots, filesetInfo.getFilesetUID()),
        () -> assertEquals(idWithUnderscores, filesetInfo.getFilesetID()));
  }

  @Test
  void works_with_non_existent_descriptor_file_path() {
    var nonExistentPath = tempDir.resolve("non-existent").resolve("DICOMDIR");
    var filesetInfo = new FilesetInfo(TEST_UID, TEST_ID, nonExistentPath, TEST_CHARSET);

    assertAll(
        () -> assertTrue(filesetInfo.getDescriptorFile().isPresent()),
        () -> assertEquals(nonExistentPath, filesetInfo.getDescriptorFile().get()),
        () -> assertFalse(Files.exists(nonExistentPath)));
  }

  // Helper methods

  private Path createTestDescriptorFile() throws IOException {
    return createTestDescriptorFile(DICOMDIR_FILENAME);
  }

  private Path createTestDescriptorFile(String filename) throws IOException {
    var descriptorFile = tempDir.resolve(filename);
    var dicomdirContent =
        """
        # DICOMDIR file content
        # This is a test DICOMDIR file
        PATIENT,STUDY,SERIES,IMAGE
        """;
    Files.write(descriptorFile, dicomdirContent.getBytes());
    return descriptorFile;
  }
}
