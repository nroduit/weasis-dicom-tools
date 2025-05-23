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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.util.StringUtil;

/**
 * @author Gunter Zeilinger (gunterze@protonmail.com)
 * @since Apr 2019
 */
public class DateTimeUtils {
  private static final Logger LOGGER = LoggerFactory.getLogger(DateTimeUtils.class);

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

  public static LocalDate parseDA(String value) {
    return LocalDate.from(DA_PARSER.parse(value.trim()));
  }

  public static String formatDA(Temporal value) {
    return DA_FORMATTER.format(value);
  }

  public static LocalTime parseTM(String value) {
    return LocalTime.from(TM_PARSER.parse(value.trim()));
  }

  public static LocalTime parseTMMax(String value) {
    return parseTM(value).plusNanos(nanosToAdd(value));
  }

  public static String formatTM(Temporal value) {
    return TM_FORMATTER.format(value);
  }

  public static Temporal parseDT(String value) {
    TemporalAccessor temporal = DT_PARSER.parse(value.trim());
    LocalDate date =
        temporal.isSupported(DAY_OF_MONTH)
            ? LocalDate.from(temporal)
            : LocalDate.of(temporal.get(YEAR), getMonth(temporal), 1);
    LocalTime time = temporal.isSupported(HOUR_OF_DAY) ? LocalTime.from(temporal) : LocalTime.MIN;
    LocalDateTime dateTime = LocalDateTime.of(date, time);
    return temporal.isSupported(OFFSET_SECONDS)
        ? ZonedDateTime.of(dateTime, ZoneOffset.ofTotalSeconds(temporal.get(OFFSET_SECONDS)))
        : dateTime;
  }

  public static LocalDateTime dateTime(LocalDate date, LocalTime time) {
    if (date == null) {
      return null;
    }
    if (time == null) {
      return date.atStartOfDay();
    }
    return LocalDateTime.of(date, time);
  }

  public static long combineTags(int tagDate, int tagTime) {
    return ((long) tagDate << 32) | (tagTime & 0xFFFFFFFFL);
  }

  /**
   * Create a Date object from a date and a time dicom attributes.
   *
   * @param dcm the DICOM attributes
   * @param tagDate the date tag
   * @param tagTime the time tag
   * @return the Date object
   */
  public static Date dateTime(Attributes dcm, int tagDate, int tagTime) {
    if (dcm == null) {
      return null;
    }

    return dcm.getDate(combineTags(tagDate, tagTime));
  }

  /**
   * Create a Date object from a date and a time object.
   *
   * @param tz the time zone
   * @param date the date object
   * @param time the time object
   * @param acceptNullDateOrTime if false, return null when date or time is null
   * @return the Date object
   */
  public static Date dateTime(TimeZone tz, Date date, Date time, boolean acceptNullDateOrTime) {
    if (!acceptNullDateOrTime && (date == null || time == null)) {
      return null;
    }
    Calendar calendar =
        tz == null || date == null ? Calendar.getInstance() : Calendar.getInstance(tz);

    Calendar datePart = Calendar.getInstance();
    datePart.setTime(date == null ? new Date(0) : date);
    calendar.set(Calendar.YEAR, datePart.get(Calendar.YEAR));
    calendar.set(Calendar.MONTH, datePart.get(Calendar.MONTH));
    calendar.set(Calendar.DAY_OF_MONTH, datePart.get(Calendar.DAY_OF_MONTH));

    Calendar timePart = Calendar.getInstance();
    timePart.setTime(time == null ? new Date(0) : time);
    calendar.set(Calendar.HOUR_OF_DAY, timePart.get(Calendar.HOUR_OF_DAY));
    calendar.set(Calendar.MINUTE, timePart.get(Calendar.MINUTE));
    calendar.set(Calendar.SECOND, timePart.get(Calendar.SECOND));
    calendar.set(Calendar.MILLISECOND, timePart.get(Calendar.MILLISECOND));

    return calendar.getTime();
  }

  private static int getMonth(TemporalAccessor temporal) {
    return temporal.isSupported(MONTH_OF_YEAR) ? temporal.get(MONTH_OF_YEAR) : 1;
  }

