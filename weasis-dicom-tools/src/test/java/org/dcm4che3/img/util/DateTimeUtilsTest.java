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

import java.time.*;
import java.time.temporal.Temporal;
import java.time.temporal.TemporalAccessor;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.Locale;
import java.util.TimeZone;
import javax.xml.datatype.DatatypeConfigurationException;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.VR;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junitpioneer.jupiter.DefaultTimeZone;

class DateTimeUtilsTest {

  @Nested
  @DisplayName("DICOM Date Parsing Tests")
  class DicomDateTests {

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
    @DisplayName("Parse DA string - standard format")
    void shouldParseDAStandardFormat() {
      LocalDate expected = LocalDate.of(2024, 7, 15);
      LocalDate result = DateTimeUtils.parseDA("20240715");
      assertEquals(expected, result);
    }

    @Test
    @DisplayName("Parse DA string - legacy format with dots")
    void shouldParseDALegacyFormat() {
      LocalDate expected = LocalDate.of(2024, 7, 15);
      LocalDate result = DateTimeUtils.parseDA("2024.07.15");
      assertEquals(expected, result);
    }

    @Test
    @DisplayName("Format LocalDate to DA string")
    void shouldFormatLocalDateToDAString() {
      LocalDate date = LocalDate.of(2022, 12, 25);
      String result = DateTimeUtils.formatDA(date);
      assertEquals("20221225", result);
    }

    @Test
    @DisplayName("Format LocalDateTime to DA string")
    void shouldFormatLocalDateTimeToDAString() {
      LocalDateTime dateTime = LocalDateTime.of(2022, 12, 25, 15, 30);
      String result = DateTimeUtils.formatDA(dateTime);
      assertEquals("20221225", result);
    }

    @Test
    @DisplayName("Format ZonedDateTime to DA string")
    void shouldFormatZonedDateTimeToDAString() {
      ZonedDateTime zonedDateTime =
          ZonedDateTime.of(2022, 12, 25, 15, 30, 0, 0, ZoneId.systemDefault());
      String result = DateTimeUtils.formatDA(zonedDateTime);
      assertEquals("20221225", result);
    }
  }

  @Nested
  @DisplayName("DICOM Time Parsing Tests")
  class DicomTimeTests {

    @Test
    @DisplayName("Get DicomTime")
    void testGetDicomTime() {
      assertNull(DicomObjectUtil.getDicomTime("2020-12-25"));
      assertNull(DicomObjectUtil.getDicomTime(" "));
      assertNull(DicomObjectUtil.getDicomTime("235959000151"));

      LocalTime time = LocalTime.of(23, 59, 59, 151_000);
      // Dicom compliant
      assertEquals(time, DicomObjectUtil.getDicomTime("235959.000151"));
      assertEquals(
          LocalTime.of(23, 59, 59, 21_000_000), DicomObjectUtil.getDicomTime("235959.021"));
      assertEquals(time, DicomObjectUtil.getDicomTime(" 235959.000151"));
      assertEquals(time, DicomObjectUtil.getDicomTime("235959.000151 "));
      // Dicom compliant (old)
      assertEquals(time, DicomObjectUtil.getDicomTime("23:59:59.000151"));
    }

    @Test
    @DisplayName("Parse TM string - hours minutes seconds")
    void shouldParseTMHoursMinutesSeconds() {
      String time = "123059";
      LocalTime result = DateTimeUtils.parseTM(time);
      assertEquals(LocalTime.of(12, 30, 59), result);
    }

    @Test
    @DisplayName("Parse TM string - with microseconds")
    void shouldParseTMWithMicroseconds() {
      String time = "123059.123456";
      LocalTime result = DateTimeUtils.parseTM(time);
      assertEquals(LocalTime.of(12, 30, 59, 123_456_000), result);
    }

    @Test
    @DisplayName("Parse TM string - legacy format with colons")
    void shouldParseTMLegacyFormat() {
      String time = "12:30:59.123456";
      LocalTime result = DateTimeUtils.parseTM(time);
      assertEquals(LocalTime.of(12, 30, 59, 123_456_000), result);
    }

