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

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Code;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.UID;
import org.dcm4che3.data.VR;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.weasis.dicom.web.RecordingHttpServer.CannedResponse;
import org.weasis.dicom.web.RecordingHttpServer.RecordedRequest;

@DisplayNameGeneration(ReplaceUnderscores.class)
class UpsRSTest {

  private static final String WORKITEM_UID = "1.2.3.4.5";
  private static final String TRANSACTION_UID = "2.25.123";

  private RecordingHttpServer server;
  private List<RecordedRequest> requests;
  private String baseUrl;

  @BeforeEach
  void startServer() throws IOException {
    server = new RecordingHttpServer();
    requests = server.requests();
    baseUrl = server.url("/dicomweb");
  }

  @AfterEach
  void stopServer() {
    server.close();
  }

  private UpsRS client() {
    return client(AuthorizationProvider.NONE);
  }

  private UpsRS client(AuthorizationProvider auth) {
    return new UpsRS(UpsConfig.builder().baseUrl(baseUrl).authorization(auth).build());
  }

  private UpsRS clientAs(String requesterAet, boolean requesterInPath) {
    return new UpsRS(
        UpsConfig.builder()
            .baseUrl(baseUrl)
            .requesterAet(requesterAet)
            .requesterInPath(requesterInPath)
            .build());
  }

  private static Attributes workitem() {
    Attributes attrs = new Attributes();
    attrs.setString(Tag.ProcedureStepLabel, VR.LO, "Segmentation");
    attrs.setString(Tag.PatientName, VR.PN, "Doe^John");
    return attrs;
  }

  private static Attributes readBody(RecordedRequest request) {
    List<Attributes> items = UpsRS.readDatasets(request.body());
    assertEquals(1, items.size());
    return items.get(0);
  }

  @Nested
  class Transactions {

    @Test
    void create_workitem_sets_mandatory_attributes_and_returns_location() throws Exception {
      server.enqueue(
          new CannedResponse(
              201,
              Map.of("Content-Location", baseUrl + "/workitems/" + WORKITEM_UID),
              new byte[0]));

      UpsResponse response = client().createWorkitem(workitem(), WORKITEM_UID);

      assertEquals(201, response.statusCode());
      assertEquals(WORKITEM_UID, response.workitemUid().orElseThrow());
      RecordedRequest request = requests.get(0);
      assertEquals("POST", request.method());
      assertEquals("/dicomweb/workitems?workitem=" + WORKITEM_UID, request.uri());
      assertEquals(UpsRS.DICOM_JSON, request.header("Content-type"));
      assertNull(request.header("Authorization"));
      Attributes body = readBody(request);
      assertEquals("SCHEDULED", body.getString(Tag.ProcedureStepState));
      assertTrue(body.containsValue(Tag.ScheduledProcedureStepStartDateTime));
      assertEquals("Doe^John", body.getString(Tag.PatientName));
    }

    @Test
    void retrieve_workitem_parses_dicom_json_array() throws Exception {
      server.enqueue(new CannedResponse(200, Map.of(), UpsRS.writeDataset(workitem())));

      Attributes attrs = client().retrieveWorkitem(WORKITEM_UID);

      assertEquals("Segmentation", attrs.getString(Tag.ProcedureStepLabel));
      assertEquals("GET", requests.get(0).method());
      assertEquals("/dicomweb/workitems/" + WORKITEM_UID, requests.get(0).uri());
    }

    @Test
    void update_workitem_sends_the_transaction_uid_in_the_query_and_the_data_set()
        throws Exception {
      client().updateWorkitem(WORKITEM_UID, TRANSACTION_UID, workitem());

      RecordedRequest request = requests.get(0);
      assertEquals("POST", request.method());
      assertEquals(
          "/dicomweb/workitems/" + WORKITEM_UID + "?TransactionUid=" + TRANSACTION_UID,
          request.uri());
      Attributes body = readBody(request);
      assertEquals(TRANSACTION_UID, body.getString(Tag.TransactionUID));
      assertEquals("Segmentation", body.getString(Tag.ProcedureStepLabel));
    }

    @Test
    void update_of_a_scheduled_workitem_carries_no_transaction_uid() throws Exception {
      client().updateWorkitem(WORKITEM_UID, null, workitem());

      RecordedRequest request = requests.get(0);
      assertEquals("/dicomweb/workitems/" + WORKITEM_UID, request.uri());
      assertFalse(readBody(request).contains(Tag.TransactionUID));
    }

    @Test
    void change_state_puts_transaction_and_state() throws Exception {
      client().changeState(WORKITEM_UID, TRANSACTION_UID, ProcedureStepState.IN_PROGRESS);

      RecordedRequest request = requests.get(0);
      assertEquals("PUT", request.method());
      assertEquals("/dicomweb/workitems/" + WORKITEM_UID + "/state", request.uri());
      Attributes body = readBody(request);
      assertEquals(TRANSACTION_UID, body.getString(Tag.TransactionUID));
      assertEquals("IN PROGRESS", body.getString(Tag.ProcedureStepState));
    }

