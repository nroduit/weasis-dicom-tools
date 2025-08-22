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
import org.dcm4che3.img.data.EmbeddedOverlay;
import org.dcm4che3.img.data.OverlayData;
import org.dcm4che3.img.lut.ModalityLutModule;
import org.dcm4che3.img.lut.VoiLutModule;
import org.dcm4che3.img.util.DicomUtils;
import org.dcm4che3.img.util.PaletteColorUtils;
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
    this.paletteColorLookupTable = createPaletteColorLookupTable(dcm);

    // Initialize DICOM metadata
    var metadata = createDicomMetadata(dcm);
    this.sopClassUID = metadata.sopClassUID();
    this.seriesInstanceUID = metadata.seriesInstanceUID();
    this.modality = metadata.modality();
    this.stationName = metadata.stationName();
    this.anatomicRegion = metadata.anatomicRegion();

    // Initialize overlay and presentation data
    var overlayContainer = createOverlayData(dcm);
    this.embeddedOverlay = overlayContainer.embeddedOverlays();
    this.overlayData = overlayContainer.overlays();
    this.presentationLUTShape = overlayContainer.presentationLUTShape();

    // Initialize pixel padding and LUT modules
    var lutData = createLutData(dcm);
    this.pixelPaddingValue = lutData.pixelPaddingValue();
    this.pixelPaddingRangeLimit = lutData.pixelPaddingRangeLimit();
    this.modalityLUT = lutData.modalityLUT();
    this.voiLUT = lutData.voiLUT();

    // Initialize frame-specific collections
    this.minMaxPixelValues = createNullFilledList(frames);
    this.voiLutPerFrame = createNullFilledList(frames);
    this.modalityLutPerFrame = createNullFilledList(frames);
  }

  private LookupTableCV createPaletteColorLookupTable(Attributes dcm) {
    if (hasPaletteColorLookupTable()) {
      LookupTableCV lookup = PaletteColorUtils.getPaletteColorLookupTable(dcm);
      if (lookup != null) {
        return lookup;
      } else {
        LOGGER.warn("No palette color lookup table found in DICOM attributes");
      }
    }
    return null;
  }

  private PixelAttributes initializePixelAttributes(Attributes dcm, int bitsCompressed) {
    var numSamples = Math.max(dcm.getInt(Tag.SamplesPerPixel, 1), 1);
    var pixelColorModel =
        PhotometricInterpretation.fromString(
            dcm.getString(Tag.PhotometricInterpretation, "MONOCHROME2"));
    var maxBitsAllocated = Math.max(dcm.getInt(Tag.BitsAllocated, 8), 1);
    var bitsStoredValue =
        Math.min(Math.max(dcm.getInt(Tag.BitsStored, maxBitsAllocated), 1), maxBitsAllocated);
    var highBitValue = Math.min(dcm.getInt(Tag.HighBit, bitsStoredValue - 1), bitsStoredValue - 1);
    var finalBitsCompressed =
        bitsCompressed > 0 ? Math.min(bitsCompressed, maxBitsAllocated) : bitsStoredValue;
    var pixelRepValue = dcm.getInt(Tag.PixelRepresentation, 0);
    var planarConfig = dcm.getInt(Tag.PlanarConfiguration, 0);
    var presentationType = dcm.getString(Tag.PixelPresentation);

    return new PixelAttributes(
        numSamples,
        pixelColorModel,
        maxBitsAllocated,
        bitsStoredValue,
        finalBitsCompressed,
        highBitValue,
        pixelRepValue,
        planarConfig,
        presentationType);
  }

  private DicomMetadata createDicomMetadata(Attributes dcm) {
    return new DicomMetadata(
        dcm.getString(Tag.SOPClassUID),
        dcm.getString(Tag.SeriesInstanceUID),
        dcm.getString(Tag.Modality),
        dcm.getString(Tag.StationName),
        AnatomicRegion.read(dcm));
  }

  private OverlayDataContainer createOverlayData(Attributes dcm) {
    var embeddedOverlays = EmbeddedOverlay.getEmbeddedOverlay(dcm);
    var overlays = OverlayData.getOverlayData(dcm, 0xffff);

    return new OverlayDataContainer(
        embeddedOverlays, overlays, dcm.getString(Tag.PresentationLUTShape));
  }

  private LutDataContainer createLutData(Attributes dcm) {
    var paddingValue = DicomUtils.getIntegerFromDicomElement(dcm, Tag.PixelPaddingValue, null);
    var paddingRangeLimit =
        DicomUtils.getIntegerFromDicomElement(dcm, Tag.PixelPaddingRangeLimit, null);
    return new LutDataContainer(
        paddingValue, paddingRangeLimit, new ModalityLutModule(dcm), new VoiLutModule(dcm));
  }

  private static <T> List<T> createNullFilledList(int size) {
    return new ArrayList<>(Collections.nCopies(size, null));
  }

  // === Basic Image Properties ===

  public int getRows() {
    return rows;
  }

  public int getColumns() {
    return columns;
  }

  public int getFrames() {
    return frames;
  }

  public int getSamples() {
    return samples;
  }

  public boolean isMultiframe() {
    return frames > 1;
  }

  // === Pixel Representation Properties ===

  public PhotometricInterpretation getPhotometricInterpretation() {
    return photometricInterpretation;
  }

  public int getBitsAllocated() {
    return bitsAllocated;
  }

  public int getBitsStored() {
    return bitsStored;
  }

  public int getBitsCompressed() {
    return bitsCompressed;
  }

  public int getHighBit() {
    return highBit;
  }

  public int getPixelRepresentation() {
    return pixelRepresentation;
  }

  public int getPlanarConfiguration() {
    return planarConfiguration;
  }

  public String getPixelPresentation() {
    return pixelPresentation;
  }

  public boolean isSigned() {
    return pixelRepresentation != 0;
  }

  public boolean isBanded() {
    return planarConfiguration != 0;
  }

  public LookupTableCV getPaletteColorLookupTable() {
    return paletteColorLookupTable;
  }

  /** Returns true if the image uses a palette color lookup table. */
  public boolean hasPaletteColorLookupTable() {
    return photometricInterpretation == PhotometricInterpretation.PALETTE_COLOR
        || (photometricInterpretation.isMonochrome()
            && pixelPresentation != null
            && pixelPresentation.contains("COLOR"));
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

  public String getSopClassUID() {
    return sopClassUID;
  }

  public String getSeriesInstanceUID() {
    return seriesInstanceUID;
  }

  public String getModality() {
    return modality;
  }

  public String getStationName() {
    return stationName;
  }

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

  public boolean isMultiframeWithEmbeddedOverlays() {
    return isMultiframe() && !embeddedOverlay.isEmpty();
  }

  public String getPresentationLUTShape() {
    return presentationLUTShape;
  }

  // === Pixel Padding and LUT Modules ===

  public Optional<Integer> getPixelPaddingValue() {
    return Optional.ofNullable(pixelPaddingValue);
  }

  public Optional<Integer> getPixelPaddingRangeLimit() {
    return Optional.ofNullable(pixelPaddingRangeLimit);
  }

  public ModalityLutModule getModalityLUT() {
    return modalityLUT;
  }

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
    return isValidFrameIndex(frame) ? minMaxPixelValues.get(frame) : null;
  }

  /**
   * Sets the min/max pixel values for the specified frame.
   *
   * @param frame the frame index (0-based)
   * @param minMaxPixelValue the min/max result to set
   */
  public void setMinMaxPixelValue(int frame, MinMaxLocResult minMaxPixelValue) {
    if (isValidFrameIndex(frame)) {
      minMaxPixelValues.set(frame, minMaxPixelValue);
    }
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
    if (isValidFrameIndex(frame)) {
      voiLutPerFrame.set(frame, voiLut);
    }
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
    if (isValidFrameIndex(frame)) {
      modalityLutPerFrame.set(frame, modalityLut);
    }
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

  // === Record Types for Data Transfer ===

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
