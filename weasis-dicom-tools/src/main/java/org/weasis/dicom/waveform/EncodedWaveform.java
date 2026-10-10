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

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.BulkData;
import org.dcm4che3.data.Fragments;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.VR;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.weasis.opencv.waveform.T261Parameters;
import org.weasis.opencv.waveform.T261WaveformWriter;
import org.weasis.opencv.waveform.WaveformReader;

/**
 * The Waveform Data (5400,1010) of one multiplex group produced by a transcoding, spooled to a
 * temporary file so that the data set can be written with {@code DicomOutputStream} without holding
 * the samples in memory. Close it after the data set has been written: the temporary file is
 * deleted.
 *
 * <p>For a T.261 transfer syntax the value is a {@link Fragments}: one Item per frame sequence of
 * the stream, preceded by a filled Basic Offset Table (the byte offset of each Item from the first
 * Item after the table, as for pixel data), so that a reader can locate any frame sequence without
 * decoding. For a native transfer syntax the value is a {@link BulkData} of little-endian samples,
 * sample-interleaved, at the Waveform Bits Allocated of the Item.
 */
public final class EncodedWaveform implements AutoCloseable {

  private final Path file;
  private final VR vr;
  private final Object value;
  private final int items;
  private final long samples;

  private EncodedWaveform(Path file, VR vr, Object value, int items, long samples) {
    this.file = file;
    this.vr = vr;
    this.value = value;
    this.items = items;
    this.samples = samples;
  }

  /**
   * Encodes the samples of a reader into a T.261 stream, one Item per frame sequence. Memory is
   * bounded by one source window plus one frame sequence whatever the length of the recording.
   */
  public static EncodedWaveform encodeT261(WaveformReader source, T261Parameters parameters)
      throws IOException {
    Path file = Files.createTempFile("t261-", ".bws");
    try {
      List<Long> lengths = new ArrayList<>();
      try (OutputStream out = new BufferedOutputStream(Files.newOutputStream(file), 1 << 16)) {
        T261WaveformWriter.encode(
            source,
            parameters,
            item -> {
              try {
                out.write(item);
                lengths.add((long) item.length);
              } catch (IOException e) {
                throw new UncheckedIOException(e);
              }
            });
      } catch (UncheckedIOException e) {
        throw e.getCause();
      }
      return new EncodedWaveform(
          file, VR.OB, fragmentsOf(file, lengths), lengths.size(), source.length());
    } catch (IOException | RuntimeException e) {
      Files.deleteIfExists(file);
      throw e;
    }
  }

  /**
   * Decodes the samples of a reader to native Waveform Data at the Bits Allocated of the
   * descriptor. Only signed interpretations are produced, as T.261 streams carry signed samples.
   */
  public static EncodedWaveform decodeNative(WaveformReader source, WaveformDescriptor desc)
      throws IOException {
    if (!desc.interpretation().isSigned()) {
      throw new IOException(
          "T.261 carries signed samples; Waveform Sample Interpretation "
              + desc.interpretation()
              + " is not supported");
    }
    if (source.channels() != desc.channels()) {
      throw new IOException(
          "The stream has "
              + source.channels()
              + " channels, the Item declares "
              + desc.channels());
    }
    Path file = Files.createTempFile("waveform-", ".raw");
    try {
      int bytesPerSample = desc.interpretation().bytesPerSample();
      long written = 0;
      try (OutputStream out = new BufferedOutputStream(Files.newOutputStream(file), 1 << 16)) {
        long length = source.length();
        int window = Math.max(1, source.preferredWindow());
        for (long start = 0; start < length; start += window) {
          int count = (int) Math.min(window, length - start);
          Mat chunk = source.read(start, count);
          try {
            byte[] bytes = toLittleEndian(chunk, desc.interpretation().cvType());
            out.write(bytes);
            written += bytes.length;
          } finally {
            chunk.release();
          }
        }
      }
      if (written % 2 != 0) {
        throw new IOException("Odd native waveform length " + written);
      }
      VR vr = bytesPerSample == 1 ? VR.OB : VR.OW;
      BulkData bulk = new BulkData(file.toUri().toString(), 0, written, false);
      return new EncodedWaveform(file, vr, bulk, 1, source.length());
    } catch (IOException | RuntimeException e) {
      Files.deleteIfExists(file);
      throw e;
    }
  }

  private static Fragments fragmentsOf(Path file, List<Long> lengths) {
    String uri = file.toUri().toString();
    Fragments fragments = new Fragments(VR.OB, false, lengths.size() + 1);
    ByteBuffer table = ByteBuffer.allocate(4 * lengths.size()).order(ByteOrder.LITTLE_ENDIAN);
    long fileOffset = 0;
    long itemOffset = 0;
    for (long length : lengths) {
      if (itemOffset > 0xFFFFFFFFL) {
        throw new IllegalStateException("Waveform stream beyond the 32-bit Basic Offset Table");
      }
      table.putInt((int) itemOffset);
      fragments.add(new BulkData(uri, fileOffset, length, false));
      fileOffset += length;
      itemOffset += 8 + ((length + 1) & ~1L);
    }
    fragments.add(0, table.array());
    return fragments;
  }

  private static byte[] toLittleEndian(Mat chunk, int cvType) {
    Mat converted = chunk;
    if (chunk.type() != cvType) {
      converted = new Mat();
      chunk.convertTo(converted, cvType);
    }
    try {
      int count = (int) converted.total();
      switch (CvType.depth(cvType)) {
        case CvType.CV_8S -> {
          byte[] bytes = new byte[count];
          converted.get(0, 0, bytes);
          return bytes;
        }
        case CvType.CV_16S -> {
          short[] values = new short[count];
          converted.get(0, 0, values);
          ByteBuffer buf = ByteBuffer.allocate(2 * count).order(ByteOrder.LITTLE_ENDIAN);
          buf.asShortBuffer().put(values);
          return buf.array();
        }
        case CvType.CV_32S -> {
          int[] values = new int[count];
          converted.get(0, 0, values);
          ByteBuffer buf = ByteBuffer.allocate(4 * count).order(ByteOrder.LITTLE_ENDIAN);
          buf.asIntBuffer().put(values);
          return buf.array();
        }
        default -> throw new IllegalArgumentException("Unsupported sample type " + cvType);
      }
    } finally {
      if (converted != chunk) {
        converted.release();
      }
    }
  }

  /** The {@link Fragments} (T.261) or {@link BulkData} (native) to set as Waveform Data. */
  public Object value() {
    return value;
  }

  /** OB for T.261 Items and 8-bit samples, OW otherwise. */
  public VR vr() {
    return vr;
  }

  /** The number of Items (T.261: one per frame sequence), 1 for native data. */
  public int items() {
    return items;
  }

  /** The number of samples per channel. */
  public long samples() {
    return samples;
  }

  /** The temporary file holding the data; deleted by {@link #close()}. */
  public Path file() {
    return file;
  }

  /** Sets this data as the Waveform Data (5400,1010) of a Waveform Sequence Item. */
  public void setTo(Attributes item) {
    item.setValue(Tag.WaveformData, vr, value);
  }

  @Override
  public void close() throws IOException {
    Files.deleteIfExists(file);
  }
}
