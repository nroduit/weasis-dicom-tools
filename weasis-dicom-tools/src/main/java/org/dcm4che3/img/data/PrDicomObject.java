/*
 * Copyright (c) 2021 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.dcm4che3.img.data;

import java.awt.Color;
import java.awt.geom.Area;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.dcm4che3.img.lut.ModalityLutModule;
import org.dcm4che3.img.lut.VoiLutModule;
import org.dcm4che3.img.stream.ImageDescriptor;
import org.dcm4che3.img.util.DicomObjectUtil;
import org.dcm4che3.img.util.LookupTableUtils;
import org.dcm4che3.io.DicomInputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.util.StringUtil;
import org.weasis.opencv.data.LookupTableCV;
import org.weasis.opencv.op.lut.PresentationStateLut;

/**
 * Represents a DICOM Presentation State (PR) object that defines how medical images should be
 * displayed.
 *
 * <p>This class handles all standard DICOM presentation state SOP classes from 11.1 to 11.12,
 * including grayscale, color, volumetric, and specialized presentation states. It provides access
 * to LUT transformations, overlays, annotations, and display parameters.
 *
 * @author Nicolas Roduit
 * @see <a
 *     href="http://dicom.nema.org/medical/dicom/current/output/chtml/part03/sect_A.33.html">DICOM
 *     PS3.3 - Presentation State Module</a>
 */
public class PrDicomObject implements PresentationStateLut {
  private static final Logger LOGGER = LoggerFactory.getLogger(PrDicomObject.class);

  private static final String IDENTITY_LUT_SHAPE = "IDENTITY";

  private final Attributes dcmPR;
  private final ModalityLutModule modalityLUT;
  private final List<OverlayData> overlays;
  private final List<OverlayData> shutterOverlays;
  private final VoiLutModule voiLUT;
  private final LookupTableCV prLut;
  private final String prLutExplanation;
  private final String prLUTShapeMode;
  private final PresentationStateType presentationStateType;

  /**
   * Enumeration of all DICOM Presentation State SOP Classes (UIDs 11.1-11.12) grouped by
   * functionality. Each type defines specific capabilities and data structures for medical image
   * presentation.
   */
  public enum PresentationStateType {
    GRAYSCALE_SOFTCOPY(
        "1.2.840.10008.5.1.4.1.1.11.1",
        "Grayscale Softcopy Presentation State Storage",
        PresentationGroup.BASIC_GRAYSCALE),

    COLOR_SOFTCOPY(
        "1.2.840.10008.5.1.4.1.1.11.2",
        "Color Softcopy Presentation State Storage",
        PresentationGroup.COLOR_BASED),

    PSEUDO_COLOR_SOFTCOPY(
        "1.2.840.10008.5.1.4.1.1.11.3",
        "Pseudo-Color Softcopy Presentation State Storage",
        PresentationGroup.COLOR_BASED),

    BLENDING_SOFTCOPY(
        "1.2.840.10008.5.1.4.1.1.11.4",
        "Blending Softcopy Presentation State Storage",
        PresentationGroup.COLOR_BASED),

    XA_XRF_GRAYSCALE_SOFTCOPY(
        "1.2.840.10008.5.1.4.1.1.11.5",
        "XA/XRF Grayscale Softcopy Presentation State Storage",
        PresentationGroup.SPECIALIZED),

    GRAYSCALE_PLANAR_MPR_VOLUMETRIC(
        "1.2.840.10008.5.1.4.1.1.11.6",
        "Grayscale Planar MPR Volumetric Presentation State Storage",
        PresentationGroup.VOLUMETRIC),

    COMPOSITING_PLANAR_MPR_VOLUMETRIC(
        "1.2.840.10008.5.1.4.1.1.11.7",
        "Compositing Planar MPR Volumetric Presentation State Storage",
        PresentationGroup.VOLUMETRIC),

