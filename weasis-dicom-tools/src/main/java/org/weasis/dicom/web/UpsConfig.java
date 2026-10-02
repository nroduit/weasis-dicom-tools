/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.web;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/** Configuration of a {@link UpsRS} client. */
public final class UpsConfig {

  private static final String DEFAULT_USER_AGENT = "Weasis UPS-RS Client";
  private static final Duration DEFAULT_CONNECT_TIMEOUT = Duration.ofSeconds(10);
  private static final Duration DEFAULT_REQUEST_TIMEOUT = Duration.ofSeconds(60);
  private static final String WORKITEMS = "/workitems";

  private final String baseUrl;
  private final String userAgent;
  private final Map<String, String> headers;
  private final Duration connectTimeout;
  private final Duration requestTimeout;
  private final HttpClient.Version httpVersion;
  private final AuthorizationProvider authorization;

  private UpsConfig(Builder builder) {
    this.baseUrl = normalizeUrl(builder.baseUrl);
    this.userAgent = builder.userAgent;
    this.headers = Map.copyOf(builder.headers);
    this.connectTimeout = builder.connectTimeout;
    this.requestTimeout = builder.requestTimeout;
    this.httpVersion = builder.httpVersion;
    this.authorization = builder.authorization;
  }

  /** Returns the DICOMweb service root, without trailing {@code /workitems}. */
  public String getBaseUrl() {
    return baseUrl;
  }

  public String getUserAgent() {
    return userAgent;
  }

  public Map<String, String> getHeaders() {
    return headers;
  }

  public Duration getConnectTimeout() {
    return connectTimeout;
  }

  public Duration getRequestTimeout() {
    return requestTimeout;
  }

  public HttpClient.Version getHttpVersion() {
    return httpVersion;
  }

  public AuthorizationProvider getAuthorization() {
    return authorization;
  }

  public static Builder builder() {
    return new Builder();
  }

  private static String normalizeUrl(String url) {
    String normalized = url.trim();
    while (normalized.endsWith("/")) {
      normalized = normalized.substring(0, normalized.length() - 1);
    }
    if (normalized.endsWith(WORKITEMS)) {
      normalized = normalized.substring(0, normalized.length() - WORKITEMS.length());
    }
    return normalized;
  }

  public static final class Builder {
    private String baseUrl;
    private String userAgent = DEFAULT_USER_AGENT;
    private final Map<String, String> headers = new HashMap<>();
    private Duration connectTimeout = DEFAULT_CONNECT_TIMEOUT;
    private Duration requestTimeout = DEFAULT_REQUEST_TIMEOUT;
    private HttpClient.Version httpVersion = HttpClient.Version.HTTP_1_1;
    private AuthorizationProvider authorization = AuthorizationProvider.NONE;

    private Builder() {}

    /** Sets the DICOMweb service root (a trailing {@code /workitems} is accepted). */
    public Builder baseUrl(String baseUrl) {
      this.baseUrl = baseUrl;
      return this;
    }

    public Builder userAgent(String userAgent) {
      this.userAgent = userAgent != null ? userAgent : DEFAULT_USER_AGENT;
      return this;
    }

    public Builder header(String name, String value) {
      Objects.requireNonNull(name, "Header name cannot be null");
      if (value != null) {
        headers.put(name, value);
      }
      return this;
    }

    public Builder headers(Map<String, String> headers) {
      if (headers != null) {
        this.headers.putAll(headers);
      }
      return this;
    }

    public Builder connectTimeout(Duration connectTimeout) {
      this.connectTimeout = Objects.requireNonNull(connectTimeout);
      return this;
    }

    public Builder requestTimeout(Duration requestTimeout) {
      this.requestTimeout = Objects.requireNonNull(requestTimeout);
      return this;
    }

    public Builder httpVersion(HttpClient.Version httpVersion) {
      this.httpVersion = Objects.requireNonNull(httpVersion);
      return this;
    }

    public Builder authorization(AuthorizationProvider authorization) {
      this.authorization = authorization != null ? authorization : AuthorizationProvider.NONE;
      return this;
    }

    public UpsConfig build() {
      Objects.requireNonNull(baseUrl, "Base URL is required");
      HttpAuthorization.checkNoConflict(authorization, headers);
      return new UpsConfig(this);
    }
  }
}
