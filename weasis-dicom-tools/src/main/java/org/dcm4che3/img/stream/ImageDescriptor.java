/*
 * Copyright (c) 2021 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.dcm4che3.img.stream;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.dcm4che3.image.PhotometricInterpretation;
import org.dcm4che3.img.DicomImageUtils;
import org.dcm4che3.img.data.EmbeddedOverlay;
import org.dcm4che3.img.data.OverlayData;
import org.dcm4che3.img.lut.ModalityLutModule;
import org.dcm4che3.img.lut.VoiLutModule;
import org.dcm4che3.img.util.DicomUtils;
import org.opencv.core.Core.MinMaxLocResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.dicom.ref.AnatomicRegion;
import org.weasis.opencv.data.LookupTableCV;

/**
 * Immutable descriptor containing comprehensive metadata and properties for DICOM images.
 *
 * <p>This class encapsulates all essential image characteristics including dimensions, pixel
 * representation, color space information, and specialized DICOM attributes. It provides
 * thread-safe access to both basic image properties and advanced features like LUT transformations,
 * overlay data, and frame-specific metadata.
 *
 * <p><strong>Key Features:</strong>
 *
 * <ul>
 *   <li>Immutable design ensuring thread safety
 *   <li>Support for multi-frame images with per-frame metadata
 *   <li>Comprehensive overlay and embedded overlay handling
 *   <li>LUT (Look-Up Table) support for modality and VOI transformations
 *   <li>Anatomical region and modality-specific metadata
 * </ul>
 *
 * @author Nicolas Roduit
 */
public final class ImageDescriptor {
  private static final Logger LOGGER = LoggerFactory.getLogger(ImageDescriptor.class);

  // Image dimension properties
  private final int rows;
  private final int columns;
  private final int frames;

  // Pixel representation properties
  private final int samples;
  private final PhotometricInterpretation photometricInterpretation;
  private final int bitsAllocated;
  private final int bitsStored;
  private final int bitsCompressed;
  private final int highBit;
  private final int pixelRepresentation;
  private final int planarConfiguration;
  private final String pixelPresentation;
  private final LookupTableCV paletteColorLookupTable;

  // DICOM metadata
  private final String sopClassUID;
  private final String seriesInstanceUID;
  private final String modality;
  private final String stationName;
  private final AnatomicRegion anatomicRegion;

  // Overlay and presentation data
  private final List<EmbeddedOverlay> embeddedOverlay;
  private final List<OverlayData> overlayData;
  private final String presentationLUTShape;

  // Pixel padding and LUT modules
  private final Integer pixelPaddingValue;
  private final Integer pixelPaddingRangeLimit;
  private final ModalityLutModule modalityLUT;
  private final VoiLutModule voiLUT;

  // Frame-specific data collections
  private final List<MinMaxLocResult> minMaxPixelValues;
  private final List<VoiLutModule> voiLutPerFrame;
  private final List<ModalityLutModule> modalityLutPerFrame;

  /**
   * Creates an image descriptor from DICOM attributes.
   *
   * @param dcm the DICOM attributes containing image metadata
   * @throws NullPointerException if dcm is null
   */
  public ImageDescriptor(Attributes dcm) {
    this(dcm, 0);
  }

