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

import java.lang.reflect.Array;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAccessor;
import java.util.Arrays;
import java.util.Date;
import java.util.Locale;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.DoubleStream;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.UID;
import org.dcm4che3.util.TagUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.util.StringUtil;

/**
 * Utility class for DICOM data manipulation and validation. Provides methods for checking transfer
 * syntaxes, formatting text values, extracting data from DICOM elements, and calculating patient
 * age.
 *
 * @author Nicolas Roduit
 */
public class DicomUtils {
  private static final Logger LOGGER = LoggerFactory.getLogger(DicomUtils.class);

  private DicomUtils() {}

  /**
   * Checks if the given UID represents a video transfer syntax.
   *
   * @param uid the transfer syntax UID to check
   * @return true if the UID represents a video transfer syntax, false otherwise
   */
  public static boolean isVideo(String uid) {
    return switch (uid) {
      case UID.MPEG2MPML,
              UID.MPEG2MPMLF,
              UID.MPEG2MPHL,
              UID.MPEG2MPHLF,
              UID.MPEG4HP41,
              UID.MPEG4HP41F,
              UID.MPEG4HP41BD,
              UID.MPEG4HP41BDF,
              UID.MPEG4HP422D,
              UID.MPEG4HP422DF,
              UID.MPEG4HP423D,
              UID.MPEG4HP423DF,
              UID.MPEG4HP42STEREO,
              UID.MPEG4HP42STEREOF,
              UID.HEVCMP51,
              UID.HEVCM10P51 ->
          true;
      default -> false;
    };
  }

  /**
   * Checks if the given UID represents a JPEG 2000 transfer syntax.
   *
   * @param uid the transfer syntax UID to check
   * @return true if the UID represents a JPEG 2000 transfer syntax, false otherwise
   */
  public static boolean isJpeg2000(String uid) {
    return switch (uid) {
      case UID.JPEG2000Lossless,
              UID.JPEG2000,
              UID.JPEG2000MCLossless,
              UID.JPEG2000MC,
              UID.HTJ2KLossless,
              UID.HTJ2KLosslessRPCL,
              UID.HTJ2K ->
          true;
      default -> false;
    };
  }

  /**
   * Checks if the given UID represents a native (uncompressed) transfer syntax.
   *
   * @param uid the transfer syntax UID to check
   * @return true if the UID represents a native transfer syntax, false otherwise
   */
  public static boolean isNative(String uid) {
    return switch (uid) {
      case UID.ImplicitVRLittleEndian, UID.ExplicitVRLittleEndian, UID.ExplicitVRBigEndian -> true;
      default -> false;
    };
  }

  /**
   * Formats a value according to the specified format string using the default locale.
   *
   * @param value the value to format (can be String, String[], TemporalAccessor, arrays, etc.)
   * @param format the format string (supports $V placeholder with optional formatting)
   * @return the formatted text, or empty string if value is null
   */
  public static String getFormattedText(Object value, String format) {
    return getFormattedText(value, format, Locale.getDefault());
  }

  /**
   * Formats a value according to the specified format string and locale. Supports various data
   * types including arrays and temporal objects.
   *
   * @param value the value to format
   * @param format the format string (supports $V placeholder with optional formatting like
   *     $V:f:pattern$ or $V:l:limit$)
   * @param locale the locale to use for formatting
   * @return the formatted text, or empty string if value is null
   */
  public static String getFormattedText(Object value, String format, Locale locale) {
    if (value == null) {
      return StringUtil.EMPTY_STRING;
    }

    String str = convertValueToString(value, locale);

    if (StringUtil.hasText(format) && !"$V".equals(format.trim())) {
      return formatValue(str, isDecimalType(value), format);
    }

    return str == null ? StringUtil.EMPTY_STRING : str;
  }

  private static String convertValueToString(Object value, Locale locale) {
    if (value instanceof String string) {
      return string;
    } else if (value instanceof String[] strings) {
      return String.join("\\", Arrays.asList(strings));
    } else if (value instanceof TemporalAccessor temporal) {
      return DateTimeUtils.formatDateTime(temporal, locale);
    } else if (value instanceof TemporalAccessor[] temporals) {
      return Stream.of(temporals)
          .map(v -> DateTimeUtils.formatDateTime(v, locale))
          .collect(Collectors.joining(", "));
    } else if (value instanceof float[] array) {
      return IntStream.range(0, array.length)
          .mapToObj(i -> String.valueOf(array[i]))
          .collect(Collectors.joining(", "));
    } else if (value instanceof double[] array) {
      return DoubleStream.of(array).mapToObj(String::valueOf).collect(Collectors.joining(", "));
    } else if (value instanceof int[] array) {
      return IntStream.of(array).mapToObj(String::valueOf).collect(Collectors.joining(", "));
    } else {
      return value.toString();
    }
  }