    ADVANCED_BLENDING(
        "1.2.840.10008.5.1.4.1.1.11.8",
        "Advanced Blending Presentation State Storage",
        PresentationGroup.ADVANCED_BLENDING),

    VOLUME_RENDERING_VOLUMETRIC(
        "1.2.840.10008.5.1.4.1.1.11.9",
        "Volume Rendering Volumetric Presentation State Storage",
        PresentationGroup.VOLUMETRIC),

    SEGMENTED_VOLUME_RENDERING_VOLUMETRIC(
        "1.2.840.10008.5.1.4.1.1.11.10",
        "Segmented Volume Rendering Volumetric Presentation State Storage",
        PresentationGroup.VOLUMETRIC),

    MULTIPLE_VOLUME_RENDERING_VOLUMETRIC(
        "1.2.840.10008.5.1.4.1.1.11.11",
        "Multiple Volume Rendering Volumetric Presentation State Storage",
        PresentationGroup.VOLUMETRIC),

    VARIABLE_MODALITY_LUT_SOFTCOPY(
        "1.2.840.10008.5.1.4.1.1.11.12",
        "Variable Modality LUT Softcopy Presentation State Storage",
        PresentationGroup.ADVANCED_LUT);

    private final String uid;
    private final String description;
    private final PresentationGroup group;

    PresentationStateType(String uid, String description, PresentationGroup group) {
      this.uid = uid;
      this.description = description;
      this.group = group;
    }

    public String getUid() {
      return uid;
    }

    public String getDescription() {
      return description;
    }

    public PresentationGroup getGroup() {
      return group;
    }

    public static Optional<PresentationStateType> fromUid(String uid) {
      for (PresentationStateType type : values()) {
        if (type.uid.equals(uid)) {
          return Optional.of(type);
        }
      }
      return Optional.empty();
    }

    public static Set<PresentationStateType> getByGroup(PresentationGroup group) {
      return Set.of(values()).stream()
          .filter(type -> type.group == group)
          .collect(Collectors.toSet());
    }
  }

  /**
   * Functional groups that categorize presentation state types by their capabilities and use cases.
   */
  public enum PresentationGroup {
    BASIC_GRAYSCALE("Basic grayscale presentation with standard windowing"),
    COLOR_BASED("Color and pseudo-color presentation states"),
    SPECIALIZED("Specialized modality-specific presentation states"),
    VOLUMETRIC("3D volumetric rendering presentation states"),
    ADVANCED_BLENDING("Advanced blending and compositing"),
    ADVANCED_LUT("Advanced LUT manipulation and variable modality LUT");

    private final String description;

    PresentationGroup(String description) {
      this.description = description;
    }

    public String getDescription() {
      return description;
    }
  }

  /** Creates a presentation state object from DICOM attributes. */
  public PrDicomObject(Attributes dcmPR) {
    this(dcmPR, null);
  }

  /**
   * Creates a presentation state object from DICOM attributes with optional image descriptor.
   *
   * @param dcmPR DICOM presentation state attributes
   * @param desc optional image descriptor with pre-computed data
   * @throws NullPointerException if dcmPR is null
   * @throws IllegalStateException if SOP class UID is not recognized
   */
  public PrDicomObject(Attributes dcmPR, ImageDescriptor desc) {
    this.dcmPR = Objects.requireNonNull(dcmPR);
    this.presentationStateType = validateAndGetPresentationStateType();

    this.modalityLUT = desc == null ? new ModalityLutModule(dcmPR) : desc.getModalityLUT();
    this.voiLUT = buildVoiLut(dcmPR);
    this.overlays = OverlayData.getPrOverlayData(dcmPR, -1);
    this.shutterOverlays =
        desc == null ? OverlayData.getOverlayData(dcmPR, 0xffff) : desc.getOverlayData();

    var lutData = initializePresentationLut();
    this.prLut = lutData.lut().orElse(null);
    this.prLutExplanation = lutData.explanation().orElse(null);
    this.prLUTShapeMode = lutData.shapeMode().orElse(null);
  }