  /**
   * Creates an image descriptor from DICOM attributes with specified bit compression.
   *
   * @param dcm the DICOM attributes containing image metadata
   * @param bitsCompressed the number of bits used for compressed pixel data, or 0 to use bits
   *     stored value
   * @throws NullPointerException if dcm is null
   */
  public ImageDescriptor(Attributes dcm, int bitsCompressed) {
    Objects.requireNonNull(dcm, "DICOM attributes cannot be null");

    // Initialize basic image dimensions
    this.rows = Math.max(dcm.getInt(Tag.Rows, 0), 0);
    this.columns = Math.max(dcm.getInt(Tag.Columns, 0), 0);
    this.frames = Math.max(dcm.getInt(Tag.NumberOfFrames, 1), 1);

    // Initialize pixel representation attributes
    var pixelAttributes = initializePixelAttributes(dcm, bitsCompressed);
    this.samples = pixelAttributes.samples();
    this.photometricInterpretation = pixelAttributes.photometricInterpretation();
    this.bitsAllocated = pixelAttributes.bitsAllocated();
    this.bitsStored = pixelAttributes.bitsStored();
    this.bitsCompressed = pixelAttributes.bitsCompressed();
    this.highBit = pixelAttributes.highBit();
    this.pixelRepresentation = pixelAttributes.pixelRepresentation();
    this.planarConfiguration = pixelAttributes.planarConfiguration();
    this.pixelPresentation = pixelAttributes.pixelPresentation();
    this.paletteColorLookupTable = initializePaletteColorLookupTable(dcm);

    // Initialize DICOM metadata
    var metadata = initializeDicomMetadata(dcm);
    this.sopClassUID = metadata.sopClassUID();
    this.seriesInstanceUID = metadata.seriesInstanceUID();
    this.modality = metadata.modality();
    this.stationName = metadata.stationName();
    this.anatomicRegion = metadata.anatomicRegion();

    // Initialize overlay and presentation data
    var overlayData = initializeOverlayData(dcm);
    this.embeddedOverlay = overlayData.embeddedOverlays();
    this.overlayData = overlayData.overlays();
    this.presentationLUTShape = overlayData.presentationLUTShape();

    // Initialize pixel padding and LUT modules
    var lutData = initializeLutData(dcm);
    this.pixelPaddingValue = lutData.pixelPaddingValue();
    this.pixelPaddingRangeLimit = lutData.pixelPaddingRangeLimit();
    this.modalityLUT = lutData.modalityLUT();
    this.voiLUT = lutData.voiLUT();

    // Initialize frame-specific collections
    this.minMaxPixelValues = initializeFrameCollection(frames);
    this.voiLutPerFrame = initializeFrameCollection(frames);
    this.modalityLutPerFrame = initializeFrameCollection(frames);
  }

  private LookupTableCV initializePaletteColorLookupTable(Attributes dcm) {
    if (hasPaletteColorLookupTable()) {
      LookupTableCV lookup = DicomImageUtils.getPaletteColorLookupTable(dcm);
      if (lookup != null) {
        return lookup;
      } else {
        LOGGER.warn("No palette color lookup table found in DICOM attributes");
      }
    }
    return null;
  }

  private PixelAttributes initializePixelAttributes(Attributes dcm, int bitsCompressed) {
    int samples = Math.max(dcm.getInt(Tag.SamplesPerPixel, 1), 1);
    var photometricInterpretation =
        PhotometricInterpretation.fromString(
            dcm.getString(Tag.PhotometricInterpretation, "MONOCHROME2"));
    int bitsAllocated = Math.max(dcm.getInt(Tag.BitsAllocated, 8), 1);
    int bitsStored =
        Math.min(Math.max(dcm.getInt(Tag.BitsStored, bitsAllocated), 1), bitsAllocated);
    int highBit = Math.min(dcm.getInt(Tag.HighBit, bitsStored - 1), bitsStored - 1);
    int finalBitsCompressed =
        bitsCompressed > 0 ? Math.min(bitsCompressed, bitsAllocated) : bitsStored;

    int pixelRepresentation = dcm.getInt(Tag.PixelRepresentation, 0);
    int planarConfiguration = dcm.getInt(Tag.PlanarConfiguration, 0);
    String pixelPresentation = dcm.getString(Tag.PixelPresentation);

    return new PixelAttributes(
        samples,
        photometricInterpretation,
        bitsAllocated,
        bitsStored,
        finalBitsCompressed,
        highBit,
        pixelRepresentation,
        planarConfiguration,
        pixelPresentation);
  }

  private DicomMetadata initializeDicomMetadata(Attributes dcm) {
    return new DicomMetadata(
        dcm.getString(Tag.SOPClassUID),
        dcm.getString(Tag.SeriesInstanceUID),
        dcm.getString(Tag.Modality),
        dcm.getString(Tag.StationName),
        AnatomicRegion.read(dcm));
  }

  private OverlayDataContainer initializeOverlayData(Attributes dcm) {
    var embeddedOverlays = EmbeddedOverlay.getEmbeddedOverlay(dcm);
    var overlays = OverlayData.getOverlayData(dcm, 0xffff);
    String presentationLUTShape = dcm.getString(Tag.PresentationLUTShape);

    return new OverlayDataContainer(embeddedOverlays, overlays, presentationLUTShape);
  }

  private LutDataContainer initializeLutData(Attributes dcm) {
    Integer pixelPaddingValue =
        DicomUtils.getIntegerFromDicomElement(dcm, Tag.PixelPaddingValue, null);
    Integer pixelPaddingRangeLimit =
        DicomUtils.getIntegerFromDicomElement(dcm, Tag.PixelPaddingRangeLimit, null);
    var modalityLUT = new ModalityLutModule(dcm);
    var voiLUT = new VoiLutModule(dcm);

    return new LutDataContainer(pixelPaddingValue, pixelPaddingRangeLimit, modalityLUT, voiLUT);
  }

