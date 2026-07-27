/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.mf;

/**
 * Connector used by Weasis to complete a manifest query, mirroring the {@code connectorType}
 * enumeration of the 2.5 manifest schema.
 *
 * <p>With {@link #DICOM_WEB} the manifest may stop at any hierarchy level (Patient, Study, Series
 * or Instance); Weasis queries the missing lower levels through DICOMweb. {@link #DICOM} and {@link
 * #DB} expect a manifest fully populated down to the instances.
 *
 * @since 5.34.3
 */
public enum QueryMode {
  DB,
  DICOM,
  DICOM_WEB;

  /** Default connector, assumed when the manifest omits the {@code queryMode} attribute. */
  public static final QueryMode DEFAULT = DICOM;

  /** Returns the mode matching {@code value}, or {@link #DEFAULT} when null or unknown. */
  public static QueryMode fromValue(String value) {
    if (value != null) {
      for (QueryMode mode : values()) {
        if (mode.name().equalsIgnoreCase(value.trim())) {
          return mode;
        }
      }
    }
    return DEFAULT;
  }
}
