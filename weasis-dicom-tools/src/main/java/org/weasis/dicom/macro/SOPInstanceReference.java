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
import org.dcm4che3.img.util.DicomUtils;

/**
 * DICOM SOP Instance Reference Macro implementation for managing references to individual DICOM
 * objects (Service-Object Pair instances).
 *
 * <p>This is the fundamental building block for DICOM object referencing, providing precise
 * identification of individual DICOM instances within the imaging enterprise. A SOP Instance
 * Reference uniquely identifies:
 *
 * <ul>
 *   <li>A specific DICOM object (image, waveform, document, etc.)
 *   <li>The type of object through its SOP Class
 *   <li>Optional frame-level granularity for multi-frame objects
 *   <li>Instance-level metadata for navigation and display
 * </ul>
 *
 * <p><strong>SOP (Service-Object Pair) Concept:</strong>
 *
 * <ul>
 *   <li><strong>Service:</strong> The DICOM operations supported by the object type
 *   <li><strong>Object:</strong> The actual data content and attributes
 *   <li><strong>Pair:</strong> The combination defining both what it is and what can be done with
 *       it
 * </ul>
 *
 * <p><strong>Common SOP Classes:</strong>
 *
 * <ul>
 *   <li><strong>CT Image Storage:</strong> Computed Tomography images
 *   <li><strong>MR Image Storage:</strong> Magnetic Resonance images
 *   <li><strong>US Image Storage:</strong> Ultrasound images and videos
 *   <li><strong>SR Document Storage:</strong> Structured Report documents
 *   <li><strong>KO Document Storage:</strong> Key Object Selection documents
 *   <li><strong>PR State Storage:</strong> Grayscale Softcopy Presentation States
 * </ul>
 *
 * <p><strong>Reference Granularity Levels:</strong>
 *
 * <ol>
 *   <li><strong>Instance Level:</strong> References the entire DICOM object
 *   <li><strong>Frame Level:</strong> References specific frames within multi-frame objects
 *   <li><strong>Segment Level:</strong> References specific segments (in specialized SOP classes)
 * </ol>
 *
 * <p><strong>Usage Scenarios:</strong>
 *
 * <ul>
 *   <li>Key Object Documents marking significant images
 *   <li>Structured Reports citing evidence instances
 *   <li>Presentation States defining display parameters
 *   <li>Hanging Protocols organizing viewer layouts
 *   <li>Teaching Files creating educational collections
 *   <li>Quality Assurance workflows tracking reviewed instances
 * </ul>
 *
 * @see SOPInstanceReferenceAndMAC
 * @see SOPInstanceReferenceAndPurpose
 * @see Module
 */
public class SOPInstanceReference extends Module {

  /**
   * Creates a SOPInstanceReference from existing DICOM attributes.
   *
   * @param dcmItems the DICOM attributes containing SOP instance reference information
   */
  public SOPInstanceReference(Attributes dcmItems) {
    super(dcmItems);
  }

  /** Creates an empty SOPInstanceReference with default attributes. */
  public SOPInstanceReference() {
    super(new Attributes());
  }

  /**
   * Converts a DICOM sequence to a collection of SOPInstanceReference objects.
   *
   * @param seq the DICOM sequence to convert (may be null or empty)
   * @return an immutable collection of SOPInstanceReference objects, empty if input is null or
   *     empty
   */
  public static Collection<SOPInstanceReference> toSOPInstanceReferenceMacros(Sequence seq) {
    if (seq == null || seq.isEmpty()) {
      return List.of();
    }

    return seq.stream().map(SOPInstanceReference::new).toList();
  }

  /**
   * Gets the array of frame numbers referenced within a multi-frame SOP instance.
   *
   * <p>Frame numbers provide sub-instance level referencing for multi-frame DICOM objects such as:
   *
   * <ul>
   *   <li><strong>Ultrasound:</strong> Cine loops and multi-frame acquisitions
   *   <li><strong>Nuclear Medicine:</strong> Dynamic and gated studies
   *   <li><strong>Enhanced CT/MR:</strong> Multi-phase and functional imaging
   *   <li><strong>X-Ray Angiography:</strong> Fluoroscopic sequences
   *   <li><strong>Microscopy:</strong> Multi-focal plane imaging
   * </ul>
   *
   * <p><strong>Frame Numbering:</strong> Frames are numbered starting from 1 (not 0). An empty or
   * null array indicates the entire multi-frame object is referenced.
   *
   * @return array of referenced frame numbers (1-based), or null if entire instance is referenced
   */
  public int[] getReferencedFrameNumber() {
    return DicomUtils.getIntArrayFromDicomElement(dcmItems, Tag.ReferencedFrameNumber, null);
  }

