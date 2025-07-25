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
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.time.temporal.TemporalAccessor;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.UID;
import org.dcm4che3.data.VR;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.junitpioneer.jupiter.DefaultLocale;
import org.junitpioneer.jupiter.DefaultTimeZone;

@DefaultLocale(language = "en", country = "US")
class DicomUtilsTest {

  @Nested
  @DisplayName("Period calculations")
  class PeriodCalculations {

    @Test
    @DisplayName("Calculate periods between different dates with years")
    void testGetPeriodYears() {
      assertEquals(
          "014M", DicomUtils.getPeriod(LocalDate.of(2020, 1, 1), LocalDate.of(2021, 3, 1)));
      assertEquals(
          "050Y", DicomUtils.getPeriod(LocalDate.of(1961, 6, 25), LocalDate.of(2012, 6, 24)));
      assertEquals(
          "051Y", DicomUtils.getPeriod(LocalDate.of(1961, 6, 25), LocalDate.of(2012, 6, 25)));
      assertEquals(
          "050Y", DicomUtils.getPeriod(LocalDate.of(1961, 7, 14), LocalDate.of(2012, 6, 25)));
    }

    @Test
    @DisplayName("Calculate periods with months")
    void testGetPeriodMonths() {
      assertEquals(
          "005M", DicomUtils.getPeriod(LocalDate.of(2012, 1, 3), LocalDate.of(2012, 6, 25)));
      assertEquals(
          "011M", DicomUtils.getPeriod(LocalDate.of(2020, 1, 1), LocalDate.of(2020, 12, 31)));
    }

    @Test
    @DisplayName("Calculate periods with days")
    void testGetPeriodDays() {
      assertEquals(
          "031D", DicomUtils.getPeriod(LocalDate.of(2012, 5, 25), LocalDate.of(2012, 6, 25)));
      assertEquals(
          "003D", DicomUtils.getPeriod(LocalDate.of(2012, 6, 22), LocalDate.of(2012, 6, 25)));
      assertEquals(
          "001D", DicomUtils.getPeriod(LocalDate.of(2020, 1, 1), LocalDate.of(2020, 1, 2)));
    }

    @Test
    @DisplayName("Handle leap year calculations correctly")
    void testGetPeriodLeapYear() {
      assertEquals(
          "011Y", DicomUtils.getPeriod(LocalDate.of(2000, 2, 29), LocalDate.of(2011, 3, 1)));
      assertEquals(
          "010Y", DicomUtils.getPeriod(LocalDate.of(2000, 2, 29), LocalDate.of(2011, 2, 28)));
      assertEquals(
          "011Y", DicomUtils.getPeriod(LocalDate.of(2000, 2, 29), LocalDate.of(2012, 2, 28)));
      assertEquals(
          "012Y", DicomUtils.getPeriod(LocalDate.of(2000, 2, 29), LocalDate.of(2012, 2, 29)));
      assertEquals(
          "012Y", DicomUtils.getPeriod(LocalDate.of(2000, 2, 28), LocalDate.of(2012, 2, 28)));
    }

    @Test
    @DisplayName("Throw exception for null dates")
    void testGetPeriodNullDates() {
      LocalDate validDate = LocalDate.of(2020, 1, 1);

      Assertions.assertThrows(
          NullPointerException.class, () -> DicomUtils.getPeriod(null, validDate));
      Assertions.assertThrows(
          NullPointerException.class, () -> DicomUtils.getPeriod(validDate, null));
      Assertions.assertThrows(NullPointerException.class, () -> DicomUtils.getPeriod(null, null));
    }
  }

  @Nested
  @DisplayName("Transfer syntax validation")
  class TransferSyntaxValidation {

    @ParameterizedTest
    @ValueSource(
        strings = {
          UID.MPEG2MPML, UID.MPEG2MPMLF, UID.MPEG2MPHL, UID.MPEG2MPHLF,
          UID.MPEG4HP41, UID.MPEG4HP41F, UID.MPEG4HP41BD, UID.MPEG4HP41BDF,
          UID.MPEG4HP422D, UID.MPEG4HP422DF, UID.MPEG4HP423D, UID.MPEG4HP423DF,
          UID.MPEG4HP42STEREO, UID.MPEG4HP42STEREOF, UID.HEVCMP51, UID.HEVCM10P51
        })
    @DisplayName("Recognize video transfer syntaxes")
    void testIsVideoTrue(String uid) {
      assertTrue(DicomUtils.isVideo(uid));
    }

