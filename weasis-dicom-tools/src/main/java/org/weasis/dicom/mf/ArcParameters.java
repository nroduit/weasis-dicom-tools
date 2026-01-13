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
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.util.StringUtil;

/**
 * Configuration parameters for DICOM archive access and manifest generation. This class manages
 * archive connection settings, HTTP tags, DICOM tag overrides, and authentication parameters used
 * in WADO (Web Access to DICOM Objects) operations.
 *
 * <p>The parameters support both manifest version 1.0 and 2.5 formats, providing backward
 * compatibility while supporting modern DICOM web standards.
 *
 * @since 5.34.0.3
 */
public class ArcParameters {
  private static final Logger LOGGER = LoggerFactory.getLogger(ArcParameters.class);

  // Manifest version 2.5
  public static final String TAG_DOCUMENT_ROOT = "manifest";
  public static final String MANIFEST_UID = "uid";
  public static final String SCHEMA =
      "xmlns=\"http://www.weasis.org/xsd/2.5\" xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"";
  public static final String TAG_ARC_QUERY = "arcQuery";
  public static final String ARCHIVE_ID = "arcId";
  public static final String BASE_URL = "baseUrl";
  public static final String QUERY_MODE = "queryMode";
  public static final String TAG_PR_ROOT = "presentations";
  public static final String TAG_PR = "presentation";
  public static final String TAG_SEL_ROOT = "selections";
  public static final String TAG_SEL = "selection";

  // Manifest version 1
  public static final String TAG_HTTP_TAG = "httpTag";
  public static final String ADDITIONAL_PARAMETERS = "additionalParameters";
  public static final String OVERRIDE_TAGS = "overrideDicomTagsList";
  public static final String WEB_LOGIN = "webLogin";

  public static final String TAG_DELIMITER = ",";
  public static final int DEFAULT_HTTP_TAG_CAPACITY = 2;

  private final String baseURL;
  private final String archiveID;
  private final String additionalParameters;
  private final int[] overrideDicomTagIDList;
  private final String webLogin;
  private final List<HttpTag> httpTaglist;

  /**
   * Creates archive parameters with the specified configuration.
   *
   * @param archiveID the archive identifier, required
   * @param baseURL the base URL for WADO access, required
   * @param additionalParameters additional query parameters, may be null
   * @param overrideDicomTagsList comma-separated list of DICOM tag IDs to override, may be null
   * @param webLogin web authentication login, may be null
   * @throws NullPointerException if archiveID or baseURL is null
   */
  public ArcParameters(
      String archiveID,
      String baseURL,
      String additionalParameters,
      String overrideDicomTagsList,
      String webLogin) {
    this.archiveID = Objects.requireNonNull(archiveID, "Archive ID cannot be null");
    this.baseURL = Objects.requireNonNull(baseURL, "Base URL cannot be null");
    this.webLogin = normalizeWebLogin(webLogin);
    this.additionalParameters = normalizeParameters(additionalParameters);
    this.httpTaglist = new ArrayList<>(DEFAULT_HTTP_TAG_CAPACITY);
    this.overrideDicomTagIDList = parseOverrideTags(overrideDicomTagsList);
  }

  /**
   * Returns an unmodifiable view of the HTTP tag list.
   *
   * @return the HTTP tag list
   */
  public List<HttpTag> getHttpTaglist() {
    return Collections.unmodifiableList(httpTaglist);
  }

  /**
   * Adds an HTTP tag if both key and value are non-null.
   *
   * @param key the HTTP header key
   * @param value the HTTP header value
   */
  public void addHttpTag(String key, String value) {
    if (key != null && value != null) {
      httpTaglist.add(new HttpTag(key, value));
    }
  }

  /**
   * Returns the archive identifier.
   *
   * @return the archive ID
   */
  public String getArchiveID() {
    return archiveID;
  }

  /**
   * Returns the base URL for WADO access.
   *
   * @return the base URL
   */
  public String getBaseURL() {
    return baseURL;
  }

  /**
   * Returns the web authentication login.
   *
   * @return the web login, or null if not specified
   */
  public String getWebLogin() {
    return webLogin;
  }

  /**
   * Returns the additional query parameters.
   *
   * @return the additional parameters, never null
   */
  public String getAdditionalParameters() {
    return additionalParameters;
  }

  /**
   * Returns a defensive copy of the override DICOM tag ID list.
   *
   * @return array of tag IDs to override, or null if not specified
   */
  public int[] getOverrideDicomTagIDList() {
    return overrideDicomTagIDList != null ? overrideDicomTagIDList.clone() : null;
  }

  /**
   * Returns the override DICOM tags as a comma-separated string.
   *
   * @return comma-separated tag IDs, or null if not specified
   */
  public String getOverrideDicomTagsList() {
    return overrideDicomTagIDList != null
        ? IntStream.of(overrideDicomTagIDList)
            .mapToObj(String::valueOf)
            .collect(Collectors.joining(TAG_DELIMITER))
        : null;
  }

  @Override
  public String toString() {
    return "ArcParameters{"
        + "archiveID='"
        + archiveID
        + '\''
        + ", baseURL='"
        + baseURL
        + '\''
        + ", webLogin='"
        + webLogin
        + '\''
        + ", additionalParameters='"
        + additionalParameters
        + '\''
        + ", overrideTags="
        + Arrays.toString(overrideDicomTagIDList)
        + ", httpTagCount="
        + httpTaglist.size()
        + '}';
  }

  // Normalizes web login by trimming whitespace
  private String normalizeWebLogin(String webLogin) {
    return webLogin != null ? webLogin.trim() : null;
  }

  // Normalizes additional parameters to empty string if null or empty
  private String normalizeParameters(String parameters) {
    return StringUtil.hasText(parameters) ? parameters : "";
  }

  // Parses comma-separated override tags into integer array
  private int[] parseOverrideTags(String overrideDicomTagsList) {
    if (!StringUtil.hasText(overrideDicomTagsList)) {
      return null;
    }
    String[] tagStrings = overrideDicomTagsList.split(TAG_DELIMITER);
    return Arrays.stream(tagStrings)
        .mapToInt(this::parseTagId)
        .filter(tagId -> tagId != -1) // Filter out invalid tags
        .toArray();
  }

  // Parses a single tag ID string to integer
  private int parseTagId(String tagString) {
    try {
      return Integer.decode(tagString.trim());
    } catch (NumberFormatException e) {
      LOGGER.warn("Invalid DICOM tag ID '{}' - skipping", tagString.trim());
      return -1;
    }
  }
}
