/*
 * Copyright (c) 2014-2020 Weasis Team and other contributors.
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

import java.time.LocalDateTime;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.VR;
import org.dcm4che3.net.Status;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayNameGeneration(ReplaceUnderscores.class)
class DicomStateTest {

  @Mock private DicomProgress mockProgress;

  @Nested
  class Constructor_Tests {

    @Test
    void default_constructor_creates_state_with_pending_status() {
      var state = new DicomState();

      assertEquals(Status.Pending, state.getStatus());
      assertNull(state.getMessage());
      assertNull(state.getProgress());
      assertTrue(state.getDicomRSP().isEmpty());
      assertTrue(state.getDicomMatchingKeys().isEmpty());
      assertEquals(-1, state.getBytesSize());
    }

    @Test
    void constructor_with_progress_creates_state_with_pending_status() {
      var state = new DicomState(mockProgress);

      assertEquals(Status.Pending, state.getStatus());
      assertNull(state.getMessage());
      assertSame(mockProgress, state.getProgress());
      assertTrue(state.getDicomRSP().isEmpty());
      assertTrue(state.getDicomMatchingKeys().isEmpty());
      assertEquals(-1, state.getBytesSize());
    }

    @Test
    void full_constructor_initializes_all_fields() {
      var initialMessage = "Initial message";
      var state = new DicomState(Status.Success, initialMessage, mockProgress);

      assertEquals(Status.Success, state.getStatus());
      assertEquals(initialMessage, state.getMessage());
      assertSame(mockProgress, state.getProgress());
      assertTrue(state.getDicomRSP().isEmpty());
      assertTrue(state.getDicomMatchingKeys().isEmpty());
      assertEquals(-1, state.getBytesSize());
    }
  }

  @Nested
  class Status_Tests {

    @Test
    void getStatus_returns_progress_status_when_progress_has_attributes() {
      var progressStatus = Status.Success;
      var attributes = createTestAttributes();

      when(mockProgress.getAttributes()).thenReturn(attributes);
      when(mockProgress.getStatus()).thenReturn(progressStatus);

      var state = new DicomState(Status.Pending, null, mockProgress);

      assertEquals(progressStatus, state.getStatus());
    }

    @Test
    void getStatus_returns_internal_status_when_progress_is_null() {
      var internalStatus = Status.Success;
      var state = new DicomState(internalStatus, null, null);

      assertEquals(internalStatus, state.getStatus());
    }

    @Test
    void getStatus_returns_internal_status_when_progress_has_no_attributes() {
      var internalStatus = Status.Pending;
      when(mockProgress.getAttributes()).thenReturn(null);

      var state = new DicomState(internalStatus, null, mockProgress);

      assertEquals(internalStatus, state.getStatus());
    }

    @Test
    void setStatus_updates_internal_status() {
      var state = new DicomState();
      var newStatus = Status.Success;

      state.setStatus(newStatus);

      assertEquals(newStatus, state.getStatus());
    }
  }

  @Nested
  class Message_Tests {

    @Test
    void message_operations_are_thread_safe() throws InterruptedException {
      var state = new DicomState();
      var threadCount = 10;
      var executorService = Executors.newFixedThreadPool(threadCount);

      for (int i = 0; i < threadCount; i++) {
        final var messageIndex = i;
        executorService.submit(
            () -> {
              state.setMessage("Message " + messageIndex);
              assertNotNull(state.getMessage());
            });
      }

      executorService.shutdown();
      assertTrue(executorService.awaitTermination(5, TimeUnit.SECONDS));
      assertNotNull(state.getMessage());
    }

    @Test
    void setMessage_and_getMessage_work_correctly() {
      var state = new DicomState();
      var testMessage = "Test message";

      state.setMessage(testMessage);

      assertEquals(testMessage, state.getMessage());
    }
  }

  @Nested
  class DateTime_Tests {

    @Test
    void datetime_setters_and_getters_work_correctly() {
      var state = new DicomState();
      var now = LocalDateTime.now();

      state.setStartConnectionDateTime(now);
      state.setStartTransferDateTime(now.plusMinutes(1));
      state.setEndTransferDateTime(now.plusMinutes(2));

      assertEquals(now, state.getStartConnectionDateTime());
      assertEquals(now.plusMinutes(1), state.getStartTransferDateTime());
      assertEquals(now.plusMinutes(2), state.getEndTransferDateTime());
    }

    @Test
    void addProcessTime_with_two_timestamps_sets_transfer_times() {
      var state = new DicomState();
      var startTime = System.currentTimeMillis();
      var endTime = startTime + 1000;

      state.addProcessTime(startTime, endTime);

      assertNotNull(state.getStartTransferDateTime());
      assertNotNull(state.getEndTransferDateTime());
      assertNull(state.getStartConnectionDateTime());
      assertTrue(state.getEndTransferDateTime().isAfter(state.getStartTransferDateTime()));
    }

    @Test
    void addProcessTime_with_three_timestamps_sets_all_times() {
      var state = new DicomState();
      var connectionTime = System.currentTimeMillis();
      var startTime = connectionTime + 500;
      var endTime = startTime + 1000;

      state.addProcessTime(connectionTime, startTime, endTime);

      assertNotNull(state.getStartConnectionDateTime());
      assertNotNull(state.getStartTransferDateTime());
      assertNotNull(state.getEndTransferDateTime());

      assertTrue(state.getStartTransferDateTime().isAfter(state.getStartConnectionDateTime()));
      assertTrue(state.getEndTransferDateTime().isAfter(state.getStartTransferDateTime()));
    }

    @Test
    void addProcessTime_ignores_zero_or_negative_timestamps() {
      var state = new DicomState();

      state.addProcessTime(0, -1, 0);

      assertNull(state.getStartConnectionDateTime());
      assertNull(state.getStartTransferDateTime());
      assertNull(state.getEndTransferDateTime());
    }
  }

  @Nested
  class Collection_Tests {

    @Test
    void getDicomRSP_returns_immutable_copy() {
      var state = new DicomState();
      var attributes = createTestAttributes();

      state.addDicomRSP(attributes);
      var rspList = state.getDicomRSP();

      assertEquals(1, rspList.size());
      assertThrows(UnsupportedOperationException.class, () -> rspList.add(new Attributes()));
    }

    @Test
    void getDicomMatchingKeys_returns_immutable_copy() {
      var state = new DicomState();
      var param = new DicomParam(Tag.PatientID, "12345");

      state.addDicomMatchingKeys(param);
      var keysList = state.getDicomMatchingKeys();

      assertEquals(1, keysList.size());
      assertThrows(UnsupportedOperationException.class, () -> keysList.add(param));
    }

    @Test
    void addDicomRSP_ignores_null_values() {
      var state = new DicomState();

      state.addDicomRSP(null);

      assertTrue(state.getDicomRSP().isEmpty());
    }

    @Test
    void addDicomMatchingKeys_ignores_null_values() {
      var state = new DicomState();

      state.addDicomMatchingKeys(null);

      assertTrue(state.getDicomMatchingKeys().isEmpty());
    }

    @Test
    void addDicomRSP_adds_valid_attributes() {
      var state = new DicomState();
      var attributes = createTestAttributes();

      state.addDicomRSP(attributes);

      assertEquals(1, state.getDicomRSP().size());
      assertEquals(attributes, state.getDicomRSP().get(0));
    }

    @Test
    void addDicomMatchingKeys_adds_valid_parameters() {
      var state = new DicomState();
      var param = new DicomParam(Tag.PatientID, "12345");

      state.addDicomMatchingKeys(param);

      assertEquals(1, state.getDicomMatchingKeys().size());
      assertEquals(param, state.getDicomMatchingKeys().get(0));
    }
  }

  @Nested
  class BytesSize_Tests {

    @Test
    void bytesSize_default_value_is_negative_one() {
      var state = new DicomState();

      assertEquals(-1, state.getBytesSize());
    }

    @Test
    void setBytesSize_updates_value() {
      var state = new DicomState();
      var size = 1024L;

      state.setBytesSize(size);

      assertEquals(size, state.getBytesSize());
    }
  }

  @Nested
  class ErrorMessage_Tests {

    @Test
    void errorMessage_starts_as_null() {
      var state = new DicomState();

      assertNull(state.getErrorMessage());
    }

    @Test
    void setErrorMessage_updates_value() {
      var state = new DicomState();
      var errorMsg = "Test error";

      state.setErrorMessage(errorMsg);

      assertEquals(errorMsg, state.getErrorMessage());
    }
  }

  @Nested
  class BuildMessage_Tests {

    @Test
    void buildMessage_creates_new_state_when_null_provided() {
      var result = DicomState.buildMessage(null, null, null);

      assertNotNull(result);
      assertEquals(Status.UnableToProcess, result.getStatus());
    }

    @Test
    void buildMessage_uses_existing_state_when_provided() {
      var originalState = new DicomState(Status.Success, "Original", null);

      var result = DicomState.buildMessage(originalState, null, null);

      assertSame(originalState, result);
      assertEquals(Status.Success, result.getStatus());
    }

    @Test
    void buildMessage_appends_time_message_when_no_failures() {
      var state = new DicomState(Status.Success, null, null);
      var timeMessage = "Operation completed in 100ms";

      var result = DicomState.buildMessage(state, timeMessage, null);

      assertEquals(timeMessage, result.getMessage());
    }

    @Test
    void buildMessage_handles_exception_information() {
      var state = new DicomState();
      var exception = new RuntimeException("Test exception");

      var result = DicomState.buildMessage(state, null, exception);

      assertTrue(result.getMessage().contains("Test exception"));
      assertEquals("Test exception", result.getErrorMessage());
      assertEquals(Status.UnableToProcess, result.getStatus());
    }

    @Test
    void buildMessage_handles_progress_with_failed_operations() {
      when(mockProgress.getNumberOfFailedSuboperations()).thenReturn(2);
      when(mockProgress.getNumberOfCompletedSuboperations()).thenReturn(3);
      when(mockProgress.getNumberOfWarningSuboperations()).thenReturn(0);
      when(mockProgress.getNumberOfRemainingSuboperations()).thenReturn(0);

      var state = new DicomState(Status.Pending, null, mockProgress);

      var result = DicomState.buildMessage(state, null, null);

      assertTrue(result.getMessage().contains("2/5 operations has failed"));
      assertEquals(Status.UnableToProcess, result.getStatus());
    }

    @Test
    void buildMessage_handles_progress_with_remaining_operations() {
      when(mockProgress.getNumberOfFailedSuboperations()).thenReturn(0);
      when(mockProgress.getNumberOfRemainingSuboperations()).thenReturn(3);

      var state = new DicomState(Status.Pending, null, mockProgress);

      var result = DicomState.buildMessage(state, null, null);

      assertTrue(result.getMessage().contains("3 operations remains"));
    }

    @Test
    void buildMessage_handles_progress_with_warning_operations() {
      when(mockProgress.getNumberOfFailedSuboperations()).thenReturn(0);
      when(mockProgress.getNumberOfRemainingSuboperations()).thenReturn(0);
      when(mockProgress.getNumberOfWarningSuboperations()).thenReturn(1);

      var state = new DicomState(Status.Pending, null, mockProgress);

      var result = DicomState.buildMessage(state, null, null);

      assertTrue(result.getMessage().contains("1 operations has a warning status"));
    }

    @Test
    void buildMessage_handles_dicom_error_comment() {
      var errorComment = "DICOM protocol error";
      var attributes = createTestAttributes();

      when(mockProgress.getAttributes()).thenReturn(attributes);
      when(mockProgress.getErrorComment()).thenReturn(errorComment);

      var state = new DicomState(Status.Pending, null, mockProgress);

      var result = DicomState.buildMessage(state, null, null);

      assertTrue(result.getMessage().contains("DICOM error: " + errorComment));
    }

    @Test
    void buildMessage_handles_error_status() {
      var attributes = createTestAttributes();
      var errorStatus = Status.ProcessingFailure;

      when(mockProgress.getAttributes()).thenReturn(attributes);
      when(mockProgress.getStatus()).thenReturn(errorStatus);

      var state = new DicomState(errorStatus, null, mockProgress);

      var result = DicomState.buildMessage(state, null, null);

      assertTrue(result.getMessage().contains("DICOM status: " + errorStatus));
    }

    @Test
    void buildMessage_ignores_success_status() {
      var attributes = createTestAttributes();

      when(mockProgress.getAttributes()).thenReturn(attributes);
      when(mockProgress.getStatus()).thenReturn(Status.Success);

      var state = new DicomState(Status.Success, null, mockProgress);

      var result = DicomState.buildMessage(state, "Success message", null);

      assertEquals("Success message", result.getMessage());
    }

    @Test
    void buildMessage_combines_multiple_error_sources() {
      var errorComment = "Network error";
      var exception = new RuntimeException("Connection failed");
      var attributes = createTestAttributes();

      when(mockProgress.getAttributes()).thenReturn(attributes);
      when(mockProgress.getErrorComment()).thenReturn(errorComment);
      when(mockProgress.getNumberOfFailedSuboperations()).thenReturn(1);
      when(mockProgress.getNumberOfCompletedSuboperations()).thenReturn(0);

      var state = new DicomState(Status.ProcessingFailure, null, mockProgress);

      var result = DicomState.buildMessage(state, null, exception);

      var message = result.getMessage();
      assertTrue(message.contains("1/1 operations has failed"));
      assertTrue(message.contains("Connection failed"));
      assertTrue(message.contains("DICOM error: Network error"));
      assertEquals("Connection failed", result.getErrorMessage());
    }
  }

  private Attributes createTestAttributes() {
    var attrs = new Attributes();
    attrs.setString(Tag.PatientID, VR.LO, "TEST123");
    attrs.setString(Tag.StudyInstanceUID, VR.UI, "1.2.3.4.5.6.7");
    attrs.setString(Tag.SeriesInstanceUID, VR.UI, "1.2.3.4.5.6.8");
    attrs.setString(Tag.SOPInstanceUID, VR.UI, "1.2.3.4.5.6.9");
    return attrs;
  }
}
