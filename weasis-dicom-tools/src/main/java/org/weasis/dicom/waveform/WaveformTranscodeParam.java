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
import org.weasis.opencv.waveform.T261Parameters;
import org.weasis.opencv.waveform.T261Parameters.SignalType;

/**
 * Parameters of a waveform transcoding: the destination transfer syntax (a native one or one of
 * {@link T261TransferSyntax}) and the T.261 encoder options.
 */
public class WaveformTranscodeParam {

  private final String outputTsuid;
  private boolean outputFmi = true;
  private double stepSize;
  private SignalType signalType = SignalType.ECG;
  private int intraPeriod;
  private int profileLevel;

  /**
   * @param outputTsuid the destination transfer syntax: a native one, {@link
   *     T261TransferSyntax#lossless()} or {@link T261TransferSyntax#lossy()}
   */
  public WaveformTranscodeParam(String outputTsuid) {
    this.outputTsuid = Objects.requireNonNull(outputTsuid);
  }

  public String getOutputTsuid() {
    return outputTsuid;
  }

  public boolean isOutputFmi() {
    return outputFmi;
  }

  /** Whether to write the File Meta Information (Part 10 file); true by default. */
  public void setOutputFmi(boolean outputFmi) {
    this.outputFmi = outputFmi;
  }

  public double getStepSize() {
    return stepSize;
  }

  /**
   * The quantization step size of the lossy transfer syntax (0 keeps the preset value of the codec,
   * 1.0 is lossless). The sample error grows with it and is of its order in sample units, not
   * strictly bounded by it: the quantization applies in the transform domain.
   */
  public void setStepSize(double stepSize) {
    if (stepSize < 0) {
      throw new IllegalArgumentException("Negative step size");
    }
    this.stepSize = stepSize;
  }

  public SignalType getSignalType() {
    return signalType;
  }

  /** The preset family of the encoder (ECG by default). */
  public void setSignalType(SignalType signalType) {
    this.signalType = Objects.requireNonNull(signalType);
  }

  public int getIntraPeriod() {
    return intraPeriod;
  }

  /**
   * The frame sequence length in samples, the unit of random access and of each Item of the
   * encapsulated Waveform Data (0 keeps the preset value, 98 304 samples).
   */
  public void setIntraPeriod(int intraPeriod) {
    if (intraPeriod < 0) {
      throw new IllegalArgumentException("Negative intra period");
    }
    this.intraPeriod = intraPeriod;
  }

  public int getProfileLevel() {
    return profileLevel;
  }

  /** The T.261 Main profile level, 0 for the preset value. */
  public void setProfileLevel(int profileLevel) {
    this.profileLevel = profileLevel;
  }

  /** True when the destination is one of the T.261 transfer syntaxes. */
  public boolean isT261Output() {
    return T261TransferSyntax.isT261(outputTsuid);
  }

  /** The encoder parameters for one multiplex group, derived from its descriptor. */
  public T261Parameters t261Parameters(WaveformDescriptor desc) {
    if (!desc.interpretation().isSigned()) {
      throw new IllegalArgumentException(
          "Sup 253 restricts T.261 to signed samples (SB, SS, SL); the Item uses "
              + desc.interpretation());
    }
    if (desc.bitsStored() > WaveformDescriptor.T261_MAX_BITS_STORED) {
      throw new IllegalArgumentException(
          "Waveform Bits Stored "
              + desc.bitsStored()
              + " exceeds the "
              + WaveformDescriptor.T261_MAX_BITS_STORED
              + " bits of T.261");
    }
    boolean lossless = !T261TransferSyntax.isLossy(outputTsuid);
    return new T261Parameters(
        desc.channels(),
        desc.bitsAllocated(),
        desc.bitsStored(),
        desc.samplingFrequency(),
        lossless,
        lossless ? 0 : stepSize,
        signalType,
        intraPeriod,
        profileLevel);
  }
}
