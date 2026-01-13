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

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.VR;
import org.dcm4che3.io.DicomInputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.util.StreamUtil;
import org.weasis.dicom.param.DicomProgress;
import org.weasis.dicom.param.DicomState;

/**
 * Utility class providing common DICOM service operations including thread management, progress
 * notification, and resource cleanup.
 */
public final class ServiceUtil {
  private static final Logger LOGGER = LoggerFactory.getLogger(ServiceUtil.class);
  private static final int MIN_REMAINING_OPERATIONS = 1;

  /** Progress status enumeration for DICOM operations */
  public enum ProgressStatus {
    FAILED,
    WARNING,
    COMPLETED
  }

  private ServiceUtil() {}

  /**
   * Creates a custom ThreadFactory that prefixes thread names with the given name.
   *
   * @param name the prefix for thread names
   * @return a new ThreadFactory instance
   * @throws NullPointerException if name is null
   */
  public static ThreadFactory getThreadFactory(String name) {
    Objects.requireNonNull(name, "Thread factory name cannot be null");
    return runnable -> {
      var thread = Executors.defaultThreadFactory().newThread(runnable);
      thread.setName(name + "-" + thread.getName());
      return thread;
    };
  }

  /** Safely shuts down an ExecutorService, handling any exceptions */
  public static void shutdownService(ExecutorService executorService) {
    if (executorService != null) {
      try {
        executorService.shutdown();
      } catch (Exception e) {
        LOGGER.error("ExecutorService shutdown failed", e);
      }
    }
  }

  /** Forces closing of resources when progress is available */
  public static void forceGettingAttributes(DicomState dcmState, AutoCloseable closeable) {
    var progress = dcmState.getProgress();
    if (progress != null) {
      StreamUtil.safeClose(closeable);
    }
  }

  /** Safely closes DicomInputStream and cleans up bulk data files */
  public static void safeClose(DicomInputStream inputStream) {
    if (inputStream != null) {
      inputStream.getBulkDataFiles().forEach(file -> deleteBulkDataFile(file.toPath()));
    }
  }

  /**
   * Notifies progress with DICOM state and operation details.
   *
   * @param state the DICOM state containing progress information
   * @param instanceUID the affected SOP instance UID
   * @param classUID the affected SOP class UID
   * @param status the operation status code
   * @param progressStatus the progress status
   * @param numberOfSuboperations total number of sub-operations
   */
  public static void notifyProgression(
      DicomState state,
      String instanceUID,
      String classUID,
      int status,
      ProgressStatus progressStatus,
      int numberOfSuboperations) {
    state.setStatus(status);
    var progress = state.getProgress();
    if (progress == null) {
      return;
    }

    var attributes = Optional.ofNullable(progress.getAttributes()).orElseGet(Attributes::new);
    setStatusAttributes(attributes, instanceUID, classUID, status);
    notifyProgression(progress, attributes, progressStatus, numberOfSuboperations);
    progress.setAttributes(attributes);
  }

  /**
   * Updates progress attributes based on the current operation status.
   *
   * @param progress the DICOM progress tracker
   * @param attributes the command attributes to update
   * @param progressStatus the current operation status
   * @param numberOfSuboperations total number of sub-operations
   */
  public static void notifyProgression(
      DicomProgress progress,
      Attributes attributes,
      ProgressStatus progressStatus,
      int numberOfSuboperations) {

    if (progress == null || attributes == null) {
      return;
    }

    var operationCounts = calculateOperationCounts(progress, numberOfSuboperations);
    var updatedCounts = updateCountsForStatus(operationCounts, progressStatus);
    setOperationCountAttributes(attributes, updatedCounts);
  }

  /**
   * Calculates the total number of sub-operations from command attributes.
   *
   * @param attributes the command attributes
   * @return total number of sub-operations, or 0 if attributes is null
   */
  public static int getTotalOfSuboperations(Attributes attributes) {
    if (attributes == null) {
      return 0;
    }
    return attributes.getInt(Tag.NumberOfCompletedSuboperations, 0)
        + attributes.getInt(Tag.NumberOfFailedSuboperations, 0)
        + attributes.getInt(Tag.NumberOfWarningSuboperations, 0)
        + attributes.getInt(Tag.NumberOfRemainingSuboperations, 0);
  }

  private static void deleteBulkDataFile(Path filePath) {
    try {
      Files.deleteIfExists(filePath);
    } catch (Exception e) {
      LOGGER.warn("Failed to delete bulk data file: {}", filePath, e);
    }
  }

  private static void setStatusAttributes(
      Attributes attributes, String instanceUID, String classUID, int status) {
    attributes.setInt(Tag.Status, VR.US, status);
    attributes.setString(Tag.AffectedSOPInstanceUID, VR.UI, instanceUID);
    attributes.setString(Tag.AffectedSOPClassUID, VR.UI, classUID);
  }

  private static OperationCounts calculateOperationCounts(
      DicomProgress progress, int numberOfSuboperations) {
    if (progress.getAttributes() == null) {
      return new OperationCounts(0, 0, 0, numberOfSuboperations);
    }

    int completed = progress.getNumberOfCompletedSuboperations();
    int failed = progress.getNumberOfFailedSuboperations();
    int warning = progress.getNumberOfWarningSuboperations();
    int remaining = numberOfSuboperations - (completed + failed + warning);

    return new OperationCounts(
        completed, failed, warning, Math.max(remaining, MIN_REMAINING_OPERATIONS));
  }

  private static OperationCounts updateCountsForStatus(
      OperationCounts counts, ProgressStatus status) {
    return switch (status) {
      case COMPLETED ->
          new OperationCounts(
              counts.completed + 1, counts.failed, counts.warning, counts.remaining);
      case FAILED ->
          new OperationCounts(
              counts.completed, counts.failed + 1, counts.warning, counts.remaining);
      case WARNING ->
          new OperationCounts(
              counts.completed, counts.failed, counts.warning + 1, counts.remaining);
    };
  }

  private static void setOperationCountAttributes(Attributes attributes, OperationCounts counts) {
    attributes.setInt(Tag.NumberOfCompletedSuboperations, VR.US, counts.completed);
    attributes.setInt(Tag.NumberOfFailedSuboperations, VR.US, counts.failed);
    attributes.setInt(Tag.NumberOfWarningSuboperations, VR.US, counts.warning);
    attributes.setInt(Tag.NumberOfRemainingSuboperations, VR.US, counts.remaining - 1);
  }

  /** Record to encapsulate operation counts for cleaner parameter passing */
  private record OperationCounts(int completed, int failed, int warning, int remaining) {}
}
