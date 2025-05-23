/*
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.macro;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Sequence;
import org.dcm4che3.data.Tag;

public class SOPInstanceReferenceAndPurpose extends SOPInstanceReference {

  public SOPInstanceReferenceAndPurpose(Attributes dcmItems) {
    super(dcmItems);
  }

  public SOPInstanceReferenceAndPurpose() {
    this(new Attributes());
  }

  public static Collection<SOPInstanceReferenceAndPurpose> toSOPInstanceReferenceAndPurposesMacros(
      Sequence seq) {

    if (seq == null || seq.isEmpty()) {
      return Collections.emptyList();
    }

    ArrayList<SOPInstanceReferenceAndPurpose> list = new ArrayList<>(seq.size());

    for (Attributes attr : seq) {
      list.add(new SOPInstanceReferenceAndPurpose(attr));
    }

    return list;
  }

  public Code getPurposeOfReferenceCode() {
    return Code.getNestedCode(dcmItems, Tag.PurposeOfReferenceCodeSequence);
  }

  public void setPurposeOfReferenceCode(Code code) {
    updateSequence(Tag.PurposeOfReferenceCodeSequence, code);
  }
}