    @Test
    @DisplayName("Parse TM string to LocalTime with Max Nanos")
    void shouldParseTimeStringToLocalTimeWithMaxNanos() {
      String time = "123059";
      LocalTime result = DateTimeUtils.parseTMMax(time);
      assertEquals(LocalTime.of(12, 30, 59, 999_999_999), result);
    }

    @Test
    @DisplayName("Parse TM Max - hours only")
    void shouldParseTMMaxHoursOnly() {
      String time = "12";
      LocalTime result = DateTimeUtils.parseTMMax(time);
      assertEquals(LocalTime.of(12, 59, 59, 999_999_999), result);
    }

    @Test
    @DisplayName("Parse TM Max - hours and minutes")
    void shouldParseTMMaxHoursMinutes() {
      String time = "1230";
      LocalTime result = DateTimeUtils.parseTMMax(time);
      assertEquals(LocalTime.of(12, 30, 59, 999_999_999), result);
    }

    @Test
    @DisplayName("Format LocalTime to TM string")
    void shouldFormatLocalTimeToTMString() {
      LocalTime time = LocalTime.of(12, 30, 59);
      String result = DateTimeUtils.formatTM(time);
      assertEquals("123059.000000", result);
    }

    @Test
    @DisplayName("Format LocalTime with microseconds to TM string")
    void shouldFormatLocalTimeWithMicrosecondsToTMString() {
      LocalTime time = LocalTime.of(12, 30, 59, 978_000);
      String result = DateTimeUtils.formatTM(time);
      assertEquals("123059.000978", result);
    }

    @Test
    @DisplayName("Truncate TM string with valid length")
    void shouldTruncateTMStringWithValidLength() {
      String time = "123059.000978";
      String result = DateTimeUtils.truncateTM(time, 6);
      assertEquals("123059", result);
    }

    @Test
    @DisplayName("Truncate TM string with invalid length throws exception")
    void shouldThrowExceptionWhenTruncatingTMWithInvalidLength() {
      String time = "123059.000978";
      assertThrows(IllegalArgumentException.class, () -> DateTimeUtils.truncateTM(time, 1));
    }
  }

  @Nested
  @DisplayName("DICOM DateTime Parsing Tests")
  class DicomDateTimeTests {

    @Test
    @DisplayName("Parse DT string - full format")
    void shouldParseDTFullFormat() {
      String dateTime = "20240715123059.123456";
      Temporal result = DateTimeUtils.parseDT(dateTime);
      assertEquals(LocalDateTime.of(2024, 7, 15, 12, 30, 59, 123_456_000), result);
    }

    @Test
    @DisplayName("Parse DT string - with timezone")
    void shouldParseDTWithTimezone() {
      String dateTime = "20240715123059.123456+0200";
      Temporal result = DateTimeUtils.parseDT(dateTime);
      ZonedDateTime expected =
          LocalDateTime.of(2024, 7, 15, 12, 30, 59, 123_456_000).atZone(ZoneOffset.ofHours(2));
      assertEquals(expected, result);
    }

    @Test
    @DisplayName("Parse DT string - date only")
    void shouldParseDTDateOnly() {
      String dateTime = "20240715";
      Temporal result = DateTimeUtils.parseDT(dateTime);
      assertEquals(LocalDateTime.of(2024, 7, 15, 0, 0, 0), result);
    }

    @Test
    @DisplayName("Parse maximum DateTime with length greater than 8")
    void shouldParseDTMaxWhenLengthGreaterThanEight() {
      String value = "20201225123059.000000+0000";
      Temporal result = DateTimeUtils.parseDTMax(value);
      assertEquals(LocalDateTime.of(2020, 12, 25, 12, 30, 59, 999).atZone(ZoneOffset.UTC), result);

      value = "20201225123059.000+0000";
      result = DateTimeUtils.parseDTMax(value);
      assertEquals(
          LocalDateTime.of(2020, 12, 25, 12, 30, 59, 999_999).atZone(ZoneOffset.UTC), result);
    }

    @Test
    @DisplayName("Parse maximum DateTime with length equal to 8")
    void shouldParseDTMaxWhenLengthEqualToEight() {
      String value = "20201225";
      Temporal result = DateTimeUtils.parseDTMax(value);
      assertEquals(LocalDateTime.of(2020, 12, 25, 23, 59, 59, 999_999_999), result);
    }

