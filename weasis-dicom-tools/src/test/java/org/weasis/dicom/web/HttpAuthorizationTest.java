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

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.UID;
import org.dcm4che3.data.VR;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.weasis.dicom.web.RecordingHttpServer.CannedResponse;
import org.weasis.dicom.web.RecordingHttpServer.RecordedRequest;

@DisplayNameGeneration(ReplaceUnderscores.class)
class HttpAuthorizationTest {

  private static final String WWW_AUTHENTICATE = "WWW-Authenticate";

  private static HttpHeaders challenge(String value) {
    return HttpHeaders.of(Map.of(WWW_AUTHENTICATE, List.of(value)), (k, v) -> true);
  }

  /** Bearer provider whose token changes on each accepted invalidation. */
  private static final class RotatingToken {
    final AtomicInteger generation = new AtomicInteger(1);
    final AtomicInteger resets = new AtomicInteger();
    final AuthorizationProvider provider =
        AuthorizationProvider.bearer(
            () -> "token" + generation.get(),
            () -> {
              resets.incrementAndGet();
              generation.incrementAndGet();
            });
  }

  @Nested
  class Rejection {

    @ParameterizedTest
    @ValueSource(
        strings = {
          "Bearer realm=\"pacs\"",
          "Bearer realm=\"pacs\", error=\"invalid_token\", error_description=\"expired\"",
          "Basic realm=\"pacs\""
        })
    void retry_when_token_refused(String header) {
      assertTrue(HttpAuthorization.isRejected("Bearer t", 401, challenge(header)));
    }

    @ParameterizedTest
    @ValueSource(
        strings = {
          "Bearer error=\"insufficient_scope\", scope=\"ups.write\"",
          "Bearer error=invalid_request"
        })
    void no_retry_when_renewal_cannot_help(String header) {
      assertFalse(HttpAuthorization.isRejected("Bearer t", 401, challenge(header)));
    }

    @Test
    void no_retry_without_credentials_or_on_forbidden() {
      HttpHeaders none = HttpHeaders.of(Map.of(), (k, v) -> true);
      assertFalse(HttpAuthorization.isRejected(null, 401, none));
      assertFalse(HttpAuthorization.isRejected("Bearer t", 403, none));
    }
  }

  @Test
  void redirects_are_disabled_only_with_a_provider() {
    assertEquals(
        HttpClient.Redirect.NEVER,
        HttpAuthorization.redirectPolicy(new RotatingToken().provider));
    assertEquals(
        HttpClient.Redirect.NORMAL,
        HttpAuthorization.redirectPolicy(AuthorizationProvider.NONE));
  }

  @Nested
  class Bearer_provider {

    @Test
    void stale_rejection_keeps_the_renewed_token() throws IOException {
      RotatingToken token = new RotatingToken();
      String first = token.provider.authorization();

      // Two concurrent requests rejected with the same token: only one renewal
      token.provider.invalidate(first);
      token.provider.invalidate(first);

      assertEquals(1, token.resets.get());
      assertEquals("Bearer token2", token.provider.authorization());
    }

    @Test
    void conflicting_static_header_is_rejected() {
      var builder =
          DicomStowConfig.builder()
              .requestUrl("https://pacs/dicomweb")
              .header("Authorization", "Bearer x")
              .authorization(new RotatingToken().provider);
      assertThrows(IllegalArgumentException.class, builder::build);
    }
  }

  @Nested
  class Stow {

    private RecordingHttpServer server;
    @TempDir Path tempDir;

    @BeforeEach
    void startServer() throws IOException {
      server = new RecordingHttpServer();
    }

    @AfterEach
    void stopServer() {
      server.close();
    }

    private DicomStowRS stow(AuthorizationProvider auth) {
      return stow(auth, DicomStowConfig.DEFAULT_EXPECT_CONTINUE_THRESHOLD);
    }

    private DicomStowRS stow(AuthorizationProvider auth, long expectContinueThreshold) {
      return new DicomStowRS(
          DicomStowConfig.builder()
              .requestUrl(server.url("/dicomweb"))
              .authorization(auth)
              .expectContinueThreshold(expectContinueThreshold)
              .build());
    }

    private Path smallFile() throws IOException {
      return Files.write(tempDir.resolve("test.dcm"), new byte[] {1, 2, 3, 4});
    }

    private static Attributes dataset() {
      Attributes attrs = new Attributes();
      attrs.setString(Tag.SOPClassUID, VR.UI, UID.SecondaryCaptureImageStorage);
      attrs.setString(Tag.SOPInstanceUID, VR.UI, "1.2.3.4");
      return attrs;
    }

