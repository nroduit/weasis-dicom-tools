/*
 * Copyright (c) 2024 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.dcm4che3.img.util;

import static org.junit.jupiter.api.Assertions.*;

import java.time.*;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.stream.Stream;
import javax.xml.datatype.DatatypeConfigurationException;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.VR;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.junitpioneer.jupiter.DefaultTimeZone;

@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class DateTimeUtilsTest {

  @Nested
  class Dicom_date_parsing {

    @Test
    void parse_DA_standard_format() {
      var expected = LocalDate.of(2024, 7, 15);
      var result = DateTimeUtils.parseDA("20240715");
      assertEquals(expected, result);
    }

    @Test
    void parse_DA_legacy_format_with_dots() {
      var expected = LocalDate.of(2024, 7, 15);
      var result = DateTimeUtils.parseDA("2024.07.15");
      assertEquals(expected, result);
    }

    @ParameterizedTest
    @ValueSource(strings = {"  20240715  ", " 2024.07.15 ", "20240715", "2024.07.15"})
    void parse_DA_handles_whitespace(String input) {
      var expected = LocalDate.of(2024, 7, 15);
      var result = DateTimeUtils.parseDA(input);
      assertEquals(expected, result);
    }

    @ParameterizedTest
    @ValueSource(strings = {"2024-07-15", "20240715T", "invalid", "", "202407"})
    void parse_DA_throws_exception_for_invalid_formats(String input) {
      assertThrows(DateTimeParseException.class, () -> DateTimeUtils.parseDA(input));
    }

    @Test
    void format_LocalDate_to_DA_string() {
      var date = LocalDate.of(2022, 12, 25);
      var result = DateTimeUtils.formatDA(date);
      assertEquals("20221225", result);
    }

    @Test
    void format_LocalDateTime_to_DA_string() {
      var dateTime = LocalDateTime.of(2022, 12, 25, 15, 30);
      var result = DateTimeUtils.formatDA(dateTime);
      assertEquals("20221225", result);
    }

    @Test
    void format_ZonedDateTime_to_DA_string() {
      var zonedDateTime = ZonedDateTime.of(2022, 12, 25, 15, 30, 0, 0, ZoneId.systemDefault());
      var result = DateTimeUtils.formatDA(zonedDateTime);
      assertEquals("20221225", result);
    }
  }

  @Nested
  class Dicom_time_parsing {

    @ParameterizedTest
    @CsvSource({
      "123059, 12, 30, 59, 0",
      "123059.123456, 12, 30, 59, 123456000",
      "12:30:59, 12, 30, 59, 0",
      "12:30:59.123456, 12, 30, 59, 123456000"
    })
    void parse_TM_various_formats(String input, int hour, int minute, int second, int nano) {
      var expected = LocalTime.of(hour, minute, second, nano);
      var result = DateTimeUtils.parseTM(input);
      assertEquals(expected, result);
    }

    @ParameterizedTest
    @ValueSource(strings = {"  123059  ", " 12:30:59 ", "123059", "12:30:59"})
    void parse_TM_handles_whitespace(String input) {
      var expected = LocalTime.of(12, 30, 59);
      var result = DateTimeUtils.parseTM(input);
      assertEquals(expected, result);
    }

    @ParameterizedTest
    @CsvSource({
      "12, 12, 59, 59, 999999999",
      "1230, 12, 30, 59, 999999999",
      "123059, 12, 30, 59, 999999999",
      "123059.1, 12, 30, 59, 199999999",
      "123059.12, 12, 30, 59, 129999999"
    })
    void parse_TM_max_adds_correct_nanoseconds(
        String input, int hour, int minute, int second, int nano) {
      var expected = LocalTime.of(hour, minute, second, nano);
      var result = DateTimeUtils.parseTMMax(input);
      assertEquals(expected, result);
    }

    @Test
    void format_LocalTime_to_TM_string() {
      var time = LocalTime.of(12, 30, 59);
      var result = DateTimeUtils.formatTM(time);
      assertEquals("123059.000000", result);
    }

    @Test
    void format_LocalTime_with_microseconds_to_TM_string() {
      var time = LocalTime.of(12, 30, 59, 978_000);
      var result = DateTimeUtils.formatTM(time);
      assertEquals("123059.000978", result);
    }

    @ParameterizedTest
    @CsvSource({
      "123059.000978, 6, 123059",
      "123059.000978, 8, 123059.0",
      "123059.000978, 10, 123059.000",
      "123059.000978, 13, 123059.000978"
    })
    void truncate_TM_string_with_valid_length(String input, int maxLength, String expected) {
      var result = DateTimeUtils.truncateTM(input, maxLength);
      assertEquals(expected, result);
    }

    @Test
    void truncate_TM_string_throws_exception_for_invalid_length() {
      assertThrows(IllegalArgumentException.class, () -> DateTimeUtils.truncateTM("123059", 1));
    }
  }

  @Nested
  class Dicom_datetime_parsing {

    @Test
    void parse_DT_full_format() {
      var input = "20240715123059.123456";
      var result = DateTimeUtils.parseDT(input);
      assertEquals(LocalDateTime.of(2024, 7, 15, 12, 30, 59, 123_456_000), result);
    }

    @Test
    void parse_DT_with_timezone() {
      var input = "20240715123059.123456+0200";
      var result = DateTimeUtils.parseDT(input);
      var expected =
          LocalDateTime.of(2024, 7, 15, 12, 30, 59, 123_456_000).atZone(ZoneOffset.ofHours(2));
      assertEquals(expected, result);
    }

    @ParameterizedTest
    @CsvSource({
      "2024, 2024, 1, 1, 0, 0, 0, 0",
      "202407, 2024, 7, 1, 0, 0, 0, 0",
      "20240715, 2024, 7, 15, 0, 0, 0, 0",
      "2024071512, 2024, 7, 15, 12, 0, 0, 0",
      "202407151230, 2024, 7, 15, 12, 30, 0, 0"
    })
    void parse_DT_partial_precision(
        String input, int year, int month, int day, int hour, int minute, int second, int nano) {
      var result = DateTimeUtils.parseDT(input);
      var expected = LocalDateTime.of(year, month, day, hour, minute, second, nano);
      assertEquals(expected, result);
    }

    @Test
    void parse_DT_max_with_full_precision() {
      var input = "20201225123059.000000+0000";
      var result = DateTimeUtils.parseDTMax(input);
      var expected = LocalDateTime.of(2020, 12, 25, 12, 30, 59, 999).atZone(ZoneOffset.UTC);
      assertEquals(expected, result);
    }

    @Test
    void parse_DT_max_with_date_only() {
      var input = "20201225";
      var result = DateTimeUtils.parseDTMax(input);
      var expected = LocalDateTime.of(2020, 12, 25, 23, 59, 59, 999_999_999);
      assertEquals(expected, result);
    }

    @Test
    void parse_DT_max_with_month_precision() {
      var input = "202012";
      var result = DateTimeUtils.parseDTMax(input);
      var expected = LocalDateTime.of(2020, 12, 31, 23, 59, 59, 999_999_999);
      assertEquals(expected, result);
    }

    @Test
    void format_LocalDateTime_to_DT_string() {
      var dateTime = LocalDateTime.of(2022, 12, 25, 15, 30);
      var result = DateTimeUtils.formatDT(dateTime);
      assertEquals("20221225153000.000000", result);
    }

    @Test
    void format_ZonedDateTime_to_DT_string() {
      var dateTime =
          LocalDateTime.of(2022, 12, 25, 15, 30, 45, 123_000_000).atZone(ZoneOffset.ofHours(2));
      var result = DateTimeUtils.formatDT(dateTime);
      assertEquals("20221225153045.123000+0200", result);
    }

    @ParameterizedTest
    @CsvSource({
      "20221225153000.000000, 8, 20221225",
      "20221225153000.000000+0200, 12, 202212251530+0200",
      "20221225153000.000000, 14, 20221225153000"
    })
    void truncate_DT_string(String input, int maxLength, String expected) {
      var result = DateTimeUtils.truncateDT(input, maxLength);
      assertEquals(expected, result);
    }

    @Test
    void truncate_DT_string_throws_exception_for_invalid_length() {
      assertThrows(IllegalArgumentException.class, () -> DateTimeUtils.truncateDT("20221225", 3));
    }
  }

  @Nested
  class Date_conversion_tests {

    @Test
    void convert_null_date_returns_null() {
      assertAll(
          () -> assertNull(DateTimeUtils.toLocalDate(null)),
          () -> assertNull(DateTimeUtils.toLocalTime(null)),
          () -> assertNull(DateTimeUtils.toLocalDateTime(null)));
    }

    @Test
    @DefaultTimeZone("UTC")
    void convert_Date_to_LocalDate() {
      var calendar = createCalendar(2024, Calendar.JUNE, 21, 14, 30, 45, 123);
      var date = calendar.getTime();
      var result = DateTimeUtils.toLocalDate(date);
      assertEquals(LocalDate.of(2024, 6, 21), result);
    }

    @Test
    @DefaultTimeZone("UTC")
    void convert_Date_to_LocalTime() {
      var calendar = createCalendar(2024, Calendar.JUNE, 21, 21, 45, 59, 394);
      var date = calendar.getTime();
      var result = DateTimeUtils.toLocalTime(date);
      assertEquals(LocalTime.of(21, 45, 59, 394_000_000), result);
    }

    @Test
    @DefaultTimeZone("UTC")
    void convert_Date_to_LocalDateTime() {
      var calendar = createCalendar(2024, Calendar.JUNE, 21, 21, 45, 59, 394);
      var date = calendar.getTime();
      var result = DateTimeUtils.toLocalDateTime(date);
      assertEquals(LocalDateTime.of(2024, 6, 21, 21, 45, 59, 394_000_000), result);
    }
  }

  @Nested
  class DateTime_combination_tests {

    @Test
    void combine_LocalDate_and_LocalTime() {
      var date = LocalDate.of(2024, 7, 15);
      var time = LocalTime.of(14, 30, 45);
      var result = DateTimeUtils.dateTime(date, time);
      assertEquals(LocalDateTime.of(2024, 7, 15, 14, 30, 45), result);
    }

    @Test
    void combine_LocalDate_with_null_time_returns_start_of_day() {
      var date = LocalDate.of(2024, 7, 15);
      var result = DateTimeUtils.dateTime(date, null);
      assertEquals(LocalDateTime.of(2024, 7, 15, 0, 0, 0), result);
    }

    @Test
    void combine_null_date_returns_null() {
      var time = LocalTime.of(14, 30, 45);
      var result = DateTimeUtils.dateTime(null, time);
      assertNull(result);
    }

    @Test
    @DefaultTimeZone("UTC")
    void combine_Date_objects_with_timezone() {
      var timezone = TimeZone.getTimeZone(ZoneOffset.ofHours(-5));
      var dateCalendar = createCalendar(2024, Calendar.JULY, 15, 0, 0, 0, 0);
      var timeCalendar = createCalendar(1970, Calendar.JANUARY, 1, 14, 30, 45, 123);

      var result =
          DateTimeUtils.dateTime(timezone, dateCalendar.getTime(), timeCalendar.getTime(), false);

      var expectedCalendar = Calendar.getInstance(timezone);
      expectedCalendar.set(2024, Calendar.JULY, 15, 14, 30, 45);
      expectedCalendar.set(Calendar.MILLISECOND, 123);

      assertEquals(expectedCalendar.getTime(), result);
    }

    @Test
    @DefaultTimeZone("UTC")
    void combine_Date_objects_with_null_policy() {
      var timeCalendar = createCalendar(1970, Calendar.JANUARY, 1, 14, 30, 45, 0);
      var time = timeCalendar.getTime();

      // With acceptNullDateOrTime = true
      var result = DateTimeUtils.dateTime(null, null, time, true);
      assertNotNull(result);

      // With acceptNullDateOrTime = false
      result = DateTimeUtils.dateTime(null, null, time, false);
      assertNull(result);
    }

    @Test
    @DefaultTimeZone("UTC")
    void combine_datetime_from_DICOM_attributes() {
      var attributes = new Attributes();
      attributes.setString(Tag.StudyDate, VR.DA, "20240715");
      attributes.setString(Tag.StudyTime, VR.TM, "143045");

      var result = DateTimeUtils.dateTime(attributes, Tag.StudyDate, Tag.StudyTime);

      var expected = createCalendar(2024, Calendar.JULY, 15, 14, 30, 45, 0).getTime();
      assertEquals(expected, result);
    }

    @Test
    void return_null_for_null_DICOM_attributes() {
      var result = DateTimeUtils.dateTime(null, Tag.StudyDate, Tag.StudyTime);
      assertNull(result);
    }

    @Test
    void combine_tags_creates_correct_long_value() {
      int dateTag = 0x00080020; // StudyDate
      int timeTag = 0x00080030; // StudyTime

      var combined = DateTimeUtils.combineTags(dateTag, timeTag);
      var expected = ((long) dateTag << 32) | (timeTag & 0xFFFFFFFFL);

      assertEquals(expected, combined);
    }
  }

  @Nested
  class Display_formatting_tests {

    @ParameterizedTest
    @MethodSource("provideTemporalFormatting")
    void format_temporal_objects_for_display(
        Object temporal, Locale locale, boolean shouldBeEmpty) {
      var result =
          DateTimeUtils.formatDateTime((java.time.temporal.TemporalAccessor) temporal, locale);

      if (shouldBeEmpty) {
        assertEquals("", result);
      } else {
        assertFalse(result.isEmpty());
      }
    }

    static Stream<Arguments> provideTemporalFormatting() {
      return Stream.of(
          Arguments.of(LocalDate.of(2024, 7, 15), Locale.US, false),
          Arguments.of(LocalTime.of(14, 30, 45), Locale.US, false),
          Arguments.of(LocalDateTime.of(2024, 7, 15, 14, 30, 45), Locale.US, false),
          Arguments.of(
              ZonedDateTime.of(2024, 7, 15, 14, 30, 45, 0, ZoneId.of("UTC")), Locale.US, false),
          Arguments.of(Instant.ofEpochSecond(1721050245), Locale.US, false));
    }

    @Test
    void format_DateTime_with_default_locale() {
      var date = LocalDate.of(2024, 7, 15);
      var result = DateTimeUtils.formatDateTime(date);
      assertFalse(result.isEmpty());
    }

    @Test
    void format_LocalDate_for_display_US_locale() {
      var date = LocalDate.of(2024, 7, 15);
      var result = DateTimeUtils.formatDateTime(date, Locale.US);
      assertEquals("Jul 15, 2024", result);
    }
  }

  @Nested
  class XML_datetime_tests {

    @ParameterizedTest
    @ValueSource(
        strings = {
          "2022-03-21T12:00:00",
          "2022-03-21T12:00:00+01:00",
          "2022-03-21T12:30:45.123456"
        })
    void parse_XML_datetime_valid_formats(String input) throws DatatypeConfigurationException {
      var result = DateTimeUtils.parseXmlDateTime(input);
      assertNotNull(result);
      assertTrue(result instanceof GregorianCalendar);
    }

    @Test
    void parse_XML_datetime_with_timezone() throws DatatypeConfigurationException {
      var result = DateTimeUtils.parseXmlDateTime("2022-03-21T12:00:00+01:00");

      // Convert to UTC for comparison
      var resultInUTC = new GregorianCalendar(TimeZone.getTimeZone("UTC"));
      resultInUTC.setTimeInMillis(result.getTimeInMillis());

      var expected = new GregorianCalendar(TimeZone.getTimeZone("UTC"));
      expected.set(2022, Calendar.MARCH, 21, 11, 0, 0);
      expected.set(Calendar.MILLISECOND, 0);

      assertEquals(expected.getTime(), resultInUTC.getTime());
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   ", "invalid-xml"})
    void parse_XML_datetime_throws_exception_for_invalid_input(String input) {
      assertThrows(Exception.class, () -> DateTimeUtils.parseXmlDateTime(input));
    }

    @Test
    void parse_XML_datetime_throws_exception_for_null_input() {
      assertThrows(IllegalArgumentException.class, () -> DateTimeUtils.parseXmlDateTime(null));
    }
  }

  @Nested
  class Edge_cases_and_error_handling {

    @ParameterizedTest
    @ValueSource(strings = {"  20240715  ", "  123059  ", "  20240715123059  "})
    void handle_whitespace_in_parsing(String input) {
      assertDoesNotThrow(
          () -> {
            if (input.trim().length() == 8 && !input.contains(":")) {
              if (input.trim().matches("\\d{8}")) {
                DateTimeUtils.parseDA(input);
              } else {
                DateTimeUtils.parseDT(input);
              }
            } else if (input.trim().length() == 6 || input.contains(":")) {
              DateTimeUtils.parseTM(input);
            } else {
              DateTimeUtils.parseDT(input);
            }
          });
    }

    @Test
    void parse_DT_with_partial_precision_year_month_only() {
      var value = "202407";
      var result = DateTimeUtils.parseDT(value);
      assertEquals(LocalDateTime.of(2024, 7, 1, 0, 0, 0), result);
    }

    @Test
    void parse_DT_max_with_year_precision() {
      var value = "2024";
      var result = DateTimeUtils.parseDTMax(value);
      assertEquals(LocalDateTime.of(2024, 12, 31, 23, 59, 59, 999_999_999), result);
    }
  }

  // Helper methods using real Calendar instances instead of mocks
  private static Calendar createCalendar(
      int year, int month, int day, int hour, int minute, int second, int millisecond) {
    var calendar = Calendar.getInstance();
    calendar.set(year, month, day, hour, minute, second);
    calendar.set(Calendar.MILLISECOND, millisecond);
    return calendar;
  }
}
