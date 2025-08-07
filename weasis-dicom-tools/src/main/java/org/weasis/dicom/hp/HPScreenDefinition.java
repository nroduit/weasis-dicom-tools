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

public class HPScreenDefinition extends Module {

  public HPScreenDefinition(Attributes attributes) {
    super(attributes);
  }

  public HPScreenDefinition() {
    super(new Attributes());
  }

  public Integer getNumberOfVerticalPixels() {
    return DicomUtils.getIntegerFromDicomElement(dcmItems, Tag.NumberOfVerticalPixels, null);
  }

  public void setNumberOfVerticalPixels(int value) {
    dcmItems.setInt(Tag.NumberOfVerticalPixels, VR.US, value);
  }

  public Integer getNumberOfHorizontalPixels() {
    return DicomUtils.getIntegerFromDicomElement(dcmItems, Tag.NumberOfHorizontalPixels, null);
  }

  public void setNumberOfHorizontalPixels(int value) {
    dcmItems.setInt(Tag.NumberOfHorizontalPixels, VR.US, value);
  }

  public double[] getDisplayEnvironmentSpatialPosition() {
    return DicomUtils.getDoubleArrayFromDicomElement(
        dcmItems, Tag.DisplayEnvironmentSpatialPosition, null);
  }

  public void setDisplayEnvironmentSpatialPosition(double[] values) {
    dcmItems.setDouble(Tag.DisplayEnvironmentSpatialPosition, VR.FD, values);
  }

  public Integer getScreenMinimumColorBitDepth() {
    return DicomUtils.getIntegerFromDicomElement(dcmItems, Tag.ScreenMinimumColorBitDepth, null);
  }

  public void setScreenMinimumColorBitDepth(int value) {
    dcmItems.setInt(Tag.ScreenMinimumColorBitDepth, VR.US, value);
  }

  public Integer getScreenMinimumGrayscaleBitDepth() {
    return DicomUtils.getIntegerFromDicomElement(
        dcmItems, Tag.ScreenMinimumGrayscaleBitDepth, null);
  }

  public void setScreenMinimumGrayscaleBitDepth(int value) {
    dcmItems.setInt(Tag.ScreenMinimumGrayscaleBitDepth, VR.US, value);
  }

  public Integer getApplicationMaximumRepaintTime() {
    return DicomUtils.getIntegerFromDicomElement(dcmItems, Tag.ApplicationMaximumRepaintTime, null);
  }

  public void setApplicationMaximumRepaintTime(int value) {
    dcmItems.setInt(Tag.ApplicationMaximumRepaintTime, VR.US, value);
  }
}
