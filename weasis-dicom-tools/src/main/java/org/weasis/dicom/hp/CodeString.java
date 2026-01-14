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

import org.dcm4che3.data.VR;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CodeString {
  private static final Logger LOGGER = LoggerFactory.getLogger(CodeString.class);
  public static final String YES = "YES";
  public static final String NO = "NO";

  public static final String MATCH = "MATCH";
  public static final String NO_MATCH = "NO_MATCH";

  public static final String PRESENT = "PRESENT";
  public static final String NOT_PRESENT = "NOT_PRESENT";

  public static final String ABSTRACT_PRIOR = "ABSTRACT_PRIOR";
  public static final String RELATIVE_TIME = "RELATIVE_TIME";

  public static final String INCREASING = "INCREASING";
  public static final String DECREASING = "DECREASING";

  public static final String ALONG_AXIS = "ALONG_AXIS";
  public static final String BY_ACQ_TIME = "BY_ACQ_TIME";

  public static final String MAINTAIN_LAYOUT = "MAINTAIN_LAYOUT";
  public static final String ADAPT_LAYOUT = "ADAPT_LAYOUT";

  public static final String MANUFACTURER = "MANUFACTURER";
  public static final String SITE = "SITE";
  public static final String SINGLE_USER = "SINGLE_USER";
  public static final String USER_GROUP = "USER_GROUP";

  public static final String COLOR = "COLOR";

  public static final String MPR = "MPR";
  public static final String RENDERING_3D = "3D_RENDERING";
  public static final String SLAB = "SLAB";

  public static final String SAGITTAL = "SAGITTAL";
  public static final String TRANSVERSE = "TRANSVERSE";
  public static final String CORONAL = "CORONAL";
  public static final String OBLIQUE = "OBLIQUE";

  public static final String LUNG = "LUNG";
  public static final String MEDIASTINUM = "MEDIASTINUM";
  public static final String ABDO_PELVIS = "ABDO_PELVIS";
  public static final String LIVER = "LIVER";
  public static final String SOFT_TISSUE = "SOFT_TISSUE";
  public static final String BONE = "BONE";
  public static final String BRAIN = "BRAIN";
  public static final String POST_FOSSA = "POST_FOSSA";

  public static final String BLACK_BODY = "BLACK_BODY";
  public static final String HOT_IRON = "HOT_IRON";
  public static final String DEFAULT = "DEFAULT";

  public static final String TILED = "TILED";
  public static final String STACK = "STACK";
  public static final String CINE = "CINE";
  public static final String PROCESSED = "PROCESSED";
  public static final String SINGLE = "SINGLE";

  public static final String VERTICAL = "VERTICAL";
  public static final String HORIZONTAL = "HORIZONTAL";

  public static final String PAGE = "PAGE";
  public static final String ROW_COLUMN = "ROW_COLUMN";
  public static final String IMAGE = "IMAGE";

  public static VR getVR(String vr) {
    try {
      return VR.valueOf(vr);
    } catch (Exception e) {
      LOGGER.error("Cannot find VR for {}", vr);
      return VR.OB;
    }
  }

  public static int sortingDirectionToSign(String cs) {
    if (cs.equals(INCREASING)) {
      return 1;
    }
    if (cs.equals(DECREASING)) {
      return -1;
    }
    throw new IllegalArgumentException("Invalid (0072,0604) Sorting Direction: " + cs);
  }
}
