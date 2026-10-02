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

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArrayList;

/** Loopback HTTP server replying with queued responses and recording every request. */
final class RecordingHttpServer implements AutoCloseable {

  record RecordedRequest(
      String method, String uri, Map<String, List<String>> headers, byte[] body) {
    String header(String name) {
      List<String> values = headers.get(name);
      return values == null ? null : values.get(0);
    }
  }

  record CannedResponse(int status, Map<String, String> headers, byte[] body) {
    static CannedResponse of(int status) {
      return new CannedResponse(status, Map.of(), new byte[0]);
    }

    static CannedResponse of(int status, String headerName, String headerValue) {
      return new CannedResponse(status, Map.of(headerName, headerValue), new byte[0]);
    }
  }

  private final HttpServer server;
  private final List<RecordedRequest> requests = new CopyOnWriteArrayList<>();
  private final ConcurrentLinkedQueue<CannedResponse> responses = new ConcurrentLinkedQueue<>();

  RecordingHttpServer() throws IOException {
    server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
    server.createContext("/", this::handle);
    server.start();
  }

  String url(String path) {
    return "http://localhost:" + server.getAddress().getPort() + path;
  }

  void enqueue(CannedResponse... canned) {
    responses.addAll(List.of(canned));
  }

  List<RecordedRequest> requests() {
    return requests;
  }

  @Override
  public void close() {
    server.stop(0);
  }

  private void handle(HttpExchange exchange) throws IOException {
    requests.add(
        new RecordedRequest(
            exchange.getRequestMethod(),
            exchange.getRequestURI().toString(),
            Map.copyOf(exchange.getRequestHeaders()),
            exchange.getRequestBody().readAllBytes()));
    CannedResponse rsp = responses.poll();
    if (rsp == null) {
      rsp = CannedResponse.of(200);
    }
    rsp.headers().forEach((k, v) -> exchange.getResponseHeaders().add(k, v));
    exchange.sendResponseHeaders(rsp.status(), rsp.body().length == 0 ? -1 : rsp.body().length);
    exchange.getResponseBody().write(rsp.body());
    exchange.close();
  }
}
