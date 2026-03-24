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
 * Enumeration for Partial Data Display Handling (0072,0208) defined terms.
 *
 * @see <a
 *     href="https://dicom.nema.org/medical/dicom/current/output/chtml/part03/sect_C.23.3.html">DICOM
 *     Part 3: C.23.3</a>
 */
public enum PartialDataDisplayHandling {
  MAINTAIN_LAYOUT("MAINTAIN_LAYOUT"),
  ADAPT_LAYOUT("ADAPT_LAYOUT");

  private final String codeString;

  PartialDataDisplayHandling(String codeString) {
    this.codeString = codeString;
  }

  public String getCodeString() {
    return codeString;
  }

  @Override
  public String toString() {
    return codeString;
  }

  public static PartialDataDisplayHandling fromString(String value) {
    for (PartialDataDisplayHandling pddh : values()) {
      if (pddh.codeString.equals(value)) {
        return pddh;
      }
    }
    throw new IllegalArgumentException("Invalid Partial Data Display Handling: " + value);
  }
}
