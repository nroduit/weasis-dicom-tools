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

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Builds TIFF-structured EXIF blocks (IFD0, Exif and GPS sub-IFDs) for the tests. */
final class TiffBuilder {

  enum Ifd {
    IMAGE,
    EXIF,
    GPS
  }

  private record Entry(int tag, int type, int count, byte[] data) {}

  private final ByteOrder order;
  private final List<Entry> image = new ArrayList<>();
  private final List<Entry> exif = new ArrayList<>();
  private final List<Entry> gps = new ArrayList<>();

  TiffBuilder(ByteOrder order) {
    this.order = order;
  }

  TiffBuilder ascii(Ifd ifd, int tag, String value) {
    byte[] text = (value + "\0").getBytes(StandardCharsets.UTF_8);
    return raw(ifd, tag, ExifValue.ASCII, text.length, text);
  }

  TiffBuilder undefined(Ifd ifd, int tag, byte[] value) {
    return raw(ifd, tag, ExifValue.UNDEFINED, value.length, value);
  }

  TiffBuilder bytes(Ifd ifd, int tag, int... values) {
    byte[] data = new byte[values.length];
    for (int i = 0; i < values.length; i++) {
      data[i] = (byte) values[i];
    }
    return raw(ifd, tag, ExifValue.BYTE, values.length, data);
  }

  TiffBuilder shorts(Ifd ifd, int tag, int... values) {
    ByteBuffer buf = allocate(values.length * 2);
    Arrays.stream(values).forEach(v -> buf.putShort((short) v));
    return raw(ifd, tag, ExifValue.SHORT, values.length, buf.array());
  }

  TiffBuilder longs(Ifd ifd, int tag, long... values) {
    ByteBuffer buf = allocate(values.length * 4);
    Arrays.stream(values).forEach(v -> buf.putInt((int) v));
    return raw(ifd, tag, ExifValue.LONG, values.length, buf.array());
  }

  /** Numerator / denominator pairs. */
  TiffBuilder rationals(Ifd ifd, int tag, long... pairs) {
    return fraction(ifd, tag, ExifValue.RATIONAL, pairs);
  }

  /** Numerator / denominator pairs. */
  TiffBuilder srationals(Ifd ifd, int tag, long... pairs) {
    return fraction(ifd, tag, ExifValue.SRATIONAL, pairs);
  }

  private TiffBuilder fraction(Ifd ifd, int tag, int type, long... pairs) {
    ByteBuffer buf = allocate(pairs.length * 4);
    Arrays.stream(pairs).forEach(v -> buf.putInt((int) v));
    return raw(ifd, tag, type, pairs.length / 2, buf.array());
  }

  TiffBuilder raw(Ifd ifd, int tag, int type, int count, byte[] data) {
    Entry entry = new Entry(tag, type, count, data);
    switch (ifd) {
      case IMAGE -> image.add(entry);
      case EXIF -> exif.add(entry);
      case GPS -> gps.add(entry);
    }
    return this;
  }

  byte[] build() {
    List<Entry> first = new ArrayList<>(image);
    // Pointer entries are LONG values patched once the offsets are known
    int exifPointer = exif.isEmpty() ? -1 : first.size();
    if (exifPointer >= 0) {
      first.add(new Entry(ExifData.EXIF_IFD_POINTER, ExifValue.LONG, 1, new byte[4]));
    }
    int gpsPointer = gps.isEmpty() ? -1 : first.size();
    if (gpsPointer >= 0) {
      first.add(new Entry(ExifData.GPS_IFD_POINTER, ExifValue.LONG, 1, new byte[4]));
    }
    int exifOffset = 8 + size(first);
    int gpsOffset = exifOffset + size(exif);
    if (exifPointer >= 0) {
      first.set(exifPointer, pointer(ExifData.EXIF_IFD_POINTER, exifOffset));
    }
    if (gpsPointer >= 0) {
      first.set(gpsPointer, pointer(ExifData.GPS_IFD_POINTER, gpsOffset));
    }

    ByteArrayOutputStream out = new ByteArrayOutputStream();
    ByteBuffer header = allocate(8);
    header.put(order == ByteOrder.LITTLE_ENDIAN ? new byte[] {'I', 'I'} : new byte[] {'M', 'M'});
    header.putShort((short) 42).putInt(8);
    out.writeBytes(header.array());
    out.writeBytes(ifd(first, 8));
    out.writeBytes(ifd(exif, exifOffset));
    out.writeBytes(ifd(gps, gpsOffset));
    return out.toByteArray();
  }

  /** Wraps an EXIF block into a minimal JPEG header: SOI, APP0, an XMP APP1, the Exif APP1, SOS. */
  static byte[] jpeg(byte[] tiff) {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    out.writeBytes(new byte[] {(byte) 0xFF, (byte) 0xD8});
    segment(out, 0xE0, "JFIF\0".getBytes(StandardCharsets.US_ASCII));
    segment(out, 0xE1, "http://ns.adobe.com/xap/1.0/\0<x/>".getBytes(StandardCharsets.US_ASCII));
    ByteArrayOutputStream exif = new ByteArrayOutputStream();
    exif.writeBytes(new byte[] {'E', 'x', 'i', 'f', 0, 0});
    exif.writeBytes(tiff);
    segment(out, 0xE1, exif.toByteArray());
    segment(out, 0xDA, new byte[] {0});
    return out.toByteArray();
  }

  private static void segment(ByteArrayOutputStream out, int marker, byte[] payload) {
    int length = payload.length + 2;
    out.writeBytes(new byte[] {(byte) 0xFF, (byte) marker, (byte) (length >> 8), (byte) length});
    out.writeBytes(payload);
  }

  private Entry pointer(int tag, int offset) {
    return new Entry(tag, ExifValue.LONG, 1, allocate(4).putInt(offset).array());
  }

  private static int size(List<Entry> entries) {
    if (entries.isEmpty()) {
      return 0;
    }
    int size = 2 + entries.size() * 12 + 4;
    for (Entry e : entries) {
      if (e.data().length > 4) {
        size += e.data().length + (e.data().length & 1);
      }
    }
    return size;
  }

  private byte[] ifd(List<Entry> entries, int offset) {
    if (entries.isEmpty()) {
      return new byte[0];
    }
    ByteBuffer buf = allocate(size(entries));
    buf.putShort((short) entries.size());
    int dataPos = offset + 2 + entries.size() * 12 + 4;
    ByteBuffer data = allocate(buf.capacity());
    for (Entry e : entries) {
      buf.putShort((short) e.tag()).putShort((short) e.type()).putInt(e.count());
      if (e.data().length <= 4) {
        buf.put(Arrays.copyOf(e.data(), 4));
      } else {
        buf.putInt(dataPos);
        data.put(e.data());
        if ((e.data().length & 1) != 0) {
          data.put((byte) 0);
        }
        dataPos += e.data().length + (e.data().length & 1);
      }
    }
    buf.putInt(0); // No next IFD
    buf.put(data.array(), 0, data.position());
    return buf.array();
  }

  private ByteBuffer allocate(int size) {
    return ByteBuffer.allocate(size).order(order);
  }
}
