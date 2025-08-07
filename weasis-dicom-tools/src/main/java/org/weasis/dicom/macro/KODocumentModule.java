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
import java.util.Date;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.VR;

/**
 * DICOM Key Object Document Module implementation for managing Key Object Selection Documents.
 *
 * <p>Key Object Selection Documents (KOD) are specialized DICOM objects that identify and reference
 * significant instances from imaging studies. They serve as "bookmarks" or "shortcuts" to important
 * images, allowing users and applications to:
 *
 * <ul>
 *   <li>Mark clinically significant images for review
 *   <li>Create teaching file collections
 *   <li>Identify images for quality assurance
 *   <li>Flag images requiring follow-up or additional analysis
 *   <li>Create structured reports referencing specific evidence
 * </ul>
 *
 * <p>This module manages the core attributes of a Key Object Document including:
 *
 * <ul>
 *   <li>Document identification and versioning
 *   <li>Content creation timestamp
 *   <li>References to the original procedure requests
 *   <li>Evidence sequences pointing to significant instances
 *   <li>Identical document references for version tracking
 * </ul>
 *
 * <p><strong>Key Object Selection Workflow:</strong>
 *
 * <ol>
 *   <li>User reviews imaging study and identifies significant instances
 *   <li>Key Object Document is created referencing those instances
 *   <li>Document can be stored, transmitted, and used for navigation
 *   <li>Applications can use KOD to quickly access marked instances
 * </ol>
 *
 * @see HierarchicalSOPInstanceReference
 * @see ReferencedRequest
 * @see Module
 */
public class KODocumentModule extends Module {

  /**
   * Creates an empty KODocumentModule with default attributes.
   *
   * <p>This constructor initializes a new Key Object Document module with no attributes set. It can
   * be used to create a new document that will be populated with Key Object selections and other
   * relevant information later.
   */
  public KODocumentModule() {
    super(new Attributes());
  }

  /**
   * Creates a KODocumentModule from existing DICOM attributes.
   *
   * @param dcmItems the DICOM attributes containing Key Object Document information
   */
  public KODocumentModule(Attributes dcmItems) {
    super(dcmItems);
  }

  /**
   * Gets the instance number that uniquely identifies this Key Object Document within its series.
   *
   * @return the instance number as a string representation
   */
  public String getInstanceNumber() {
    return dcmItems.getString(Tag.InstanceNumber);
  }

  public void setInstanceNumber(String instanceNumber) {
    dcmItems.setString(Tag.InstanceNumber, VR.IS, instanceNumber);
  }

  /**
   * Gets the date and time when this Key Object Document content was created or last modified.
   *
   * <p>This timestamp indicates when the selection of key objects was made, not when the referenced
   * instances were originally acquired.
   *
   * @return the content creation/modification timestamp
   */
  public Date getContentDateTime() {
    return dcmItems.getDate(Tag.ContentDateAndTime);
  }

  public void setContentDateTime(Date dateTime) {
    dcmItems.setDate(Tag.ContentDateAndTime, dateTime);
  }

  /**
   * Gets the collection of procedure requests that originated the studies containing the referenced
   * key objects.
   *
   * <p>This provides traceability back to the original clinical context that generated the imaging
   * studies from which key objects were selected.
   *
   * @return collection of referenced requests, empty if no requests are referenced
   */
  public Collection<ReferencedRequest> getReferencedRequests() {
    return ReferencedRequest.toReferencedRequestMacros(
        dcmItems.getSequence(Tag.ReferencedRequestSequence));
  }

  public void setReferencedRequest(Collection<ReferencedRequest> referencedRequests) {
    updateSequence(Tag.ReferencedRequestSequence, referencedRequests);
  }

  /**
   * Gets the hierarchical references to all instances that constitute the evidence for the current
   * requested procedure.
   *
   * <p>This sequence contains the actual "key objects" - the DICOM instances that have been
   * identified as significant or noteworthy. The references are organized hierarchically by study
   * and series for efficient navigation.
   *
   * <p><strong>Common Evidence Types:</strong>
   *
   * <ul>
   *   <li>Images showing pathological findings
   *   <li>Key frames from dynamic or functional studies
   *   <li>Representative images for teaching purposes
   *   <li>Images requiring quality review or follow-up
   * </ul>
   *
   * @return collection of hierarchical references to evidence instances
   */
  public Collection<HierarchicalSOPInstanceReference> getCurrentRequestedProcedureEvidences() {
    return HierarchicalSOPInstanceReference.toHierarchicalSOPInstanceReferenceMacros(
        dcmItems.getSequence(Tag.CurrentRequestedProcedureEvidenceSequence));
  }

  public void setCurrentRequestedProcedureEvidences(
      Collection<HierarchicalSOPInstanceReference> evidenceReferences) {
    updateSequence(Tag.CurrentRequestedProcedureEvidenceSequence, evidenceReferences);
  }

  /**
   * Gets references to other Key Object Documents that are identical in content to this document.
   *
   * <p>This mechanism supports version control and duplicate detection by allowing Key Object
   * Documents to reference other documents with identical key object selections. This is useful
   * for:
   *
   * <ul>
   *   <li>Tracking document versions and updates
   *   <li>Managing duplicate selections across different systems
   *   <li>Maintaining consistency in collaborative environments
   *   <li>Supporting document synchronization workflows
   * </ul>
   *
   * @return collection of references to identical documents, empty if none exist
   */
  public Collection<HierarchicalSOPInstanceReference> getIdenticalDocuments() {
    return HierarchicalSOPInstanceReference.toHierarchicalSOPInstanceReferenceMacros(
        dcmItems.getSequence(Tag.IdenticalDocumentsSequence));
  }

  public void setIdenticalDocuments(Collection<HierarchicalSOPInstanceReference> identicalRefs) {
    updateSequence(Tag.IdenticalDocumentsSequence, identicalRefs);
  }
}
