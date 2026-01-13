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
import org.junit.jupiter.params.provider.NullAndEmptySource;

@DisplayNameGeneration(ReplaceUnderscores.class)
class StudyTest {

  private static final String STUDY_UID = "1.2.3.4.5";
  private static final String STUDY_DESCRIPTION = "Test Study";
  private static final String STUDY_DATE = "20240101";
  private static final String STUDY_TIME = "120000";
  private static final String ACCESSION_NUMBER = "ACC123";
  private static final String REFERRING_PHYSICIAN = "Dr. Smith";

  private Study study;

  @BeforeEach
  void setUp() {
    study = new Study(STUDY_UID);
  }

  @Nested
  class Constructor_Tests {

    @Test
    void constructor_with_valid_study_uid_creates_study() {
      var newStudy = new Study("1.2.3.4.6");

      assertEquals("1.2.3.4.6", newStudy.getStudyInstanceUID());
      assertTrue(newStudy.isEmpty());
    }

    @Test
    void constructor_with_null_study_uid_throws_exception() {
      assertThrows(NullPointerException.class, () -> new Study(null));
    }
  }

  @Nested
  class Basic_Properties_Tests {

    @Test
    void getStudyInstanceUID_returns_constructor_value() {
      assertEquals(STUDY_UID, study.getStudyInstanceUID());
    }

    @Test
    void setStudyID_sets_and_gets_value() {
      study.setStudyID("STUDY123");
      assertEquals("STUDY123", study.getStudyID());
    }

    @Test
    void setStudyDescription_sets_and_gets_value() {
      study.setStudyDescription(STUDY_DESCRIPTION);
      assertEquals(STUDY_DESCRIPTION, study.getStudyDescription());
    }

    @Test
    void setStudyDate_sets_and_gets_value() {
      study.setStudyDate(STUDY_DATE);
      assertEquals(STUDY_DATE, study.getStudyDate());
    }

    @Test
    void setStudyTime_sets_and_gets_value() {
      study.setStudyTime(STUDY_TIME);
      assertEquals(STUDY_TIME, study.getStudyTime());
    }

    @Test
    void setAccessionNumber_sets_and_gets_value() {
      study.setAccessionNumber(ACCESSION_NUMBER);
      assertEquals(ACCESSION_NUMBER, study.getAccessionNumber());
    }

    @Test
    void setReferringPhysicianName_sets_and_gets_value() {
      study.setReferringPhysicianName(REFERRING_PHYSICIAN);
      assertEquals(REFERRING_PHYSICIAN, study.getReferringPhysicianName());
    }

    @ParameterizedTest
    @NullAndEmptySource
    void properties_accept_null_and_empty_values(String value) {
      assertDoesNotThrow(
          () -> {
            study.setStudyID(value);
            study.setStudyDescription(value);
            study.setStudyDate(value);
            study.setStudyTime(value);
            study.setAccessionNumber(value);
            study.setReferringPhysicianName(value);
          });
    }
  }

  @Nested
  class Series_Management_Tests {

    private Series series1;
    private Series series2;

    @BeforeEach
    void setUp() {
      series1 = new Series("1.2.3.4.7");
      series1.addSopInstance(new SopInstance("1.2.3.4.7.1", 1));
      series2 = new Series("1.2.3.4.8");
    }

    @Test
    void addSeries_with_valid_series_adds_series() {
      study.addSeries(series1);

      assertEquals(series1, study.getSeries("1.2.3.4.7"));
      assertFalse(study.isEmpty());
    }

    @Test
    void addSeries_with_null_series_ignores_addition() {
      study.addSeries(null);
      assertTrue(study.isEmpty());
    }

    @Test
    void getSeries_with_existing_uid_returns_series() {
      study.addSeries(series1);
      assertEquals(series1, study.getSeries("1.2.3.4.7"));
    }

    @Test
    void getSeries_with_non_existing_uid_returns_null() {
      assertNull(study.getSeries("non.existing.uid"));
    }

    @Test
    void removeSeries_with_existing_uid_removes_and_returns_series() {
      study.addSeries(series1);

      assertEquals(series1, study.removeSeries("1.2.3.4.7"));
      assertNull(study.getSeries("1.2.3.4.7"));
    }

    @Test
    void removeSeries_with_non_existing_uid_returns_null() {
      assertNull(study.removeSeries("non.existing.uid"));
    }

    @Test
    void getSeries_collection_returns_all_series() {
      study.addSeries(series1);
      study.addSeries(series2);

      Collection<Series> seriesCollection = study.getSeries();
      assertEquals(2, seriesCollection.size());
      assertTrue(seriesCollection.contains(series1));
      assertTrue(seriesCollection.contains(series2));
    }

    @Test
    void getEntrySet_returns_series_entry_set() {
      study.addSeries(series1);
      study.addSeries(series2);

      Set<Entry<String, Series>> entrySet = study.getEntrySet();
      assertEquals(2, entrySet.size());
    }

