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
 * DICOM SOP Instance Reference with Message Authentication Code (MAC) and Digital Signatures for
 * cryptographically protected object references.
 *
 * <p>This class extends the basic SOP instance referencing capability with comprehensive
 * cryptographic integrity protection, providing both Message Authentication Codes (MACs) and
 * Digital Signatures. This enables:
 *
 * <ul>
 *   <li>Cryptographic verification of reference integrity
 *   <li>Detection of unauthorized modifications to referenced objects
 *   <li>Non-repudiation through digital signatures
 *   <li>Compliance with healthcare security and regulatory requirements
 * </ul>
 *
 * <p><strong>Security Architecture:</strong>
 *
 * <pre>
 * SOP Instance Reference
 *   ├── Basic Reference (UID, Class, Frames)
 *   ├── Purpose Code (Why referenced)
 *   ├── MAC Parameters (Integrity protection)
 *   └── Digital Signatures (Authentication & non-repudiation)
 * </pre>
 *
 * <p><strong>Cryptographic Protection Levels:</strong>
 *
 * <ul>
 *   <li><strong>MAC Protection:</strong> Detects tampering and ensures data integrity
 *   <li><strong>Digital Signatures:</strong> Provides authentication and non-repudiation
 *   <li><strong>Combined Protection:</strong> Offers both integrity and authenticity guarantees
 * </ul>
 *
 * <p><strong>Healthcare Security Use Cases:</strong>
 *
 * <ul>
 *   <li><strong>Legal Evidence:</strong> Forensically sound medical imaging evidence
 *   <li><strong>Regulatory Compliance:</strong> FDA, HIPAA, and international standards
 *   <li><strong>Clinical Trials:</strong> Tamper-evident research data integrity
 *   <li><strong>Peer Review:</strong> Authenticated case presentations
 *   <li><strong>Audit Trails:</strong> Comprehensive security event logging
 *   <li><strong>Long-term Archival:</strong> Preservation of authenticity over time
 * </ul>
 *
 * <p><strong>Security Workflow Integration:</strong>
 *
 * <ul>
 *   <li>Key Object Documents with authenticated selections
 *   <li>Structured Reports with verified evidence references
 *   <li>Teaching Files with protected educational content
 *   <li>Quality Assurance with signed review decisions
 * </ul>
 *
 * @see SOPInstanceReferenceAndPurpose
 * @see MACParameters
 * @see DigitalSignatures
 * @see Module
 */
public class SOPInstanceReferenceAndMAC extends SOPInstanceReferenceAndPurpose {

  /**
   * Creates a SOPInstanceReferenceAndMAC from existing DICOM attributes.
   *
   * @param dcmItems the DICOM attributes containing SOP instance reference and security information
   */
  public SOPInstanceReferenceAndMAC(Attributes dcmItems) {
    super(dcmItems);
  }

  /** Creates an empty SOPInstanceReferenceAndMAC with default attributes. */
  public SOPInstanceReferenceAndMAC() {
    super(new Attributes());
  }

  /**
   * Converts a DICOM sequence to a collection of SOPInstanceReferenceAndMAC objects.
   *
   * @param seq the DICOM sequence to convert (may be null or empty)
   * @return an immutable collection of SOPInstanceReferenceAndMAC objects, empty if input is null
   *     or empty
   */
  public static Collection<SOPInstanceReferenceAndMAC> toSOPInstanceReferenceAndMacMacros(
      Sequence seq) {

    if (seq == null || seq.isEmpty()) {
      return List.of();
    }

    return seq.stream().map(SOPInstanceReferenceAndMAC::new).toList();
  }

  /**
   * Gets the collection of MAC parameters used for cryptographic integrity protection.
   *
   * <p>Message Authentication Codes provide tamper detection by computing cryptographic hash values
   * over selected portions of the referenced DICOM object. Each MAC parameter set defines:
   *
   * <ul>
   *   <li><strong>Algorithm:</strong> Cryptographic hash function (SHA256, RIPEMD160, etc.)
   *   <li><strong>Scope:</strong> Which data elements are included in MAC calculation
   *   <li><strong>Transfer Syntax:</strong> Encoding used during MAC computation
   *   <li><strong>Identifier:</strong> MAC ID for linking parameters to MAC values
   * </ul>
   *
   * <p><strong>MAC Security Benefits:</strong>
   *
   * <ul>
   *   <li>Detects unauthorized modifications to protected data elements
   *   <li>Enables selective protection of critical medical information
   *   <li>Supports automated integrity verification workflows
   *   <li>Provides evidence of tampering for audit and compliance
   * </ul>
   *
   * <p><strong>Multiple MAC Strategy:</strong> Different MAC parameters may protect different
   * aspects of the same reference:
   *
   * <ul>
   *   <li>One MAC for pixel data integrity
   *   <li>Another MAC for critical metadata elements
   *   <li>Separate MACs for different algorithm strengths
   * </ul>
   *
   * @return collection of MAC parameters defining integrity protection, may be empty
   */
  public Collection<MACParameters> getMACParameters() {
    return MACParameters.toMACParametersMacros(dcmItems.getSequence(Tag.MACParametersSequence));
  }

  public void setMACParameters(Collection<MACParameters> macParameters) {
    updateSequence(Tag.MACParametersSequence, macParameters);
  }

  /**
   * Gets the collection of digital signatures providing authentication and non-repudiation.
   *
   * <p>Digital signatures use public-key cryptography to provide strong authentication of the
   * signer's identity and guarantee non-repudiation. Each signature includes:
   *
   * <ul>
   *   <li><strong>Certificate Chain:</strong> X.509 certificates establishing signer identity
   *   <li><strong>Signature Value:</strong> Cryptographic signature over protected content
   *   <li><strong>Signing Time:</strong> Timestamp of signature creation
   *   <li><strong>Signature Purpose:</strong> Coded reason for signing
   * </ul>
   *
   * @return collection of digital signatures providing authentication, may be empty
   */
  public Collection<DigitalSignatures> getDigitalSignatures() {
    return DigitalSignatures.toDigitalSignaturesMacros(
        dcmItems.getSequence(Tag.DigitalSignaturesSequence));
  }

  public void setDigitalSignatures(Collection<DigitalSignatures> digitalSignatures) {
    updateSequence(Tag.DigitalSignaturesSequence, digitalSignatures);
  }
}
