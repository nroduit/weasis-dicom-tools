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

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Configuration for DICOM STOW-RS operations. Provides builder pattern for flexible configuration.
 */
public final class DicomStowConfig {

  private static final String DEFAULT_USER_AGENT = "Weasis STOW-RS Client";
  private static final int DEFAULT_THREAD_POOL_SIZE = 5;
  private static final Duration DEFAULT_CONNECT_TIMEOUT = Duration.ofSeconds(10);

  private final String requestUrl;
  private final ContentType contentType;
  private final String userAgent;
  private final Map<String, String> headers;
  private final int threadPoolSize;
  private final Duration connectTimeout;

  private DicomStowConfig(Builder builder) {
    this.requestUrl = normalizeUrl(builder.requestUrl);
    this.contentType = builder.contentType;
    this.userAgent = builder.userAgent;
    this.headers = Map.copyOf(builder.headers);
    this.threadPoolSize = builder.threadPoolSize;
    this.connectTimeout = builder.connectTimeout;
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

    public DicomStowConfig build() {
      Objects.requireNonNull(requestUrl, "Request URL is required");
      return new DicomStowConfig(this);
    }
  }
}