  public static Temporal parseDTMax(String value) {
    int length = lengthWithoutZone(value);
    return length > 8
        ? parseDT(value).plus(nanosToAdd(length - 8), ChronoUnit.NANOS)
        : parseDT(value).plus(1, yearsMonthsDays(length)).minus(1, ChronoUnit.NANOS);
  }

  public static String formatDT(Temporal value) {
    return DT_FORMATTER.format(value);
  }

  public static String truncateTM(String value, int maxLength) {
    if (maxLength < 2) throw new IllegalArgumentException("maxLength %d < 2" + maxLength);

    return truncate(value, value.length(), maxLength, 8);
  }

  public static String truncateDT(String value, int maxLength) {
    if (maxLength < 4) throw new IllegalArgumentException("maxLength %d < 4" + maxLength);

    int index = indexOfZone(value);
    return index < 0
        ? truncate(value, value.length(), maxLength, 16)
        : truncate(value, index, maxLength, 16) + value.substring(index);
  }

  private static long nanosToAdd(String tm) {
    int length = tm.length();
    int index = tm.lastIndexOf(':');
    if (index > 0) {
      length--;
      if (index > 4) length--;
    }
    return nanosToAdd(length);
  }

  private static long nanosToAdd(int length) {
    return switch (length) {
      case 2 -> 3599999999999L;
      case 4 -> 59999999999L;
      case 6, 7 -> 999999999L;
      case 8 -> 99999999L;
      case 9 -> 9999999L;
      case 10 -> 999999L;
      case 11 -> 99999L;
      case 12 -> 9999L;
      case 13 -> 999L;
      default -> throw new IllegalArgumentException("length: " + length);
    };
  }

  private static ChronoUnit yearsMonthsDays(int length) {
    return switch (length) {
      case 4 -> ChronoUnit.YEARS;
      case 6 -> ChronoUnit.MONTHS;
      case 8 -> ChronoUnit.DAYS;
      default -> throw new IllegalArgumentException("length: " + length);
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

  /** Conversion from old to new Time API */
  private static final DateTimeFormatter defaultDateFormatter =
      DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM);

  private static final DateTimeFormatter defaultTimeFormatter =
      DateTimeFormatter.ofLocalizedTime(FormatStyle.MEDIUM);
  private static final DateTimeFormatter defaultDateTimeFormatter =
      DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM);

  /**
   * Convert date or time object to display date in String with FormatStyle.MEDIUM
   *
   * @param date the date or time object
   * @return the time to display with FormatStyle.MEDIUM
   */
  public static String formatDateTime(TemporalAccessor date) {
    return formatDateTime(date, Locale.getDefault());
  }

  public static String formatDateTime(TemporalAccessor date, Locale locale) {
    if (date instanceof LocalDate) {
      return defaultDateFormatter.withLocale(locale).format(date);
    } else if (date instanceof LocalTime) {
      return defaultTimeFormatter.withLocale(locale).format(date);
    } else if (date instanceof LocalDateTime || date instanceof ZonedDateTime) {
      return defaultDateTimeFormatter.withLocale(locale).format(date);
    } else if (date instanceof Instant) {
      return defaultDateTimeFormatter
          .withLocale(locale)
          .format(((Instant) date).atZone(ZoneId.systemDefault()));
    }
    return "";
  }

  public static LocalDate toLocalDate(Date date) {
    if (date != null) {
      LocalDateTime datetime = LocalDateTime.ofInstant(date.toInstant(), ZoneId.systemDefault());
      return datetime.toLocalDate();
    }
    return null;
  }

  public static LocalTime toLocalTime(Date date) {
    if (date != null) {
      LocalDateTime datetime = LocalDateTime.ofInstant(date.toInstant(), ZoneId.systemDefault());
      return datetime.toLocalTime();
    }
    return null;
  }

  public static LocalDateTime toLocalDateTime(Date date) {
    if (date != null) {
      return LocalDateTime.ofInstant(date.toInstant(), ZoneId.systemDefault());
    }
    return null;
  }

  public static GregorianCalendar parseXmlDateTime(CharSequence s)
      throws DatatypeConfigurationException {
    if (!StringUtil.hasText(s)) {
      throw new IllegalArgumentException("Input CharSequence cannot be null or empty");
    }
    String val = s.toString().trim();
    return DatatypeFactory.newInstance().newXMLGregorianCalendar(val).toGregorianCalendar();
  }
}
