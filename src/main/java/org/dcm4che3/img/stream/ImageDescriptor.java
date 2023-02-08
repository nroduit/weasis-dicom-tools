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

import java.util.List;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.dcm4che3.image.PhotometricInterpretation;
import org.dcm4che3.img.data.EmbeddedOverlay;
import org.dcm4che3.img.data.OverlayData;
import org.dcm4che3.img.lut.ModalityLutModule;
import org.dcm4che3.img.lut.VoiLutModule;
import org.dcm4che3.img.util.DicomUtils;

/**
 * @author Nicolas Roduit
 */
public final class ImageDescriptor {

  private final int rows;
  private final int columns;
  private final int samples;
  private final PhotometricInterpretation photometricInterpretation;
  private final int bitsAllocated;
  private final int bitsStored;
  private final int bitsCompressed;
  private final int pixelRepresentation;
  private final String sopClassUID;
  private final String bodyPartExamined;
  private final int frames;
  private final List<EmbeddedOverlay> embeddedOverlay;
  private final List<OverlayData> overlayData;
  private final int planarConfiguration;
  private final String presentationLUTShape;
  private final String modality;
  private final Integer pixelPaddingValue;
  private final Integer pixelPaddingRangeLimit;
  private final ModalityLutModule modalityLUT;
  private final VoiLutModule voiLUT;
  private final int highBit;
  private final String stationName;
  private final String pixelPresentation;

  public ImageDescriptor(Attributes dcm) {
    this(dcm, 0);
  }

  public ImageDescriptor(Attributes dcm, int bitsCompressed) {
    this.rows = dcm.getInt(Tag.Rows, 0);
    this.columns = dcm.getInt(Tag.Columns, 0);
    this.samples = dcm.getInt(Tag.SamplesPerPixel, 0);
    this.photometricInterpretation =
        PhotometricInterpretation.fromString(
            dcm.getString(Tag.PhotometricInterpretation, "MONOCHROME2"));
    this.pixelPresentation = dcm.getString(Tag.PixelPresentation);
    this.bitsAllocated = dcm.getInt(Tag.BitsAllocated, 8);
    this.bitsStored = dcm.getInt(Tag.BitsStored, bitsAllocated);
    this.highBit = dcm.getInt(Tag.HighBit, bitsStored - 1);
    this.bitsCompressed = bitsCompressed > 0 ? Math.min(bitsCompressed, bitsAllocated) : bitsStored;
    this.pixelRepresentation = dcm.getInt(Tag.PixelRepresentation, 0);
    this.planarConfiguration = dcm.getInt(Tag.PlanarConfiguration, 0);
    this.sopClassUID = dcm.getString(Tag.SOPClassUID);
    this.bodyPartExamined = dcm.getString(Tag.BodyPartExamined);
    this.stationName = dcm.getString(Tag.StationName);
    this.frames = dcm.getInt(Tag.NumberOfFrames, 1);
    this.embeddedOverlay = EmbeddedOverlay.getEmbeddedOverlay(dcm);
    this.overlayData = OverlayData.getOverlayData(dcm, 0xffff);
    this.presentationLUTShape = dcm.getString(Tag.PresentationLUTShape);
    this.modality = dcm.getString(Tag.Modality);
    this.pixelPaddingValue =
        DicomUtils.getIntegerFromDicomElement(dcm, Tag.PixelPaddingValue, null);
    this.pixelPaddingRangeLimit =
        DicomUtils.getIntegerFromDicomElement(dcm, Tag.PixelPaddingRangeLimit, null);
    this.modalityLUT = new ModalityLutModule(dcm); // TODO handle PixelValueTransformationSequence
    this.voiLUT = new VoiLutModule(dcm); // TODO handle PixelValueTransformationSequence
  }

  public int getRows() {
    return rows;
  }

  public int getColumns() {
    return columns;
  }

  public int getSamples() {
    return samples;
  }

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

  public String getPixelPresentation() {
    return pixelPresentation;
  }

  public boolean hasPaletteColorLookupTable() {
    return photometricInterpretation == PhotometricInterpretation.PALETTE_COLOR
        || (pixelPresentation != null && !"MONOCHROME".equals(pixelPresentation));
  }

  public int getPixelRepresentation() {
    return pixelRepresentation;
  }

  public int getPlanarConfiguration() {
    return planarConfiguration;
  }

  public String getSopClassUID() {
    return sopClassUID;
  }

  public String getBodyPartExamined() {
    return bodyPartExamined;
  }

  public String getStationName() {
    return stationName;
  }

  public int getFrames() {
    return frames;
  }

  public boolean isMultiframe() {
    return frames > 1;
  }

  public int getFrameLength() {
    return rows * columns * samples * bitsAllocated / 8;
  }

  public int getLength() {
    return getFrameLength() * frames;
  }

  public boolean isSigned() {
    return pixelRepresentation != 0;
  }

  public boolean isBanded() {
    return planarConfiguration != 0;
  }

  public List<EmbeddedOverlay> getEmbeddedOverlay() {
    return embeddedOverlay;
  }

  public boolean isMultiframeWithEmbeddedOverlays() {
    return !embeddedOverlay.isEmpty() && frames > 1;
  }

  public String getPresentationLUTShape() {
    return presentationLUTShape;
  }

  public String getModality() {
    return modality;
  }

  public Integer getPixelPaddingValue() {
    return pixelPaddingValue;
  }

  public Integer getPixelPaddingRangeLimit() {
    return pixelPaddingRangeLimit;
  }

  public ModalityLutModule getModalityLUT() {
    return modalityLUT;
  }

  public VoiLutModule getVoiLUT() {
    return voiLUT;
  }

  public boolean isFloatPixelData() {
    return (bitsAllocated == 32 && !"RTDOSE".equals(modality)) || bitsAllocated == 64;
  }

  public int getHighBit() {
    return highBit;
  }

  public List<OverlayData> getOverlayData() {
    return overlayData;
  }
}
