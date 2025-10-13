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

/**
 * A functional interface for monitoring DICOM operation progress.
 *
 * <p>Implementations receive notifications about DICOM progress updates including operation status,
 * completion counts, and error information. Listeners are notified in a thread-safe manner but
 * should avoid blocking operations to prevent impacting the DICOM operation performance.
 */
@FunctionalInterface
public interface ProgressListener {

  /**
   * Called when DICOM operation progress is updated.
   *
   * <p>This method is invoked whenever there are changes to the operation state, such as completed
   * sub-operations, errors, or status updates. Implementations should be lightweight and
   * non-blocking.
   *
   * @param progress the current progress information containing operation status, counts, and any
   *     error details
   */
  void handleProgression(DicomProgress progress);
}