  private PresentationStateType validateAndGetPresentationStateType() {
    String sopClassUid = dcmPR.getString(Tag.SOPClassUID, "");
    Optional<PresentationStateType> type = PresentationStateType.fromUid(sopClassUid);
    if (type.isEmpty()) {
      throw new IllegalStateException(
          "SOPClassUID '"
              + sopClassUid
              + "' does not match any supported DICOM Presentation State");
    }

    PresentationGroup group = type.get().group;
    if (group != PresentationGroup.BASIC_GRAYSCALE && group != PresentationGroup.COLOR_BASED) {
      LOGGER.warn("Presentation State '{}' is not fully supported", type.get().name());
    }
    return type.get();
  }

  private PresentationLutData initializePresentationLut() {
    Attributes dcmLut = dcmPR.getNestedDataset(Tag.PresentationLUTSequence);
    return dcmLut != null
        ? createPresentationLutFromSequence(dcmLut)
        : createPresentationLutFromShape();
  }

  private PresentationLutData createPresentationLutFromSequence(Attributes dcmLut) {
    return new PresentationLutData(
        LookupTableUtils.createLut(dcmLut),
        Optional.ofNullable(dcmPR.getString(Tag.LUTExplanation)),
        Optional.of(IDENTITY_LUT_SHAPE));
  }

  private PresentationLutData createPresentationLutFromShape() {
    return new PresentationLutData(
        Optional.empty(),
        Optional.empty(),
        Optional.ofNullable(dcmPR.getString(Tag.PresentationLUTShape)));
  }

  private static VoiLutModule buildVoiLut(Attributes dcmPR) {
    Attributes seqDcm = dcmPR.getNestedDataset(Tag.SoftcopyVOILUTSequence);
    return seqDcm == null ? null : new VoiLutModule(seqDcm);
  }

  private boolean isApplicableByReference(
      int childTag, String seriesInstanceUID, String sopInstanceUID, int referenceNumber) {
    if (!StringUtil.hasText(seriesInstanceUID)) {
      return false;
    }
    return getReferencedSeriesSequence().stream()
        .filter(
            refSeriesSeq -> seriesInstanceUID.equals(refSeriesSeq.getString(Tag.SeriesInstanceUID)))
        .anyMatch(
            refSeriesSeq ->
                isImageFrameApplicable(refSeriesSeq, childTag, sopInstanceUID, referenceNumber));
  }

  private boolean isImageFrameApplicable(
      Attributes refSeriesSeq, int childTag, String sopInstanceUID, int referenceNumber) {
    List<Attributes> refImgSeq =
        DicomObjectUtil.getSequence(refSeriesSeq, Tag.ReferencedImageSequence);
    return DicomObjectUtil.isImageFrameApplicableToReferencedImageSequence(
        refImgSeq, childTag, sopInstanceUID, referenceNumber, true);
  }

  // === Public API Methods ===

  public Attributes getDicomObject() {
    return dcmPR;
  }

  public LocalDateTime getPresentationCreationDateTime() {
    return DicomObjectUtil.dateTime(
        dcmPR, Tag.PresentationCreationDate, Tag.PresentationCreationTime);
  }

  @Override
  public Optional<LookupTableCV> getPrLut() {
    return Optional.ofNullable(prLut);
  }

  @Override
  public Optional<String> getPrLutExplanation() {
    return Optional.ofNullable(prLutExplanation);
  }

  @Override
  public Optional<String> getPrLutShapeMode() {
    return Optional.ofNullable(prLUTShapeMode);
  }

  public PresentationStateType getPresentationStateType() {
    return presentationStateType;
  }

  public PresentationGroup getPresentationGroup() {
    return presentationStateType.getGroup();
  }

