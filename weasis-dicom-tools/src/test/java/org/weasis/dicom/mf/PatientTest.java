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

import java.io.IOException;
import java.io.StringWriter;
import java.util.Collection;
import java.util.Map.Entry;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;

@DisplayNameGeneration(ReplaceUnderscores.class)
class PatientTest {

  private static final String PATIENT_ID = "12345";
  private static final String ISSUER_ID = "HOSPITAL_A";
  private static final String PATIENT_NAME = "Doe^John";
  private static final String BIRTH_DATE = "19800101";
  private static final String BIRTH_TIME = "120000";

  private Patient patient;

  @BeforeEach
  void setUp() {
    patient = new Patient(PATIENT_ID, ISSUER_ID);
  }

  @Nested
  class Constructor_Tests {

    @Test
    void constructor_with_valid_patient_id_creates_patient() {
      var newPatient = new Patient("123", null);

      assertEquals("123", newPatient.getPatientID());
      assertNull(newPatient.getIssuerOfPatientID());
      assertEquals("", newPatient.getPatientName());
      assertTrue(newPatient.isEmpty());
    }

    @Test
    void constructor_with_patient_id_and_issuer_creates_patient() {
      assertEquals(PATIENT_ID, patient.getPatientID());
      assertEquals(ISSUER_ID, patient.getIssuerOfPatientID());
      assertEquals("", patient.getPatientName());
      assertTrue(patient.isEmpty());
    }

    @Test
    void constructor_with_null_patient_id_throws_exception() {
      assertThrows(NullPointerException.class, () -> new Patient(null, ISSUER_ID));
    }
  }

  @Nested
  class Pseudo_Patient_UID_Tests {

    @Test
    void getPseudoPatientUID_with_issuer_returns_combined_id() {
      assertEquals(PATIENT_ID + ISSUER_ID, patient.getPseudoPatientUID());
    }

    @Test
    void getPseudoPatientUID_without_issuer_returns_patient_id() {
      var patientWithoutIssuer = new Patient(PATIENT_ID, null);
      assertEquals(PATIENT_ID, patientWithoutIssuer.getPseudoPatientUID());
    }
  }

  @Nested
  class Patient_Name_Tests {

    @Test
    void setPatientName_with_valid_name_sets_name() {
      patient.setPatientName(PATIENT_NAME);
      assertEquals(PATIENT_NAME, patient.getPatientName());
    }

    @Test
    void setPatientName_with_null_sets_empty_string() {
      patient.setPatientName(null);
      assertEquals("", patient.getPatientName());
    }

    @Test
    void setPatientName_with_empty_string_keeps_empty_string() {
      patient.setPatientName("");
      assertEquals("", patient.getPatientName());
    }
  }

  @Nested
  class Patient_Sex_Tests {

    @ParameterizedTest
    @CsvSource({
      "M, M",
      "Male, M",
      "MALE, M",
      "m, M",
      "male, M",
      "F, F",
      "Female, F",
      "FEMALE, F",
      "f, F",
      "female, F",
      "O, O",
      "Other, O",
      "Unknown, O",
      "X, O",
      "'', O"
    })
    void setPatientSex_normalizes_values_correctly(String input, String expected) {
      patient.setPatientSex(input);
      assertEquals(expected, patient.getPatientSex());
    }

    @Test
    void setPatientSex_with_null_sets_null() {
      patient.setPatientSex(null);
      assertNull(patient.getPatientSex());
    }
  }

  @Nested
  class Birth_Date_And_Time_Tests {

    @Test
    void setPatientBirthDate_sets_value() {
      patient.setPatientBirthDate(BIRTH_DATE);
      assertEquals(BIRTH_DATE, patient.getPatientBirthDate());
    }

    @Test
    void setPatientBirthTime_sets_value() {
      patient.setPatientBirthTime(BIRTH_TIME);
      assertEquals(BIRTH_TIME, patient.getPatientBirthTime());
    }

    @ParameterizedTest
    @NullAndEmptySource
    void setPatientBirthDate_with_null_or_empty_sets_value(String value) {
      patient.setPatientBirthDate(value);
      assertEquals(value, patient.getPatientBirthDate());
    }
  }

  @Nested
  class Study_Management_Tests {

    private Study study1;
    private Study study2;

    @BeforeEach
    void setUp() {
      study1 = new Study("1.2.3.4.5");
      Series series1 = new Series("1.2.3.4.7");
      series1.addSopInstance(new SopInstance("1.2.3.4.7.1", 1));
      study1.addSeries(series1);
      study2 = new Study("1.2.3.4.6");
    }

    @Test
    void addStudy_with_valid_study_adds_study() {
      patient.addStudy(study1);

      assertEquals(study1, patient.getStudy("1.2.3.4.5"));
      assertFalse(patient.isEmpty());
    }

    @Test
    void addStudy_with_null_study_ignores_addition() {
      patient.addStudy(null);
      assertTrue(patient.isEmpty());
    }

    @Test
    void getStudy_with_existing_uid_returns_study() {
      patient.addStudy(study1);
      assertEquals(study1, patient.getStudy("1.2.3.4.5"));
    }

    @Test
    void getStudy_with_non_existing_uid_returns_null() {
      assertNull(patient.getStudy("non.existing.uid"));
    }

