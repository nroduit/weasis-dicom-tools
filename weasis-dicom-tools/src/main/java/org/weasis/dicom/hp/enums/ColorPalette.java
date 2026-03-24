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
 * Enumeration for recommended color palette names defined terms.
 *
 * @see <a
 *     href="https://dicom.nema.org/medical/dicom/current/output/chtml/part03/sect_C.23.3.html">DICOM
 *     Part 3: C.23.3</a>
 */
public enum ColorPalette {
  BLACK_BODY("BLACK_BODY"),
  HOT_IRON("HOT_IRON"),
  DEFAULT("DEFAULT");

  private final String codeString;

  ColorPalette(String codeString) {
    this.codeString = codeString;
  }

  public String getCodeString() {
    return codeString;
  }

  @Override
  public String toString() {
    return codeString;
  }

  public static ColorPalette fromString(String value) {
    for (ColorPalette cp : values()) {
      if (cp.codeString.equals(value)) {
        return cp;
      }
    }
    throw new IllegalArgumentException("Invalid Color Palette: " + value);
  }
}
