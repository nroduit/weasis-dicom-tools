/*
 * Copyright (c) 2024 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.hp.plugins;

import org.dcm4che3.data.Attributes;
import org.weasis.dicom.hp.HPSelector;
import org.weasis.dicom.hp.spi.HPSelectorCategoryService;

public class ImagePlaneSelectorService extends HPSelectorCategoryService {

  private static final float MIN_MIN_COSINE = 0.8f;
  private float minCosine = ImagePlaneSelector.DEF_MIN_COSINE;

  public ImagePlaneSelectorService() {
    super("IMAGE_PLANE");
  }

  @Override
  public void setProperty(String name, Object value) {
    if (!"MinCosine".equals(name)) {
      throw new IllegalArgumentException("Unsupported property: " + name);
    }
    float tmp = (Float) value;
    if (tmp < MIN_MIN_COSINE || tmp > 1f) {
      throw new IllegalArgumentException("minCosine: " + value);
    }
    minCosine = tmp;
  }

  @Override
  public Object getProperty(String name) {
    if (!"MinCosine".equals(name)) {
      throw new IllegalArgumentException("Unsupported property: " + name);
    }
    return minCosine;
  }

  @Override
  public HPSelector createHPSelector(Attributes filterOp) {
    ImagePlaneSelector sel = new ImagePlaneSelector(filterOp);
    sel.setMinCosine(minCosine);
    return sel;
  }
}
