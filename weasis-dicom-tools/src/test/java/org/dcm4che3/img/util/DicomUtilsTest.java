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
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.time.temporal.TemporalAccessor;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.VR;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junitpioneer.jupiter.DefaultLocale;
import org.junitpioneer.jupiter.DefaultTimeZone;

@DefaultLocale(language = "en", country = "US")
class DicomUtilsTest {

  @Test
  @DisplayName("Get Period with different dates")
  void testGetPeriod() {
    assertEquals(
        "1967Y", DicomUtils.getPeriod(LocalDate.ofYearDay(2, 2), LocalDate.of(1970, 1, 1)));
    assertEquals(
        "050Y",
        DicomUtils.getPeriod(DateTimeUtils.parseDA("19610625"), DateTimeUtils.parseDA("20120624")));
    assertEquals(
        "051Y",
        DicomUtils.getPeriod(DateTimeUtils.parseDA("19610625"), DateTimeUtils.parseDA("20120625")));
    assertEquals(
        "050Y",
        DicomUtils.getPeriod(DateTimeUtils.parseDA("19610714"), DateTimeUtils.parseDA("20120625")));

    assertEquals(
        "005M",
        DicomUtils.getPeriod(DateTimeUtils.parseDA("20120103"), DateTimeUtils.parseDA("20120625")));
    assertEquals(
        "031D",
        DicomUtils.getPeriod(DateTimeUtils.parseDA("20120525"), DateTimeUtils.parseDA("20120625")));
    assertEquals(
        "003D",
        DicomUtils.getPeriod(DateTimeUtils.parseDA("20120622"), DateTimeUtils.parseDA("20120625")));

    assertEquals(
        "011Y",
        DicomUtils.getPeriod(DateTimeUtils.parseDA("20000229"), DateTimeUtils.parseDA("20110301")));
    assertEquals(
        "010Y",
        DicomUtils.getPeriod(DateTimeUtils.parseDA("20000229"), DateTimeUtils.parseDA("20110228")));
    assertEquals(
        "011Y",
        DicomUtils.getPeriod(DateTimeUtils.parseDA("20000229"), DateTimeUtils.parseDA("20120228")));
    assertEquals(
        "012Y",
        DicomUtils.getPeriod(DateTimeUtils.parseDA("20000229"), DateTimeUtils.parseDA("20120229")));
    assertEquals(
        "012Y",
        DicomUtils.getPeriod(DateTimeUtils.parseDA("20000229"), DateTimeUtils.parseDA("20120301")));
    assertEquals(
        "012Y",
        DicomUtils.getPeriod(DateTimeUtils.parseDA("20000228"), DateTimeUtils.parseDA("20120228")));
    assertEquals(
        "012Y",
        DicomUtils.getPeriod(DateTimeUtils.parseDA("20000228"), DateTimeUtils.parseDA("20120229")));

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
  @DisplayName("Is a video transfer syntax")
  void testIsVideo() {
    assertFalse(DicomUtils.isVideo("1234"));
    assertFalse(DicomUtils.isVideo("1.2.840.10008.1.2.5"));

    assertTrue(DicomUtils.isVideo("1.2.840.10008.1.2.4.100"));
    assertTrue(DicomUtils.isVideo("1.2.840.10008.1.2.4.101"));
    assertTrue(DicomUtils.isVideo("1.2.840.10008.1.2.4.102"));
    assertTrue(DicomUtils.isVideo("1.2.840.10008.1.2.4.103"));
    assertTrue(DicomUtils.isVideo("1.2.840.10008.1.2.4.104"));
    assertTrue(DicomUtils.isVideo("1.2.840.10008.1.2.4.105"));
    assertTrue(DicomUtils.isVideo("1.2.840.10008.1.2.4.106"));
    assertTrue(DicomUtils.isVideo("1.2.840.10008.1.2.4.107"));
    assertTrue(DicomUtils.isVideo("1.2.840.10008.1.2.4.108"));
  }

  @Test
  @DisplayName("Is a jpeg2000 transfer syntax")
  void testIsJpeg2000() {
    assertFalse(DicomUtils.isJpeg2000("1234"));
    assertFalse(DicomUtils.isJpeg2000("1.2.840.10008.1.2.5"));

    assertTrue(DicomUtils.isJpeg2000("1.2.840.10008.1.2.4.90"));
    assertTrue(DicomUtils.isJpeg2000("1.2.840.10008.1.2.4.91"));
    assertTrue(DicomUtils.isJpeg2000("1.2.840.10008.1.2.4.92"));
    assertTrue(DicomUtils.isJpeg2000("1.2.840.10008.1.2.4.93"));
    assertTrue(DicomUtils.isJpeg2000("1.2.840.10008.1.2.4.201"));
    assertTrue(DicomUtils.isJpeg2000("1.2.840.10008.1.2.4.202"));
    assertTrue(DicomUtils.isJpeg2000("1.2.840.10008.1.2.4.203"));
  }

  @Test
  @DisplayName("Is a native transfer syntax")
  void testIsNative() {
    // Arrange, Act and Assert
    assertFalse(DicomUtils.isNative("1234"));
    assertFalse(DicomUtils.isNative("1.2.840.10008.1.3"));

    assertTrue(DicomUtils.isNative("1.2.840.10008.1.2"));
    assertTrue(DicomUtils.isNative("1.2.840.10008.1.2.1"));
    assertTrue(DicomUtils.isNative("1.2.840.10008.1.2.2"));
  }

  @Test
  @DisplayName("Format text with different values and formats")
  void testGetFormattedText() {
    assertEquals("Value", DicomUtils.getFormattedText("Value", "Format"));
    assertEquals("", DicomUtils.getFormattedText(null, "foo"));
    assertEquals("42", DicomUtils.getFormattedText("42", "foo"));
    assertEquals("42", DicomUtils.getFormattedText(42, "Format"));
    assertEquals("10.0", DicomUtils.getFormattedText(10.0f, "Format"));
    assertEquals("10.0", DicomUtils.getFormattedText(10.0d, "Format"));
    assertEquals("Value", DicomUtils.getFormattedText("Value", "$V"));
    assertEquals("Value", DicomUtils.getFormattedText("Value", null));
    assertEquals("Value", DicomUtils.getFormattedText("Value", ""));
    assertEquals(
        "Test1\\Test2", DicomUtils.getFormattedText(new String[] {"Test1", "Test2"}, "Format"));

    assertEquals("1, 2", DicomUtils.getFormattedText(new int[] {1, 2}, "Format"));
    assertEquals("1.0, 2.0", DicomUtils.getFormattedText(new float[] {1.0f, 2.0f}, "Format"));
    assertEquals("1.0, 2.0", DicomUtils.getFormattedText(new double[] {1.0, 2.0}, "Format"));

    // Replace non-breaking spaces with regular spaces before comparing
    TemporalAccessor[] temporalAccessors = {LocalDate.of(2022, 12, 25), LocalDate.of(2022, 12, 26)};
    assertEquals(
        "Dec 25, 2022, Dec 26, 2022",
        DicomUtils.getFormattedText(temporalAccessors, "Format").replace(" ", " "));
    assertEquals(
        "Jan 1, 1970",
        DicomUtils.getFormattedText(LocalDate.of(1970, 1, 1), "Format").replace(" ", " "));
    assertEquals(
        "Jan 1, 1970, 12:00:00 AM",
        DicomUtils.getFormattedText(LocalDate.of(1970, 1, 1).atStartOfDay(), "Format", Locale.US)
            .replace(" ", " "));
    assertEquals(
        "12:00:00 AM",
        DicomUtils.getFormattedText(LocalTime.MIDNIGHT, "Format", Locale.US).replace(" ", " "));
    assertEquals("", DicomUtils.getFormattedText(DayOfWeek.MONDAY, "Format").replace(" ", " "));
    assertEquals(
        "Jan 1, 1970, 12:00:00 AM",
        DicomUtils.getFormattedText(
                LocalDate.of(1970, 1, 1).atStartOfDay().atZone(ZoneOffset.UTC), "Format", Locale.US)
            .replace(" ", " "));
  }

  @Test
  @DisplayName("Format text with non-string value")
  void shouldFormatNonStringValue() {
    Object object = new Object();
    String result = DicomUtils.getFormattedText(object, "Format");
    assertEquals(object.toString(), result);
  }

  @Test
  @DisplayName("Format value with decimal and pattern")
  void shouldFormatValueWithDecimalAndPattern() {
    String result = DicomUtils.formatValue("1.2345", true, "$V:f$0.00$");
    assertEquals("1.23", result);
  }

  @Test
  @DisplayName("Format value with non-decimal and pattern")
  void shouldFormatValueWithNonDecimalAndPattern() {
    String result = DicomUtils.formatValue("TestTestTest", false, "$V:l$5$");
    assertEquals("TestT...", result);
  }

  @Test
  @DisplayName("Get string and string array from Dicom Element containing multiple values")
  void testGetStringFromDicomElement() {
    String[] STRING_ARRAY = {"RECTANGULAR", "CIRCULAR", "POLYGONAL"};
    Attributes attributes = new Attributes();
    attributes.setString(Tag.ShutterShape, VR.CS, STRING_ARRAY);
    assertEquals(
        "RECTANGULAR\\CIRCULAR\\POLYGONAL",
        DicomUtils.getStringFromDicomElement(attributes, Tag.ShutterShape));

    assertEquals(
        STRING_ARRAY, DicomUtils.getStringArrayFromDicomElement(attributes, Tag.ShutterShape));
  }

  @Test
  @DisplayName("Get String array from Dicom Element with default value")
  void testGetStringArrayFromDicomElement() {
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

  @Test
  @DisplayName("Get Date from Dicom Element")
  @DefaultTimeZone("Europe/Paris")
  void testGetDateFromDicomElement() {
    TimeZone timeZone = TimeZone.getDefault();
    Attributes attributes = new Attributes();
    attributes.setTimezone(timeZone);

    Calendar calendarA = Calendar.getInstance(timeZone);
    calendarA.set(2024, Calendar.JUNE, 21, 0, 0, 0);
    calendarA.set(Calendar.MILLISECOND, 0);
    Date date = calendarA.getTime();

    Date defaultDate =
        Date.from(LocalDate.of(1970, 1, 1).atStartOfDay().atZone(ZoneOffset.UTC).toInstant());
    assertNull(DicomUtils.getDateFromDicomElement(null, Tag.StudyDate, null));
    assertEquals(defaultDate, DicomUtils.getDateFromDicomElement(null, Tag.StudyDate, defaultDate));
    assertEquals(
        defaultDate, DicomUtils.getDateFromDicomElement(attributes, Tag.StudyDate, defaultDate));

    attributes.setDate(Tag.StudyDate, VR.DA, date);
    assertEquals(date, DicomUtils.getDateFromDicomElement(attributes, Tag.StudyDate, null));
  }

  @Test
  @DisplayName("Get date array from Dicom Element with default value")
  @DefaultTimeZone("Europe/Paris")
  void testGetDatesFromDicomElement() {
    TimeZone timeZone = TimeZone.getDefault();
    Attributes dicom = new Attributes();
    dicom.setTimezone(timeZone);
    Date fromResult =
        Date.from(
            LocalDate.of(1985, 3, 21)
                .atStartOfDay()
                .atZone(ZoneOffset.systemDefault())
                .toInstant());

    Date[] actualDatesFromDicomElement =
        DicomUtils.getDatesFromDicomElement(
            dicom, 1, "Private Creator ID", new Date[] {fromResult, fromResult});

    assertEquals(2, actualDatesFromDicomElement.length);
    assertSame(fromResult, actualDatesFromDicomElement[0]);

    int privateCreatorId = 0x70070070;
    int privateDateId = 0x70077001;
    dicom.setString(privateCreatorId, VR.LO, "Private Creator ID");
    dicom.setDate(privateDateId, VR.DA, fromResult, new Date());
    actualDatesFromDicomElement =
        DicomUtils.getDatesFromDicomElement(
            dicom, privateDateId, "Private Creator ID", new Date[] {fromResult});
    assertEquals(2, actualDatesFromDicomElement.length);
    assertEquals(fromResult, actualDatesFromDicomElement[0]);
  }

  @Test
  @DisplayName("Get Patient Age in Period")
  void testGetPatientAgeInPeriod() {
    Date birthdate =
        Date.from(
            LocalDate.of(1985, 3, 21)
                .atStartOfDay()
                .atZone(ZoneOffset.systemDefault())
                .toInstant());
    Date date =
        Date.from(
            LocalDate.of(2003, 7, 19)
                .atStartOfDay()
                .atZone(ZoneOffset.systemDefault())
                .toInstant());
    Attributes dicom = new Attributes();
    dicom.setDate(Tag.PatientBirthDate, VR.DA, birthdate);
    dicom.setString(Tag.PatientAge, VR.AS, "017Y");

    assertEquals("foo", DicomUtils.getPatientAgeInPeriod(null, 1, "foo", "foo", false));
    assertNull(DicomUtils.getPatientAgeInPeriod(dicom, 1, "foo", "foo", false));

    dicom.setDate(Tag.AcquisitionDate, VR.DA, date);
    assertEquals(
        "42", DicomUtils.getPatientAgeInPeriod(dicom, 65536, "Private Creator ID", "42", true));
    assertEquals(
        "42",
        DicomUtils.getPatientAgeInPeriod(
            Attributes.createFileMetaInformation("1234", "1234", "1234", true),
            65536,
            "Private Creator ID",
            "42",
            true));
    assertEquals(
        "018Y", DicomUtils.getPatientAgeInPeriod(dicom, 65536, "Private Creator ID", null, true));
    assertEquals("017Y", DicomUtils.getPatientAgeInPeriod(dicom, Tag.PatientAge, null, null, true));
    assertEquals(
        "018Y", DicomUtils.getPatientAgeInPeriod(dicom, 65536, "Private Creator ID", "", true));
    assertEquals("018Y", DicomUtils.getPatientAgeInPeriod(dicom, 1, true));
    assertNull(DicomUtils.getPatientAgeInPeriod(null, 1, false));
    assertEquals("018Y", DicomUtils.getPatientAgeInPeriod(dicom, 1, false));
  }

  @Test
  @DisplayName("Get Integer from Dicom Element")
  void testGetIntegerFromDicomElement() {
    Attributes attributes = new Attributes();
    Integer defaultValue = 5;
    Integer value = 10;

    assertNull(DicomUtils.getIntegerFromDicomElement(null, Tag.NumberOfFrames, null));
    assertEquals(
        defaultValue,
        DicomUtils.getIntegerFromDicomElement(null, Tag.NumberOfFrames, defaultValue));
    assertEquals(
        defaultValue,
        DicomUtils.getIntegerFromDicomElement(attributes, Tag.NumberOfFrames, defaultValue));

    attributes.setInt(Tag.NumberOfFrames, VR.IS, value);
    assertEquals(
        value, DicomUtils.getIntegerFromDicomElement(attributes, Tag.NumberOfFrames, null));

    attributes.setString(Tag.NumberOfFrames, VR.LO, "non-integer");
    // Cannot covert non-integer to integer
    assertEquals(
        defaultValue,
        DicomUtils.getIntegerFromDicomElement(attributes, Tag.NumberOfFrames, defaultValue));
  }

  @Test
  @DisplayName("Get Float from Dicom Element")
  void testGetFloatFromDicomElement() {
    Attributes attributes = new Attributes();
    Float defaultValue = 5f;
    Float value = 10f;

    assertNull(DicomUtils.getFloatFromDicomElement(null, Tag.LineThickness, null));
    assertEquals(
        defaultValue, DicomUtils.getFloatFromDicomElement(null, Tag.LineThickness, defaultValue));
    assertEquals(
        defaultValue,
        DicomUtils.getFloatFromDicomElement(attributes, Tag.LineThickness, defaultValue));

    attributes.setFloat(Tag.LineThickness, VR.FL, value);
    assertEquals(value, DicomUtils.getFloatFromDicomElement(attributes, Tag.LineThickness, null));

    attributes.setString(Tag.LineThickness, VR.LO, "non-float");
    // Cannot covert non-float to float
    assertEquals(
        defaultValue,
        DicomUtils.getFloatFromDicomElement(attributes, Tag.LineThickness, defaultValue));
  }

  @Test
  @DisplayName("Get Double from Dicom Element")
  void testGetDoubleFromDicomElement() {
    Attributes attributes = new Attributes();
    Double defaultValue = 1.0;
    Double value = 0.85;

    assertNull(DicomUtils.getDoubleFromDicomElement(null, Tag.FilterLowFrequency, null));
    assertEquals(
        defaultValue,
        DicomUtils.getDoubleFromDicomElement(null, Tag.FilterLowFrequency, defaultValue));
    assertEquals(
        defaultValue,
        DicomUtils.getDoubleFromDicomElement(attributes, Tag.FilterLowFrequency, defaultValue));

    attributes.setDouble(Tag.FilterLowFrequency, VR.DS, value);
    assertEquals(
        value, DicomUtils.getDoubleFromDicomElement(attributes, Tag.FilterLowFrequency, null));

    attributes.setString(Tag.FilterLowFrequency, VR.LO, "non-double");
    // Cannot covert non-double to double
    assertEquals(
        defaultValue,
        DicomUtils.getDoubleFromDicomElement(attributes, Tag.FilterLowFrequency, defaultValue));
  }

  @Test
  @DisplayName("Get Integer array from Dicom Element")
  void testGetIntArrayFromDicomElement() {
    Attributes attributes = new Attributes();
    int[] defaultValue = {5, 10, 15};
    int[] value = {10, 20, 30};

    assertNull(
        DicomUtils.getIntArrayFromDicomElement(
            null, Tag.GraphicLayerRecommendedDisplayCIELabValue, null));
    assertArrayEquals(
        defaultValue,
        DicomUtils.getIntArrayFromDicomElement(
            null, Tag.GraphicLayerRecommendedDisplayCIELabValue, defaultValue));
    assertArrayEquals(
        defaultValue,
        DicomUtils.getIntArrayFromDicomElement(
            attributes, Tag.GraphicLayerRecommendedDisplayCIELabValue, defaultValue));

    attributes.setInt(Tag.GraphicLayerRecommendedDisplayCIELabValue, VR.IS, value);
    assertArrayEquals(
        value,
        DicomUtils.getIntArrayFromDicomElement(
            attributes, Tag.GraphicLayerRecommendedDisplayCIELabValue, null));

    attributes.setString(Tag.GraphicLayerRecommendedDisplayCIELabValue, VR.LO, "non-integer");
    assertArrayEquals(
        defaultValue,
        DicomUtils.getIntArrayFromDicomElement(
            attributes, Tag.GraphicLayerRecommendedDisplayCIELabValue, defaultValue));
  }

  @Test
  @DisplayName("Get Float array from Dicom Element")
  void testGetFloatArrayFromDicomElement() {
    Attributes attributes = new Attributes();
    float[] defaultValue = {1.0f, 1.0f};
    float[] value = {0.85f, 0.47f};

    assertNull(DicomUtils.getFloatArrayFromDicomElement(null, Tag.RotationPoint, null));
    assertArrayEquals(
        defaultValue,
        DicomUtils.getFloatArrayFromDicomElement(null, Tag.RotationPoint, defaultValue));
    assertArrayEquals(
        defaultValue,
        DicomUtils.getFloatArrayFromDicomElement(attributes, Tag.RotationPoint, defaultValue));

    attributes.setFloat(Tag.RotationPoint, VR.FL, value);
    assertArrayEquals(
        value, DicomUtils.getFloatArrayFromDicomElement(attributes, Tag.RotationPoint, null));

    attributes.setString(Tag.RotationPoint, VR.LO, "non-float");
    assertArrayEquals(
        defaultValue,
        DicomUtils.getFloatArrayFromDicomElement(attributes, Tag.RotationPoint, defaultValue));
  }

  @Test
  @DisplayName("Get Double array from Dicom Element")
  void testGetDoubleArrayFromDicomElement() {
    Attributes attributes = new Attributes();
    double[] defaultValue = {1.0, 1.0};
    double[] value = {0.85, 0.47};

    assertNull(DicomUtils.getDoubleArrayFromDicomElement(null, Tag.PixelSpacing, null));
    assertArrayEquals(
        defaultValue,
        DicomUtils.getDoubleArrayFromDicomElement(null, Tag.PixelSpacing, defaultValue));
    assertArrayEquals(
        defaultValue,
        DicomUtils.getDoubleArrayFromDicomElement(attributes, Tag.PixelSpacing, defaultValue));

    attributes.setDouble(Tag.PixelSpacing, VR.DS, value);
    assertArrayEquals(
        value, DicomUtils.getDoubleArrayFromDicomElement(attributes, Tag.PixelSpacing, null));

    attributes.setString(Tag.PixelSpacing, VR.LO, "non-double");
    assertArrayEquals(
        defaultValue,
        DicomUtils.getDoubleArrayFromDicomElement(attributes, Tag.PixelSpacing, defaultValue));
  }
}
