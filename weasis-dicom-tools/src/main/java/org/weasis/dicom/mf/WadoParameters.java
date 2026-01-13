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

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Objects;

/**
 * Configuration parameters for WADO (Web Access to DICOM Objects) services, extending the base
 * archive parameters with WADO-specific settings for DICOM web access protocols.
 *
 * <p>WADO parameters define how DICOM objects are retrieved via web protocols, supporting both
 * legacy WADO-URI and modern WADO-RS (RESTful Services) standards. The class provides flexible
 * configuration options for different deployment scenarios.
 *
 * <p><strong>WADO Protocols:</strong>
 *
 * <ul>
 *   <li><strong>WADO-URI</strong> - Legacy HTTP-based retrieval using GET requests
 *   <li><strong>WADO-RS</strong> - Modern RESTful web services for DICOM access
 * </ul>
 *
 * <p><strong>Usage Examples:</strong>
 *
 * <pre>{@code
 * // Simple WADO-URI configuration
 * WadoParameters wado = WadoParameters.wadoUri("http://pacs.example.com/wado", true);
 *
 * // WADO-RS with authentication
 * WadoParameters wadoRs = WadoParameters.wadoRs("http://pacs.example.com/dicomweb")
 *     .withWebLogin("user:pass")
 *     .build();
 *
 * // Full configuration
 * WadoParameters full = new WadoParameters("archive1", "http://pacs.example.com/wado",
 *     true, "param1=value1", "0008,0018", "auth_token", false);
 * }</pre>
 *
 * @see ArcParameters
 * @see QueryResult
 * @see DefaultQueryResult
 */
public class WadoParameters extends ArcParameters {

  // Manifest version 1.0
  public static final String TAG_WADO_QUERY = "wado_query";
  public static final String WADO_URL = "wadoURL";
  public static final String WADO_ONLY_SOP_UID = "requireOnlySOPInstanceUID";

  private final boolean requireOnlySOPInstanceUID;
  private final boolean wadoRS;

  /**
   * Creates WADO parameters with the specified configuration.
   *
   * @param archiveID the archive identifier, may be empty but not null
   * @param wadoURL the WADO service URL, required
   * @param requireOnlySOPInstanceUID whether to require only SOP Instance UID for retrieval
   * @param additionalParameters additional query parameters, may be null
   * @param overrideDicomTagsList comma-separated list of DICOM tag IDs to override, may be null
   * @param webLogin web authentication credentials, may be null
   * @param wadoRS whether to use WADO-RS protocol instead of WADO-URI
   * @throws NullPointerException if wadoURL is null
   * @throws IllegalArgumentException if wadoURL is invalid
   */
  public WadoParameters(
      String archiveID,
      String wadoURL,
      boolean requireOnlySOPInstanceUID,
      String additionalParameters,
      String overrideDicomTagsList,
      String webLogin,
      boolean wadoRS) {
    super(
        Objects.requireNonNull(archiveID, "Archive ID cannot be null"),
        validateAndNormalizeUrl(wadoURL),
        additionalParameters,
        overrideDicomTagsList,
        webLogin);
    this.requireOnlySOPInstanceUID = requireOnlySOPInstanceUID;
    this.wadoRS = wadoRS;
  }

  /**
   * Creates WADO-URI parameters with default archive ID.
   *
   * @param wadoURL the WADO service URL, required
   * @param requireOnlySOPInstanceUID whether to require only SOP Instance UID for retrieval
   * @throws NullPointerException if wadoURL is null
   * @throws IllegalArgumentException if wadoURL is invalid
   */
  public WadoParameters(String wadoURL, boolean requireOnlySOPInstanceUID) {
    this("", wadoURL, requireOnlySOPInstanceUID, null, null, null, false);
  }

