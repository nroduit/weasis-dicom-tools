/*
 * Copyright (c) 2017-2019 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.param;

/**
 * Functional interface for handling cancellation events in DICOM operations.
 *
 * <p>This interface provides a callback mechanism for cancelling long-running DICOM operations such
 * as file transfers, queries, or retrievals. Implementations should handle cancellation gracefully
 * and ensure proper resource cleanup.
 */
@FunctionalInterface
public interface CancelListener {

  /**
   * Initiates cancellation of the associated operation.
   *
   * <p>This method should be called when the operation needs to be canceled. Implementations must
   * be thread-safe as this method may be called from different threads concurrently.
   */
  void cancel();
}
