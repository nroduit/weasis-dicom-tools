/*
 * Copyright (c) 2017-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.mf;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.util.StringUtil;

public class ArcParameters {
  private static final Logger LOGGER = LoggerFactory.getLogger(ArcParameters.class);

  // Manifest 2.5
  public static final String TAG_DOCUMENT_ROOT = "manifest";
  public static final String MANIFEST_UID = "uid";
  public static final String SCHEMA =
      "xmlns=\"http://www.weasis.org/xsd/2.5\" xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"";
  public static final String TAG_ARC_QUERY = "arcQuery";
  public static final String ARCHIVE_ID = "arcId";
  public static final String BASE_URL = "baseUrl";
  public static final String TAG_PR_ROOT = "presentations";
  public static final String TAG_PR = "presentation";
  public static final String TAG_SEL_ROOT = "selections";
  public static final String TAG_SEL = "selection";

  // Manifest 1
  public static final String TAG_HTTP_TAG = "httpTag";
  public static final String ADDITIONNAL_PARAMETERS = "additionnalParameters";
  public static final String OVERRIDE_TAGS = "overrideDicomTagsList";
  public static final String WEB_LOGIN = "webLogin";

  private final String baseURL;
  private final String archiveID;
  private final String additionnalParameters;
  private final int[] overrideDicomTagIDList;
  private final String webLogin;
  private final List<HttpTag> httpTaglist;

  public ArcParameters(
      String archiveID,
      String baseURL,
      String additionnalParameters,
      String overrideDicomTagsList,
      String webLogin) {
    this.archiveID = Objects.requireNonNull(archiveID);
    this.baseURL = Objects.requireNonNull(baseURL);
    this.webLogin = webLogin == null ? null : webLogin.trim();
    this.additionnalParameters =
        StringUtil.hasText(additionnalParameters) ? additionnalParameters : "";
    this.httpTaglist = new ArrayList<>(2);
    if (StringUtil.hasText(overrideDicomTagsList)) {
      String[] val = overrideDicomTagsList.split(","); // $NON-NLS-1$
      overrideDicomTagIDList = new int[val.length];
      for (int i = 0; i < val.length; i++) {
        try {
          overrideDicomTagIDList[i] = Integer.decode(val[i].trim());
        } catch (NumberFormatException e) {
          LOGGER.error("Cannot read dicom tag list", e); // $NON-NLS-1$
        }
      }
    } else {
      overrideDicomTagIDList = null;
    }
  }

  public List<HttpTag> getHttpTaglist() {
    return httpTaglist;
  }

  public void addHttpTag(String key, String value) {
    if (key != null && value != null) {
      httpTaglist.add(new HttpTag(key, value));
    }
  }

  public String getArchiveID() {
    return archiveID;
  }

  public String getBaseURL() {
    return baseURL;
  }

  public String getWebLogin() {
    return webLogin;
  }

  public String getAdditionnalParameters() {
    return additionnalParameters;
  }

  public int[] getOverrideDicomTagIDList() {
    return overrideDicomTagIDList;
  }

  public String getOverrideDicomTagsList() {
    if (overrideDicomTagIDList != null) {
      return IntStream.of(overrideDicomTagIDList)
          .mapToObj(String::valueOf)
          .collect(Collectors.joining(","));
    }
    return null;
  }
}
