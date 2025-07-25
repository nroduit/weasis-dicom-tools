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
import java.io.FileInputStream;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.dcm4che3.img.DicomImageUtils;
import org.dcm4che3.img.lut.ModalityLutModule;
import org.dcm4che3.img.lut.VoiLutModule;
import org.dcm4che3.img.stream.ImageDescriptor;
import org.dcm4che3.img.util.DicomObjectUtil;
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
    // Basic grayscale presentation with standard windowing and overlays
    GRAYSCALE_SOFTCOPY(
        "1.2.840.10008.5.1.4.1.1.11.1",
        "Grayscale Softcopy Presentation State Storage",
        PresentationGroup.BASIC_GRAYSCALE),

    // Color image presentation with RGB/YBR color spaces
    COLOR_SOFTCOPY(
        "1.2.840.10008.5.1.4.1.1.11.2",
        "Color Softcopy Presentation State Storage",
        PresentationGroup.COLOR_BASED),

    // Pseudo-color mapping from grayscale to color using LUTs
    PSEUDO_COLOR_SOFTCOPY(
        "1.2.840.10008.5.1.4.1.1.11.3",
        "Pseudo-Color Softcopy Presentation State Storage",
        PresentationGroup.COLOR_BASED),

    // Multi-layer image blending with transparency and compositing
    BLENDING_SOFTCOPY(
        "1.2.840.10008.5.1.4.1.1.11.4",
        "Blending Softcopy Presentation State Storage",
        PresentationGroup.COLOR_BASED),

    // X-ray Angiography/Fluoroscopy specific presentation parameters
    XA_XRF_GRAYSCALE_SOFTCOPY(
        "1.2.840.10008.5.1.4.1.1.11.5",
        "XA/XRF Grayscale Softcopy Presentation State Storage",
        PresentationGroup.SPECIALIZED),

    // Multi-planar reconstruction (MPR) for 3D volumetric datasets
    GRAYSCALE_PLANAR_MPR_VOLUMETRIC(
        "1.2.840.10008.5.1.4.1.1.11.6",
        "Grayscale Planar MPR Volumetric Presentation State Storage",
        PresentationGroup.VOLUMETRIC),

    // MPR with compositing of multiple volumes or overlays
    COMPOSITING_PLANAR_MPR_VOLUMETRIC(
        "1.2.840.10008.5.1.4.1.1.11.7",
        "Compositing Planar MPR Volumetric Presentation State Storage",
        PresentationGroup.VOLUMETRIC),

    // Advanced blending with opacity maps and complex compositing algorithms
    ADVANCED_BLENDING(
        "1.2.840.10008.5.1.4.1.1.11.8",
        "Advanced Blending Presentation State Storage",
        PresentationGroup.ADVANCED_BLENDING),

    // 3D volume rendering with transfer functions and lighting
    VOLUME_RENDERING_VOLUMETRIC(
        "1.2.840.10008.5.1.4.1.1.11.9",
        "Volume Rendering Volumetric Presentation State Storage",
        PresentationGroup.VOLUMETRIC),

    // Volume rendering with segmentation masks and region-specific parameters
    SEGMENTED_VOLUME_RENDERING_VOLUMETRIC(
        "1.2.840.10008.5.1.4.1.1.11.10",
        "Segmented Volume Rendering Volumetric Presentation State Storage",
        PresentationGroup.VOLUMETRIC),

    // Multiple simultaneous volume renderings with different parameters
    MULTIPLE_VOLUME_RENDERING_VOLUMETRIC(
        "1.2.840.10008.5.1.4.1.1.11.11",
        "Multiple Volume Rendering Volumetric Presentation State Storage",
        PresentationGroup.VOLUMETRIC),

    // Dynamic modality LUT that can vary per frame or region
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

    /** Returns the DICOM UID for this presentation state type. */
    public String getUid() {
      return uid;
    }

    /** Returns the human-readable description of this presentation state type. */
    public String getDescription() {
      return description;
    }

    /** Returns the functional group this presentation state belongs to. */
    public PresentationGroup getGroup() {
      return group;
    }

    /** Finds presentation state type by its DICOM UID. */
    public static Optional<PresentationStateType> fromUid(String uid) {
      for (PresentationStateType type : values()) {
        if (type.uid.equals(uid)) {
          return Optional.of(type);
        }
      }
      return Optional.empty();
    }

    /** Returns all presentation state types belonging to the specified functional group. */
    public static Set<PresentationStateType> getByGroup(PresentationGroup group) {
      return Set.of(values()).stream()
          .filter(type -> type.group == group)
          .collect(java.util.stream.Collectors.toSet());
    }
  }

  /**
   * Functional groups that categorize presentation state types by their capabilities and use cases.
   * This grouping helps determine which features and data structures are available.
   */
  public enum PresentationGroup {
    /** Basic grayscale presentation with standard windowing and simple overlays */
    BASIC_GRAYSCALE("Basic grayscale presentation with standard windowing"),

    /** Color and pseudo-color presentation with RGB/color space transformations */
    COLOR_BASED("Color and pseudo-color presentation states"),

    /** Modality-specific presentation states with specialized parameters */
    SPECIALIZED("Specialized modality-specific presentation states"),

    /** 3D volumetric rendering and multi-planar reconstruction */
    VOLUMETRIC("3D volumetric rendering presentation states"),

    /** Advanced blending with complex compositing algorithms */
    ADVANCED_BLENDING("Advanced blending and compositing"),

    /** Advanced LUT manipulation with variable and dynamic transformations */
    ADVANCED_LUT("Advanced LUT manipulation and variable modality LUT");

    private final String description;

    PresentationGroup(String description) {
      this.description = description;
    }

    /** Returns the description of this functional group. */
    public String getDescription() {
      return description;
    }
  }

  /**
   * Creates a presentation state object from DICOM attributes. Uses default image descriptor for
   * modality LUT and overlay initialization.
   */
  public PrDicomObject(Attributes dcmPR) {
    this(dcmPR, null);
  }

  /**
   * Creates a presentation state object from DICOM attributes with optional image descriptor. The
   * image descriptor provides pre-computed modality LUT and overlay data when available.
   *
   * @param dcmPR DICOM presentation state attributes
   * @param desc optional image descriptor with pre-computed data. Can be null.
   * @throws NullPointerException if dcmPR is null.
   * @throws IllegalStateException if SOP class UID is not recognized.
   */
  public PrDicomObject(Attributes dcmPR, ImageDescriptor desc) {
    this.dcmPR = Objects.requireNonNull(dcmPR);
    this.presentationStateType = validateAndGetPresentationStateType();

    this.modalityLUT = initializeModalityLut(desc);
    this.voiLUT = buildVoiLut(dcmPR);
    this.overlays = OverlayData.getPrOverlayData(dcmPR, -1);
    this.shutterOverlays = initializeShutterOverlays(desc);
    PresentationLutData lutData = initializePresentationLut();
    this.prLut = lutData.lut().orElse(null);
    this.prLutExplanation = lutData.explanation().orElse(null);
    this.prLUTShapeMode = lutData.shapeMode().orElse(null);
  }

  // Validates SOP class UID and returns corresponding presentation state type
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

  // Initializes modality LUT from image descriptor or creates new from DICOM attributes
  private ModalityLutModule initializeModalityLut(ImageDescriptor desc) {
    return desc == null ? new ModalityLutModule(dcmPR) : desc.getModalityLUT();
  }

  // Initializes shutter overlays from image descriptor or extracts from DICOM attributes
  private List<OverlayData> initializeShutterOverlays(ImageDescriptor desc) {
    return desc == null ? OverlayData.getOverlayData(dcmPR, 0xffff) : desc.getOverlayData();
  }

  // Creates presentation LUT data from sequence or shape parameters
  private PresentationLutData initializePresentationLut() {
    Attributes dcmLut = dcmPR.getNestedDataset(Tag.PresentationLUTSequence);
    if (dcmLut != null) {
      return createPresentationLutFromSequence(dcmLut);
    } else {
      return createPresentationLutFromShape();
    }
  }

  // Creates presentation LUT from DICOM LUT sequence with full range transformation
  private PresentationLutData createPresentationLutFromSequence(Attributes dcmLut) {
    Optional<LookupTableCV> lut = DicomImageUtils.createLut(dcmLut);
    Optional<String> explanation = Optional.ofNullable(dcmPR.getString(Tag.LUTExplanation));
    Optional<String> shapeMode = Optional.of(IDENTITY_LUT_SHAPE);

    return new PresentationLutData(lut, explanation, shapeMode);
  }

  // Creates presentation LUT from shape parameter (IDENTITY or INVERSE)
  private PresentationLutData createPresentationLutFromShape() {
    Optional<String> shapeMode = Optional.ofNullable(dcmPR.getString(Tag.PresentationLUTShape));
    return new PresentationLutData(Optional.empty(), Optional.empty(), shapeMode);
  }

  // Builds VOI LUT module from softcopy VOI LUT sequence if present
  private static VoiLutModule buildVoiLut(Attributes dcmPR) {
    Attributes seqDcm = dcmPR.getNestedDataset(Tag.SoftcopyVOILUTSequence);
    return seqDcm == null ? null : new VoiLutModule(seqDcm);
  }

  // Checks applicability by searching referenced series and validating image sequence
  private boolean isApplicableByReference(
      int childTag, String seriesInstanceUID, String sopInstanceUID, int referenceNumber) {
    if (!StringUtil.hasText(seriesInstanceUID)) {
      return false;
    }
    Optional<Attributes> matchingSeries =
        getReferencedSeriesSequence().stream()
            .filter(
                refSeriesSeq ->
                    seriesInstanceUID.equals(refSeriesSeq.getString(Tag.SeriesInstanceUID)))
            .findFirst();
    return matchingSeries
        .filter(
            attributes ->
                checkImageSequenceApplicability(
                    attributes, childTag, sopInstanceUID, referenceNumber))
        .isPresent();
  }

  // Validates applicability against referenced image sequence using DICOM utility
  private boolean checkImageSequenceApplicability(
      Attributes refSeriesSeq, int childTag, String sopInstanceUID, int referenceNumber) {
    List<Attributes> refImgSeq =
        DicomObjectUtil.getSequence(refSeriesSeq, Tag.ReferencedImageSequence);
    return DicomObjectUtil.isImageFrameApplicableToReferencedImageSequence(
        refImgSeq, childTag, sopInstanceUID, referenceNumber, true);
  }

  // === Public API Methods ===

  /** Returns the underlying DICOM attributes of this presentation state. */
  public Attributes getDicomObject() {
    return dcmPR;
  }

  /** Returns the creation date and time of this presentation state. */
  public LocalDateTime getPresentationCreationDateTime() {
    return DicomObjectUtil.dateTime(
        dcmPR, Tag.PresentationCreationDate, Tag.PresentationCreationTime);
  }

  /** Returns the presentation LUT if defined in the presentation state. */
  @Override
  public Optional<LookupTableCV> getPrLut() {
    return Optional.ofNullable(prLut);
  }

  /** Returns the explanation/description of the presentation LUT. */
  @Override
  public Optional<String> getPrLutExplanation() {
    return Optional.ofNullable(prLutExplanation);
  }

  /** Returns the presentation LUT shape mode (IDENTITY, INVERSE, or custom LUT). */
  @Override
  public Optional<String> getPrLutShapeMode() {
    return Optional.ofNullable(prLUTShapeMode);
  }

  /** Returns the specific presentation state type (SOP class) of this object. */
  public PresentationStateType getPresentationStateType() {
    return presentationStateType;
  }

  /** Returns the functional group this presentation state belongs to. */
  public PresentationGroup getPresentationGroup() {
    return presentationStateType.getGroup();
  }

  /** Returns the human-readable description of this presentation state type. */
  public String getSopClassDescription() {
    return presentationStateType.getDescription();
  }

  /** Returns the modality LUT module for pixel value transformations. */
  public ModalityLutModule getModalityLutModule() {
    return modalityLUT;
  }

  /** Returns the VOI LUT module if defined for window/level transformations. */
  public Optional<VoiLutModule> getVoiLUT() {
    return Optional.ofNullable(voiLUT);
  }

  /** Returns all overlays defined in this presentation state. */
  public List<OverlayData> getOverlays() {
    return overlays;
  }

  /** Returns shutter overlays for masking image regions. */
  public List<OverlayData> getShutterOverlays() {
    return shutterOverlays;
  }

  /** Returns the content label or generates default from instance number. */
  public String getPrContentLabel() {
    return dcmPR.getString(Tag.ContentLabel, "PR " + dcmPR.getInt(Tag.InstanceNumber, 0));
  }

  /** Returns true if this presentation state contains overlay data. */
  public boolean hasOverlay() {
    return !overlays.isEmpty();
  }

  /** Returns the referenced series sequence containing applicable images. */
  public List<Attributes> getReferencedSeriesSequence() {
    return DicomObjectUtil.getSequence(dcmPR, Tag.ReferencedSeriesSequence);
  }

  /** Returns graphic annotations defined in this presentation state. */
  public List<Attributes> getGraphicAnnotationSequence() {
    return DicomObjectUtil.getSequence(dcmPR, Tag.GraphicAnnotationSequence);
  }

  /** Returns graphic layer definitions for overlay organization. */
  public List<Attributes> getGraphicLayerSequence() {
    return DicomObjectUtil.getSequence(dcmPR, Tag.GraphicLayerSequence);
  }

  /** Returns the geometric shape used for image shuttering/masking. */
  public Area getShutterShape() {
    return DicomObjectUtil.getShutterShape(dcmPR);
  }

  /** Returns the color used for shutter overlay rendering. */
  public Color getShutterColor() {
    return DicomObjectUtil.getShutterColor(dcmPR);
  }

  // === Capability Checking Methods ===

  /** Returns true if this presentation state supports 3D volumetric rendering. */
  public boolean supportsVolumetricRendering() {
    return presentationStateType.getGroup() == PresentationGroup.VOLUMETRIC;
  }

  /** Returns true if this presentation state supports advanced image blending. */
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
    try (DicomInputStream dis = new DicomInputStream(new FileInputStream(prPath))) {
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

  /** Helper record for grouping presentation LUT-related data during initialization. */
  private record PresentationLutData(
      Optional<LookupTableCV> lut, Optional<String> explanation, Optional<String> shapeMode) {}
}
