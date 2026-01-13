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
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

@DisplayNameGeneration(ReplaceUnderscores.class)
class AbstractQueryResultTest {

  // Test data factories
  private WadoParameters createTestWadoParameters() {
    return WadoParameters.wadoUri("http://test.example.com/wado", true);
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
        createTestPatient("PAT003", "HOSPITAL_B"),
        createTestPatient("PAT004", null));
  }

  private Study createMockStudyWithAccession(String studyUid, String accessionNumber) {
    var study = mock(Study.class);
    when(study.getStudyInstanceUID()).thenReturn(studyUid);
    when(study.getAccessionNumber()).thenReturn(accessionNumber);
    when(study.isEmpty()).thenReturn(false);
    when(study.getEntrySet()).thenReturn(new HashSet<>());
    return study;
  }

  private Study createMockEmptyStudy(String studyUid) {
    var study = mock(Study.class);
    when(study.getStudyInstanceUID()).thenReturn(studyUid);
    when(study.isEmpty()).thenReturn(true);
    when(study.getEntrySet()).thenReturn(new HashSet<>());
    return study;
  }

  private Patient createPatientWithStudies(String patientId, String issuer, Study... studies) {
    var patient = createTestPatient(patientId, issuer);
    for (var study : studies) {
      patient.addStudy(study);
    }
    return patient;
  }

  private Series createMockSeries(String seriesUid, boolean isEmpty) {
    var series = mock(Series.class);
    when(series.getSeriesInstanceUID()).thenReturn(seriesUid);
    when(series.isEmpty()).thenReturn(isEmpty);
    return series;
  }

  // Concrete implementation for testing AbstractQueryResult
  private static class TestQueryResult extends AbstractQueryResult {
    private final WadoParameters wadoParameters;

    public TestQueryResult() {
      this.wadoParameters = WadoParameters.wadoUri("http://test.com/wado", false);
    }

    public TestQueryResult(Collection<Patient> patients) {
      super(patients);
      this.wadoParameters = WadoParameters.wadoUri("http://test.com/wado", false);
    }

    public TestQueryResult(Collection<Patient> patients, WadoParameters wadoParams) {
      super(patients);
      this.wadoParameters = wadoParams;
    }

    @Override
    public WadoParameters getWadoParameters() {
      return wadoParameters;
    }
  }

  @Nested
  class Constructor_And_Initialization_Tests {

    @Test
    void default_constructor_creates_empty_query_result() {
      var result = new TestQueryResult();

      assertAll(
          () -> assertEquals(0, result.getPatientCount()),
          () -> assertFalse(result.hasPatients()),
          () -> assertTrue(result.getPatients().isEmpty()),
          () -> assertNull(result.getViewerMessage()));
    }

    @Test
    void constructor_with_null_patients_creates_empty_result() {
      var result = new TestQueryResult(null);

      assertAll(
          () -> assertEquals(0, result.getPatientCount()),
          () -> assertFalse(result.hasPatients()),
          () -> assertTrue(result.getPatients().isEmpty()));
    }

    @Test
    void constructor_with_empty_patients_creates_empty_result() {
      var result = new TestQueryResult(Collections.emptyList());

      assertAll(
          () -> assertEquals(0, result.getPatientCount()),
          () -> assertFalse(result.hasPatients()),
          () -> assertTrue(result.getPatients().isEmpty()));
    }

    @Test
    void constructor_with_patients_initializes_correctly() {
      var patients = createTestPatients();
      var result = new TestQueryResult(patients);

      assertAll(
          () -> assertEquals(4, result.getPatientCount()),
          () -> assertTrue(result.hasPatients()),
          () -> assertEquals(4, result.getPatients().size()));
    }

    @Test
    void constructor_ignores_null_patients_in_collection() {
      var patientsWithNulls = new ArrayList<Patient>();
      patientsWithNulls.add(createTestPatient("PAT001", null));
      patientsWithNulls.add(null);
      patientsWithNulls.add(createTestPatient("PAT002", null));
      patientsWithNulls.add(null);

      var result = new TestQueryResult(patientsWithNulls);

      assertEquals(2, result.getPatientCount());
    }
  }

  @Nested
  class Patient_Management_Tests {

    @Test
    void addPatient_adds_patient_to_map() {
      var result = new TestQueryResult();
      var patient = createTestPatient("NEW_PAT", "ISSUER");

      result.addPatient(patient);

      assertAll(
          () -> assertEquals(1, result.getPatientCount()),
          () -> assertTrue(result.hasPatients()),
          () -> assertEquals(patient, result.getPatient("NEW_PAT", "ISSUER")));
    }

    @Test
    void addPatient_ignores_null_patient() {
      var result = new TestQueryResult();
      int initialCount = result.getPatientCount();

      result.addPatient(null);

      assertEquals(initialCount, result.getPatientCount());
    }

    @Test
    void addPatient_replaces_existing_patient_with_same_pseudo_uid() {
      var result = new TestQueryResult();
      var patient1 = createTestPatient("PAT001", null);
      var patient2 = createTestPatient("PAT001", null);
      patient2.setPatientName("Updated^Patient^Name");

      result.addPatient(patient1);
      result.addPatient(patient2);

      assertAll(
          () -> assertEquals(1, result.getPatientCount()),
          () ->
              assertEquals(
                  "Updated^Patient^Name", result.getPatient("PAT001", null).getPatientName()));
    }

    @Test
    void getPatient_retrieves_patient_by_id_without_issuer() {
      var patients = createTestPatients();
      var result = new TestQueryResult(patients);

      var retrievedPatient = result.getPatient("PAT001", null);

      assertAll(
          () -> assertNotNull(retrievedPatient),
          () -> assertEquals("PAT001", retrievedPatient.getPatientID()),
          () -> assertNull(retrievedPatient.getIssuerOfPatientID()));
    }

    @Test
    void getPatient_retrieves_patient_by_id_with_issuer() {
      var patients = createTestPatients();
      var result = new TestQueryResult(patients);

      var retrievedPatient = result.getPatient("PAT002", "HOSPITAL_A");

      assertAll(
          () -> assertNotNull(retrievedPatient),
          () -> assertEquals("PAT002", retrievedPatient.getPatientID()),
          () -> assertEquals("HOSPITAL_A", retrievedPatient.getIssuerOfPatientID()));
    }

    @Test
    void getPatient_returns_null_for_unknown_patient() {
      var patients = createTestPatients();
      var result = new TestQueryResult(patients);

      assertAll(
          () -> assertNull(result.getPatient("UNKNOWN", null)),
          () -> assertNull(result.getPatient("PAT001", "WRONG_ISSUER")),
          () -> assertNull(result.getPatient(null, "ISSUER")));
    }

    @Test
    void removePatient_removes_and_returns_patient() {
      var patients = createTestPatients();
      var result = new TestQueryResult(patients);

      var removedPatient = result.removePatient("PAT001", null);

      assertAll(
          () -> assertNotNull(removedPatient),
          () -> assertEquals("PAT001", removedPatient.getPatientID()),
          () -> assertEquals(3, result.getPatientCount()),
          () -> assertNull(result.getPatient("PAT001", null)));
    }

    @Test
    void removePatient_returns_null_for_unknown_patient() {
      var patients = createTestPatients();
      var result = new TestQueryResult(patients);
      int initialCount = result.getPatientCount();

      var removedPatient = result.removePatient("UNKNOWN", null);

      assertAll(
          () -> assertNull(removedPatient),
          () -> assertEquals(initialCount, result.getPatientCount()));
    }

    @Test
    void removePatient_handles_null_patient_id() {
      var patients = createTestPatients();
      var result = new TestQueryResult(patients);

      var removedPatient = result.removePatient(null, "ISSUER");

      assertNull(removedPatient);
    }
  }

  @Nested
  class Patient_Filtering_Tests {

    @Test
    void removePatientId_with_issuer_removes_by_pseudo_uid() {
      var patients = createTestPatients();
      var result = new TestQueryResult(patients);

      // Using pseudo UIDs (patientId + issuer)
      result.removePatientId(List.of("PAT002HOSPITAL_A", "PAT003HOSPITAL_B"), true);

      assertAll(
          () -> assertEquals(2, result.getPatientCount()),
          () -> assertNotNull(result.getPatient("PAT001", null)),
          () -> assertNotNull(result.getPatient("PAT004", null)),
          () -> assertNull(result.getPatient("PAT002", "HOSPITAL_A")),
          () -> assertNull(result.getPatient("PAT003", "HOSPITAL_B")));
    }

    @Test
    void removePatientId_without_issuer_removes_by_patient_id_match() {
      var patients = createTestPatients();
      var result = new TestQueryResult(patients);

      result.removePatientId(List.of("PAT001", "PAT004"), false);

      assertAll(
          () -> assertEquals(2, result.getPatientCount()),
          () -> assertNull(result.getPatient("PAT001", null)),
          () -> assertNull(result.getPatient("PAT004", null)),
          () -> assertNotNull(result.getPatient("PAT002", "HOSPITAL_A")),
          () -> assertNotNull(result.getPatient("PAT003", "HOSPITAL_B")));
    }

    @Test
    void removePatientId_handles_null_and_empty_lists() {
      var patients = createTestPatients();
      var result = new TestQueryResult(patients);
      int initialCount = result.getPatientCount();

      assertAll(
          () -> {
            result.removePatientId((List<String>) null, false);
            assertEquals(initialCount, result.getPatientCount());
          },
          () -> {
            result.removePatientId(Collections.emptyList(), false);
            assertEquals(initialCount, result.getPatientCount());
          });
    }

    @Test
    void removePatientId_collection_convenience_method() {
      var patients = createTestPatients();
      var result = new TestQueryResult(patients);

      result.removePatientId(Set.of("PAT001", "PAT002"));

      assertAll(
          () -> assertEquals(2, result.getPatientCount()),
          () -> assertNull(result.getPatient("PAT001", null)),
          () -> assertNotNull(result.getPatient("PAT003", "HOSPITAL_B")));
    }
  }

  @Nested
  class Study_Filtering_Tests {

    private Study createStudyWithAccession(String studyUid, String accessionNumber) {
      var study = new Study(studyUid);
      study.setAccessionNumber(accessionNumber);
      study.setStudyDescription("Test Study " + studyUid);
      study.setStudyDate("20240101");
      study.setStudyTime("120000");

      // Add a series with a SOP instance to make the study non-empty
      var series = new Series("SERIES_" + studyUid);
      series.setSeriesDescription("Test Series");
      series.setSeriesNumber("1");
      series.setModality("CT");

      var sopInstance = new SopInstance("SOP_" + studyUid, 1);
      series.addSopInstance(sopInstance);

      study.addSeries(series);
      return study;
    }

    private Study createEmptyStudy(String studyUid) {
      var study = new Study(studyUid);
      study.setAccessionNumber("ACC_EMPTY_" + studyUid);
      study.setStudyDescription("Empty Study " + studyUid);
      return study; // No series added, so it's empty
    }

    private Patient createPatientWithStudies(String patientId, String issuer, Study... studies) {
      var patient = createTestPatient(patientId, issuer);
      for (var study : studies) {
        patient.addStudy(study);
      }
      return patient;
    }

    @Test
    void removeStudyUid_removes_studies_by_uid() {
      var study1 = createStudyWithAccession("STUDY001", "ACC001");
      var study2 = createStudyWithAccession("STUDY002", "ACC002");
      var study3 = createStudyWithAccession("STUDY003", "ACC003");

      var patient1 = createPatientWithStudies("PAT001", null, study1, study2);
      var patient2 = createPatientWithStudies("PAT002", null, study3);

      var result = new TestQueryResult(List.of(patient1, patient2));

      // Verify initial state
      assertEquals(2, patient1.getStudies().size());
      assertEquals(1, patient2.getStudies().size());
      assertNotNull(patient1.getStudy("STUDY001"));
      assertNotNull(patient1.getStudy("STUDY002"));
      assertNotNull(patient2.getStudy("STUDY003"));

      result.removeStudyUid(List.of("STUDY001", "STUDY003"));

      // Verify studies were removed from all patients
      assertAll(
          () -> assertNull(patient1.getStudy("STUDY001")),
          () -> assertNotNull(patient1.getStudy("STUDY002")), // Should remain
          () -> assertNull(patient2.getStudy("STUDY003")),
          () -> assertEquals(1, patient1.getStudies().size()),
          () -> assertEquals(0, patient2.getStudies().size()));
    }

    @Test
    void removeStudyUid_handles_null_and_empty_lists() {
      var study1 = createStudyWithAccession("STUDY001", "ACC001");
      var study2 = createStudyWithAccession("STUDY002", "ACC002");
      var patient = createPatientWithStudies("PAT001", null, study1, study2);
      var result = new TestQueryResult(List.of(patient));

      int initialStudyCount = patient.getStudies().size();

      assertAll(
          () -> {
            result.removeStudyUid((List<String>) null);
            assertEquals(initialStudyCount, patient.getStudies().size());
            assertNotNull(patient.getStudy("STUDY001"));
            assertNotNull(patient.getStudy("STUDY002"));
          },
          () -> {
            result.removeStudyUid(Collections.emptyList());
            assertEquals(initialStudyCount, patient.getStudies().size());
            assertNotNull(patient.getStudy("STUDY001"));
            assertNotNull(patient.getStudy("STUDY002"));
          });
    }

    @Test
    void removeStudyUid_collection_convenience_method() {
      var study1 = createStudyWithAccession("STUDY001", "ACC001");
      var study2 = createStudyWithAccession("STUDY002", "ACC002");
      var patient = createPatientWithStudies("PAT001", null, study1, study2);
      var result = new TestQueryResult(List.of(patient));

      result.removeStudyUid(Set.of("STUDY001"));

      assertAll(
          () -> assertNull(patient.getStudy("STUDY001")),
          () -> assertNotNull(patient.getStudy("STUDY002")),
          () -> assertEquals(1, patient.getStudies().size()));
    }

    @Test
    void removeStudyUid_calls_removeItemsWithoutElements() {
      var study1 = createStudyWithAccession("STUDY001", "ACC001");
      var emptyStudy = createEmptyStudy("EMPTY_STUDY");

      var patient1 = createPatientWithStudies("PAT001", null, study1);
      var patient2 = createPatientWithStudies("PAT002", null, emptyStudy);

      var result = new TestQueryResult(List.of(patient1, patient2));

      // Initially both patients should be present
      assertEquals(2, result.getPatientCount());

      result.removeStudyUid(List.of("STUDY001", "EMPTY_STUDY"));

      // After removing studies and calling removeItemsWithoutElements,
      // patients with no studies should be cleaned up
      assertAll(
          () -> assertNull(patient1.getStudy("STUDY001")),
          () -> assertNull(patient2.getStudy("EMPTY_STUDY")),
          () -> assertTrue(patient1.getStudies().isEmpty()),
          () -> assertTrue(patient2.getStudies().isEmpty()));
    }

    @Test
    void removeStudyUid_handles_non_existent_studies() {
      var study1 = createStudyWithAccession("STUDY001", "ACC001");
      var patient = createPatientWithStudies("PAT001", null, study1);
      var result = new TestQueryResult(List.of(patient));

      int initialStudyCount = patient.getStudies().size();

      // Try to remove non-existent study
      result.removeStudyUid(List.of("NON_EXISTENT_STUDY"));

      // Verify no studies were affected
      assertAll(
          () -> assertEquals(initialStudyCount, patient.getStudies().size()),
          () -> assertNotNull(patient.getStudy("STUDY001")));
    }

    @Test
    void removeStudyUid_handles_studies_with_multiple_series() {
      var study = new Study("MULTI_SERIES_STUDY");
      study.setAccessionNumber("ACC_MULTI");
      study.setStudyDescription("Multi Series Study");

      // Add multiple series to the study
      for (int i = 1; i <= 3; i++) {
        var series = new Series("SERIES_00" + i);
        series.setSeriesDescription("Series " + i);
        series.setSeriesNumber(String.valueOf(i));
        series.setModality("CT");

        // Add SOP instances to make series non-empty
        for (int j = 1; j <= 2; j++) {
          var sopInstance = new SopInstance("SOP_" + i + "_" + j, j);
          series.addSopInstance(sopInstance);
        }

        study.addSeries(series);
      }

      var patient = createPatientWithStudies("PAT001", null, study);
      var result = new TestQueryResult(List.of(patient));

      // Verify study has multiple series
      assertEquals(3, patient.getStudy("MULTI_SERIES_STUDY").getSeries().size());

      result.removeStudyUid(List.of("MULTI_SERIES_STUDY"));

      // Study should be completely removed
      assertAll(
          () -> assertNull(patient.getStudy("MULTI_SERIES_STUDY")),
          () -> assertTrue(patient.getStudies().isEmpty()));
    }

    @Test
    void removeStudyUid_preserves_studies_not_in_removal_list() {
      var study1 = createStudyWithAccession("STUDY001", "ACC001");
      var study2 = createStudyWithAccession("STUDY002", "ACC002");
      var study3 = createStudyWithAccession("STUDY003", "ACC003");

      var patient = createPatientWithStudies("PAT001", null, study1, study2, study3);
      var result = new TestQueryResult(List.of(patient));

      result.removeStudyUid(List.of("STUDY002"));

      assertAll(
          () -> assertNotNull(patient.getStudy("STUDY001")),
          () -> assertNull(patient.getStudy("STUDY002")),
          () -> assertNotNull(patient.getStudy("STUDY003")),
          () -> assertEquals(2, patient.getStudies().size()));
    }

    @Test
    void removeStudyUid_works_across_multiple_patients() {
      var study1 = createStudyWithAccession("STUDY001", "ACC001");
      var study2 = createStudyWithAccession("STUDY002", "ACC002");
      var study3 = createStudyWithAccession("STUDY001", "ACC001"); // Same UID, different patient
      var study4 = createStudyWithAccession("STUDY004", "ACC004");

      var patient1 = createPatientWithStudies("PAT001", null, study1, study2);
      var patient2 = createPatientWithStudies("PAT002", null, study3, study4);

      var result = new TestQueryResult(List.of(patient1, patient2));

      result.removeStudyUid(List.of("STUDY001"));

      // STUDY001 should be removed from both patients
      assertAll(
          () -> assertNull(patient1.getStudy("STUDY001")),
          () -> assertNotNull(patient1.getStudy("STUDY002")),
          () -> assertNull(patient2.getStudy("STUDY001")),
          () -> assertNotNull(patient2.getStudy("STUDY004")),
          () -> assertEquals(1, patient1.getStudies().size()),
          () -> assertEquals(1, patient2.getStudies().size()));
    }

    @Test
    void removeStudyUid_handles_empty_patients() {
      var emptyPatient = createTestPatient("EMPTY_PAT", null);
      var normalPatient =
          createPatientWithStudies(
              "NORMAL_PAT", null, createStudyWithAccession("STUDY001", "ACC001"));

      var result = new TestQueryResult(List.of(emptyPatient, normalPatient));

      // This should not cause any errors
      assertDoesNotThrow(() -> result.removeStudyUid(List.of("STUDY001", "STUDY002")));

      // Verify the removal worked correctly
      assertAll(
          () -> assertTrue(emptyPatient.getStudies().isEmpty()), // Still empty
          () -> assertNull(normalPatient.getStudy("STUDY001")) // Study removed
          );
    }
  }

  @Nested
  class Accession_Number_Filtering_Tests {

    @Test
    void removeAccessionNumber_handles_null_and_empty_lists() {
      var study = createMockStudyWithAccession("STUDY001", "ACC001");
      var patient = createPatientWithStudies("PAT001", null, study);
      var result = new TestQueryResult(List.of(patient));

      assertAll(
          () -> assertDoesNotThrow(() -> result.removeAccessionNumber((List<String>) null)),
          () -> assertDoesNotThrow(() -> result.removeAccessionNumber(Collections.emptyList())));
    }

    @Test
    void removeAccessionNumber_collection_convenience_method() {
      var study = createMockStudyWithAccession("STUDY001", "ACC001");
      var patient = createPatientWithStudies("PAT001", null, study);
      var result = new TestQueryResult(List.of(patient));

      assertDoesNotThrow(() -> result.removeAccessionNumber(Set.of("ACC001")));
    }
  }

  @Nested
  class Series_Filtering_Tests {

    private Study createStudyWithSeries(
        String studyUid, String accessionNumber, String... seriesUids) {
      var study = new Study(studyUid);
      study.setAccessionNumber(accessionNumber);

      for (String seriesUid : seriesUids) {
        var series = new Series(seriesUid);
        series.setSeriesDescription("Test Series " + seriesUid);
        series.setSeriesNumber("1");
        series.setModality("CT");

        // Add a SOP instance to make the series non-empty
        var sopInstance = new SopInstance("1.2.3.4.5." + seriesUid, 1);
        series.addSopInstance(sopInstance);

        study.addSeries(series);
      }

      return study;
    }

    private Patient createPatientWithStudiesAndSeries(String patientId, String issuer) {
      var patient = createTestPatient(patientId, issuer);

      // Add study with multiple series
      var study1 =
          createStudyWithSeries("STUDY001", "ACC001", "SERIES001", "SERIES002", "SERIES003");
      patient.addStudy(study1);

      // Add another study with different series
      var study2 = createStudyWithSeries("STUDY002", "ACC002", "SERIES004", "SERIES005");
      patient.addStudy(study2);

      return patient;
    }

    @Test
    void removeSeriesUid_removes_series_from_studies() {
      var patient1 = createPatientWithStudiesAndSeries("PAT001", null);
      var patient2 = createPatientWithStudiesAndSeries("PAT002", null);

      var result = new TestQueryResult(List.of(patient1, patient2));

      // Verify initial state
      assertEquals(2, result.getPatientCount());
      assertNotNull(patient1.getStudy("STUDY001").getSeries("SERIES001"));
      assertNotNull(patient1.getStudy("STUDY001").getSeries("SERIES002"));
      assertNotNull(patient2.getStudy("STUDY001").getSeries("SERIES001"));

      result.removeSeriesUid(List.of("SERIES001", "SERIES004"));

      // Verify series were removed from all studies across all patients
      assertAll(
          () -> assertNull(patient1.getStudy("STUDY001").getSeries("SERIES001")),
          () ->
              assertNotNull(patient1.getStudy("STUDY001").getSeries("SERIES002")), // Should remain
          () ->
              assertNotNull(patient1.getStudy("STUDY001").getSeries("SERIES003")), // Should remain
          () -> assertNull(patient1.getStudy("STUDY002").getSeries("SERIES004")),
          () ->
              assertNotNull(patient1.getStudy("STUDY002").getSeries("SERIES005")), // Should remain
          () -> assertNull(patient2.getStudy("STUDY001").getSeries("SERIES001")),
          () -> assertNull(patient2.getStudy("STUDY002").getSeries("SERIES004")));
    }

    @Test
    void removeSeriesUid_handles_null_and_empty_lists() {
      var patient = createPatientWithStudiesAndSeries("PAT001", null);
      var result = new TestQueryResult(List.of(patient));

      // Count initial series
      int initialSeriesCount =
          patient.getStudy("STUDY001").getSeries().size()
              + patient.getStudy("STUDY002").getSeries().size();

      assertAll(
          () -> {
            result.removeSeriesUid((List<String>) null);
            // Verify no series were removed
            int currentCount =
                patient.getStudy("STUDY001").getSeries().size()
                    + patient.getStudy("STUDY002").getSeries().size();
            assertEquals(initialSeriesCount, currentCount);
          },
          () -> {
            result.removeSeriesUid(Collections.emptyList());
            // Verify no series were removed
            int currentCount =
                patient.getStudy("STUDY001").getSeries().size()
                    + patient.getStudy("STUDY002").getSeries().size();
            assertEquals(initialSeriesCount, currentCount);
          });
    }

    @Test
    void removeSeriesUid_collection_convenience_method() {
      var patient = createPatientWithStudiesAndSeries("PAT001", null);
      var result = new TestQueryResult(List.of(patient));

      // Verify series exists before removal
      assertNotNull(patient.getStudy("STUDY001").getSeries("SERIES001"));

      result.removeSeriesUid(Set.of("SERIES001"));

      // Verify series was removed
      assertNull(patient.getStudy("STUDY001").getSeries("SERIES001"));
    }

    @Test
    void removeSeriesUid_removes_empty_studies_after_series_removal() {
      var patient = createTestPatient("PAT001", null);

      // Create a study with only one series
      var study = createStudyWithSeries("STUDY_SINGLE", "ACC_SINGLE", "SERIES_ONLY");
      patient.addStudy(study);

      var result = new TestQueryResult(List.of(patient));

      // Verify study exists
      assertNotNull(patient.getStudy("STUDY_SINGLE"));
      assertEquals(1, patient.getStudy("STUDY_SINGLE").getSeries().size());

      result.removeSeriesUid(List.of("SERIES_ONLY"));
      assertTrue(patient.isEmpty());
    }

    @Test
    void removeSeriesUid_handles_non_existent_series() {
      var patient = createPatientWithStudiesAndSeries("PAT001", null);
      var result = new TestQueryResult(List.of(patient));

      int initialSeriesCount =
          patient.getStudy("STUDY001").getSeries().size()
              + patient.getStudy("STUDY002").getSeries().size();

      // Try to remove non-existent series
      result.removeSeriesUid(List.of("NON_EXISTENT_SERIES"));

      // Verify no series were affected
      int currentCount =
          patient.getStudy("STUDY001").getSeries().size()
              + patient.getStudy("STUDY002").getSeries().size();
      assertEquals(initialSeriesCount, currentCount);
    }

    @Test
    void removeSeriesUid_works_with_empty_studies() {
      var patient = createTestPatient("PAT001", null);

      // Create an empty study
      var emptyStudy = new Study("EMPTY_STUDY");
      emptyStudy.setAccessionNumber("ACC_EMPTY");
      patient.addStudy(emptyStudy);

      var result = new TestQueryResult(List.of(patient));

      // This should not cause any errors
      assertDoesNotThrow(() -> result.removeSeriesUid(List.of("SERIES001")));
      assertTrue(patient.isEmpty());
    }

    @Test
    void removeSeriesUid_handles_series_with_multiple_sop_instances() {
      var patient = createTestPatient("PAT001", null);
      var study = new Study("STUDY_MULTI_SOP");
      study.setAccessionNumber("ACC_MULTI");

      // Create series with multiple SOP instances
      var series = new Series("SERIES_MULTI");
      series.setSeriesDescription("Multi SOP Series");

      for (int i = 1; i <= 5; i++) {
        var sopInstance = new SopInstance("1.2.3.4.5.SOP" + i, i);
        series.addSopInstance(sopInstance);
      }

      study.addSeries(series);
      patient.addStudy(study);

      var result = new TestQueryResult(List.of(patient));

      // Verify series has multiple SOP instances
      assertEquals(
          5,
          patient.getStudy("STUDY_MULTI_SOP").getSeries("SERIES_MULTI").getSopInstances().size());

      result.removeSeriesUid(List.of("SERIES_MULTI"));

      // Series should be completely removed
      assertTrue(patient.isEmpty());
    }
  }

  @Nested
  class Empty_Elements_Cleanup_Tests {

    @Test
    void removeItemsWithoutElements_removes_empty_studies_and_series() {
      var emptyStudy = createMockEmptyStudy("EMPTY_STUDY");
      var nonEmptyStudy = createMockStudyWithAccession("NON_EMPTY_STUDY", "ACC001");

      var emptySeriesEntry =
          new AbstractMap.SimpleEntry<>("EMPTY_SERIES", createMockSeries("EMPTY_SERIES", true));
      var nonEmptySeriesEntry =
          new AbstractMap.SimpleEntry<>(
              "NON_EMPTY_SERIES", createMockSeries("NON_EMPTY_SERIES", false));

      var emptyStudyEntries = new HashSet<Map.Entry<String, Series>>();
      var nonEmptyStudyEntries = new HashSet<Map.Entry<String, Series>>();
      nonEmptyStudyEntries.add(nonEmptySeriesEntry);

      when(emptyStudy.getEntrySet()).thenReturn(emptyStudyEntries);
      when(nonEmptyStudy.getEntrySet()).thenReturn(nonEmptyStudyEntries);

      var patient = mock(Patient.class);
      when(patient.getPseudoPatientUID()).thenReturn("PAT001");
      when(patient.getPatientID()).thenReturn("PAT001");
      when(patient.isEmpty()).thenReturn(false);

      var patientEntries = new HashSet<Map.Entry<String, Study>>();
      patientEntries.add(new AbstractMap.SimpleEntry<>("EMPTY_STUDY", emptyStudy));
      patientEntries.add(new AbstractMap.SimpleEntry<>("NON_EMPTY_STUDY", nonEmptyStudy));
      when(patient.getEntrySet()).thenReturn(patientEntries);

      var result = new TestQueryResult(List.of(patient));

      result.removeItemsWithoutElements();

      // Verify the cleanup process was initiated
      verify(patient).getEntrySet();
      verify(emptyStudy).getEntrySet();
      verify(nonEmptyStudy).getEntrySet();
    }
  }

  @Nested
  class Viewer_Message_Tests {

    @Test
    void getViewerMessage_returns_null_initially() {
      var result = new TestQueryResult();
      assertNull(result.getViewerMessage());
    }

    @Test
    void setViewerMessage_stores_message() {
      var result = new TestQueryResult();
      var message = ViewerMessage.info("Test", "Test message");

      result.setViewerMessage(message);

      assertEquals(message, result.getViewerMessage());
    }

    @Test
    void setViewerMessage_can_set_null() {
      var result = new TestQueryResult();
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
      var result = new TestQueryResult();

      result.setViewerMessage(message);

      assertEquals(message, result.getViewerMessage());
    }
  }

  @Nested
  class Query_Result_Interface_Tests {

    @Test
    void getPatients_returns_unmodifiable_map() {
      var patients = createTestPatients();
      var result = new TestQueryResult(patients);

      var patientMap = result.getPatients();

      assertAll(
          () -> assertEquals(4, patientMap.size()),
          () ->
              assertThrows(
                  UnsupportedOperationException.class,
                  () -> patientMap.put("new-key", createTestPatient("NEW", null))),
          () -> assertThrows(UnsupportedOperationException.class, patientMap::clear));
    }

    @Test
    void hasPatients_returns_correct_value() {
      var emptyResult = new TestQueryResult();
      var populatedResult = new TestQueryResult(createTestPatients());

      assertAll(
          () -> assertFalse(emptyResult.hasPatients()),
          () -> assertTrue(populatedResult.hasPatients()));
    }

    @Test
    void getPatientCount_returns_correct_count() {
      var emptyResult = new TestQueryResult();
      var populatedResult = new TestQueryResult(createTestPatients());

      assertAll(
          () -> assertEquals(0, emptyResult.getPatientCount()),
          () -> assertEquals(4, populatedResult.getPatientCount()));
    }

    @Test
    void hasPatients_handles_null_patient_map_gracefully() {
      var result =
          new TestQueryResult() {
            @Override
            public Map<String, Patient> getPatients() {
              return null;
            }
          };

      assertFalse(result.hasPatients());
    }

    @Test
    void getPatientCount_handles_null_patient_map_gracefully() {
      var result =
          new TestQueryResult() {
            @Override
            public Map<String, Patient> getPatients() {
              return null;
            }
          };

      assertEquals(0, result.getPatientCount());
    }
  }

  @Nested
  class Concurrency_And_Thread_Safety_Tests {

    @Test
    void patient_map_is_thread_safe() throws Exception {
      var result = new TestQueryResult();
      var executorService = Executors.newFixedThreadPool(10);

      try {
        var futures =
            IntStream.range(0, 100)
                .mapToObj(
                    i ->
                        executorService.submit(
                            () -> {
                              var patient = createTestPatient("PAT" + i, "ISSUER" + i);
                              result.addPatient(patient);
                              return patient;
                            }))
                .toList();

        // Wait for all tasks to complete
        for (Future<Patient> future : futures) {
          future.get();
        }

        assertEquals(100, result.getPatientCount());
      } finally {
        executorService.shutdown();
      }
    }

    @Test
    void concurrent_patient_operations_are_safe() throws Exception {
      var patients =
          IntStream.range(0, 50).mapToObj(i -> createTestPatient("PAT" + i, null)).toList();

      var result = new TestQueryResult(patients);
      var executorService = Executors.newFixedThreadPool(5);

      try {
        var futures = new ArrayList<Future<?>>();

        // Add patients concurrently
        futures.addAll(
            IntStream.range(50, 75)
                .mapToObj(
                    i ->
                        executorService.submit(
                            () -> {
                              result.addPatient(createTestPatient("NEW_PAT" + i, null));
                            }))
                .toList());

        // Remove patients concurrently
        futures.addAll(
            IntStream.range(0, 25)
                .mapToObj(
                    i ->
                        executorService.submit(
                            () -> {
                              result.removePatient("PAT" + i, null);
                            }))
                .toList());

        // Wait for all operations to complete
        for (Future<?> future : futures) {
          future.get();
        }

        // Verify final state is consistent
        assertTrue(result.getPatientCount() >= 25); // At least the remaining + new patients

      } finally {
        executorService.shutdown();
      }
    }
  }

  @Nested
  class Edge_Cases_And_Boundary_Tests {

    @Test
    void patient_operations_handle_edge_cases() {
      var result = new TestQueryResult();

      assertAll(
          () -> assertDoesNotThrow(() -> result.addPatient(null)),
          () -> assertNull(result.getPatient(null, null)),
          () -> assertNull(result.getPatient("", "")),
          () -> assertNull(result.removePatient(null, null)),
          () -> assertNull(result.removePatient("", "")));
    }

    @Test
    void filtering_operations_handle_large_datasets() {
      var patients =
          IntStream.range(0, 1000)
              .mapToObj(
                  i -> createTestPatient("PAT" + String.format("%04d", i), "ISSUER" + (i % 10)))
              .toList();

      var result = new TestQueryResult(patients);

      var idsToRemove =
          IntStream.range(0, 500).mapToObj(i -> "PAT" + String.format("%04d", i)).toList();

      assertTimeout(
          java.time.Duration.ofSeconds(5),
          () -> {
            result.removePatientId(idsToRemove, false);
            assertEquals(500, result.getPatientCount());
          });
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 10, 100, 1000})
    void supports_various_patient_counts(int patientCount) {
      var patients =
          IntStream.range(0, patientCount)
              .mapToObj(i -> createTestPatient("PAT" + i, null))
              .toList();

      var result = new TestQueryResult(patients);

      assertAll(
          () -> assertEquals(patientCount, result.getPatientCount()),
          () -> assertEquals(patientCount > 0, result.hasPatients()));
    }

    @Test
    void patient_key_building_handles_special_cases() {
      var result = new TestQueryResult();

      // Test patients with special characters in IDs and issuers
      var specialPatients =
          List.of(
              createTestPatient("PAT-001", "HOSPITAL_A"),
              createTestPatient("PAT.002", "HOSPITAL.B"),
              createTestPatient("PAT_003", "HOSPITAL-C"),
              createTestPatient("PAT 004", null) // Space in ID
              );

      specialPatients.forEach(result::addPatient);

      assertAll(
          () -> assertEquals(4, result.getPatientCount()),
          () -> assertNotNull(result.getPatient("PAT-001", "HOSPITAL_A")),
          () -> assertNotNull(result.getPatient("PAT.002", "HOSPITAL.B")),
          () -> assertNotNull(result.getPatient("PAT_003", "HOSPITAL-C")),
          () -> assertNotNull(result.getPatient("PAT 004", null)));
    }
  }

  @Nested
  class Integration_And_Real_World_Scenarios_Tests {

    @Test
    void complete_workflow_scenario() {
      // Create a realistic scenario with multiple patients, studies, and filtering operations
      var patients = createTestPatients();
      var result = new TestQueryResult(patients, createTestWadoParameters());

      // Set a viewer message
      result.setViewerMessage(ViewerMessage.info("Query Complete", "Found 4 patients"));

      // Perform filtering operations
      result.removePatientId(List.of("PAT001"), false);

      // Verify final state
      assertAll(
          () -> assertEquals(3, result.getPatientCount()),
          () -> assertNull(result.getPatient("PAT001", null)),
          () -> assertNotNull(result.getViewerMessage()),
          () -> assertEquals("Query Complete", result.getViewerMessage().title()),
          () -> assertNotNull(result.getWadoParameters()));
    }

    @Test
    void mixed_issuer_scenarios() {
      var result = new TestQueryResult();

      // Add patients with and without issuers
      result.addPatient(createTestPatient("PAT001", null));
      result.addPatient(createTestPatient("PAT001", "HOSPITAL_A")); // Same ID, different issuer
      result.addPatient(createTestPatient("PAT002", "HOSPITAL_A"));
      result.addPatient(createTestPatient("PAT002", "HOSPITAL_B")); // Same ID, different issuer

      assertAll(
          () -> assertEquals(4, result.getPatientCount()),
          () -> assertNotNull(result.getPatient("PAT001", null)),
          () -> assertNotNull(result.getPatient("PAT001", "HOSPITAL_A")),
          () ->
              assertNotEquals(
                  result.getPatient("PAT001", null), result.getPatient("PAT001", "HOSPITAL_A")),
          () ->
              assertNotEquals(
                  result.getPatient("PAT002", "HOSPITAL_A"),
                  result.getPatient("PAT002", "HOSPITAL_B")));
    }

    @Test
    void pseudo_uid_generation_consistency() {
      var result = new TestQueryResult();
      var patient1 = createTestPatient("PAT001", "ISSUER");
      var patient2 = createTestPatient("PAT001", "ISSUER");

      result.addPatient(patient1);
      String pseudoUid1 = patient1.getPseudoPatientUID();

      result.addPatient(patient2);
      String pseudoUid2 = patient2.getPseudoPatientUID();

      assertAll(
          () -> assertEquals(pseudoUid1, pseudoUid2),
          () -> assertEquals(1, result.getPatientCount()), // Should replace, not add
          () -> assertSame(patient2, result.getPatients().values().iterator().next()));
    }
  }
}
