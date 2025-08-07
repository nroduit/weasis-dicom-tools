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
import org.weasis.dicom.hp.AbstractHPComparator;
import org.weasis.dicom.hp.CodeString;

public class AlongAxisComparator extends AbstractHPComparator {

  private static final int X = 0;
  private static final int Y = 1;
  private static final int Z = 2;
  private static final int RX = 0;
  private static final int RY = 1;
  private static final int RZ = 2;
  private static final int CX = 3;
  private static final int CY = 4;
  private static final int CZ = 5;

  private final Attributes sortOp;
  private final int sign;

  public AlongAxisComparator(Attributes sortOp) {
    this.sortOp = sortOp;
    String cs = sortOp.getString(Tag.SortingDirection);
    if (cs == null) {
      throw new IllegalArgumentException("Missing (0072,0604) Sorting Direction");
    }
    try {
      this.sign = CodeString.sortingDirectionToSign(cs);
    } catch (IllegalArgumentException e) {
      throw new IllegalArgumentException("Invalid (0072,0604) Sorting Direction: " + cs);
    }
  }

  public AlongAxisComparator(String sortingDirection) {
    this.sign = CodeString.sortingDirectionToSign(sortingDirection);
    this.sortOp = new Attributes();
    sortOp.setString(Tag.SortByCategory, VR.CS, CodeString.ALONG_AXIS);
    sortOp.setString(Tag.SortingDirection, VR.CS, sortingDirection);
  }

  public final Attributes getAttributes() {
    return sortOp;
  }

  public int compare(Attributes o1, int frame1, Attributes o2, int frame2) {
    try {
      double v1 = dot(o1, frame1);
      double v2 = dot(o2, frame2);
      if (v1 < v2) {
        return -sign;
      }
      if (v1 > v2) {
        return sign;
      }
    } catch (NullPointerException ignore) {
      // missing image position/orientation information - treat as equal
    } catch (NumberFormatException | IndexOutOfBoundsException ignore) {
      // invalid image position/orientation information - treat as equal
    }
    return 0;
  }

  private double dot(Attributes o, int frame) {
    double[] ipp = getImagePositionPatient(o, frame);
    double[] iop = getImageOrientationPatient(o, frame);
    double nx = iop[RY] * iop[CZ] - iop[RZ] * iop[CY];
    double ny = iop[RZ] * iop[CX] - iop[RX] * iop[CZ];
    double nz = iop[RX] * iop[CY] - iop[RY] * iop[CX];
    return ipp[X] * nx + ipp[Y] * ny + ipp[Z] * nz;
  }

  private double[] getImageOrientationPatient(Attributes o, int frame) {
    double[] iop;
    if ((iop = o.getDoubles(Tag.ImageOrientationPatient)) != null) {
      return iop;
    }

    // Check the shared first in the case of image orientation
    Attributes item = o.getNestedDataset(Tag.PerFrameFunctionalGroupsSequence, frame);
    item = item.getNestedDataset(Tag.PlanePositionSequence, 0);
    if ((iop = item.getDoubles(Tag.ImageOrientationPatient)) != null) {
      return iop;
    }

    item = o.getNestedDataset(Tag.SharedFunctionalGroupsSequence, 0);
    return item.getDoubles(Tag.ImageOrientationPatient);
  }

  private double[] getImagePositionPatient(Attributes o, int frame) {
    double[] ipp;
    if ((ipp = o.getDoubles(Tag.ImagePositionPatient)) != null) {
      return ipp;
    }

    // Check the per frame first in the case of image position
    Attributes item = o.getNestedDataset(Tag.PerFrameFunctionalGroupsSequence, frame);
    item = item.getNestedDataset(Tag.PlanePositionSequence, 0);
    if ((ipp = item.getDoubles(Tag.ImagePositionPatient)) != null) {
      return ipp;
    }

    item = o.getNestedDataset(Tag.SharedFunctionalGroupsSequence, 0);
    return item.getDoubles(Tag.ImagePositionPatient);
  }
}
