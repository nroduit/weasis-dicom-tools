/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.exif;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/**
 * Value of one EXIF/TIFF tag, kept as its raw bytes with the byte order of the TIFF block it comes
 * from, and decoded on demand.
 */
public final class ExifValue {
  public static final int BYTE = 1;
  public static final int ASCII = 2;
  public static final int SHORT = 3;
  public static final int LONG = 4;
  public static final int RATIONAL = 5;
  public static final int SBYTE = 6;
  public static final int UNDEFINED = 7;
  public static final int SSHORT = 8;
  public static final int SLONG = 9;
  public static final int SRATIONAL = 10;
  public static final int FLOAT = 11;
  public static final int DOUBLE = 12;

  private static final long UNKNOWN_DENOMINATOR = 0xFFFFFFFFL;

  private final int type;
  private final int count;
  private final byte[] data;
  private final ByteOrder order;

  ExifValue(int type, int count, byte[] data, ByteOrder order) {
    this.type = type;
    this.count = count;
    this.data = data;
    this.order = order;
  }

  /** Size in bytes of one component of the given TIFF type, or 0 for an unknown type. */
  static int componentSize(int type) {
    return switch (type) {
      case BYTE, ASCII, SBYTE, UNDEFINED -> 1;
      case SHORT, SSHORT -> 2;
      case LONG, SLONG, FLOAT -> 4;
      case RATIONAL, SRATIONAL, DOUBLE -> 8;
      default -> 0;
    };
  }

  public int getType() {
    return type;
  }

  public int getCount() {
    return count;
  }

  /**
   * @return a copy of the raw value bytes
   */
  public byte[] bytes() {
    return data.clone();
  }

  /**
   * Decodes a text value: ASCII, or BYTE / UNDEFINED holding characters (e.g. ExifVersion). EXIF
   * declares ASCII but cameras commonly write UTF-8, so the bytes are decoded as UTF-8 up to the
   * first NUL.
   *
   * @return the trimmed text, empty when there is none
   */
  public String asString() {
    int end = 0;
    while (end < data.length && data[end] != 0) {
      end++;
    }
    return new String(data, 0, end, StandardCharsets.UTF_8).trim();
  }

  /**
   * Decodes an integer value (BYTE, SHORT, LONG and their signed variants, UNDEFINED as unsigned
   * bytes).
   *
   * @return the values, empty for a non-integer type
   */
  public long[] asLongs() {
    if (!isInteger()) {
      return new long[0];
    }
    ByteBuffer buf = buffer();
    long[] values = new long[count];
    for (int i = 0; i < count; i++) {
      values[i] =
          switch (type) {
            case SBYTE -> buf.get();
            case SHORT -> Short.toUnsignedLong(buf.getShort());
            case SSHORT -> buf.getShort();
            case LONG -> Integer.toUnsignedLong(buf.getInt());
            case SLONG -> buf.getInt();
            default -> Byte.toUnsignedLong(buf.get()); // BYTE, UNDEFINED
          };
    }
    return values;
  }

  /**
   * @return true for the rational and floating-point types
   */
  public boolean isFractional() {
    return switch (type) {
      case RATIONAL, SRATIONAL, FLOAT, DOUBLE -> true;
      default -> false;
    };
  }

  private boolean isInteger() {
    return switch (type) {
      case BYTE, UNDEFINED, SBYTE, SHORT, SSHORT, LONG, SLONG -> true;
      default -> false;
    };
  }

  /**
   * Decodes a numeric value as decimals. A rational whose denominator is 0 or FFFFFFFFH (the EXIF
   * markers of an unknown value) gives {@link Double#NaN}.
   *
   * @return the values, empty for a non-numeric type
   */
  public double[] asDoubles() {
    ByteBuffer buf = buffer();
    double[] values = new double[count];
    switch (type) {
      case RATIONAL -> {
        for (int i = 0; i < count; i++) {
          values[i] =
              rational(Integer.toUnsignedLong(buf.getInt()), Integer.toUnsignedLong(buf.getInt()));
        }
      }
      case SRATIONAL -> {
        for (int i = 0; i < count; i++) {
          int numerator = buf.getInt();
          int denominator = buf.getInt();
          // FFFFFFFFH is -1 as a signed value: still the "unknown" marker
          values[i] = denominator == -1 ? Double.NaN : rational(numerator, denominator);
        }
      }
      case FLOAT -> {
        for (int i = 0; i < count; i++) {
          values[i] = buf.getFloat();
        }
      }
      case DOUBLE -> {
        for (int i = 0; i < count; i++) {
          values[i] = buf.getDouble();
        }
      }
      default -> {
        return Arrays.stream(asLongs()).asDoubleStream().toArray();
      }
    }
    return values;
  }

  private static double rational(long numerator, long denominator) {
    if (denominator == 0 || denominator == UNKNOWN_DENOMINATOR) {
      return Double.NaN;
    }
    return numerator / (double) denominator;
  }

  private ByteBuffer buffer() {
    return ByteBuffer.wrap(data).order(order);
  }

  @Override
  public String toString() {
    return "ExifValue{type=" + type + ", count=" + count + '}';
  }
}
