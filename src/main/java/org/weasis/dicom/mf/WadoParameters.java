/*
 * Copyright (c) 2014-2019 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.mf;

public class WadoParameters extends ArcParameters {

  // Manifest 1.0
  public static final String TAG_WADO_QUERY = "wado_query";
  public static final String WADO_URL = "wadoURL";
  public static final String WADO_ONLY_SOP_UID = "requireOnlySOPInstanceUID";

  private final boolean requireOnlySOPInstanceUID;
  private final boolean wadoRS;

  public WadoParameters(String wadoURL, boolean requireOnlySOPInstanceUID) {
    this("", wadoURL, requireOnlySOPInstanceUID, null, null, null);
  }

  public WadoParameters(String wadoURL, boolean requireOnlySOPInstanceUID, boolean wadoRS) {
    this("", wadoURL, requireOnlySOPInstanceUID, null, null, null, wadoRS);
  }

  public WadoParameters(
      String wadoURL,
      boolean requireOnlySOPInstanceUID,
      String additionnalParameters,
      String overrideDicomTagsList,
      String webLogin) {
    this(
        "",
        wadoURL,
        requireOnlySOPInstanceUID,
        additionnalParameters,
        overrideDicomTagsList,
        webLogin);
  }

  public WadoParameters(
      String archiveID,
      String wadoURL,
      boolean requireOnlySOPInstanceUID,
      String additionnalParameters,
      String overrideDicomTagsList,
      String webLogin) {
    this(
        archiveID,
        wadoURL,
        requireOnlySOPInstanceUID,
        additionnalParameters,
        overrideDicomTagsList,
        webLogin,
        false);
  }

  public WadoParameters(
      String archiveID,
      String wadoURL,
      boolean requireOnlySOPInstanceUID,
      String additionnalParameters,
      String overrideDicomTagsList,
      String webLogin,
      boolean wadoRS) {
    super(archiveID, wadoURL, additionnalParameters, overrideDicomTagsList, webLogin);
    this.requireOnlySOPInstanceUID = requireOnlySOPInstanceUID;
    this.wadoRS = wadoRS;
  }

  public boolean isRequireOnlySOPInstanceUID() {
    return requireOnlySOPInstanceUID;
  }

  public boolean isWadoRS() {
    return wadoRS;
  }
}
