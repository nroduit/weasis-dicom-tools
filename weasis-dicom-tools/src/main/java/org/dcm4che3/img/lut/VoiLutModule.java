/*
 * Copyright (c) 2021 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.dcm4che3.img.lut;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.DoubleStream;
import java.util.stream.Stream;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Sequence;
import org.dcm4che3.data.Tag;
import org.dcm4che3.img.DicomImageUtils;
import org.dcm4che3.img.util.DicomUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.util.annotations.Generated;
import org.weasis.opencv.data.LookupTableCV;

/**
 * @author Nicolas Roduit
 */
public class VoiLutModule {
  private static final Logger LOGGER = LoggerFactory.getLogger(VoiLutModule.class);

  private List<Double> windowCenter;
  private List<Double> windowWidth;
  private List<String> lutExplanation;
  private List<LookupTableCV> lut;
  private List<String> windowCenterWidthExplanation;
  private String voiLutFunction;

  /**
   * VOI LUT Module
   *
   * @see <a
   *     href="http://dicom.nema.org/medical/dicom/current/output/chtml/part03/sect_C.12.html">C.11.2
   *     VOI LUT Module</a>
   */
  public VoiLutModule(Attributes dcm) {
    this.windowCenter = Collections.emptyList();
    this.windowWidth = Collections.emptyList();
    this.lutExplanation = Collections.emptyList();
    this.lut = Collections.emptyList();
    this.windowCenterWidthExplanation = Collections.emptyList();
    this.voiLutFunction = null;
    init(Objects.requireNonNull(dcm));
  }

  private void init(Attributes dcm) {
    Optional<double[]> wc = Optional.ofNullable(dcm.getDoubles(Tag.WindowCenter));
    Optional<double[]> ww = Optional.ofNullable(dcm.getDoubles(Tag.WindowWidth));
    if (wc.isPresent() && ww.isPresent()) {
      this.windowCenter = DoubleStream.of(wc.get()).boxed().collect(Collectors.toList());
      this.windowWidth = DoubleStream.of(ww.get()).boxed().collect(Collectors.toList());
      this.voiLutFunction = dcm.getString(Tag.VOILUTFunction);
      String[] wexpl =
          DicomUtils.getStringArrayFromDicomElement(dcm, Tag.WindowCenterWidthExplanation);
      if (wexpl != null) {
        this.windowCenterWidthExplanation = Stream.of(wexpl).toList();
      }
    }

    Sequence voiSeq = dcm.getSequence(Tag.VOILUTSequence);
    if (voiSeq != null && !voiSeq.isEmpty()) {
      this.lutExplanation = voiSeq.stream().map(i -> i.getString(Tag.LUTExplanation, "")).toList();
      this.lut = voiSeq.stream().map(i -> DicomImageUtils.createLut(i).orElse(null)).toList();
    }

    if (LOGGER.isDebugEnabled()) {
      logLutConsistency();
    }
  }

  @Generated
  private void logLutConsistency() {
    // If multiple Window center and window width values are present, both Attributes shall have
    // the same number of values and shall be considered as pairs. Multiple values indicate that
    // multiple alternative views may be presented
    if (windowCenter.isEmpty() && !windowWidth.isEmpty()) {
      LOGGER.debug("VOI Window Center is required if Window Width is present");
    } else if (!windowCenter.isEmpty() && windowWidth.isEmpty()) {
      LOGGER.debug("VOI Window Width is required if Window Center is present");
    } else if (windowWidth.size() != windowCenter.size()) {
      LOGGER.debug(
          "VOI Window Center and Width attributes have different number of values : {} => {}",
          windowCenter.size(),
          windowWidth.size());
    }
  }

  public List<Double> getWindowCenter() {
    return windowCenter;
  }

  public List<Double> getWindowWidth() {
    return windowWidth;
  }

  public List<String> getLutExplanation() {
    return lutExplanation;
  }

  public List<LookupTableCV> getLut() {
    return lut;
  }

  public List<String> getWindowCenterWidthExplanation() {
    return windowCenterWidthExplanation;
  }

  public Optional<String> getVoiLutFunction() {
    return Optional.ofNullable(voiLutFunction);
  }
}
