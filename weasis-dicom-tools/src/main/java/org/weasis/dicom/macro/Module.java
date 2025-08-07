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

/**
 * Base class for DICOM module implementations that encapsulates DICOM attributes and provides
 * utility methods for sequence manipulation.
 *
 * <p>This class serves as a foundation for specific DICOM module implementations by providing:
 *
 * <ul>
 *   <li>Safe access to underlying DICOM attributes
 *   <li>Common sequence manipulation operations
 *   <li>Automatic handling of attribute parent relationships
 * </ul>
 *
 * <p><strong>Thread Safety:</strong> This class is not thread-safe. External synchronization is
 * required when accessed from multiple threads.
 *
 * @since 1.0
 */
public class Module {

  /** The underlying DICOM attributes container. */
  protected final Attributes dcmItems;

  /**
   * Constructs a new Module with the specified DICOM attributes.
   *
   * @param dcmItems the DICOM attributes container (must not be null)
   * @throws NullPointerException if dcmItems is null
   */
  public Module(Attributes dcmItems) {
    this.dcmItems = Objects.requireNonNull(dcmItems);
  }

  /**
   * Returns the underlying DICOM attributes.
   *
   * <p><strong>Warning:</strong> Direct modifications of the returned {@code Attributes} are
   * strongly discouraged as they may cause inconsistencies in the internal state of this object.
   * Use the provided methods for safe modifications.
   *
   * @return the DICOM attributes (never null)
   */
  public final Attributes getAttributes() {
    return dcmItems;
  }

  /**
   * Removes all items from the specified sequence.
   *
   * @param seqTag the sequence tag identifier
   */
  public final void removeAllSequenceItems(int seqTag) {
    var seq = dcmItems.getSequence(seqTag);
    if (seq != null) {
      seq.clear();
    }
  }

  /**
   * Removes the item at the specified index from the sequence.
   *
   * @param seqTag the sequence tag identifier
   * @param index the zero-based index of the item to remove
   */
  public final void removeSequenceItem(int seqTag, int index) {
    var seq = dcmItems.getSequence(seqTag);
    if (seq != null && index >= 0 && index < seq.size()) {
      seq.remove(index);
    }
  }

  /**
   * Removes the specified item from the sequence.
   *
   * @param seqTag the sequence tag identifier
   * @param item the attributes item to remove
   */
  public final void removeSequenceItem(int seqTag, Attributes item) {
    var seq = dcmItems.getSequence(seqTag);
    if (seq != null) {
      seq.remove(item);
    }
  }

  /**
   * Updates a sequence with a single module item.
   *
   * <p>This method clears any existing sequence and creates a new one with the provided module. If
   * the module is null, the sequence is effectively removed.
   *
   * @param tag the sequence tag identifier
   * @param module the module to add to the sequence (null to remove sequence)
   */
  protected void updateSequence(int tag, Module module) {

    clearExistingSequence(tag);

    if (module != null) {
      var attributes = prepareAttributesForSequence(module.getAttributes());
      dcmItems.newSequence(tag, 1).add(attributes);
    }
  }

  /**
   * Updates a sequence with multiple module items.
   *
   * <p>This method clears any existing sequence and creates a new one with all provided modules. If
   * the collection is null or empty, the sequence is effectively removed.
   *
   * @param tag the sequence tag identifier
   * @param modules the collection of modules to add to the sequence (null or empty to remove
   *     sequence)
   */
  protected void updateSequence(int tag, Collection<? extends Module> modules) {

    clearExistingSequence(tag);

    if (modules != null && !modules.isEmpty()) {
      var newSequence = dcmItems.newSequence(tag, modules.size());
      for (var module : modules) {
        var attributes = prepareAttributesForSequence(module.getAttributes());
        newSequence.add(attributes);
      }
    }
  }

  /** Clears the existing sequence if present. */
  private void clearExistingSequence(int tag) {
    var oldSequence = dcmItems.getSequence(tag);
    if (oldSequence != null) {
      oldSequence.clear(); // Allows removing parents of Attributes
    }
  }

  /** Prepares attributes for inclusion in a sequence by handling parent relationships. */
  private Attributes prepareAttributesForSequence(Attributes attributes) {
    var parent = attributes.getParent();
    if (parent != null) {
      // Copy attributes and set parent to null to avoid circular references
      return new Attributes(attributes);
    }
    return attributes;
  }
}