  private static boolean isDecimalType(Object value) {
    return value instanceof Float || value instanceof Double;
  }

  /**
   * Applies format string to a value with support for decimal and text formatting.
   *
   * @param value the string value to format
   * @param decimal true if the value is a decimal number
   * @param format the format string containing $V placeholder
   * @return the formatted string
   */
  protected static String formatValue(String value, boolean decimal, String format) {
    String str = value;
    int index = format.indexOf("$V");
    int fmLength = 2;
    if (index == -1) {
      return str;
    }

    FormatResult result = processFormatSpecifiers(str, decimal, format, index, fmLength);
    str = result.value();
    fmLength = result.length();

    return buildFormattedString(str, format, index, fmLength);
  }

  private static FormatResult processFormatSpecifiers(
      String value, boolean decimal, String format, int index, int fmLength) {
    boolean hasFormatSpecifier =
        format.length() > index + fmLength && format.charAt(index + fmLength) == ':';

    if (!hasFormatSpecifier) {
      return new FormatResult(value, fmLength);
    }

    fmLength++; // skip ':'
    char specifier = format.charAt(index + fmLength);
    fmLength++;

    if (specifier == 'f' && decimal) {
      return processDecimalFormat(value, format, index, fmLength);
    } else if (specifier == 'l') {
      return processLengthLimit(value, format, index, fmLength);
    } else {
      return new FormatResult(value, fmLength);
    }
  }

  private static FormatResult processDecimalFormat(
      String value, String format, int index, int fmLength) {
    String pattern = getPattern(index + fmLength, format);
    if (pattern == null) {
      return new FormatResult(value, fmLength);
    }
    try {
      String formattedValue =
          new DecimalFormat(pattern, DecimalFormatSymbols.getInstance())
              .format(Double.parseDouble(value));
      return new FormatResult(formattedValue, fmLength + pattern.length() + 2);
    } catch (NumberFormatException e) {
      LOGGER.warn("Cannot apply pattern to decimal value", e);
      return new FormatResult(value, fmLength + pattern.length() + 2);
    }
  }

  private static FormatResult processLengthLimit(
      String value, String format, int index, int fmLength) {
    String pattern = getPattern(index + fmLength, format);
    if (pattern == null) {
      return new FormatResult(value, fmLength);
    }
    try {
      int limit = Integer.parseInt(pattern);
      String limitedValue = value.length() > limit ? value.substring(0, limit) + "..." : value;
      return new FormatResult(limitedValue, fmLength + pattern.length() + 2);
    } catch (NumberFormatException e) {
      LOGGER.warn("Cannot apply pattern to limit value", e);
      return new FormatResult(value, fmLength + pattern.length() + 2);
    }
  }

  private static String buildFormattedString(String value, String format, int index, int fmLength) {
    StringBuilder sb = new StringBuilder();
    sb.append(format, 0, index);
    sb.append(value);
    if (format.length() > index + fmLength) {
      sb.append(format.substring(index + fmLength));
    }
    return sb.toString();
  }

  private static String getPattern(int startIndex, String format) {
    int beginIndex = format.indexOf('$', startIndex);
    int endIndex = format.indexOf('$', startIndex + 2);
    return (beginIndex == -1 || endIndex == -1) ? null : format.substring(beginIndex + 1, endIndex);
  }

  /**
   * Extracts a string value from a DICOM element, joining multiple values with backslashes.
   *
   * @param dicom the DICOM attributes
   * @param tag the DICOM tag
   * @return the string value or null if not present
   */
  public static String getStringFromDicomElement(Attributes dicom, int tag) {
    if (dicom == null || !dicom.containsValue(tag)) {
      return null;
    }

    String[] strings = dicom.getStrings(tag);
    if (strings == null || strings.length == 0) {
      return null;
    }
    return strings.length == 1 ? strings[0] : String.join("\\", strings);
  }

  /**
   * Extracts a string array from a DICOM element.
   *
   * @param dicom the DICOM attributes
   * @param tag the DICOM tag
   * @return the string array or null if not present
   */
  public static String[] getStringArrayFromDicomElement(Attributes dicom, int tag) {
    return getStringArrayFromDicomElement(dicom, tag, (String) null);
  }