    @ParameterizedTest
    @ValueSource(strings = {"1234", "1.2.840.10008.1.2.5", UID.ImplicitVRLittleEndian})
    @DisplayName("Reject non-video transfer syntaxes")
    void testIsVideoFalse(String uid) {
      assertFalse(DicomUtils.isVideo(uid));
    }

    @ParameterizedTest
    @ValueSource(
        strings = {
          UID.JPEG2000Lossless,
          UID.JPEG2000,
          UID.JPEG2000MCLossless,
          UID.JPEG2000MC,
          UID.HTJ2KLossless,
          UID.HTJ2KLosslessRPCL,
          UID.HTJ2K
        })
    @DisplayName("Recognize JPEG 2000 transfer syntaxes")
    void testIsJpeg2000True(String uid) {
      assertTrue(DicomUtils.isJpeg2000(uid));
    }

    @ParameterizedTest
    @ValueSource(strings = {"1234", "1.2.840.10008.1.2.5", UID.MPEG2MPML})
    @DisplayName("Reject non-JPEG 2000 transfer syntaxes")
    void testIsJpeg2000False(String uid) {
      assertFalse(DicomUtils.isJpeg2000(uid));
    }

    @ParameterizedTest
    @ValueSource(
        strings = {UID.ImplicitVRLittleEndian, UID.ExplicitVRLittleEndian, UID.ExplicitVRBigEndian})
    @DisplayName("Recognize native transfer syntaxes")
    void testIsNativeTrue(String uid) {
      assertTrue(DicomUtils.isNative(uid));
    }

    @ParameterizedTest
    @ValueSource(strings = {"1234", "1.2.840.10008.1.3", UID.JPEG2000})
    @DisplayName("Reject non-native transfer syntaxes")
    void testIsNativeFalse(String uid) {
      assertFalse(DicomUtils.isNative(uid));
    }
  }

  @Nested
  @DisplayName("Text formatting")
  class TextFormatting {

    @Test
    @DisplayName("Format different data types")
    void testGetFormattedTextBasicTypes() {
      assertEquals("", DicomUtils.getFormattedText(null, "foo"));
      assertEquals("Value", DicomUtils.getFormattedText("Value", "Format"));
      assertEquals("42", DicomUtils.getFormattedText(42, "Format"));
      assertEquals("10.0", DicomUtils.getFormattedText(10.0f, "Format"));
      assertEquals("10.0", DicomUtils.getFormattedText(10.0d, "Format"));
      assertEquals("true", DicomUtils.getFormattedText(true, "Format"));
    }

    @Test
    @DisplayName("Format arrays")
    void testGetFormattedTextArrays() {
      assertEquals(
          "Test1\\Test2", DicomUtils.getFormattedText(new String[] {"Test1", "Test2"}, "Format"));
      assertEquals("1, 2, 3", DicomUtils.getFormattedText(new int[] {1, 2, 3}, "Format"));
      assertEquals("1.5, 2.5", DicomUtils.getFormattedText(new float[] {1.5f, 2.5f}, "Format"));
      assertEquals("1.5, 2.5", DicomUtils.getFormattedText(new double[] {1.5, 2.5}, "Format"));
    }

    @Test
    @DisplayName("Format temporal objects")
    void testGetFormattedTextTemporal() {
      LocalDate date = LocalDate.of(2022, 12, 25);
      LocalTime time = LocalTime.MIDNIGHT;
      LocalDateTime dateTime = LocalDateTime.of(2022, 12, 25, 10, 30);

      String formattedDate = DicomUtils.getFormattedText(date, "Format");
      assertTrue(formattedDate.contains("2022"));

      String formattedTime = DicomUtils.getFormattedText(time, "Format", Locale.US);
      assertTrue(formattedTime.contains("12:00"));

      TemporalAccessor[] temporals = {date, dateTime};
      String formattedArray = DicomUtils.getFormattedText(temporals, "Format");
      assertTrue(formattedArray.contains(","));
    }

