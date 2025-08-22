/*
 * Copyright (c) 2009-2021 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.dcm4che3.img.util;

import static java.time.temporal.ChronoField.*;
import static java.time.temporal.ChronoField.NANO_OF_SECOND;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.FormatStyle;
import java.time.temporal.ChronoUnit;
import java.time.temporal.Temporal;
import java.time.temporal.TemporalAccessor;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.Locale;
import java.util.TimeZone;
import javax.xml.datatype.DatatypeConfigurationException;
import javax.xml.datatype.DatatypeFactory;
import org.dcm4che3.data.Attributes;
import org.weasis.core.util.StringUtil;

/**
 * Utility class for DICOM date and time parsing, formatting, and conversion operations.
 *
 * <p>This class provides methods to:
 *
 * <ul>
 *   <li>Parse and format DICOM DA (Date), TM (Time), and DT (DateTime) values
 *   <li>Convert between legacy Date/Calendar objects and modern LocalDate/LocalTime/LocalDateTime
 *   <li>Handle timezone-aware date/time operations
 *   <li>Parse XML DateTime values
 * </ul>
 *
 * <p>DICOM date/time formats:
 *
 * <ul>
 *   <li>DA: YYYYMMDD (with optional dots as separators for legacy support)
 *   <li>TM: HHMMSS.FFFFFF (with optional colons as separators for legacy support)
 *   <li>DT: YYYYMMDDHHMMSS.FFFFFF+ZZZZ (with optional timezone offset)
 * </ul>
 *
 * @author Gunter Zeilinger (gunterze@protonmail.com)
 * @author Nicolas Roduit
 * @since Apr 2019
 */
public final class DateTimeUtils {

  // DICOM DA (Date) formatters
  private static final DateTimeFormatter DA_PARSER =
      new DateTimeFormatterBuilder()
          .appendValue(YEAR, 4)
          .optionalStart()
          .appendLiteral('.')
          .optionalEnd()
          .appendValue(MONTH_OF_YEAR, 2)
          .optionalStart()
          .appendLiteral('.')
          .optionalEnd()
          .appendValue(DAY_OF_MONTH, 2)
          .toFormatter();

  private static final DateTimeFormatter DA_FORMATTER =
      new DateTimeFormatterBuilder()
          .appendValue(YEAR, 4)
          .appendValue(MONTH_OF_YEAR, 2)
          .appendValue(DAY_OF_MONTH, 2)
          .toFormatter();

  // DICOM TM (Time) formatters
  private static final DateTimeFormatter TM_PARSER =
      new DateTimeFormatterBuilder()
          .appendValue(HOUR_OF_DAY, 2)
          .optionalStart()
          .optionalStart()
          .appendLiteral(':')
          .optionalEnd()
          .appendValue(MINUTE_OF_HOUR, 2)
          .optionalStart()
          .optionalStart()
          .appendLiteral(':')
          .optionalEnd()
          .appendValue(SECOND_OF_MINUTE, 2)
          .optionalStart()
          .appendFraction(NANO_OF_SECOND, 0, 6, true)
          .toFormatter();

  private static final DateTimeFormatter TM_FORMATTER =
      new DateTimeFormatterBuilder()
          .appendValue(HOUR_OF_DAY, 2)
          .appendValue(MINUTE_OF_HOUR, 2)
          .appendValue(SECOND_OF_MINUTE, 2)
          .appendFraction(NANO_OF_SECOND, 6, 6, true)
          .toFormatter();

  // DICOM DT (DateTime) formatters
  private static final DateTimeFormatter DT_PARSER =
      new DateTimeFormatterBuilder()
          .appendValue(YEAR, 4)
          .optionalStart()
          .appendValue(MONTH_OF_YEAR, 2)
          .optionalStart()
          .appendValue(DAY_OF_MONTH, 2)
          .optionalStart()
          .appendValue(HOUR_OF_DAY, 2)
          .optionalStart()
          .appendValue(MINUTE_OF_HOUR, 2)
          .optionalStart()
          .appendValue(SECOND_OF_MINUTE, 2)
          .optionalStart()
          .appendFraction(NANO_OF_SECOND, 0, 6, true)
          .optionalEnd()
          .optionalEnd()
          .optionalEnd()
          .optionalEnd()
          .optionalEnd()
          .optionalEnd()
          .optionalStart()
          .appendOffset("+HHMM", "+0000")
          .toFormatter();

  private static final DateTimeFormatter DT_FORMATTER =
      new DateTimeFormatterBuilder()
          .appendValue(YEAR, 4)
          .appendValue(MONTH_OF_YEAR, 2)
          .appendValue(DAY_OF_MONTH, 2)
          .appendValue(HOUR_OF_DAY, 2)
          .appendValue(MINUTE_OF_HOUR, 2)
          .appendValue(SECOND_OF_MINUTE, 2)
          .appendFraction(NANO_OF_SECOND, 6, 6, true)
          .optionalStart()
          .appendOffset("+HHMM", "+0000")
          .toFormatter();

