/*
 * Copyright (c) 2014-2019 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.param;

import java.util.Arrays;
import java.util.Objects;
import org.dcm4che3.data.ElementDictionary;

/**
 * Represents a DICOM parameter with its tag, values, and optional parent sequence tags. This
 * immutable class encapsulates DICOM attribute information for query operations.
 *
 * @since 5.0
 */
public class DicomParam {

  private final int tag;
  private final String[] values;
  private final int[] parentSeqTags;

  public DicomParam(int tag, String... values) {
    this(null, tag, values);
  }

  /**
   * Creates a DICOM parameter with parent sequence tags, tag, and values.
   *
   * @param parentSeqTags the parent sequence tags, may be null
   * @param tag the DICOM tag
   * @param values the parameter values (varargs)
   */
  public DicomParam(int[] parentSeqTags, int tag, String... values) {
    this.tag = tag;
    this.values = values != null ? values.clone() : new String[0];
    this.parentSeqTags = parentSeqTags != null ? parentSeqTags.clone() : null;
  }

  /**
   * Gets the DICOM tag.
   *
   * @return the DICOM tag
   */
  public int getTag() {
    return tag;
  }

  /**
   * Gets a defensive copy of the parameter values.
   *
   * @return a copy of the values array
   */
  public String[] getValues() {
    return values.clone();
  }

  /**
   * Gets a defensive copy of the parent sequence tags.
   *
   * @return a copy of the parent sequence tags array, or null if not set
   */
  public int[] getParentSeqTags() {
    return parentSeqTags != null ? parentSeqTags.clone() : null;
  }

  /**
   * Gets the DICOM element name for this tag.
   *
   * @return the element keyword or null if not found
   */
  public String getTagName() {
    return ElementDictionary.keywordOf(tag, null);
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) return true;
    if (obj == null || getClass() != obj.getClass()) return false;

    DicomParam that = (DicomParam) obj;
    return tag == that.tag
        && Arrays.equals(values, that.values)
        && Arrays.equals(parentSeqTags, that.parentSeqTags);
  }

  @Override
  public int hashCode() {
    return Objects.hash(tag, Arrays.hashCode(values), Arrays.hashCode(parentSeqTags));
  }

  @Override
  public String toString() {
    return "DicomParam{"
        + "tag="
        + String.format("0x%08X", tag)
        + ", tagName='"
        + getTagName()
        + '\''
        + ", values="
        + Arrays.toString(values)
        + ", parentSeqTags="
        + Arrays.toString(parentSeqTags)
        + '}';
  }
}