  /**
   * Extracts a string array from a DICOM element with private creator ID.
   *
   * @param dicom the DICOM attributes
   * @param tag the DICOM tag
   * @param privateCreatorID the private creator ID (can be null)
   * @return the string array or null if not present
   */
  public static String[] getStringArrayFromDicomElement(
      Attributes dicom, int tag, String privateCreatorID) {
    if (dicom == null || !dicom.containsValue(tag)) {
      return null;
    }
    return dicom.getStrings(privateCreatorID, tag);
  }

  /**
   * Extracts a string array from a DICOM element with default value.
   *
   * @param dicom the DICOM attributes
   * @param tag the DICOM tag
   * @param defaultValue the default value to return if not present
   * @return the string array or default value
   */
  public static String[] getStringArrayFromDicomElement(
      Attributes dicom, int tag, String[] defaultValue) {
    return getStringArrayFromDicomElement(dicom, tag, null, defaultValue);
  }

  /**
   * Extracts a string array from a DICOM element with private creator ID and default value.
   *
   * @param dicom the DICOM attributes
   * @param tag the DICOM tag
   * @param privateCreatorID the private creator ID (can be null)
   * @param defaultValue the default value to return if not present
   * @return the string array or default value
   */
  public static String[] getStringArrayFromDicomElement(
      Attributes dicom, int tag, String privateCreatorID, String[] defaultValue) {
    if (dicom == null || !dicom.containsValue(tag)) {
      return defaultValue;
    }
    String[] val = dicom.getStrings(privateCreatorID, tag);
    return (val == null || val.length == 0) ? defaultValue : val;
  }

  /**
   * Extracts a date from a DICOM element.
   *
   * @param dicom the DICOM attributes
   * @param tag the DICOM tag
   * @param defaultValue the default value to return if not present
   * @return the date or default value
   */
  public static Date getDateFromDicomElement(Attributes dicom, int tag, Date defaultValue) {
    if (dicom == null || !dicom.containsValue(tag)) {
      return defaultValue;
    }
    return dicom.getDate(tag, defaultValue);
  }

  /**
   * Extracts a date array from a DICOM element with private creator ID.
   *
   * @param dicom the DICOM attributes
   * @param tag the DICOM tag
   * @param privateCreatorID the private creator ID (can be null)
   * @param defaultValue the default value to return if not present
   * @return the date array or default value
   */
  public static Date[] getDatesFromDicomElement(
      Attributes dicom, int tag, String privateCreatorID, Date[] defaultValue) {
    if (dicom == null || !dicom.containsValue(tag)) {
      return defaultValue;
    }
    Date[] val = dicom.getDates(privateCreatorID, tag);
    return (val == null || val.length == 0) ? defaultValue : val;
  }

  /**
   * Calculates patient age based on birth date and various date fields in DICOM. If
   * computeOnlyIfNull is false, always computes the age from available dates.
   *
   * @param dicom the DICOM attributes
   * @param tag the tag to check for existing age value
   * @param computeOnlyIfNull if true, only compute if no existing value found
   * @return the patient age in DICOM format (nnnY, nnnM, or nnnD) or null
   */
  public static String getPatientAgeInPeriod(Attributes dicom, int tag, boolean computeOnlyIfNull) {
    return getPatientAgeInPeriod(dicom, tag, null, null, computeOnlyIfNull);
  }

  /**
   * Calculates patient age based on birth date and various date fields in DICOM.
   *
   * @param dicom the DICOM attributes
   * @param tag the tag to check for existing age value
   * @param privateCreatorID the private creator ID (can be null)
   * @param defaultValue the default value to return if calculation fails
   * @param computeOnlyIfNull if true, only compute if no existing value found
   * @return the patient age in DICOM format (nnnY, nnnM, or nnnD) or default value
   */
  public static String getPatientAgeInPeriod(
      Attributes dicom,
      int tag,
      String privateCreatorID,
      String defaultValue,
      boolean computeOnlyIfNull) {
    if (dicom == null) {
      return defaultValue;
    }

    if (computeOnlyIfNull) {
      String existingAge = dicom.getString(privateCreatorID, tag, defaultValue);
      if (StringUtil.hasText(existingAge)) {
        return existingAge;
      }
    }
    return calculateAgeFromDates(dicom);
  }

  private static String calculateAgeFromDates(Attributes dicom) {
    Date studyDate =
        findFirstAvailableDate(
            dicom,
            Tag.ContentDate,
            Tag.AcquisitionDate,
            Tag.DateOfSecondaryCapture,
            Tag.SeriesDate,
            Tag.StudyDate);

    if (studyDate == null) {
      return null;
    }
    Date birthDate = dicom.getDate(Tag.PatientBirthDate);
    if (birthDate == null) {
      return null;
    }

    return getPeriod(DateTimeUtils.toLocalDate(birthDate), DateTimeUtils.toLocalDate(studyDate));
  }

