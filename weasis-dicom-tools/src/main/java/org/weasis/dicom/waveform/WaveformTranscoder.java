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
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Sequence;
import org.dcm4che3.data.Tag;
import org.dcm4che3.io.DicomInputStream;
import org.dcm4che3.io.DicomInputStream.IncludeBulkData;
import org.dcm4che3.io.DicomOutputStream;
import org.weasis.opencv.waveform.WaveformReader;

/**
 * Transcodes the Waveform Data of a DICOM waveform object between the native transfer syntaxes and
 * the T.261 ones of Supplement 253 (native to lossless or lossy, T.261 to native, lossless to
 * lossy), in both directions through streams: the samples are never held in memory as a whole.
 *
 * <p>Note: the supplement under public comment does not assign the Lossy Waveform Compression
 * attributes yet, so the lossy transcoding does not record the compression in the data set.
 */
public final class WaveformTranscoder {

  private WaveformTranscoder() {}

  /**
   * Transcodes a DICOM waveform file into another file.
   *
   * @param srcPath the source DICOM file
   * @param dstPath the destination file, or a directory in which the source file name is reused
   * @param params the destination transfer syntax and encoder options
   * @return the written file
   * @throws IOException when the source cannot be read or transcoded
   */
  public static Path dcm2dcm(Path srcPath, Path dstPath, WaveformTranscodeParam params)
      throws IOException {
    Path outPath = Files.isDirectory(dstPath) ? dstPath.resolve(srcPath.getFileName()) : dstPath;
    try (OutputStream out = new BufferedOutputStream(Files.newOutputStream(outPath), 1 << 16)) {
      dcm2dcm(srcPath, out, params);
    } catch (IOException | RuntimeException e) {
      Files.deleteIfExists(outPath);
      throw e;
    }
    return outPath;
  }

  /** Transcodes a DICOM waveform file to an output stream. */
  public static void dcm2dcm(Path srcPath, OutputStream outputStream, WaveformTranscodeParam params)
      throws IOException {
    try (DicomInputStream dis = new DicomInputStream(srcPath.toFile())) {
      dis.setIncludeBulkData(IncludeBulkData.URI);
      Attributes dataset = dis.readDataset();
      String srcTsuid = dis.getTransferSyntax();
      List<EncodedWaveform> encoded = transcode(dataset, srcTsuid, params);
      try {
        String dstTsuid = params.getOutputTsuid();
        Attributes fmi = params.isOutputFmi() ? dataset.createFileMetaInformation(dstTsuid) : null;
        try (DicomOutputStream dos = new DicomOutputStream(outputStream, dstTsuid)) {
          dos.writeDataset(fmi, dataset);
        }
      } finally {
        closeAll(encoded);
      }
    }
  }

  /**
   * Replaces the Waveform Data of every Waveform Sequence Item of a data set by its transcoded
   * value. The data set is modified in place and can then be written with {@code DicomOutputStream}
   * in the destination transfer syntax; close the returned values afterwards to delete their
   * temporary files. Nothing is done between two native transfer syntaxes.
   *
   * @param dataset a data set read with bulk data references or in memory
   * @param srcTsuid the transfer syntax of the data set
   * @param params the destination transfer syntax and encoder options
   * @return the transcoded values, one per multiplex group
   * @throws IOException when a multiplex group cannot be transcoded
   */
  public static List<EncodedWaveform> transcode(
      Attributes dataset, String srcTsuid, WaveformTranscodeParam params) throws IOException {
    boolean srcT261 = T261TransferSyntax.isT261(srcTsuid);
    boolean dstT261 = params.isT261Output();
    List<EncodedWaveform> encoded = new ArrayList<>();
    if (!srcT261 && !dstT261) {
      return encoded;
    }
    Sequence groups = dataset.getSequence(Tag.WaveformSequence);
    if (groups == null || groups.isEmpty()) {
      throw new IOException("No Waveform Sequence in the data set");
    }
    try {
      for (Attributes item : groups) {
        WaveformDescriptor desc = WaveformDescriptor.of(item);
        try (WaveformReader reader = WaveformReaders.open(item, desc, srcTsuid)) {
          EncodedWaveform value =
              dstT261
                  ? EncodedWaveform.encodeT261(reader, params.t261Parameters(desc))
                  : EncodedWaveform.decodeNative(reader, desc);
          encoded.add(value);
          value.setTo(item);
        } catch (IOException e) {
          throw e;
        } catch (Exception e) {
          throw new IOException("Transcoding the waveform group " + desc.label(), e);
        }
      }
    } catch (IOException | RuntimeException e) {
      closeAll(encoded);
      throw e;
    }
    return encoded;
  }

  private static void closeAll(List<EncodedWaveform> encoded) throws IOException {
    IOException first = null;
    for (EncodedWaveform value : encoded) {
      try {
        value.close();
      } catch (IOException e) {
        if (first == null) {
          first = e;
        }
      }
    }
    if (first != null) {
      throw first;
    }
  }
}