  /**
   * Creates WADO parameters with protocol selection and default archive ID.
   *
   * @param wadoURL the WADO service URL, required
   * @param requireOnlySOPInstanceUID whether to require only SOP Instance UID for retrieval
   * @param wadoRS whether to use WADO-RS protocol
   * @throws NullPointerException if wadoURL is null
   * @throws IllegalArgumentException if wadoURL is invalid
   */
  public WadoParameters(String wadoURL, boolean requireOnlySOPInstanceUID, boolean wadoRS) {
    this("", wadoURL, requireOnlySOPInstanceUID, null, null, null, wadoRS);
  }

  /**
   * Creates WADO-URI parameters with extended configuration and default archive ID.
   *
   * @param wadoURL the WADO service URL, required
   * @param requireOnlySOPInstanceUID whether to require only SOP Instance UID for retrieval
   * @param additionalParameters additional query parameters, may be null
   * @param overrideDicomTagsList comma-separated list of DICOM tag IDs to override, may be null
   * @param webLogin web authentication credentials, may be null
   * @throws NullPointerException if wadoURL is null
   * @throws IllegalArgumentException if wadoURL is invalid
   */
  public WadoParameters(
      String wadoURL,
      boolean requireOnlySOPInstanceUID,
      String additionalParameters,
      String overrideDicomTagsList,
      String webLogin) {
    this(
        "",
        wadoURL,
        requireOnlySOPInstanceUID,
        additionalParameters,
        overrideDicomTagsList,
        webLogin,
        false);
  }

  /**
   * Creates WADO-URI parameters with full configuration except WADO-RS flag.
   *
   * @param archiveID the archive identifier, may be empty but not null
   * @param wadoURL the WADO service URL, required
   * @param requireOnlySOPInstanceUID whether to require only SOP Instance UID for retrieval
   * @param additionalParameters additional query parameters, may be null
   * @param overrideDicomTagsList comma-separated list of DICOM tag IDs to override, may be null
   * @param webLogin web authentication credentials, may be null
   * @throws NullPointerException if wadoURL is null
   * @throws IllegalArgumentException if wadoURL is invalid
   */
  public WadoParameters(
      String archiveID,
      String wadoURL,
      boolean requireOnlySOPInstanceUID,
      String additionalParameters,
      String overrideDicomTagsList,
      String webLogin) {
    this(
        archiveID,
        wadoURL,
        requireOnlySOPInstanceUID,
        additionalParameters,
        overrideDicomTagsList,
        webLogin,
        false);
  }

  /**
   * Creates a simple WADO-URI configuration.
   *
   * @param wadoURL the WADO service URL
   * @param requireOnlySOPInstanceUID whether to require only SOP Instance UID
   * @return a new WadoParameters instance
   * @since 5.34.0.4
   */
  public static WadoParameters wadoUri(String wadoURL, boolean requireOnlySOPInstanceUID) {
    return new WadoParameters(wadoURL, requireOnlySOPInstanceUID, false);
  }

  /**
   * Creates a simple WADO-RS configuration.
   *
   * @param wadoURL the WADO service URL
   * @return a builder for WADO-RS parameters
   * @since 5.34.0.4
   */
  public static Builder wadoRs(String wadoURL) {
    return new Builder(wadoURL, true);
  }

  /**
   * Creates a builder for complex configurations.
   *
   * @param wadoURL the WADO service URL
   * @return a new parameter builder
   * @since 5.34.0.4
   */
  public static Builder builder(String wadoURL) {
    return new Builder(wadoURL, false);
  }

  /**
   * Returns whether to require only SOP Instance UID for retrieval operations.
   *
   * @return true if only SOP Instance UID is required
   */
  public boolean isRequireOnlySOPInstanceUID() {
    return requireOnlySOPInstanceUID;
  }

  /**
   * Returns whether this configuration uses WADO-RS protocol.
   *
   * @return true if using WADO-RS, false if using WADO-URI
   */
  public boolean isWadoRS() {
    return wadoRS;
  }

  /**
   * Returns the protocol name for this configuration.
   *
   * @return "WADO-RS" or "WADO-URI"
   */
  public String getProtocolName() {
    return wadoRS ? "WADO-RS" : "WADO-URI";
  }