    @Test
    @DisplayName("Parse maximum DateTime with length less than 8")
    void shouldParseDTMaxWhenLengthLessThanEight() {
      String value = "202012";
      Temporal result = DateTimeUtils.parseDTMax(value);
      assertEquals(LocalDateTime.of(2020, 12, 31, 23, 59, 59, 999_999_999), result);
    }

    @Test
    @DisplayName("Format LocalDateTime to DT string")
    void shouldFormatLocalDateTimeToDTString() {
      LocalDateTime dateTime = LocalDateTime.of(2022, 12, 25, 15, 30);
      String result = DateTimeUtils.formatDT(dateTime);
      assertEquals("20221225153000.000000", result);
    }

    @Test
    @DisplayName("Format ZonedDateTime to DT string")
    void shouldFormatZonedDateTimeToDTString() {
      ZonedDateTime dateTime =
          LocalDateTime.of(2022, 12, 25, 15, 30, 45, 123_000_000).atZone(ZoneOffset.ofHours(2));
      String result = DateTimeUtils.formatDT(dateTime);
      assertEquals("20221225153045.123000+0200", result);
    }

    @Test
    @DisplayName("Truncate DT string with valid length")
    void shouldTruncateDTStringWithValidLength() {
      String dateTime = "20221225153000.000000";
      String result = DateTimeUtils.truncateDT(dateTime, 8);
      assertEquals("20221225", result);
    }

    @Test
    @DisplayName("Truncate DT string with timezone preserved")
    void shouldTruncateDTStringPreservingTimezone() {
      String dateTime = "20221225153000.000000+0200";
      String result = DateTimeUtils.truncateDT(dateTime, 12);
      assertEquals("202212251530+0200", result);
    }

    @Test
    @DisplayName("Truncate DT string with invalid length throws exception")
    void shouldThrowExceptionWhenTruncatingDTWithInvalidLength() {
      String dateTime = "20221225153000.000000";
      assertThrows(IllegalArgumentException.class, () -> DateTimeUtils.truncateDT(dateTime, 3));
    }
  }

  @Nested
  @DisplayName("Date Conversion Tests")
  class DateConversionTests {

    @Test
    @DisplayName("Convert Date to LocalDate")
    @DefaultTimeZone("UTC")
    void shouldConvertDateToLocalDate() {
      assertNull(DateTimeUtils.toLocalDate(null));

      Calendar calendar = Calendar.getInstance();
      calendar.set(2024, Calendar.JUNE, 21, 14, 30, 45);
      calendar.set(Calendar.MILLISECOND, 123);
      Date date = calendar.getTime();

      LocalDate localDate = DateTimeUtils.toLocalDate(date);
      assertEquals(LocalDate.of(2024, 6, 21), localDate);
    }

    @Test
    @DisplayName("Convert Date to LocalTime")
    @DefaultTimeZone("UTC")
    void shouldConvertDateToLocalTime() {
      assertNull(DateTimeUtils.toLocalTime(null));

      Calendar calendar = Calendar.getInstance();
      calendar.set(2024, Calendar.JUNE, 21, 21, 45, 59);
      calendar.set(Calendar.MILLISECOND, 394);
      Date date = calendar.getTime();

      LocalTime localTime = DateTimeUtils.toLocalTime(date);
      assertEquals(LocalTime.of(21, 45, 59, 394_000_000), localTime);
    }

    @Test
    @DisplayName("Convert Date to LocalDateTime")
    @DefaultTimeZone("UTC")
    void shouldConvertDateToLocalDateTime() {
      assertNull(DateTimeUtils.toLocalDateTime(null));

      Calendar calendar = Calendar.getInstance();
      calendar.set(2024, Calendar.JUNE, 21, 21, 45, 59);
      calendar.set(Calendar.MILLISECOND, 394);
      Date date = calendar.getTime();

      LocalDateTime localDateTime = DateTimeUtils.toLocalDateTime(date);
      assertEquals(LocalDateTime.of(2024, 6, 21, 21, 45, 59, 394_000_000), localDateTime);
    }
  }

  @Nested
  @DisplayName("DateTime Combination Tests")
  class DateTimeCombinationTests {

