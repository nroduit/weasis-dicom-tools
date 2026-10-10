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

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Random;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.BulkData;
import org.dcm4che3.data.Fragments;
import org.dcm4che3.data.Sequence;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.UID;
import org.dcm4che3.data.VR;
import org.dcm4che3.io.DicomInputStream;
import org.dcm4che3.io.DicomInputStream.IncludeBulkData;
import org.dcm4che3.io.DicomOutputStream;
import org.dcm4che3.util.UIDUtils;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.weasis.opencv.natives.NativeLibrary;
import org.weasis.opencv.waveform.NativeWaveformReader;
import org.weasis.opencv.waveform.T261WaveformReader;
import org.weasis.opencv.waveform.WaveformReader;

@DisplayNameGeneration(ReplaceUnderscores.class)
class WaveformTranscoderTest {

  private static final int CHANNELS = 12;
  private static final int SAMPLES = 5000;
  private static final double FREQUENCY = 500.0;
  private static final int INTRA_PERIOD = 1024;

  @TempDir Path dir;

  @BeforeAll
  static void loadNativeLib() {
    NativeLibrary.loadLibraryFromLibraryName();
  }

  /** 12-bit ECG-like samples: a sine per channel plus noise, sample-interleaved. */
  private static short[] ecgSamples() {
    Random random = new Random(42);
    short[] samples = new short[SAMPLES * CHANNELS];
    for (int s = 0; s < SAMPLES; s++) {
      for (int c = 0; c < CHANNELS; c++) {
        double value =
            1500 * Math.sin(2 * Math.PI * (c + 1) * s / 400.0) + random.nextGaussian() * 40;
        samples[s * CHANNELS + c] = (short) Math.max(-2048, Math.min(2047, Math.round(value)));
      }
    }
    return samples;
  }

  private static byte[] littleEndian(short[] samples) {
    ByteBuffer buf = ByteBuffer.allocate(2 * samples.length).order(ByteOrder.LITTLE_ENDIAN);
    buf.asShortBuffer().put(samples);
    return buf.array();
  }

  private static Attributes ecgDataset(short[] samples) {
    Attributes dcm = new Attributes();
    dcm.setString(Tag.SOPClassUID, VR.UI, UID.TwelveLeadECGWaveformStorage);
    dcm.setString(Tag.SOPInstanceUID, VR.UI, UIDUtils.createUID());
    dcm.setString(Tag.Modality, VR.CS, "ECG");
    dcm.setString(Tag.PatientName, VR.PN, "Waveform^Test");
    Sequence groups = dcm.newSequence(Tag.WaveformSequence, 1);
    Attributes group = new Attributes();
    group.setString(Tag.MultiplexGroupLabel, VR.SH, "12-lead");
    group.setInt(Tag.NumberOfWaveformChannels, VR.US, CHANNELS);
    group.setInt(Tag.NumberOfWaveformSamples, VR.UL, SAMPLES);
    group.setDouble(Tag.SamplingFrequency, VR.DS, FREQUENCY);
    group.setInt(Tag.WaveformBitsAllocated, VR.US, 16);
    group.setString(Tag.WaveformSampleInterpretation, VR.CS, "SS");
    Sequence channels = group.newSequence(Tag.ChannelDefinitionSequence, CHANNELS);
    for (int c = 0; c < CHANNELS; c++) {
      Attributes channel = new Attributes();
      channel.setInt(Tag.WaveformBitsStored, VR.US, 12);
      channel.setDouble(Tag.ChannelSensitivity, VR.DS, 1.25);
      channels.add(channel);
    }
    group.setBytes(Tag.WaveformData, VR.OW, littleEndian(samples));
    groups.add(group);
    return dcm;
  }

  private Path writeNativeFile(short[] samples) throws IOException {
    Path file = dir.resolve("ecg-native.dcm");
    Attributes dcm = ecgDataset(samples);
    try (DicomOutputStream dos = new DicomOutputStream(file.toFile())) {
      dos.writeDataset(dcm.createFileMetaInformation(UID.ExplicitVRLittleEndian), dcm);
    }
    return file;
  }

  private static Attributes read(Path file, IncludeBulkData mode) throws IOException {
    try (DicomInputStream dis = new DicomInputStream(file.toFile())) {
      dis.setIncludeBulkData(mode);
      return dis.readDataset();
    }
  }