    @Test
    void requester_is_a_query_parameter_by_default_and_a_path_segment_for_dcm4chee()
        throws Exception {
      clientAs("WEASIS", false)
          .changeState(WORKITEM_UID, TRANSACTION_UID, ProcedureStepState.COMPLETED);
      clientAs("WEASIS", false).requestCancellation(WORKITEM_UID, null);
      clientAs("WEASIS", true)
          .changeState(WORKITEM_UID, TRANSACTION_UID, ProcedureStepState.COMPLETED);
      clientAs("WEASIS", true).requestCancellation(WORKITEM_UID, null);

      String path = "/dicomweb/workitems/" + WORKITEM_UID;
      assertEquals(path + "/state?requester=WEASIS", requests.get(0).uri());
      assertEquals(path + "/cancelrequest?requester=WEASIS", requests.get(1).uri());
      assertEquals(path + "/state/WEASIS", requests.get(2).uri());
      assertEquals(path + "/cancelrequest/WEASIS", requests.get(3).uri());
      assertThrows(
          IllegalArgumentException.class,
          () -> UpsConfig.builder().baseUrl(baseUrl).requesterInPath(true).build());
    }

    @Test
    void subscriber_defaults_to_the_requester_ae_title() throws Exception {
      clientAs("WEASIS", false).subscribe(WORKITEM_UID, null, false);

      assertEquals(
          "/dicomweb/workitems/" + WORKITEM_UID + "/subscribers/WEASIS?deletionlock=false",
          requests.get(0).uri());
      assertThrows(
          IllegalArgumentException.class, () -> client().subscribe(WORKITEM_UID, null, false));
    }

    @Test
    void request_cancellation_sends_reason_and_reports_warnings() throws Exception {
      server.enqueue(
          new CannedResponse(202, Map.of("Warning", "299 SCP: already canceled"), new byte[0]));
      Attributes reason =
          UpsRS.cancellationReason(
              "Wrong patient",
              new Code("110514", "DCM", null, "Incorrect worklist entry"),
              null,
              "Dr Who");

      UpsResponse response = client().requestCancellation(WORKITEM_UID, reason);

      assertTrue(response.hasWarnings());
      assertEquals("/dicomweb/workitems/" + WORKITEM_UID + "/cancelrequest", requests.get(0).uri());
      Attributes body = readBody(requests.get(0));
      assertEquals("Wrong patient", body.getString(Tag.ReasonForCancellation));
      assertEquals(
          "110514",
          body.getNestedDataset(Tag.ProcedureStepDiscontinuationReasonCodeSequence)
              .getString(Tag.CodeValue));
    }

    @Test
    void search_returns_empty_list_on_no_content() throws Exception {
      server.enqueue(CannedResponse.of(204));

      List<Attributes> result =
          client()
              .searchWorkitems(
                  new UpsQuery()
                      .match("ProcedureStepState", "SCHEDULED")
                      .includeAllFields()
                      .limit(10));

      assertTrue(result.isEmpty());
      assertEquals(
          "/dicomweb/workitems?ProcedureStepState=SCHEDULED&includefield=all&limit=10",
          requests.get(0).uri());
    }

    @Test
    void subscribe_to_global_worklist_when_no_workitem() throws Exception {
      client().subscribe(null, "WEASIS", true);

      assertEquals("POST", requests.get(0).method());
      assertEquals(
          "/dicomweb/workitems/"
              + UID.UPSGlobalSubscriptionInstance
              + "/subscribers/WEASIS?deletionlock=true",
          requests.get(0).uri());
    }

    @Test
    void subscribe_filtered_encodes_matching_keys() throws Exception {
      Attributes filter = new Attributes();
      filter.setString(Tag.ProcedureStepState, VR.CS, "SCHEDULED");

      client().subscribeFiltered("WEASIS", filter, false);

      assertEquals(
          "/dicomweb/workitems/"
              + UID.UPSFilteredGlobalSubscriptionInstance
              + "/subscribers/WEASIS?deletionlock=false&filter=ProcedureStepState%3DSCHEDULED",
          requests.get(0).uri());
    }

    @Test
    void unsubscribe_and_suspend_use_global_instance() throws Exception {
      UpsRS ups = client();
      ups.unsubscribe(null, "WEASIS");
      ups.suspendGlobalSubscription("WEASIS");

      String global = "/dicomweb/workitems/" + UID.UPSGlobalSubscriptionInstance;
      assertEquals("DELETE", requests.get(0).method());
      assertEquals(global + "/subscribers/WEASIS", requests.get(0).uri());
      assertEquals(global + "/subscribers/WEASIS/suspend", requests.get(1).uri());
    }

    @Test
    void error_status_is_reported_as_http_exception() {
      server.enqueue(new CannedResponse(409, Map.of(), "Wrong state".getBytes()));

      HttpException ex =
          assertThrows(
              HttpException.class,
              () ->
                  client()
                      .changeState(WORKITEM_UID, TRANSACTION_UID, ProcedureStepState.COMPLETED));

      assertEquals(409, ex.getStatusCode());
      assertEquals("Wrong state", ex.getResponseBody().orElseThrow());
    }
  }

