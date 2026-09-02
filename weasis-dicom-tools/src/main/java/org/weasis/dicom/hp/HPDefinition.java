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
import java.util.Optional;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Sequence;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.VR;
import org.weasis.core.util.StringUtil;
import org.weasis.core.util.annotations.Generated;
import org.weasis.dicom.macro.Code;
import org.weasis.dicom.macro.Module;
import org.weasis.dicom.ref.AnatomicRegion;
import org.weasis.dicom.ref.AnatomySelector;
import org.weasis.dicom.ref.CodingScheme;
import org.weasis.dicom.ref.RegionGroup;

public class HPDefinition extends Module {

  public HPDefinition() {
    this(new Attributes());
  }

  public HPDefinition(Attributes item) {
    super(item);
  }

  @Generated
  public String getModality() {
    return dcmItems.getString(Tag.Modality);
  }

  @Generated
  public void setModality(String modality) {
    dcmItems.setString(Tag.Modality, VR.CS, modality);
  }

  @Generated
  public String getLaterality() {
    return dcmItems.getString(Tag.Laterality);
  }

  @Generated
  public void setLaterality(String laterality) {
    dcmItems.setString(Tag.Laterality, VR.CS, laterality);
  }

  @Generated
  public Collection<Code> getAnatomicRegionCode() {
    return Code.toCodeMacros(dcmItems.getSequence(Tag.AnatomicRegionSequence));
  }

  @Generated
  public void addAnatomicRegionCodes(Code code) {
    addCode(Tag.AnatomicRegionSequence, code);
  }

  @Generated
  public Collection<Code> getProcedureCodes() {
    return Code.toCodeMacros(dcmItems.getSequence(Tag.ProcedureCodeSequence));
  }

  @Generated
  public void addProcedureCode(Code code) {
    addCode(Tag.ProcedureCodeSequence, code);
  }

  @Generated
  public Collection<Code> getReasonForRequestedProcedureCodes() {
    return Code.toCodeMacros(dcmItems.getSequence(Tag.ReasonForRequestedProcedureCodeSequence));
  }

  @Generated
  public void addReasonForRequestedProcedureCode(Code code) {
    addCode(Tag.ReasonForRequestedProcedureCodeSequence, code);
  }

  @Generated
  protected void addCode(int tag, Code code) {
    Sequence seq = dcmItems.ensureSequence(tag, 1);
    seq.add(code.getAttributes());
  }

  /**
   * Whether the protocol intent of this item covers a study image: each criterion the item carries
   * (modality, anatomy, laterality) must hold; procedure and reason codes are not evaluated.
   *
   * @param modality Modality of the image
   * @param anatomy the resolved anatomy of the image, null when unknown
   * @param laterality Image Laterality, else Laterality of the image, null when absent
   */
  public boolean appliesTo(String modality, AnatomicRegion anatomy, String laterality) {
    String hpModality = getModality();
    if (StringUtil.hasText(hpModality) && !hpModality.trim().equals(trim(modality))) {
      return false;
    }
    Collection<Code> regions = getAnatomicRegionCode();
    if (!regions.isEmpty() && regions.stream().noneMatch(c -> regionApplies(c, anatomy))) {
      return false;
    }
    return lateralityApplies(getLaterality(), trim(laterality));
  }

  /**
   * Same as {@link #appliesTo(String, AnatomicRegion, String)} with the values of an image.
   *
   * <p>Deviation: the anatomy of an image without Anatomic Region Sequence is resolved from Body
   * Part Examined (PS3.16 Table L-1), which PS3.3 C.23.1 does not use for Sequence Matching.
   */
  public boolean appliesTo(Attributes image) {
    return appliesTo(
        image.getString(Tag.Modality),
        AnatomicRegion.read(image),
        image.getString(Tag.ImageLaterality, image.getString(Tag.Laterality)));
  }

  /**
   * An exact code (retired SNOMED-RT identifiers resolved) or, as a Weasis leniency beyond the
   * exact Sequence Matching of PS3.3 C.23.1, the code of a region group area covering the image
   * region: a protocol for the Chest applies to a Lung image.
   */
  static boolean regionApplies(Code code, AnatomicRegion anatomy) {
    if (anatomy == null || !StringUtil.hasText(code.getExistingCodeValue())) {
      return false;
    }
    String token = code.getCodingSchemeDesignator() + ":" + code.getExistingCodeValue();
    Optional<AnatomySelector> selector = AnatomySelector.parse(token);
    if (selector.isEmpty()) {
      return false;
    }
    if (selector.get().matches(anatomy)) {
      return true;
    }
    return selector.get() instanceof AnatomySelector.Code c
        && c.scheme() == CodingScheme.SCT
        && RegionGroup.fromSnomedCode(c.codeValue()).map(anatomy::isIn).orElse(false);
  }

  // Laterality (0020,0060) of the item: R, L, B, U; empty (Type 2) applies to any image.
  static boolean lateralityApplies(String hpLaterality, String imageLaterality) {
    String hp = trim(hpLaterality);
    if (hp == null) {
      return true;
    }
    if ("U".equals(hp)) {
      return imageLaterality == null || "U".equals(imageLaterality);
    }
    return hp.equals(imageLaterality);
  }

  private static String trim(String value) {
    return StringUtil.hasText(value) ? value.trim() : null;
  }
}
