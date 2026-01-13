/*
 * Copyright (c) 2014-2019 Weasis Team and other contributors.
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

import java.nio.file.Paths;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.VR;
import org.dcm4che3.net.Status;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@DisplayNameGeneration(ReplaceUnderscores.class)
@ExtendWith(MockitoExtension.class)
class DicomProgressTest {

  private DicomProgress progress;

  @Mock private ProgressListener mockListener;

  @BeforeEach
  void setUp() {
    progress = new DicomProgress();
  }

  @Nested
  class Initial_State_Tests {

    @Test
    void should_initialize_with_default_values() {
      assertNull(progress.getAttributes());
      assertFalse(progress.isCancelled());
      assertFalse(progress.isLastFailed());
      assertNull(progress.getProcessedFile());
      assertEquals(Status.Pending, progress.getStatus());
    }

    @Test
    void should_return_minus_one_for_all_operation_counts_when_no_attributes() {
      assertEquals(-1, progress.getNumberOfRemainingSuboperations());
      assertEquals(-1, progress.getNumberOfCompletedSuboperations());
      assertEquals(-1, progress.getNumberOfFailedSuboperations());
      assertEquals(-1, progress.getNumberOfWarningSuboperations());
    }

    @Test
    void should_return_null_error_comment_when_no_attributes() {
      assertNull(progress.getErrorComment());
    }
  }

  @Nested
  class Attributes_Management_Tests {

    @Test
    void should_set_and_get_attributes() {
      var attributes = createAttributes(Status.Success, 10, 5, 2, 1, "Test comment");

      progress.setAttributes(attributes);

      assertEquals(attributes, progress.getAttributes());
      assertEquals(Status.Success, progress.getStatus());
      assertEquals(10, progress.getNumberOfRemainingSuboperations());
      assertEquals(5, progress.getNumberOfCompletedSuboperations());
      assertEquals(2, progress.getNumberOfFailedSuboperations());
      assertEquals(1, progress.getNumberOfWarningSuboperations());
      assertEquals("Test comment", progress.getErrorComment());
    }

    @Test
    void should_handle_null_attributes() {
      progress.setAttributes(null);

      assertNull(progress.getAttributes());
      assertEquals(Status.Pending, progress.getStatus());
      assertEquals(-1, progress.getNumberOfRemainingSuboperations());
      assertNull(progress.getErrorComment());
    }

    @Test
    void should_detect_last_operation_failed_when_failure_count_increases() {
      var initialAttributes = createAttributes(Status.Pending, 10, 5, 2, 1, null);
      var updatedAttributes = createAttributes(Status.Pending, 9, 6, 3, 1, null);

      progress.setAttributes(initialAttributes);
      assertFalse(progress.isLastFailed());

      progress.setAttributes(updatedAttributes);
      assertTrue(progress.isLastFailed());
    }

    @Test
    void should_not_mark_as_failed_when_failure_count_remains_same() {
      var attributes1 = createAttributes(Status.Pending, 10, 5, 2, 1, null);
      var attributes2 = createAttributes(Status.Pending, 9, 6, 2, 1, null);

      progress.setAttributes(attributes1);
      progress.setAttributes(attributes2);

      assertFalse(progress.isLastFailed());
    }

    @Test
    void should_handle_attributes_with_missing_tags() {
      var attributes = new Attributes();
      attributes.setInt(Tag.Status, VR.US, Status.Success);
      // Only set status, leave other tags missing

      progress.setAttributes(attributes);

      assertEquals(Status.Success, progress.getStatus());
      assertEquals(-1, progress.getNumberOfRemainingSuboperations());
      assertEquals(-1, progress.getNumberOfCompletedSuboperations());
      assertEquals(-1, progress.getNumberOfFailedSuboperations());
      assertEquals(-1, progress.getNumberOfWarningSuboperations());
    }
  }

  @Nested
  class File_Management_Tests {

    @Test
    void should_set_and_get_processed_file() {
      var testPath = Paths.get("/test/file.dcm");

      progress.setProcessedFile(testPath);

      assertEquals(testPath, progress.getProcessedFile());
    }

    @Test
    void should_handle_null_processed_file() {
      progress.setProcessedFile(null);

      assertNull(progress.getProcessedFile());
    }

    @Test
    void should_update_processed_file() {
      var firstPath = Paths.get("/first/file.dcm");
      var secondPath = Paths.get("/second/file.dcm");

      progress.setProcessedFile(firstPath);
      assertEquals(firstPath, progress.getProcessedFile());

      progress.setProcessedFile(secondPath);
      assertEquals(secondPath, progress.getProcessedFile());
    }
  }

  @Nested
  class Cancellation_Tests {

    @Test
    void should_cancel_operation() {
      assertFalse(progress.isCancelled());

      progress.cancel();

      assertTrue(progress.isCancelled());
    }

    @Test
    void should_return_cancel_status_when_cancelled() {
      var attributes = createAttributes(Status.Success, 5, 3, 0, 0, null);
      progress.setAttributes(attributes);

      progress.cancel();

      assertEquals(Status.Cancel, progress.getStatus());
    }

    @Test
    void should_maintain_other_attributes_after_cancellation() {
      var testPath = Paths.get("/test/file.dcm");
      var attributes = createAttributes(Status.Success, 5, 3, 1, 0, "Error");

      progress.setProcessedFile(testPath);
      progress.setAttributes(attributes);
      progress.cancel();

      assertEquals(testPath, progress.getProcessedFile());
      assertEquals(attributes, progress.getAttributes());
      assertEquals(5, progress.getNumberOfRemainingSuboperations());
      assertEquals("Error", progress.getErrorComment());
    }
  }

  @Nested
  class Listener_Management_Tests {

    @Test
    void should_add_progress_listener() {
      progress.addProgressListener(mockListener);
      var attributes = createAttributes(Status.Success, 5, 3, 0, 0, null);

      progress.setAttributes(attributes);

      verify(mockListener).handleProgression(progress);
    }

    @Test
    void should_ignore_null_listener_when_adding() {
      assertDoesNotThrow(() -> progress.addProgressListener(null));
    }

    @Test
    void should_remove_progress_listener() {
      progress.addProgressListener(mockListener);
      progress.removeProgressListener(mockListener);
      var attributes = createAttributes(Status.Success, 5, 3, 0, 0, null);

      progress.setAttributes(attributes);

      verify(mockListener, never()).handleProgression(any());
    }

    @Test
    void should_handle_removing_null_listener() {
      assertDoesNotThrow(() -> progress.removeProgressListener(null));
    }

    @Test
    void should_notify_multiple_listeners() {
      var listener1 = mock(ProgressListener.class);
      var listener2 = mock(ProgressListener.class);

      progress.addProgressListener(listener1);
      progress.addProgressListener(listener2);

      var attributes = createAttributes(Status.Success, 5, 3, 0, 0, null);
      progress.setAttributes(attributes);

      verify(listener1).handleProgression(progress);
      verify(listener2).handleProgression(progress);
    }

    @Test
    void should_not_add_duplicate_listeners() {
      progress.addProgressListener(mockListener);
      progress.addProgressListener(mockListener);

      var attributes = createAttributes(Status.Success, 5, 3, 0, 0, null);
      progress.setAttributes(attributes);

      verify(mockListener, times(1)).handleProgression(progress);
    }
  }

  @Nested
  class Thread_Safety_Tests {

    @Test
    void should_handle_concurrent_listener_modifications() throws InterruptedException {
      var listenerCount = new AtomicInteger(0);
      var latch = new CountDownLatch(10);

      // Add listeners concurrently
      for (int i = 0; i < 10; i++) {
        new Thread(
                () -> {
                  try {
                    progress.addProgressListener(p -> listenerCount.incrementAndGet());
                    var attributes = createAttributes(Status.Success, 1, 0, 0, 0, null);
                    progress.setAttributes(attributes);
                  } finally {
                    latch.countDown();
                  }
                })
            .start();
      }

      assertTrue(latch.await(5, TimeUnit.SECONDS));
      assertTrue(listenerCount.get() > 0);
    }

    @Test
    void should_handle_concurrent_attribute_updates() throws InterruptedException {
      var updateCount = new AtomicInteger(0);
      var latch = new CountDownLatch(10);

      progress.addProgressListener(p -> updateCount.incrementAndGet());

      // Update attributes concurrently
      for (int i = 0; i < 10; i++) {
        final int index = i;
        new Thread(
                () -> {
                  try {
                    var attributes =
                        createAttributes(Status.Pending, index, 0, 0, 0, "Update " + index);
                    progress.setAttributes(attributes);
                  } finally {
                    latch.countDown();
                  }
                })
            .start();
      }

      assertTrue(latch.await(5, TimeUnit.SECONDS));
      assertEquals(10, updateCount.get());
    }

    @Test
    void should_handle_concurrent_cancellation_and_status_checks() throws InterruptedException {
      var cancelThread = new Thread(progress::cancel);
      var statusCheckResults = new AtomicReference<Boolean>();
      var statusThread =
          new Thread(
              () -> {
                // Give cancel thread a chance to run
                try {
                  Thread.sleep(50);
                } catch (InterruptedException e) {
                  Thread.currentThread().interrupt();
                }
                statusCheckResults.set(progress.isCancelled());
              });

      cancelThread.start();
      statusThread.start();

      cancelThread.join();
      statusThread.join();

      assertTrue(statusCheckResults.get());
    }
  }

  @Nested
  class Status_Behavior_Tests {

    @Test
    void should_prioritize_cancellation_over_attribute_status() {
      var attributes = createAttributes(Status.Success, 5, 3, 0, 0, null);
      progress.setAttributes(attributes);

      progress.cancel();

      assertEquals(Status.Cancel, progress.getStatus());
    }

    @Test
    void should_return_pending_status_with_no_attributes() {
      assertEquals(Status.Pending, progress.getStatus());
    }

    @Test
    void should_return_attribute_status_when_not_cancelled() {
      var attributes = createAttributes(Status.PendingWarning, 2, 8, 1, 2, null);

      progress.setAttributes(attributes);

      assertEquals(Status.PendingWarning, progress.getStatus());
    }
  }

  @Nested
  class Integration_Tests {

    @Test
    void should_handle_complete_workflow() {
      var progressEvents = new AtomicInteger(0);
      var lastFailedDetected = new AtomicBoolean(false);

      progress.addProgressListener(
          p -> {
            progressEvents.incrementAndGet();
            if (p.isLastFailed()) {
              lastFailedDetected.set(true);
            }
          });

      // Initial state
      var initialAttributes = createAttributes(Status.Pending, 10, 0, 0, 0, null);
      progress.setProcessedFile(Paths.get("/initial/file.dcm"));
      progress.setAttributes(initialAttributes);

      // Progress with failure
      var failureAttributes = createAttributes(Status.Pending, 8, 1, 1, 0, "Failed transfer");
      progress.setProcessedFile(Paths.get("/failed/file.dcm"));
      progress.setAttributes(failureAttributes);

      // Final completion
      var completionAttributes = createAttributes(Status.Success, 0, 10, 1, 0, null);
      progress.setAttributes(completionAttributes);

      assertEquals(3, progressEvents.get());
      assertTrue(lastFailedDetected.get());
      assertEquals(Status.Success, progress.getStatus());
      assertEquals(Paths.get("/failed/file.dcm"), progress.getProcessedFile());
    }
  }

  private Attributes createAttributes(
      int status, int remaining, int completed, int failed, int warnings, String errorComment) {
    var attributes = new Attributes();
    attributes.setInt(Tag.Status, VR.US, status);
    attributes.setInt(Tag.NumberOfRemainingSuboperations, VR.US, remaining);
    attributes.setInt(Tag.NumberOfCompletedSuboperations, VR.US, completed);
    attributes.setInt(Tag.NumberOfFailedSuboperations, VR.US, failed);
    attributes.setInt(Tag.NumberOfWarningSuboperations, VR.US, warnings);
    if (errorComment != null) {
      attributes.setString(Tag.ErrorComment, VR.LO, errorComment);
    }
    return attributes;
  }
}