  /**
   * Sets the frame number references for multi-frame objects.
   *
   * <p><strong>Usage Guidelines:</strong>
   *
   * <ul>
   *   <li>Frame numbers must be 1-based (first frame is frame 1)
   *   <li>Omitting frame numbers means the entire instance is referenced
   *   <li>Frame numbers should be sorted in ascending order
   *   <li>Duplicate frame numbers should be avoided
   * </ul>
   *
   * @param frameNumbers the list of frame numbers to reference (1-based), or empty for entire
   *     instance
   */
  public void setReferencedFrameNumber(int... frameNumbers) {
    dcmItems.setInt(Tag.ReferencedFrameNumber, VR.IS, frameNumbers);
  }

  /**
   * Gets the globally unique identifier of the referenced SOP instance.
   *
   * <p>The SOP Instance UID is the definitive identifier for a specific DICOM object anywhere in
   * the world. It enables:
   *
   * <ul>
   *   <li>Unambiguous identification across different systems
   *   <li>Cross-reference resolution in distributed environments
   *   <li>Data integrity verification and duplicate detection
   *   <li>Audit trail creation and compliance reporting
   * </ul>
   *
   * @return the SOP Instance UID uniquely identifying the referenced object
   */
  public String getReferencedSOPInstanceUID() {
    return dcmItems.getString(Tag.ReferencedSOPInstanceUID);
  }

  public void setReferencedSOPInstanceUID(String instanceUID) {
    dcmItems.setString(Tag.ReferencedSOPInstanceUID, VR.UI, instanceUID);
  }

  /**
   * Gets the SOP Class UID that defines the type and capabilities of the referenced instance.
   *
   * <p>The SOP Class UID determines:
   *
   * <ul>
   *   <li><strong>Object Type:</strong> What kind of data the instance contains
   *   <li><strong>Supported Operations:</strong> Which DICOM services can be performed
   *   <li><strong>Attribute Requirements:</strong> Which data elements must be present
   *   <li><strong>Validation Rules:</strong> How to verify object conformance
   * </ul>
   *
   * <p><strong>Application Benefits:</strong>
   *
   * <ul>
   *   <li>Enables type-specific processing and display
   *   <li>Supports capability negotiation between systems
   *   <li>Facilitates workflow routing and automation
   *   <li>Ensures appropriate handling of different object types
   * </ul>
   *
   * @return the SOP Class UID identifying the type of the referenced object
   */
  public String getReferencedSOPClassUID() {
    return dcmItems.getString(Tag.ReferencedSOPClassUID);
  }

  public void setReferencedSOPClassUID(String classUID) {
    dcmItems.setString(Tag.ReferencedSOPClassUID, VR.UI, classUID);
  }

  /**
   * Gets the instance number of the referenced SOP instance within its series.
   *
   * <p>The instance number provides a human-readable identifier for the instance within its
   * containing series. While not globally unique, it offers:
   *
   * <ul>
   *   <li>Intuitive ordering and navigation within series
   *   <li>User-friendly identification in interfaces
   *   <li>Series-relative positioning information
   *   <li>Support for sequential processing workflows
   * </ul>
   *
   * <p><strong>Note:</strong> Instance numbers are not guaranteed to be sequential or start from 1,
   * as they may reflect acquisition order, reconstruction parameters, or other organizational
   * schemes.
   *
   * @return the instance number within the series, or null if not specified
   */
  public Integer getInstanceNumber() {
    return DicomUtils.getIntegerFromDicomElement(dcmItems, Tag.InstanceNumber, null);
  }

  public void setInstanceNumber(Integer instanceNumber) {
    if (instanceNumber != null) {
      dcmItems.setInt(Tag.InstanceNumber, VR.IS, instanceNumber);
    }
  }
}