    @Test
    void expect_continue_applies_to_large_known_payloads_with_a_provider() {
      DicomStowConfig withProvider =
          DicomStowConfig.builder()
              .requestUrl("https://pacs")
              .authorization(new RotatingToken().provider)
              .build();
      long threshold = DicomStowConfig.DEFAULT_EXPECT_CONTINUE_THRESHOLD;
      assertTrue(withProvider.useExpectContinue(threshold));
      assertFalse(withProvider.useExpectContinue(threshold - 1));
      assertFalse(withProvider.useExpectContinue(-1));

      DicomStowConfig staticHeader =
          DicomStowConfig.builder()
              .requestUrl("https://pacs")
              .header("Authorization", "Basic abc")
              .build();
      assertEquals(-1, staticHeader.getExpectContinueThreshold());
      assertFalse(staticHeader.useExpectContinue(Long.MAX_VALUE));

      DicomStowConfig always =
          DicomStowConfig.builder().requestUrl("https://pacs").expectContinueThreshold(0).build();
      assertTrue(always.useExpectContinue(-1));
      assertTrue(always.useExpectContinue(1));
    }

    @Test
    void small_upload_skips_expect_continue() throws Exception {
      try (DicomStowRS stow = stow(new RotatingToken().provider)) {
        stow.uploadDicom(smallFile());
      }

      assertNull(server.requests().get(0).header("Expect"));
    }

    @Test
    void request_timeout_is_applied_when_configured() throws Exception {
      HttpClient client = mock(HttpClient.class);
      @SuppressWarnings("unchecked")
      HttpResponse<Object> response = mock(HttpResponse.class);
      when(response.statusCode()).thenReturn(200);
      when(response.body()).thenReturn("");
      when(client.send(any(), any())).thenReturn(response);
      DicomStowConfig config =
          DicomStowConfig.builder()
              .requestUrl("https://pacs")
              .requestTimeout(Duration.ofMinutes(5))
              .build();

      try (DicomStowRS stow = new DicomStowRS(config, client)) {
        stow.uploadDicom(smallFile());
      }

      ArgumentCaptor<HttpRequest> request = ArgumentCaptor.forClass(HttpRequest.class);
      verify(client).send(request.capture(), any());
      assertEquals(Optional.of(Duration.ofMinutes(5)), request.getValue().timeout());
      assertTrue(DicomStowConfig.builder().requestUrl("x").build().getRequestTimeout().isEmpty());
      assertThrows(
          IllegalArgumentException.class,
          () -> DicomStowConfig.builder().requestTimeout(Duration.ZERO));
    }

    @Test
    void rejected_token_is_renewed_and_upload_replayed() throws Exception {
      RotatingToken token = new RotatingToken();
      server.enqueue(CannedResponse.of(401), CannedResponse.of(200));
      try (DicomStowRS stow = stow(token.provider, 0)) {
        stow.uploadDicom(smallFile());
      }

      List<RecordedRequest> requests = server.requests();
      assertEquals(2, requests.size());
      assertEquals("Bearer token1", requests.get(0).header("Authorization"));
      assertEquals("Bearer token2", requests.get(1).header("Authorization"));
      assertTrue("100-continue".equalsIgnoreCase(requests.get(0).header("Expect")));
      assertEquals("/dicomweb/studies", requests.get(1).uri());
      assertArrayEquals(requests.get(0).body(), requests.get(1).body());
      assertTrue(new String(requests.get(1).body()).endsWith("--"));
      assertEquals(1, token.resets.get());
    }

    @Test
    void one_shot_stream_is_not_replayed_but_token_is_invalidated() throws Exception {
      RotatingToken token = new RotatingToken();
      server.enqueue(CannedResponse.of(401));
      Attributes fmi = dataset().createFileMetaInformation(UID.ExplicitVRLittleEndian);

      try (DicomStowRS stow = stow(token.provider)) {
        HttpException ex =
            assertThrows(
                HttpException.class,
                () -> stow.uploadDicom(new ByteArrayInputStream(new byte[] {1, 2}), fmi));
        assertEquals(401, ex.getStatusCode());
      }

      assertEquals(1, server.requests().size());
      assertEquals(1, token.resets.get());
    }

    @Test
    void insufficient_scope_is_not_retried() throws Exception {
      RotatingToken token = new RotatingToken();
      server.enqueue(
          CannedResponse.of(401, WWW_AUTHENTICATE, "Bearer error=\"insufficient_scope\""));

      try (DicomStowRS stow = stow(token.provider)) {
        assertThrows(
            HttpException.class,
            () -> stow.uploadDicom(dataset(), UID.ExplicitVRLittleEndian));
      }

      assertEquals(1, server.requests().size());
      assertEquals(0, token.resets.get());
    }

    @Test
    void legacy_static_header_is_still_sent() throws Exception {
      try (DicomStowRS stow =
          new DicomStowRS(
              server.url("/dicomweb"),
              ContentType.APPLICATION_DICOM,
              null,
              Map.of("Authorization", "Basic abc"))) {
        stow.uploadDicom(dataset(), UID.ExplicitVRLittleEndian);
      }

      assertEquals("Basic abc", server.requests().get(0).header("Authorization"));
    }
  }
}