    @Test
    @DisplayName("Combine LocalDate and LocalTime")
    void shouldCombineLocalDateAndLocalTime() {
      LocalDate date = LocalDate.of(2024, 7, 15);
      LocalTime time = LocalTime.of(14, 30, 45);

      LocalDateTime result = DateTimeUtils.dateTime(date, time);
      assertEquals(LocalDateTime.of(2024, 7, 15, 14, 30, 45), result);
    }

    @Test
    @DisplayName("Combine LocalDate with null time")
    void shouldCombineLocalDateWithNullTime() {
      LocalDate date = LocalDate.of(2024, 7, 15);

      LocalDateTime result = DateTimeUtils.dateTime(date, null);
      assertEquals(LocalDateTime.of(2024, 7, 15, 0, 0, 0), result);
    }

    @Test
    @DisplayName("Combine null date returns null")
    void shouldReturnNullForNullDate() {
      LocalTime time = LocalTime.of(14, 30, 45);

      LocalDateTime result = DateTimeUtils.dateTime(null, time);
      assertNull(result);
    }

    @Test
    @DisplayName("Combine Date objects with timezone")
    @DefaultTimeZone("UTC")
    void shouldCombineDateObjectsWithTimezone() {
      TimeZone timezone = TimeZone.getTimeZone(ZoneOffset.ofHours(-5));

      Calendar dateCalendar = Calendar.getInstance();
      dateCalendar.set(2024, Calendar.JULY, 15, 0, 0, 0);
      dateCalendar.set(Calendar.MILLISECOND, 0);
      Date date = dateCalendar.getTime();

      Calendar timeCalendar = Calendar.getInstance();
      timeCalendar.set(1970, Calendar.JANUARY, 1, 14, 30, 45);
      timeCalendar.set(Calendar.MILLISECOND, 123);
      Date time = timeCalendar.getTime();

      Date result = DateTimeUtils.dateTime(timezone, date, time, false);

      Calendar expectedCalendar = Calendar.getInstance(timezone);
      expectedCalendar.set(2024, Calendar.JULY, 15, 14, 30, 45);
      expectedCalendar.set(Calendar.MILLISECOND, 123);

      assertEquals(expectedCalendar.getTime(), result);
    }

    @Test
    @DisplayName("Combine Date objects with accept null policy")
    @DefaultTimeZone("UTC")
    void shouldCombineDateObjectsWithAcceptNullPolicy() {
      Calendar timeCalendar = Calendar.getInstance();
      timeCalendar.set(1970, Calendar.JANUARY, 1, 14, 30, 45);
      Date time = timeCalendar.getTime();

      // With acceptNullDateOrTime = true
      Date result = DateTimeUtils.dateTime(null, null, time, true);
      assertEquals(time, result);

      // With acceptNullDateOrTime = false
      result = DateTimeUtils.dateTime(null, null, time, false);
      assertNull(result);
    }

    @Test
    @DisplayName("Combine Date and Time from DICOM attributes")
    @DefaultTimeZone("UTC")
    void shouldCombineDateTimeFromDicomAttributes() {
      Attributes attributes = new Attributes();
      attributes.setString(Tag.StudyDate, VR.DA, "20240715");
      attributes.setString(Tag.StudyTime, VR.TM, "143045");

      Date result = DateTimeUtils.dateTime(attributes, Tag.StudyDate, Tag.StudyTime);

      Calendar expected = Calendar.getInstance();
      expected.set(2024, Calendar.JULY, 15, 14, 30, 45);
      expected.set(Calendar.MILLISECOND, 0);

      assertEquals(expected.getTime(), result);
    }

    @Test
    @DisplayName("Combine Date and Time from DICOM attributes with timezone")
    @DefaultTimeZone("UTC")
    void shouldCombineDateTimeFromDicomAttributesWithTimezone() {
      Attributes attributes = new Attributes();
      attributes.setString(Tag.TimezoneOffsetFromUTC, VR.SH, "+0200");
      attributes.setString(Tag.SeriesDate, VR.DA, "20240715");
      attributes.setString(Tag.SeriesTime, VR.TM, "143045");

      Date result = DateTimeUtils.dateTime(attributes, Tag.SeriesDate, Tag.SeriesTime);

      // Expected time adjusted for timezone offset
      Calendar expected = Calendar.getInstance();
      expected.set(2024, Calendar.JULY, 15, 12, 30, 45); // UTC time (2 hours earlier)
      expected.set(Calendar.MILLISECOND, 0);

      assertEquals(expected.getTime(), result);
    }

