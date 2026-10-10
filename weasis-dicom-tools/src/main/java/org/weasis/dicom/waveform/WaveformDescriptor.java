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

import java.util.Objects;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Sequence;
import org.dcm4che3.data.Tag;
import org.weasis.opencv.waveform.WaveformSampleInterpretation;

/**
 * The attributes of one Waveform Sequence (5400,0100) Item (one multiplex group) that drive the
 * decoding and the encoding of its Waveform Data (5400,1010).
 *
 * @param channels Number of Waveform Channels (003A,0005)
 * @param numberOfSamples Number of Waveform Samples (003A,0010)
 * @param samplingFrequency Sampling Frequency (003A,001A) in Hz
 * @param bitsAllocated Waveform Bits Allocated (5400,1004): 8, 16 or 32
 * @param bitsStored the largest Waveform Bits Stored (003A,021A) of the Channel Definition
 *     Sequence, or {@code bitsAllocated} when absent (capped at 24 for 32-bit samples)
 * @param interpretation Waveform Sample Interpretation (5400,1006)
 * @param label Multiplex Group Label (003A,0020), may be null
 */
public record WaveformDescriptor(
    int channels,
    long numberOfSamples,
    double samplingFrequency,
    int bitsAllocated,
    int bitsStored,
    WaveformSampleInterpretation interpretation,
    String label) {

  /** The largest Bits Stored the T.261 codec accepts (Sup 253). */
  public static final int T261_MAX_BITS_STORED = 24;

  public WaveformDescriptor {
    Objects.requireNonNull(interpretation);
    if (channels <= 0) {
      throw new IllegalArgumentException("Number of Waveform Channels must be positive");
    }
    if (numberOfSamples < 0) {
      throw new IllegalArgumentException("Negative Number of Waveform Samples");
    }
    if (bitsAllocated != interpretation.bitsAllocated()) {
      throw new IllegalArgumentException(
          "Waveform Bits Allocated "
              + bitsAllocated
              + " does not match Waveform Sample Interpretation "
              + interpretation);
    }
    if (bitsStored <= 0 || bitsStored > bitsAllocated) {
      throw new IllegalArgumentException("Waveform Bits Stored must be 1.." + bitsAllocated);
    }
  }

  /** Reads the descriptor of one Waveform Sequence Item. */
  public static WaveformDescriptor of(Attributes item) {
    int channels = item.getInt(Tag.NumberOfWaveformChannels, 0);
    long samples = item.getInt(Tag.NumberOfWaveformSamples, 0) & 0xFFFFFFFFL;
    double frequency = item.getDouble(Tag.SamplingFrequency, 0.0);
    int bitsAllocated = item.getInt(Tag.WaveformBitsAllocated, 0);
    WaveformSampleInterpretation interpretation =
        WaveformSampleInterpretation.fromDicom(item.getString(Tag.WaveformSampleInterpretation));
    int bitsStored = maxBitsStored(item.getSequence(Tag.ChannelDefinitionSequence));
    if (bitsStored <= 0) {
      bitsStored = Math.min(bitsAllocated, T261_MAX_BITS_STORED);
    }
    return new WaveformDescriptor(
        channels,
        samples,
        frequency,
        bitsAllocated,
        bitsStored,
        interpretation,
        item.getString(Tag.MultiplexGroupLabel));
  }

  private static int maxBitsStored(Sequence channelDefinitions) {
    int max = 0;
    if (channelDefinitions != null) {
      for (Attributes channel : channelDefinitions) {
        max = Math.max(max, channel.getInt(Tag.WaveformBitsStored, 0));
      }
    }
    return max;
  }

  /** The native length of the Waveform Data in bytes. */
  public long nativeLength() {
    return numberOfSamples * channels * interpretation.bytesPerSample();
  }

  /** The sample window the transcoder reads at once from native data (about 1 MB). */
  public int preferredWindow() {
    int bytesPerSampleSet = channels * interpretation.bytesPerSample();
    return (int) Math.max(1024, Math.min(numberOfSamples, (1 << 20) / bytesPerSampleSet));
  }
}
