/*
 * Copyright (c) 2023 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.dcm4che3.imageio.codec.mp4;

import java.nio.ByteBuffer;

/**
 * @author Gunter Zeilinger (gunterze@protonmail.com)
 * @since Jan 2020
 */
public class MP4FileType {
  public static final int QT = 0x71742020;
  public static final int ISOM = 0x69736f6d;
  public static final MP4FileType ISOM_QT = new MP4FileType(ISOM, 0, ISOM, QT);

  private final int[] brands;

  public MP4FileType(int majorBrand, int minorVersion, int... compatibleBrands) {
    this.brands = new int[2 + compatibleBrands.length];
    brands[0] = majorBrand;
    brands[1] = minorVersion;
    System.arraycopy(compatibleBrands, 0, brands, 2, compatibleBrands.length);
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    append4CC(sb.append("ftyp["), brands[0]);
    sb.append('.').append(brands[1]);
    for (int i = 2; i < brands.length; i++) {
      append4CC(sb.append(", "), brands[i]);
    }
    sb.append(']');
    return sb.toString();
  }

  public byte[] toBytes() {
    ByteBuffer bb = ByteBuffer.allocate(size());
    bb.putInt(bb.remaining());
    bb.putInt(0x66747970);
    for (int brand : brands) {
      bb.putInt(brand);
    }
    return bb.array();
  }

  private static void append4CC(StringBuilder sb, int brand) {
    sb.append((char) ((brand >>> 24) & 0xFF));
    sb.append((char) ((brand >>> 16) & 0xFF));
    sb.append((char) ((brand >>> 8) & 0xFF));
    sb.append((char) ((brand) & 0xFF));
  }

  public int size() {
    return (2 + brands.length) * 4;
  }

  public int majorBrand() {
    return brands[0];
  }

  public int minorVersion() {
    return brands[1];
  }

  public int[] compatibleBrands() {
    int[] compatibleBrands = new int[brands.length - 2];
    System.arraycopy(brands, 2, compatibleBrands, 0, compatibleBrands.length);
    return compatibleBrands;
  }
}
