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
import java.util.TimeZone;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

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
  void testGetDateTime() {
    assertNull(DateTimeUtils.dateTime((LocalDate) null, null));

    TimeZone timeZone = TimeZone.getDefault();
    Calendar calendarA = Calendar.getInstance(timeZone);
    calendarA.set(2024, Calendar.JUNE, 21, 0, 0, 0);
    calendarA.set(Calendar.MILLISECOND, 0);
    Date date = calendarA.getTime();
    calendarA.set(1970, Calendar.JANUARY, 1, 5, 27, 54);
    calendarA.set(Calendar.MILLISECOND, 5394);
    Date time = calendarA.getTime();
    Date result = DateTimeUtils.dateTime(date, null);
    assertEquals(date, result);

    result = DateTimeUtils.dateTime(null, time);
    assertEquals(time, result);

    result = DateTimeUtils.dateTime(date, time);
    calendarA.set(2024, Calendar.JUNE, 21, 5, 27, 54);
    calendarA.set(Calendar.MILLISECOND, 5394);
    assertEquals(calendarA.getTime(), result);
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
}
