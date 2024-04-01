/*
 * Copyright (c) 2024 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.util;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.TimeZone;
import javax.xml.datatype.DatatypeConfigurationException;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class DateUtilTest {

  @Test
  void getDicomDateReturnsCorrectDateForValidInput() {
    LocalDate result = DateUtil.getDicomDate("20220321");
    Assertions.assertEquals(LocalDate.of(2022, 3, 21), result);
    Assertions.assertEquals(result, DateUtil.getDicomDate("2022.03.21")); // Old format

    result = DateUtil.getDicomDate("invalid");
    Assertions.assertNull(result);
  }

  @Test
  void getDicomTimeReturnsCorrectTimeForValidInput() {
    LocalTime result = DateUtil.getDicomTime("120000");
    Assertions.assertEquals(LocalTime.of(12, 0, 0), result);

    result = DateUtil.getDicomTime("invalid");
    Assertions.assertNull(result);
  }

  @Test
  void dateTimeReturnsCorrectDateTimeForValidInput() {
    LocalDate date = LocalDate.of(2022, 1, 1);
    LocalTime time = LocalTime.of(12, 0, 0);
    Assertions.assertEquals(DateUtil.dateTime(date, time), date.atTime(time));
  }

  @Test
  void formatDicomDateReturnsCorrectFormatForValidInput() {
    LocalDate date = LocalDate.of(2022, 1, 1);
    Assertions.assertEquals("20220101", DateUtil.formatDicomDate(date));
  }

  @Test
  void formatDicomTimeReturnsCorrectFormatForValidInput() {
    LocalTime time = LocalTime.of(12, 15, 59);
    Assertions.assertEquals("121559", DateUtil.formatDicomTime(time));
  }

  @Test
  void parseXmlDateTimeReturnsCorrectDateForValidInput() throws DatatypeConfigurationException {
    TimeZone utc = TimeZone.getTimeZone("UTC");
    GregorianCalendar result = DateUtil.parseXmlDateTime("2022-03-21T12:00:00");
    result.setTimeZone(utc);
    GregorianCalendar expected = new GregorianCalendar(2022, Calendar.MARCH, 21, 12, 0, 0);
    expected.setTimeZone(utc);
    Assertions.assertEquals(expected.getTime(), result.getTime());

    utc = TimeZone.getTimeZone("UTC+1");
    result = DateUtil.parseXmlDateTime("2022-03-21T12:00:00+01:00");
    expected = new GregorianCalendar(2022, Calendar.MARCH, 21, 11, 0, 0);
    expected.setTimeZone(utc);
    Assertions.assertEquals(expected.getTime(), result.getTime());
  }
}