  private static Date findFirstAvailableDate(Attributes dicom, int... tagIds) {
    for (int tagId : tagIds) {
      Date date = dicom.getDate(tagId);
      if (date != null) {
        return date;
      }
    }
    return null;
  }

  /**
   * Calculates the period between two dates in DICOM age format. Returns format: nnnY for years,
   * nnnM for months, nnnD for days.
   *
   * @param first the start date
   * @param last the end date
   * @return the period in DICOM format
   * @throws NullPointerException if either date is null
   */
  public static String getPeriod(LocalDate first, LocalDate last) {
    Objects.requireNonNull(first);
    Objects.requireNonNull(last);

    long years = ChronoUnit.YEARS.between(first, last);
    if (years >= 2) {
      return String.format("%03dY", years);
    }
    long months = ChronoUnit.MONTHS.between(first, last);
    if (months >= 2) {
      return String.format("%03dM", months);
    }
    return String.format("%03dD", ChronoUnit.DAYS.between(first, last));
  }

  /**
   * Extracts a float value from a DICOM element.
   *
   * @param dicom the DICOM attributes
   * @param tag the DICOM tag
   * @param defaultValue the default value to return if not present or parsing fails
   * @return the float value or default value
   */
  public static Float getFloatFromDicomElement(Attributes dicom, int tag, Float defaultValue) {
    return getFloatFromDicomElement(dicom, tag, null, defaultValue);
  }

  /**
   * Extracts a float value from a DICOM element with private creator ID.
   *
   * @param dicom the DICOM attributes
   * @param tag the DICOM tag
   * @param privateCreatorID the private creator ID (can be null)
   * @param defaultValue the default value to return if not present or parsing fails
   * @return the float value or default value
   */
  public static Float getFloatFromDicomElement(
      Attributes dicom, int tag, String privateCreatorID, Float defaultValue) {
    if (dicom == null || !dicom.containsValue(tag)) {
      return defaultValue;
    }
    return parseNumericValue(
        () -> dicom.getFloat(privateCreatorID, tag, defaultValue == null ? 0.0F : defaultValue),
        defaultValue,
        tag,
        "Float");
  }

  /**
   * Extracts an integer value from a DICOM element.
   *
   * @param dicom the DICOM attributes
   * @param tag the DICOM tag
   * @param defaultValue the default value to return if not present or parsing fails
   * @return the integer value or default value
   */
  public static Integer getIntegerFromDicomElement(
      Attributes dicom, int tag, Integer defaultValue) {
    return getIntegerFromDicomElement(dicom, tag, null, defaultValue);
  }

  /**
   * Extracts an integer value from a DICOM element with private creator ID.
   *
   * @param dicom the DICOM attributes
   * @param tag the DICOM tag
   * @param privateCreatorID the private creator ID (can be null)
   * @param defaultValue the default value to return if not present or parsing fails
   * @return the integer value or default value
   */
  public static Integer getIntegerFromDicomElement(
      Attributes dicom, int tag, String privateCreatorID, Integer defaultValue) {
    if (dicom == null || !dicom.containsValue(tag)) {
      return defaultValue;
    }
    return parseNumericValue(
        () -> dicom.getInt(privateCreatorID, tag, defaultValue == null ? 0 : defaultValue),
        defaultValue,
        tag,
        "Integer");
  }

  /**
   * Extracts a double value from a DICOM element.
   *
   * @param dicom the DICOM attributes
   * @param tag the DICOM tag
   * @param defaultValue the default value to return if not present or parsing fails
   * @return the double value or default value
   */
  public static Double getDoubleFromDicomElement(Attributes dicom, int tag, Double defaultValue) {
    return getDoubleFromDicomElement(dicom, tag, null, defaultValue);
  }

  /**
   * Extracts a double value from a DICOM element with private creator ID.
   *
   * @param dicom the DICOM attributes
   * @param tag the DICOM tag
   * @param privateCreatorID the private creator ID (can be null)
   * @param defaultValue the default value to return if not present or parsing fails
   * @return the double value or default value
   */
  public static Double getDoubleFromDicomElement(
      Attributes dicom, int tag, String privateCreatorID, Double defaultValue) {
    if (dicom == null || !dicom.containsValue(tag)) {
      return defaultValue;
    }
    return parseNumericValue(
        () -> dicom.getDouble(privateCreatorID, tag, defaultValue == null ? 0.0 : defaultValue),
        defaultValue,
        tag,
        "Double");
  }

