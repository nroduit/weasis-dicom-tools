/*
 * Copyright (c) 2024 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.ref;

import static org.weasis.dicom.ref.CodingScheme.DCM;
import static org.weasis.dicom.ref.CodingScheme.SCT;

public enum AnatomicModifier {
  RIGHT(SCT, 24028007),
  LEFT(SCT, 7771000),
  BILATERAL(SCT, 51440002),
  UNILATERAL(SCT, 66459002),
  LATERAL(SCT, 49370004),
  MEDIAN(DCM, 130290),
  ANTERIOR(SCT, 255549009),
  POSTERIOR(SCT, 255551008),
  CEPHALIC(SCT, 66787007),
  CAUDAL(SCT, 3583002),
  MEDIAL(SCT, 255561001),
  CENTRAL(SCT, 26216008),
  PERIPHERAL(SCT, 14414005),
  EXTERNAL(SCT, 261074009),
  INTERNAL(SCT, 260521003),
  INTERMEDIATE(SCT, 11896004),
  INFERIOR(SCT, 261089000),
  SUPERIOR(SCT, 264217000),
  TRANSVERSE(SCT, 62824007),
  PROXIMAL(SCT, 40415009),
  DISTAL(SCT, 46053002),
  POSTAXIAL(SCT, 60583000),
  PREAXIAL(SCT, 32400000),
  APICAL(SCT, 43674008),
  BASAL(SCT, 57195005),
  AFFERENT(SCT, 49530007),
  EFFERENT(SCT, 33843005),
  CORONAL(SCT, 81654009),
  SUPERFICIAL(SCT, 26283006),
  DEEP(SCT, 795002),
  HORIZONTAL(SCT, 24020000),
  LONGITUDINAL(SCT, 38717003),
  VERTICAL(SCT, 33096000),
  SAGITTAL(SCT, 30730003),
  AXIAL(SCT, 24422004),
  EXTRA_ARTICULAR(SCT, 87687004),
  SURFACE(SCT, 410679008),
  GUTTER(SCT, 68493006),
  HILAR(SCT, 32381004),
  CAPSULAR(SCT, 11070000),
  SUBCAPSULAR(SCT, 61397002),
  EDGE(SCT, 57183005),
  ANTEROLATERAL(SCT, 37197008),
  POSTEROLATERAL(SCT, 90069004),
  INTRA_ARTICULAR(SCT, 131183008),
  MARGINAL(SCT, 112233002);

  private final CodingScheme scheme;
  private final String codeValue;

  AnatomicModifier(CodingScheme scheme, int codeValue) {
    this.scheme = scheme;
    this.codeValue = String.valueOf(codeValue);
  }

  public String getCodeValue() {
    return codeValue;
  }

  public String getCodeMeaning() {
    return MesModifier.getString(codeValue);
  }

  public CodingScheme getScheme() {
    return scheme;
  }

  @Override
  public String toString() {
    return getCodeMeaning();
  }
}
