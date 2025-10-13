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

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.net.Status;
import org.weasis.core.util.StringUtil;

/**
 * Represents the state of a DICOM operation, including progress, status, timing information, and
 * results. This class is thread-safe for message operations and provides comprehensive tracking of
 * DICOM operations execution.
 *
 * @since 5.0
 */
public class DicomState {
  private static final int INVALID_STATUS_FLAG = -1;

  private final List<Attributes> dicomRSP;
  private final DicomProgress progress;
  private final List<DicomParam> dicomMatchingKeys;

  private volatile int status;
  private volatile String message;
  private volatile String errorMessage;
  private LocalDateTime startConnectionDateTime;
  private LocalDateTime startTransferDateTime;
  private LocalDateTime endTransferDateTime;
  private long bytesSize = -1;

  /** Creates a new DicomState with pending status. */
  public DicomState() {
    this(Status.Pending, null, null);
  }

  /** Creates a new DicomState with pending status and specified progress. */
  public DicomState(DicomProgress progress) {
    this(Status.Pending, null, progress);
  }

  /**
   * Creates a DicomState with specified parameters.
   *
   * @param status the initial DICOM status
   * @param message the initial message
   * @param progress the progress handler (may be null)
   */
  public DicomState(int status, String message, DicomProgress progress) {
    this.status = status;
    this.message = message;
    this.progress = progress;
    this.dicomRSP = new ArrayList<>();
    this.dicomMatchingKeys = new ArrayList<>();
  }

  /** Returns the current DICOM status, prioritizing progress status if available. */
  public int getStatus() {
    if (progress != null && progress.getAttributes() != null) {
      return progress.getStatus();
    }
    return status;
  }

  /** Sets the DICOM status. */
  public void setStatus(int status) {
    this.status = status;
  }

  public synchronized String getMessage() {
    return message;
  }

  public synchronized void setMessage(String message) {
    this.message = message;
  }

  public DicomProgress getProgress() {
    return progress;
  }

  /** Returns an unmodifiable view of DICOM responses. */
  public List<Attributes> getDicomRSP() {
    return List.copyOf(dicomRSP);
  }

  /** Returns an unmodifiable view of DICOM matching keys. */
  public List<DicomParam> getDicomMatchingKeys() {
    return List.copyOf(dicomMatchingKeys);
  }

  public LocalDateTime getStartTransferDateTime() {
    return startTransferDateTime;
  }

  public void setStartTransferDateTime(LocalDateTime startTransferDateTime) {
    this.startTransferDateTime = startTransferDateTime;
  }

  public LocalDateTime getEndTransferDateTime() {
    return endTransferDateTime;
  }

  public void setEndTransferDateTime(LocalDateTime endTransferDateTime) {
    this.endTransferDateTime = endTransferDateTime;
  }

  public LocalDateTime getStartConnectionDateTime() {
    return startConnectionDateTime;
  }

  public void setStartConnectionDateTime(LocalDateTime startConnectionDateTime) {
    this.startConnectionDateTime = startConnectionDateTime;
  }

  public String getErrorMessage() {
    return errorMessage;
  }

  public void setErrorMessage(String errorMessage) {
    this.errorMessage = errorMessage;
  }

  public long getBytesSize() {
    return bytesSize;
  }

  public void setBytesSize(long bytesSize) {
    this.bytesSize = bytesSize;
  }

  public Duration getProcessTime() {
    if (startTransferDateTime != null && endTransferDateTime != null) {
      return Duration.between(startTransferDateTime, endTransferDateTime);
    }
    return null;
  }

  /** Adds a DICOM response to the internal list. */
  public void addDicomRSP(Attributes dicomRSP) {
    if (dicomRSP != null) {
      this.dicomRSP.add(dicomRSP);
    }
  }

  /** Adds a DICOM parameter to the matching keys list. */
  public void addDicomMatchingKeys(DicomParam param) {
    if (param != null) {
      this.dicomMatchingKeys.add(param);
    }
  }

  /** Sets process timing with start and end timestamps. */
  public void addProcessTime(long startTimeStamp, long endTimeStamp) {
    addProcessTime(0, startTimeStamp, endTimeStamp);
  }

