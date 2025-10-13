/*
 * Copyright (c) 2024 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.mf;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.*;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EmptySource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

@DisplayNameGeneration(ReplaceUnderscores.class)
class DefaultQueryResultTest {

  // Test data factories
  private WadoParameters createTestWadoParameters() {
    return WadoParameters.wadoUri("http://test.example.com/wado", true);
  }

  private WadoParameters createTestWadoRSParameters() {
    return WadoParameters.wadoRs("https://test.example.com/dicomweb")
        .withArchiveID("test-archive")
        .withWebLogin("testuser:testpass")
        .build();
  }

  private Patient createTestPatient(String patientId, String issuer) {
    var patient = new Patient(patientId, issuer);
    patient.setPatientName("Test^Patient^" + patientId);
    patient.setPatientSex("M");
    patient.setPatientBirthDate("19800101");
    return patient;
  }

  private List<Patient> createTestPatients() {
    return List.of(
        createTestPatient("PAT001", null),
        createTestPatient("PAT002", "HOSPITAL_A"),
        createTestPatient("PAT003", "HOSPITAL_B"));
  }

  private Study createTestStudy(String studyUid, String accessionNumber) {
    var study = mock(Study.class);
    when(study.getStudyInstanceUID()).thenReturn(studyUid);
    when(study.getAccessionNumber()).thenReturn(accessionNumber);
    when(study.isEmpty()).thenReturn(false);
    when(study.getEntrySet()).thenReturn(new HashSet<>());
    return study;
  }

  @Nested
  class Constructor_Tests {

    @Test
    void constructor_with_patients_and_wado_parameters_creates_result() {
      var patients = createTestPatients();
      var wadoParams = createTestWadoParameters();

      var result = new DefaultQueryResult(patients, wadoParams);

      assertAll(
          () -> assertEquals(wadoParams, result.getWadoParameters()),
          () -> assertEquals(3, result.getPatientCount()),
          () -> assertTrue(result.hasPatients()),
          () -> assertNull(result.getViewerMessage()));
    }

    @Test
    void constructor_with_null_patients_creates_empty_result() {
      var wadoParams = createTestWadoParameters();

      var result = new DefaultQueryResult((Collection<Patient>) null, wadoParams);

      assertAll(
          () -> assertEquals(wadoParams, result.getWadoParameters()),
          () -> assertEquals(0, result.getPatientCount()),
          () -> assertFalse(result.hasPatients()));
    }

    @Test
    void constructor_with_empty_patients_creates_empty_result() {
      var wadoParams = createTestWadoParameters();

      var result = new DefaultQueryResult(Collections.emptyList(), wadoParams);

      assertAll(
          () -> assertEquals(wadoParams, result.getWadoParameters()),
          () -> assertEquals(0, result.getPatientCount()),
          () -> assertFalse(result.hasPatients()));
    }

    @Test
    void constructor_with_only_wado_parameters_creates_empty_result() {
      var wadoParams = createTestWadoParameters();

      var result = new DefaultQueryResult(wadoParams);

      assertAll(
          () -> assertEquals(wadoParams, result.getWadoParameters()),
          () -> assertEquals(0, result.getPatientCount()),
          () -> assertFalse(result.hasPatients()));
    }

    @Test
    void constructor_throws_exception_for_null_wado_parameters() {
      var patients = createTestPatients();

      var exception =
          assertThrows(NullPointerException.class, () -> new DefaultQueryResult(patients, null));

      assertEquals("WADO parameters cannot be null", exception.getMessage());
    }

    @Test
    void constructor_throws_exception_for_null_wado_parameters_in_empty_constructor() {
      var exception = assertThrows(NullPointerException.class, () -> new DefaultQueryResult(null));

      assertEquals("WADO parameters cannot be null", exception.getMessage());
    }
  }

  @Nested
  class Wado_Parameters_Tests {

    @Test
    void getWadoParameters_returns_exact_instance_passed_in_constructor() {
      var wadoParams = createTestWadoParameters();
      var result = new DefaultQueryResult(wadoParams);

      assertSame(wadoParams, result.getWadoParameters());
    }

    @Test
    void supports_wado_uri_parameters() {
      var wadoParams = WadoParameters.wadoUri("http://pacs.example.com/wado", false);
      var result = new DefaultQueryResult(wadoParams);

      var retrievedParams = result.getWadoParameters();
      assertAll(
          () -> assertFalse(retrievedParams.isWadoRS()),
          () -> assertEquals("WADO-URI", retrievedParams.getProtocolName()),
          () -> assertFalse(retrievedParams.isRequireOnlySOPInstanceUID()));
    }

    @Test
    void supports_wado_rs_parameters() {
      var wadoParams = createTestWadoRSParameters();
      var result = new DefaultQueryResult(wadoParams);

      var retrievedParams = result.getWadoParameters();
      assertAll(
          () -> assertTrue(retrievedParams.isWadoRS()),
          () -> assertEquals("WADO-RS", retrievedParams.getProtocolName()),
          () -> assertEquals("test-archive", retrievedParams.getArchiveID()));
    }
  }

  @Nested
  class Patient_Management_Tests {

    @Test
    void addPatient_adds_patient_to_result() {
      var result = new DefaultQueryResult(createTestWadoParameters());
      var patient = createTestPatient("NEW_PAT", null);

      result.addPatient(patient);

      assertAll(
          () -> assertEquals(1, result.getPatientCount()),
          () -> assertTrue(result.hasPatients()),
          () -> assertTrue(result.getPatients().containsValue(patient)));
    }

    @Test
    void addPatient_ignores_null_patient() {
      var result = new DefaultQueryResult(createTestWadoParameters());

      result.addPatient(null);

      assertAll(
          () -> assertEquals(0, result.getPatientCount()), () -> assertFalse(result.hasPatients()));
    }

    @Test
    void getPatient_retrieves_patient_by_id_without_issuer() {
      var patients = createTestPatients();
      var result = new DefaultQueryResult(patients, createTestWadoParameters());

      var retrievedPatient = result.getPatient("PAT001", null);

      assertAll(
          () -> assertNotNull(retrievedPatient),
          () -> assertEquals("PAT001", retrievedPatient.getPatientID()),
          () -> assertNull(retrievedPatient.getIssuerOfPatientID()));
    }

    @Test
    void getPatient_retrieves_patient_by_id_with_issuer() {
      var patients = createTestPatients();
      var result = new DefaultQueryResult(patients, createTestWadoParameters());

      var retrievedPatient = result.getPatient("PAT002", "HOSPITAL_A");

      assertAll(
          () -> assertNotNull(retrievedPatient),
          () -> assertEquals("PAT002", retrievedPatient.getPatientID()),
          () -> assertEquals("HOSPITAL_A", retrievedPatient.getIssuerOfPatientID()));
    }

    @Test
    void getPatient_returns_null_for_unknown_patient() {
      var patients = createTestPatients();
      var result = new DefaultQueryResult(patients, createTestWadoParameters());

      var retrievedPatient = result.getPatient("UNKNOWN", null);

      assertNull(retrievedPatient);
    }

    @Test
    void removePatient_removes_and_returns_patient() {
      var patients = createTestPatients();
      var result = new DefaultQueryResult(patients, createTestWadoParameters());

      var removedPatient = result.removePatient("PAT001", null);

      assertAll(
          () -> assertNotNull(removedPatient),
          () -> assertEquals("PAT001", removedPatient.getPatientID()),
          () -> assertEquals(2, result.getPatientCount()),
          () -> assertNull(result.getPatient("PAT001", null)));
    }

    @Test
    void removePatient_returns_null_for_unknown_patient() {
      var patients = createTestPatients();
      var result = new DefaultQueryResult(patients, createTestWadoParameters());

      var removedPatient = result.removePatient("UNKNOWN", null);

      assertAll(() -> assertNull(removedPatient), () -> assertEquals(3, result.getPatientCount()));
    }
  }

  @Nested
  class Patient_Filtering_Tests {

    @Test
    void removePatientId_removes_patients_by_id_list() {
      var patients = createTestPatients();
      var result = new DefaultQueryResult(patients, createTestWadoParameters());

      result.removePatientId(List.of("PAT001", "PAT003"));

      assertAll(
          () -> assertEquals(1, result.getPatientCount()),
          () -> assertNotNull(result.getPatient("PAT002", "HOSPITAL_A")),
          () -> assertNull(result.getPatient("PAT001", null)),
          () -> assertNull(result.getPatient("PAT003", "HOSPITAL_B")));
    }

    @Test
    void removePatientId_with_collection_converts_to_list() {
      var patients = createTestPatients();
      var result = new DefaultQueryResult(patients, createTestWadoParameters());

      result.removePatientId(Set.of("PAT001", "PAT002"));

      assertAll(
          () -> assertEquals(1, result.getPatientCount()),
          () -> assertNotNull(result.getPatient("PAT003", "HOSPITAL_B")));
    }

    @ParameterizedTest
    @EmptySource
    void removePatientId_handles_null_and_empty_lists(List<String> patientIds) {
      var patients = createTestPatients();
      var result = new DefaultQueryResult(patients, createTestWadoParameters());
      int originalCount = result.getPatientCount();

      result.removePatientId(patientIds);

      assertEquals(originalCount, result.getPatientCount());
    }
  }

  @Nested
  class Viewer_Message_Tests {

    @Test
    void setViewerMessage_stores_message() {
      var result = new DefaultQueryResult(createTestWadoParameters());
      var message = ViewerMessage.info("Test", "Test message");

      result.setViewerMessage(message);

      assertEquals(message, result.getViewerMessage());
    }

    @Test
    void setViewerMessage_can_be_null() {
      var result = new DefaultQueryResult(createTestWadoParameters());
      var message = ViewerMessage.warn("Warning", "Warning message");

      result.setViewerMessage(message);
      result.setViewerMessage(null);

      assertNull(result.getViewerMessage());
    }

    static Stream<Arguments> viewerMessageTypes() {
      return Stream.of(
          Arguments.of(ViewerMessage.info("Info", "Information message")),
          Arguments.of(ViewerMessage.warn("Warning", "Warning message")),
          Arguments.of(ViewerMessage.error("Error", "Error message")));
    }

    @ParameterizedTest
    @MethodSource("viewerMessageTypes")
    void setViewerMessage_accepts_all_message_types(ViewerMessage message) {
      var result = new DefaultQueryResult(createTestWadoParameters());

      result.setViewerMessage(message);

      assertEquals(message, result.getViewerMessage());
    }
  }

  @Nested
  class String_Representation_Tests {

    @Test
    void toString_includes_patient_count_and_wado_parameters() {
      var patients = createTestPatients();
      var wadoParams = createTestWadoParameters();
      var result = new DefaultQueryResult(patients, wadoParams);

      String stringRep = result.toString();

      assertAll(
          () -> assertTrue(stringRep.contains("DefaultQueryResult")),
          () -> assertTrue(stringRep.contains("patientCount=3")),
          () -> assertTrue(stringRep.contains("wadoParameters=")),
          () -> assertTrue(stringRep.contains("hasViewerMessage=false")));
    }

    @Test
    void toString_shows_viewer_message_presence() {
      var result = new DefaultQueryResult(createTestWadoParameters());
      var message = ViewerMessage.info("Test", "Test message");
      result.setViewerMessage(message);

      String stringRep = result.toString();

      assertTrue(stringRep.contains("hasViewerMessage=true"));
    }

    @Test
    void toString_shows_empty_result_correctly() {
      var result = new DefaultQueryResult(createTestWadoParameters());

      String stringRep = result.toString();

      assertAll(
          () -> assertTrue(stringRep.contains("patientCount=0")),
          () -> assertTrue(stringRep.contains("hasViewerMessage=false")));
    }
  }

  @Nested
  class Equality_And_HashCode_Tests {

    @Test
    void equals_returns_true_for_identical_results() {
      var patients = createTestPatients();
      var wadoParams = createTestWadoParameters();
      var result1 = new DefaultQueryResult(patients, wadoParams);
      var result2 = new DefaultQueryResult(patients, wadoParams);

      assertEquals(result1, result2);
    }

    @Test
    void equals_returns_true_for_same_instance() {
      var result = new DefaultQueryResult(createTestWadoParameters());
      assertEquals(result, result);
    }

    @Test
    void equals_returns_false_for_different_patients() {
      var wadoParams = createTestWadoParameters();
      var result1 = new DefaultQueryResult(createTestPatients(), wadoParams);
      var result2 = new DefaultQueryResult(List.of(createTestPatient("OTHER", null)), wadoParams);

      assertNotEquals(result1, result2);
    }

    @Test
    void equals_returns_false_for_different_wado_parameters() {
      var patients = createTestPatients();
      var wadoParams1 = WadoParameters.wadoUri("http://server1.com/wado", true);
      var wadoParams2 = WadoParameters.wadoUri("http://server2.com/wado", true);
      var result1 = new DefaultQueryResult(patients, wadoParams1);
      var result2 = new DefaultQueryResult(patients, wadoParams2);

      assertNotEquals(result1, result2);
    }

    @Test
    void equals_returns_false_for_different_viewer_messages() {
      var patients = createTestPatients();
      var wadoParams = createTestWadoParameters();
      var result1 = new DefaultQueryResult(patients, wadoParams);
      var result2 = new DefaultQueryResult(patients, wadoParams);

      result1.setViewerMessage(ViewerMessage.info("Test", "Message"));
      result2.setViewerMessage(ViewerMessage.warn("Test", "Different message"));

      assertNotEquals(result1, result2);
    }

    @Test
    void equals_returns_false_for_null() {
      var result = new DefaultQueryResult(createTestWadoParameters());
      assertNotEquals(result, null);
    }

    @Test
    void equals_returns_false_for_different_object_type() {
      var result = new DefaultQueryResult(createTestWadoParameters());
      assertNotEquals(result, "not a DefaultQueryResult");
    }

    @Test
    void hashCode_is_consistent_for_equal_objects() {
      var patients = createTestPatients();
      var wadoParams = createTestWadoParameters();
      var result1 = new DefaultQueryResult(patients, wadoParams);
      var result2 = new DefaultQueryResult(patients, wadoParams);

      assertEquals(result1.hashCode(), result2.hashCode());
    }

    @Test
    void hashCode_is_different_for_different_objects() {
      var wadoParams1 = WadoParameters.wadoUri("http://server1.com/wado", true);
      var wadoParams2 = WadoParameters.wadoUri("http://server2.com/wado", true);
      var result1 = new DefaultQueryResult(wadoParams1);
      var result2 = new DefaultQueryResult(wadoParams2);

      assertNotEquals(result1.hashCode(), result2.hashCode());
    }

    @Test
    void hashCode_is_consistent_across_multiple_calls() {
      var result = new DefaultQueryResult(createTestWadoParameters());
      int firstHash = result.hashCode();
      int secondHash = result.hashCode();

      assertEquals(firstHash, secondHash);
    }

    @Test
    void equals_considers_viewer_message_in_comparison() {
      var wadoParams = createTestWadoParameters();
      var result1 = new DefaultQueryResult(wadoParams);
      var result2 = new DefaultQueryResult(wadoParams);
      var message = ViewerMessage.info("Test", "Message");

      result1.setViewerMessage(message);
      result2.setViewerMessage(message);

      assertEquals(result1, result2);
    }
  }

  @Nested
  class Inherited_Functionality_Tests {

    @Test
    void getPatients_returns_unmodifiable_map() {
      var patients = createTestPatients();
      var result = new DefaultQueryResult(patients, createTestWadoParameters());

      var patientMap = result.getPatients();

      assertAll(
          () -> assertEquals(3, patientMap.size()),
          () ->
              assertThrows(
                  UnsupportedOperationException.class,
                  () -> patientMap.put("new-key", createTestPatient("NEW", null))),
          () -> assertThrows(UnsupportedOperationException.class, patientMap::clear));
    }

    @Test
    void hasPatients_returns_correct_value() {
      var emptyResult = new DefaultQueryResult(createTestWadoParameters());
      var populatedResult =
          new DefaultQueryResult(createTestPatients(), createTestWadoParameters());

      assertAll(
          () -> assertFalse(emptyResult.hasPatients()),
          () -> assertTrue(populatedResult.hasPatients()));
    }

    @Test
    void getPatientCount_returns_correct_count() {
      var emptyResult = new DefaultQueryResult(createTestWadoParameters());
      var populatedResult =
          new DefaultQueryResult(createTestPatients(), createTestWadoParameters());

      assertAll(
          () -> assertEquals(0, emptyResult.getPatientCount()),
          () -> assertEquals(3, populatedResult.getPatientCount()));
    }

    @Test
    void removeItemsWithoutElements_removes_empty_patients() {
      var result = new DefaultQueryResult(createTestWadoParameters());
      var emptyPatient = mock(Patient.class);
      when(emptyPatient.getPseudoPatientUID()).thenReturn("empty-patient");
      when(emptyPatient.isEmpty()).thenReturn(true);

      result.addPatient(emptyPatient);
      result.removeItemsWithoutElements();

      assertEquals(0, result.getPatientCount());
    }
  }

  @Nested
  class Edge_Cases_And_Scenarios_Tests {

    @Test
    void works_with_different_wado_parameter_configurations() {
      var wadoUriParams = WadoParameters.wadoUri("http://pacs.example.com/wado", true);
      var wadoRsParams =
          WadoParameters.wadoRs("https://pacs.example.com/dicomweb")
              .withArchiveID("archive1")
              .build();

      var result1 = new DefaultQueryResult(wadoUriParams);
      var result2 = new DefaultQueryResult(wadoRsParams);

      assertAll(
          () -> assertFalse(result1.getWadoParameters().isWadoRS()),
          () -> assertTrue(result2.getWadoParameters().isWadoRS()),
          () -> assertEquals("archive1", result2.getWadoParameters().getArchiveID()));
    }

    @Test
    void handles_large_patient_collections() {
      var patients = new ArrayList<Patient>();
      for (int i = 0; i < 1000; i++) {
        patients.add(createTestPatient("PAT" + String.format("%04d", i), "ISSUER" + (i % 10)));
      }

      var result = new DefaultQueryResult(patients, createTestWadoParameters());

      assertEquals(1000, result.getPatientCount());
    }

    @Test
    void thread_safety_with_concurrent_patient_operations() {
      var result = new DefaultQueryResult(createTestWadoParameters());

      // This tests the underlying ConcurrentHashMap usage
      assertDoesNotThrow(
          () -> {
            for (int i = 0; i < 100; i++) {
              result.addPatient(createTestPatient("PAT" + i, null));
            }
          });

      assertEquals(100, result.getPatientCount());
    }

    @ParameterizedTest
    @ValueSource(
        strings = {
          "http://pacs.example.com/wado",
          "https://secure.pacs.com/dicomweb",
          "http://localhost:8080/wado"
        })
    void supports_various_wado_url_formats(String wadoUrl) {
      var wadoParams = WadoParameters.wadoUri(wadoUrl, false);

      assertDoesNotThrow(() -> new DefaultQueryResult(wadoParams));
    }

    @Test
    void maintains_patient_order_in_collection() {
      var orderedPatients =
          List.of(
              createTestPatient("PAT_C", null),
              createTestPatient("PAT_A", null),
              createTestPatient("PAT_B", null));

      var result = new DefaultQueryResult(orderedPatients, createTestWadoParameters());

      // While the internal map doesn't guarantee order, all patients should be present
      var patientIds = result.getPatients().values().stream().map(Patient::getPatientID).toList();

      assertAll(
          () -> assertTrue(patientIds.contains("PAT_A")),
          () -> assertTrue(patientIds.contains("PAT_B")),
          () -> assertTrue(patientIds.contains("PAT_C")));
    }
  }
}