    @Test
    @DisplayName("Return null for null DICOM attributes")
    void shouldReturnNullForNullDicomAttributes() {
      Date result = DateTimeUtils.dateTime(null, Tag.StudyDate, Tag.StudyTime);
      assertNull(result);
    }
  }

  @Nested
  @DisplayName("Display Formatting Tests")
  class DisplayFormattingTests {

    @Test
    @DisplayName("Format LocalDate for display")
    void shouldFormatLocalDateForDisplay() {
      LocalDate date = LocalDate.of(2024, 7, 15);
      String result = DateTimeUtils.formatDateTime(date, Locale.US);
      assertEquals("Jul 15, 2024", result);
    }

    @Test
    @DisplayName("Format LocalTime for display")
    void shouldFormatLocalTimeForDisplay() {
      LocalTime time = LocalTime.of(14, 30, 45);
      String result = DateTimeUtils.formatDateTime(time, Locale.US);
      assertEquals("2:30:45 PM", result);
    }

    @Test
    @DisplayName("Format LocalDateTime for display")
    void shouldFormatLocalDateTimeForDisplay() {
      LocalDateTime dateTime = LocalDateTime.of(2024, 7, 15, 14, 30, 45);
      String result = DateTimeUtils.formatDateTime(dateTime, Locale.US);
      assertEquals("Jul 15, 2024, 2:30:45 PM", result);
    }

    @Test
    @DisplayName("Format ZonedDateTime for display")
    void shouldFormatZonedDateTimeForDisplay() {
      ZonedDateTime dateTime =
          LocalDateTime.of(2024, 7, 15, 14, 30, 45).atZone(ZoneId.of("America/New_York"));
      String result = DateTimeUtils.formatDateTime(dateTime, Locale.US);
      assertEquals("Jul 15, 2024, 2:30:45 PM", result);
    }

    @Test
    @DisplayName("Format Instant for display")
    void shouldFormatInstantForDisplay() {
      Instant instant =
          LocalDateTime.of(2024, 7, 15, 14, 30, 45).atZone(ZoneId.systemDefault()).toInstant();
      String result = DateTimeUtils.formatDateTime(instant, Locale.US);
      assertNotNull(result);
      assertFalse(result.isEmpty());
    }

    @Test
    @DisplayName("Format unsupported temporal type returns empty string")
    void shouldReturnEmptyStringForUnsupportedType() {
      // Create a custom TemporalAccessor that doesn't match any supported type
      TemporalAccessor unsupported =
          new TemporalAccessor() {
            @Override
            public boolean isSupported(java.time.temporal.TemporalField field) {
              return false;
            }

            @Override
            public long getLong(java.time.temporal.TemporalField field) {
              throw new UnsupportedOperationException();
            }
          };

      String result = DateTimeUtils.formatDateTime(unsupported, Locale.US);
      assertEquals("", result);
    }

    @Test
    @DisplayName("Format DateTime with default locale")
    void shouldFormatDateTimeWithDefaultLocale() {
      LocalDate date = LocalDate.of(2024, 7, 15);
      String result = DateTimeUtils.formatDateTime(date);
      assertNotNull(result);
      assertFalse(result.isEmpty());
    }
  }

  @Nested
  @DisplayName("XML DateTime Tests")
  class XmlDateTimeTests {

    @Test
    @DisplayName("Parse XML DateTime without timezone")
    void shouldParseXmlDateTimeWithoutTimezone() throws DatatypeConfigurationException {
      GregorianCalendar result = DateTimeUtils.parseXmlDateTime("2022-03-21T12:00:00");

      GregorianCalendar expected = new GregorianCalendar(2022, Calendar.MARCH, 21, 12, 0, 0);
      expected.setTimeZone(result.getTimeZone()); // Use same timezone for comparison

      assertEquals(expected.getTime(), result.getTime());
    }

