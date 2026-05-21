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
import org.dcm4che3.data.Sequence;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.VR;
import org.weasis.core.util.annotations.Generated;
import org.weasis.dicom.macro.Code;
import org.weasis.dicom.macro.Module;

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
}