  @Nested
  class OAuth2 {

    @Test
    void bearer_token_is_sent_on_each_request() throws Exception {
      UpsRS ups = client(AuthorizationProvider.bearer(() -> "token1", () -> {}));
      ups.searchWorkitems(null);
      ups.unsubscribe(WORKITEM_UID, "WEASIS");

      assertEquals("Bearer token1", requests.get(1).header("Authorization"));

      assertEquals("Bearer token1", requests.get(0).header("Authorization"));
    }

    @Test
    void rejected_token_is_invalidated_and_request_replayed_once() throws Exception {
      AtomicInteger generation = new AtomicInteger(1);
      AtomicInteger resets = new AtomicInteger();
      AuthorizationProvider auth =
          AuthorizationProvider.bearer(
              () -> "token" + generation.get(),
              () -> {
                resets.incrementAndGet();
                generation.incrementAndGet();
              });
      server.enqueue(CannedResponse.of(401));
      server.enqueue(CannedResponse.of(201));

      UpsResponse response = client(auth).createWorkitem(workitem(), null);

      assertEquals(201, response.statusCode());
      assertEquals(1, resets.get());
      assertEquals(2, requests.size());
      assertEquals("Bearer token1", requests.get(0).header("Authorization"));
      assertEquals("Bearer token2", requests.get(1).header("Authorization"));
      // Replayed request carries the same payload
      assertArrayEquals(requests.get(0).body(), requests.get(1).body());
    }

    @Test
    void second_rejection_fails_without_further_retry() {
      AtomicInteger resets = new AtomicInteger();
      server.enqueue(CannedResponse.of(401));
      server.enqueue(CannedResponse.of(401));

      HttpException ex =
          assertThrows(
              HttpException.class,
              () ->
                  client(AuthorizationProvider.bearer(() -> "t", resets::incrementAndGet))
                      .retrieveWorkitem(WORKITEM_UID));

      assertEquals(401, ex.getStatusCode());
      assertEquals(1, resets.get());
      assertEquals(2, requests.size());
    }

    @Test
    void missing_token_fails_before_sending() {
      assertThrows(
          IOException.class,
          () -> client(AuthorizationProvider.bearer(() -> null, () -> {})).retrieveWorkitem("1.2"));
      assertTrue(requests.isEmpty());
    }

    @Test
    void static_authorization_header_conflicting_with_provider_is_rejected() {
      UpsConfig.Builder builder =
          UpsConfig.builder()
              .baseUrl(baseUrl)
              .header("authorization", "Bearer x")
              .authorization(AuthorizationProvider.bearer(() -> "y", () -> {}));
      assertThrows(IllegalArgumentException.class, builder::build);
    }
  }

  @Nested
  class Helpers {

    @Test
    void base_url_is_normalized() {
      UpsConfig config = UpsConfig.builder().baseUrl("https://pacs/dicomweb/workitems/").build();
      assertEquals("https://pacs/dicomweb", config.getBaseUrl());
    }

    @Test
    void query_flattens_sequences_and_empty_keys() {
      Attributes keys = new Attributes();
      keys.setString(Tag.PatientID, VR.LO, "P 1");
      keys.setNull(Tag.PatientName, VR.PN);
      Attributes item = new Attributes();
      item.setString(Tag.CodeValue, VR.SH, "CT01");
      keys.newSequence(Tag.ScheduledStationNameCodeSequence, 1).add(item);

      String query = new UpsQuery().match(keys).fuzzyMatching(true).offset(5).toQueryString();

      assertEquals(
          "PatientID=P%201&ScheduledStationNameCodeSequence.CodeValue=CT01"
              + "&includefield=PatientName&fuzzymatching=true&offset=5",
          query);
    }

    @Test
    void procedure_step_state_round_trips() {
      for (ProcedureStepState state : ProcedureStepState.values()) {
        assertEquals(state, ProcedureStepState.fromCode(state.code()));
      }
      assertThrows(IllegalArgumentException.class, () -> ProcedureStepState.fromCode("DONE"));
    }

    @Test
    void event_report_listener_dispatches_each_dataset() {
      Attributes event = new Attributes();
      event.setString(Tag.AffectedSOPInstanceUID, VR.UI, WORKITEM_UID);
      event.setInt(Tag.EventTypeID, VR.US, 1);
      event.setString(Tag.ProcedureStepState, VR.CS, "IN PROGRESS");
      List<Attributes> received = new ArrayList<>();

      var listener = new UpsRS.EventReportListener(received::add);
      listener.dispatch(new String(UpsRS.writeDataset(event)));
      listener.dispatch("not json");

      assertEquals(1, received.size());
      assertEquals(WORKITEM_UID, received.get(0).getString(Tag.AffectedSOPInstanceUID));
      assertEquals(1, received.get(0).getInt(Tag.EventTypeID, 0));
    }
  }
}