  /**
   * Extracts an integer array from a DICOM element.
   *
   * @param dicom the DICOM attributes
   * @param tag the DICOM tag
   * @param defaultValue the default value to return if not present or parsing fails
   * @return the integer array or default value
   */
  public static int[] getIntArrayFromDicomElement(Attributes dicom, int tag, int[] defaultValue) {
    return getIntArrayFromDicomElement(dicom, tag, null, defaultValue);
  }

  /**
   * Extracts an integer array from a DICOM element with private creator ID.
   *
   * @param dicom the DICOM attributes
   * @param tag the DICOM tag
   * @param privateCreatorID the private creator ID (can be null)
   * @param defaultValue the default value to return if not present or parsing fails
   * @return the integer array or default value
   */
  public static int[] getIntArrayFromDicomElement(
      Attributes dicom, int tag, String privateCreatorID, int[] defaultValue) {
    if (dicom == null || !dicom.containsValue(tag)) {
      return defaultValue;
    }
    return parseArrayValue(() -> dicom.getInts(privateCreatorID, tag), defaultValue, tag, "int[]");
  }

  /**
   * Extracts a float array from a DICOM element.
   *
   * @param dicom the DICOM attributes
   * @param tag the DICOM tag
   * @param defaultValue the default value to return if not present or parsing fails
   * @return the float array or default value
   */
  public static float[] getFloatArrayFromDicomElement(
      Attributes dicom, int tag, float[] defaultValue) {
    return getFloatArrayFromDicomElement(dicom, tag, null, defaultValue);
  }

  /**
   * Extracts a float array from a DICOM element with private creator ID.
   *
   * @param dicom the DICOM attributes
   * @param tag the DICOM tag
   * @param privateCreatorID the private creator ID (can be null)
   * @param defaultValue the default value to return if not present or parsing fails
   * @return the float array or default value
   */
  public static float[] getFloatArrayFromDicomElement(
      Attributes dicom, int tag, String privateCreatorID, float[] defaultValue) {
    if (dicom == null || !dicom.containsValue(tag)) {
      return defaultValue;
    }
    return parseArrayValue(
        () -> dicom.getFloats(privateCreatorID, tag), defaultValue, tag, "float[]");
  }

  /**
   * Extracts a double array from a DICOM element.
   *
   * @param dicom the DICOM attributes
   * @param tag the DICOM tag
   * @param defaultValue the default value to return if not present or parsing fails
   * @return the double array or default value
   */
  public static double[] getDoubleArrayFromDicomElement(
      Attributes dicom, int tag, double[] defaultValue) {
    return getDoubleArrayFromDicomElement(dicom, tag, null, defaultValue);
  }

  /**
   * Extracts a double array from a DICOM element with private creator ID.
   *
   * @param dicom the DICOM attributes
   * @param tag the DICOM tag
   * @param privateCreatorID the private creator ID (can be null)
   * @param defaultValue the default value to return if not present or parsing fails
   * @return the double array or default value
   */
  public static double[] getDoubleArrayFromDicomElement(
      Attributes dicom, int tag, String privateCreatorID, double[] defaultValue) {
    if (dicom == null || !dicom.containsValue(tag)) {
      return defaultValue;
    }
    return parseArrayValue(
        () -> dicom.getDoubles(privateCreatorID, tag), defaultValue, tag, "double[]");
  }

  private static <T> T parseNumericValue(
      NumericValueSupplier<T> supplier, T defaultValue, int tag, String type) {
    try {
      return supplier.get();
    } catch (NumberFormatException e) {
      LOGGER.error("Cannot parse {} of {}: {} ", type, TagUtils.toString(tag), e.getMessage());
      return defaultValue;
    }
  }

  private static <T> T parseArrayValue(
      ArrayValueSupplier<T> supplier, T defaultValue, int tag, String type) {
    try {
      T val = supplier.get();
      return (val != null && Array.getLength(val) != 0) ? val : defaultValue;
    } catch (NumberFormatException e) {
      LOGGER.error("Cannot parse {} of {}: {} ", type, TagUtils.toString(tag), e.getMessage());
      return defaultValue;
    }
  }

  @FunctionalInterface
  private interface NumericValueSupplier<T> {
    T get() throws NumberFormatException;
  }

  @FunctionalInterface
  private interface ArrayValueSupplier<T> {
    T get() throws NumberFormatException;
  }

  private record FormatResult(String value, int length) {}
}
