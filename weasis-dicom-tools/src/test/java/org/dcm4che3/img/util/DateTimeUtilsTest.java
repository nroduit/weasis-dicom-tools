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
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.Month;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.temporal.Temporal;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.TimeZone;
import javax.xml.datatype.DatatypeConfigurationException;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.VR;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junitpioneer.jupiter.DefaultTimeZone;

class DateTimeUtilsTest {
  @Test
  @DisplayName("Get DicomDate")
  void testGetDicomDate() {
    assertNull(DicomObjectUtil.getDicomDate("2020-12-25"));
    assertNull(DicomObjectUtil.getDicomDate(""));

    LocalDate day = LocalDate.of(2020, Month.DECEMBER, 25);
    // Dicom compliant
    assertEquals(day, DicomObjectUtil.getDicomDate("20201225"));
    assertEquals(day, DicomObjectUtil.getDicomDate(" 20201225"));
    assertEquals(day, DicomObjectUtil.getDicomDate("20201225 "));
    // Dicom compliant (old)
    assertEquals(day, DicomObjectUtil.getDicomDate("2020.12.25"));
  }

  @Test
  @DisplayName("Get DicomTime")
  void testGetDicomTime() {
    assertNull(DicomObjectUtil.getDicomTime("2020-12-25"));
    assertNull(DicomObjectUtil.getDicomTime(" "));
    assertNull(DicomObjectUtil.getDicomTime("235959000151"));

    LocalTime time = LocalTime.of(23, 59, 59, 151_000);
    // Dicom compliant
    assertEquals(time, DicomObjectUtil.getDicomTime("235959.000151"));
    assertEquals(LocalTime.of(23, 59, 59, 21_000_000), DicomObjectUtil.getDicomTime("235959.021"));
    assertEquals(time, DicomObjectUtil.getDicomTime(" 235959.000151"));
    assertEquals(time, DicomObjectUtil.getDicomTime("235959.000151 "));
    // Dicom compliant (old)
    assertEquals(time, DicomObjectUtil.getDicomTime("23:59:59.000151"));
  }

  @Test
  @DisplayName("Convert Date to LocalDate")
  @DefaultTimeZone("UTC")
  void testGetDateToLocalDate() {
    assertNull(DateTimeUtils.toLocalDate(null));

    TimeZone timeZone = TimeZone.getDefault();
    Calendar calendarA = Calendar.getInstance(timeZone);
    calendarA.set(2024, Calendar.JUNE, 21, 0, 0, 0);
    calendarA.set(Calendar.MILLISECOND, 0);
    Date date = calendarA.getTime();

    LocalDate localDate = DateTimeUtils.toLocalDate(date);
    assertEquals(LocalDate.of(2024, 6, 21), localDate);
  }

  @Test
  @DisplayName("Convert Date to LocalTime")
  @DefaultTimeZone("UTC")
  void testGetDateToLocalTime() {
    assertNull(DateTimeUtils.toLocalTime(null));

    TimeZone timeZone = TimeZone.getDefault();
    Calendar calendarA = Calendar.getInstance(timeZone);
    calendarA.set(2024, Calendar.JUNE, 21, 21, 45, 59);
    calendarA.set(Calendar.MILLISECOND, 394);
    Date date = calendarA.getTime();

    LocalTime localTime = DateTimeUtils.toLocalTime(date);
    assertEquals(LocalTime.of(21, 45, 59, 394_000_000), localTime);
  }

  @Test
  @DisplayName("Convert date to LocalDateTime")
  @DefaultTimeZone("UTC")
  void testGetDateToLocalDateTime() {
    assertNull(DateTimeUtils.toLocalDateTime(null));

    TimeZone timeZone = TimeZone.getDefault();
    Calendar calendarA = Calendar.getInstance(timeZone);
    calendarA.set(2024, Calendar.JUNE, 21, 21, 45, 59);
    calendarA.set(Calendar.MILLISECOND, 394);
    Date date = calendarA.getTime();

    LocalDateTime localDateTime = DateTimeUtils.toLocalDateTime(date);
    assertEquals(LocalDateTime.of(2024, 6, 21, 21, 45, 59, 394_000_000), localDateTime);
  }