    @Test
    @DisplayName("Handle $V placeholder")
    void testGetFormattedTextPlaceholder() {
      assertEquals("Value", DicomUtils.getFormattedText("Value", "$V"));
      assertEquals("Value", DicomUtils.getFormattedText("Value", null));
      assertEquals("Value", DicomUtils.getFormattedText("Value", ""));
    }

    @ParameterizedTest
    @CsvSource({
      "1.2345, true, '$V:f$0.00$', 1.23",
      "123.456, true, '$V:f$#.#$', 123.5",
      "TestTestTest, false, '$V:l$5$', 'TestT...'",
      "Short, false, '$V:l$10$', Short",
      "42, false, 'Value: $V units', 'Value: 42 units'",
      "TestTestTest, false, 'Value: $V:l$4$ units', 'Value: Test... units'",
      // Null and invalid pattern cases
      "123.45, true, '$V:f$0.00$$', 123.45$",
      "123.45, true, '$V:f$0.00', 123.45$0.00",
      "123.45, true, '$V:f$invalid$', invalid123",
      "TestString, false, '$V:l$invalid$', TestString",
      "not_a_number, true, '$V:f$0.00$', not_a_number",
      "TestString, false, '$V:l$0$', '...'",
      "Short, false, '$V:l$1000$', Short",
      // Complex format strings with error conditions
      "123.456, true, 'Value: $V:f$0.00$ units', 'Value: 123.46 units'",
      "TestString, false, 'Prefix: $V:l$5$', 'Prefix: TestS...'",
      "123.456, true, '$V:f$0.0$ suffix', '123.5 suffix'",
      "SimpleValue, false, 'Before $V After', 'Before SimpleValue After'",
      // Pattern extraction edge cases
      "123.45, true, '$V:f$0.00$extra$', '123.45extra$'",
      "123.45, true, '$V:f', 123.45",
      "TestString, false, '$V:l', TestString",
      "123.456, true, '$V:f$#,##0.00$', 123.46",
      // Boundary conditions
      "'This is a very long string', false, '$V:l$3$', 'Thi...'",
      "'', false, '$V:l$5$', ''",
      "A, false, '$V:l$1$', A",
      "0.001, true, '$V:f$0.0000$', 0.0010",
      "123456789.123, true, '$V:f$0.00$', 123456789.12"
    })
    @DisplayName("Format values with patterns")
    void testFormatValueWithPatterns(
        String value, boolean decimal, String format, String expected) {
      String result = DicomUtils.formatValue(value, decimal, format);
      assertEquals(expected, result);
    }
  }

  @Nested
  @DisplayName("DICOM element extraction")
  class DicomElementExtraction {

    private Attributes createTestAttributes() {
      Attributes attrs = new Attributes();
      attrs.setString(Tag.PatientName, VR.PN, "DOE^JOHN");
      attrs.setString(Tag.ShutterShape, VR.CS, "RECTANGULAR", "CIRCULAR", "POLYGONAL");
      attrs.setInt(Tag.Rows, VR.US, 512);
      attrs.setFloat(Tag.PixelSpacing, VR.DS, 0.5f, 1.0f);
      attrs.setDouble(Tag.WindowWidth, VR.DS, 400.0, 800.0);
      attrs.setTimezone(TimeZone.getTimeZone("Europe/Paris"));
      Date date = new Date();
      attrs.setDate(Tag.StudyDate, VR.DA, date);
      attrs.setDate(Tag.StudyTime, VR.TM, date);
      return attrs;
    }

    @Test
    @DisplayName("Extract string from DICOM element")
    void testGetStringFromDicomElement() {
      Attributes attrs = createTestAttributes();

      assertEquals("DOE^JOHN", DicomUtils.getStringFromDicomElement(attrs, Tag.PatientName));
      assertEquals(
          "RECTANGULAR\\CIRCULAR\\POLYGONAL",
          DicomUtils.getStringFromDicomElement(attrs, Tag.ShutterShape));
      assertNull(DicomUtils.getStringFromDicomElement(attrs, Tag.PatientID));
      assertNull(DicomUtils.getStringFromDicomElement(null, Tag.PatientName));
    }

