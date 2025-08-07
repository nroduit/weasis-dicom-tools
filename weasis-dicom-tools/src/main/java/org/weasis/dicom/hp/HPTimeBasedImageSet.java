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

import java.util.Collection;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.VR;
import org.dcm4che3.img.util.DicomUtils;
import org.weasis.dicom.macro.Code;
import org.weasis.dicom.macro.Module;

public class HPTimeBasedImageSet extends Module {

  public HPTimeBasedImageSet() {
    super(new Attributes());
  }

  public HPTimeBasedImageSet(Attributes attributes) {
    super(attributes);
  }

  public Integer getImageSetNumber() {
    return DicomUtils.getIntegerFromDicomElement(dcmItems, Tag.ImageSetNumber, null);
  }

  public void setImageSetNumber(int imageSetNumber) {
    dcmItems.setInt(Tag.ImageSetNumber, VR.US, imageSetNumber);
  }

  public String getImageSetLabel() {
    return dcmItems.getString(Tag.ImageSetLabel);
  }

  public void setImageSetLabel(String imageSetLabel) {
    dcmItems.setString(Tag.ImageSetLabel, VR.LO, imageSetLabel);
  }

  public String getImageSetSelectorCategory() {
    return dcmItems.getString(Tag.ImageSetSelectorCategory);
  }

  public boolean hasRelativeTime() {
    return dcmItems.containsValue(Tag.RelativeTime);
  }

  public RelativeTime getRelativeTime() {
    RelativeTimeUnits units = RelativeTimeUnits.valueOf(dcmItems.getString(Tag.RelativeTimeUnits));
    return new RelativeTime(dcmItems.getInts(Tag.RelativeTime), units);
  }

  public void setRelativeTime(RelativeTime relativeTime) {
    dcmItems.setString(Tag.ImageSetSelectorCategory, VR.CS, CodeString.RELATIVE_TIME);
    dcmItems.setInt(Tag.RelativeTime, VR.US, relativeTime.getValues());
    dcmItems.setString(Tag.RelativeTimeUnits, VR.CS, relativeTime.getUnits().getCodeString());
  }

  public boolean hasAbstractPriorValue() {
    return dcmItems.containsValue(Tag.AbstractPriorValue);
  }

  public AbstractPriorValue getAbstractPriorValue() {
    return new AbstractPriorValue(dcmItems.getInts(Tag.AbstractPriorValue));
  }

  public void setAbstractPriorValue(AbstractPriorValue abstractPriorValue) {
    dcmItems.setString(Tag.ImageSetSelectorCategory, VR.CS, CodeString.ABSTRACT_PRIOR);
    dcmItems.setInt(Tag.AbstractPriorValue, VR.SS, abstractPriorValue.getValues());
  }

  public boolean hasAbstractPriorCode() {
    return dcmItems.containsValue(Tag.AbstractPriorCodeSequence);
  }

  public Code getAbstractPriorCode() {
    Collection<Code> items = Code.toCodeMacros(dcmItems.getSequence(Tag.AbstractPriorCodeSequence));
    return items.isEmpty() ? null : items.iterator().next();
  }

  public void setAbstractPriorCode(Code code) {
    dcmItems.setString(Tag.ImageSetSelectorCategory, VR.CS, CodeString.ABSTRACT_PRIOR);
    dcmItems.ensureSequence(Tag.AbstractPriorCodeSequence, 1).add(code.getAttributes());
  }
}