    @Test
    @DisplayName("Parse XML DateTime with timezone")
    void shouldParseXmlDateTimeWithTimezone() throws DatatypeConfigurationException {
      GregorianCalendar result = DateTimeUtils.parseXmlDateTime("2022-03-21T12:00:00+01:00");

      // Create expected calendar directly in UTC representing the same instant
      // 2022-03-21T12:00:00+01:00 is the same instant as 2022-03-21T11:00:00Z
      GregorianCalendar expected = new GregorianCalendar(TimeZone.getTimeZone("UTC"));
      expected.set(2022, Calendar.MARCH, 21, 11, 0, 0);
      expected.set(Calendar.MILLISECOND, 0);

      // Convert result to UTC for comparison - this preserves the instant in time
      GregorianCalendar resultInUTC = new GregorianCalendar(TimeZone.getTimeZone("UTC"));
      resultInUTC.setTimeInMillis(result.getTimeInMillis());

      assertEquals(expected.getTime(), resultInUTC.getTime());
    }

    @Test
    @DisplayName("Parse XML DateTime with microseconds")
    void shouldParseXmlDateTimeWithMicroseconds() throws DatatypeConfigurationException {
      GregorianCalendar result = DateTimeUtils.parseXmlDateTime("2022-03-21T12:30:45.123456");

      GregorianCalendar expected = new GregorianCalendar(2022, Calendar.MARCH, 21, 12, 30, 45);
      expected.set(Calendar.MILLISECOND, 123);
      expected.setTimeZone(result.getTimeZone());

      assertEquals(expected.getTime(), result.getTime());
    }

    @Test
    @DisplayName("Parse XML DateTime throws exception for null input")
    void shouldThrowExceptionForNullInput() {
      assertThrows(IllegalArgumentException.class, () -> DateTimeUtils.parseXmlDateTime(null));
    }

    @Test
    @DisplayName("Parse XML DateTime throws exception for empty input")
    void shouldThrowExceptionForEmptyInput() {
      assertThrows(IllegalArgumentException.class, () -> DateTimeUtils.parseXmlDateTime(""));
    }
  }

  @Nested
  @DisplayName("Edge Cases and Error Handling")
  class EdgeCasesTests {

    @Test
    @DisplayName("Handle whitespace in DA parsing")
    void shouldHandleWhitespaceInDAParsing() {
      LocalDate expected = LocalDate.of(2024, 7, 15);
      assertEquals(expected, DateTimeUtils.parseDA("  20240715  "));
    }

    @Test
    @DisplayName("Handle whitespace in TM parsing")
    void shouldHandleWhitespaceInTMParsing() {
      LocalTime expected = LocalTime.of(12, 30, 59);
      assertEquals(expected, DateTimeUtils.parseTM("  123059  "));
    }

    @Test
    @DisplayName("Handle whitespace in DT parsing")
    void shouldHandleWhitespaceInDTParsing() {
      LocalDateTime expected = LocalDateTime.of(2024, 7, 15, 12, 30, 59);
      assertEquals(expected, DateTimeUtils.parseDT("  20240715123059  "));
    }

    @Test
    @DisplayName("Parse DT with partial precision - year and month only")
    void shouldParseDTPartialPrecisionYearMonth() {
      String value = "202407";
      Temporal result = DateTimeUtils.parseDT(value);
      assertEquals(LocalDateTime.of(2024, 7, 1, 0, 0, 0), result);
    }

    @Test
    @DisplayName("Parse DT Max with year precision")
    void shouldParseDTMaxWithYearPrecision() {
      String value = "2024";
      Temporal result = DateTimeUtils.parseDTMax(value);
      assertEquals(LocalDateTime.of(2024, 12, 31, 23, 59, 59, 999_999_999), result);
    }

    @Test
    @DisplayName("Combine tags creates correct long value")
    void shouldCombineTagsCorrectly() {
      int dateTag = 0x00080020; // StudyDate
      int timeTag = 0x00080030; // StudyTime

      long combined = DateTimeUtils.combineTags(dateTag, timeTag);
      long expected = ((long) dateTag << 32) | (timeTag & 0xFFFFFFFFL);

      assertEquals(expected, combined);
    }
  }
}
