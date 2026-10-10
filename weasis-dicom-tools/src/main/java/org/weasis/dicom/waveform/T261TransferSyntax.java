/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.waveform;

import java.util.Objects;

/**
 * Transfer syntaxes of DICOM Supplement 253 (waveform compression with ITU-T T.261).
 *
 * <p>The supplement under public comment (v9) does not assign the UIDs yet: it uses the
 * placeholders {@code UID_TS1_lossless} and {@code UID_TS2_lossy}. The values used here default to
 * those placeholders and can be overridden with the system properties {@code
 * weasis.dicom.t261.lossless.uid} / {@code weasis.dicom.t261.lossy.uid} or with {@link
 * #setUIDs(String, String)} once the final UIDs are published, without any other change in the
 * callers.
 */
public final class T261TransferSyntax {

  /** Placeholder of the lossless transfer syntax in Sup 253 v9. */
  public static final String PLACEHOLDER_LOSSLESS = "UID_TS1_lossless";

  /** Placeholder of the lossy transfer syntax in Sup 253 v9. */
  public static final String PLACEHOLDER_LOSSY = "UID_TS2_lossy";

  private static volatile String lossless =
      System.getProperty("weasis.dicom.t261.lossless.uid", PLACEHOLDER_LOSSLESS);
  private static volatile String lossy =
      System.getProperty("weasis.dicom.t261.lossy.uid", PLACEHOLDER_LOSSY);

  private T261TransferSyntax() {}

  /** The UID of the T.261 lossless transfer syntax. */
  public static String lossless() {
    return lossless;
  }

  /** The UID of the T.261 lossy transfer syntax. */
  public static String lossy() {
    return lossy;
  }

  /** Replaces the placeholders with the UIDs assigned by the standard. */
  public static void setUIDs(String losslessUID, String lossyUID) {
    lossless = Objects.requireNonNull(losslessUID);
    lossy = Objects.requireNonNull(lossyUID);
  }

  /** True for either T.261 transfer syntax. */
  public static boolean isT261(String tsuid) {
    return tsuid != null && (tsuid.equals(lossless) || tsuid.equals(lossy));
  }

  /** True for the T.261 lossy transfer syntax. */
  public static boolean isLossy(String tsuid) {
    return tsuid != null && tsuid.equals(lossy);
  }
}