    @Test
    void removeStudy_with_existing_uid_removes_and_returns_study() {
      patient.addStudy(study1);

      assertEquals(study1, patient.removeStudy("1.2.3.4.5"));
      assertNull(patient.getStudy("1.2.3.4.5"));
    }

    @Test
    void removeStudy_with_non_existing_uid_returns_null() {
      assertNull(patient.removeStudy("non.existing.uid"));
    }

    @Test
    void getStudies_returns_all_studies() {
      patient.addStudy(study1);
      patient.addStudy(study2);

      Collection<Study> studies = patient.getStudies();
      assertEquals(2, studies.size());
      assertTrue(studies.contains(study1));
      assertTrue(studies.contains(study2));
    }

    @Test
    void getEntrySet_returns_study_entry_set() {
      patient.addStudy(study1);
      patient.addStudy(study2);

      Set<Entry<String, Study>> entrySet = patient.getEntrySet();
      assertEquals(2, entrySet.size());
    }

    @Test
    void isEmpty_with_empty_studies_returns_true() {
      patient.addStudy(study2); // Empty study
      assertTrue(patient.isEmpty());
    }

    @Test
    void isEmpty_with_non_empty_study_returns_false() {
      var series = new Series("1.2.3.4.7");
      var sopInstance = new SopInstance("1.2.3.4.8", 1);
      series.addSopInstance(sopInstance);
      study1.addSeries(series);

      patient.addStudy(study1);
      assertFalse(patient.isEmpty());
    }
  }

  @Nested
  class XML_Serialization_Tests {

    @Test
    void toXml_with_complete_patient_generates_valid_xml() throws IOException {
      setupCompletePatient();

      var writer = new StringWriter();
      patient.toXml(writer);
      String xml = writer.toString();

      assertAll(
          () -> assertTrue(xml.contains("<Patient ")),
          () -> assertTrue(xml.contains("PatientID=\"" + PATIENT_ID + "\"")),
          () -> assertTrue(xml.contains("IssuerOfPatientID=\"" + ISSUER_ID + "\"")),
          () -> assertTrue(xml.contains("PatientName=\"" + PATIENT_NAME + "\"")),
          () -> assertTrue(xml.contains("PatientBirthDate=\"" + BIRTH_DATE + "\"")),
          () -> assertTrue(xml.contains("PatientSex=\"M\"")),
          () -> assertTrue(xml.contains("</Patient>")));
    }

    @Test
    void toXml_includes_studies_in_sorted_order() throws IOException {
      setupPatientWithStudies();

      var writer = new StringWriter();
      patient.toXml(writer);
      String xml = writer.toString();

      assertTrue(xml.contains("<Study "));
      assertTrue(xml.contains("StudyInstanceUID=\"1.2.3.4.5\""));
    }

    private void setupCompletePatient() {
      patient.setPatientName(PATIENT_NAME);
      patient.setPatientBirthDate(BIRTH_DATE);
      patient.setPatientBirthTime(BIRTH_TIME);
      patient.setPatientSex("Male");
    }

    private void setupPatientWithStudies() {
      patient.setPatientName(PATIENT_NAME);
      var study = new Study("1.2.3.4.5");
      study.setStudyDescription("Test Study");
      patient.addStudy(study);
    }
  }

  @Nested
  class Comparison_Tests {

    @Test
    void compareTo_compares_by_patient_name() {
      var patient1 = new Patient("1", null);
      patient1.setPatientName("Alice");

      var patient2 = new Patient("2", null);
      patient2.setPatientName("Bob");

      assertTrue(patient1.compareTo(patient2) < 0);
      assertTrue(patient2.compareTo(patient1) > 0);
      assertEquals(0, patient1.compareTo(patient1));
    }
  }

  @Nested
  class Equality_Tests {

    @Test
    void equals_with_same_patient_id_and_issuer_returns_true() {
      var patient1 = new Patient(PATIENT_ID, ISSUER_ID);
      var patient2 = new Patient(PATIENT_ID, ISSUER_ID);

      assertEquals(patient1, patient2);
      assertEquals(patient1.hashCode(), patient2.hashCode());
    }

    @Test
    void equals_with_different_patient_id_returns_false() {
      var patient1 = new Patient("123", ISSUER_ID);
      var patient2 = new Patient("456", ISSUER_ID);

      assertNotEquals(patient1, patient2);
    }

    @Test
    void equals_with_different_issuer_returns_false() {
      var patient1 = new Patient(PATIENT_ID, "HOSPITAL_A");
      var patient2 = new Patient(PATIENT_ID, "HOSPITAL_B");

      assertNotEquals(patient1, patient2);
    }

    @Test
    void equals_with_null_issuer_handles_correctly() {
      var patient1 = new Patient(PATIENT_ID, null);
      var patient2 = new Patient(PATIENT_ID, null);
      var patient3 = new Patient(PATIENT_ID, ISSUER_ID);

      assertEquals(patient1, patient2);
      assertNotEquals(patient1, patient3);
      assertNotEquals(patient3, patient1);
    }

    @Test
    void equals_with_self_returns_true() {
      assertEquals(patient, patient);
    }

    @Test
    void equals_with_null_returns_false() {
      assertNotEquals(patient, null);
    }

    @Test
    void equals_with_different_class_returns_false() {
      assertNotEquals(patient, "string");
    }
  }
}
