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
import java.util.*;
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
public final class DicomUtils {
  private static final Logger LOGGER = LoggerFactory.getLogger(DicomUtils.class);

  // Video transfer syntax UIDs
  private static final Set<String> VIDEO_TRANSFER_SYNTAXES =
      Set.of(
          UID.MPEG2MPML,
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
          UID.HEVCM10P51);

  // JPEG 2000 transfer syntax UIDs
  private static final Set<String> JPEG2000_TRANSFER_SYNTAXES =
      Set.of(
          UID.JPEG2000Lossless,
          UID.JPEG2000,
          UID.JPEG2000MCLossless,
          UID.JPEG2000MC,
          UID.HTJ2KLossless,
          UID.HTJ2KLosslessRPCL,
          UID.HTJ2K);

  // Native (uncompressed) transfer syntax UIDs
  private static final Set<String> NATIVE_TRANSFER_SYNTAXES =
      Set.of(UID.ImplicitVRLittleEndian, UID.ExplicitVRLittleEndian, UID.ExplicitVRBigEndian);

  // Date tags to search for patient age calculation (in order of preference)
  private static final int[] STUDY_DATE_TAGS = {
    Tag.ContentDate, Tag.AcquisitionDate, Tag.DateOfSecondaryCapture, Tag.SeriesDate, Tag.StudyDate
  };

  private DicomUtils() {}

  /**
   * Validates if the given string is a valid DICOM UID.
   *
   * @param uid the UID string to validate
   * @return true if the UID is valid, false otherwise
   */
  public static boolean isValidUID(String uid) {
    return StringUtil.hasText(uid) && uid.matches("^[0-9]+(\\.[0-9]+)*$") && uid.length() <= 64;
  }

  /**
   * Checks if the given UID represents a video transfer syntax.
   *
   * @param uid the transfer syntax UID to check
   * @return true if the UID represents a video transfer syntax, false otherwise
   */
  public static boolean isVideo(String uid) {
    return VIDEO_TRANSFER_SYNTAXES.contains(uid);
  }

