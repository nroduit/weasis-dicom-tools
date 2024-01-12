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

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.net.Status;
import org.weasis.core.util.StringUtil;

public class DicomState {
  private final List<Attributes> dicomRSP;
  private final DicomProgress progress;
  private final List<DicomParam> dicomMatchingKeys;

  private volatile int status;
  private String message;
  private String errorMessage;
  private LocalDateTime startConnectionDateTime;
  private LocalDateTime startTransferDateTime;
  private LocalDateTime endTransferDateTime;
  private long bytesSize;

  public DicomState() {
    this(Status.Pending, null, null);
  }

  public DicomState(DicomProgress progress) {
    this(Status.Pending, null, progress);
  }

  public DicomState(int status, String message, DicomProgress progress) {
    this.status = status;
    this.message = message;
    this.progress = progress;
    this.dicomRSP = new ArrayList<>();
    this.dicomMatchingKeys = new ArrayList<>();
    this.bytesSize = -1;
  }

  /**
   * Get the DICOM status @ see org.dcm4che3.net.Status
   *
   * @return the DICOM status of the process
   */
  public int getStatus() {
    if (progress != null && progress.getAttributes() != null) {
      return progress.getStatus();
    }
    return status;
  }

  /**
   * Set the DICOM status @ see org.dcm4che3.net.Status
   *
   * @param status DICOM status of the process
   */
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

  public List<Attributes> getDicomRSP() {
    return dicomRSP;
  }

  public List<DicomParam> getDicomMatchingKeys() {
    return dicomMatchingKeys;
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

  public void addDicomRSP(Attributes dicomRSP) {
    this.dicomRSP.add(dicomRSP);
  }

  public void addDicomMatchingKeys(DicomParam param) {
    this.dicomMatchingKeys.add(param);
  }

  public void addProcessTime(long startTimeStamp, long endTimeStamp) {
    addProcessTime(0, startTimeStamp, endTimeStamp);
  }

  public void addProcessTime(long connectionTimeStamp, long startTimeStamp, long endTimeStamp) {
    if (connectionTimeStamp > 0) {
      setStartConnectionDateTime(
          Instant.ofEpochMilli(connectionTimeStamp)
              .atZone(ZoneId.systemDefault())
              .toLocalDateTime());
    }
    if (startTimeStamp > 0) {
      setStartTransferDateTime(
          Instant.ofEpochMilli(startTimeStamp).atZone(ZoneId.systemDefault()).toLocalDateTime());
    }
    if (endTimeStamp > 0) {
      setEndTransferDateTime(
          Instant.ofEpochMilli(endTimeStamp).atZone(ZoneId.systemDefault()).toLocalDateTime());
    }
  }

  public static DicomState buildMessage(DicomState dcmState, String timeMessage, Exception e) {
    DicomState state = dcmState;
    if (state == null) {
      state = new DicomState(Status.UnableToProcess, null, null);
    }

    DicomProgress p = state.getProgress();
    int s = state.getStatus();

    StringBuilder msg = new StringBuilder();

    boolean hasFailed = false;
    if (p != null) {
      int failed = p.getNumberOfFailedSuboperations();
      int warning = p.getNumberOfWarningSuboperations();
      int remaining = p.getNumberOfRemainingSuboperations();
      if (failed > 0) {
        hasFailed = true;
        msg.append(
            String.format(
                "%d/%d operations has failed.",
                failed, failed + p.getNumberOfCompletedSuboperations()));
      } else if (remaining > 0) {
        msg.append(String.format("%d operations remains. ", remaining));
      } else if (warning > 0) {
        msg.append(String.format("%d operations has a warning status. ", warning));
      }
    }
    if (e != null) {
      hasFailed = true;
      if (msg.length() > 0) {
        msg.append(" ");
      }
      msg.append(e.getMessage());
      state.setErrorMessage(e.getMessage());
    }

    if (p != null && p.getAttributes() != null) {
      String error = p.getErrorComment();
      if (StringUtil.hasText(error)) {
        hasFailed = true;
        if (msg.length() > 0) {
          msg.append("\n");
        }
        msg.append("DICOM error");
        msg.append(StringUtil.COLON_AND_SPACE);
        msg.append(error);
      }

      if (!Status.isPending(s) && s != -1 && s != Status.Success && s != Status.Cancel) {
        if (msg.length() > 0) {
          msg.append("\n");
        }
        msg.append("DICOM status");
        msg.append(StringUtil.COLON_AND_SPACE);
        msg.append(s);
      }
    }

    if (!hasFailed) {
      if (timeMessage != null) {
        msg.append(timeMessage);
      }
    } else {
      if (Status.isPending(s) || s == -1) {
        state.setStatus(Status.UnableToProcess);
      }
    }
    state.setMessage(msg.toString());
    return state;
  }
}