  /** Sets process timing with connection, start, and end timestamps. */
  public void addProcessTime(long connectionTimeStamp, long startTimeStamp, long endTimeStamp) {
    if (connectionTimeStamp > 0) {
      setStartConnectionDateTime(timestampToDateTime(connectionTimeStamp));
    }
    if (startTimeStamp > 0) {
      setStartTransferDateTime(timestampToDateTime(startTimeStamp));
    }
    if (endTimeStamp > 0) {
      setEndTransferDateTime(timestampToDateTime(endTimeStamp));
    }
  }

  private LocalDateTime timestampToDateTime(long timestamp) {
    return Instant.ofEpochMilli(timestamp).atZone(ZoneId.systemDefault()).toLocalDateTime();
  }

  /**
   * Builds a comprehensive message for the DicomState based on progress, errors, and timing.
   *
   * @param dcmState the current state (created if null)
   * @param timeMessage optional timing message
   * @param exception optional exception information
   * @return the updated DicomState with built message
   */
  public static DicomState buildMessage(
      DicomState dcmState, String timeMessage, Exception exception) {
    DicomState state =
        Objects.requireNonNullElse(dcmState, new DicomState(Status.UnableToProcess, null, null));

    var messageBuilder = new StringBuilder();
    boolean hasFailed = appendProgressInfo(state.getProgress(), messageBuilder);
    hasFailed |= appendExceptionInfo(exception, messageBuilder, state);
    hasFailed |= appendDicomErrorInfo(state.getProgress(), state.getStatus(), messageBuilder);

    appendFinalMessage(hasFailed, timeMessage, messageBuilder, state);
    state.setMessage(messageBuilder.toString());
    return state;
  }

  private static boolean appendProgressInfo(DicomProgress progress, StringBuilder messageBuilder) {
    if (progress == null) return false;

    int failed = progress.getNumberOfFailedSuboperations();
    int warning = progress.getNumberOfWarningSuboperations();
    int remaining = progress.getNumberOfRemainingSuboperations();
    int completed = progress.getNumberOfCompletedSuboperations();

    if (failed > 0) {
      messageBuilder.append(
          String.format("%d/%d operations has failed.", failed, failed + completed));
      return true;
    } else if (remaining > 0) {
      messageBuilder.append(String.format("%d operations remains. ", remaining));
    } else if (warning > 0) {
      messageBuilder.append(String.format("%d operations has a warning status. ", warning));
    }
    return false;
  }

  private static boolean appendExceptionInfo(
      Exception exception, StringBuilder messageBuilder, DicomState state) {
    if (exception == null) return false;

    if (!messageBuilder.isEmpty()) {
      messageBuilder.append(" ");
    }
    messageBuilder.append(exception.getMessage());
    state.setErrorMessage(exception.getMessage());
    return true;
  }

  private static boolean appendDicomErrorInfo(
      DicomProgress progress, int status, StringBuilder messageBuilder) {
    if (progress == null || progress.getAttributes() == null) return false;

    boolean hasFailed = false;
    String error = progress.getErrorComment();
    if (StringUtil.hasText(error)) {
      hasFailed = true;
      if (!messageBuilder.isEmpty()) {
        messageBuilder.append("\n");
      }
      messageBuilder.append("DICOM error").append(StringUtil.COLON_AND_SPACE).append(error);
    }

    if (isErrorStatus(status)) {
      if (!messageBuilder.isEmpty()) {
        messageBuilder.append("\n");
      }
      messageBuilder.append("DICOM status").append(StringUtil.COLON_AND_SPACE).append(status);
    }
    return hasFailed;
  }

  private static boolean isErrorStatus(int status) {
    return !Status.isPending(status)
        && status != INVALID_STATUS_FLAG
        && status != Status.Success
        && status != Status.Cancel;
  }

  private static void appendFinalMessage(
      boolean hasFailed, String timeMessage, StringBuilder messageBuilder, DicomState state) {
    if (!hasFailed && timeMessage != null) {
      messageBuilder.append(timeMessage);
    } else if (hasFailed) {
      int status = state.getStatus();
      if (Status.isPending(status) || status == INVALID_STATUS_FLAG) {
        state.setStatus(Status.UnableToProcess);
      }
    }
  }
}
