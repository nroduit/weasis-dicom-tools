/*
 * Copyright (c) 2017-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.util;

import static java.time.temporal.ChronoField.DAY_OF_MONTH;
import static java.time.temporal.ChronoField.HOUR_OF_DAY;
import static java.time.temporal.ChronoField.MINUTE_OF_HOUR;
import static java.time.temporal.ChronoField.MONTH_OF_YEAR;
import static java.time.temporal.ChronoField.SECOND_OF_MINUTE;
import static java.time.temporal.ChronoField.YEAR;

import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.temporal.ChronoField;
import java.util.GregorianCalendar;
import javax.xml.datatype.DatatypeConfigurationException;
import javax.xml.datatype.DatatypeFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.util.StringUtil;

/** Code from org.weasis.dicom.codec.TagD */
public class DateUtil {
  private static final Logger LOGGER = LoggerFactory.getLogger(DateUtil.class);

  static final DateTimeFormatter DICOM_DATE =
      new DateTimeFormatterBuilder()
          .appendValue(YEAR, 4)
          .appendValue(MONTH_OF_YEAR, 2)
          .appendValue(DAY_OF_MONTH, 2)
          .toFormatter();
  static final DateTimeFormatter DICOM_TIME =
      new DateTimeFormatterBuilder()
          .appendValue(HOUR_OF_DAY, 2)
          .optionalStart()
          .appendValue(MINUTE_OF_HOUR, 2)
          .optionalStart()
          .appendValue(SECOND_OF_MINUTE, 2)
          .appendFraction(ChronoField.MICRO_OF_SECOND, 0, 6, true)
          .toFormatter();

  private DateUtil() {}

  public static LocalDate getDicomDate(String date) {
    if (StringUtil.hasText(date)) {
      try {
        if (date.length() > 8) {
          StringBuilder buf = new StringBuilder(8);
          // Try to fix old format yyyy.mm.dd (prior DICOM3.0)
          date.chars().filter(Character::isDigit).forEachOrdered(i -> buf.append((char) i));
          return LocalDate.parse(buf.toString().trim(), DICOM_DATE);
        }
        return LocalDate.parse(date, DICOM_DATE);
      } catch (Exception e) {
        LOGGER.error("Parse DICOM date", e); // $NON-NLS-1$
      }
    }
    return null;
  }

  public static LocalTime getDicomTime(String time) {
    if (StringUtil.hasText(time)) {
      try {
        return LocalTime.parse(time.trim(), DICOM_TIME);
      } catch (Exception e) {
        try {
          StringBuilder buf = new StringBuilder(8);
          // Try to fix old format HH:MM:SS.frac (prior DICOM3.0)
          time.chars().filter(i -> ':' != (char) i).forEachOrdered(i -> buf.append((char) i));
          return LocalTime.parse(buf.toString().trim(), DICOM_TIME);
        } catch (Exception e1) {
          LOGGER.error("Parse DICOM time", e1); // $NON-NLS-1$
        }
      }
    }
    return null;
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

  public static String formatDicomDate(LocalDate date) {
    if (date != null) {
      try {
        return DICOM_DATE.format(date);
      } catch (DateTimeException e) {
        LOGGER.error("Format date", e); // $NON-NLS-1$
      }
    }
    return StringUtil.EMPTY_STRING;
  }

  public static String formatDicomTime(LocalTime time) {
    if (time != null) {
      try {
        return DICOM_TIME.format(time);
      } catch (DateTimeException e) {
        LOGGER.error("Format time", e); // $NON-NLS-1$
      }
    }
    return StringUtil.EMPTY_STRING;
  }

  public static GregorianCalendar parseXmlDateTime(CharSequence s)
      throws DatatypeConfigurationException {
    String val = s.toString().trim();
    return DatatypeFactory.newInstance().newXMLGregorianCalendar(val).toGregorianCalendar();
  }
}