    @Test
    @DisplayName("Extract string array from DICOM element")
    void testGetStringArrayFromDicomElement() {
      Attributes attrs = createTestAttributes();
      String[] expected = {"RECTANGULAR", "CIRCULAR", "POLYGONAL"};

      assertArrayEquals(
          expected, DicomUtils.getStringArrayFromDicomElement(attrs, Tag.ShutterShape));
      assertNull(DicomUtils.getStringArrayFromDicomElement(attrs, Tag.PatientID));
      assertNull(DicomUtils.getStringArrayFromDicomElement(null, Tag.ShutterShape));

      // Test with default value
      String[] defaultValue = {"DEFAULT"};
      assertArrayEquals(
          defaultValue,
          DicomUtils.getStringArrayFromDicomElement(attrs, Tag.PatientID, defaultValue));
      assertArrayEquals(
          expected,
          DicomUtils.getStringArrayFromDicomElement(attrs, Tag.ShutterShape, defaultValue));
    }

    @Test
    @DisplayName("Extract numeric values from DICOM element")
    void testGetNumericFromDicomElement() {
      Attributes attrs = createTestAttributes();

      assertEquals(512, DicomUtils.getIntegerFromDicomElement(attrs, Tag.Rows, 0));
      assertEquals(100, DicomUtils.getIntegerFromDicomElement(attrs, Tag.PatientAge, 100));

      assertEquals(0.5f, DicomUtils.getFloatFromDicomElement(attrs, Tag.PixelSpacing, 0.0f));
      assertEquals(1.5f, DicomUtils.getFloatFromDicomElement(attrs, Tag.SliceThickness, 1.5f));

      assertEquals(400.0, DicomUtils.getDoubleFromDicomElement(attrs, Tag.WindowWidth, 0.0));
      assertEquals(256.0, DicomUtils.getDoubleFromDicomElement(attrs, Tag.WindowCenter, 256.0));
    }

    @Test
    @DisplayName("Extract array values from DICOM element")
    void testGetArrayFromDicomElement() {
      Attributes attrs = createTestAttributes();

      float[] expectedFloats = {0.5f, 1.0f};
      assertArrayEquals(
          expectedFloats, DicomUtils.getFloatArrayFromDicomElement(attrs, Tag.PixelSpacing, null));

      double[] expectedDoubles = {400.0, 800.0};
      assertArrayEquals(
          expectedDoubles, DicomUtils.getDoubleArrayFromDicomElement(attrs, Tag.WindowWidth, null));

      int[] defaultInts = {1, 2, 3};
      assertArrayEquals(
          defaultInts,
          DicomUtils.getIntArrayFromDicomElement(attrs, Tag.ReferencedFrameNumber, defaultInts));
    }

    @Test
    @DisplayName("Extract dates from DICOM element")
    @DefaultTimeZone("Europe/Paris")
    void testGetDateFromDicomElement() {
      Attributes attrs = createTestAttributes();
      Date now = new Date();

      // Study date should be present
      Date studyDate = DicomUtils.getDateFromDicomElement(attrs, Tag.StudyDate, null);
      Date studyTime = DicomUtils.getDateFromDicomElement(attrs, Tag.StudyTime, null);
      Date studyDateTime = DateTimeUtils.dateTime(attrs.getTimeZone(), studyDate, studyTime, false);

      assertTrue(Math.abs(studyDateTime.getTime() - now.getTime()) < 1000); // Within 1 second

      // Patient birth date should return default
      Date defaultDate = new Date(0);
      assertEquals(
          defaultDate,
          DicomUtils.getDateFromDicomElement(attrs, Tag.PatientBirthDate, defaultDate));

      // Test with multiple dates
      Date[] dates = new Date[] {now, new Date(now.getTime() + 86400000)};
      attrs.setDate(Tag.AcquisitionDate, VR.DA, dates);

      Date[] result = DicomUtils.getDatesFromDicomElement(attrs, Tag.AcquisitionDate, null, null);
      assertEquals(2, result.length);
    }
  }

  @Nested
  @DisplayName("Patient age calculation")
  class PatientAgeCalculation {

    private Attributes createPatientAttributes(String birthDate, String studyDate) {
      Attributes attrs = new Attributes();
      if (birthDate != null) {
        attrs.setString(Tag.PatientBirthDate, VR.DA, birthDate);
      }
      if (studyDate != null) {
        attrs.setString(Tag.StudyDate, VR.DA, studyDate);
      }
      return attrs;
    }

