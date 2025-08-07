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
import org.dcm4che3.data.Sequence;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.VR;
import org.weasis.dicom.macro.Module;

public class HPImageSet extends Module {

  private final List<HPSelector> selectors;
  private int imageSetNumber;

  protected HPImageSet(List<HPSelector> selectors, Attributes attributes) {
    super(attributes);
    this.selectors = selectors;
    addSTimeBasedImageSetsSequence(attributes.getSequence(Tag.TimeBasedImageSetsSequence));
  }

  protected HPImageSet(int imageSetNumber) {
    this(null, imageSetNumber);
  }

  protected HPImageSet(HPImageSet shareSelectors, int imageSetNumber) {
    super(new Attributes());
    this.imageSetNumber = imageSetNumber;
    if (shareSelectors == null) {
      this.selectors = new ArrayList<>(4);
    } else {
      this.selectors = new ArrayList<>(shareSelectors.selectors);
      Sequence tbissq = shareSelectors.getTimeBasedImageSetsSequence();
      addSTimeBasedImageSetsSequence(tbissq);
    }
  }

  protected void addSTimeBasedImageSetsSequence(Sequence sequence) {
    if (sequence != null && !sequence.isEmpty()) {
      Sequence seq = dcmItems.ensureSequence(Tag.TimeBasedImageSetsSequence, sequence.size());
      for (Attributes a : sequence) {
        seq.add(new Attributes(a));
      }
      setImageSetNumber(imageSetNumber);
    }
  }

  public boolean contains(Attributes dcm, int frame) {
    for (HPSelector selector : selectors) {
      if (!selector.matches(dcm, frame)) {
        return false;
      }
    }
    return true;
  }

  public Sequence getImageSetSelectorSequence() {
    return dcmItems.getSequence(Tag.ImageSetSelectorSequence);
  }

  public Sequence getTimeBasedImageSetsSequence() {
    return dcmItems.getSequence(Tag.TimeBasedImageSetsSequence);
  }

  public void addTimeBasedImageSet(HPTimeBasedImageSet item) {
    dcmItems.ensureSequence(Tag.TimeBasedImageSetsSequence, 1).add(item.getAttributes());
  }

  public List<HPSelector> getImageSetSelectors() {
    return Collections.unmodifiableList(selectors);
  }

  public void addImageSetSelector(HPSelector selector) {
    dcmItems.ensureSequence(Tag.ImageSetSelectorSequence, 1).add(selector.getAttributes());
    selectors.add(selector);
  }

  public int getImageSetNumber() {
    return imageSetNumber;
  }

  public void setImageSetNumber(int i) {
    this.imageSetNumber = i;
    Sequence seq = getTimeBasedImageSetsSequence();
    if (seq != null && !seq.isEmpty()) {
      seq.forEach(attributes1 -> attributes1.setInt(Tag.ImageSetNumber, VR.US, i));
    }
  }
}
