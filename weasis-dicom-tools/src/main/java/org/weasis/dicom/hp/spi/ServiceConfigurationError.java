/*
 * Copyright (c) 2024 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.hp.spi;

public class ServiceConfigurationError extends Error {

  /** Constructs a new instance with the specified detail string. */
  public ServiceConfigurationError(String msg) {
    super(msg);
  }

  /** Constructs a new instance that wraps the specified throwable. */
  public ServiceConfigurationError(Throwable x) {
    super(x);
  }
}
