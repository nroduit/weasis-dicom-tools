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

import static org.weasis.dicom.web.MultipartConstants.CONTENT_TYPE;
import static org.weasis.dicom.web.UpsQuery.encode;

import jakarta.json.Json;
import jakarta.json.stream.JsonGenerator;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.WebSocket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Consumer;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Code;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.UID;
import org.dcm4che3.data.VR;
import org.dcm4che3.json.JSONReader;
import org.dcm4che3.json.JSONWriter;
import org.dcm4che3.util.DateUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Unified Procedure Step client over DICOMweb (UPS-RS, PS3.18 §11), the RESTful counterpart of the
 * dcm4che {@code upsscu} tool. Payloads are encoded as {@code application/dicom+json}.
 *
 * <p>Authentication is delegated to the {@link AuthorizationProvider} of the {@link UpsConfig}, so
 * the host application keeps control of the OAuth2 tokens. On HTTP 401 the provider is invalidated
 * and the request is replayed once.
 */
public class UpsRS {

  private static final Logger LOGGER = LoggerFactory.getLogger(UpsRS.class);

  static final String DICOM_JSON = MultipartConstants.DicomContentType.JSON.getMimeType();
  private static final String WORKITEMS = "/workitems";

  private final UpsConfig config;
  private final HttpClient httpClient;

  public UpsRS(UpsConfig config) {
    this(config, createHttpClient(config));
  }

  /**
   * Creates a client sharing {@code httpClient}, e.g. to reuse its connection pool. The caller is
   * responsible for its redirect policy: following redirects may replay the credentials to another
   * origin.
   */
  public UpsRS(UpsConfig config, HttpClient httpClient) {
    this.config = Objects.requireNonNull(config, "Configuration cannot be null");
    this.httpClient = Objects.requireNonNull(httpClient, "HTTP client cannot be null");
    HttpAuthorization.warnIfUnencrypted(config.getAuthorization(), config.getBaseUrl());
  }

  public UpsConfig getConfig() {
    return config;
  }

  /**
   * Creates a workitem (PS3.18 §11.4). Procedure Step State is set to SCHEDULED and Scheduled
   * Procedure Step Start DateTime to now when absent, as required by PS3.4 §CC.2.5.1.3.1.
   *
   * @param workitemUid the SOP Instance UID to assign, or {@code null} to let the server assign it
   */
  public UpsResponse createWorkitem(Attributes workitem, String workitemUid)
      throws IOException, InterruptedException {
    Attributes data = new Attributes(Objects.requireNonNull(workitem, "Workitem cannot be null"));
    if (!data.containsValue(Tag.ProcedureStepState)) {
      data.setString(Tag.ProcedureStepState, VR.CS, ProcedureStepState.SCHEDULED.code());
    }
    if (!data.containsValue(Tag.ScheduledProcedureStepStartDateTime)) {
      data.setString(
          Tag.ScheduledProcedureStepStartDateTime, VR.DT, DateUtils.formatDT(null, new Date()));
    }
    String query = workitemUid == null ? "" : "?workitem=" + encode(workitemUid);
    return execute(request(WORKITEMS + query).POST(jsonBody(data)));
  }

  /** Retrieves a workitem (PS3.18 §11.5). */
  public Attributes retrieveWorkitem(String workitemUid) throws IOException, InterruptedException {
    HttpResponse<byte[]> response = send(request(workitemPath(workitemUid)).GET());
    List<Attributes> items = readDatasets(response.body());
    if (items.isEmpty()) {
      throw new IOException("Empty response when retrieving workitem " + workitemUid);
    }
    return items.get(0);
  }

  /**
   * Updates a workitem (PS3.18 §11.6).
   *
   * @param transactionUid required once the workitem is IN PROGRESS, {@code null} when SCHEDULED
   */
  public UpsResponse updateWorkitem(String workitemUid, String transactionUid, Attributes changes)
      throws IOException, InterruptedException {
    Objects.requireNonNull(changes, "Changes cannot be null");
    String query = transactionUid == null ? "" : "?transaction=" + encode(transactionUid);
    return execute(request(workitemPath(workitemUid) + query).POST(jsonBody(changes)));
  }

  /** Changes the state of a workitem (PS3.18 §11.7). */
  public UpsResponse changeState(
      String workitemUid, String transactionUid, ProcedureStepState state)
      throws IOException, InterruptedException {
    Attributes data = new Attributes(2);
    data.setString(
        Tag.TransactionUID,
        VR.UI,
        Objects.requireNonNull(transactionUid, "Transaction UID cannot be null"));
    data.setString(Tag.ProcedureStepState, VR.CS, Objects.requireNonNull(state).code());
    return execute(request(workitemPath(workitemUid) + "/state").PUT(jsonBody(data)));
  }

