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

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.weasis.core.util.StreamUtil;

@DisplayNameGeneration(ReplaceUnderscores.class)
class DicomFilesTest {

  @TempDir Path tempDir;

  private final List<CallbackResult> callbackResults = new ArrayList<>();
  private final AtomicInteger successCount = new AtomicInteger();
  private final AtomicInteger failureCount = new AtomicInteger();

  @BeforeEach
  void setUp() {
    callbackResults.clear();
    successCount.set(0);
    failureCount.set(0);
  }

  @Test
  void scan_with_xml_file_processes_correctly() throws Exception {
    // Given
    var xmlFile = createDicomXmlFile("test.xml");
    var callback = createRecordingCallback(true);

    // When
    DicomFiles.scan(List.of(xmlFile.toString()), callback);

    // Then
    assertEquals(1, callbackResults.size());
    var result = callbackResults.get(0);
    assertEquals("test.xml", result.fileName());
    assertEquals(-1L, result.datasetPosition());
    assertNotNull(result.dataset());
    assertTrue(result.dataset().contains(Tag.SOPInstanceUID));
  }

  @Test
  void scan_with_real_dicom_file_processes_correctly() throws Exception {
    // Given
    var dicomFile = createRealDicomFile("test.dcm");
    var callback = createRecordingCallback(true);

    // When
    DicomFiles.scan(List.of(dicomFile.toString()), callback);

    // Then
    assertEquals(1, callbackResults.size());
    var result = callbackResults.get(0);
    assertEquals("test.dcm", result.fileName());
    assertTrue(result.datasetPosition() > 0);
    assertNotNull(result.fileMetaInfo());
    assertNotNull(result.dataset());
    assertTrue(result.fileMetaInfo().contains(Tag.MediaStorageSOPInstanceUID));
    assertTrue(result.dataset().contains(Tag.SOPInstanceUID));
  }

  @Test
  void scan_with_directory_processes_all_files() throws Exception {
    // Given
    var subDir = tempDir.resolve("subdir");
    Files.createDirectories(subDir);

    createNonDicomFile("file1.txt");
    createRealDicomFile("file2.dcm");
    createDicomXmlFile(subDir.resolve("file3.xml"));

    var callback = createRecordingCallback(true);

    // When
    DicomFiles.scan(List.of(tempDir.toString()), callback);

    // Then
    assertEquals(2, callbackResults.size());
    var fileNames = callbackResults.stream().map(CallbackResult::fileName).sorted().toList();
    assertEquals(List.of("file2.dcm", "file3.xml"), fileNames);
  }

  @Test
  void scan_with_nested_directories_processes_recursively() throws Exception {
    // Given
    var level1 = tempDir.resolve("level1");
    var level2 = level1.resolve("level2");
    Files.createDirectories(level2);

    createNonDicomFile("root.txt");
    createRealDicomFile(level1.resolve("level1.dcm"));
    createDicomXmlFile(level2.resolve("level2.xml"));

    var callback = createRecordingCallback(true);

    // When
    DicomFiles.scan(List.of(tempDir.toString()), callback);

    // Then
    assertEquals(2, callbackResults.size());
    var fileNames = callbackResults.stream().map(CallbackResult::fileName).sorted().toList();
    assertEquals(List.of("level1.dcm", "level2.xml"), fileNames);
  }

  @Test
  void scan_with_unreadable_directory_handles_gracefully() throws IOException {
    // Given
    var unreadableDir = tempDir.resolve("unreadable");
    try {
      Files.createDirectories(unreadableDir);
      unreadableDir.toFile().setReadable(false);

      var callback = createRecordingCallback(true);

      // When & Then - should not throw exception
      assertDoesNotThrow(() -> DicomFiles.scan(List.of(unreadableDir.toString()), callback));

    } finally {
      // Cleanup
      unreadableDir.toFile().setReadable(true);
    }
  }

  @Test
  void scan_with_nonexistent_file_handles_gracefully() {
    // Given
    var nonexistentFile = tempDir.resolve("nonexistent.dcm").toString();
    var callback = createRecordingCallback(true);

    // When & Then
    assertDoesNotThrow(() -> DicomFiles.scan(List.of(nonexistentFile), callback));
    assertEquals(0, callbackResults.size());
  }

