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
import org.junit.jupiter.params.provider.ValueSource;

@DisplayNameGeneration(ReplaceUnderscores.class)
class SeriesTest {

  private static final String SERIES_UID = "1.2.3.4.5";
  private static final String SERIES_DESCRIPTION = "Test Series";
  private static final String SERIES_NUMBER = "1";
  private static final String MODALITY = "CT";
  private static final String WADO_TSUID = "1.2.840.10008.1.2.1";

  private Series series;

  @BeforeEach
  void setUp() {
    series = new Series(SERIES_UID);
  }

  @Nested
  class Constructor_Tests {

    @Test
    void constructor_with_valid_series_uid_creates_series() {
      var newSeries = new Series("1.2.3.4.6");

      assertEquals("1.2.3.4.6", newSeries.getSeriesInstanceUID());
      assertTrue(newSeries.isEmpty());
    }

    @Test
    void constructor_with_null_series_uid_throws_exception() {
      assertThrows(NullPointerException.class, () -> new Series(null));
    }
  }

  @Nested
  class Basic_Properties_Tests {

    @Test
    void getSeriesInstanceUID_returns_constructor_value() {
      assertEquals(SERIES_UID, series.getSeriesInstanceUID());
    }

    @Test
    void setSeriesDescription_sets_and_gets_value() {
      series.setSeriesDescription(SERIES_DESCRIPTION);
      assertEquals(SERIES_DESCRIPTION, series.getSeriesDescription());
    }

    @Test
    void setSeriesNumber_with_valid_number_sets_trimmed_value() {
      series.setSeriesNumber("  123  ");
      assertEquals("123", series.getSeriesNumber());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "\t\n"})
    void setSeriesNumber_with_null_or_empty_sets_null(String input) {
      series.setSeriesNumber(input);
      assertNull(series.getSeriesNumber());
    }

    @Test
    void setModality_sets_and_gets_value() {
      series.setModality(MODALITY);
      assertEquals(MODALITY, series.getModality());
    }

    @Test
    void setWadoTransferSyntaxUID_sets_and_gets_value() {
      series.setWadoTransferSyntaxUID(WADO_TSUID);
      assertEquals(WADO_TSUID, series.getWadoTransferSyntaxUID());
    }