  private static <T> List<T> initializeFrameCollection(int frames) {
    List<T> collection = new ArrayList<>(frames);
    for (int i = 0; i < frames; i++) {
      collection.add(null);
    }
    return collection;
  }

  // === Basic Image Properties ===

  /** Returns the number of rows (height) in the image. */
  public int getRows() {
    return rows;
  }

  /** Returns the number of columns (width) in the image. */
  public int getColumns() {
    return columns;
  }

  /** Returns the number of frames in the image (1 for single-frame images). */
  public int getFrames() {
    return frames;
  }

  /** Returns the number of samples (color components) per pixel. */
  public int getSamples() {
    return samples;
  }

  /** Returns true if this is a multi-frame image. */
  public boolean isMultiframe() {
    return frames > 1;
  }

  // === Pixel Representation Properties ===

  /** Returns the photometric interpretation defining the color space. */
  public PhotometricInterpretation getPhotometricInterpretation() {
    return photometricInterpretation;
  }

  /** Returns the number of bits allocated for each pixel sample. */
  public int getBitsAllocated() {
    return bitsAllocated;
  }

  /** Returns the number of bits actually used for pixel data storage. */
  public int getBitsStored() {
    return bitsStored;
  }

  /** Returns the number of bits used in compressed pixel data (0 if uncompressed). */
  public int getBitsCompressed() {
    return bitsCompressed;
  }

  /** Returns the position of the most significant bit in pixel data. */
  public int getHighBit() {
    return highBit;
  }

  /** Returns the pixel representation (0 = unsigned, 1 = signed). */
  public int getPixelRepresentation() {
    return pixelRepresentation;
  }

  /** Returns the planar configuration for multi-sample pixels. */
  public int getPlanarConfiguration() {
    return planarConfiguration;
  }

  /** Returns the pixel presentation value or null if not specified. */
  public String getPixelPresentation() {
    return pixelPresentation;
  }

  /** Returns true if pixel data is signed (two's complement). */
  public boolean isSigned() {
    return pixelRepresentation != 0;
  }

  /** Returns true if pixel samples are stored in separate planes (banded format). */
  public boolean isBanded() {
    return planarConfiguration != 0;
  }

  /** Returns the palette color lookup table if available, or null if not used. */
  public LookupTableCV getPaletteColorLookupTable() {
    return paletteColorLookupTable;
  }

  /** Returns true if the image uses a palette color lookup table. */
  public boolean hasPaletteColorLookupTable() {
    return photometricInterpretation == PhotometricInterpretation.PALETTE_COLOR
        || "COLOR".equals(pixelPresentation);
  }

  /** Returns true if pixel data represents floating-point values. */
  public boolean isFloatPixelData() {
    return (bitsAllocated == 32 && !"RTDOSE".equals(modality)) || bitsAllocated == 64;
  }

  // === Data Size Calculations ===

  /** Returns the size in bytes of a single frame of pixel data. */
  public int getFrameLength() {
    return rows * columns * samples * bitsAllocated / 8;
  }

  /** Returns the total size in bytes of all frames of pixel data. */
  public int getLength() {
    return getFrameLength() * frames;
  }

  // === DICOM Metadata ===

  /** Returns the SOP Class UID identifying the type of DICOM object. */
  public String getSopClassUID() {
    return sopClassUID;
  }

  /** Returns the Series Instance UID grouping related images. */
  public String getSeriesInstanceUID() {
    return seriesInstanceUID;
  }

  /** Returns the modality (imaging technique) used to acquire the image. */
  public String getModality() {
    return modality;
  }

  /** Returns the station name where the image was acquired. */
  public String getStationName() {
    return stationName;
  }

  /** Returns the anatomical region information or null if not specified. */
  public AnatomicRegion getAnatomicRegion() {
    return anatomicRegion;
  }

  // === Overlay and Presentation Data ===

  /** Returns an immutable list of embedded overlays. */
  public List<EmbeddedOverlay> getEmbeddedOverlay() {
    return Collections.unmodifiableList(embeddedOverlay);
  }

  /** Returns an immutable list of overlay data objects. */
  public List<OverlayData> getOverlayData() {
    return Collections.unmodifiableList(overlayData);
  }

  /** Returns true if this is a multi-frame image with embedded overlays. */
  public boolean isMultiframeWithEmbeddedOverlays() {
    return !embeddedOverlay.isEmpty() && frames > 1;
  }

  /** Returns the presentation LUT shape or null if not specified. */
  public String getPresentationLUTShape() {
    return presentationLUTShape;
  }