  @Test
  void scan_with_multiple_files_processes_all() throws Exception {
    // Given
    var file1 = createNonDicomFile("test1.txt");
    var file2 = createRealDicomFile("test2.dcm");
    var file3 = createDicomXmlFile("test3.xml");

    var filePaths = List.of(file1.toString(), file2.toString(), file3.toString());
    var callback = createRecordingCallback(true);

    // When
    DicomFiles.scan(filePaths, callback);

    // Then
    assertEquals(2, callbackResults.size());
    assertEquals(2, successCount.get());
  }

  @Test
  void scan_with_callback_exception_continues_processing() throws Exception {
    // Given
    createNonDicomFile("file1.txt");
    createNonDicomFile("file2.txt");

    var callback = new ExceptionThrowingCallback();

    // When & Then
    assertDoesNotThrow(() -> DicomFiles.scan(List.of(tempDir.toString()), callback));
    assertEquals(0, callback.getProcessedFiles().size());
  }

  @Test
  void scan_with_printout_true_prints_progress() throws Exception {
    // Given
    var originalOut = System.out;
    var outputCapture = new ByteArrayOutputStream();
    System.setOut(new PrintStream(outputCapture));

    try {
      createRealDicomFile("test2.dcm");
      var callback = createRecordingCallback(true);

      // When
      DicomFiles.scan(List.of(tempDir.toString()), true, callback);

      // Then
      var output = outputCapture.toString();
      assertTrue(output.contains("."), "Should print success indicator");

    } finally {
      System.setOut(originalOut);
    }
  }

  @Test
  void scan_with_printout_false_does_not_print() throws Exception {
    // Given
    var originalOut = System.out;
    var outputCapture = new ByteArrayOutputStream();
    System.setOut(new PrintStream(outputCapture));

    try {
      createRealDicomFile("test2.dcm");
      var callback = createRecordingCallback(true);

      // When
      DicomFiles.scan(List.of(tempDir.toString()), false, callback);

      // Then
      var output = outputCapture.toString();
      assertFalse(output.contains("."), "Should not print when printout is false");

    } finally {
      System.setOut(originalOut);
    }
  }

  @Test
  void scan_with_failing_callback_prints_failure_indicator() throws Exception {
    // Given
    var originalOut = System.out;
    var outputCapture = new ByteArrayOutputStream();
    System.setOut(new PrintStream(outputCapture));

    try {
      createNonDicomFile("test.txt");
      var callback = createRecordingCallback(false);

      // When
      DicomFiles.scan(List.of(tempDir.toString()), true, callback);

      // Then
      var output = outputCapture.toString();
      assertTrue(output.contains("I"), "Should print failure indicator");

    } finally {
      System.setOut(originalOut);
    }
  }

  @Test
  void scan_single_parameter_defaults_to_printout_true() throws Exception {
    // Given
    var originalOut = System.out;
    var outputCapture = new ByteArrayOutputStream();
    System.setOut(new PrintStream(outputCapture));

    try {
      createNonDicomFile("test.txt");
      var callback = createRecordingCallback(true);

      // When
      DicomFiles.scan(List.of(tempDir.toString()), callback);

      // Then
      var output = outputCapture.toString();
      assertTrue(output.contains("."), "Should print with default printout=true");

    } finally {
      System.setOut(originalOut);
    }
  }

  @Test
  void scan_with_xml_file_provides_correct_dataset_position() throws Exception {
    // Given
    var xmlFile = createDicomXmlFile("test.xml");
    var callback = createRecordingCallback(true);

    // When
    DicomFiles.scan(List.of(xmlFile.toString()), callback);

    // Then
    assertEquals(1, callbackResults.size());
    assertEquals(
        -1L, callbackResults.get(0).datasetPosition(), "XML files should have dataset position -1");
  }

  @Test
  void scan_with_empty_file_list_does_nothing() {
    // Given
    var callback = createRecordingCallback(true);

    // When
    DicomFiles.scan(List.of(), callback);

    // Then
    assertEquals(0, callbackResults.size());
    assertEquals(0, successCount.get());
  }

