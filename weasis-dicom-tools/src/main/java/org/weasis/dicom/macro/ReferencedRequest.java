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
 * DICOM Referenced Request Sequence Macro implementation for managing references to the original
 * procedure requests that initiated imaging studies.
 *
 * <p>This class provides traceability from derived DICOM objects (such as Key Object Documents,
 * Structured Reports, or Presentation States) back to the original clinical requests that generated
 * the imaging studies. This traceability is essential for:
 *
 * <ul>
 *   <li>Clinical workflow management and audit trails
 *   <li>Billing and reimbursement processes
 *   <li>Quality assurance and peer review
 *   <li>Legal and regulatory compliance
 *   <li>Clinical decision support and follow-up tracking
 * </ul>
 *
 * <p><strong>Healthcare Information System Integration:</strong>
 *
 * <ul>
 *   <li><strong>RIS Integration:</strong> Links to Radiology Information System orders
 *   <li><strong>HIS Integration:</strong> Connects to Hospital Information System requests
 *   <li><strong>EMR Integration:</strong> Provides context for Electronic Medical Records
 *   <li><strong>Workflow Orchestration:</strong> Enables automated process management
 * </ul>
 *
 * <p><strong>Request Identification Hierarchy:</strong>
 *
 * <ol>
 *   <li><strong>Accession Number:</strong> Unique identifier for the imaging examination
 *   <li><strong>Placer Order Number:</strong> Identifier from the ordering system (e.g., EMR)
 *   <li><strong>Filler Order Number:</strong> Identifier from the fulfilling system (e.g., RIS)
 *   <li><strong>Requested Procedure ID:</strong> Specific procedure within the order
 * </ol>
 *
 * @see SOPInstanceReference
 * @see Code
 * @see Module
 */
public class ReferencedRequest extends Module {
  /**
   * Creates a ReferencedRequest from existing DICOM attributes.
   *
   * @param dcmItems the DICOM attributes containing referenced request information
   */
  public ReferencedRequest(Attributes dcmItems) {
    super(dcmItems);
  }

  /** Creates an empty ReferencedRequest with default attributes. */
  public ReferencedRequest() {
    super(new Attributes());
  }

  /**
   * Converts a DICOM sequence to a collection of ReferencedRequest objects.
   *
   * @param seq the DICOM sequence to convert (may be null or empty)
   * @return an immutable collection of ReferencedRequest objects, empty if input is null or empty
   */
  public static Collection<ReferencedRequest> toReferencedRequestMacros(Sequence seq) {
    if (seq == null || seq.isEmpty()) {
      return List.of();
    }

    return seq.stream().map(ReferencedRequest::new).toList();
  }

  /**
   * Gets the unique identifier of the study that was created in response to this request.
   *
   * <p>This provides the direct link between the original clinical request and the resulting
   * imaging study, enabling systems to trace from request to acquired images.
   *
   * @return the Study Instance UID of the resulting study
   */
  public String getStudyInstanceUID() {
    return dcmItems.getString(Tag.StudyInstanceUID);
  }

  public void setStudyInstanceUID(String studyUID) {
    dcmItems.setString(Tag.StudyInstanceUID, VR.UI, studyUID);
  }

  /**
   * Gets the reference to the Study SOP Instance that documents this imaging request.
   *
   * <p>Some systems create specific DICOM Study objects that contain metadata about the imaging
   * request itself. This reference points to such documentation objects.
   *
   * @return reference to the study SOP instance, or null if not present
   */
  public SOPInstanceReference getReferencedStudySOPInstance() {
    Attributes item = dcmItems.getNestedDataset(Tag.ReferencedStudySequence);
    return item != null ? new SOPInstanceReference(item) : null;
  }

  public void setReferencedStudySOPInstance(SOPInstanceReference referencedStudy) {
    updateSequence(Tag.ReferencedStudySequence, referencedStudy);
  }

  /**
   * Gets the accession number that uniquely identifies the imaging examination request.
   *
   * <p>The accession number is typically assigned by the Radiology Information System (RIS) and
   * serves as the primary identifier for tracking an imaging examination through the entire
   * workflow from order to report. It's used for:
   *
   * <ul>
   *   <li>Patient scheduling and registration
   *   <li>Image acquisition workflow management
   *   <li>Report generation and distribution
   *   <li>Billing and reimbursement processes
   * </ul>
   *
   * @return the accession number for this imaging request
   */
  public String getAccessionNumber() {
    return dcmItems.getString(Tag.AccessionNumber);
  }

  public void setAccessionNumber(String accessionNumber) {
    dcmItems.setString(Tag.AccessionNumber, VR.SH, accessionNumber);
  }

