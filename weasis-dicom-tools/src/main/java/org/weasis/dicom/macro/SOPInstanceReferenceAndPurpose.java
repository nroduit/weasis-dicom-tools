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

/**
 * DICOM SOP Instance Reference with Purpose Code for semantically enriched object references.
 *
 * <p>This class extends the basic SOP instance referencing capability with coded purpose
 * information that explains why the instance is being referenced. This semantic enrichment enables:
 *
 * <ul>
 *   <li>Automated processing based on reference intent
 *   <li>Intelligent workflow routing and decision making
 *   <li>Enhanced user interfaces with contextual information
 *   <li>Compliance with clinical documentation requirements
 * </ul>
 *
 * <p><strong>Reference Hierarchy with Purpose:</strong>
 *
 * <pre>
 * SOP Instance Reference
 *   ├── Basic Reference (UID, Class, Frames)
 *   └── Purpose Code (Why referenced)
 *       ├── Code Value (Machine-readable identifier)
 *       ├── Coding Scheme (Context/vocabulary)
 *       └── Code Meaning (Human-readable description)
 * </pre>
 *
 * <p><strong>Common Purpose Categories:</strong>
 *
 * <ul>
 *   <li><strong>Clinical Evidence:</strong> Supporting diagnosis or clinical decision
 *   <li><strong>Quality Control:</strong> QA review, calibration, or validation
 *   <li><strong>Educational:</strong> Teaching, training, or demonstration
 *   <li><strong>Research:</strong> Clinical trial or scientific study
 *   <li><strong>Administrative:</strong> Billing, audit, or compliance documentation
 *   <li><strong>Technical:</strong> Processing, reconstruction, or analysis
 * </ul>
 *
 * <p><strong>Purpose-Driven Workflows:</strong>
 *
 * <ul>
 *   <li><strong>Key Object Documents:</strong> Marking images as "for diagnosis", "for quality
 *       review"
 *   <li><strong>Structured Reports:</strong> Citing evidence as "abnormal finding", "comparison
 *       study"
 *   <li><strong>Teaching Files:</strong> Categorizing cases as "classic example", "rare pathology"
 *   <li><strong>Clinical Trials:</strong> Tagging images as "baseline", "response assessment"
 * </ul>
 *
 * <p><strong>Integration Benefits:</strong>
 *
 * <ul>
 *   <li>PACS systems can filter and route based on purpose
 *   <li>Viewers can provide purpose-specific display options
 *   <li>Workflow engines can trigger appropriate actions
 *   <li>Analytics systems can categorize and analyze usage patterns
 * </ul>
 *
 * <p><strong>Standard Purpose Vocabularies:</strong>
 *
 * <ul>
 *   <li><strong>DICOM Context Groups:</strong> Standardized purpose codes
 *   <li><strong>SNOMED CT:</strong> Clinical terminology for medical purposes
 *   <li><strong>LOINC:</strong> Laboratory and clinical observation codes
 *   <li><strong>Local Codes:</strong> Institution-specific purpose classifications
 * </ul>
 *
 * @see SOPInstanceReference
 * @see SOPInstanceReferenceAndMAC
 * @see Code
 * @see Module
 */
public class SOPInstanceReferenceAndPurpose extends SOPInstanceReference {

  /**
   * Creates a SOPInstanceReferenceAndPurpose from existing DICOM attributes.
   *
   * @param dcmItems the DICOM attributes containing SOP instance reference and purpose information
   */
  public SOPInstanceReferenceAndPurpose(Attributes dcmItems) {
    super(dcmItems);
  }

  /** Creates an empty SOPInstanceReferenceAndPurpose with default attributes. */
  public SOPInstanceReferenceAndPurpose() {
    this(new Attributes());
  }

  /**
   * Converts a DICOM sequence to a collection of SOPInstanceReferenceAndPurpose objects.
   *
   * @param seq the DICOM sequence to convert (may be null or empty)
   * @return an immutable collection of SOPInstanceReferenceAndPurpose objects, empty if input is
   *     null or empty
   */
  public static Collection<SOPInstanceReferenceAndPurpose> toSOPInstanceReferenceAndPurposesMacros(
      Sequence seq) {

    if (seq == null || seq.isEmpty()) {
      return List.of();
    }

    return seq.stream().map(SOPInstanceReferenceAndPurpose::new).toList();
  }

  /**
   * Gets the coded purpose for referencing this SOP instance.
   *
   * <p>The Purpose of Reference Code provides standardized, machine-readable semantics that explain
   * why this particular instance is being referenced. This enables:
   *
   * <ul>
   *   <li><strong>Automated Processing:</strong> Systems can handle references differently based on
   *       purpose
   *   <li><strong>Workflow Optimization:</strong> Route references to appropriate review queues
   *   <li><strong>User Interface Enhancement:</strong> Display purpose-specific icons and
   *       descriptions
   *   <li><strong>Analytics and Reporting:</strong> Categorize usage patterns and clinical
   *       activities
   * </ul>
   *
   * @return the coded purpose for this reference, or null if purpose is not specified
   */
  public Code getPurposeOfReferenceCode() {
    return Code.getNestedCode(dcmItems, Tag.PurposeOfReferenceCodeSequence);
  }

  public void setPurposeOfReferenceCode(Code purposeCode) {
    updateSequence(Tag.PurposeOfReferenceCodeSequence, purposeCode);
  }
}