  public String getSopClassDescription() {
    return presentationStateType.getDescription();
  }

  public ModalityLutModule getModalityLutModule() {
    return modalityLUT;
  }

  public Optional<VoiLutModule> getVoiLUT() {
    return Optional.ofNullable(voiLUT);
  }

  public List<OverlayData> getOverlays() {
    return overlays;
  }

  public List<OverlayData> getShutterOverlays() {
    return shutterOverlays;
  }

  public String getPrContentLabel() {
    return dcmPR.getString(Tag.ContentLabel, "PR " + dcmPR.getInt(Tag.InstanceNumber, 0));
  }

  public boolean hasOverlay() {
    return !overlays.isEmpty();
  }

  public List<Attributes> getReferencedSeriesSequence() {
    return DicomObjectUtil.getSequence(dcmPR, Tag.ReferencedSeriesSequence);
  }

  public List<Attributes> getGraphicAnnotationSequence() {
    return DicomObjectUtil.getSequence(dcmPR, Tag.GraphicAnnotationSequence);
  }

  public List<Attributes> getGraphicLayerSequence() {
    return DicomObjectUtil.getSequence(dcmPR, Tag.GraphicLayerSequence);
  }

  public Area getShutterShape() {
    return DicomObjectUtil.getShutterShape(dcmPR);
  }

  public Color getShutterColor() {
    return DicomObjectUtil.getShutterColor(dcmPR);
  }

  // === Capability Checking Methods ===

  public boolean supportsVolumetricRendering() {
    return presentationStateType.getGroup() == PresentationGroup.VOLUMETRIC;
  }

  public boolean supportsAdvancedBlending() {
    return presentationStateType.getGroup() == PresentationGroup.ADVANCED_BLENDING;
  }

  // === Static Factory Methods ===

  /**
   * Creates a presentation state object by reading DICOM file from the specified path.
   *
   * @param prPath file path to the DICOM presentation state file
   * @return presentation state object
   * @throws IOException if file cannot be read or parsed
   */
  public static PrDicomObject getPresentationState(String prPath) throws IOException {
    return getPresentationState(Path.of(prPath));
  }

  /**
   * Creates a presentation state object by reading DICOM file from the specified path.
   *
   * @param prPath file path to the DICOM presentation state file
   * @return presentation state object
   * @throws IOException if file cannot be read or parsed
   */
  public static PrDicomObject getPresentationState(Path prPath) throws IOException {
    try (var inputStream = Files.newInputStream(prPath);
        var dis = new DicomInputStream(inputStream)) {
      return new PrDicomObject(dis.readDataset());
    }
  }

  // === Applicability Checking Methods ===

  /**
   * Checks if this presentation state is applicable to a specific image frame.
   *
   * @param seriesInstanceUID the series UID of the target image
   * @param sopInstanceUID the SOP instance UID of the target image
   * @param frame the frame number (1-based)
   * @return true if this presentation state applies to the specified frame
   */
  public boolean isImageFrameApplicable(
      String seriesInstanceUID, String sopInstanceUID, int frame) {
    return isApplicableByReference(
        Tag.ReferencedFrameNumber, seriesInstanceUID, sopInstanceUID, frame);
  }

  /**
   * Checks if this presentation state is applicable to a specific segmentation segment.
   *
   * @param seriesInstanceUID the series UID of the target segmentation
   * @param sopInstanceUID the SOP instance UID of the target segmentation
   * @param segment the segment number
   * @return true if this presentation state applies to the specified segment
   */
  public boolean isSegmentationSegmentApplicable(
      String seriesInstanceUID, String sopInstanceUID, int segment) {
    return isApplicableByReference(
        Tag.ReferencedSegmentNumber, seriesInstanceUID, sopInstanceUID, segment);
  }

  private record PresentationLutData(
      Optional<LookupTableCV> lut, Optional<String> explanation, Optional<String> shapeMode) {}
}
