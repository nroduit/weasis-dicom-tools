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

import java.util.Date;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.VR;
import org.weasis.dicom.hp.AbstractHPComparator;
import org.weasis.dicom.hp.CodeString;

public class ByAcqTimeComparator extends AbstractHPComparator {

  private final int sign;
  private final Attributes sortOp;

  public ByAcqTimeComparator(Attributes sortOp) {
    this.sortOp = sortOp;
    String cs = sortOp.getString(Tag.SortingDirection);
    if (cs == null) {
      throw new IllegalArgumentException("Missing (0072,0604) Sorting Direction");
    }
    this.sign = CodeString.sortingDirectionToSign(cs);
  }

  public ByAcqTimeComparator(String sortingDirection) {
    this.sign = CodeString.sortingDirectionToSign(sortingDirection);
    this.sortOp = new Attributes();
    sortOp.setString(Tag.SortByCategory, VR.CS, CodeString.BY_ACQ_TIME);
    sortOp.setString(Tag.SortingDirection, VR.CS, sortingDirection);
  }

  public final Attributes getAttributes() {
    return sortOp;
  }

  public int compare(Attributes o1, int frame1, Attributes o2, int frame2) {
    Date t1 = toAcqTime(o1);
    Date t2 = toAcqTime(o2);
    if (t1 == null || t2 == null) {
      return 0;
    }
    return t1.compareTo(t2) * sign;
  }

  private Date toAcqTime(Attributes o) {
    Date t = o.getDate(Tag.AcquisitionDate, Tag.AcquisitionTime);
    if (t == null) {
      t = o.getDate(Tag.AcquisitionDateTime);
      if (t == null) {
        t = o.getDate(Tag.ContentDate, Tag.ContentTime);
      }
    }
    return t;
  }
}