  @Test
  @DisplayName("Get DateTime")
  @DefaultTimeZone("UTC")
  void testGetDateTime() {
    assertNull(DateTimeUtils.dateTime(null, null));

    TimeZone timeZone1 = TimeZone.getTimeZone(ZoneOffset.ofHours(-10));
    TimeZone timeZone2 = TimeZone.getTimeZone(ZoneOffset.ofHours(+10));
    Calendar calendarA = Calendar.getInstance();
    calendarA.set(2024, Calendar.JUNE, 21, 0, 0, 0);
    calendarA.set(Calendar.MILLISECOND, 0);
    Date date = calendarA.getTime();
    calendarA.set(1970, Calendar.JANUARY, 1, 5, 27, 54);
    calendarA.set(Calendar.MILLISECOND, 394);
    Date time = calendarA.getTime();
    Date result = DateTimeUtils.dateTime(null, date, null, true);
    assertEquals(date, result);

    result = DateTimeUtils.dateTime(null, null, time, true);
    assertEquals(time, result);
    result = DateTimeUtils.dateTime(timeZone1, null, time, true);
    assertEquals(time, result);
    result = DateTimeUtils.dateTime(timeZone2, null, time, true);
    assertEquals(time, result);

    result = DateTimeUtils.dateTime(timeZone1, date, time, false);
    Calendar calendar1 = Calendar.getInstance(timeZone1);
    calendar1.set(2024, Calendar.JUNE, 21, 5, 27, 54);
    calendar1.set(Calendar.MILLISECOND, 394);
    assertEquals(calendar1.getTime(), result);

    result = DateTimeUtils.dateTime(timeZone2, date, time, false);
    calendar1 = Calendar.getInstance(timeZone2);
    calendar1.set(2024, Calendar.JUNE, 21, 5, 27, 54);
    calendar1.set(Calendar.MILLISECOND, 394);
    assertEquals(calendar1.getTime(), result);

    assertNull(DateTimeUtils.dateTime(null, null, time, false));
    assertNull(DateTimeUtils.dateTime(null, date, null, false));
    assertNull(DateTimeUtils.dateTime(timeZone1, null, null, false));
  }

  @Test
  @DisplayName("Combine Date and Time")
  @DefaultTimeZone("UTC")
  void testCombineDateTime() {
    assertNull(DateTimeUtils.dateTime(null, 0, 0));

    Attributes attributes = new Attributes();
    attributes.setString(Tag.TimezoneOffsetFromUTC, VR.SH, "+1500");
    attributes.setString(Tag.RadiopharmaceuticalStartDateTime, VR.DT, "20250212084500");
    attributes.setString(Tag.SeriesDate, VR.DA, "20250212");
    attributes.setString(Tag.SeriesTime, VR.TM, "091530");

    Date radioPharmaceuticalStartDateTime =
        attributes.getDate(Tag.RadiopharmaceuticalStartDateTime);

    Date seriesDateTime = attributes.getDate(Tag.SeriesDateAndTime);
    Date result = DateTimeUtils.dateTime(attributes, Tag.SeriesDate, Tag.SeriesTime);
    assertEquals(seriesDateTime, result);

    Date seriesDate = attributes.getDate(Tag.SeriesDate);
    Date seriesTime = attributes.getDate(Tag.SeriesTime);
    result = DateTimeUtils.dateTime(attributes.getTimeZone(), seriesDate, seriesTime, false);
    assertEquals(seriesDateTime, result);

    assertEquals(1830000, seriesDateTime.getTime() - radioPharmaceuticalStartDateTime.getTime());
  }

  @Test
  @DisplayName("Parse maximum DateTime with length greater than 8")
  void shouldParseDTMaxWhenLengthGreaterThanEight() {
    String value = "20201225123059.000000+0000";
    Temporal result = DateTimeUtils.parseDTMax(value);
    assertEquals(LocalDateTime.of(2020, 12, 25, 12, 30, 59, 999).atZone(ZoneOffset.UTC), result);

    value = "20201225123059.000+0000";
    result = DateTimeUtils.parseDTMax(value);
    assertEquals(LocalDateTime.of(2020, 12, 25, 12, 30, 59, 999999).atZone(ZoneOffset.UTC), result);
  }

  @Test
  @DisplayName("Parse maximum DateTime with length equal to 8")
  void shouldParseDTMaxWhenLengthEqualToEight() {
    String value = "20201225";
    Temporal result = DateTimeUtils.parseDTMax(value);
    assertEquals(LocalDateTime.of(2020, 12, 25, 23, 59, 59, 999999999), result);
  }

  @Test
  @DisplayName("Parse maximum DateTime with length less than 8")
  void shouldParseDTMaxWhenLengthLessThanEight() {
    String value = "202012";
    Temporal result = DateTimeUtils.parseDTMax(value);
    assertEquals(LocalDateTime.of(2020, 12, 31, 23, 59, 59, 999999999), result);
  }

