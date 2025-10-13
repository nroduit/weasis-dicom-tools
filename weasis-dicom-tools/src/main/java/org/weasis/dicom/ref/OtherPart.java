/*
 * Copyright (c) 2024 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.ref;

import java.util.Objects;
import org.weasis.core.util.StringUtil;
import org.weasis.dicom.macro.ItemCode;

/**
 * Represents a custom anatomical part not defined in the standard BodyPart or SurfacePart enums.
 * This record provides a flexible way to handle anatomical structures that may be
 * institution-specific or newly defined in medical terminology systems.
 *
 * <p>This implementation is immutable and thread-safe, following the record pattern introduced in
 * Java 17. It provides automatic implementations of equals(), hashCode(), and toString() methods.
 *
 * <p>Example usage:
 *
 * <pre>{@code
 * OtherPart customPart = new OtherPart(
 *     "12345678",
 *     "Custom anatomical structure",
 *     CodingScheme.SCT,
 *     true  // paired structure
 * );
 * }</pre>
 *
 * @param codeValue the unique code value identifying this anatomical part
 * @param codeMeaning the human-readable description of this anatomical part
 * @param codingScheme the coding scheme that defines this code
 * @param paired whether this anatomical structure naturally occurs in pairs
 * @see AnatomicItem
 * @see BodyPart
 * @see SurfacePart
 * @see CodingScheme
 */
public record OtherPart(
    String codeValue, String codeMeaning, CodingScheme codingScheme, boolean paired)
    implements AnatomicItem {

  /**
   * Compact constructor with validation to ensure data integrity. This constructor automatically
   * validates all parameters and provides clear error messages for invalid inputs.
   *
   * @throws IllegalArgumentException if any required parameter is null or empty
   */
  public OtherPart {
    if (!StringUtil.hasText(codeValue)) {
      throw new IllegalArgumentException("Code value cannot be null or empty");
    }
    if (!StringUtil.hasText(codeMeaning)) {
      codeMeaning = "UNKNOWN";
    }
    if (codingScheme == null) {
      codingScheme = CodingScheme.UNKNOWN;
    }
  }

  /**
   * Convenience constructor for unpaired anatomical structures. Creates an OtherPart with paired
   * set to false.
   *
   * @param codeValue the unique code value
   * @param codeMeaning the human-readable description
   * @param codingScheme the coding scheme
   */
  public OtherPart(String codeValue, String codeMeaning, CodingScheme codingScheme) {
    this(codeValue, codeMeaning, codingScheme, false);
  }

  @Override
  public String getCodeValue() {
    return codeValue;
  }

  @Override
  public String getCodeMeaning() {
    return codeMeaning;
  }

  @Override
  public CodingScheme getCodingScheme() {
    return codingScheme;
  }

  /**
   * Returns null as custom parts typically don't have legacy DICOM codes. Legacy codes are
   * primarily associated with standard BodyPart enum values.
   *
   * @return always null for custom anatomical parts
   */
  @Override
  public String getLegacyCode() {
    return null;
  }

  @Override
  public boolean isPaired() {
    return paired;
  }

  /**
   * Checks if this OtherPart uses the same coding scheme as another ItemCode.
   *
   * @param other the ItemCode to compare against
   * @return true if both use the same coding scheme, false otherwise
   */
  public boolean hasSameCodingScheme(ItemCode other) {
    return other != null && Objects.equals(this.codingScheme, other.getCodingScheme());
  }
}
