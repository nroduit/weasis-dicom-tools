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

import java.util.Date;
import java.util.List;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Sequence;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.VR;
import org.weasis.core.util.StringUtil;
import org.weasis.dicom.ref.CodingScheme;

/**
 * DICOM Code Sequence Macro implementation that provides access to coded values and their meanings.
 *
 * <p>This class represents a coded entry as defined in DICOM Part 3, consisting of:
 *
 * <ul>
 *   <li>Code Value - the actual coded value (required)
 *   <li>Coding Scheme Designator - identifies the coding scheme (required)
 *   <li>Code Meaning - human-readable meaning of the code (required)
 *   <li>Context and mapping information (optional)
 * </ul>
 *
 * <p>Supports multiple code value formats: standard Code Value, Long Code Value (UC VR), and URN
 * Code Value (UR VR) as per DICOM CP-1640.
 *
 * @see ItemCode
 * @see CodingScheme
 */
public class Code extends Module implements ItemCode {

  /**
   * Creates a Code instance from existing DICOM attributes.
   *
   * @param dcmItems the DICOM attributes containing code information
   */
  public Code(Attributes dcmItems) {
    super(dcmItems);
  }

  /** Creates an empty Code instance with default attributes. */
  public Code() {
    this(new Attributes());
  }

  /**
   * Converts a DICOM sequence to a list of Code objects.
   *
   * @param seq the DICOM sequence to convert (may be null or empty)
   * @return an immutable list of Code objects, empty if input is null or empty
   */
  public static List<Code> toCodeMacros(Sequence seq) {
    if (seq == null || seq.isEmpty()) {
      return List.of();
    }

    return seq.stream().map(Code::new).toList();
  }

  /**
   * Extracts a nested Code from DICOM attributes at the specified tag.
   *
   * @param dcmItems the parent DICOM attributes
   * @param tag the DICOM tag containing the nested code sequence
   * @return the nested Code object, or null if not present
   */
  public static Code getNestedCode(Attributes dcmItems, int tag) {
    Attributes item = dcmItems.getNestedDataset(tag);
    return item != null ? new Code(item) : null;
  }

  /**
   * Returns the first available code value, trying Code Value, Long Code Value, then URN Code
   * Value.
   *
   * @return the first non-empty code value found, or null if none exist
   */
  public String getExistingCodeValue() {
    String val = getCodeValue();
    if (!StringUtil.hasText(val)) {
      val = getLongCodeValue();
    }
    if (!StringUtil.hasText(val)) {
      val = getURNCodeValue();
    }
    return val;
  }

  @Override
  public String getCodeValue() {
    return dcmItems.getString(Tag.CodeValue);
  }

  public void setCodeValue(String value) {
    dcmItems.setString(Tag.CodeValue, VR.SH, value);
  }

  public String getLongCodeValue() {
    return dcmItems.getString(Tag.LongCodeValue);
  }

  public void setLongCodeValue(String value) {
    dcmItems.setString(Tag.LongCodeValue, VR.UC, value);
  }

  public String getURNCodeValue() {
    return dcmItems.getString(Tag.URNCodeValue);
  }

  public void setURNCodeValue(String value) {
    dcmItems.setString(Tag.URNCodeValue, VR.UR, value);
  }

  public String getCodingSchemeDesignator() {
    return dcmItems.getString(Tag.CodingSchemeDesignator);
  }

  public void setCodingSchemeDesignator(String value) {
    dcmItems.setString(Tag.CodingSchemeDesignator, VR.SH, value);
  }

  public String getCodingSchemeVersion() {
    return dcmItems.getString(Tag.CodingSchemeVersion);
  }

  public void setCodingSchemeVersion(String value) {
    dcmItems.setString(Tag.CodingSchemeVersion, VR.SH, value);
  }

  @Override
  public String getCodeMeaning() {
    return dcmItems.getString(Tag.CodeMeaning);
  }

  public void setCodeMeaning(String value) {
    dcmItems.setString(Tag.CodeMeaning, VR.LO, value);
  }

  @Override
  public CodingScheme getCodingScheme() {
    return CodingScheme.fromDesignator(getCodingSchemeDesignator()).orElse(null);
  }

  public void setCodingScheme(CodingScheme codingScheme) {
    if (codingScheme != null) {
      setCodingSchemeDesignator(codingScheme.getDesignator());
    }
  }

  public String getContextIdentifier() {
    return dcmItems.getString(Tag.ContextIdentifier);
  }

  public void setContextIdentifier(String value) {
    dcmItems.setString(Tag.ContextIdentifier, VR.CS, value);
  }

  public String getContextUID() {
    return dcmItems.getString(Tag.ContextUID);
  }

  public void setContextUID(String value) {
    dcmItems.setString(Tag.ContextUID, VR.UI, value);
  }

  public String getMappingResource() {
    return dcmItems.getString(Tag.MappingResource);
  }

  public void setMappingResource(String value) {
    dcmItems.setString(Tag.MappingResource, VR.CS, value);
  }

  public Date getContextGroupVersion() {
    return dcmItems.getDate(Tag.ContextGroupVersion);
  }

  public void setContextGroupVersion(Date date) {
    dcmItems.setDate(Tag.ContextGroupVersion, VR.DT, date);
  }

  public String getContextGroupExtensionFlag() {
    return dcmItems.getString(Tag.ContextGroupExtensionFlag);
  }

  public void setContextGroupExtensionFlag(String value) {
    dcmItems.setString(Tag.ContextGroupExtensionFlag, VR.CS, value);
  }

  public Date getContextGroupLocalVersion() {
    return dcmItems.getDate(Tag.ContextGroupLocalVersion);
  }

  public void setContextGroupLocalVersion(Date date) {
    dcmItems.setDate(Tag.ContextGroupLocalVersion, VR.DT, date);
  }

  public String getContextGroupExtensionCreatorUID() {
    return dcmItems.getString(Tag.ContextGroupExtensionCreatorUID);
  }

  public void setContextGroupExtensionCreatorUID(String value) {
    dcmItems.setString(Tag.ContextGroupExtensionCreatorUID, VR.UI, value);
  }
}
