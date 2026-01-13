/*
 * Copyright (c) 2024 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.macro;

import org.weasis.dicom.ref.CodingScheme;

/**
 * Represents a code item in DICOM, which includes a code value, its meaning, and the coding scheme
 * that defines it.
 */
public interface ItemCode {

  /**
   * Returns the code value as defined in the coding scheme.
   *
   * @return the code value, may be null
   */
  String getCodeValue();

  /**
   * Returns the human-readable meaning of the code.
   *
   * @return the code meaning, may be null
   */
  String getCodeMeaning();

  /**
   * Returns the coding scheme that defines this code.
   *
   * @return the coding scheme, may be null
   */
  CodingScheme getCodingScheme();
}