  private static String transferSyntax(Path file) throws IOException {
    try (DicomInputStream dis = new DicomInputStream(file.toFile())) {
      dis.setIncludeBulkData(IncludeBulkData.NO);
      dis.readDataset();
      return dis.getTransferSyntax();
    }
  }

  private static short[] readAll(WaveformReader reader) {
    Mat window = reader.read(0, (int) reader.length());
    try {
      assertEquals(CvType.CV_16S, window.type());
      short[] values = new short[(int) window.total()];
      window.get(0, 0, values);
      return values;
    } finally {
      window.release();
    }
  }

  private static WaveformTranscodeParam losslessParam() {
    WaveformTranscodeParam param = new WaveformTranscodeParam(T261TransferSyntax.lossless());
    param.setIntraPeriod(INTRA_PERIOD);
    return param;
  }

  @Test
  void native_file_opens_as_memory_mapped_reader() throws IOException {
    short[] samples = ecgSamples();
    Path file = writeNativeFile(samples);
    Attributes dcm = read(file, IncludeBulkData.URI);
    Attributes group = dcm.getSequence(Tag.WaveformSequence).get(0);
    assertInstanceOf(BulkData.class, group.getValue(Tag.WaveformData));

    WaveformDescriptor desc = WaveformDescriptor.of(group);
    assertEquals(12, desc.bitsStored());
    assertEquals(SAMPLES, desc.numberOfSamples());
    try (WaveformReader reader = WaveformReaders.open(group, UID.ExplicitVRLittleEndian)) {
      assertInstanceOf(NativeWaveformReader.class, reader);
      assertEquals(CHANNELS, reader.channels());
      assertEquals(FREQUENCY, reader.samplingFrequency());
      assertArrayEquals(samples, readAll(reader));
    }
  }

  @Test
  void lossless_transcoding_writes_one_item_per_frame_sequence_with_offset_table()
      throws IOException {
    short[] samples = ecgSamples();
    Path src = writeNativeFile(samples);
    Path dst = WaveformTranscoder.dcm2dcm(src, dir.resolve("ecg-t261.dcm"), losslessParam());

    assertEquals(T261TransferSyntax.lossless(), transferSyntax(dst));
    Attributes dcm = read(dst, IncludeBulkData.URI);
    Attributes group = dcm.getSequence(Tag.WaveformSequence).get(0);
    assertEquals("Waveform^Test", dcm.getString(Tag.PatientName));
    assertEquals(SAMPLES, group.getInt(Tag.NumberOfWaveformSamples, 0));

    Object value = group.getValue(Tag.WaveformData);
    Fragments fragments = assertInstanceOf(Fragments.class, value);
    int expectedItems = (SAMPLES + INTRA_PERIOD - 1) / INTRA_PERIOD;
    assertEquals(expectedItems + 1, fragments.size());

    BulkData table = assertInstanceOf(BulkData.class, fragments.get(0));
    assertEquals(4 * expectedItems, table.length());
    ByteBuffer offsets =
        ByteBuffer.wrap(Files.readAllBytes(dst), (int) table.offset(), table.length())
            .order(ByteOrder.LITTLE_ENDIAN);
    long firstItem = ((BulkData) fragments.get(1)).offset() - 8;
    for (int i = 0; i < expectedItems; i++) {
      BulkData item = (BulkData) fragments.get(i + 1);
      assertEquals(item.offset() - 8 - firstItem, offsets.getInt() & 0xFFFFFFFFL, "Item " + i);
    }

    try (WaveformReader reader = WaveformReaders.open(group, T261TransferSyntax.lossless())) {
      T261WaveformReader t261 = assertInstanceOf(T261WaveformReader.class, reader);
      assertEquals(expectedItems, t261.frameSequences());
      assertEquals(12, t261.bitDepth());
      assertEquals(FREQUENCY, reader.samplingFrequency());
      assertArrayEquals(samples, readAll(reader));
    }
    assertTrue(Files.size(dst) < Files.size(src) * 0.7, "T.261 lossless should compress the ECG");
  }

