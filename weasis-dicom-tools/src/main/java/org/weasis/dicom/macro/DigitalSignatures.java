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

import java.io.IOException;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Sequence;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.VR;

/**
 * DICOM Digital Signatures Sequence Macro implementation for managing digital signatures and
 * certificate information in DICOM objects.
 *
 * <p>This class provides access to digital signature attributes as defined in DICOM Part 3,
 * including:
 *
 * <ul>
 *   <li>Message Authentication Code (MAC) identification
 *   <li>Digital signature metadata (UID, timestamp)
 *   <li>Certificate information and validation data
 *   <li>Certified timestamps for non-repudiation
 *   <li>Purpose codes for signature intent
 * </ul>
 *
 * <p>Digital signatures in DICOM are used to ensure data integrity, authenticity, and
 * non-repudiation of medical images and associated information.
 *
 * @see Code
 * @see Module
 */
public class DigitalSignatures extends Module {

  /**
   * Creates a DigitalSignatures instance from existing DICOM attributes.
   *
   * @param dcmItems the DICOM attributes containing digital signature information
   */
  public DigitalSignatures(Attributes dcmItems) {
    super(dcmItems);
  }

  /** Creates an empty DigitalSignatures instance with default attributes. */
  public DigitalSignatures() {
    super(new Attributes());
  }

  /**
   * Converts a DICOM sequence to a collection of DigitalSignatures objects.
   *
   * @param seq the DICOM sequence to convert (may be null or empty)
   * @return an immutable collection of DigitalSignatures objects, empty if input is null or empty
   */
  public static Collection<DigitalSignatures> toDigitalSignaturesMacros(Sequence seq) {
    if (seq == null || seq.isEmpty()) {
      return List.of();
    }

    return seq.stream().map(DigitalSignatures::new).toList();
  }

  /**
   * Gets the MAC ID Number that identifies the Message Authentication Code.
   *
   * @return the MAC ID number, or -1 if not present
   */
  public int getMACIDNumber() {
    return dcmItems.getInt(Tag.MACIDNumber, -1);
  }

  public void setMACIDNumber(int value) {
    dcmItems.setInt(Tag.MACIDNumber, VR.US, value);
  }

  /**
   * Gets the unique identifier for this digital signature.
   *
   * @return the digital signature UID
   */
  public String getDigitalSignatureUID() {
    return dcmItems.getString(Tag.DigitalSignatureUID);
  }

  public void setDigitalSignatureUID(String uid) {
    dcmItems.setString(Tag.DigitalSignatureUID, VR.UI, uid);
  }

  /**
   * Gets the date and time when the digital signature was created.
   *
   * @return the signature creation timestamp
   */
  public Date getDigitalSignatureDateTime() {
    return dcmItems.getDate(Tag.DigitalSignatureDateTime);
  }

  public void setDigitalSignatureDateTime(Date dateTime) {
    dcmItems.setDate(Tag.DigitalSignatureDateTime, VR.DT, dateTime);
  }

  /**
   * Gets the type of certificate used for signing.
   *
   * @return the certificate type (e.g., "X509_1993_SIG")
   */
  public String getCertificateType() {
    return dcmItems.getString(Tag.CertificateType);
  }

  public void setCertificateType(String type) {
    dcmItems.setString(Tag.CertificateType, VR.CS, type);
  }

  /**
   * Gets the certificate of the signer in binary format.
   *
   * @return the certificate data as byte array
   * @throws IOException if an error occurs reading the certificate data
   */
  public byte[] getCertificateOfSigner() throws IOException {
    return dcmItems.getBytes(Tag.CertificateOfSigner);
  }

  public void setCertificateOfSigner(byte[] certificate) {
    dcmItems.setBytes(Tag.CertificateOfSigner, VR.OB, certificate);
  }

  /**
   * Gets the digital signature data in binary format.
   *
   * @return the signature data as byte array
   * @throws IOException if an error occurs reading the signature data
   */
  public byte[] getSignature() throws IOException {
    return dcmItems.getBytes(Tag.Signature);
  }

  public void setSignature(byte[] signature) {
    dcmItems.setBytes(Tag.Signature, VR.OB, signature);
  }

  /**
   * Gets the type of certified timestamp used.
   *
   * @return the certified timestamp type
   */
  public String getCertifiedTimestampType() {
    return dcmItems.getString(Tag.CertifiedTimestampType);
  }

  public void setCertifiedTimestampType(String type) {
    dcmItems.setString(Tag.CertifiedTimestampType, VR.CS, type);
  }

  /**
   * Gets the certified timestamp data in binary format.
   *
   * @return the timestamp data as byte array
   * @throws IOException if an error occurs reading the timestamp data
   */
  public byte[] getCertifiedTimestamp() throws IOException {
    return dcmItems.getBytes(Tag.CertifiedTimestamp);
  }

  public void setCertifiedTimestamp(byte[] timestamp) {
    dcmItems.setBytes(Tag.CertifiedTimestamp, VR.OB, timestamp);
  }

  /**
   * Gets the purpose code that describes the intent of this digital signature.
   *
   * @return the digital signature purpose code, or null if not present
   */
  public Code getDigitalSignaturePurposeCode() {
    return Code.getNestedCode(dcmItems, Tag.DigitalSignaturePurposeCodeSequence);
  }

  public void setDigitalSignaturePurposeCode(Code code) {
    updateSequence(Tag.DigitalSignaturePurposeCodeSequence, code);
  }
}