  @Test
  void scan_with_mixed_file_types_processes_all() throws Exception {
    // Given
    createNonDicomFile("document.txt");
    createNonDicomFile("image.jpg");
    createDicomXmlFile("dicom.xml");
    createRealDicomFile("medical.dcm");

    var callback = createRecordingCallback(true);

    // When
    DicomFiles.scan(List.of(tempDir.toString()), callback);

    // Then only one DICOM XML and one DICOM file should be processed
    assertEquals(2, callbackResults.size());
    assertEquals(2, successCount.get());
  }

  @Test
  void scan_with_invalid_dicom_file_handles_error() throws Exception {
    // Given
    var invalidDicom = createCorruptedDicomFile("invalid.dcm");
    var callback = createRecordingCallback(true);

    // When & Then
    assertDoesNotThrow(() -> DicomFiles.scan(List.of(invalidDicom.toString()), callback));
    // Error should be handled gracefully, no callback should be made for corrupted files
    assertEquals(0, callbackResults.size());
  }

  // Helper methods and classes

  private Path createNonDicomFile(String filename) throws IOException {
    var file = tempDir.resolve(filename);
    Files.write(file, "This is not a DICOM file".getBytes());
    return file;
  }

  private Path createDicomXmlFile(String filename) throws IOException {
    return createDicomXmlFile(tempDir.resolve(filename));
  }

  private Path createDicomXmlFile(Path filePath) throws IOException {
    var xmlContent =
        """
        <?xml version="1.0" encoding="UTF-8"?>
        <NativeDicomModel>
          <DicomAttribute tag="00080018" vr="UI" keyword="SOPInstanceUID">
            <Value number="1">1.2.3.4.5.6.7.8.9</Value>
          </DicomAttribute>
          <DicomAttribute tag="00080016" vr="UI" keyword="SOPClassUID">
            <Value number="1">1.2.840.10008.5.1.4.1.1.1</Value>
          </DicomAttribute>
          <DicomAttribute tag="00100010" vr="PN" keyword="PatientName">
            <Value number="1">Test^Patient</Value>
          </DicomAttribute>
          <DicomAttribute tag="0020000D" vr="UI" keyword="StudyInstanceUID">
            <Value number="1">1.2.3.4.5.6.7.8.9.10</Value>
          </DicomAttribute>
        </NativeDicomModel>
        """;
    Files.createDirectories(filePath.getParent());
    Files.write(filePath, xmlContent.getBytes());
    return filePath;
  }

  private Path createRealDicomFile(String filename) throws IOException {
    return createRealDicomFile(tempDir.resolve(filename));
  }

  private Path createRealDicomFile(Path filePath) throws IOException {
    Files.createDirectories(filePath.getParent());
    StreamUtil.copyFile(Path.of("src/test/resources/org/dcm4che3/img/prLUTs.dcm"), filePath);
    return filePath;
  }

  private Path createCorruptedDicomFile(String filename) throws IOException {
    var file = tempDir.resolve(filename);
    // Create a file with DICOM prefix but invalid structure
    var content = "DICM".getBytes();
    Files.write(file, content);
    return file;
  }

  private DicomFiles.Callback createRecordingCallback(boolean returnValue) {
    return new RecordingCallback(returnValue);
  }

  // Real callback implementations

  private record CallbackResult(
      String fileName,
      Attributes fileMetaInfo,
      long datasetPosition,
      Attributes dataset,
      boolean success) {}

  private class RecordingCallback implements DicomFiles.Callback {
    private final boolean returnValue;

    RecordingCallback(boolean returnValue) {
      this.returnValue = returnValue;
    }

    @Override
    public boolean dicomFile(File f, Attributes fmi, long dsPos, Attributes ds) {
      callbackResults.add(new CallbackResult(f.getName(), fmi, dsPos, ds, returnValue));
      if (returnValue) {
        successCount.incrementAndGet();
      } else {
        failureCount.incrementAndGet();
      }
      return returnValue;
    }
  }

  private static class ExceptionThrowingCallback implements DicomFiles.Callback {
    private final AtomicInteger callCount = new AtomicInteger();
    private final List<String> processedFiles = new ArrayList<>();

    @Override
    public boolean dicomFile(File f, Attributes fmi, long dsPos, Attributes ds) throws Exception {
      int count = callCount.incrementAndGet();
      processedFiles.add(f.getName());
      if (count == 1) {
        throw new RuntimeException("Test exception");
      }
      return true;
    }

    List<String> getProcessedFiles() {
      return new ArrayList<>(processedFiles);
    }
  }
}