  @Override
  public String toString() {
    return String.format(
        "WadoParameters{archiveID='%s', wadoURL='%s', protocol='%s', requireOnlySOPInstanceUID=%b, "
            + "webLogin='%s', additionalParams='%s', httpTagCount=%d}",
        getArchiveID(),
        getBaseURL(),
        getProtocolName(),
        requireOnlySOPInstanceUID,
        getWebLogin(),
        getAdditionalParameters(),
        getHttpTaglist().size());
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    }
    if (!(obj instanceof WadoParameters that)) {
      return false;
    }
    return super.equals(obj)
        && requireOnlySOPInstanceUID == that.requireOnlySOPInstanceUID
        && wadoRS == that.wadoRS;
  }

  @Override
  public int hashCode() {
    return Objects.hash(super.hashCode(), requireOnlySOPInstanceUID, wadoRS);
  }

  // Validates and normalizes the WADO URL
  private static String validateAndNormalizeUrl(String wadoURL) {
    Objects.requireNonNull(wadoURL, "WADO URL cannot be null");

    if (wadoURL.isBlank()) {
      throw new IllegalArgumentException("WADO URL cannot be blank");
    }

    try {
      URI uri = new URI(wadoURL.trim());
      if (uri.getScheme() == null) {
        throw new IllegalArgumentException("WADO URL must include a scheme (http:// or https://)");
      }
      if (!uri.getScheme().matches("^https?$")) {
        throw new IllegalArgumentException("WADO URL must use HTTP or HTTPS protocol");
      }
      return uri.toString();
    } catch (URISyntaxException e) {
      throw new IllegalArgumentException("Invalid WADO URL format: " + wadoURL, e);
    }
  }

  /**
   * Builder pattern for creating WadoParameters with complex configurations. Provides a fluent API
   * for setting optional parameters.
   *
   * @since 5.34.0.4
   */
  public static class Builder {
    private String archiveID = "";
    private final String wadoURL;
    private boolean requireOnlySOPInstanceUID = false;
    private String additionalParameters;
    private String overrideDicomTagsList;
    private String webLogin;
    private final boolean wadoRS;

    private Builder(String wadoURL, boolean wadoRS) {
      this.wadoURL = wadoURL;
      this.wadoRS = wadoRS;
    }

    /**
     * Sets the archive identifier.
     *
     * @param archiveID the archive identifier
     * @return this builder
     */
    public Builder withArchiveID(String archiveID) {
      this.archiveID = archiveID != null ? archiveID : "";
      return this;
    }

    /**
     * Sets whether to require only SOP Instance UID.
     *
     * @param requireOnlySOPInstanceUID true to require only SOP Instance UID
     * @return this builder
     */
    public Builder withRequireOnlySOPInstanceUID(boolean requireOnlySOPInstanceUID) {
      this.requireOnlySOPInstanceUID = requireOnlySOPInstanceUID;
      return this;
    }

    /**
     * Sets additional query parameters.
     *
     * @param additionalParameters the additional parameters
     * @return this builder
     */
    public Builder withAdditionalParameters(String additionalParameters) {
      this.additionalParameters = additionalParameters;
      return this;
    }

    /**
     * Sets DICOM tags to override.
     *
     * @param overrideDicomTagsList comma-separated list of tag IDs
     * @return this builder
     */
    public Builder withOverrideDicomTagsList(String overrideDicomTagsList) {
      this.overrideDicomTagsList = overrideDicomTagsList;
      return this;
    }

    /**
     * Sets web authentication credentials.
     *
     * @param webLogin the authentication credentials
     * @return this builder
     */
    public Builder withWebLogin(String webLogin) {
      this.webLogin = webLogin;
      return this;
    }

    /**
     * Builds the WadoParameters instance.
     *
     * @return a new WadoParameters instance
     */
    public WadoParameters build() {
      return new WadoParameters(
          archiveID,
          wadoURL,
          requireOnlySOPInstanceUID,
          additionalParameters,
          overrideDicomTagsList,
          webLogin,
          wadoRS);
    }
  }
}
