/*
 * Copyright (c) 2017-2019 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.mf;

import static org.weasis.dicom.web.MultipartConstants.CONTENT_TYPE;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Represents an HTTP header key-value pair for DICOM web access configurations. This immutable
 * class encapsulates HTTP headers used in WADO (Web Access to DICOM Objects) operations and archive
 * queries.
 *
 * <p>HTTP tags are commonly used for:
 *
 * <ul>
 *   <li>Authentication headers (Authorization, Bearer tokens)
 *   <li>Content negotiation (Accept, Content-Type)
 *   <li>Custom application headers (X-Custom-Header)
 *   <li>CORS and security headers
 * </ul>
 *
 * <p><strong>Usage Example:</strong>
 *
 * <pre>{@code
 * HttpTag authHeader = new HttpTag("Authorization", "Bearer token123");
 * HttpTag contentType = HttpTag.of("Content-Type", "application/dicom+json");
 * }</pre>
 *
 * @see ArcParameters
 * @see WadoParameters
 * @since 5.34.0.3
 */
public final class HttpTag {

  private static final Pattern VALID_HEADER_NAME =
      Pattern.compile("^[a-zA-Z0-9!#$&'*+\\-.^_`|~]+$");
  private static final int MAX_HEADER_LENGTH = 8192; // RFC 7230 recommended limit
  private final String key;
  private final String value;

  /**
   * Creates an HTTP tag with the specified key and value.
   *
   * @param key the HTTP header name, must be non-null and follow RFC 7230 token format
   * @param value the HTTP header value, must be non-null
   * @throws NullPointerException if key or value is null
   * @throws IllegalArgumentException if key contains invalid characters or value is too long
   */
  public HttpTag(String key, String value) {
    this.key = validateKey(Objects.requireNonNull(key, "HTTP header key cannot be null"));
    this.value = validateValue(Objects.requireNonNull(value, "HTTP header value cannot be null"));
  }

  /**
   * Creates an HTTP tag with the specified key and value. This is a convenience factory method that
   * provides better readability for method chaining.
   *
   * @param key the HTTP header name, must be non-null and follow RFC 7230 token format
   * @param value the HTTP header value, must be non-null
   * @return a new HttpTag instance
   * @throws NullPointerException if key or value is null
   * @throws IllegalArgumentException if key contains invalid characters or value is too long
   * @since 5.34.0.4
   */
  public static HttpTag of(String key, String value) {
    return new HttpTag(key, value);
  }

  /**
   * Creates an authorization header with Bearer token.
   *
   * @param token the bearer token, must be non-null
   * @return a new HttpTag for authorization
   * @throws NullPointerException if token is null
   * @since 5.34.0.4
   */
  public static HttpTag authorization(String token) {
    return new HttpTag(
        "Authorization", "Bearer " + Objects.requireNonNull(token, "Token cannot be null"));
  }

  /**
   * Creates a content-type header.
   *
   * @param contentType the content type, must be non-null
   * @return a new HttpTag for content type
   * @throws NullPointerException if contentType is null
   * @since 5.34.0.4
   */
  public static HttpTag contentType(String contentType) {
    return new HttpTag(
        CONTENT_TYPE, Objects.requireNonNull(contentType, "Content type cannot be null"));
  }

  /**
   * Returns the HTTP header name.
   *
   * @return the header key
   */
  public String getKey() {
    return key;
  }

  /**
   * Returns the HTTP header value.
   *
   * @return the header value
   */
  public String getValue() {
    return value;
  }

  /**
   * Checks if this HTTP tag represents an authorization header.
   *
   * @return true if this is an authorization header, false otherwise
   * @since 5.34.0.4
   */
  public boolean isAuthorizationHeader() {
    return "Authorization".equalsIgnoreCase(key);
  }

  /**
   * Checks if this HTTP tag has a sensitive value that should not be logged in plain text.
   *
   * @return true if the header contains sensitive information
   * @since 5.34.0.4
   */
  public boolean isSensitive() {
    String lowerKey = key.toLowerCase();
    return lowerKey.contains("auth")
        || lowerKey.contains("token")
        || lowerKey.contains("password")
        || lowerKey.contains("secret")
        || lowerKey.contains("key");
  }

  /**
   * Returns a string representation suitable for HTTP header formatting.
   *
   * @return the HTTP header in "Key: Value" format
   */
  public String toHeaderString() {
    return key + ": " + value;
  }

  @Override
  public String toString() {
    return isSensitive()
        ? String.format("HttpTag{key='%s', value='[REDACTED]'}", key)
        : String.format("HttpTag{key='%s', value='%s'}", key, value);
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    }
    if (!(obj instanceof HttpTag that)) {
      return false;
    }
    return Objects.equals(key, that.key) && Objects.equals(value, that.value);
  }

  @Override
  public int hashCode() {
    return Objects.hash(key, value);
  }

  // Validates HTTP header name according to RFC 7230
  private String validateKey(String key) {
    if (key.isBlank()) {
      throw new IllegalArgumentException("HTTP header key cannot be blank");
    }
    if (!VALID_HEADER_NAME.matcher(key).matches()) {
      throw new IllegalArgumentException("HTTP header key contains invalid characters: " + key);
    }
    return key.trim();
  }

  // Validates HTTP header value length and basic format
  private String validateValue(String value) {
    if (value.length() > MAX_HEADER_LENGTH) {
      throw new IllegalArgumentException(
          "HTTP header value exceeds maximum length: " + value.length());
    }
    // Remove leading/trailing whitespace but preserve internal whitespace
    String trimmed = value.trim();
    // Check for control characters that aren't allowed in HTTP headers
    if (trimmed
        .chars()
        .anyMatch(c -> c < 32 && c != 9)) { // Allow tab (9) but not other control chars
      throw new IllegalArgumentException("HTTP header value contains invalid control characters");
    }
    return trimmed;
  }
}
