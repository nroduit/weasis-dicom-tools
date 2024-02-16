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

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.Date;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.VR;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class DicomUtilsTest {

  @Test
  void testGetPeriod() {
    assertEquals(
        "050Y",
        DicomUtils.getPeriod(
            DateTimeUtils.parseDA("19610625"), DateTimeUtils.parseDA("20120624"))); // NON-NLS
    assertEquals(
        "051Y",
        DicomUtils.getPeriod(
            DateTimeUtils.parseDA("19610625"), DateTimeUtils.parseDA("20120625"))); // NON-NLS
    assertEquals(
        "050Y",
        DicomUtils.getPeriod(
            DateTimeUtils.parseDA("19610714"), DateTimeUtils.parseDA("20120625"))); // NON-NLS

    assertEquals(
        "005M",
        DicomUtils.getPeriod(
            DateTimeUtils.parseDA("20120103"), DateTimeUtils.parseDA("20120625"))); // NON-NLS
    assertEquals(
        "031D",
        DicomUtils.getPeriod(
            DateTimeUtils.parseDA("20120525"), DateTimeUtils.parseDA("20120625"))); // NON-NLS
    assertEquals(
        "003D",
        DicomUtils.getPeriod(
            DateTimeUtils.parseDA("20120622"), DateTimeUtils.parseDA("20120625"))); // NON-NLS

    assertEquals(
        "011Y",
        DicomUtils.getPeriod(
            DateTimeUtils.parseDA("20000229"), DateTimeUtils.parseDA("20110301"))); // NON-NLS
    assertEquals(
        "010Y",
        DicomUtils.getPeriod(
            DateTimeUtils.parseDA("20000229"), DateTimeUtils.parseDA("20110228"))); // NON-NLS
    assertEquals(
        "011Y",
        DicomUtils.getPeriod(
            DateTimeUtils.parseDA("20000229"), DateTimeUtils.parseDA("20120228"))); // NON-NLS
    assertEquals(
        "012Y",
        DicomUtils.getPeriod(
            DateTimeUtils.parseDA("20000229"), DateTimeUtils.parseDA("20120229"))); // NON-NLS
    assertEquals(
        "012Y",
        DicomUtils.getPeriod(
            DateTimeUtils.parseDA("20000229"), DateTimeUtils.parseDA("20120301"))); // NON-NLS
    assertEquals(
        "012Y",
        DicomUtils.getPeriod(
            DateTimeUtils.parseDA("20000228"), DateTimeUtils.parseDA("20120228"))); // NON-NLS
    assertEquals(
        "012Y",
        DicomUtils.getPeriod(
            DateTimeUtils.parseDA("20000228"), DateTimeUtils.parseDA("20120229"))); // NON-NLS

    LocalDate date1 = DateTimeUtils.parseDA("20000228");

    Assertions.assertThrows(
        DateTimeParseException.class,
        () -> {
          DateTimeUtils.parseDA("20122406"); // invalid => null
        });
    Assertions.assertThrows(
        NullPointerException.class,
        () -> {
          DicomUtils.getPeriod(date1, null);
        });
  }

  @Test
  void testGetStringFromDicomElement() {
    String[] STRING_ARRAY = {"RECTANGULAR", "CIRCULAR", "POLYGONAL"};
    Attributes attributes = new Attributes();
    attributes.setString(Tag.ShutterShape, VR.CS, STRING_ARRAY);
    assertEquals(
        "RECTANGULAR\\CIRCULAR\\POLYGONAL",
        DicomUtils.getStringFromDicomElement(attributes, Tag.ShutterShape)); // NON-NLS
    assertNull(DicomUtils.getStringFromDicomElement(attributes, Tag.ShutterPresentationValue));
  }

  @Test
  void testGetStringArrayFromDicomElementAttributesInt() {
    String[] STRING_ARRAY = {"RECTANGULAR", "CIRCULAR", "POLYGONAL"};
    Attributes attributes = new Attributes();
    attributes.setString(Tag.ShutterShape, VR.CS, STRING_ARRAY);
    assertEquals(
        STRING_ARRAY, DicomUtils.getStringArrayFromDicomElement(attributes, Tag.ShutterShape));
    assertNull(DicomUtils.getStringArrayFromDicomElement(attributes, Tag.ShutterPresentationValue));
  }

  /** Method under test: {@link DicomUtils#formatValue(String, boolean, String)} */
  @Test
  void testFormatValue() {
    // Arrange, Act and Assert
    assertEquals("42", DicomUtils.formatValue("42", true, "Format"));
    assertEquals("42", DicomUtils.formatValue("42", true, "$V"));
  }

  /** Method under test: {@link DicomUtils#isVideo(String)} */
  @Test
  void testIsVideo() {
    // Arrange, Act and Assert
    assertFalse(DicomUtils.isVideo("1234"));
    assertTrue(DicomUtils.isVideo((String) "1.2.840.10008.1.2.4.100"));
    assertTrue(DicomUtils.isVideo((String) "1.2.840.10008.1.2.4.101"));
    assertTrue(DicomUtils.isVideo((String) "1.2.840.10008.1.2.4.102"));
    assertTrue(DicomUtils.isVideo((String) "1.2.840.10008.1.2.4.103"));
    assertTrue(DicomUtils.isVideo((String) "1.2.840.10008.1.2.4.104"));
    assertTrue(DicomUtils.isVideo((String) "1.2.840.10008.1.2.4.105"));
    assertTrue(DicomUtils.isVideo((String) "1.2.840.10008.1.2.4.106"));
    assertTrue(DicomUtils.isVideo((String) "1.2.840.10008.1.2.4.107"));
    assertTrue(DicomUtils.isVideo((String) "1.2.840.10008.1.2.4.108"));
  }

  /** Method under test: {@link DicomUtils#isJpeg2000(String)} */
  @Test
  void testIsJpeg2000() {
    // Arrange, Act and Assert
    assertFalse(DicomUtils.isJpeg2000("1234"));
    assertTrue(DicomUtils.isJpeg2000((String) "1.2.840.10008.1.2.4.90"));
    assertTrue(DicomUtils.isJpeg2000((String) "1.2.840.10008.1.2.4.91"));
    assertTrue(DicomUtils.isJpeg2000((String) "1.2.840.10008.1.2.4.92"));
    assertTrue(DicomUtils.isJpeg2000((String) "1.2.840.10008.1.2.4.93"));
  }

  /** Method under test: {@link DicomUtils#isNative(String)} */
  @Test
  void testIsNative() {
    // Arrange, Act and Assert
    assertFalse(DicomUtils.isNative("1234"));
    assertTrue(DicomUtils.isNative((String) "1.2.840.10008.1.2"));
    assertTrue(DicomUtils.isNative((String) "1.2.840.10008.1.2.1"));
    assertTrue(DicomUtils.isNative((String) "1.2.840.10008.1.2.2"));
  }

  /** Method under test: {@link DicomUtils#getFormattedText(Object, String)} */
  @Test
  void testGetFormattedText() {
    // Arrange, Act and Assert
    assertEquals("Value", DicomUtils.getFormattedText("Value", "Format"));
    assertEquals("", DicomUtils.getFormattedText(null, "foo"));
    assertEquals("42", DicomUtils.getFormattedText("42", "foo"));
    assertEquals("42", DicomUtils.getFormattedText(42, "Format"));
    assertEquals("10.0", DicomUtils.getFormattedText(10.0f, "Format"));
    assertEquals("10.0", DicomUtils.getFormattedText(10.0d, "Format"));
    assertEquals("Value", DicomUtils.getFormattedText("Value", "$V"));
    assertEquals("Value", DicomUtils.getFormattedText("Value", null));
    assertEquals("Value", DicomUtils.getFormattedText("Value", ""));
    assertEquals("1 janv. 1970", DicomUtils.getFormattedText(LocalDate.of(1970, 1, 1), "Format"));
    assertEquals(
        "1 janv. 1970, 00:00:00",
        DicomUtils.getFormattedText(LocalDate.of(1970, 1, 1).atStartOfDay(), "Format"));
    assertEquals("00:00:00", DicomUtils.getFormattedText(LocalTime.MIDNIGHT, "Format"));
    assertEquals("", DicomUtils.getFormattedText(DayOfWeek.MONDAY, "Format"));
    assertEquals(
        "1 janv. 1970, 00:00:00",
        DicomUtils.getFormattedText(
            LocalDate.of(1970, 1, 1).atStartOfDay().atZone(ZoneOffset.UTC), "Format"));
  }

  /**
   * Method under test: {@link DicomUtils#getStringArrayFromDicomElement(Attributes, int, String)}
   */
  @Test
  void testGetStringArrayFromDicomElement() {
    // Arrange, Act and Assert
    assertNull(
        DicomUtils.getStringArrayFromDicomElement(new Attributes(), 1, "Private Creator ID"));
    assertNull(DicomUtils.getStringArrayFromDicomElement(null, 1, (String) null));
    assertArrayEquals(
        new String[] {"42"},
        DicomUtils.getStringArrayFromDicomElement(
            new Attributes(), 1, "Private Creator ID", new String[] {"42"}));
    assertArrayEquals(
        new String[] {"foo"},
        DicomUtils.getStringArrayFromDicomElement(null, 1, null, new String[] {"foo"}));
    assertArrayEquals(
        new String[] {"42"},
        DicomUtils.getStringArrayFromDicomElement(new Attributes(), 1, new String[] {"42"}));
    assertArrayEquals(
        new String[] {"foo"},
        DicomUtils.getStringArrayFromDicomElement(null, 1, new String[] {"foo"}));
  }

  /** Method under test: {@link DicomUtils#getDateFromDicomElement(Attributes, int, Date)} */
  @Test
  void testGetDateFromDicomElement() {
    // Arrange
    Attributes dicom = mock(Attributes.class);
    when(dicom.getDate(anyInt(), Mockito.<Date>any()))
        .thenReturn(
            Date.from(LocalDate.of(1970, 1, 1).atStartOfDay().atZone(ZoneOffset.UTC).toInstant()));
    when(dicom.containsValue(anyInt())).thenReturn(true);

    // Act
    DicomUtils.getDateFromDicomElement(
        dicom,
        1,
        Date.from(LocalDate.of(1970, 1, 1).atStartOfDay().atZone(ZoneOffset.UTC).toInstant()));

    // Assert
    verify(dicom).containsValue(anyInt());
    verify(dicom).getDate(anyInt(), Mockito.<Date>any());
  }

  /** Method under test: {@link DicomUtils#getDateFromDicomElement(Attributes, int, Date)} */
  @Test
  void testGetDateFromDicomElement2() {
    // Arrange
    Attributes dicom = mock(Attributes.class);
    when(dicom.getDate(anyInt(), Mockito.<Date>any())).thenThrow(new NumberFormatException("foo"));
    when(dicom.containsValue(anyInt())).thenReturn(true);

    // Act and Assert
    assertThrows(
        NumberFormatException.class,
        () ->
            DicomUtils.getDateFromDicomElement(
                dicom,
                1,
                Date.from(
                    LocalDate.of(1970, 1, 1).atStartOfDay().atZone(ZoneOffset.UTC).toInstant())));
    verify(dicom).containsValue(anyInt());
    verify(dicom).getDate(anyInt(), Mockito.<Date>any());
  }

  /**
   * Method under test: {@link DicomUtils#getDatesFromDicomElement(Attributes, int, String, Date[])}
   */
  @Test
  void testGetDatesFromDicomElement() {
    // Arrange
    Attributes dicom = new Attributes();
    Date fromResult =
        Date.from(LocalDate.of(1970, 1, 1).atStartOfDay().atZone(ZoneOffset.UTC).toInstant());

    // Act
    Date[] actualDatesFromDicomElement =
        DicomUtils.getDatesFromDicomElement(
            dicom, 1, "Private Creator ID", new Date[] {fromResult});

    // Assert
    assertEquals(1, actualDatesFromDicomElement.length);
    assertSame(fromResult, actualDatesFromDicomElement[0]);
  }

  /**
   * Method under test: {@link DicomUtils#getDatesFromDicomElement(Attributes, int, String, Date[])}
   */
  @Test
  void testGetDatesFromDicomElement2() {
    // Arrange
    Date fromResult =
        Date.from(LocalDate.of(1970, 1, 1).atStartOfDay().atZone(ZoneOffset.UTC).toInstant());

    // Act
    Date[] actualDatesFromDicomElement =
        DicomUtils.getDatesFromDicomElement(null, 1, null, new Date[] {fromResult});

    // Assert
    assertEquals(1, actualDatesFromDicomElement.length);
    assertSame(fromResult, actualDatesFromDicomElement[0]);
  }

  /**
   * Method under test: {@link DicomUtils#getDatesFromDicomElement(Attributes, int, String,
   * java.util.Date[])}
   */
  @Test
  void testGetDatesFromDicomElement3() {
    // Arrange, Act and Assert
    assertEquals(
        1,
        DicomUtils.getDatesFromDicomElement(
                new Attributes(),
                1,
                "Private Creator ID",
                new java.util.Date[] {mock(java.sql.Date.class)})
            .length);
  }

  /**
   * Method under test: {@link DicomUtils#getPatientAgeInPeriod(Attributes, int, String, String,
   * boolean)}
   */
  @Test
  void testGetPatientAgeInPeriod() {
    // Arrange, Act and Assert
    assertEquals("foo", DicomUtils.getPatientAgeInPeriod(null, 1, "foo", "foo", false));
    assertNull(DicomUtils.getPatientAgeInPeriod(new Attributes(), 1, "foo", "foo", false));
    assertEquals(
        "42",
        DicomUtils.getPatientAgeInPeriod(
            new Attributes(), 65536, "Private Creator ID", "42", true));
    assertEquals(
        "42",
        DicomUtils.getPatientAgeInPeriod(
            Attributes.createFileMetaInformation("1234", "1234", "1234", true),
            65536,
            "Private Creator ID",
            "42",
            true));
    assertNull(
        DicomUtils.getPatientAgeInPeriod(
            new Attributes(), 65536, "Private Creator ID", null, true));
    assertNull(
        DicomUtils.getPatientAgeInPeriod(new Attributes(), 65536, "Private Creator ID", "", true));
    assertNull(DicomUtils.getPatientAgeInPeriod(new Attributes(), 1, true));
    assertNull(DicomUtils.getPatientAgeInPeriod(null, 1, false));
    assertNull(DicomUtils.getPatientAgeInPeriod(new Attributes(), 1, false));
  }

  /** Method under test: {@link DicomUtils#getPeriod(LocalDate, LocalDate)} */
  @Test
  void testGetPeriod2() {
    // Arrange
    LocalDate first = LocalDate.ofYearDay(2, 2);

    // Act and Assert
    assertEquals("1967Y", DicomUtils.getPeriod(first, LocalDate.of(1970, 1, 1)));
  }

  /** Method under test: {@link DicomUtils#getFloatFromDicomElement(Attributes, int, Float)} */
  @Test
  void testGetFloatFromDicomElement() {
    // Arrange, Act and Assert
    assertEquals(
        10.0f, DicomUtils.getFloatFromDicomElement(new Attributes(), 1, 10.0f).floatValue());
    assertEquals(10.0f, DicomUtils.getFloatFromDicomElement(null, 1, 10.0f).floatValue());
    assertEquals(
        10.0f,
        DicomUtils.getFloatFromDicomElement(new Attributes(), 1, "Private Creator ID", 10.0f)
            .floatValue());
    assertEquals(
        10.0f,
        DicomUtils.getFloatFromDicomElement(null, 1, "Private Creator ID", 10.0f).floatValue());
  }

  /** Method under test: {@link DicomUtils#getIntegerFromDicomElement(Attributes, int, Integer)} */
  @Test
  void testGetIntegerFromDicomElement() {
    // Arrange, Act and Assert
    assertEquals(42, DicomUtils.getIntegerFromDicomElement(new Attributes(), 1, 42).intValue());
    assertEquals(42, DicomUtils.getIntegerFromDicomElement(null, 1, 42).intValue());
    assertEquals(
        42,
        DicomUtils.getIntegerFromDicomElement(new Attributes(), 1, "Private Creator ID", 42)
            .intValue());
    assertEquals(
        42, DicomUtils.getIntegerFromDicomElement(null, 1, "Private Creator ID", 42).intValue());
  }

  /** Method under test: {@link DicomUtils#getLongFromDicomElement(Attributes, int, Long)} */
  @Test
  void testGetLongFromDicomElement() {
    // Arrange, Act and Assert
    assertEquals(42L, DicomUtils.getLongFromDicomElement(new Attributes(), 1, 42L).longValue());
    assertEquals(42L, DicomUtils.getLongFromDicomElement(null, 1, 42L).longValue());
    assertEquals(
        42L,
        DicomUtils.getLongFromDicomElement(new Attributes(), 1, "Private Creator ID", 42L)
            .longValue());
    assertEquals(
        42L, DicomUtils.getLongFromDicomElement(null, 1, "Private Creator ID", 42L).longValue());
  }

  /** Method under test: {@link DicomUtils#getDoubleFromDicomElement(Attributes, int, Double)} */
  @Test
  void testGetDoubleFromDicomElement() {
    // Arrange, Act and Assert
    assertEquals(
        10.0d, DicomUtils.getDoubleFromDicomElement(new Attributes(), 1, 10.0d).doubleValue());
    assertEquals(10.0d, DicomUtils.getDoubleFromDicomElement(null, 1, 10.0d).doubleValue());
    assertEquals(
        10.0d,
        DicomUtils.getDoubleFromDicomElement(new Attributes(), 1, "Private Creator ID", 10.0d)
            .doubleValue());
    assertEquals(
        10.0d,
        DicomUtils.getDoubleFromDicomElement(null, 1, "Private Creator ID", 10.0d).doubleValue());
  }

  /**
   * Method under test: {@link DicomUtils#getIntArrayFromDicomElement(Attributes, int, String,
   * int[])}
   */
  @Test
  void testGetIntArrayFromDicomElement() {
    // Arrange, Act and Assert
    assertArrayEquals(
        new int[] {42, 1, 42, 1},
        DicomUtils.getIntArrayFromDicomElement(
            new Attributes(), 1, "Private Creator ID", new int[] {42, 1, 42, 1}));
    assertArrayEquals(
        new int[] {42, 1, 42, 1},
        DicomUtils.getIntArrayFromDicomElement(
            null, 1, "Private Creator ID", new int[] {42, 1, 42, 1}));
    assertArrayEquals(
        new int[] {42, 1, 42, 1},
        DicomUtils.getIntArrayFromDicomElement(new Attributes(), 1, new int[] {42, 1, 42, 1}));
    assertArrayEquals(
        new int[] {42, 1, 42, 1},
        DicomUtils.getIntArrayFromDicomElement(null, 1, new int[] {42, 1, 42, 1}));
  }

  /**
   * Method under test: {@link DicomUtils#getFloatArrayFromDicomElement(Attributes, int, String,
   * float[])}
   */
  @Test
  void testGetFloatArrayFromDicomElement() {
    // Arrange, Act and Assert
    assertArrayEquals(
        new float[] {10.0f, 0.5f, 10.0f, 0.5f},
        DicomUtils.getFloatArrayFromDicomElement(
            new Attributes(), 1, "Private Creator ID", new float[] {10.0f, 0.5f, 10.0f, 0.5f}),
        0.0f);
    assertArrayEquals(
        new float[] {10.0f, 0.5f, 10.0f, 0.5f},
        DicomUtils.getFloatArrayFromDicomElement(
            null, 1, "Private Creator ID", new float[] {10.0f, 0.5f, 10.0f, 0.5f}),
        0.0f);
    assertArrayEquals(
        new float[] {10.0f, 0.5f, 10.0f, 0.5f},
        DicomUtils.getFloatArrayFromDicomElement(
            new Attributes(), 1, new float[] {10.0f, 0.5f, 10.0f, 0.5f}),
        0.0f);
    assertArrayEquals(
        new float[] {10.0f, 0.5f, 10.0f, 0.5f},
        DicomUtils.getFloatArrayFromDicomElement(null, 1, new float[] {10.0f, 0.5f, 10.0f, 0.5f}),
        0.0f);
  }

  /**
   * Method under test: {@link DicomUtils#getDoubleArrayFromDicomElement(Attributes, int, String,
   * double[])}
   */
  @Test
  void testGetDoubleArrayFromDicomElement() {
    // Arrange, Act and Assert
    assertArrayEquals(
        new double[] {10.0d, 0.5d, 10.0d, 0.5d},
        DicomUtils.getDoubleArrayFromDicomElement(
            new Attributes(), 1, "Private Creator ID", new double[] {10.0d, 0.5d, 10.0d, 0.5d}),
        0.0);
    assertArrayEquals(
        new double[] {10.0d, 0.5d, 10.0d, 0.5d},
        DicomUtils.getDoubleArrayFromDicomElement(
            null, 1, "Private Creator ID", new double[] {10.0d, 0.5d, 10.0d, 0.5d}),
        0.0);
    assertArrayEquals(
        new double[] {10.0d, 0.5d, 10.0d, 0.5d},
        DicomUtils.getDoubleArrayFromDicomElement(
            new Attributes(), 1, new double[] {10.0d, 0.5d, 10.0d, 0.5d}),
        0.0);
    assertArrayEquals(
        new double[] {10.0d, 0.5d, 10.0d, 0.5d},
        DicomUtils.getDoubleArrayFromDicomElement(null, 1, new double[] {10.0d, 0.5d, 10.0d, 0.5d}),
        0.0);
  }
}
