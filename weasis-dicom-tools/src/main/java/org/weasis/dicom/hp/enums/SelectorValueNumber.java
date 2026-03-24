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
 * Enumeration for Selector Value Number (0072,0028) defined terms.
 *
 * @see <a
 *     href="https://dicom.nema.org/medical/dicom/current/output/chtml/part03/sect_C.23.3.html">DICOM
 *     Part 3: C.23.3</a>
 */
public enum SelectorValueNumber {
  ABSTRACT_PRIOR("ABSTRACT_PRIOR"),
  RELATIVE_TIME("RELATIVE_TIME");

  private final String codeString;

  SelectorValueNumber(String codeString) {
    this.codeString = codeString;
  }

  public String getCodeString() {
    return codeString;
  }

  @Override
  public String toString() {
    return codeString;
  }

  public static SelectorValueNumber fromString(String value) {
    for (SelectorValueNumber svn : values()) {
      if (svn.codeString.equals(value)) {
        return svn;
      }
    }
    throw new IllegalArgumentException("Invalid Selector Value Number: " + value);
  }
}
