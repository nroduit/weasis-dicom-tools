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
 * DICOM MAC Parameters Sequence Macro implementation for managing Message Authentication Code
 * configuration and validation parameters.
 *
 * <p>Message Authentication Codes (MACs) provide cryptographic integrity protection for DICOM
 * objects by computing hash values over selected data elements. This class manages the parameters
 * that control MAC calculation and validation, including:
 *
 * <ul>
 *   <li>MAC identification and algorithm specification
 *   <li>Transfer syntax used during MAC calculation
 *   <li>Specification of which data elements are included in MAC computation
 *   <li>Configuration for MAC verification processes
 * </ul>
 *
 * <p><strong>MAC Security Model:</strong>
 *
 * <ul>
 *   <li><strong>Data Integrity:</strong> Detects unauthorized modifications to DICOM objects
 *   <li><strong>Selective Protection:</strong> Allows MAC computation over specific data elements
 *   <li><strong>Algorithm Flexibility:</strong> Supports various cryptographic hash algorithms
 *   <li><strong>Transfer Syntax Independence:</strong> MAC calculation can be tied to specific
 *       encoding
 * </ul>
 *
 * <p><strong>Common MAC Algorithms:</strong>
 *
 * <ul>
 *   <li>RIPEMD160 - 160-bit hash algorithm
 *   <li>SHA1 - Secure Hash Algorithm 1
 *   <li>MD5 - Message Digest Algorithm 5 (deprecated for security)
 *   <li>SHA256, SHA384, SHA512 - Advanced SHA variants
 * </ul>
 *
 * @see DigitalSignatures
 * @see Module
 */
public class MACParameters extends Module {

  /**
   * Creates a MACParameters instance from existing DICOM attributes.
   *
   * @param dcmItems the DICOM attributes containing MAC parameter information
   */
  public MACParameters(Attributes dcmItems) {
    super(dcmItems);
  }

  /** Creates an empty MACParameters instance with default attributes. */
  public MACParameters() {
    super(new Attributes());
  }

  /**
   * Converts a DICOM sequence to a collection of MACParameters objects.
   *
   * @param seq the DICOM sequence to convert (may be null or empty)
   * @return an immutable collection of MACParameters objects, empty if input is null or empty
   */
  public static Collection<MACParameters> toMACParametersMacros(Sequence seq) {
    if (seq == null || seq.isEmpty()) {
      return List.of();
    }

    return seq.stream().map(MACParameters::new).toList();
  }

  /**
   * Gets the MAC ID Number that uniquely identifies this MAC within the DICOM object.
   *
   * <p>This identifier links MAC parameters to their corresponding MAC values and allows multiple
   * MACs to coexist within the same DICOM object for different purposes or algorithm types.
   *
   * @return the MAC ID number, or -1 if not present
   */
  public int getMACIDNumber() {
    return dcmItems.getInt(Tag.MACIDNumber, -1);
  }

  public void setMACIDNumber(int macId) {
    dcmItems.setInt(Tag.MACIDNumber, VR.US, macId);
  }

  /**
   * Gets the Transfer Syntax UID used during MAC calculation.
   *
   * <p>The transfer syntax determines how DICOM data elements are encoded when computing the MAC.
   * This ensures that MAC validation uses the same encoding as MAC generation, preventing false
   * validation failures due to encoding differences.
   *
   * <p><strong>Common Transfer Syntaxes for MAC:</strong>
   *
   * <ul>
   *   <li>Explicit VR Little Endian - Most common for MAC calculation
   *   <li>Implicit VR Little Endian - Legacy compatibility
   *   <li>Explicit VR Big Endian - Less common but supported
   * </ul>
   *
   * @return the transfer syntax UID used for MAC calculation
   */
  public String getMACCalculationTransferSyntaxUID() {
    return dcmItems.getString(Tag.MACCalculationTransferSyntaxUID);
  }

  public void setMACCalculationTransferSyntaxUID(String transferSyntaxUID) {
    dcmItems.setString(Tag.MACCalculationTransferSyntaxUID, VR.UI, transferSyntaxUID);
  }

  /**
   * Gets the cryptographic algorithm used for MAC computation.
   *
   * <p>The algorithm determines the cryptographic strength and output size of the MAC. Algorithm
   * selection should consider security requirements, performance constraints, and regulatory
   * compliance needs.
   *
   * @return the MAC algorithm identifier (e.g., "RIPEMD160", "SHA1", "SHA256")
   */
  public String getMACAlgorithm() {
    return dcmItems.getString(Tag.MACAlgorithm);
  }

  public void setMACAlgorithm(String algorithm) {
    dcmItems.setString(Tag.MACAlgorithm, VR.CS, algorithm);
  }

  /**
   * Gets the array of DICOM tags identifying which data elements are included in MAC calculation.
   *
   * <p>This selective inclusion mechanism allows MAC protection of specific data elements while
   * excluding others. This is useful for:
   *
   * <ul>
   *   <li>Protecting critical medical data while allowing metadata changes
   *   <li>Excluding elements that may change during processing (e.g., timestamps)
   *   <li>Creating MACs for specific functional groups of elements
   *   <li>Supporting workflows where some elements need modification after signing
   * </ul>
   *
   * <p><strong>Tag Format:</strong> Tags are represented as 32-bit integers where the high 16 bits
   * contain the group number and low 16 bits contain the element number.
   *
   * @return array of DICOM tags included in MAC calculation, or null if not specified
   */
  public int[] getDataElementsSigned() {
    return dcmItems.getInts(Tag.DataElementsSigned);
  }

  public void setDataElementsSigned(int[] signedTags) {
    dcmItems.setInt(Tag.DataElementsSigned, VR.AT, signedTags);
  }
}
