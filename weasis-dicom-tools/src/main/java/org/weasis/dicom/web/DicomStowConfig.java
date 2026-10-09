/*
 * Copyright (c) 2020 Weasis Team and other contributors.
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
import java.util.Optional;

/**
 * Configuration for DICOM STOW-RS operations. Provides builder pattern for flexible configuration.
 */
public final class DicomStowConfig {

  private static final String DEFAULT_USER_AGENT = "Weasis STOW-RS Client";
  private static final int DEFAULT_THREAD_POOL_SIZE = 5;
  private static final Duration DEFAULT_CONNECT_TIMEOUT = Duration.ofSeconds(10);
  // Below this size the extra round trip of 100-continue costs more than an occasional re-upload.
  static final long DEFAULT_EXPECT_CONTINUE_THRESHOLD = 8L * 1024 * 1024;
  static final long EXPECT_CONTINUE_DISABLED = -1;
  // DICOMweb servers vary in HTTP/2 support, and STOW uses multipart/related streaming
  // which is best-tested over chunked HTTP/1.1.
  static final HttpClient.Version DEFAULT_HTTP_VERSION = HttpClient.Version.HTTP_1_1;

  private final String requestUrl;
  private final ContentType contentType;
  private final String userAgent;
  private final Map<String, String> headers;
  private final int threadPoolSize;
  private final Duration connectTimeout;
  private final HttpClient.Version httpVersion;
  private final AuthorizationProvider authorization;
  private final long expectContinueThreshold;
  private final Duration requestTimeout;

  private DicomStowConfig(Builder builder) {
    this.requestUrl = normalizeUrl(builder.requestUrl);
    this.contentType = builder.contentType;
    this.userAgent = builder.userAgent;
    this.headers = Map.copyOf(builder.headers);
    this.threadPoolSize = builder.threadPoolSize;
    this.connectTimeout = builder.connectTimeout;
    this.httpVersion = builder.httpVersion;
    this.authorization = builder.authorization;
    this.requestTimeout = builder.requestTimeout;
    if (builder.expectContinueThreshold != null) {
      this.expectContinueThreshold = builder.expectContinueThreshold;
    } else {
      this.expectContinueThreshold =
          authorization != AuthorizationProvider.NONE
              ? DEFAULT_EXPECT_CONTINUE_THRESHOLD
              : EXPECT_CONTINUE_DISABLED;
    }
  }

  public String getRequestUrl() {
    return requestUrl;
  }

  public ContentType getContentType() {
    return contentType;
  }

  public String getUserAgent() {
    return userAgent;
  }

  public Map<String, String> getHeaders() {
    return headers;
  }

  public int getThreadPoolSize() {
    return threadPoolSize;
  }

  public Duration getConnectTimeout() {
    return connectTimeout;
  }

  public HttpClient.Version getHttpVersion() {
    return httpVersion;
  }

  public AuthorizationProvider getAuthorization() {
    return authorization;
  }

  /** Returns the minimum payload size sent with {@code Expect: 100-continue}, or -1 if disabled. */
  public long getExpectContinueThreshold() {
    return expectContinueThreshold;
  }

  /**
   * Whether an upload of {@code payloadSize} bytes (-1 if unknown) waits for {@code 100 Continue},
   * so a rejected request costs no body upload.
   */
  public boolean useExpectContinue(long payloadSize) {
    if (expectContinueThreshold < 0) {
      return false;
    }
    return expectContinueThreshold == 0 || payloadSize >= expectContinueThreshold;
  }

  /** Returns the maximum time to wait for the response, including the upload. */
  public Optional<Duration> getRequestTimeout() {
    return Optional.ofNullable(requestTimeout);
  }

  /** Creates a new builder instance. */
  public static Builder builder() {
    return new Builder();
  }

  private String normalizeUrl(String url) {
    Objects.requireNonNull(url, "Request URL cannot be null");

    String normalized = url.trim();
    if (normalized.endsWith("/")) {
      normalized = normalized.substring(0, normalized.length() - 1);
    }
    if (!normalized.endsWith("/studies")) {
      normalized += "/studies";
    }
    return normalized;
  }

  /** Builder for DicomStowConfig. */
  public static final class Builder {
    private String requestUrl;
    private ContentType contentType = ContentType.APPLICATION_DICOM;
    private String userAgent = DEFAULT_USER_AGENT;
    private final Map<String, String> headers = new HashMap<>();
    private int threadPoolSize = DEFAULT_THREAD_POOL_SIZE;
    private Duration connectTimeout = DEFAULT_CONNECT_TIMEOUT;
    private HttpClient.Version httpVersion = DEFAULT_HTTP_VERSION;
    private AuthorizationProvider authorization = AuthorizationProvider.NONE;
    private Long expectContinueThreshold;
    private Duration requestTimeout;

    private Builder() {}

    public Builder requestUrl(String requestUrl) {
      this.requestUrl = requestUrl;
      return this;
    }

    public Builder contentType(ContentType contentType) {
      this.contentType = Objects.requireNonNull(contentType);
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

    public Builder threadPoolSize(int threadPoolSize) {
      if (threadPoolSize <= 0) {
        throw new IllegalArgumentException("Thread pool size must be positive");
      }
      this.threadPoolSize = threadPoolSize;
      return this;
    }

    public Builder connectTimeout(Duration connectTimeout) {
      this.connectTimeout = Objects.requireNonNull(connectTimeout);
      return this;
    }

    public Builder httpVersion(HttpClient.Version httpVersion) {
      this.httpVersion = Objects.requireNonNull(httpVersion);
      return this;
    }

    /** Sets the provider of renewable credentials, e.g. OAuth2 bearer tokens. */
    public Builder authorization(AuthorizationProvider authorization) {
      this.authorization = authorization != null ? authorization : AuthorizationProvider.NONE;
      return this;
    }

    /**
     * Sends {@code Expect: 100-continue} (RFC 9110 §10.1.1) for payloads of at least {@code
     * minBytes}, so the server can reject the credentials before a large upload, at the cost of one
     * round trip. {@code 0} applies it to every upload, a negative value disables it. Defaults to 8
     * MiB with an {@link AuthorizationProvider}, disabled otherwise. Disable it for servers or
     * proxies that do not answer {@code 100 Continue}.
     */
    public Builder expectContinueThreshold(long minBytes) {
      this.expectContinueThreshold = minBytes < 0 ? EXPECT_CONTINUE_DISABLED : minBytes;
      return this;
    }

    /**
     * Sets the maximum time to wait for the response, upload included. None by default, as the
     * upload time depends on the payload size; set it to bound a server that never answers.
     */
    public Builder requestTimeout(Duration requestTimeout) {
      Objects.requireNonNull(requestTimeout, "Request timeout cannot be null");
      if (requestTimeout.isNegative() || requestTimeout.isZero()) {
        throw new IllegalArgumentException("Request timeout must be positive");
      }
      this.requestTimeout = requestTimeout;
      return this;
    }

    public DicomStowConfig build() {
      Objects.requireNonNull(requestUrl, "Request URL is required");
      HttpAuthorization.checkNoConflict(authorization, headers);
      return new DicomStowConfig(this);
    }
  }
}
