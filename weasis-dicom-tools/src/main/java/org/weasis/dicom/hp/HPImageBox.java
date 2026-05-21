/*
 * Copyright (c) 2024 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.hp;

import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.VR;
import org.dcm4che3.img.util.DicomUtils;
import org.weasis.core.util.annotations.Generated;
import org.weasis.dicom.hp.enums.ImageBoxLayoutType;
import org.weasis.dicom.macro.Module;

public class HPImageBox extends Module {

  public HPImageBox() {
    super(new Attributes());
  }

  public HPImageBox(Attributes item, int tot) {
    super(item);
    if (tot > 1) {
      if (!ImageBoxLayoutType.TILED
          .getCodeString()
          .equals(item.getString(Tag.ImageBoxLayoutType))) {
        throw new IllegalArgumentException(item.getString(Tag.ImageBoxLayoutType));
      }
    }
  }

  @Generated
  public Integer getImageBoxNumber() {
    return DicomUtils.getIntegerFromDicomElement(dcmItems, Tag.ImageBoxNumber, null);
  }

  @Generated
  public void setImageBoxNumber(int value) {
    dcmItems.setInt(Tag.ImageBoxNumber, VR.US, value);
  }

  @Generated
  public double[] getDisplayEnvironmentSpatialPosition() {
    return dcmItems.getDoubles(Tag.DisplayEnvironmentSpatialPosition);
  }

  @Generated
  public void setDisplayEnvironmentSpatialPosition(double[] values) {
    dcmItems.setDouble(Tag.DisplayEnvironmentSpatialPosition, VR.FD, values);
  }

  @Generated
  public ImageBoxLayoutType getImageBoxLayoutType() {
    return ImageBoxLayoutType.fromString(dcmItems.getString(Tag.ImageBoxLayoutType));
  }

  @Generated
  public void setImageBoxLayoutType(ImageBoxLayoutType type) {
    dcmItems.setString(Tag.ImageBoxLayoutType, VR.CS, type.getCodeString());
  }

  @Generated
  public Integer getImageBoxTileHorizontalDimension() {
    return DicomUtils.getIntegerFromDicomElement(
        dcmItems, Tag.ImageBoxTileHorizontalDimension, null);
  }

  @Generated
  public void setImageBoxTileHorizontalDimension(int value) {
    dcmItems.setInt(Tag.ImageBoxTileHorizontalDimension, VR.US, value);
  }

  @Generated
  public Integer getImageBoxTileVerticalDimension() {
    return DicomUtils.getIntegerFromDicomElement(dcmItems, Tag.ImageBoxTileVerticalDimension, null);
  }

  @Generated
  public void setImageBoxTileVerticalDimension(int value) {
    dcmItems.setInt(Tag.ImageBoxTileVerticalDimension, VR.US, value);
  }

  @Generated
  public String getImageBoxScrollDirection() {
    return dcmItems.getString(Tag.ImageBoxScrollDirection);
  }

  @Generated
  public void setImageBoxScrollDirection(String value) {
    dcmItems.setString(Tag.ImageBoxScrollDirection, VR.CS, value);
  }

  @Generated
  public String getImageBoxSmallScrollType() {
    return dcmItems.getString(Tag.ImageBoxSmallScrollType);
  }

  @Generated
  public void setImageBoxSmallScrollType(String value) {
    dcmItems.setString(Tag.ImageBoxSmallScrollType, VR.CS, value);
  }

  @Generated
  public Integer getImageBoxSmallScrollAmount() {
    return DicomUtils.getIntegerFromDicomElement(dcmItems, Tag.ImageBoxSmallScrollAmount, null);
  }

  @Generated
  public void setImageBoxSmallScrollAmount(int value) {
    dcmItems.setInt(Tag.ImageBoxSmallScrollAmount, VR.US, value);
  }

  @Generated
  public String getImageBoxLargeScrollType() {
    return dcmItems.getString(Tag.ImageBoxLargeScrollType);
  }

  @Generated
  public void setImageBoxLargeScrollType(String value) {
    dcmItems.setString(Tag.ImageBoxLargeScrollType, VR.CS, value);
  }

  @Generated
  public Integer getImageBoxOverlapPriority() {
    return DicomUtils.getIntegerFromDicomElement(dcmItems, Tag.ImageBoxOverlapPriority, null);
  }

  @Generated
  public void setImageBoxOverlapPriority(int value) {
    dcmItems.setInt(Tag.ImageBoxOverlapPriority, VR.US, value);
  }

  @Generated
  public Integer getPreferredPlaybackSequencing() {
    return DicomUtils.getIntegerFromDicomElement(dcmItems, Tag.PreferredPlaybackSequencing, null);
  }

  @Generated
  public void setPreferredPlaybackSequencing(int value) {
    dcmItems.setInt(Tag.PreferredPlaybackSequencing, VR.US, value);
  }

  @Generated
  public Integer getRecommendedDisplayFrameRate() {
    return DicomUtils.getIntegerFromDicomElement(dcmItems, Tag.RecommendedDisplayFrameRate, null);
  }

  @Generated
  public void setRecommendedDisplayFrameRate(int value) {
    dcmItems.setInt(Tag.RecommendedDisplayFrameRate, VR.IS, value);
  }

  @Generated
  public double getCineRelativeToRealTime() {
    return DicomUtils.getIntegerFromDicomElement(dcmItems, Tag.CineRelativeToRealTime, null);
  }

  @Generated
  public void setCineRelativeToRealTime(double value) {
    dcmItems.setDouble(Tag.CineRelativeToRealTime, VR.FD, value);
  }
}
