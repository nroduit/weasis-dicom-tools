/*
 * Copyright (c) 2021 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.web;

import java.io.IOException;
import java.io.Serial;
import java.net.http.HttpResponse;
import java.util.Objects;
import java.util.Optional;
import org.weasis.core.util.annotations.Generated;

/**
 * Exception thrown when HTTP operations fail with error status codes. Provides detailed information
 * about HTTP errors including status codes, response bodies, and categorization of error types.
 */
@Generated
public final class HttpException extends IOException {

  @Serial private static final long serialVersionUID = 1L;
  private final int statusCode;
  private final String responseBody;
  private final HttpErrorCategory category;

  /**
   * Creates an HTTP exception with status code and cause.
   *
   * @param message the error message
   * @param statusCode the HTTP status code
   * @param cause the underlying cause
   */
  public HttpException(String message, int statusCode, Throwable cause) {
    this(message, statusCode, null, cause);
  }

  /**
   * Creates an HTTP exception with full details.
   *
   * @param message the error message
   * @param statusCode the HTTP status code
   * @param responseBody the HTTP response body, may be null
   * @param cause the underlying cause, may be null
   */
  public HttpException(String message, int statusCode, String responseBody, Throwable cause) {
    super(createDetailedMessage(message, statusCode, responseBody), cause);
    this.statusCode = validateStatusCode(statusCode);
    this.responseBody = responseBody;
    this.category = HttpErrorCategory.fromStatusCode(statusCode);
  }

  /**
   * Creates an HTTP exception from an HTTP response.
   *
   * @param response the HTTP response
   * @return a new HttpException instance
   */
  public static HttpException fromResponse(HttpResponse<String> response) {
    return fromResponse("HTTP request failed", response);
  }

  /**
   * Creates an HTTP exception from an HTTP response with custom message.
   *
   * @param message the custom error message
   * @param response the HTTP response
   * @return a new HttpException instance
   */
  public static HttpException fromResponse(String message, HttpResponse<String> response) {
    Objects.requireNonNull(response, "HTTP response cannot be null");
    return new HttpException(
        Objects.requireNonNull(message, "Message cannot be null"),
        response.statusCode(),
        response.body(),
        null);
  }

  /**
   * Returns the HTTP status code.
   *
   * @return the HTTP status code
   */
  public int getStatusCode() {
    return statusCode;
  }

  /**
   * Returns the HTTP response body if available.
   *
   * @return optional containing response body, empty if not available
   */
  public Optional<String> getResponseBody() {
    return Optional.ofNullable(responseBody);
  }

  /**
   * Returns the error category based on status code.
   *
   * @return the HTTP error category
   */
  public HttpErrorCategory getCategory() {
    return category;
  }

  /**
   * Checks if this is a client error (4xx status code).
   *
   * @return true if client error, false otherwise
   */
  public boolean isClientError() {
    return category == HttpErrorCategory.CLIENT_ERROR;
  }

  /**
   * Checks if this is a server error (5xx status code).
   *
   * @return true if server error, false otherwise
   */
  public boolean isServerError() {
    return category == HttpErrorCategory.SERVER_ERROR;
  }

  /**
   * Checks if this error might be recoverable (typically 5xx or specific 4xx codes).
   *
   * @return true if potentially recoverable, false otherwise
   */
  public boolean isRecoverable() {
    return isServerError()
        || statusCode == 408
        || // Request Timeout
        statusCode == 429; // Too Many Requests
  }

  /**
   * Returns the standard HTTP status text for the status code.
   *
   * @return the HTTP status text
   */
  public String getStatusText() {
    return HttpStatusText.getText(statusCode);
  }

  private static String createDetailedMessage(String message, int statusCode, String responseBody) {
    var sb = new StringBuilder(message);
    sb.append(" (HTTP ").append(statusCode);

    String statusText = HttpStatusText.getText(statusCode);
    if (!statusText.isEmpty()) {
      sb.append(" ").append(statusText);
    }
    sb.append(")");

    if (responseBody != null && !responseBody.trim().isEmpty()) {
      String truncatedBody =
          responseBody.length() > 200 ? responseBody.substring(0, 197) + "..." : responseBody;
      sb.append(": ").append(truncatedBody);
    }

    return sb.toString();
  }

  private static int validateStatusCode(int statusCode) {
    if (statusCode < 100 || statusCode > 599) {
      throw new IllegalArgumentException("Invalid HTTP status code: " + statusCode);
    }
    return statusCode;
  }

  /** Categorizes HTTP errors by status code ranges. */
  public enum HttpErrorCategory {
    /** Informational responses (1xx) */
    INFORMATIONAL,
    /** Successful responses (2xx) */
    SUCCESS,
    /** Redirection messages (3xx) */
    REDIRECTION,
    /** Client error responses (4xx) */
    CLIENT_ERROR,
    /** Server error responses (5xx) */
    SERVER_ERROR,
    /** Unknown or invalid status code */
    UNKNOWN;

    static HttpErrorCategory fromStatusCode(int statusCode) {
      return switch (statusCode / 100) {
        case 1 -> INFORMATIONAL;
        case 2 -> SUCCESS;
        case 3 -> REDIRECTION;
        case 4 -> CLIENT_ERROR;
        case 5 -> SERVER_ERROR;
        default -> UNKNOWN;
      };
    }
  }

  /** Utility class for HTTP status text lookup. */
  private static final class HttpStatusText {

    static String getText(int statusCode) {
      return switch (statusCode) {
        // 4xx Client Errors
        case 400 -> "Bad Request";
        case 401 -> "Unauthorized";
        case 403 -> "Forbidden";
        case 404 -> "Not Found";
        case 405 -> "Method Not Allowed";
        case 408 -> "Request Timeout";
        case 409 -> "Conflict";
        case 410 -> "Gone";
        case 413 -> "Payload Too Large";
        case 415 -> "Unsupported Media Type";
        case 422 -> "Unprocessable Entity";
        case 429 -> "Too Many Requests";

        // 5xx Server Errors
        case 500 -> "Internal Server Error";
        case 501 -> "Not Implemented";
        case 502 -> "Bad Gateway";
        case 503 -> "Service Unavailable";
        case 504 -> "Gateway Timeout";
        case 505 -> "HTTP Version Not Supported";

        // Common 2xx Success codes (shouldn't normally create exceptions)
        case 200 -> "OK";
        case 201 -> "Created";
        case 202 -> "Accepted";
        case 204 -> "No Content";

        // Common 3xx Redirection codes
        case 301 -> "Moved Permanently";
        case 302 -> "Found";
        case 304 -> "Not Modified";
        case 307 -> "Temporary Redirect";
        case 308 -> "Permanent Redirect";

        default -> "";
      };
    }

    private HttpStatusText() {
      // Utility class
    }
  }
}