  // Display formatters
  private static final DateTimeFormatter DEFAULT_DATE_FORMATTER =
      DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM);
  private static final DateTimeFormatter DEFAULT_TIME_FORMATTER =
      DateTimeFormatter.ofLocalizedTime(FormatStyle.MEDIUM);
  private static final DateTimeFormatter DEFAULT_DATETIME_FORMATTER =
      DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM);

  private DateTimeUtils() {
    // Prevent instantiation
  }

  /**
   * Parses a DICOM DA (Date) value. Supports both standard format (YYYYMMDD) and legacy format with
   * dots (YYYY.MM.DD).
   *
   * @param value the date string to parse
   * @return the parsed LocalDate
   * @throws java.time.format.DateTimeParseException if the value cannot be parsed
   */
  public static LocalDate parseDA(String value) {
    return LocalDate.from(DA_PARSER.parse(value.trim()));
  }

  /**
   * Formats a temporal object to DICOM DA (Date) format (YYYYMMDD).
   *
   * @param value the temporal object to format
   * @return the formatted date string
   */
  public static String formatDA(Temporal value) {
    return DA_FORMATTER.format(value);
  }

  /**
   * Parses a DICOM TM (Time) value. Supports both standard format (HHMMSS.FFFFFF) and legacy format
   * with colons (HH:MM:SS.FFFFFF).
   *
   * @param value the time string to parse
   * @return the parsed LocalTime
   * @throws java.time.format.DateTimeParseException if the value cannot be parsed
   */
  public static LocalTime parseTM(String value) {
    return LocalTime.from(TM_PARSER.parse(value.trim()));
  }

  /**
   * Parses a DICOM TM (Time) value and returns the maximum possible time for the given precision.
   * For example, "1230" becomes "12:30:59.999999999".
   *
   * @param value the time string to parse
   * @return the parsed LocalTime with maximum nanoseconds for the given precision
   */
  public static LocalTime parseTMMax(String value) {
    return parseTM(value).plusNanos(nanosToAdd(value));
  }

  /**
   * Formats a temporal object to DICOM TM (Time) format (HHMMSS.FFFFFF).
   *
   * @param value the temporal object to format
   * @return the formatted time string
   */
  public static String formatTM(Temporal value) {
    return TM_FORMATTER.format(value);
  }

  /**
   * Parses a DICOM DT (DateTime) value. Supports various precisions from year-only to full datetime
   * with timezone offset.
   *
   * @param value the datetime string to parse
   * @return a Temporal object (LocalDateTime for values without timezone, ZonedDateTime for values
   *     with timezone)
   * @throws java.time.format.DateTimeParseException if the value cannot be parsed
   */
  public static Temporal parseDT(String value) {
    var temporal = DT_PARSER.parse(value.trim());
    var date = extractDate(temporal);
    var time = extractTime(temporal);
    var dateTime = LocalDateTime.of(date, time);
    return temporal.isSupported(OFFSET_SECONDS)
        ? ZonedDateTime.of(dateTime, ZoneOffset.ofTotalSeconds(temporal.get(OFFSET_SECONDS)))
        : dateTime;
  }

  /**
   * Parses a DICOM DT (DateTime) value and returns the maximum possible datetime for the given
   * precision. For dates without time components, returns the last nanosecond of the specified
   * period.
   *
   * @param value the datetime string to parse
   * @return a Temporal with maximum precision for the given input
   */
  public static Temporal parseDTMax(String value) {
    int length = lengthWithoutZone(value);
    return length > 8
        ? parseDT(value).plus(nanosToAdd(length - 8), ChronoUnit.NANOS)
        : parseDT(value).plus(1, yearsMonthsDays(length)).minus(1, ChronoUnit.NANOS);
  }

  /**
   * Formats a temporal object to DICOM DT (DateTime) format.
   *
   * @param value the temporal object to format
   * @return the formatted datetime string
   */
  public static String formatDT(Temporal value) {
    return DT_FORMATTER.format(value);
  }

  /**
   * Creates a LocalDateTime from separate date and time components. If time is null, returns the
   * date at start of day.
   *
   * @param date the date component (required)
   * @param time the time component (optional)
   * @return the combined LocalDateTime, or null if date is null
   */
  public static LocalDateTime dateTime(LocalDate date, LocalTime time) {
    if (date == null) {
      return null;
    }
    return time == null ? date.atStartOfDay() : LocalDateTime.of(date, time);
  }

  /**
   * Combines two DICOM tag identifiers into a single long value for composite date/time lookup.
   *
   * @param tagDate the date tag identifier
   * @param tagTime the time tag identifier
   * @return the combined tag value
   */
  public static long combineTags(int tagDate, int tagTime) {
    return ((long) tagDate << 32) | (tagTime & 0xFFFFFFFFL);
  }

  /**
   * Creates a Date object from DICOM date and time attributes.
   *
   * @param dcm the DICOM attributes
   * @param tagDate the date tag identifier
   * @param tagTime the time tag identifier
   * @return the combined Date object, or null if dcm is null
   */
  public static Date dateTime(Attributes dcm, int tagDate, int tagTime) {
    return dcm == null ? null : dcm.getDate(combineTags(tagDate, tagTime));
  }

  /**
   * Creates a Date object by combining separate date and time Date objects.
   *
   * @param tz the timezone to use (null for system default)
   * @param date the date component (null uses epoch date)
   * @param time the time component (null uses midnight)
   * @param acceptNullDateOrTime if false, returns null when either date or time is null
   * @return the combined Date object
   */
  public static Date dateTime(TimeZone tz, Date date, Date time, boolean acceptNullDateOrTime) {
    if (!acceptNullDateOrTime && (date == null || time == null)) {
      return null;
    }

    var calendar = createCalendar(tz, date);
    setDateFields(calendar, date);
    setTimeFields(calendar, time);
    return calendar.getTime();
  }

  /**
   * Truncates a DICOM TM (Time) string to the specified maximum length.
   *
   * @param value the time string to truncate
   * @param maxLength the maximum length (minimum 2)
   * @return the truncated time string
   * @throws IllegalArgumentException if maxLength is less than 2
   */
  public static String truncateTM(String value, int maxLength) {
    if (maxLength < 2) {
      throw new IllegalArgumentException("maxLength " + maxLength + " < 2");
    }
    return truncate(value, value.length(), maxLength, 8);
  }

  /**
   * Truncates a DICOM DT (DateTime) string to the specified maximum length. Preserves timezone
   * offset if present.
   *
   * @param value the datetime string to truncate
   * @param maxLength the maximum length (minimum 4)
   * @return the truncated datetime string
   * @throws IllegalArgumentException if maxLength is less than 4
   */
  public static String truncateDT(String value, int maxLength) {
    if (maxLength < 4) {
      throw new IllegalArgumentException("maxLength " + maxLength + " < 4");
    }

    int index = indexOfZone(value);
    return index < 0
        ? truncate(value, value.length(), maxLength, 16)
        : truncate(value, index, maxLength, 16) + value.substring(index);
  }

  /**
   * Formats a temporal object for display using localized medium format.
   *
   * @param date the temporal object to format
   * @return the formatted string for display
   */
  public static String formatDateTime(TemporalAccessor date) {
    return formatDateTime(date, Locale.getDefault());
  }

  /**
   * Formats a temporal object for display using the specified locale.
   *
   * @param date the temporal object to format
   * @param locale the locale to use for formatting
   * @return the formatted string for display
   */
  public static String formatDateTime(TemporalAccessor date, Locale locale) {
    if (date instanceof LocalDate) {
      return DEFAULT_DATE_FORMATTER.withLocale(locale).format(date);
    } else if (date instanceof LocalTime) {
      return DEFAULT_TIME_FORMATTER.withLocale(locale).format(date);
    } else if (date instanceof LocalDateTime) {
      return DEFAULT_DATETIME_FORMATTER.withLocale(locale).format(date);
    } else if (date instanceof ZonedDateTime) {
      return DEFAULT_DATETIME_FORMATTER.withLocale(locale).format(date);
    } else if (date instanceof Instant instant) {
      return DEFAULT_DATETIME_FORMATTER
          .withLocale(locale)
          .format((instant).atZone(ZoneId.systemDefault()));
    }
    return "";
  }

  /**
   * Converts a Date to LocalDate using the system default timezone.
   *
   * @param date the Date to convert
   * @return the LocalDate, or null if date is null
   */
  public static LocalDate toLocalDate(Date date) {
    return date == null
        ? null
        : LocalDateTime.ofInstant(date.toInstant(), ZoneId.systemDefault()).toLocalDate();
  }

  /**
   * Converts a Date to LocalTime using the system default timezone.
   *
   * @param date the Date to convert
   * @return the LocalTime, or null if date is null
   */
  public static LocalTime toLocalTime(Date date) {
    return date == null
        ? null
        : LocalDateTime.ofInstant(date.toInstant(), ZoneId.systemDefault()).toLocalTime();
  }

  /**
   * Converts a Date to LocalDateTime using the system default timezone.
   *
   * @param date the Date to convert
   * @return the LocalDateTime, or null if date is null
   */
  public static LocalDateTime toLocalDateTime(Date date) {
    return date == null ? null : LocalDateTime.ofInstant(date.toInstant(), ZoneId.systemDefault());
  }

  /**
   * Parses an XML DateTime string and returns a GregorianCalendar.
   *
   * @param s the XML DateTime string to parse
   * @return the parsed GregorianCalendar
   * @throws DatatypeConfigurationException if the factory cannot be created
   * @throws IllegalArgumentException if the input is null or empty
   */
  public static GregorianCalendar parseXmlDateTime(CharSequence s)
      throws DatatypeConfigurationException {
    if (!StringUtil.hasText(s)) {
      throw new IllegalArgumentException("Input CharSequence cannot be null or empty");
    }
    var val = s.toString().trim();
    return DatatypeFactory.newInstance().newXMLGregorianCalendar(val).toGregorianCalendar();
  }

  // Private helper methods

  private static LocalDate extractDate(TemporalAccessor temporal) {
    return temporal.isSupported(DAY_OF_MONTH)
        ? LocalDate.from(temporal)
        : LocalDate.of(temporal.get(YEAR), getMonth(temporal), 1);
  }

  private static LocalTime extractTime(TemporalAccessor temporal) {
    return temporal.isSupported(HOUR_OF_DAY) ? LocalTime.from(temporal) : LocalTime.MIN;
  }

  private static int getMonth(TemporalAccessor temporal) {
    return temporal.isSupported(MONTH_OF_YEAR) ? temporal.get(MONTH_OF_YEAR) : 1;
  }

  private static Calendar createCalendar(TimeZone tz, Date date) {
    return tz == null || date == null ? Calendar.getInstance() : Calendar.getInstance(tz);
  }

  private static void setDateFields(Calendar calendar, Date date) {
    var datePart = Calendar.getInstance();
    datePart.setTime(date == null ? new Date(0) : date);
    calendar.set(Calendar.YEAR, datePart.get(Calendar.YEAR));
    calendar.set(Calendar.MONTH, datePart.get(Calendar.MONTH));
    calendar.set(Calendar.DAY_OF_MONTH, datePart.get(Calendar.DAY_OF_MONTH));
  }

  private static void setTimeFields(Calendar calendar, Date time) {
    var timePart = Calendar.getInstance();
    timePart.setTime(time == null ? new Date(0) : time);
    calendar.set(Calendar.HOUR_OF_DAY, timePart.get(Calendar.HOUR_OF_DAY));
    calendar.set(Calendar.MINUTE, timePart.get(Calendar.MINUTE));
    calendar.set(Calendar.SECOND, timePart.get(Calendar.SECOND));
    calendar.set(Calendar.MILLISECOND, timePart.get(Calendar.MILLISECOND));
  }

  private static long nanosToAdd(String tm) {
    int length = tm.length();
    int index = tm.lastIndexOf(':');
    if (index > 0) {
      length -= (index > 4) ? 2 : 1; // Account for colons
    }
    return nanosToAdd(length);
  }

  private static long nanosToAdd(int length) {
    return switch (length) {
      case 2 -> 3599999999999L; // HH -> add to reach 23:59:59.999999999
      case 4 -> 59999999999L; // HHMM -> add to reach MM:59:59.999999999
      case 6, 7 -> 999999999L; // HHMMSS -> add to reach SS.999999999
      case 8 -> 99999999L; // HHMMSS.F
      case 9 -> 9999999L; // HHMMSS.FF
      case 10 -> 999999L; // HHMMSS.FFF
      case 11 -> 99999L; // HHMMSS.FFFF
      case 12 -> 9999L; // HHMMSS.FFFFF
      case 13 -> 999L; // HHMMSS.FFFFFF
      default -> throw new IllegalArgumentException("Unsupported length: " + length);
    };
  }

  private static ChronoUnit yearsMonthsDays(int length) {
    return switch (length) {
      case 4 -> ChronoUnit.YEARS; // YYYY
      case 6 -> ChronoUnit.MONTHS; // YYYYMM
      case 8 -> ChronoUnit.DAYS; // YYYYMMDD
      default -> throw new IllegalArgumentException("Unsupported length: " + length);
    };
  }

  private static int lengthWithoutZone(String value) {
    int index = indexOfZone(value);
    return index < 0 ? value.length() : index;
  }

  private static int indexOfZone(String value) {
    int index = value.length() - 5;
    return index >= 4 && isSign(value.charAt(index)) ? index : -1;
  }

  private static boolean isSign(char ch) {
    return ch == '+' || ch == '-';
  }

  private static String truncate(String value, int length, int maxLength, int fractionPos) {
    return value.substring(0, adjustMaxLength(Math.min(length, maxLength), fractionPos));
  }

  private static int adjustMaxLength(int maxLength, int fractionPos) {
    return maxLength < fractionPos ? maxLength & ~1 : maxLength;
  }
}
