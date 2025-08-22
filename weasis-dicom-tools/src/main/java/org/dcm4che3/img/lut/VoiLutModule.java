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

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.DoubleStream;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Sequence;
import org.dcm4che3.data.Tag;
import org.dcm4che3.img.util.DicomUtils;
import org.dcm4che3.img.util.LookupTableUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.util.annotations.Generated;
import org.weasis.opencv.data.LookupTableCV;

/**
 * VOI LUT Module for DICOM Value of Interest transformations.
 *
 * <p>This module handles the VOI LUT functionality which defines the transformation of modality
 * pixel values into pixel values that are meaningful for print and display. It supports both window
 * center/width values and VOI LUT sequences.
 *
 * @see <a
 *     href="https://dicom.nema.org/medical/dicom/current/output/chtml/part03/sect_C.11.2.html#sect_C.11.2">
 *     C.11.2 VOI LUT Module</a>
 * @author Nicolas Roduit
 */
public final class VoiLutModule {
  private static final Logger LOGGER = LoggerFactory.getLogger(VoiLutModule.class);

  private final List<Double> windowCenter;
  private final List<Double> windowWidth;
  private final List<String> lutExplanation;
  private final List<LookupTableCV> lut;
  private final List<String> windowCenterWidthExplanation;
  private final String voiLutFunction;

  /**
   * Creates a new VoiLutModule from DICOM attributes.
   *
   * @param dcm DICOM attributes, must not be null
   * @throws NullPointerException if dcm is null
   */
  public VoiLutModule(Attributes dcm) {
    Objects.requireNonNull(dcm);

    var windowCenterArray = dcm.getDoubles(Tag.WindowCenter);
    var windowWidthArray = dcm.getDoubles(Tag.WindowWidth);

    this.windowCenter =
        windowCenterArray != null ? DoubleStream.of(windowCenterArray).boxed().toList() : List.of();
    this.windowWidth =
        windowWidthArray != null ? DoubleStream.of(windowWidthArray).boxed().toList() : List.of();

    this.voiLutFunction = hasWindowValues() ? dcm.getString(Tag.VOILUTFunction) : null;

    this.windowCenterWidthExplanation =
        hasWindowValues() ? extractStringList(dcm, Tag.WindowCenterWidthExplanation) : List.of();

    var voiSeq = dcm.getSequence(Tag.VOILUTSequence);
    if (voiSeq != null && !voiSeq.isEmpty()) {
      this.lutExplanation = extractLutExplanations(voiSeq);
      this.lut = extractLookupTables(voiSeq);
    } else {
      this.lutExplanation = List.of();
      this.lut = List.of();
    }

    if (LOGGER.isDebugEnabled()) {
      validateConsistency();
    }
  }

  private boolean hasWindowValues() {
    return !windowCenter.isEmpty() && !windowWidth.isEmpty();
  }

  private static List<String> extractStringList(Attributes dcm, int tag) {
    var values = DicomUtils.getStringArrayFromDicomElement(dcm, tag);
    return values != null ? List.of(values) : List.of();
  }

  private static List<String> extractLutExplanations(Sequence voiSeq) {
    return voiSeq.stream().map(item -> item.getString(Tag.LUTExplanation, "")).toList();
  }

  private static List<LookupTableCV> extractLookupTables(Sequence voiSeq) {
    return voiSeq.stream().map(LookupTableUtils::createLut).flatMap(Optional::stream).toList();
  }

  @Generated
  private void validateConsistency() {
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