  @Test
  @DisplayName("Format Temporal to DA String")
  void testFormatTemporalToDAString() {
    Temporal temporal = LocalTime.of(12, 30, 59, 978000);
    String result = DateTimeUtils.formatTM(temporal);
    assertEquals("123059.000978", result);
  }

  @Test
  @DisplayName("Parse Time String to LocalTime")
  void shouldParseTimeStringToLocalTime() {
    String time = "123059";
    LocalTime result = DateTimeUtils.parseTM(time);
    assertEquals(LocalTime.of(12, 30, 59), result);
  }

  @Test
  @DisplayName("Parse Time String to LocalTime with Max Nanos")
  void shouldParseTimeStringToLocalTimeWithMaxNanos() {
    String time = "123059";
    LocalTime result = DateTimeUtils.parseTMMax(time);
    assertEquals(LocalTime.of(12, 30, 59, 999999999), result);
  }

  @Test
  @DisplayName("Format Temporal to TM String")
  void shouldFormatTemporalToTMString() {
    Temporal temporal = LocalTime.of(12, 30, 59);
    String result = DateTimeUtils.formatTM(temporal);
    assertEquals("123059.000000", result);
  }

  @Test
  @DisplayName("Format Temporal to DA String with LocalDate")
  void shouldFormatLocalDateToDAString() {
    LocalDate localDate = LocalDate.of(2022, 12, 25);
    String result = DateTimeUtils.formatDA(localDate);
    assertEquals("20221225", result);
  }

  @Test
  @DisplayName("Format Temporal to DA String with LocalDateTime")
  void shouldFormatLocalDateTimeToDAString() {
    LocalDateTime localDateTime = LocalDateTime.of(2022, 12, 25, 15, 30);
    String result = DateTimeUtils.formatDA(localDateTime);
    assertEquals("20221225", result);
  }

  @Test
  @DisplayName("Format Temporal to DA String with ZonedDateTime")
  void shouldFormatZonedDateTimeToDAString() {
    ZonedDateTime zonedDateTime =
        ZonedDateTime.of(2022, 12, 25, 15, 30, 0, 0, ZoneId.systemDefault());
    String result = DateTimeUtils.formatDA(zonedDateTime);
    assertEquals("20221225", result);
  }

  @Test
  @DisplayName("Format Temporal to DT String")
  void shouldFormatTemporalToDTString() {
    Temporal temporal = LocalDateTime.of(2022, 12, 25, 15, 30);
    String result = DateTimeUtils.formatDT(temporal);
    assertEquals("20221225153000.000000", result);
  }

  @Test
  @DisplayName("Truncate TM String with valid length")
  void shouldTruncateTMStringWithValidLength() {
    String time = "123059.000978";
    String result = DateTimeUtils.truncateTM(time, 6);
    assertEquals("123059", result);

    // Truncate TM String with invalid length
    assertThrows(IllegalArgumentException.class, () -> DateTimeUtils.truncateTM(time, 1));
  }

  @Test
  @DisplayName("Truncate DT String with valid length")
  void shouldTruncateDTStringWithValidLength() {
    String dateTime = "20221225153000.000000";
    String result = DateTimeUtils.truncateDT(dateTime, 8);
    assertEquals("20221225", result);

    // Truncate DT String with invalid length
    assertThrows(IllegalArgumentException.class, () -> DateTimeUtils.truncateDT(dateTime, 3));
  }

  @Test
  void parseXmlDateTimeReturnsCorrectDateForValidInput() throws DatatypeConfigurationException {
    TimeZone utc = TimeZone.getTimeZone("UTC");
    GregorianCalendar result = DateTimeUtils.parseXmlDateTime("2022-03-21T12:00:00");
    result.setTimeZone(utc);
    GregorianCalendar expected = new GregorianCalendar(2022, Calendar.MARCH, 21, 12, 0, 0);
    expected.setTimeZone(utc);
    Assertions.assertEquals(expected.getTime(), result.getTime());

    utc = TimeZone.getTimeZone("UTC+1");
    result = DateTimeUtils.parseXmlDateTime("2022-03-21T12:00:00+01:00");
    expected = new GregorianCalendar(2022, Calendar.MARCH, 21, 11, 0, 0);
    expected.setTimeZone(utc);
    Assertions.assertEquals(expected.getTime(), result.getTime());
  }
}