  /**
   * Determines whether the given UID represents a JPEG XL transfer syntax.
   *
   * @param uid the transfer syntax UID to check
   * @return true if the UID is one of the JPEG XL-related transfer syntaxes, false otherwise
   */
  public static boolean isJpegXL(String uid) {
    return switch (uid) {
      case UID.JPEG2000Lossless, UID.JPEGXLJPEGRecompression, UID.JPEGXL -> true;
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
    return JPEG2000_TRANSFER_SYNTAXES.contains(uid);
  }

  /**
   * Checks if the given UID represents a native (uncompressed) transfer syntax.
   *
   * @param uid the transfer syntax UID to check
   * @return true if the UID represents a native transfer syntax, false otherwise
   */
  public static boolean isNative(String uid) {
    return NATIVE_TRANSFER_SYNTAXES.contains(uid);
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

    var stringValue = convertValueToString(value, locale);

    if (StringUtil.hasText(format) && !"$V".equals(format.trim())) {
      return formatValue(stringValue, isDecimalType(value), format);
    }

    return Objects.requireNonNullElse(stringValue, StringUtil.EMPTY_STRING);
  }

  private static String convertValueToString(Object value, Locale locale) {
    if (value instanceof String string) {
      return string;
    } else if (value instanceof String[] strings) {
      return String.join("\\", strings);
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
    return value instanceof Double || value instanceof Float;
  }

  /**
   * Applies format string to a value with support for decimal and text formatting.
   *
   * @param value the string value to format
   * @param decimal true if the value is a decimal number
   * @param format the format string containing $V placeholder
   * @return the formatted string
   */
  static String formatValue(String value, boolean decimal, String format) {
    var placeholders = PlaceholderParser.parseFormat(format);
    if (placeholders.isEmpty()) {
      return value;
    }

    var result = format;
    for (var placeholder : placeholders) {
      var processedValue = placeholder.process(value, decimal);
      result = result.replace(placeholder.fullMatch(), processedValue);
    }
    return result;
  }

  /** Represents a format placeholder with its type and parameters. */
  private record Placeholder(PlaceholderType type, String parameter, String fullMatch) {

    String process(String value, boolean isDecimal) {
      return type.process(value, parameter, isDecimal);
    }
  }

  /** Types of supported placeholders. */
  private enum PlaceholderType {
    SIMPLE {
      @Override
      String process(String value, String parameter, boolean isDecimal) {
        return value;
      }
    },

    DECIMAL_FORMAT {
      @Override
      String process(String value, String parameter, boolean isDecimal) {
        if (!isDecimal || parameter == null) {
          return value;
        }
        try {
          var formatter = new DecimalFormat(parameter, DecimalFormatSymbols.getInstance());
          return formatter.format(Double.parseDouble(value));
        } catch (NumberFormatException e) {
          LOGGER.warn("Cannot apply decimal pattern '{}' to value '{}'", parameter, value);
          return value;
        }
      }
    },

    LENGTH_LIMIT {
      @Override
      String process(String value, String parameter, boolean isDecimal) {
        if (parameter == null) {
          return value;
        }
        try {
          var limit = Integer.parseInt(parameter);
          return value.length() > limit ? value.substring(0, limit) + "..." : value;
        } catch (NumberFormatException e) {
          LOGGER.warn("Cannot parse length limit '{}' for value '{}'", parameter, value);
          return value;
        }
      }
    };

    abstract String process(String value, String parameter, boolean isDecimal);
  }

  /** Parser for format placeholders. */
  private static final class PlaceholderParser {
    private static final String PLACEHOLDER_PATTERN = "\\$V(?::([fl])\\$([^$]*)\\$)?";
    private static final java.util.regex.Pattern PATTERN =
        java.util.regex.Pattern.compile(PLACEHOLDER_PATTERN);

    static java.util.List<Placeholder> parseFormat(String format) {
      var placeholders = new java.util.ArrayList<Placeholder>();
      var matcher = PATTERN.matcher(format);

      while (matcher.find()) {
        var fullMatch = matcher.group(0);
        var specifier = matcher.group(1);
        var parameter = matcher.group(2);

        var type = determineType(specifier);
        placeholders.add(new Placeholder(type, parameter, fullMatch));
      }

      return placeholders;
    }

    private static PlaceholderType determineType(String specifier) {
      if (specifier == null) {
        return PlaceholderType.SIMPLE;
      }
      return switch (specifier) {
        case "f" -> PlaceholderType.DECIMAL_FORMAT;
        case "l" -> PlaceholderType.LENGTH_LIMIT;
        default -> PlaceholderType.SIMPLE;
      };
    }
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

    var strings = dicom.getStrings(tag);
    if (strings == null || strings.length == 0) {
      return null;
    }
    return strings.length == 1 ? strings[0] : String.join("\\", strings);
  }

  /** Extracts a string array from a DICOM element. */
  public static String[] getStringArrayFromDicomElement(Attributes dicom, int tag) {
    return getStringArrayFromDicomElement(dicom, tag, (String) null);
  }

  /** Extracts a string array from a DICOM element with private creator ID. */
  public static String[] getStringArrayFromDicomElement(
      Attributes dicom, int tag, String privateCreatorID) {
    if (dicom == null || !dicom.containsValue(tag)) {
      return null;
    }
    return dicom.getStrings(privateCreatorID, tag);
  }

  /** Extracts a string array from a DICOM element with default value. */
  public static String[] getStringArrayFromDicomElement(
      Attributes dicom, int tag, String[] defaultValue) {
    return getStringArrayFromDicomElement(dicom, tag, null, defaultValue);
  }

  /** Extracts a string array from a DICOM element with private creator ID and default value. */
  public static String[] getStringArrayFromDicomElement(
      Attributes dicom, int tag, String privateCreatorID, String[] defaultValue) {
    if (dicom == null || !dicom.containsValue(tag)) {
      return defaultValue;
    }
    var values = dicom.getStrings(privateCreatorID, tag);
    return (values == null || values.length == 0) ? defaultValue : values;
  }

  /** Extracts a date from a DICOM element. */
  public static Date getDateFromDicomElement(Attributes dicom, int tag, Date defaultValue) {
    if (dicom == null || !dicom.containsValue(tag)) {
      return defaultValue;
    }
    return dicom.getDate(tag, defaultValue);
  }

  /** Extracts a date array from a DICOM element with private creator ID. */
  public static Date[] getDatesFromDicomElement(
      Attributes dicom, int tag, String privateCreatorID, Date[] defaultValue) {
    if (dicom == null || !dicom.containsValue(tag)) {
      return defaultValue;
    }
    var values = dicom.getDates(privateCreatorID, tag);
    return (values == null || values.length == 0) ? defaultValue : values;
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
      var existingAge = dicom.getString(privateCreatorID, tag, defaultValue);
      if (StringUtil.hasText(existingAge)) {
        return existingAge;
      }
    }
    return calculateAgeFromDates(dicom);
  }

  private static String calculateAgeFromDates(Attributes dicom) {
    var studyDate = findFirstAvailableDate(dicom, STUDY_DATE_TAGS);

    if (studyDate == null) {
      return null;
    }
    var birthDate = dicom.getDate(Tag.PatientBirthDate);
    if (birthDate == null) {
      return null;
    }

    return getPeriod(DateTimeUtils.toLocalDate(birthDate), DateTimeUtils.toLocalDate(studyDate));
  }

  private static Date findFirstAvailableDate(Attributes dicom, int... tagIds) {
    for (var tagId : tagIds) {
      var date = dicom.getDate(tagId);
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
    Objects.requireNonNull(first, "First date cannot be null");
    Objects.requireNonNull(last, "Last date cannot be null");

    var years = ChronoUnit.YEARS.between(first, last);
    if (years >= 2) {
      return "%03dY".formatted(years);
    }
    var months = ChronoUnit.MONTHS.between(first, last);
    if (months >= 2) {
      return "%03dM".formatted(months);
    }
    return "%03dD".formatted(ChronoUnit.DAYS.between(first, last));
  }

  /** Extracts a float value from a DICOM element. */
  public static Float getFloatFromDicomElement(Attributes dicom, int tag, Float defaultValue) {
    return getFloatFromDicomElement(dicom, tag, null, defaultValue);
  }

  /** Extracts a float value from a DICOM element with private creator ID. */
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

  /** Extracts an integer value from a DICOM element. */
  public static Integer getIntegerFromDicomElement(
      Attributes dicom, int tag, Integer defaultValue) {
    return getIntegerFromDicomElement(dicom, tag, null, defaultValue);
  }

  /** Extracts an integer value from a DICOM element with private creator ID. */
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

  /** Extracts a double value from a DICOM element. */
  public static Double getDoubleFromDicomElement(Attributes dicom, int tag, Double defaultValue) {
    return getDoubleFromDicomElement(dicom, tag, null, defaultValue);
  }

  /** Extracts a double value from a DICOM element with private creator ID. */
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

  /** Extracts an integer array from a DICOM element. */
  public static int[] getIntArrayFromDicomElement(Attributes dicom, int tag, int[] defaultValue) {
    return getIntArrayFromDicomElement(dicom, tag, null, defaultValue);
  }

  /** Extracts an integer array from a DICOM element with private creator ID. */
  public static int[] getIntArrayFromDicomElement(
      Attributes dicom, int tag, String privateCreatorID, int[] defaultValue) {
    if (dicom == null || !dicom.containsValue(tag)) {
      return defaultValue;
    }
    return parseArrayValue(() -> dicom.getInts(privateCreatorID, tag), defaultValue, tag, "int[]");
  }

  /** Extracts a float array from a DICOM element. */
  public static float[] getFloatArrayFromDicomElement(
      Attributes dicom, int tag, float[] defaultValue) {
    return getFloatArrayFromDicomElement(dicom, tag, null, defaultValue);
  }

  /** Extracts a float array from a DICOM element with private creator ID. */
  public static float[] getFloatArrayFromDicomElement(
      Attributes dicom, int tag, String privateCreatorID, float[] defaultValue) {
    if (dicom == null || !dicom.containsValue(tag)) {
      return defaultValue;
    }
    return parseArrayValue(
        () -> dicom.getFloats(privateCreatorID, tag), defaultValue, tag, "float[]");
  }

  /** Extracts a double array from a DICOM element. */
  public static double[] getDoubleArrayFromDicomElement(
      Attributes dicom, int tag, double[] defaultValue) {
    return getDoubleArrayFromDicomElement(dicom, tag, null, defaultValue);
  }

  /** Extracts a double array from a DICOM element with private creator ID. */
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
      LOGGER.error("Cannot parse {} for tag {}: {}", type, TagUtils.toString(tag), e.getMessage());
      return defaultValue;
    }
  }

  private static <T> T parseArrayValue(
      ArrayValueSupplier<T> supplier, T defaultValue, int tag, String type) {
    try {
      var value = supplier.get();
      return (value != null && Array.getLength(value) != 0) ? value : defaultValue;
    } catch (NumberFormatException e) {
      LOGGER.error("Cannot parse {} for tag {}: {}", type, TagUtils.toString(tag), e.getMessage());
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
