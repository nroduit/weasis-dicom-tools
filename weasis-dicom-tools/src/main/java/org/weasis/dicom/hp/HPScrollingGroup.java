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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.VR;
import org.weasis.dicom.macro.Module;

public class HPScrollingGroup extends Module {

  private final List<HPDisplaySet> displaySets;

  public HPScrollingGroup() {
    super(new Attributes());
    displaySets = new ArrayList<>();
  }

  public HPScrollingGroup(Attributes attributes, List<HPDisplaySet> totDisplaySets) {
    super(attributes);
    int[] group = attributes.getInts(Tag.DisplaySetScrollingGroup);
    if (group == null) {
      throw new IllegalArgumentException("Missing (0072,0212) Display Set Scrolling Group");
    }
    if (group.length < 2) {
      throw new IllegalArgumentException(
          "Display Set Scrolling Group cannot have less than 2 Display Sets");
    }
    this.displaySets = new ArrayList<>(group.length);
    for (int i : group) {
      try {
        this.displaySets.add(totDisplaySets.get(i - 1));
      } catch (IndexOutOfBoundsException e) {
        throw new IllegalArgumentException("Referenced Display Set does not exists");
      }
    }
  }

  public List<HPDisplaySet> getDisplaySets() {
    return Collections.unmodifiableList(displaySets);
  }

  public void addDisplaySet(HPDisplaySet displaySet) {
    if (displaySet.getDisplaySetNumber() == 0) {
      throw new IllegalArgumentException("Missing Display Set Number");
    }
    displaySets.add(displaySet);
    updateAttributes();
  }

  public boolean removeDisplaySet(HPDisplaySet displaySet) {
    if (displaySet == null) {
      throw new NullPointerException();
    }

    if (!displaySets.remove(displaySet)) {
      return false;
    }
    updateAttributes();
    return true;
  }

  public void updateAttributes() {
    int[] val = new int[displaySets.size()];
    for (int i = 0; i < val.length; i++) {
      val[i] = displaySets.get(i).getDisplaySetNumber();
    }
    dcmItems.setInt(Tag.DisplaySetScrollingGroup, VR.US, val);
  }

  public boolean isValid() {
    return displaySets.size() >= 2;
  }
}
