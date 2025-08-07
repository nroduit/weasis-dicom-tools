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
 * DICOM Series and Instance Reference Macro implementation for managing references to entire DICOM
 * series and their contained SOP instances with retrieval information.
 *
 * <p>This class serves as an intermediate level in the DICOM hierarchical reference model, bridging
 * between study-level references and individual instance references. It provides:
 *
 * <ul>
 *   <li>Series-level identification and grouping of related instances
 *   <li>Retrieval location information for accessing referenced data
 *   <li>Storage media information for offline or archived content
 *   <li>Collections of individual SOP instance references within the series
 * </ul>
 *
 * <p><strong>DICOM Hierarchy Context:</strong>
 *
 * <pre>
 * Study (Patient Examination)
 *   └── Series (Acquisition/Reconstruction Group)
 *       └── SOP Instances (Individual Images/Objects)
 * </pre>
 *
 * <p><strong>Retrieval Scenarios:</strong>
 *
 * <ul>
 *   <li><strong>Network Retrieval:</strong> Using AE Title to identify DICOM nodes
 *   <li><strong>Media Retrieval:</strong> Using File Set IDs for CD/DVD/USB storage
 *   <li><strong>Archive Retrieval:</strong> Combining network and media identifiers
 *   <li><strong>Cloud Storage:</strong> Using extended retrieval mechanisms
 * </ul>
 *
 * <p><strong>Common Use Cases:</strong>
 *
 * <ul>
 *   <li>Key Object Documents referencing significant series
 *   <li>Structured Reports citing evidence from multiple series
 *   <li>Presentation States displaying series-level layouts
 *   <li>Hanging Protocols organizing multi-series displays
 *   <li>Teaching File collections organizing educational content
 * </ul>
 *
 * @see SOPInstanceReferenceAndMAC
 * @see HierarchicalSOPInstanceReference
 * @see Module
 */
public class SeriesAndInstanceReference extends Module {

  /**
   * Creates a SeriesAndInstanceReference from existing DICOM attributes.
   *
   * @param dcmItems the DICOM attributes containing series and instance reference information
   */
  public SeriesAndInstanceReference(Attributes dcmItems) {
    super(dcmItems);
  }

  /** Creates an empty SeriesAndInstanceReference with default attributes. */
  public SeriesAndInstanceReference() {
    super(new Attributes());
  }

  /**
   * Converts a DICOM sequence to a collection of SeriesAndInstanceReference objects.
   *
   * @param seq the DICOM sequence to convert (may be null or empty)
   * @return an immutable collection of SeriesAndInstanceReference objects, empty if input is null
   *     or empty
   */
  public static Collection<SeriesAndInstanceReference> toSeriesAndInstanceReferenceMacros(
      Sequence seq) {
    if (seq == null || seq.isEmpty()) {
      return List.of();
    }

    return seq.stream().map(SeriesAndInstanceReference::new).toList();
  }

  /**
   * Gets the unique identifier of the referenced DICOM series.
   *
   * <p>The Series Instance UID groups related images or objects that were acquired or reconstructed
   * as part of the same logical acquisition. Examples include:
   *
   * <ul>
   *   <li>All axial slices from a CT scan
   *   <li>All images in a multi-frame cardiac sequence
   *   <li>All reconstructions from the same raw data
   *   <li>All images with the same acquisition parameters
   * </ul>
   *
   * @return the Series Instance UID uniquely identifying this series
   */
  public String getSeriesInstanceUID() {
    return dcmItems.getString(Tag.SeriesInstanceUID);
  }

  public void setSeriesInstanceUID(String seriesUID) {
    dcmItems.setString(Tag.SeriesInstanceUID, VR.UI, seriesUID);
  }

  /**
   * Gets the Application Entity Title of the DICOM node where this series can be retrieved.
   *
   * <p>The AE Title identifies a specific DICOM application on the network that can provide the
   * referenced series. This enables automated retrieval workflows where applications can:
   *
   * <ul>
   *   <li>Query the identified node for series availability
   *   <li>Retrieve the series using C-MOVE or C-GET operations
   *   <li>Establish associations for real-time access
   *   <li>Implement load balancing across multiple sources
   * </ul>
   *
   * @return the AE Title for retrieving this series, or null if not specified
   */
  public String getRetrieveAETitle() {
    return dcmItems.getString(Tag.RetrieveAETitle);
  }

  public void setRetrieveAETitle(String aeTitle) {
    dcmItems.setString(Tag.RetrieveAETitle, VR.AE, aeTitle);
  }

  /**
   * Gets the identifier of the storage media file set containing this series.
   *
   * <p>This identifies a specific file set on physical media (CD, DVD, USB, etc.) where the series
   * is stored. The File Set ID provides a human-readable identifier for the media volume, enabling:
   *
   * <ul>
   *   <li>Physical media management and cataloging
   *   <li>Archive retrieval and restoration processes
   *   <li>Offline storage workflow integration
   *   <li>Disaster recovery and backup validation
   * </ul>
   *
   * @return the storage media file set identifier, or null if not on physical media
   */
  public String getStorageMediaFileSetID() {
    return dcmItems.getString(Tag.StorageMediaFileSetID);
  }

  public void setStorageMediaFileSetID(String fileSetId) {
    dcmItems.setString(Tag.StorageMediaFileSetID, VR.SH, fileSetId);
  }

  /**
   * Gets the unique identifier of the storage media file set containing this series.
   *
   * <p>This UID provides a globally unique identifier for the file set, complementing the
   * human-readable File Set ID. It enables precise identification of storage media across different
   * systems and time periods.
   *
   * @return the storage media file set UID, or null if not specified
   */
  public String getStorageMediaFileSetUID() {
    return dcmItems.getString(Tag.StorageMediaFileSetUID);
  }

  public void setStorageMediaFileSetUID(String fileSetUID) {
    dcmItems.setString(Tag.StorageMediaFileSetUID, VR.UI, fileSetUID);
  }

  /**
   * Gets the collection of individual SOP instance references within this series.
   *
   * <p>Each SOP instance reference identifies a specific DICOM object (image, waveform, document,
   * etc.) within the series. The references include:
   *
   * <ul>
   *   <li>SOP Instance UID for unique identification
   *   <li>SOP Class UID indicating the object type
   *   <li>Optional frame numbers for multi-frame objects
   *   <li>MAC parameters for integrity verification
   * </ul>
   *
   * <p><strong>Instance Selection Strategies:</strong>
   *
   * <ul>
   *   <li><strong>Complete Series:</strong> All instances in the series
   *   <li><strong>Key Images:</strong> Clinically significant instances only
   *   <li><strong>Sampling:</strong> Representative instances for overview
   *   <li><strong>Quality Control:</strong> Instances requiring review
   * </ul>
   *
   * @return collection of SOP instance references within this series, may be empty
   */
  public Collection<SOPInstanceReferenceAndMAC> getReferencedSOPInstances() {
    return SOPInstanceReferenceAndMAC.toSOPInstanceReferenceAndMacMacros(
        dcmItems.getSequence(Tag.ReferencedSOPSequence));
  }

  public void setReferencedSOPInstances(
      Collection<SOPInstanceReferenceAndMAC> referencedInstances) {
    updateSequence(Tag.ReferencedSOPSequence, referencedInstances);
  }
}
