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
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.VR;
import org.weasis.dicom.geom.ImageOrientation;
import org.weasis.dicom.geom.ImageOrientation.Plan;
import org.weasis.dicom.geom.Orientation;
import org.weasis.dicom.geom.PatientOrientation.Biped;
import org.weasis.dicom.geom.Vector3;
import org.weasis.dicom.hp.AbstractHPSelector;

public class ImagePlaneSelector extends AbstractHPSelector {

  public static final float DEF_MIN_COSINE = 0.9f;

  private final Attributes filterOp;
  private float minCosine = DEF_MIN_COSINE;
  private final Plan[] imagePlanes;

  public ImagePlaneSelector(Attributes filterOp) {
    String vrStr = filterOp.getString(Tag.SelectorAttributeVR);
    if (vrStr == null) {
      throw new IllegalArgumentException("Missing (0072,0050) Selector Attribute VR");
    }
    if (!"CS".equals(vrStr)) {
      throw new IllegalArgumentException("(0072,0050) Selector Attribute VR: " + vrStr);
    }
    String[] values = filterOp.getStrings(Tag.SelectorCSValue);
    if (values == null || values.length == 0) {
      throw new IllegalArgumentException("Missing (0072,0062) AbstractHPSelector CS Value");
    }
    this.imagePlanes = new Plan[values.length];
    for (int i = 0; i < values.length; i++) {
      imagePlanes[i] = Plan.valueOf(values[i]);
    }
    this.filterOp = filterOp;
  }

  public ImagePlaneSelector(Plan[] imagePlanes) {
    this.imagePlanes = imagePlanes.clone();
    this.filterOp = new Attributes();
    filterOp.setString(Tag.FilterByCategory, VR.CS, "IMAGE_PLANE");
    filterOp.setString(Tag.SelectorAttributeVR, VR.CS, "CS");
    String[] values = new String[imagePlanes.length];
    for (int i = 0; i < values.length; i++) {
      values[i] = imagePlanes[i].name();
    }
    filterOp.setString(Tag.SelectorCSValue, VR.CS, values);
  }

  public final Attributes getAttributes() {
    return filterOp;
  }

  public final float getMinCosine() {
    return minCosine;
  }

  public final void setMinCosine(float minCosine) {
    this.minCosine = minCosine;
  }

  public boolean matches(Attributes attributes, int frame) {
    Plan imagePlane;

    double[] floats = attributes.getDoubles(Tag.ImageOrientationPatient);
    if (floats != null && floats.length == 6) {
      Vector3 vr = new Vector3(floats);
      Vector3 vc = new Vector3(floats[3], floats[4], floats[5]);
      imagePlane = ImageOrientation.getPlan(vr, vc, minCosine);
    } else {
      String[] ss = attributes.getStrings(Tag.PatientOrientation);
      if (ss != null && ss.length == 2) {
        Orientation rowAxis = Biped.fromString(ss[0]);
        Orientation colAxis = Biped.fromString(ss[1]);
        imagePlane = ImageOrientation.getPlan(rowAxis, colAxis);
      } else {
        return true;
      }
    }
    for (Plan plane : imagePlanes) {
      if (imagePlane == plane) {
        return true;
      }
    }
    return false;
  }
}
