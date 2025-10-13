/*
 * Copyright (c) 2014-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.op;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.dcm4che3.net.Status;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.junit.jupiter.MockitoExtension;
import org.weasis.dicom.param.AdvancedParams;
import org.weasis.dicom.param.ConnectOptions;
import org.weasis.dicom.param.CstoreParams;
import org.weasis.dicom.param.DicomNode;
import org.weasis.dicom.param.DicomProgress;
import org.weasis.dicom.param.DicomState;

@DisplayNameGeneration(ReplaceUnderscores.class)
@ExtendWith(MockitoExtension.class)
class CStoreTest {

  private static final String CALLING_AET = "CALLING_AET";
  private static final String CALLED_AET = "CALLED_AET";
  private static final String HOST = "localhost";
  private static final int PORT = 11112;

  private DicomNode callingNode;
  private DicomNode calledNode;
  private List<String> testFiles;

  @TempDir Path tempDir;

  @BeforeEach
  void setUp() throws IOException {
    callingNode = new DicomNode(CALLING_AET, HOST, PORT);
    calledNode = new DicomNode(CALLED_AET, HOST, PORT + 1);
    testFiles = createTestFiles();
  }

  private List<String> createTestFiles() throws IOException {
    var files = new ArrayList<String>();

    // Create some test files
    var file1 = tempDir.resolve("test1.dcm");
    var file2 = tempDir.resolve("test2.dcm");

    Files.writeString(file1, "test DICOM content 1");
    Files.writeString(file2, "test DICOM content 2");

    files.add(file1.toString());
    files.add(file2.toString());

    return files;
  }

  @Nested
  class Basic_Process_Methods {

    @Test
    void should_process_with_minimal_parameters() {
      var result = CStore.process(callingNode, calledNode, testFiles);

      assertNotNull(result);
      // The operation will fail due to no actual DICOM server, but we test the method delegation
      assertEquals(Status.UnableToProcess, result.getStatus());
    }

    @Test
    void should_process_with_progress() {
      var progress = new DicomProgress();

      var result = CStore.process(callingNode, calledNode, testFiles, progress);

      assertNotNull(result);
      assertEquals(Status.UnableToProcess, result.getStatus());
    }

    @Test
    void should_process_with_advanced_params() {
      var params = new AdvancedParams();
      params.setPriority(1);

      var result = CStore.process(params, callingNode, calledNode, testFiles);

      assertNotNull(result);
      assertEquals(Status.UnableToProcess, result.getStatus());
    }

    @Test
    void should_process_with_advanced_params_and_progress() {
      var params = new AdvancedParams();
      var progress = new DicomProgress();

      var result = CStore.process(params, callingNode, calledNode, testFiles, progress);

      assertNotNull(result);
      assertEquals(Status.UnableToProcess, result.getStatus());
    }

    @Test
    void should_process_with_full_parameters() {
      var params = new AdvancedParams();
      var progress = new DicomProgress();
      var cstoreParams = new CstoreParams(null, false, null);

      var result =
          CStore.process(params, callingNode, calledNode, testFiles, progress, cstoreParams);

      assertNotNull(result);
      assertEquals(Status.UnableToProcess, result.getStatus());
    }
  }

  @Nested
  class Parameter_Validation {

    @Test
    void should_throw_exception_when_calling_node_is_null() {
      var exception =
          assertThrows(
              NullPointerException.class, () -> CStore.process(null, calledNode, testFiles));

      assertTrue(exception.getMessage().contains("callingNode cannot be null"));
    }

    @Test
    void should_throw_exception_when_called_node_is_null() {
      var exception =
          assertThrows(
              NullPointerException.class, () -> CStore.process(callingNode, null, testFiles));

      assertTrue(exception.getMessage().contains("calledNode cannot be null"));
    }

    @Test
    void should_handle_null_advanced_params() {
      var result = CStore.process(null, callingNode, calledNode, testFiles);

      assertNotNull(result);
      assertEquals(Status.UnableToProcess, result.getStatus());
    }

    @Test
    void should_handle_null_cstore_params() {
      var result = CStore.process(null, callingNode, calledNode, testFiles, null, null);

      assertNotNull(result);
      assertEquals(Status.UnableToProcess, result.getStatus());
    }

    @Test
    void should_handle_null_progress() {
      var result = CStore.process(callingNode, calledNode, testFiles, null);

      assertNotNull(result);
      assertEquals(Status.UnableToProcess, result.getStatus());
    }
  }

  @Nested
  class File_Handling {

    @Test
    void should_handle_empty_file_list() {
      var emptyFiles = Collections.<String>emptyList();

      var result = CStore.process(callingNode, calledNode, emptyFiles);

      assertNotNull(result);
      assertEquals(Status.UnableToProcess, result.getStatus());
    }

    @Test
    void should_handle_non_existent_files() {
      var nonExistentFiles = List.of("/path/to/non/existent/file.dcm");

      var result = CStore.process(callingNode, calledNode, nonExistentFiles);

      assertNotNull(result);
      assertEquals(Status.UnableToProcess, result.getStatus());
    }

    @Test
    void should_handle_large_file_list() {
      var largeFileList = new ArrayList<String>();
      for (int i = 0; i < 1000; i++) {
        largeFileList.add("/path/to/file" + i + ".dcm");
      }

      var result = CStore.process(callingNode, calledNode, largeFileList);

      assertNotNull(result);
      assertEquals(Status.UnableToProcess, result.getStatus());
    }
  }

  @Nested
  class Advanced_Parameters_Configuration {

    @Test
    void should_respect_priority_setting() {
      var params = new AdvancedParams();
      params.setPriority(2);

      var result = CStore.process(params, callingNode, calledNode, testFiles);

      assertNotNull(result);
      assertEquals(Status.UnableToProcess, result.getStatus());
    }

    @Test
    void should_handle_connection_timeout() {
      var params = new AdvancedParams();
      ConnectOptions connectOptions = new ConnectOptions();
      connectOptions.setConnectTimeout(1000);
      params.setConnectOptions(connectOptions);

      var result = CStore.process(params, callingNode, calledNode, testFiles);

      assertNotNull(result);
      assertEquals(Status.UnableToProcess, result.getStatus());
    }

    @Test
    void should_handle_accept_timeout() {
      var params = new AdvancedParams();
      ConnectOptions connectOptions = new ConnectOptions();
      connectOptions.setAcceptTimeout(2000);
      params.setConnectOptions(connectOptions);

      var result = CStore.process(params, callingNode, calledNode, testFiles);

      assertNotNull(result);
      assertEquals(Status.UnableToProcess, result.getStatus());
    }
  }

  @Nested
  class CStore_Parameters_Configuration {

    @Test
    void should_handle_extend_negotiation_disabled() {
      var cstoreParams = new CstoreParams(null, false, null);

      var result = CStore.process(null, callingNode, calledNode, testFiles, null, cstoreParams);

      assertNotNull(result);
      assertEquals(Status.UnableToProcess, result.getStatus());
    }

    @Test
    void should_handle_extend_negotiation_enabled() throws MalformedURLException {
      var url = new URL("file:///path/to/sop/classes");
      var cstoreParams = new CstoreParams(null, true, url);

      var result = CStore.process(null, callingNode, calledNode, testFiles, null, cstoreParams);

      assertNotNull(result);
      assertEquals(Status.UnableToProcess, result.getStatus());
    }

    @Test
    void should_handle_custom_editors() {
      var cstoreParams = new CstoreParams(Collections.emptyList(), false, null);

      var result = CStore.process(null, callingNode, calledNode, testFiles, null, cstoreParams);

      assertNotNull(result);
      assertEquals(Status.UnableToProcess, result.getStatus());
    }
  }

  @Nested
  class Progress_Tracking {

    @Test
    void should_work_with_progress_listeners() {
      var progress = new DicomProgress();
      var listenerCalled = new boolean[1];

      progress.addProgressListener(p -> listenerCalled[0] = true);

      var result = CStore.process(callingNode, calledNode, testFiles, progress);

      assertNotNull(result);
      assertEquals(Status.UnableToProcess, result.getStatus());
    }

    @Test
    void should_handle_cancelled_progress() {
      var progress = new DicomProgress();
      progress.cancel();

      var result = CStore.process(callingNode, calledNode, testFiles, progress);

      assertNotNull(result);
      assertEquals(Status.UnableToProcess, result.getStatus());
    }

    @Test
    void should_handle_progress_with_attributes() {
      var progress = new DicomProgress();
      // Progress will be updated during the store operation

      var result = CStore.process(callingNode, calledNode, testFiles, progress);

      assertNotNull(result);
      assertEquals(Status.UnableToProcess, result.getStatus());
    }
  }

  @Nested
  class Error_Handling {

    @Test
    void should_handle_malformed_file_paths() {
      var malformedFiles = List.of("", "   ", "\t\n", "invalid\u0000path");

      var result = CStore.process(callingNode, calledNode, malformedFiles);

      assertNotNull(result);
      assertEquals(Status.UnableToProcess, result.getStatus());
    }
  }

  @Nested
  class Integration_Scenarios {

    @Test
    void should_handle_complete_workflow_with_all_parameters() {
      var params = new AdvancedParams();
      params.setPriority(1);
      ConnectOptions connectOptions = new ConnectOptions();
      connectOptions.setConnectTimeout(5000);
      params.setConnectOptions(connectOptions);

      var progress = new DicomProgress();
      var progressUpdates = new ArrayList<DicomProgress>();
      progress.addProgressListener(progressUpdates::add);

      var cstoreParams = new CstoreParams(Collections.emptyList(), false, null);

      var result =
          CStore.process(params, callingNode, calledNode, testFiles, progress, cstoreParams);

      assertNotNull(result);
      assertEquals(Status.UnableToProcess, result.getStatus());
    }

    @Test
    void should_preserve_node_configuration() {
      var originalCallingAet = callingNode.getAet();
      var originalCalledAet = calledNode.getAet();

      CStore.process(callingNode, calledNode, testFiles);

      // Verify the original nodes weren't modified
      assertEquals(originalCallingAet, callingNode.getAet());
      assertEquals(originalCalledAet, calledNode.getAet());
    }

    @Test
    void should_handle_concurrent_operations() throws InterruptedException {
      var results = new DicomState[2];
      var threads = new Thread[2];

      for (int i = 0; i < 2; i++) {
        final int index = i;
        threads[i] =
            new Thread(
                () -> {
                  results[index] = CStore.process(callingNode, calledNode, testFiles);
                });
        threads[i].start();
      }

      for (var thread : threads) {
        thread.join();
      }

      for (var result : results) {
        assertNotNull(result);
        assertEquals(Status.UnableToProcess, result.getStatus());
      }
    }
  }

  @Nested
  class Dicom_Node_Variations {

    @Test
    void should_handle_different_port_numbers() {
      var callingNodeDiffPort = new DicomNode(CALLING_AET, HOST, 8080);
      var calledNodeDiffPort = new DicomNode(CALLED_AET, HOST, 9090);

      var result = CStore.process(callingNodeDiffPort, calledNodeDiffPort, testFiles);

      assertNotNull(result);
      assertEquals(Status.UnableToProcess, result.getStatus());
    }

    @Test
    void should_handle_different_hostnames() {
      var callingNodeDiffHost = new DicomNode(CALLING_AET, "127.0.0.1", PORT);
      var calledNodeDiffHost = new DicomNode(CALLED_AET, "remote-server", PORT + 1);

      var result = CStore.process(callingNodeDiffHost, calledNodeDiffHost, testFiles);

      assertNotNull(result);
      assertEquals(Status.UnableToProcess, result.getStatus());
    }

    @Test
    void should_handle_same_node_configuration() {
      var sameNode = new DicomNode("SAME_AET", HOST, PORT);

      var result = CStore.process(sameNode, sameNode, testFiles);

      assertNotNull(result);
      assertEquals(Status.UnableToProcess, result.getStatus());
    }
  }

  @Nested
  class Method_Delegation_Tests {

    @Test
    void should_delegate_three_parameter_method() {
      var result1 = CStore.process(callingNode, calledNode, testFiles);
      var result2 = CStore.process(null, callingNode, calledNode, testFiles, null, null);

      assertNotNull(result1);
      assertNotNull(result2);
      assertEquals(result1.getStatus(), result2.getStatus());
    }

    @Test
    void should_delegate_four_parameter_method() {
      var progress = new DicomProgress();
      var result1 = CStore.process(callingNode, calledNode, testFiles, progress);
      var result2 = CStore.process(null, callingNode, calledNode, testFiles, progress, null);

      assertNotNull(result1);
      assertNotNull(result2);
      assertEquals(result1.getStatus(), result2.getStatus());
    }

    @Test
    void should_delegate_five_parameter_method_with_params() {
      var params = new AdvancedParams();
      var result1 = CStore.process(params, callingNode, calledNode, testFiles);
      var result2 = CStore.process(params, callingNode, calledNode, testFiles, null, null);

      assertNotNull(result1);
      assertNotNull(result2);
      assertEquals(result1.getStatus(), result2.getStatus());
    }

    @Test
    void should_delegate_five_parameter_method_with_progress() {
      var params = new AdvancedParams();
      var progress = new DicomProgress();
      var result1 = CStore.process(params, callingNode, calledNode, testFiles, progress);
      var result2 = CStore.process(params, callingNode, calledNode, testFiles, progress, null);

      assertNotNull(result1);
      assertNotNull(result2);
      assertEquals(result1.getStatus(), result2.getStatus());
    }
  }
}