  /**
   * Requests the cancellation of a workitem owned by another performer (PS3.18 §11.8).
   *
   * @param reason optional dataset, see {@link #cancellationReason}
   */
  public UpsResponse requestCancellation(String workitemUid, Attributes reason)
      throws IOException, InterruptedException {
    HttpRequest.Builder builder = request(workitemPath(workitemUid) + "/cancelrequest");
    builder.POST(reason == null ? HttpRequest.BodyPublishers.noBody() : jsonBody(reason));
    return execute(builder);
  }

  /** Searches workitems (PS3.18 §11.9). Returns an empty list on HTTP 204. */
  public List<Attributes> searchWorkitems(UpsQuery query) throws IOException, InterruptedException {
    String queryString = query == null ? "" : query.toQueryString();
    String path = queryString.isEmpty() ? WORKITEMS : WORKITEMS + "?" + queryString;
    HttpResponse<byte[]> response = send(request(path).GET());
    return readDatasets(response.body());
  }

  /**
   * Subscribes to a workitem, or to the Global Worklist when {@code workitemUid} is {@code null}
   * (PS3.18 §11.10).
   *
   * @param deletionLock whether the SCP keeps the workitems until the subscriber is notified
   */
  public UpsResponse subscribe(String workitemUid, String aeTitle, boolean deletionLock)
      throws IOException, InterruptedException {
    String uid = workitemUid == null ? UID.UPSGlobalSubscriptionInstance : workitemUid;
    String path = subscriberPath(uid, aeTitle) + "?deletionlock=" + deletionLock;
    return execute(request(path).POST(HttpRequest.BodyPublishers.noBody()));
  }

  /** Subscribes to the workitems of the Global Worklist that match {@code filter}. */
  public UpsResponse subscribeFiltered(String aeTitle, Attributes filter, boolean deletionLock)
      throws IOException, InterruptedException {
    Objects.requireNonNull(filter, "Filter cannot be null");
    if (filter.isEmpty()) {
      throw new IllegalArgumentException("Filtered subscription requires matching keys");
    }
    String path =
        subscriberPath(UID.UPSFilteredGlobalSubscriptionInstance, aeTitle)
            + "?deletionlock="
            + deletionLock
            + "&filter="
            + encode(UpsQuery.toFilter(filter));
    return execute(request(path).POST(HttpRequest.BodyPublishers.noBody()));
  }

  /**
   * Unsubscribes from a workitem, or from the Global Worklist when {@code workitemUid} is {@code
   * null} (PS3.18 §11.11).
   */
  public UpsResponse unsubscribe(String workitemUid, String aeTitle)
      throws IOException, InterruptedException {
    String uid = workitemUid == null ? UID.UPSGlobalSubscriptionInstance : workitemUid;
    return execute(request(subscriberPath(uid, aeTitle)).DELETE());
  }

  /** Stops new global subscriptions while keeping the existing ones (PS3.18 §11.12). */
  public UpsResponse suspendGlobalSubscription(String aeTitle)
      throws IOException, InterruptedException {
    String path = subscriberPath(UID.UPSGlobalSubscriptionInstance, aeTitle) + "/suspend";
    return execute(request(path).POST(HttpRequest.BodyPublishers.noBody()));
  }

  /**
   * Opens the WebSocket channel receiving the event reports of the subscriptions of {@code aeTitle}
   * (PS3.18 §11.13). Each report is delivered as a dataset carrying the Event Type ID (0000,1002)
   * and the Affected SOP Instance UID (0000,1000).
   */
  public CompletableFuture<WebSocket> openNotificationChannel(
      String aeTitle, Consumer<Attributes> listener) {
    Objects.requireNonNull(listener, "Listener cannot be null");
    URI uri = toWebSocketUri(config.getBaseUrl() + "/subscribers/" + encodeAet(aeTitle));
    return HttpAuthorization.connect(
        config.getAuthorization(), this::webSocketBuilder, uri, new EventReportListener(listener));
  }

  /** Builds the optional dataset of a cancellation request (PS3.4 Table CC.2.2-1). */
  public static Attributes cancellationReason(
      String reason, Code reasonCode, String contactUri, String contactName) {
    Attributes attrs = new Attributes();
    if (reason != null) {
      attrs.setString(Tag.ReasonForCancellation, VR.LT, reason);
    }
    if (reasonCode != null) {
      attrs.newSequence(Tag.ProcedureStepDiscontinuationReasonCodeSequence, 1)
          .add(reasonCode.toItem());
    }
    if (contactUri != null) {
      attrs.setString(Tag.ContactURI, VR.UR, contactUri);
    }
    if (contactName != null) {
      attrs.setString(Tag.ContactDisplayName, VR.LO, contactName);
    }
    return attrs;
  }

  static byte[] writeDataset(Attributes attrs) {
    var out = new ByteArrayOutputStream();
    try (JsonGenerator gen = Json.createGenerator(out)) {
      // DICOM JSON Model is an array of datasets (PS3.18 §F.2)
      gen.writeStartArray();
      new JSONWriter(gen).write(attrs);
      gen.writeEnd();
    }
    return out.toByteArray();
  }

