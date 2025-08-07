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
import org.weasis.dicom.macro.Code;

public abstract class AbstractHPSelector implements HPSelector {

  public String getImageSetSelectorUsageFlag() {
    return getAttributes().getString(Tag.ImageSetSelectorUsageFlag);
  }

  public String getFilterByCategory() {
    return getAttributes().getString(Tag.FilterByCategory);
  }

  public String getFilterByAttributePresence() {
    return getAttributes().getString(Tag.FilterByAttributePresence);
  }

  public Integer getSelectorAttribute() {
    return DicomUtils.getIntegerFromDicomElement(getAttributes(), Tag.SelectorAttribute, null);
  }

  public String getSelectorAttributeVR() {
    return getAttributes().getString(Tag.SelectorAttributeVR);
  }

  public Integer getSelectorSequencePointer() {
    return DicomUtils.getIntegerFromDicomElement(
        getAttributes(), Tag.SelectorSequencePointer, null);
  }

  public Integer getFunctionalGroupPointer() {
    return DicomUtils.getIntegerFromDicomElement(getAttributes(), Tag.FunctionalGroupPointer, null);
  }

  public String getSelectorSequencePointerPrivateCreator() {
    return getAttributes().getString(Tag.SelectorSequencePointerPrivateCreator);
  }

  public String getFunctionalGroupPrivateCreator() {
    return getAttributes().getString(Tag.FunctionalGroupPrivateCreator);
  }

  public String getSelectorAttributePrivateCreator() {
    return getAttributes().getString(Tag.SelectorAttributePrivateCreator);
  }

  public Object getSelectorValue() {
    String vrStr = getSelectorAttributeVR();
    return switch (CodeString.getVR(vrStr)) {
      case AT -> getAttributes().getInts(Tag.SelectorATValue);
      case CS -> getAttributes().getStrings(Tag.SelectorCSValue);
      case DS -> getAttributes().getFloats(Tag.SelectorDSValue);
      case FD -> getAttributes().getDoubles(Tag.SelectorFDValue);
      case FL -> getAttributes().getFloats(Tag.SelectorFLValue);
      case IS -> getAttributes().getInts(Tag.SelectorISValue);
      case LO -> getAttributes().getStrings(Tag.SelectorLOValue);
      case LT -> getAttributes().getStrings(Tag.SelectorLTValue);
      case PN -> getAttributes().getStrings(Tag.SelectorPNValue);
      case SH -> getAttributes().getStrings(Tag.SelectorSHValue);
      case SL -> getAttributes().getInts(Tag.SelectorSLValue);
      case SQ -> Code.toCodeMacros(getAttributes().getSequence(Tag.SelectorCodeSequenceValue));
      case SS -> getAttributes().getInts(Tag.SelectorSSValue);
      case ST -> getAttributes().getStrings(Tag.SelectorSTValue);
      case UL -> getAttributes().getInts(Tag.SelectorULValue);
      case US -> getAttributes().getInts(Tag.SelectorUSValue);
      case UT -> getAttributes().getStrings(Tag.SelectorUTValue);
      default -> null;
    };
  }

  public Integer getSelectorValueNumber() {
    return DicomUtils.getIntegerFromDicomElement(getAttributes(), Tag.SelectorValueNumber, null);
  }

  public String getFilterByOperator() {
    return getAttributes().getString(Tag.FilterByOperator);
  }
}
