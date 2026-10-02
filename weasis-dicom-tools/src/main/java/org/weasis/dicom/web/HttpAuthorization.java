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
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.WebSocket;
import java.net.http.WebSocketHandshakeException;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Applies an {@link AuthorizationProvider} to DICOMweb exchanges: sets the {@code Authorization}
 * header and, when the server rejects the credentials, invalidates them and replays the request
 * once.
 */
final class HttpAuthorization {

  private static final Logger LOGGER = LoggerFactory.getLogger(HttpAuthorization.class);

  static final String AUTHORIZATION = "Authorization";
  private static final Pattern ERROR_CODE =
      Pattern.compile("\\berror\\s*=\\s*\"?([A-Za-z_]+)", Pattern.CASE_INSENSITIVE);

  private HttpAuthorization() {}

  /** Sends {@code request}, retrying once with fresh credentials if they were rejected. */
  static <T> HttpResponse<T> send(
      HttpClient client,
      AuthorizationProvider auth,
      HttpRequest request,
      HttpResponse.BodyHandler<T> handler)
      throws IOException, InterruptedException {
    return send(client, auth, request, handler, true);
  }

  /**
   * @param replayable whether the request body can be published again; when not, rejected
   *     credentials are still invalidated but the request is not replayed
   */
  static <T> HttpResponse<T> send(
      HttpClient client,
      AuthorizationProvider auth,
      HttpRequest request,
      HttpResponse.BodyHandler<T> handler,
      boolean replayable)
      throws IOException, InterruptedException {
    String credentials = auth.authorization();
    HttpResponse<T> response = client.send(withCredentials(request, credentials), handler);
    if (credentials != null
        && isRejected(credentials, response.statusCode(), response.headers())) {
      if (!replayable) {
        auth.invalidate(credentials);
        return response;
      }
      LOGGER.debug("Credentials rejected by {}, retrying with renewed ones", request.uri());
      auth.invalidate(credentials);
      response = client.send(withCredentials(request, auth.authorization()), handler);
    }
    return response;
  }

  /** Opens a WebSocket, retrying the handshake once with fresh credentials if rejected. */
  static CompletableFuture<WebSocket> connect(
      AuthorizationProvider auth,
      Supplier<WebSocket.Builder> builders,
      URI uri,
      WebSocket.Listener listener) {
    return connect(auth, builders, uri, listener, true);
  }

  /**
   * Never follows redirects with provider credentials, as the target may be another origin. A
   * static {@code Authorization} header keeps the legacy behavior.
   */
  static HttpClient.Redirect redirectPolicy(AuthorizationProvider auth) {
    return auth != AuthorizationProvider.NONE ? HttpClient.Redirect.NEVER : HttpClient.Redirect.NORMAL;
  }

  static void checkNoConflict(AuthorizationProvider auth, Map<String, String> headers) {
    if (auth != AuthorizationProvider.NONE
        && headers.keySet().stream().anyMatch(AUTHORIZATION::equalsIgnoreCase)) {
      throw new IllegalArgumentException(
          "Authorization header conflicts with the AuthorizationProvider");
    }
  }

  static void warnIfUnencrypted(AuthorizationProvider auth, String url) {
    if (auth != AuthorizationProvider.NONE && url.regionMatches(true, 0, "http:", 0, 5)) {
      // RFC 6750 §5.3 requires TLS to protect bearer tokens.
      LOGGER.warn("Credentials are sent over an unencrypted connection: {}", url);
    }
  }

  /**
   * A 401 is worth a retry only if credentials were sent and the challenge does not report an
   * error that renewed credentials cannot fix (RFC 6750 §3.1: only {@code invalid_token}, or no
   * error code, means the token itself was refused).
   */
  static boolean isRejected(String credentials, int status, HttpHeaders headers) {
    if (credentials == null || status != HttpURLConnection.HTTP_UNAUTHORIZED) {
      return false;
    }
    for (String challenge : headers.allValues("WWW-Authenticate")) {
      var matcher = ERROR_CODE.matcher(challenge);
      if (matcher.find() && !"invalid_token".equalsIgnoreCase(matcher.group(1))) {
        return false;
      }
    }
    return true;
  }

  private static HttpRequest withCredentials(HttpRequest request, String credentials) {
    if (credentials == null) {
      return request;
    }
    return HttpRequest.newBuilder(request, (name, value) -> true)
        .setHeader(AUTHORIZATION, credentials)
        .build();
  }

  private static CompletableFuture<WebSocket> connect(
      AuthorizationProvider auth,
      Supplier<WebSocket.Builder> builders,
      URI uri,
      WebSocket.Listener listener,
      boolean retry) {
    String credentials;
    try {
      credentials = auth.authorization();
    } catch (IOException e) {
      return CompletableFuture.failedFuture(e);
    }
    WebSocket.Builder builder = builders.get();
    if (credentials != null) {
      builder.header(AUTHORIZATION, credentials);
    }
    return builder
        .buildAsync(uri, listener)
        .exceptionallyCompose(
            ex -> {
              Throwable cause = ex instanceof CompletionException ? ex.getCause() : ex;
              if (retry
                  && cause instanceof WebSocketHandshakeException handshake
                  && isRejected(
                      credentials,
                      handshake.getResponse().statusCode(),
                      handshake.getResponse().headers())) {
                auth.invalidate(credentials);
                return connect(auth, builders, uri, listener, false);
              }
              return CompletableFuture.failedFuture(cause);
            });
  }
}