    @Test
    @DisplayName("Calculate patient age from birth and study dates")
    void testGetPatientAgeInPeriod() {
      Attributes attrs = createPatientAttributes("19800515", "20220515");

      String age = DicomUtils.getPatientAgeInPeriod(attrs, Tag.PatientAge, false);
      assertEquals("042Y", age);
    }

    @Test
    @DisplayName("Use existing age when computeOnlyIfNull is true")
    void testGetPatientAgeExisting() {
      Attributes attrs = createPatientAttributes("19800515", "20220515");
      attrs.setString(Tag.PatientAge, VR.AS, "041Y");

      String age = DicomUtils.getPatientAgeInPeriod(attrs, Tag.PatientAge, true);
      assertEquals("041Y", age);
    }

    @Test
    @DisplayName("Calculate age from different date fields")
    void testGetPatientAgeFromDifferentDates() {
      Attributes attrs = createPatientAttributes("19800515", null);
      attrs.setString(Tag.ContentDate, VR.DA, "20220515");

      String age = DicomUtils.getPatientAgeInPeriod(attrs, Tag.PatientAge, false);
      assertEquals("042Y", age);
    }

    @Test
    @DisplayName("Return null when dates are missing")
    void testGetPatientAgeNoDates() {
      Attributes attrs = new Attributes();

      String age = DicomUtils.getPatientAgeInPeriod(attrs, Tag.PatientAge, false);
      assertNull(age);

      // Only birth date
      attrs.setString(Tag.PatientBirthDate, VR.DA, "19800515");
      age = DicomUtils.getPatientAgeInPeriod(attrs, Tag.PatientAge, false);
      assertNull(age);

      // Only study date
      attrs = new Attributes();
      attrs.setString(Tag.StudyDate, VR.DA, "20220515");
      age = DicomUtils.getPatientAgeInPeriod(attrs, Tag.PatientAge, false);
      assertNull(age);
    }

    @Test
    @DisplayName("Handle null attributes")
    void testGetPatientAgeNullAttributes() {
      String age = DicomUtils.getPatientAgeInPeriod(null, Tag.PatientAge, false);
      assertNull(age);
    }

    @Test
    @DisplayName("Use default value with private creator")
    void testGetPatientAgeWithPrivateCreator() {
      String defaultAge = "030Y";

      // Test with private creator
      Attributes attrs = new Attributes();
      int privateTag = Tag.PatientAge | 0x00010000; // Simulate private tag
      attrs.setString("PRIVATE", privateTag, VR.AS, defaultAge);
      String age = DicomUtils.getPatientAgeInPeriod(attrs, privateTag, "PRIVATE", defaultAge, true);
      assertEquals(defaultAge, age);
    }

    @ParameterizedTest
    @CsvSource({
      "19800101, 20220101, 042Y",
      "20200101, 20201201, 011M",
      "20220601, 20220610, 009D",
      "20000229, 20120228, 011Y", // Leap year edge case
      "20000229, 20120229, 012Y" // Leap year exact
    })
    @DisplayName("Calculate ages for various scenarios")
    void testGetPatientAgeScenarios(String birthDate, String studyDate, String expectedAge) {
      Attributes attrs = createPatientAttributes(birthDate, studyDate);
      String age = DicomUtils.getPatientAgeInPeriod(attrs, Tag.PatientAge, false);
      assertEquals(expectedAge, age);
    }
  }

  @Nested
  @DisplayName("Error handling")
  class ErrorHandling {

    @Test
    @DisplayName("Handle invalid date parsing")
    void testInvalidDateParsing() {
      Assertions.assertThrows(
          DateTimeParseException.class,
          () -> {
            DateTimeUtils.parseDA("20122406"); // Invalid date
          });
    }

    @Test
    @DisplayName("Handle empty arrays")
    void testEmptyArrays() {
      Attributes attrs = new Attributes();
      attrs.setString(Tag.WindowWidth, VR.DS); // Empty array

      double[] defaultArray = {1.0, 2.0};
      double[] result =
          DicomUtils.getDoubleArrayFromDicomElement(attrs, Tag.WindowWidth, defaultArray);
      assertArrayEquals(defaultArray, result);
    }
  }
}