  @Test
  void t261_items_read_from_memory_decode_to_the_same_samples() throws IOException {
    short[] samples = ecgSamples();
    Path dst =
        WaveformTranscoder.dcm2dcm(
            writeNativeFile(samples), dir.resolve("ecg-t261.dcm"), losslessParam());
    Attributes dcm = read(dst, IncludeBulkData.YES);
    Attributes group = dcm.getSequence(Tag.WaveformSequence).get(0);
    Fragments fragments = assertInstanceOf(Fragments.class, group.getValue(Tag.WaveformData));
    assertInstanceOf(byte[].class, fragments.get(1));
    try (WaveformReader reader = WaveformReaders.open(group, T261TransferSyntax.lossless())) {
      assertArrayEquals(samples, readAll(reader));
    }
  }

  @Test
  void transcoding_back_to_native_restores_the_original_waveform_data() throws IOException {
    short[] samples = ecgSamples();
    Path src = writeNativeFile(samples);
    Path t261 = WaveformTranscoder.dcm2dcm(src, dir.resolve("ecg-t261.dcm"), losslessParam());
    Path back =
        WaveformTranscoder.dcm2dcm(
            t261,
            dir.resolve("ecg-back.dcm"),
            new WaveformTranscodeParam(UID.ExplicitVRLittleEndian));

    assertEquals(UID.ExplicitVRLittleEndian, transferSyntax(back));
    Attributes dcm = read(back, IncludeBulkData.YES);
    Attributes group = dcm.getSequence(Tag.WaveformSequence).get(0);
    VR.Holder vr = new VR.Holder();
    byte[] data = assertInstanceOf(byte[].class, group.getValue(Tag.WaveformData, vr));
    assertEquals(VR.OW, vr.vr);
    assertArrayEquals(littleEndian(samples), data);
    assertEquals(Files.size(src), Files.size(back));
  }

  @Test
  void lossy_transcoding_keeps_the_error_of_the_order_of_the_step_size() throws IOException {
    short[] samples = ecgSamples();
    Path src = writeNativeFile(samples);
    Path lossless = WaveformTranscoder.dcm2dcm(src, dir.resolve("lossless.dcm"), losslessParam());
    WaveformTranscodeParam param = new WaveformTranscodeParam(T261TransferSyntax.lossy());
    param.setIntraPeriod(INTRA_PERIOD);
    param.setStepSize(8.0);
    Path lossy = WaveformTranscoder.dcm2dcm(lossless, dir.resolve("lossy.dcm"), param);

    assertEquals(T261TransferSyntax.lossy(), transferSyntax(lossy));
    assertTrue(Files.size(lossy) < Files.size(lossless), "lossy should be smaller than lossless");
    Attributes group = read(lossy, IncludeBulkData.URI).getSequence(Tag.WaveformSequence).get(0);
    try (WaveformReader reader = WaveformReaders.open(group, T261TransferSyntax.lossy())) {
      short[] decoded = readAll(reader);
      int maxError = 0;
      for (int i = 0; i < samples.length; i++) {
        maxError = Math.max(maxError, Math.abs(decoded[i] - samples[i]));
      }
      assertTrue(maxError > 0, "lossy should not be exact");
      assertTrue(maxError <= 16, "max error " + maxError + " exceeds twice the step size");
    }
  }

  @Test
  void unsigned_samples_are_rejected_for_t261() throws IOException {
    Attributes dcm = ecgDataset(ecgSamples());
    Attributes group = dcm.getSequence(Tag.WaveformSequence).get(0);
    group.setString(Tag.WaveformSampleInterpretation, VR.CS, "US");
    Path file = dir.resolve("unsigned.dcm");
    try (DicomOutputStream dos = new DicomOutputStream(file.toFile())) {
      dos.writeDataset(dcm.createFileMetaInformation(UID.ExplicitVRLittleEndian), dcm);
    }
    assertThrows(
        IOException.class,
        () -> WaveformTranscoder.dcm2dcm(file, dir.resolve("out.dcm"), losslessParam()));
    assertTrue(Files.notExists(dir.resolve("out.dcm")));
  }

  @Test
  void native_to_native_keeps_the_waveform_data_untouched() throws IOException {
    short[] samples = ecgSamples();
    Attributes dcm = read(writeNativeFile(samples), IncludeBulkData.URI);
    assertTrue(
        WaveformTranscoder.transcode(
                dcm,
                UID.ExplicitVRLittleEndian,
                new WaveformTranscodeParam(UID.ImplicitVRLittleEndian))
            .isEmpty());
  }
}