  static List<Attributes> readDatasets(byte[] body) {
    List<Attributes> result = new ArrayList<>();
    if (body == null || body.length == 0) {
      return result;
    }
    var reader = new JSONReader(Json.createParser(new ByteArrayInputStream(body)));
    if (startsWithArray(body)) {
      reader.readDatasets((fmi, dataset) -> result.add(dataset));
    } else {
      result.add(reader.readDataset(null));
    }
    return result;
  }

  private static boolean startsWithArray(byte[] body) {
    for (byte b : body) {
      if (!Character.isWhitespace(b)) {
        return b == '[';
      }
    }
    return false;
  }

  private static HttpClient createHttpClient(UpsConfig config) {
    return HttpClient.newBuilder()
        .followRedirects(HttpAuthorization.redirectPolicy(config.getAuthorization()))
        .version(config.getHttpVersion())
        .connectTimeout(config.getConnectTimeout())
        .build();
  }

  private static String workitemPath(String workitemUid) {
    return WORKITEMS + "/" + encode(Objects.requireNonNull(workitemUid, "Workitem UID is null"));
  }

  private static String subscriberPath(String workitemUid, String aeTitle) {
    return workitemPath(workitemUid) + "/subscribers/" + encodeAet(aeTitle);
  }

  private static String encodeAet(String aeTitle) {
    Objects.requireNonNull(aeTitle, "AE Title cannot be null");
    if (aeTitle.isBlank()) {
      throw new IllegalArgumentException("AE Title cannot be blank");
    }
    return encode(aeTitle);
  }

  private static URI toWebSocketUri(String url) {
    return URI.create(url.replaceFirst("^http", "ws"));
  }

  private static HttpRequest.BodyPublisher jsonBody(Attributes attrs) {
    return HttpRequest.BodyPublishers.ofByteArray(writeDataset(attrs));
  }

  private HttpRequest.Builder request(String path) {
    HttpRequest.Builder builder =
        HttpRequest.newBuilder()
            .uri(URI.create(config.getBaseUrl() + path))
            .timeout(config.getRequestTimeout())
            .header(CONTENT_TYPE, DICOM_JSON)
            .header("Accept", DICOM_JSON)
            .header("User-Agent", config.getUserAgent());
    config.getHeaders().forEach(builder::header);
    return builder;
  }

  private UpsResponse execute(HttpRequest.Builder builder)
      throws IOException, InterruptedException {
    HttpResponse<byte[]> response = send(builder);
    List<String> warnings = response.headers().allValues("Warning");
    warnings.forEach(w -> LOGGER.warn("UPS-RS {}: {}", response.request().uri(), w));
    Optional<String> location =
        response.headers().firstValue("Content-Location").or(() -> response.headers().firstValue("Location"));
    return new UpsResponse(response.statusCode(), location, warnings);
  }

  HttpResponse<byte[]> send(HttpRequest.Builder builder) throws IOException, InterruptedException {
    HttpRequest request = builder.build();
    HttpResponse<byte[]> response =
        HttpAuthorization.send(
            httpClient, config.getAuthorization(), request, HttpResponse.BodyHandlers.ofByteArray());
    LOGGER.debug("UPS-RS {} {} -> {}", request.method(), request.uri(), response.statusCode());
    if (response.statusCode() >= HttpURLConnection.HTTP_BAD_REQUEST) {
      throw new HttpException(
          "UPS-RS %s %s failed".formatted(request.method(), request.uri()),
          response.statusCode(),
          new String(response.body(), StandardCharsets.UTF_8),
          null);
    }
    return response;
  }

  private WebSocket.Builder webSocketBuilder() {
    WebSocket.Builder builder =
        httpClient.newWebSocketBuilder().connectTimeout(config.getConnectTimeout());
    builder.header("User-Agent", config.getUserAgent());
    config.getHeaders().forEach(builder::header);
    return builder;
  }

  /** Reassembles text frames and dispatches each event report as a dataset. */
  static final class EventReportListener implements WebSocket.Listener {
    private final Consumer<Attributes> listener;
    private final StringBuilder buffer = new StringBuilder();

    EventReportListener(Consumer<Attributes> listener) {
      this.listener = listener;
    }

    @Override
    public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
      buffer.append(data);
      if (last) {
        String message = buffer.toString();
        buffer.setLength(0);
        dispatch(message);
      }
      webSocket.request(1);
      return null;
    }

    @Override
    public void onError(WebSocket webSocket, Throwable error) {
      LOGGER.error("UPS-RS notification channel error", error);
    }

    void dispatch(String message) {
      try {
        readDatasets(message.getBytes(StandardCharsets.UTF_8)).forEach(listener);
      } catch (RuntimeException e) {
        LOGGER.error("Cannot process UPS event report: {}", message, e);
      }
    }
  }
}