  // === Pixel Padding and LUT Modules ===

  /** Returns the pixel padding value as an Optional. */
  public Optional<Integer> getPixelPaddingValue() {
    return Optional.ofNullable(pixelPaddingValue);
  }

  /** Returns the pixel padding range limit as an Optional. */
  public Optional<Integer> getPixelPaddingRangeLimit() {
    return Optional.ofNullable(pixelPaddingRangeLimit);
  }

  /** Returns the modality LUT module for pixel value transformations. */
  public ModalityLutModule getModalityLUT() {
    return modalityLUT;
  }

  /** Returns the VOI LUT module for window/level transformations. */
  public VoiLutModule getVoiLUT() {
    return voiLUT;
  }

  // === Frame-Specific Data Management ===

  /**
   * Returns the min/max pixel values for the specified frame.
   *
   * @param frame the frame index (0-based)
   * @return the min/max result or null if not set or invalid frame
   */
  public MinMaxLocResult getMinMaxPixelValue(int frame) {
    if (!isValidFrameIndex(frame)) {
      LOGGER.warn("Invalid frame index for getting minMax: {}", frame);
      return null;
    }
    return minMaxPixelValues.get(frame);
  }

  /**
   * Sets the min/max pixel values for the specified frame.
   *
   * @param frame the frame index (0-based)
   * @param minMaxPixelValue the min/max result to set
   */
  public void setMinMaxPixelValue(int frame, MinMaxLocResult minMaxPixelValue) {
    if (!isValidFrameIndex(frame)) {
      LOGGER.warn("Unable to set MinMaxPixelValue for invalid frame index: {}", frame);
      return;
    }
    minMaxPixelValues.set(frame, minMaxPixelValue);
  }

  /**
   * Returns the VOI LUT module for the specified frame.
   *
   * @param frame the frame index (0-based)
   * @return the frame-specific VOI LUT or the base VOI LUT if not set
   */
  public VoiLutModule getVoiLutForFrame(int frame) {
    return getLutModule(voiLutPerFrame, voiLUT, frame);
  }

  /**
   * Sets the VOI LUT module for the specified frame.
   *
   * @param frame the frame index (0-based)
   * @param voiLut the VOI LUT module to set
   */
  public void setVoiLutForFrame(int frame, VoiLutModule voiLut) {
    if (!isValidFrameIndex(frame)) {
      LOGGER.warn("Unable to set VoiLutModule for invalid frame index: {}", frame);
      return;
    }
    voiLutPerFrame.set(frame, voiLut);
  }

  /**
   * Returns the modality LUT module for the specified frame.
   *
   * @param frame the frame index (0-based)
   * @return the frame-specific modality LUT or the base modality LUT if not set
   */
  public ModalityLutModule getModalityLutForFrame(int frame) {
    return getLutModule(modalityLutPerFrame, modalityLUT, frame);
  }

  /**
   * Sets the modality LUT module for the specified frame.
   *
   * @param frame the frame index (0-based)
   * @param modalityLut the modality LUT module to set
   */
  public void setModalityLutForFrame(int frame, ModalityLutModule modalityLut) {
    if (!isValidFrameIndex(frame)) {
      LOGGER.warn("Unable to set ModalityLutModule for invalid frame index: {}", frame);
      return;
    }
    modalityLutPerFrame.set(frame, modalityLut);
  }

  private boolean isValidFrameIndex(int frame) {
    return frame >= 0 && frame < frames;
  }

  private static <T> T getLutModule(List<T> list, T baseLut, int frame) {
    if (frame < 0 || frame >= list.size()) {
      return baseLut;
    }
    T frameLut = list.get(frame);
    return frameLut != null ? frameLut : baseLut;
  }

  // === Helper Records for Constructor Parameters ===

  private record PixelAttributes(
      int samples,
      PhotometricInterpretation photometricInterpretation,
      int bitsAllocated,
      int bitsStored,
      int bitsCompressed,
      int highBit,
      int pixelRepresentation,
      int planarConfiguration,
      String pixelPresentation) {}

  private record DicomMetadata(
      String sopClassUID,
      String seriesInstanceUID,
      String modality,
      String stationName,
      AnatomicRegion anatomicRegion) {}

  private record OverlayDataContainer(
      List<EmbeddedOverlay> embeddedOverlays,
      List<OverlayData> overlays,
      String presentationLUTShape) {}

  private record LutDataContainer(
      Integer pixelPaddingValue,
      Integer pixelPaddingRangeLimit,
      ModalityLutModule modalityLUT,
      VoiLutModule voiLUT) {}
}
