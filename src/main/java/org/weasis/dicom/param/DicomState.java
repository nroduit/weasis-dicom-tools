/*
 * Copyright (c) 2014-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at http://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.param;

import java.util.ArrayList;
import java.util.List;
import java.util.OptionalInt;
import org.dcm4che6.data.DicomObject;
import org.dcm4che6.net.Status;
import org.weasis.core.util.StringUtil;

public class DicomState {
  private volatile int status;
  private String message;
  private final List<DicomObject> dicomRSP;
  private final DicomProgress progress;

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
  }

  /**
   * Get the DICOM status @ see org.dcm4che3.net.Status
   *
   * @return the DICOM status of the process
   */
  public OptionalInt getStatus() {
    if (progress != null && progress.getAttributes() != null) {
      return progress.getStatus();
    }
    return OptionalInt.of(status);
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

  public List<DicomObject> getDicomRSP() {
    return dicomRSP;
  }

  public void addDicomRSP(DicomObject dicomRSP) {
    if (dicomRSP != null) {
      this.dicomRSP.add(dicomRSP);
    }
  }

  public static DicomState buildMessage(DicomState dcmState, String timeMessage, Exception e) {
    DicomState state = dcmState;
    if (state == null) {
      state = new DicomState(Status.UnableToProcess, null, null);
    }

    DicomProgress p = state.getProgress();
    OptionalInt s = state.getStatus();

    StringBuilder msg = new StringBuilder();

    boolean hasFailed = false;
    if (p != null) {
      int failed = p.getNumberOfFailedSuboperations().orElse(0);
      int warning = p.getNumberOfWarningSuboperations().orElse(0);
      int remaining = p.getNumberOfRemainingSuboperations().orElse(0);
      if (failed > 0) {
        hasFailed = true;
        msg.append(
            String.format(
                "%d/%d operations has failed.",
                failed, failed + p.getNumberOfCompletedSuboperations().orElse(0)));
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
      msg.append(e.getLocalizedMessage());
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

      if (s.isPresent()) {
        if (msg.length() > 0) {
          msg.append("\n");
        }
        msg.append("DICOM status");
        msg.append(StringUtil.COLON_AND_SPACE);
        msg.append(s.getAsInt());
      }
    }

    if (!hasFailed) {
      if (timeMessage != null) {
        msg.append(timeMessage);
      }
    } else {
      if (s.isPresent() && Status.isPending(s.getAsInt())) {
        state.setStatus(Status.UnableToProcess);
      }
    }
    state.setMessage(msg.toString());
    return state;
  }
}