  /**
   * Gets the placer order number from the system that placed the imaging request.
   *
   * <p>This identifier comes from the ordering system (typically an EMR or HIS) and represents the
   * order as known to the system that initiated the request. It enables bi-directional
   * communication between ordering and fulfilling systems.
   *
   * @return the placer order number for the imaging service request
   */
  public String getPlacerOrderNumberImagingServiceRequest() {
    return dcmItems.getString(Tag.PlacerOrderNumberImagingServiceRequest);
  }

  public void setPlacerOrderNumberImagingServiceRequest(String placerOrderNumber) {
    dcmItems.setString(Tag.PlacerOrderNumberImagingServiceRequest, VR.LO, placerOrderNumber);
  }

  /**
   * Gets the filler order number from the system that fulfills the imaging request.
   *
   * <p>This identifier is assigned by the fulfilling system (typically a RIS) and represents the
   * order as managed by the system responsible for scheduling and performing the imaging procedure.
   *
   * @return the filler order number for the imaging service request
   */
  public String getFillerOrderNumberImagingServiceRequest() {
    return dcmItems.getString(Tag.FillerOrderNumberImagingServiceRequest);
  }

  public void setFillerOrderNumberImagingServiceRequest(String fillerOrderNumber) {
    dcmItems.setString(Tag.FillerOrderNumberImagingServiceRequest, VR.LO, fillerOrderNumber);
  }

  /**
   * Gets the identifier for the specific requested procedure within the overall request.
   *
   * <p>A single imaging request may include multiple procedures (e.g., chest X-ray with and without
   * contrast). This ID identifies the specific procedure component within the broader request.
   *
   * @return the requested procedure identifier
   */
  public String getRequestedProcedureID() {
    return dcmItems.getString(Tag.RequestedProcedureID);
  }

  public void setRequestedProcedureID(String procedureId) {
    dcmItems.setString(Tag.RequestedProcedureID, VR.SH, procedureId);
  }

  /**
   * Gets the human-readable description of the requested procedure.
   *
   * <p>This free-text description provides clinical context about what imaging procedure was
   * requested, often including anatomical regions, contrast usage, or special techniques.
   *
   * @return the requested procedure description
   */
  public String getRequestedProcedureDescription() {
    return dcmItems.getString(Tag.RequestedProcedureDescription);
  }

  public void setRequestedProcedureDescription(String description) {
    dcmItems.setString(Tag.RequestedProcedureDescription, VR.LO, description);
  }

  /**
   * Gets the coded representation of the requested procedure.
   *
   * <p>This provides a standardized, machine-readable identification of the procedure using coding
   * schemes such as SNOMED CT, LOINC, or local procedure codes. This enables automated processing,
   * billing, and clinical decision support.
   *
   * @return the coded procedure identification, or null if not specified
   */
  public Code getRequestedProcedureCode() {
    return Code.getNestedCode(dcmItems, Tag.RequestedProcedureCodeSequence);
  }

  public void setRequestedProcedureCode(Code procedureCode) {
    updateSequence(Tag.RequestedProcedureCodeSequence, procedureCode);
  }

  /**
   * Gets the clinical reason for requesting the imaging procedure.
   *
   * <p>This free-text field captures the clinical indication, symptoms, or medical condition that
   * justified the imaging request. It provides essential clinical context for image interpretation
   * and quality assurance.
   *
   * @return the reason for the requested procedure
   */
  public String getReasonForTheRequestedProcedure() {
    return dcmItems.getString(Tag.ReasonForTheRequestedProcedure);
  }

  public void setReasonForTheRequestedProcedure(String reason) {
    dcmItems.setString(Tag.ReasonForTheRequestedProcedure, VR.LO, reason);
  }

  /**
   * Gets the coded representation of the reason for the requested procedure.
   *
   * <p>This provides a standardized way to capture clinical indications using established medical
   * coding systems. Coded reasons enable:
   *
   * <ul>
   *   <li>Automated appropriateness checking
   *   <li>Clinical decision support
   *   <li>Quality metrics and outcomes tracking
   *   <li>Reimbursement validation
   * </ul>
   *
   * @return the coded reason for the procedure request, or null if not specified
   */
  public Code getReasonForRequestedProcedureCode() {
    return Code.getNestedCode(dcmItems, Tag.ReasonForRequestedProcedureCodeSequence);
  }

  public void setReasonForRequestedProcedureCode(Code reasonCode) {
    updateSequence(Tag.ReasonForRequestedProcedureCodeSequence, reasonCode);
  }
}
