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
public class VoiLutModule {
  private static final Logger LOGGER = LoggerFactory.getLogger(VoiLutModule.class);

  private List<Double> windowCenter;
  private List<Double> windowWidth;
  private List<String> lutExplanation;
  private List<LookupTableCV> lut;
  private List<String> windowCenterWidthExplanation;
  private String voiLutFunction;

  /**
   * Creates a new VoiLutModule from DICOM attributes.
   *
   * @param dcm DICOM attributes, must not be null
   * @throws NullPointerException if dcm is null
   */
  public VoiLutModule(Attributes dcm) {
    initializeFields();
    init(Objects.requireNonNull(dcm));
  }

  /** Initializes all fields to empty collections or null values. */
  private void initializeFields() {
    this.windowCenter = Collections.emptyList();
    this.windowWidth = Collections.emptyList();
    this.lutExplanation = Collections.emptyList();
    this.lut = Collections.emptyList();
    this.windowCenterWidthExplanation = Collections.emptyList();
    this.voiLutFunction = null;
  }

  /** Main initialization method that processes DICOM attributes. */
  private void init(Attributes dcm) {
    initWindowCenterWidth(dcm);
    initVoiLutSequence(dcm);

    if (LOGGER.isDebugEnabled()) {
      logLutConsistency();
    }
  }

  /** Initializes window center and width values along with related attributes. */
  private void initWindowCenterWidth(Attributes dcm) {
    Optional<double[]> wc = Optional.ofNullable(dcm.getDoubles(Tag.WindowCenter));
    Optional<double[]> ww = Optional.ofNullable(dcm.getDoubles(Tag.WindowWidth));
    if (wc.isPresent() && ww.isPresent()) {
      this.windowCenter = convertToDoubleList(wc.get());
      this.windowWidth = convertToDoubleList(ww.get());
      this.voiLutFunction = dcm.getString(Tag.VOILUTFunction);
      initWindowCenterWidthExplanation(dcm);
    }
  }

  /** Converts double array to List of Double objects. */
  private List<Double> convertToDoubleList(double[] values) {
    return DoubleStream.of(values).boxed().toList();
  }

  /** Initializes window center width explanation if present. */
  private void initWindowCenterWidthExplanation(Attributes dcm) {
    String[] explanations =
        DicomUtils.getStringArrayFromDicomElement(dcm, Tag.WindowCenterWidthExplanation);
    if (explanations != null) {
      this.windowCenterWidthExplanation = Stream.of(explanations).toList();
    }
  }

  /** Initializes VOI LUT Sequence if present and valid. */
  private void initVoiLutSequence(Attributes dcm) {
    Sequence voiSeq = dcm.getSequence(Tag.VOILUTSequence);
    if (voiSeq != null && !voiSeq.isEmpty()) {
      processVoiLutSequence(voiSeq);
    }
  }

  /** Processes the VOI LUT Sequence to extract explanations and lookup tables. */
  private void processVoiLutSequence(Sequence voiSeq) {
    this.lutExplanation = extractLutExplanations(voiSeq);
    this.lut = extractLookupTables(voiSeq);
  }

  /** Extracts LUT explanations from the VOI LUT Sequence. */
  private List<String> extractLutExplanations(Sequence voiSeq) {
    return voiSeq.stream().map(item -> item.getString(Tag.LUTExplanation, "")).toList();
  }

  /** Extracts lookup tables from the VOI LUT Sequence. */
  private List<LookupTableCV> extractLookupTables(Sequence voiSeq) {
    return voiSeq.stream().map(DicomImageUtils::createLut).flatMap(Optional::stream).toList();
  }

  /** Logs consistency warnings and debug information about the VOI LUT configuration. */
  @Generated
  private void logLutConsistency() {
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

  /**
   * @return The list of window center values
   */
  public List<Double> getWindowCenter() {
    return windowCenter;
  }

  /**
   * @return The list of window width values
   */
  public List<Double> getWindowWidth() {
    return windowWidth;
  }

  /**
   * @return The list of LUT explanations
   */
  public List<String> getLutExplanation() {
    return lutExplanation;
  }

  /**
   * @return The list of lookup tables
   */
  public List<LookupTableCV> getLut() {
    return lut;
  }

  /**
   * @return The list of window center width explanations
   */
  public List<String> getWindowCenterWidthExplanation() {
    return windowCenterWidthExplanation;
  }

  /**
   * @return The VOI LUT function, or empty if not present
   */
  public Optional<String> getVoiLutFunction() {
    return Optional.ofNullable(voiLutFunction);
  }
}
