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

import org.dcm4che3.data.Tag;
import org.dcm4che3.img.util.DicomUtils;
import org.weasis.core.util.annotations.Generated;

public abstract class AbstractHPComparator implements HPComparator {

  @Generated
  public String getImageSetSelectorUsageFlag() {
    return getAttributes().getString(Tag.ImageSetSelectorUsageFlag);
  }

  @Generated
  public Integer getSelectorAttribute() {
    return DicomUtils.getIntegerFromDicomElement(getAttributes(), Tag.SelectorAttribute, null);
  }

  @Generated
  public Integer getSelectorSequencePointer() {
    return DicomUtils.getIntegerFromDicomElement(
        getAttributes(), Tag.SelectorSequencePointer, null);
  }

  @Generated
  public Integer getFunctionalGroupPointer() {
    return DicomUtils.getIntegerFromDicomElement(getAttributes(), Tag.FunctionalGroupPointer, null);
  }

  @Generated
  public String getSelectorSequencePointerPrivateCreator() {
    return getAttributes().getString(Tag.SelectorSequencePointerPrivateCreator);
  }

  @Generated
  public String getFunctionalGroupPrivateCreator() {
    return getAttributes().getString(Tag.FunctionalGroupPrivateCreator);
  }

  @Generated
  public String getSelectorAttributePrivateCreator() {
    return getAttributes().getString(Tag.SelectorAttributePrivateCreator);
  }

  @Generated
  public Integer getSelectorValueNumber() {
    return DicomUtils.getIntegerFromDicomElement(getAttributes(), Tag.SelectorValueNumber, null);
  }

  @Generated
  public String getSortByCategory() {
    return getAttributes().getString(Tag.SortByCategory);
  }

  @Generated
  public String getSortingDirection() {
    return getAttributes().getString(Tag.SortingDirection);
  }
}
