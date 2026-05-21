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
import org.weasis.dicom.macro.Module;

public class HPScreenDefinition extends Module {

  public HPScreenDefinition(Attributes attributes) {
    super(attributes);
  }

  public HPScreenDefinition() {
    super(new Attributes());
  }

  @Generated
  public Integer getNumberOfVerticalPixels() {
    return DicomUtils.getIntegerFromDicomElement(dcmItems, Tag.NumberOfVerticalPixels, null);
  }

  @Generated
  public void setNumberOfVerticalPixels(int value) {
    dcmItems.setInt(Tag.NumberOfVerticalPixels, VR.US, value);
  }

  @Generated
  public Integer getNumberOfHorizontalPixels() {
    return DicomUtils.getIntegerFromDicomElement(dcmItems, Tag.NumberOfHorizontalPixels, null);
  }

  @Generated
  public void setNumberOfHorizontalPixels(int value) {
    dcmItems.setInt(Tag.NumberOfHorizontalPixels, VR.US, value);
  }

  @Generated
  public double[] getDisplayEnvironmentSpatialPosition() {
    return DicomUtils.getDoubleArrayFromDicomElement(
        dcmItems, Tag.DisplayEnvironmentSpatialPosition, null);
  }

  @Generated
  public void setDisplayEnvironmentSpatialPosition(double[] values) {
    dcmItems.setDouble(Tag.DisplayEnvironmentSpatialPosition, VR.FD, values);
  }

  @Generated
  public Integer getScreenMinimumColorBitDepth() {
    return DicomUtils.getIntegerFromDicomElement(dcmItems, Tag.ScreenMinimumColorBitDepth, null);
  }

  @Generated
  public void setScreenMinimumColorBitDepth(int value) {
    dcmItems.setInt(Tag.ScreenMinimumColorBitDepth, VR.US, value);
  }

  @Generated
  public Integer getScreenMinimumGrayscaleBitDepth() {
    return DicomUtils.getIntegerFromDicomElement(
        dcmItems, Tag.ScreenMinimumGrayscaleBitDepth, null);
  }

  @Generated
  public void setScreenMinimumGrayscaleBitDepth(int value) {
    dcmItems.setInt(Tag.ScreenMinimumGrayscaleBitDepth, VR.US, value);
  }

  @Generated
  public Integer getApplicationMaximumRepaintTime() {
    return DicomUtils.getIntegerFromDicomElement(dcmItems, Tag.ApplicationMaximumRepaintTime, null);
  }

  @Generated
  public void setApplicationMaximumRepaintTime(int value) {
    dcmItems.setInt(Tag.ApplicationMaximumRepaintTime, VR.US, value);
  }
}
