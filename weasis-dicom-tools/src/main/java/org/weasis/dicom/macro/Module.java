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

import java.util.Collection;
import java.util.Objects;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Sequence;

public class Module {

  protected final Attributes dcmItems;

  public Module(Attributes dcmItems) {
    this.dcmItems = Objects.requireNonNull(dcmItems);
  }

  /**
   * Get the attributes. Direct modifications of the returned <tt>Attributes</tt> is strongly
   * discouraged as it may cause inconsistencies in the internal state of this object.
   *
   * @return the attributes
   */
  public final Attributes getAttributes() {
    return dcmItems;
  }

  public final void removeAllSequenceItems(int seqTag) {
    Sequence seq = dcmItems.getSequence(seqTag);
    if (seq != null) {
      seq.clear();
    }
  }

  public final void removeSequenceItem(int seqTag, int index) {
    Sequence seq = dcmItems.getSequence(seqTag);
    if (seq != null && index < seq.size()) {
      seq.remove(index);
    }
  }

  public final void removeSequenceItem(int seqTag, Attributes item) {
    Sequence seq = dcmItems.getSequence(seqTag);
    if (seq != null) {
      seq.remove(item);
    }
  }

  protected void updateSequence(int tag, Module module) {

    Sequence oldSequence = dcmItems.getSequence(tag);

    if (module != null) {
      if (oldSequence != null) {
        oldSequence.clear(); // Allows removing parents of Attributes
      }
      Attributes attributes = module.getAttributes();
      Attributes parent = attributes.getParent();
      if (parent != null) {
        // Copy attributes and set parent to null
        attributes = new Attributes(attributes);
      }
      dcmItems.newSequence(tag, 1).add(attributes);
    }
  }

  protected void updateSequence(int tag, Collection<? extends Module> modules) {

    Sequence oldSequence = dcmItems.getSequence(tag);

    if (modules != null && !modules.isEmpty()) {
      if (oldSequence != null) {
        oldSequence.clear(); // Allows removing parents of Attributes
      }
      Sequence newSequence = dcmItems.newSequence(tag, modules.size());
      for (Module module : modules) {
        Attributes attributes = module.getAttributes();
        Attributes parent = attributes.getParent();
        if (parent != null) {
          // Copy attributes and set parent to null
          attributes = new Attributes(attributes);
        }
        newSequence.add(attributes);
      }
    }
  }
}
