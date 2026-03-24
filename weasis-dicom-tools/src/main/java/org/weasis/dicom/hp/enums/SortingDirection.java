/*
 * Copyright (c) 2024 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.hp.enums;

/**
 * Enumeration for Sorting Direction (0072,0604) defined terms.
 *
 * @see <a
 *     href="https://dicom.nema.org/medical/dicom/current/output/chtml/part03/sect_C.23.3.html">DICOM
 *     Part 3: C.23.3</a>
 */
public enum SortingDirection {
  INCREASING("INCREASING", 1),
  DECREASING("DECREASING", -1);

  private final String codeString;
  private final int sign;

  SortingDirection(String codeString, int sign) {
    this.codeString = codeString;
    this.sign = sign;
  }

  public String getCodeString() {
    return codeString;
  }

  public int getSign() {
    return sign;
  }

  @Override
  public String toString() {
    return codeString;
  }

  public static SortingDirection fromString(String value) {
    for (SortingDirection sd : values()) {
      if (sd.codeString.equals(value)) {
        return sd;
      }
    }
    throw new IllegalArgumentException("Invalid (0072,0604) Sorting Direction: " + value);
  }
}
