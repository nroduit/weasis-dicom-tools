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

import java.io.IOException;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * Supplies the {@code Authorization} header of DICOMweb requests. The host application (e.g. the
 * Weasis {@code AuthMethod}) owns the OAuth2 flow: acquiring, caching and refreshing tokens. The
 * client calls {@link #authorization()} before every request and, when the server rejects the
 * credentials (HTTP 401 with {@code invalid_token} or no error code, RFC 6750 §3.1), calls {@link
 * #invalidate(String)} and replays the request once.
 *
 * <p>{@link #authorization()} is called for every request, so implementations should return a
 * cached token and renew it before it expires rather than wait for a rejection.
 */
@FunctionalInterface
public interface AuthorizationProvider {

  /** Provider for unauthenticated servers. */
  AuthorizationProvider NONE = () -> null;

  /**
   * @return the {@code Authorization} header value, or {@code null} to send the request without it
   * @throws IOException if credentials are required but cannot be obtained
   */
  String authorization() throws IOException;

  /**
   * Discards the cached credentials if they are still {@code rejected}. Concurrent requests
   * rejected with the same token must lead to a single renewal, so a value already replaced by
   * another thread must be kept.
   *
   * @param rejected the header value the server refused
   */
  default void invalidate(String rejected) {}

  /**
   * Creates an OAuth2 bearer token provider (RFC 6750 §2.1).
   *
   * @param accessToken supplies the current access token, refreshing it when needed
   * @param onRejected discards the cached token (e.g. {@code AuthMethod::resetToken}); called only
   *     when the rejected token is still the current one
   */
  static AuthorizationProvider bearer(Supplier<String> accessToken, Runnable onRejected) {
    Objects.requireNonNull(accessToken, "Access token supplier cannot be null");
    Objects.requireNonNull(onRejected, "Rejection callback cannot be null");
    return new AuthorizationProvider() {
      @Override
      public String authorization() throws IOException {
        String token = accessToken.get();
        if (token == null || token.isBlank()) {
          throw new IOException("Cannot get an OAuth2 access token");
        }
        return "Bearer " + token;
      }

      @Override
      public synchronized void invalidate(String rejected) {
        String current = accessToken.get();
        if (current == null || ("Bearer " + current).equals(rejected)) {
          onRejected.run();
        }
      }
    };
  }
}
