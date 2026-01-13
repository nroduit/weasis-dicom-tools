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

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.dcm4che3.net.Status;

/** Thread-safe progress tracking for DICOM operations with cancellation support. */
public class DicomProgress implements CancelListener {

  private final List<ProgressListener> listeners = new CopyOnWriteArrayList<>();
  private volatile Attributes attributes;
  private volatile boolean cancelled;
  private volatile Path processedFile;
  private volatile boolean lastFailed;

  /**
   * Gets the current DICOM attributes associated with this progress.
   *
   * @return the attributes or null if none set
   */
  public Attributes getAttributes() {
    return attributes;
  }

  /**
   * Sets the DICOM attributes and notifies all listeners.
   *
   * @param attributes the new attributes to set
   */
  public void setAttributes(Attributes attributes) {
    var previousFailed = getFailedCount();
    this.attributes = attributes;
    this.lastFailed = previousFailed >= 0 && previousFailed < getFailedCount();

    notifyListeners();
  }

  /**
   * Checks if the last operation failed.
   *
   * @return true if the last operation resulted in failure
   */
  public boolean isLastFailed() {
    return lastFailed;
  }

  /**
   * Gets the currently processed file.
   *
   * @return the processed file path or null if none set
   */
  public Path getProcessedFile() {
    return processedFile;
  }

  /**
   * Sets the currently processed file.
   *
   * @param processedFile the file being processed
   */
  public void setProcessedFile(Path processedFile) {
    this.processedFile = processedFile;
  }

  /**
   * Adds a progress listener.
   *
   * @param listener the listener to add (null values are ignored)
   */
  public void addProgressListener(ProgressListener listener) {
    if (listener != null && !listeners.contains(listener)) {
      listeners.add(listener);
    }
  }

  /**
   * Removes a progress listener.
   *
   * @param listener the listener to remove
   */
  public void removeProgressListener(ProgressListener listener) {
    listeners.remove(listener);
  }

  @Override
  public void cancel() {
    this.cancelled = true;
  }

  /**
   * Checks if the operation has been cancelled.
   *
   * @return true if cancelled
   */
  public boolean isCancelled() {
    return cancelled;
  }

  /**
   * Gets the current operation status.
   *
   * @return the status code (Cancel if cancelled, Pending if no attributes, or actual status)
   */
  public int getStatus() {
    if (cancelled) {
      return Status.Cancel;
    }
    return attributes != null ? attributes.getInt(Tag.Status, Status.Pending) : Status.Pending;
  }

  /**
   * Gets the error comment from the current attributes.
   *
   * @return the error comment or null if none available
   */
  public String getErrorComment() {
    return attributes != null ? attributes.getString(Tag.ErrorComment) : null;
  }

  /**
   * Gets the number of remaining sub-operations.
   *
   * @return the count or -1 if not available
   */
  public int getNumberOfRemainingSuboperations() {
    return getTagValue(Tag.NumberOfRemainingSuboperations);
  }

  /**
   * Gets the number of completed sub-operations.
   *
   * @return the count or -1 if not available
   */
  public int getNumberOfCompletedSuboperations() {
    return getTagValue(Tag.NumberOfCompletedSuboperations);
  }

  /**
   * Gets the number of failed sub-operations.
   *
   * @return the count or -1 if not available
   */
  public int getNumberOfFailedSuboperations() {
    return getTagValue(Tag.NumberOfFailedSuboperations);
  }

  /**
   * Gets the number of warning sub-operations.
   *
   * @return the count or -1 if not available
   */
  public int getNumberOfWarningSuboperations() {
    return getTagValue(Tag.NumberOfWarningSuboperations);
  }

  private void notifyListeners() {
    listeners.forEach(listener -> listener.handleProgression(this));
  }

  private int getTagValue(int tag) {
    return attributes != null ? attributes.getInt(tag, -1) : -1;
  }

  private int getFailedCount() {
    return getTagValue(Tag.NumberOfFailedSuboperations);
  }
}
