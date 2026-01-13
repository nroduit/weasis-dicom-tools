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
import org.weasis.dicom.macro.Module;

public class HPImageBox extends Module {

  public HPImageBox() {
    super(new Attributes());
  }

  public HPImageBox(Attributes item, int tot) {
    super(item);
    if (tot > 1) {
      if (!CodeString.TILED.equals(item.getString(Tag.ImageBoxLayoutType))) {
        throw new IllegalArgumentException(item.getString(Tag.ImageBoxLayoutType));
      }
    }
  }

  public Integer getImageBoxNumber() {
    return DicomUtils.getIntegerFromDicomElement(dcmItems, Tag.ImageBoxNumber, null);
  }

  public void setImageBoxNumber(int value) {
    dcmItems.setInt(Tag.ImageBoxNumber, VR.US, value);
  }

  public double[] getDisplayEnvironmentSpatialPosition() {
    return dcmItems.getDoubles(Tag.DisplayEnvironmentSpatialPosition);
  }

  public void setDisplayEnvironmentSpatialPosition(double[] values) {
    dcmItems.setDouble(Tag.DisplayEnvironmentSpatialPosition, VR.FD, values);
  }

  public String getImageBoxLayoutType() {
    return dcmItems.getString(Tag.ImageBoxLayoutType);
  }

  public void setImageBoxLayoutType(String type) {
    dcmItems.setString(Tag.ImageBoxLayoutType, VR.CS, type);
  }

  public Integer getImageBoxTileHorizontalDimension() {
    return DicomUtils.getIntegerFromDicomElement(
        dcmItems, Tag.ImageBoxTileHorizontalDimension, null);
  }

  public void setImageBoxTileHorizontalDimension(int value) {
    dcmItems.setInt(Tag.ImageBoxTileHorizontalDimension, VR.US, value);
  }

  public Integer getImageBoxTileVerticalDimension() {
    return DicomUtils.getIntegerFromDicomElement(dcmItems, Tag.ImageBoxTileVerticalDimension, null);
  }

  public void setImageBoxTileVerticalDimension(int value) {
    dcmItems.setInt(Tag.ImageBoxTileVerticalDimension, VR.US, value);
  }

  public String getImageBoxScrollDirection() {
    return dcmItems.getString(Tag.ImageBoxScrollDirection);
  }

  public void setImageBoxScrollDirection(String value) {
    dcmItems.setString(Tag.ImageBoxScrollDirection, VR.CS, value);
  }

  public String getImageBoxSmallScrollType() {
    return dcmItems.getString(Tag.ImageBoxSmallScrollType);
  }

  public void setImageBoxSmallScrollType(String value) {
    dcmItems.setString(Tag.ImageBoxSmallScrollType, VR.CS, value);
  }

  public Integer getImageBoxSmallScrollAmount() {
    return DicomUtils.getIntegerFromDicomElement(dcmItems, Tag.ImageBoxSmallScrollAmount, null);
  }

  public void setImageBoxSmallScrollAmount(int value) {
    dcmItems.setInt(Tag.ImageBoxSmallScrollAmount, VR.US, value);
  }

  public String getImageBoxLargeScrollType() {
    return dcmItems.getString(Tag.ImageBoxLargeScrollType);
  }

  public void setImageBoxLargeScrollType(String value) {
    dcmItems.setString(Tag.ImageBoxLargeScrollType, VR.CS, value);
  }

  public Integer getImageBoxOverlapPriority() {
    return DicomUtils.getIntegerFromDicomElement(dcmItems, Tag.ImageBoxOverlapPriority, null);
  }

  public void setImageBoxOverlapPriority(int value) {
    dcmItems.setInt(Tag.ImageBoxOverlapPriority, VR.US, value);
  }

  public Integer getPreferredPlaybackSequencing() {
    return DicomUtils.getIntegerFromDicomElement(dcmItems, Tag.PreferredPlaybackSequencing, null);
  }

  public void setPreferredPlaybackSequencing(int value) {
    dcmItems.setInt(Tag.PreferredPlaybackSequencing, VR.US, value);
  }

  public Integer getRecommendedDisplayFrameRate() {
    return DicomUtils.getIntegerFromDicomElement(dcmItems, Tag.RecommendedDisplayFrameRate, null);
  }

  public void setRecommendedDisplayFrameRate(int value) {
    dcmItems.setInt(Tag.RecommendedDisplayFrameRate, VR.IS, value);
  }

  public double getCineRelativeToRealTime() {
    return DicomUtils.getIntegerFromDicomElement(dcmItems, Tag.CineRelativeToRealTime, null);
  }

  public void setCineRelativeToRealTime(double value) {
    dcmItems.setDouble(Tag.CineRelativeToRealTime, VR.FD, value);
  }
}