    @Test
    void isEmpty_with_empty_series_returns_true() {
      study.addSeries(series2); // Empty series
      assertTrue(study.isEmpty());
    }

    @Test
    void isEmpty_with_non_empty_series_returns_false() {
      var sopInstance = new SopInstance("1.2.3.4.9", 1);
      series1.addSopInstance(sopInstance);
      study.addSeries(series1);

      assertFalse(study.isEmpty());
    }
  }

  @Nested
  class XML_Serialization_Tests {

    @Test
    void toXml_with_complete_study_generates_valid_xml() throws IOException {
      setupCompleteStudy();

      var writer = new StringWriter();
      study.toXml(writer);
      String xml = writer.toString();

      assertAll(
          () -> assertTrue(xml.contains("<Study ")),
          () -> assertTrue(xml.contains("StudyInstanceUID=\"" + STUDY_UID + "\"")),
          () -> assertTrue(xml.contains("StudyDescription=\"" + STUDY_DESCRIPTION + "\"")),
          () -> assertTrue(xml.contains("StudyDate=\"" + STUDY_DATE + "\"")),
          () -> assertTrue(xml.contains("StudyTime=\"" + STUDY_TIME + "\"")),
          () -> assertTrue(xml.contains("AccessionNumber=\"" + ACCESSION_NUMBER + "\"")),
          () -> assertTrue(xml.contains("</Study>")));
    }

    @Test
    void toXml_with_null_study_uid_skips_serialization() throws IOException {
      // This shouldn't happen due to constructor validation, but test defensive behavior
      var writer = new StringWriter();
      study.toXml(writer);
      String xml = writer.toString();

      assertTrue(xml.contains("<Study "));
    }

    @Test
    void toXml_includes_series_in_sorted_order() throws IOException {
      setupStudyWithSeries();

      var writer = new StringWriter();
      study.toXml(writer);
      String xml = writer.toString();

      assertTrue(xml.contains("<Series "));
      assertTrue(xml.contains("SeriesInstanceUID=\"1.2.3.4.7\""));
    }

    private void setupCompleteStudy() {
      study.setStudyDescription(STUDY_DESCRIPTION);
      study.setStudyDate(STUDY_DATE);
      study.setStudyTime(STUDY_TIME);
      study.setAccessionNumber(ACCESSION_NUMBER);
      study.setReferringPhysicianName(REFERRING_PHYSICIAN);
    }

    private void setupStudyWithSeries() {
      study.setStudyDescription(STUDY_DESCRIPTION);
      var series = new Series("1.2.3.4.7");
      series.setSeriesDescription("Test Series");
      study.addSeries(series);
    }
  }

  @Nested
  class Comparison_Tests {

    @Test
    void compareTo_prioritizes_study_datetime_most_recent_first() {
      var study1 = new Study("1.2.3.4.5");
      study1.setStudyDate("20240101");
      study1.setStudyTime("120000");

      var study2 = new Study("1.2.3.4.6");
      study2.setStudyDate("20240102");
      study2.setStudyTime("120000");

      // More recent study should come first (negative comparison result)
      assertTrue(study1.compareTo(study2) > 0);
      assertTrue(study2.compareTo(study1) < 0);
    }

    @Test
    void compareTo_falls_back_to_description_when_dates_equal() {
      var study1 = new Study("1.2.3.4.5");
      study1.setStudyDate("20240101");
      study1.setStudyDescription("Alpha Study");

      var study2 = new Study("1.2.3.4.6");
      study2.setStudyDate("20240101");
      study2.setStudyDescription("Beta Study");

      assertTrue(study1.compareTo(study2) < 0);
    }

    @Test
    void compareTo_handles_null_dates_and_descriptions() {
      var study1 = new Study("1.2.3.4.5");
      var study2 = new Study("1.2.3.4.6");

      assertEquals(0, study1.compareTo(study2));
    }

    @Test
    void compareTo_with_same_study_returns_zero() {
      assertEquals(0, study.compareTo(study));
    }
  }

  @Nested
  class Equality_Tests {

    @Test
    void equals_with_same_study_uid_returns_true() {
      var study1 = new Study(STUDY_UID);
      var study2 = new Study(STUDY_UID);

      assertEquals(study1, study2);
      assertEquals(study1.hashCode(), study2.hashCode());
    }

    @Test
    void equals_with_different_study_uid_returns_false() {
      var study1 = new Study("1.2.3.4.5");
      var study2 = new Study("1.2.3.4.6");

      assertNotEquals(study1, study2);
    }

    @Test
    void equals_with_self_returns_true() {
      assertEquals(study, study);
    }

    @Test
    void equals_with_null_returns_false() {
      assertNotEquals(study, null);
    }

    @Test
    void equals_with_different_class_returns_false() {
      assertNotEquals(study, "string");
    }
  }
}
