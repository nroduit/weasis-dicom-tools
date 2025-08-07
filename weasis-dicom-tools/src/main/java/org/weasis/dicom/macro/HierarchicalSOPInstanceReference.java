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
import java.util.List;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Sequence;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.VR;

/**
 * DICOM Hierarchical SOP Instance Reference Macro implementation for managing structured references
 * to DICOM objects organized by study and series hierarchy.
 *
 * <p>This class provides a hierarchical approach to referencing DICOM SOP instances, organizing
 * them by:
 *
 * <ul>
 *   <li>Study level - identified by Study Instance UID
 *   <li>Series level - containing collections of series and their instances
 *   <li>Instance level - individual SOP instances within each series
 * </ul>
 *
 * <p>This hierarchical structure is commonly used in DICOM objects that need to reference multiple
 * related images or objects, such as:
 *
 * <ul>
 *   <li>Structured Reports referencing evidence images
 *   <li>Presentation States referencing displayed images
 *   <li>Key Object Selection Documents referencing significant instances
 *   <li>Hanging Protocols specifying layout arrangements
 * </ul>
 *
 * <p>The hierarchy allows for efficient organization and navigation of large sets of referenced
 * objects while maintaining the DICOM information model structure.
 *
 * @see SeriesAndInstanceReference
 * @see SOPInstanceReference
 * @see Module
 */
public class HierarchicalSOPInstanceReference extends Module {

  /**
   * Creates a HierarchicalSOPInstanceReference from existing DICOM attributes.
   *
   * @param dcmItems the DICOM attributes containing hierarchical reference information
   */
  public HierarchicalSOPInstanceReference(Attributes dcmItems) {
    super(dcmItems);
  }

  /** Creates an empty HierarchicalSOPInstanceReference with default attributes. */
  public HierarchicalSOPInstanceReference() {
    super(new Attributes());
  }

  /**
   * Converts a DICOM sequence to a collection of HierarchicalSOPInstanceReference objects.
   *
   * @param seq the DICOM sequence to convert (may be null or empty)
   * @return an immutable collection of HierarchicalSOPInstanceReference objects, empty if input is
   *     null or empty
   */
  public static Collection<HierarchicalSOPInstanceReference>
      toHierarchicalSOPInstanceReferenceMacros(Sequence seq) {
    if (seq == null || seq.isEmpty()) {
      return List.of();
    }

    return seq.stream().map(HierarchicalSOPInstanceReference::new).toList();
  }

  /**
   * Gets the unique identifier of the study containing the referenced instances.
   *
   * @return the Study Instance UID that groups all referenced series and instances
   */
  public String getStudyInstanceUID() {
    return dcmItems.getString(Tag.StudyInstanceUID);
  }

  public void setStudyInstanceUID(String uid) {
    dcmItems.setString(Tag.StudyInstanceUID, VR.UI, uid);
  }

  /**
   * Gets the collection of series and their referenced instances within this study.
   *
   * <p>Each SeriesAndInstanceReference contains:
   *
   * <ul>
   *   <li>Series Instance UID identifying the series
   *   <li>Collection of individual SOP instance references within that series
   *   <li>Optional retrieve information (AE Title, Location, etc.)
   * </ul>
   *
   * @return collection of series references, empty if no series are referenced
   */
  public Collection<SeriesAndInstanceReference> getReferencedSeries() {
    return SeriesAndInstanceReference.toSeriesAndInstanceReferenceMacros(
        dcmItems.getSequence(Tag.ReferencedSeriesSequence));
  }

  public void setReferencedSeries(Collection<SeriesAndInstanceReference> referencedSeries) {
    updateSequence(Tag.ReferencedSeriesSequence, referencedSeries);
  }
}
