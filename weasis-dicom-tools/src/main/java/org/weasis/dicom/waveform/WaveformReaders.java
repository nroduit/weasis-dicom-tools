/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.waveform;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.BulkData;
import org.dcm4che3.data.Fragments;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.VR;
import org.weasis.opencv.waveform.NativeWaveformReader;
import org.weasis.opencv.waveform.T261WaveformReader;
import org.weasis.opencv.waveform.WaveformReader;

/**
 * Opens a {@link WaveformReader} on the Waveform Data (5400,1010) of one Waveform Sequence Item, as
 * parsed by dcm4che.
 *
 * <p>Both representations of dcm4che are supported: {@link BulkData} / {@link Fragments} of {@link
 * BulkData} when the data set was read with {@code IncludeBulkData.URI} (the samples stay in the
 * file: native data is memory-mapped per window, a T.261 stream is decoded one frame sequence at a
 * time from the Items), and {@code byte[]} values when the data set was read in memory.
 */
public final class WaveformReaders {

  private WaveformReaders() {}

  /**
   * Opens a reader on the Waveform Data of one multiplex group.
   *
   * @param item a Waveform Sequence Item
   * @param tsuid the transfer syntax of the data set the Item belongs to
   * @return a reader on the samples; close it when done
   * @throws IOException when the Waveform Data is missing or cannot be read
   */
  public static WaveformReader open(Attributes item, String tsuid) throws IOException {
    return open(item, WaveformDescriptor.of(item), tsuid);
  }

  /** Same as {@link #open(Attributes, String)} with an already parsed descriptor. */
  public static WaveformReader open(Attributes item, WaveformDescriptor desc, String tsuid)
      throws IOException {
    VR.Holder vr = new VR.Holder();
    Object value = item.getValue(Tag.WaveformData, vr);
    if (value == null) {
      throw new IOException("No Waveform Data in the Waveform Sequence Item");
    }
    if (T261TransferSyntax.isT261(tsuid)) {
      return openT261(value, desc);
    }
    return openNative(value, desc, item.bigEndian());
  }

  private static WaveformReader openT261(Object value, WaveformDescriptor desc) throws IOException {
    if (value instanceof Fragments fragments) {
      List<BulkData> items = bulkItems(fragments);
      if (items != null) {
        long[] positions = new long[items.size()];
        long[] lengths = new long[items.size()];
        for (int i = 0; i < items.size(); i++) {
          positions[i] = items.get(i).offset();
          lengths[i] = items.get(i).longLength();
        }
        return T261WaveformReader.ofFile(
            pathOf(items.get(0)), positions, lengths, desc.samplingFrequency());
      }
      return T261WaveformReader.ofMemory(concatenate(fragments), desc.samplingFrequency());
    }
    if (value instanceof BulkData bulk) {
      return T261WaveformReader.ofMemory(readAll(bulk), desc.samplingFrequency());
    }
    if (value instanceof byte[] bytes) {
      return T261WaveformReader.ofMemory(bytes, desc.samplingFrequency());
    }
    throw new IOException("Unsupported Waveform Data value: " + value.getClass().getName());
  }

  private static WaveformReader openNative(
      Object value, WaveformDescriptor desc, boolean datasetBigEndian) throws IOException {
    if (value instanceof BulkData bulk) {
      return NativeWaveformReader.ofFile(
          pathOf(bulk),
          bulk.offset(),
          Math.min(bulk.longLength(), desc.nativeLength()),
          desc.channels(),
          desc.interpretation(),
          bulk.bigEndian(),
          desc.samplingFrequency());
    }
    if (value instanceof byte[] bytes) {
      return NativeWaveformReader.ofMemory(
          ByteBuffer.wrap(bytes, 0, (int) Math.min(bytes.length, desc.nativeLength())),
          desc.channels(),
          desc.interpretation(),
          datasetBigEndian,
          desc.samplingFrequency());
    }
    if (value instanceof Fragments) {
      throw new IOException("Encapsulated Waveform Data in a native transfer syntax");
    }
    throw new IOException("Unsupported Waveform Data value: " + value.getClass().getName());
  }

  /** The Items after the Basic Offset Table, when they all reference a file; null otherwise. */
  private static List<BulkData> bulkItems(Fragments fragments) {
    List<BulkData> items = new ArrayList<>(fragments.size());
    for (int i = 1; i < fragments.size(); i++) {
      if (fragments.get(i) instanceof BulkData bulk) {
        items.add(bulk);
      } else {
        return null;
      }
    }
    return items.isEmpty() ? null : items;
  }

  private static byte[] concatenate(Fragments fragments) throws IOException {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    for (int i = 1; i < fragments.size(); i++) {
      Object fragment = fragments.get(i);
      if (fragment instanceof byte[] bytes) {
        out.write(bytes);
      } else if (fragment instanceof BulkData bulk) {
        out.write(readAll(bulk));
      }
    }
    return out.toByteArray();
  }

  private static byte[] readAll(BulkData bulk) throws IOException {
    try (InputStream in = bulk.openStream()) {
      return in.readNBytes(bulk.length());
    }
  }

  static Path pathOf(BulkData bulk) throws IOException {
    try {
      return Paths.get(URI.create(bulk.uriWithoutOffsetAndLength()));
    } catch (IllegalArgumentException e) {
      throw new IOException("Bulk data is not a local file: " + bulk.getURI(), e);
    }
  }
}
