/*
 * Copyright (c) 2017-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.util;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.VR;
import org.dcm4che3.io.DicomInputStream;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.weasis.core.util.StreamUtil;
import org.weasis.dicom.param.DicomProgress;
import org.weasis.dicom.param.DicomState;
import org.weasis.dicom.util.ServiceUtil.ProgressStatus;

@DisplayNameGeneration(ReplaceUnderscores.class)
@ExtendWith(MockitoExtension.class)
class ServiceUtilTest {

  @Mock private DicomState dicomState;
  @Mock private DicomProgress dicomProgress;
  @Mock private DicomInputStream dicomInputStream;
  @Mock private AutoCloseable autoCloseable;
  @Mock private ExecutorService executorService;

  @TempDir Path tempDir;

  @Nested
  class Thread_Factory_Tests {

    @Test
    void getThreadFactory_creates_thread_with_custom_name() {
      var threadFactory = ServiceUtil.getThreadFactory("test-service");
      var thread = threadFactory.newThread(() -> {});

      assertNotNull(thread);
      assertTrue(thread.getName().startsWith("test-service-"));
    }

    @Test
    void getThreadFactory_throws_exception_for_null_name() {
      assertThrows(NullPointerException.class, () -> ServiceUtil.getThreadFactory(null));
    }

    @Test
    void getThreadFactory_creates_runnable_threads() {
      var threadFactory = ServiceUtil.getThreadFactory("worker");
      var executed = new boolean[1];
      var thread = threadFactory.newThread(() -> executed[0] = true);

      thread.run();
      assertTrue(executed[0]);
    }

    @Test
    void getThreadFactory_preserves_thread_properties() {
      var defaultFactory = Executors.defaultThreadFactory();
      var customFactory = ServiceUtil.getThreadFactory("custom");

      var defaultThread = defaultFactory.newThread(() -> {});
      var customThread = customFactory.newThread(() -> {});

      assertEquals(defaultThread.isDaemon(), customThread.isDaemon());
      assertEquals(defaultThread.getPriority(), customThread.getPriority());
      assertEquals(defaultThread.getThreadGroup(), customThread.getThreadGroup());
    }

    @Test
    void getThreadFactory_creates_unique_thread_names() {
      var threadFactory = ServiceUtil.getThreadFactory("unique");
      var thread1 = threadFactory.newThread(() -> {});
      var thread2 = threadFactory.newThread(() -> {});

      assertNotEquals(thread1.getName(), thread2.getName());
      assertTrue(thread1.getName().startsWith("unique-"));
      assertTrue(thread2.getName().startsWith("unique-"));
    }
  }

  @Nested
  class Executor_Service_Shutdown_Tests {

    @Test
    void shutdownService_safely_shuts_down_executor() throws InterruptedException {
      var executor = Executors.newSingleThreadExecutor();
      assertFalse(executor.isShutdown());

      ServiceUtil.shutdownService(executor);

      assertTrue(executor.isShutdown());
    }

    @Test
    void shutdownService_handles_null_executor_gracefully() {
      assertDoesNotThrow(() -> ServiceUtil.shutdownService(null));
    }

    @Test
    void shutdownService_handles_exception_during_shutdown() {
      doThrow(new RuntimeException("Shutdown failed")).when(executorService).shutdown();

      assertDoesNotThrow(() -> ServiceUtil.shutdownService(executorService));
      verify(executorService).shutdown();
    }

    @Test
    void shutdownService_does_not_call_shutdown_on_null() {
      ServiceUtil.shutdownService(null);
      verifyNoInteractions(executorService);
    }
  }

  @Nested
  class Force_Getting_Attributes_Tests {

    @Test
    void forceGettingAttributes_closes_resource_when_progress_exists() {
      when(dicomState.getProgress()).thenReturn(dicomProgress);

      try (var streamUtilMock = mockStatic(StreamUtil.class)) {
        ServiceUtil.forceGettingAttributes(dicomState, autoCloseable);

        streamUtilMock.verify(() -> StreamUtil.safeClose(autoCloseable));
      }
    }

    @Test
    void forceGettingAttributes_does_not_close_resource_when_no_progress() {
      when(dicomState.getProgress()).thenReturn(null);

      try (var streamUtilMock = mockStatic(StreamUtil.class)) {
        ServiceUtil.forceGettingAttributes(dicomState, autoCloseable);

        streamUtilMock.verifyNoInteractions();
      }
    }

    @Test
    void forceGettingAttributes_handles_multiple_calls_safely() {
      when(dicomState.getProgress()).thenReturn(dicomProgress);

      try (var streamUtilMock = mockStatic(StreamUtil.class)) {
        ServiceUtil.forceGettingAttributes(dicomState, autoCloseable);
        ServiceUtil.forceGettingAttributes(dicomState, autoCloseable);

        streamUtilMock.verify(() -> StreamUtil.safeClose(autoCloseable), times(2));
      }
    }
  }

  @Nested
  class Safe_Close_Tests {

    @Test
    void safeClose_handles_null_input_stream() {
      assertDoesNotThrow(() -> ServiceUtil.safeClose(null));
    }

    @Test
    void safeClose_deletes_bulk_data_files() throws IOException {
      var testFile1 = tempDir.resolve("bulk1.tmp");
      var testFile2 = tempDir.resolve("bulk2.tmp");
      Files.createFile(testFile1);
      Files.createFile(testFile2);

      var file1 = testFile1.toFile();
      var file2 = testFile2.toFile();
      when(dicomInputStream.getBulkDataFiles()).thenReturn(List.of(file1, file2));

      ServiceUtil.safeClose(dicomInputStream);

      assertFalse(Files.exists(testFile1));
      assertFalse(Files.exists(testFile2));
    }

    @Test
    void safeClose_handles_empty_bulk_data_files() {
      when(dicomInputStream.getBulkDataFiles()).thenReturn(List.of());

      assertDoesNotThrow(() -> ServiceUtil.safeClose(dicomInputStream));
    }

    @Test
    void safeClose_handles_missing_files_gracefully() {
      var nonExistentFile = tempDir.resolve("nonexistent.tmp").toFile();
      when(dicomInputStream.getBulkDataFiles()).thenReturn(List.of(nonExistentFile));

      assertDoesNotThrow(() -> ServiceUtil.safeClose(dicomInputStream));
    }

    @Test
    void safeClose_continues_deletion_after_failure() throws IOException {
      var readOnlyFile = tempDir.resolve("readonly.tmp");
      var normalFile = tempDir.resolve("normal.tmp");
      Files.createFile(readOnlyFile);
      Files.createFile(normalFile);

      // Make first file read-only (may not prevent deletion on all systems)
      readOnlyFile.toFile().setReadOnly();

      when(dicomInputStream.getBulkDataFiles())
          .thenReturn(List.of(readOnlyFile.toFile(), normalFile.toFile()));

      assertDoesNotThrow(() -> ServiceUtil.safeClose(dicomInputStream));

      // Normal file should be deleted regardless
      assertFalse(Files.exists(normalFile));
    }
  }

  @Nested
  class Notify_Progression_State_Tests {

    private static final String INSTANCE_UID = "1.2.3.4.5";
    private static final String CLASS_UID = "1.2.840.10008.5.1.4.1.1.1";
    private static final int STATUS_SUCCESS = 0x0000;
    private static final int SUB_OPERATIONS = 10;

    @Test
    void notifyProgression_updates_state_status() {
      when(dicomState.getProgress()).thenReturn(dicomProgress);
      when(dicomProgress.getAttributes()).thenReturn(null);

      ServiceUtil.notifyProgression(
          dicomState,
          INSTANCE_UID,
          CLASS_UID,
          STATUS_SUCCESS,
          ProgressStatus.COMPLETED,
          SUB_OPERATIONS);

      verify(dicomState).setStatus(STATUS_SUCCESS);
    }

    @Test
    void notifyProgression_creates_new_attributes_when_null() {
      when(dicomState.getProgress()).thenReturn(dicomProgress);
      when(dicomProgress.getAttributes()).thenReturn(null);

      ServiceUtil.notifyProgression(
          dicomState,
          INSTANCE_UID,
          CLASS_UID,
          STATUS_SUCCESS,
          ProgressStatus.COMPLETED,
          SUB_OPERATIONS);

      verify(dicomProgress).setAttributes(any(Attributes.class));
    }

    @Test
    void notifyProgression_uses_existing_attributes() {
      var existingAttributes = new Attributes();
      when(dicomState.getProgress()).thenReturn(dicomProgress);
      when(dicomProgress.getAttributes()).thenReturn(existingAttributes);
      when(dicomProgress.getNumberOfCompletedSuboperations()).thenReturn(5);
      when(dicomProgress.getNumberOfFailedSuboperations()).thenReturn(1);
      when(dicomProgress.getNumberOfWarningSuboperations()).thenReturn(2);

      ServiceUtil.notifyProgression(
          dicomState,
          INSTANCE_UID,
          CLASS_UID,
          STATUS_SUCCESS,
          ProgressStatus.COMPLETED,
          SUB_OPERATIONS);

      assertEquals(STATUS_SUCCESS, existingAttributes.getInt(Tag.Status, -1));
      assertEquals(INSTANCE_UID, existingAttributes.getString(Tag.AffectedSOPInstanceUID));
      assertEquals(CLASS_UID, existingAttributes.getString(Tag.AffectedSOPClassUID));
    }

    @Test
    void notifyProgression_handles_null_progress() {
      when(dicomState.getProgress()).thenReturn(null);

      assertDoesNotThrow(
          () ->
              ServiceUtil.notifyProgression(
                  dicomState,
                  INSTANCE_UID,
                  CLASS_UID,
                  STATUS_SUCCESS,
                  ProgressStatus.COMPLETED,
                  SUB_OPERATIONS));

      verify(dicomState).setStatus(STATUS_SUCCESS);
      verifyNoInteractions(dicomProgress);
    }

    @ParameterizedTest
    @EnumSource(ProgressStatus.class)
    void notifyProgression_handles_all_progress_statuses(ProgressStatus status) {
      when(dicomState.getProgress()).thenReturn(dicomProgress);
      when(dicomProgress.getAttributes()).thenReturn(null);

      assertDoesNotThrow(
          () ->
              ServiceUtil.notifyProgression(
                  dicomState, INSTANCE_UID, CLASS_UID, STATUS_SUCCESS, status, SUB_OPERATIONS));
    }
  }

  @Nested
  class Notify_Progression_Attributes_Tests {

    private static final int SUB_OPERATIONS = 10;

    @Test
    void notifyProgression_handles_null_progress_or_attributes() {
      assertDoesNotThrow(
          () ->
              ServiceUtil.notifyProgression(
                  null, new Attributes(), ProgressStatus.COMPLETED, SUB_OPERATIONS));
      assertDoesNotThrow(
          () ->
              ServiceUtil.notifyProgression(
                  dicomProgress, null, ProgressStatus.COMPLETED, SUB_OPERATIONS));
      assertDoesNotThrow(
          () ->
              ServiceUtil.notifyProgression(null, null, ProgressStatus.COMPLETED, SUB_OPERATIONS));
    }

    @Test
    void notifyProgression_initializes_counts_for_new_progress() {
      var attributes = new Attributes();
      when(dicomProgress.getAttributes()).thenReturn(null);

      ServiceUtil.notifyProgression(
          dicomProgress, attributes, ProgressStatus.COMPLETED, SUB_OPERATIONS);

      assertEquals(1, attributes.getInt(Tag.NumberOfCompletedSuboperations, 0));
      assertEquals(0, attributes.getInt(Tag.NumberOfFailedSuboperations, 0));
      assertEquals(0, attributes.getInt(Tag.NumberOfWarningSuboperations, 0));
      assertEquals(SUB_OPERATIONS - 1, attributes.getInt(Tag.NumberOfRemainingSuboperations, 0));
    }

    @Test
    void notifyProgression_updates_completed_count() {
      var attributes = new Attributes();
      when(dicomProgress.getAttributes()).thenReturn(new Attributes());
      when(dicomProgress.getNumberOfCompletedSuboperations()).thenReturn(3);
      when(dicomProgress.getNumberOfFailedSuboperations()).thenReturn(1);
      when(dicomProgress.getNumberOfWarningSuboperations()).thenReturn(0);

      ServiceUtil.notifyProgression(
          dicomProgress, attributes, ProgressStatus.COMPLETED, SUB_OPERATIONS);

      assertEquals(4, attributes.getInt(Tag.NumberOfCompletedSuboperations, 0));
    }

    @Test
    void notifyProgression_updates_failed_count() {
      var attributes = new Attributes();
      when(dicomProgress.getAttributes()).thenReturn(new Attributes());
      when(dicomProgress.getNumberOfCompletedSuboperations()).thenReturn(2);
      when(dicomProgress.getNumberOfFailedSuboperations()).thenReturn(1);
      when(dicomProgress.getNumberOfWarningSuboperations()).thenReturn(1);

      ServiceUtil.notifyProgression(
          dicomProgress, attributes, ProgressStatus.FAILED, SUB_OPERATIONS);

      assertEquals(2, attributes.getInt(Tag.NumberOfFailedSuboperations, 0));
    }

    @Test
    void notifyProgression_updates_warning_count() {
      var attributes = new Attributes();
      when(dicomProgress.getAttributes()).thenReturn(new Attributes());
      when(dicomProgress.getNumberOfCompletedSuboperations()).thenReturn(2);
      when(dicomProgress.getNumberOfFailedSuboperations()).thenReturn(0);
      when(dicomProgress.getNumberOfWarningSuboperations()).thenReturn(1);

      ServiceUtil.notifyProgression(
          dicomProgress, attributes, ProgressStatus.WARNING, SUB_OPERATIONS);

      assertEquals(2, attributes.getInt(Tag.NumberOfWarningSuboperations, 0));
    }

    @Test
    void notifyProgression_calculates_remaining_operations_correctly() {
      var attributes = new Attributes();
      when(dicomProgress.getAttributes()).thenReturn(new Attributes());
      when(dicomProgress.getNumberOfCompletedSuboperations()).thenReturn(5);
      when(dicomProgress.getNumberOfFailedSuboperations()).thenReturn(2);
      when(dicomProgress.getNumberOfWarningSuboperations()).thenReturn(1);

      ServiceUtil.notifyProgression(
          dicomProgress, attributes, ProgressStatus.COMPLETED, SUB_OPERATIONS);

      // Remaining should be: total - (completed + failed + warning) - 1 = 10 - (5 + 2 + 1) - 1 = 1
      assertEquals(1, attributes.getInt(Tag.NumberOfRemainingSuboperations, 0));
    }

    @Test
    void notifyProgression_ensures_minimum_remaining_operations() {
      var attributes = new Attributes();
      when(dicomProgress.getAttributes()).thenReturn(new Attributes());
      when(dicomProgress.getNumberOfCompletedSuboperations()).thenReturn(8);
      when(dicomProgress.getNumberOfFailedSuboperations()).thenReturn(2);
      when(dicomProgress.getNumberOfWarningSuboperations()).thenReturn(1);

      ServiceUtil.notifyProgression(
          dicomProgress, attributes, ProgressStatus.COMPLETED, SUB_OPERATIONS);

      // Should be at least 0 (minimum remaining - 1)
      assertEquals(0, attributes.getInt(Tag.NumberOfRemainingSuboperations, 0));
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 5, 10, 100})
    void notifyProgression_handles_different_sub_operation_counts(int subOps) {
      var attributes = new Attributes();
      when(dicomProgress.getAttributes()).thenReturn(null);

      ServiceUtil.notifyProgression(dicomProgress, attributes, ProgressStatus.COMPLETED, subOps);

      assertEquals(1, attributes.getInt(Tag.NumberOfCompletedSuboperations, 0));
      assertEquals(subOps - 1, attributes.getInt(Tag.NumberOfRemainingSuboperations, 0));
    }
  }

  @Nested
  class Get_Total_Suboperations_Tests {

    @Test
    void getTotalOfSuboperations_returns_zero_for_null_attributes() {
      assertEquals(0, ServiceUtil.getTotalOfSuboperations(null));
    }

    @Test
    void getTotalOfSuboperations_calculates_total_correctly() {
      var attributes = new Attributes();
      attributes.setInt(Tag.NumberOfCompletedSuboperations, VR.US, 5);
      attributes.setInt(Tag.NumberOfFailedSuboperations, VR.US, 2);
      attributes.setInt(Tag.NumberOfWarningSuboperations, VR.US, 1);
      attributes.setInt(Tag.NumberOfRemainingSuboperations, VR.US, 2);

      assertEquals(10, ServiceUtil.getTotalOfSuboperations(attributes));
    }

    @Test
    void getTotalOfSuboperations_handles_missing_attributes() {
      var attributes = new Attributes();
      // No operation count attributes set

      assertEquals(0, ServiceUtil.getTotalOfSuboperations(attributes));
    }

    @Test
    void getTotalOfSuboperations_handles_partial_attributes() {
      var attributes = new Attributes();
      attributes.setInt(Tag.NumberOfCompletedSuboperations, VR.US, 3);
      attributes.setInt(Tag.NumberOfFailedSuboperations, VR.US, 1);
      // Missing warning and remaining counts

      assertEquals(4, ServiceUtil.getTotalOfSuboperations(attributes));
    }

    @Test
    void getTotalOfSuboperations_handles_zero_values() {
      var attributes = new Attributes();
      attributes.setInt(Tag.NumberOfCompletedSuboperations, VR.US, 0);
      attributes.setInt(Tag.NumberOfFailedSuboperations, VR.US, 0);
      attributes.setInt(Tag.NumberOfWarningSuboperations, VR.US, 0);
      attributes.setInt(Tag.NumberOfRemainingSuboperations, VR.US, 0);

      assertEquals(0, ServiceUtil.getTotalOfSuboperations(attributes));
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 10, 100, 1000})
    void getTotalOfSuboperations_handles_various_totals(int expectedTotal) {
      var attributes = new Attributes();
      var completed = expectedTotal / 4;
      var failed = expectedTotal / 4;
      var warning = expectedTotal / 4;
      var remaining = expectedTotal - completed - failed - warning;

      attributes.setInt(Tag.NumberOfCompletedSuboperations, VR.US, completed);
      attributes.setInt(Tag.NumberOfFailedSuboperations, VR.US, failed);
      attributes.setInt(Tag.NumberOfWarningSuboperations, VR.US, warning);
      attributes.setInt(Tag.NumberOfRemainingSuboperations, VR.US, remaining);

      assertEquals(expectedTotal, ServiceUtil.getTotalOfSuboperations(attributes));
    }
  }

  @Nested
  class Progress_Status_Tests {

    @Test
    void progressStatus_has_expected_values() {
      var values = ProgressStatus.values();
      assertEquals(3, values.length);

      var statusNames = List.of("FAILED", "WARNING", "COMPLETED");
      for (var status : values) {
        assertTrue(statusNames.contains(status.name()));
      }
    }

    @ParameterizedTest
    @EnumSource(ProgressStatus.class)
    void progressStatus_valueOf_works_correctly(ProgressStatus status) {
      assertEquals(status, ProgressStatus.valueOf(status.name()));
    }

    @Test
    void progressStatus_ordinal_values_are_stable() {
      assertEquals(0, ProgressStatus.FAILED.ordinal());
      assertEquals(1, ProgressStatus.WARNING.ordinal());
      assertEquals(2, ProgressStatus.COMPLETED.ordinal());
    }
  }

  @Nested
  class Integration_Tests {

    @Test
    void complete_workflow_with_real_objects() throws InterruptedException, ExecutionException {
      // Test complete workflow with actual objects
      var threadFactory = ServiceUtil.getThreadFactory("integration-test");
      var executor = Executors.newSingleThreadExecutor(threadFactory);
      var attributes = new Attributes();

      // Simulate some work
      var future =
          executor.submit(
              () -> {
                attributes.setString(Tag.StudyInstanceUID, VR.UI, "1.2.3.4.5");
                return "completed";
              });

      // Wait for completion
      var result = future.get();
      assertEquals("completed", result);

      // Test total calculation
      attributes.setInt(Tag.NumberOfCompletedSuboperations, VR.US, 5);
      attributes.setInt(Tag.NumberOfFailedSuboperations, VR.US, 1);
      attributes.setInt(Tag.NumberOfWarningSuboperations, VR.US, 2);
      attributes.setInt(Tag.NumberOfRemainingSuboperations, VR.US, 2);

      assertEquals(10, ServiceUtil.getTotalOfSuboperations(attributes));

      // Clean shutdown
      ServiceUtil.shutdownService(executor);
      assertTrue(executor.isShutdown());
    }

    @Test
    void thread_factory_integration_with_executor_service()
        throws InterruptedException, ExecutionException {
      var threadFactory = ServiceUtil.getThreadFactory("worker-pool");
      var executor = Executors.newFixedThreadPool(2, threadFactory);

      var completed = new int[1];
      var tasks =
          List.of(
              executor.submit(() -> completed[0]++),
              executor.submit(() -> completed[0]++),
              executor.submit(() -> completed[0]++));

      // Wait for all tasks
      for (var task : tasks) {
        task.get();
      }

      assertEquals(3, completed[0]);

      ServiceUtil.shutdownService(executor);
      assertTrue(executor.awaitTermination(1, TimeUnit.SECONDS));
    }
  }
}