    @Test
    void setThumbnail_sets_and_gets_value() {
      String thumbnail = "http://example.com/thumb.jpg";
      series.setThumbnail(thumbnail);
      assertEquals(thumbnail, series.getThumbnail());
    }
  }

  @Nested
  class WADO_Compression_Tests {

    @ParameterizedTest
    @ValueSource(ints = {0, 50, 100})
    void setWadoCompression_with_valid_values_sets_value(int compression) {
      series.setWadoCompression(compression);
      assertEquals(compression, series.getWadoCompression());
    }

    @ParameterizedTest
    @CsvSource({"-10, 0", "150, 100", "999, 100"})
    void setWadoCompression_clamps_out_of_range_values(int input, int expected) {
      series.setWadoCompression(input);
      assertEquals(expected, series.getWadoCompression());
    }

    @ParameterizedTest
    @ValueSource(strings = {"50", "0", "100"})
    void setWadoCompression_with_valid_string_parses_correctly(String input) {
      int expected = Integer.parseInt(input);
      series.setWadoCompression(input);
      assertEquals(expected, series.getWadoCompression());
    }

    @ParameterizedTest
    @ValueSource(strings = {"invalid", "", "abc", "50.5"})
    void setWadoCompression_with_invalid_string_keeps_current_value(String input) {
      int originalValue = series.getWadoCompression();
      series.setWadoCompression(input);
      assertEquals(originalValue, series.getWadoCompression());
    }

    @Test
    void setWadoCompression_with_null_string_ignores_value() {
      int originalValue = series.getWadoCompression();
      series.setWadoCompression((String) null);
      assertEquals(originalValue, series.getWadoCompression());
    }
  }

  @Nested
  class SOP_Instance_Management_Tests {

    private SopInstance sopInstance1;
    private SopInstance sopInstance2;

    @BeforeEach
    void setUp() {
      sopInstance1 = new SopInstance("1.2.3.4.7", 1);
      sopInstance2 = new SopInstance("1.2.3.4.8", 2);
    }

    @Test
    void addSopInstance_with_valid_instance_adds_instance() {
      series.addSopInstance(sopInstance1);

      assertEquals(sopInstance1, series.getSopInstance("1.2.3.4.7", 1));
      assertFalse(series.isEmpty());
    }

    @Test
    void addSopInstance_with_null_instance_ignores_addition() {
      series.addSopInstance(null);
      assertTrue(series.isEmpty());
    }

    @Test
    void getSopInstance_with_existing_uid_and_number_returns_instance() {
      series.addSopInstance(sopInstance1);
      assertEquals(sopInstance1, series.getSopInstance("1.2.3.4.7", 1));
    }

    @Test
    void getSopInstance_with_non_existing_parameters_returns_null() {
      assertNull(series.getSopInstance("non.existing.uid", 1));
    }

    @Test
    void removeSopInstance_with_existing_parameters_removes_and_returns_instance() {
      series.addSopInstance(sopInstance1);

      assertEquals(sopInstance1, series.removeSopInstance("1.2.3.4.7", 1));
      assertNull(series.getSopInstance("1.2.3.4.7", 1));
    }

    @Test
    void removeSopInstance_with_non_existing_parameters_returns_null() {
      assertNull(series.removeSopInstance("non.existing.uid", 1));
    }

    @Test
    void getSopInstances_returns_all_instances() {
      series.addSopInstance(sopInstance1);
      series.addSopInstance(sopInstance2);

      Collection<SopInstance> instances = series.getSopInstances();
      assertEquals(2, instances.size());
      assertTrue(instances.contains(sopInstance1));
      assertTrue(instances.contains(sopInstance2));
    }

    @Test
    void getEntrySet_returns_instance_entry_set() {
      series.addSopInstance(sopInstance1);
      series.addSopInstance(sopInstance2);

      Set<Entry<String, SopInstance>> entrySet = series.getEntrySet();
      assertEquals(2, entrySet.size());
    }

    @Test
    void isEmpty_with_no_instances_returns_true() {
      assertTrue(series.isEmpty());
    }

    @Test
    void isEmpty_with_instances_returns_false() {
      series.addSopInstance(sopInstance1);
      assertFalse(series.isEmpty());
    }
  }

  @Nested
  class XML_Serialization_Tests {

    @Test
    void toXml_with_complete_series_generates_valid_xml() throws IOException {
      setupCompleteSeries();

      var writer = new StringWriter();
      series.toXml(writer);
      String xml = writer.toString();

      assertAll(
          () -> assertTrue(xml.contains("<Series ")),
          () -> assertTrue(xml.contains("SeriesInstanceUID=\"" + SERIES_UID + "\"")),
          () -> assertTrue(xml.contains("SeriesDescription=\"" + SERIES_DESCRIPTION + "\"")),
          () -> assertTrue(xml.contains("SeriesNumber=\"" + SERIES_NUMBER + "\"")),
          () -> assertTrue(xml.contains("Modality=\"" + MODALITY + "\"")),
          () -> assertTrue(xml.contains("WadoCompressionRate=\"50\"")),
          () -> assertTrue(xml.contains("</Series>")));
    }

    @Test
    void toXml_without_compression_excludes_compression_attribute() throws IOException {
      series.setSeriesDescription(SERIES_DESCRIPTION);

      var writer = new StringWriter();
      series.toXml(writer);
      String xml = writer.toString();

      assertFalse(xml.contains("WadoCompressionRate"));
    }

    @Test
    void toXml_includes_sop_instances_in_sorted_order() throws IOException {
      setupSeriesWithInstances();

      var writer = new StringWriter();
      series.toXml(writer);
      String xml = writer.toString();

      assertTrue(xml.contains("<Instance "));
      assertTrue(xml.contains("SOPInstanceUID=\"1.2.3.4.7\""));
    }

    private void setupCompleteSeries() {
      series.setSeriesDescription(SERIES_DESCRIPTION);
      series.setSeriesNumber(SERIES_NUMBER);
      series.setModality(MODALITY);
      series.setWadoTransferSyntaxUID(WADO_TSUID);
      series.setWadoCompression(50);
      series.setThumbnail("http://example.com/thumb.jpg");
    }

    private void setupSeriesWithInstances() {
      series.setSeriesDescription(SERIES_DESCRIPTION);
      var sopInstance = new SopInstance("1.2.3.4.7", 1);
      series.addSopInstance(sopInstance);
    }
  }

  @Nested
  class Comparison_Tests {

    @Test
    void compareTo_compares_by_series_number() {
      var series1 = new Series("1.2.3.4.5");
      series1.setSeriesNumber("1");

      var series2 = new Series("1.2.3.4.6");
      series2.setSeriesNumber("2");

      assertTrue(series1.compareTo(series2) < 0);
      assertTrue(series2.compareTo(series1) > 0);
    }

    @Test
    void compareTo_falls_back_to_series_uid_when_numbers_equal() {
      var series1 = new Series("1.2.3.4.5");
      series1.setSeriesNumber("1");

      var series2 = new Series("1.2.3.4.6");
      series2.setSeriesNumber("1");

      assertTrue(series1.compareTo(series2) < 0);
    }

    @Test
    void compareTo_handles_null_series_numbers() {
      var series1 = new Series("1.2.3.4.5");
      var series2 = new Series("1.2.3.4.6");
      series2.setSeriesNumber("1");

      assertTrue(series1.compareTo(series2) > 0); // null comes after
    }

    @Test
    void compareTo_with_same_series_returns_zero() {
      assertEquals(0, series.compareTo(series));
    }
  }

  @Nested
  class Equality_Tests {

    @Test
    void equals_with_same_series_uid_returns_true() {
      var series1 = new Series(SERIES_UID);
      var series2 = new Series(SERIES_UID);

      assertEquals(series1, series2);
      assertEquals(series1.hashCode(), series2.hashCode());
    }

    @Test
    void equals_with_different_series_uid_returns_false() {
      var series1 = new Series("1.2.3.4.5");
      var series2 = new Series("1.2.3.4.6");

      assertNotEquals(series1, series2);
    }

    @Test
    void equals_with_self_returns_true() {
      assertEquals(series, series);
    }

    @Test
    void equals_with_null_returns_false() {
      assertNotEquals(series, null);
    }

    @Test
    void equals_with_different_class_returns_false() {
      assertNotEquals(series, "string");
    }
  }
}
