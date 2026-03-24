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
 * Enumeration for Image Set Selector Usage Flag (0072,0024) defined terms.
 *
 * @see <a
 *     href="https://dicom.nema.org/medical/dicom/current/output/chtml/part03/sect_C.23.3.html">DICOM
 *     Part 3: C.23.3</a>
 */
public enum SelectorUsageFlag {
  MATCH("MATCH"),
  NO_MATCH("NO_MATCH");

  private final String codeString;

  SelectorUsageFlag(String codeString) {
    this.codeString = codeString;
  }

  public String getCodeString() {
    return codeString;
  }

  public boolean isMatch() {
    return this == MATCH;
  }

  @Override
  public String toString() {
    return codeString;
  }

  public static SelectorUsageFlag fromString(String value) {
    for (SelectorUsageFlag flag : values()) {
      if (flag.codeString.equals(value)) {
        return flag;
      }
    }
    throw new IllegalArgumentException(
        "Invalid (0072,0024) Image Set Selector Usage Flag: " + value);
  }
}
