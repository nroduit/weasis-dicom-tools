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
 * Enumeration for Hanging Protocol Level (0072,000A) defined terms.
 *
 * @see <a
 *     href="https://dicom.nema.org/medical/dicom/current/output/chtml/part03/sect_C.23.3.html">DICOM
 *     Part 3: C.23.3</a>
 */
public enum HangingProtocolLevel {
  MANUFACTURER("MANUFACTURER"),
  SITE("SITE"),
  SINGLE_USER("SINGLE_USER"),
  USER_GROUP("USER_GROUP");

  private final String codeString;

  HangingProtocolLevel(String codeString) {
    this.codeString = codeString;
  }

  public String getCodeString() {
    return codeString;
  }

  @Override
  public String toString() {
    return codeString;
  }

  public static HangingProtocolLevel fromString(String value) {
    for (HangingProtocolLevel hpl : values()) {
      if (hpl.codeString.equals(value)) {
        return hpl;
      }
    }
    throw new IllegalArgumentException("Invalid Hanging Protocol Level: " + value);
  }
}
