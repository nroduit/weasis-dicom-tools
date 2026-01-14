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

public class HPNavigationGroup extends Module {

  private HPDisplaySet navDisplaySet;
  private final List<HPDisplaySet> refDisplaySets;

  public HPNavigationGroup() {
    super(new Attributes());
    refDisplaySets = new ArrayList<>();
  }

  public HPNavigationGroup(Attributes attributes, List<HPDisplaySet> displaySets) {
    super(attributes);
    int[] group = attributes.getInts(Tag.ReferenceDisplaySets);
    if (group == null || group.length == 0) {
      throw new IllegalArgumentException("Missing (0072,0218) Reference Display Sets");
    }
    int nds = attributes.getInt(Tag.NavigationDisplaySet, 0);
    if (nds != 0) {
      try {
        navDisplaySet = displaySets.get(nds - 1);
      } catch (IndexOutOfBoundsException e) {
        throw new IllegalArgumentException(
            "Navigation Display Set does not exists: "
                + attributes.getString(Tag.NavigationDisplaySet));
      }
    } else {
      if (group.length == 1) {
        throw new IllegalArgumentException(
            "Singular Reference Display Set without Navigation Display Set: "
                + attributes.getString(Tag.ReferenceDisplaySets));
      }
    }
    refDisplaySets = new ArrayList<>(group.length);
    for (int i : group) {
      try {
        refDisplaySets.add(displaySets.get(i - 1));
      } catch (IndexOutOfBoundsException e) {
        throw new IllegalArgumentException(
            "Reference Display Set does not exists: "
                + attributes.getString(Tag.ReferenceDisplaySets));
      }
    }
  }

  public final HPDisplaySet getNavigationDisplaySet() {
    return navDisplaySet;
  }

  public final void setNavigationDisplaySet(HPDisplaySet displaySet) {
    this.navDisplaySet = displaySet;
    updateDisplaySetNumber();
  }

  public List<HPDisplaySet> getReferenceDisplaySets() {
    return Collections.unmodifiableList(refDisplaySets);
  }

  public void addReferenceDisplaySet(HPDisplaySet displaySet) {
    if (displaySet.getDisplaySetNumber() == 0) {
      throw new IllegalArgumentException("Missing Display Set Number");
    }
    refDisplaySets.add(displaySet);
    updateReferenceDisplaySets();
  }

  public boolean removeReferenceDisplaySet(HPDisplaySet displaySet) {
    if (displaySet == null) {
      throw new NullPointerException();
    }

    if (!refDisplaySets.remove(displaySet)) {
      return false;
    }
    updateReferenceDisplaySets();
    return true;
  }

  public void updateDisplaySetNumber() {
    if (navDisplaySet == null) {
      dcmItems.remove(Tag.NavigationDisplaySet);
    } else {
      Integer val = navDisplaySet.getDisplaySetNumber();
      if (val != null) {
        dcmItems.setInt(Tag.NavigationDisplaySet, VR.US, val);
      }
    }
    updateReferenceDisplaySets();
  }

  public void updateAttributes() {
    updateDisplaySetNumber();
    updateReferenceDisplaySets();
  }

  private void updateReferenceDisplaySets() {
    int[] val = new int[refDisplaySets.size()];
    for (int i = 0; i < val.length; i++) {
      val[i] = refDisplaySets.get(i).getDisplaySetNumber();
    }
    dcmItems.setInt(Tag.ReferenceDisplaySets, VR.US, val);
  }

  public boolean isValid() {
    return refDisplaySets.size() >= (navDisplaySet != null ? 1 : 2);
  }
}
