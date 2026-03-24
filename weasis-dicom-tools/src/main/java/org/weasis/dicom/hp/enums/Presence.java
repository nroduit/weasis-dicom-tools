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
 * Enumeration for presence indicator defined terms in DICOM Hanging Protocol.
 *
 * @see <a
 *     href="https://dicom.nema.org/medical/dicom/current/output/chtml/part03/sect_C.23.3.html">DICOM
 *     Part 3: C.23.3</a>
 */
public enum Presence {
  PRESENT("PRESENT"),
  NOT_PRESENT("NOT_PRESENT");

  private final String codeString;

  Presence(String codeString) {
    this.codeString = codeString;
  }

  public String getCodeString() {
    return codeString;
  }

  public boolean isPresent() {
    return this == PRESENT;
  }

  @Override
  public String toString() {
    return codeString;
  }

  public static Presence fromString(String value) {
    for (Presence p : values()) {
      if (p.codeString.equals(value)) {
        return p;
      }
    }
    throw new IllegalArgumentException("Invalid Presence value: " + value);
  }
}
