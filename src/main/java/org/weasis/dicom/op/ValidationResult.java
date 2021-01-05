/*
 * Copyright (c) 2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at http://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.op;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.dcm4che6.data.DicomElement;
import org.dcm4che6.data.DicomObject;
import org.dcm4che6.data.ElementDictionary;
import org.dcm4che6.data.VR;
import org.dcm4che6.util.StringUtils;
import org.dcm4che6.util.TagUtils;
import org.weasis.dicom.op.IOD.DataElement;

/** @author Gunter Zeilinger <gunterze@gmail.com> */
public class ValidationResult {

  public enum Invalid {
    VR,
    VM,
    Value,
    Item,
    MultipleItems,
    Code
  }

  public class InvalidAttributeValue {
    public final IOD.DataElement dataElement;
    public final Invalid reason;
    public final ValidationResult[] itemValidationResults;
    public final IOD[] missingItems;

    public InvalidAttributeValue(
        DataElement dataElement,
        Invalid reason,
        ValidationResult[] itemValidationResults,
        IOD[] missingItems) {
      this.dataElement = dataElement;
      this.reason = reason;
      this.itemValidationResults = itemValidationResults;
      this.missingItems = missingItems;
    }
  }

  private ArrayList<IOD.DataElement> missingAttributes;
  private ArrayList<IOD.DataElement> missingAttributeValues;
  private ArrayList<IOD.DataElement> notAllowedAttributes;
  private ArrayList<InvalidAttributeValue> invalidAttributeValues;

  public boolean hasMissingAttributes() {
    return missingAttributes != null;
  }

  public boolean hasMissingAttributeValues() {
    return missingAttributeValues != null;
  }

  public boolean hasInvalidAttributeValues() {
    return invalidAttributeValues != null;
  }

  public boolean hasNotAllowedAttributes() {
    return notAllowedAttributes != null;
  }

  public boolean isValid() {
    return !hasMissingAttributes()
        && !hasMissingAttributeValues()
        && !hasInvalidAttributeValues()
        && !hasNotAllowedAttributes();
  }

  public void addMissingAttribute(IOD.DataElement dataElement) {
    if (missingAttributes == null) missingAttributes = new ArrayList<>();
    missingAttributes.add(dataElement);
  }

  public void addMissingAttributeValue(IOD.DataElement dataElement) {
    if (missingAttributeValues == null) missingAttributeValues = new ArrayList<>();
    missingAttributeValues.add(dataElement);
  }

  public void addInvalidAttributeValue(IOD.DataElement dataElement, Invalid reason) {
    addInvalidAttributeValue(dataElement, reason, null, null);
  }

  public void addInvalidAttributeValue(
      IOD.DataElement dataElement,
      Invalid reason,
      ValidationResult[] itemValidationResult,
      IOD[] missingItems) {
    if (invalidAttributeValues == null) invalidAttributeValues = new ArrayList<>();
    invalidAttributeValues.add(
        new InvalidAttributeValue(dataElement, reason, itemValidationResult, missingItems));
  }

  public void addNotAllowedAttribute(DataElement el) {
    if (notAllowedAttributes == null) notAllowedAttributes = new ArrayList<>();
    notAllowedAttributes.add(el);
  }

  public int[] tagsOfNotAllowedAttributes() {
    return tagsOf(notAllowedAttributes);
  }

  public int[] tagsOfMissingAttributeValues() {
    return tagsOf(missingAttributeValues);
  }

  public int[] tagsOfMissingAttributes() {
    return tagsOf(missingAttributes);
  }

  public int[] tagsOfInvalidAttributeValues() {
    ArrayList<InvalidAttributeValue> list = invalidAttributeValues;
    if (list == null) return DicomElement.EMPTY_INTS;

    int[] tags = new int[list.size()];
    for (int i = 0; i < tags.length; i++) tags[i] = list.get(i).dataElement.tag;
    return tags;
  }

  public int[] getOffendingElements() {
    return cat(
        tagsOfMissingAttributes(),
        tagsOfMissingAttributeValues(),
        tagsOfInvalidAttributeValues(),
        tagsOfNotAllowedAttributes());
  }

  private int[] cat(int[]... iss) {
    int length = 0;
    for (int[] is : iss) length += is.length;
    int[] tags = new int[length];
    int off = 0;
    for (int[] is : iss) {
      System.arraycopy(is, 0, tags, off, is.length);
      off += is.length;
    }
    return tags;
  }

  private int[] tagsOf(List<DataElement> list) {
    if (list == null) return DicomElement.EMPTY_INTS;

    int[] tags = new int[list.size()];
    for (int i = 0; i < tags.length; i++) tags[i] = list.get(i).tag;
    return tags;
  }

  public String getErrorComment() {
    StringBuilder sb = new StringBuilder();
    if (notAllowedAttributes != null)
      return errorComment(sb, "Not allowed Attribute", tagsOfNotAllowedAttributes()).toString();
    if (missingAttributes != null)
      return errorComment(sb, "Missing Attribute", tagsOfMissingAttributes()).toString();
    if (missingAttributeValues != null)
      return errorComment(sb, "Missing Value of Attribute", tagsOfMissingAttributeValues())
          .toString();
    if (invalidAttributeValues != null)
      return errorComment(sb, "Invalid Attribute", tagsOfInvalidAttributeValues()).toString();
    return null;
  }

  private static StringBuilder errorComment(StringBuilder sb, String prompt, int[] tags) {
    sb.append(prompt);
    String prefix = tags.length > 1 ? "s: " : ": ";
    for (int tag : tags) {
      sb.append(prefix).append(TagUtils.toString(tag));
      prefix = ", ";
    }
    return sb;
  }

  @Override
  public String toString() {
    if (isValid()) return "VALID";

    StringBuilder sb = new StringBuilder();
    if (notAllowedAttributes != null)
      errorComment(sb, "Not allowed Attribute", tagsOfNotAllowedAttributes())
          .append(StringUtils.LINE_SEPARATOR);
    if (missingAttributes != null)
      errorComment(sb, "Missing Attribute", tagsOfMissingAttributes())
          .append(StringUtils.LINE_SEPARATOR);
    if (missingAttributeValues != null)
      errorComment(sb, "Missing Value of Attribute", tagsOfMissingAttributeValues())
          .append(StringUtils.LINE_SEPARATOR);
    if (invalidAttributeValues != null)
      errorComment(sb, "Invalid Attribute", tagsOfInvalidAttributeValues())
          .append(StringUtils.LINE_SEPARATOR);

    return sb.substring(0, sb.length() - 1);
  }

  public String asText(DicomObject attrs) {
    if (isValid()) return "VALID";

    StringBuilder sb = new StringBuilder();
    appendTextTo(0, attrs, sb);
    return sb.substring(0, sb.length() - 1);
  }

  private void appendTextTo(int level, DicomObject attrs, StringBuilder sb) {
    if (notAllowedAttributes != null)
      appendTextTo(level, attrs, "Not allowed Attributes:", notAllowedAttributes, sb);
    if (missingAttributes != null)
      appendTextTo(level, attrs, "Missing Attributes:", missingAttributes, sb);
    if (missingAttributeValues != null)
      appendTextTo(level, attrs, "Missing Attribute Values:", missingAttributeValues, sb);
    if (invalidAttributeValues != null)
      appendInvalidAttributeValues(level, attrs, "Invalid Attribute Values:", sb);
  }

  private void appendTextTo(
      int level, DicomObject attrs, String title, List<DataElement> list, StringBuilder sb) {
    appendPrefixTo(level, sb);
    sb.append(title).append(StringUtils.LINE_SEPARATOR);
    for (DataElement el : list) {
      appendAttribute(level, el.tag, sb);
      appendIODRef(el.getLineNumber(), sb);
      sb.append(StringUtils.LINE_SEPARATOR);
    }
  }

  private void appendIODRef(int lineNumber, StringBuilder sb) {
    if (lineNumber > 0) sb.append(" // IOD line #").append(lineNumber);
  }

  private void appendInvalidAttributeValues(
      int level, DicomObject attrs, String title, StringBuilder sb) {
    appendPrefixTo(level, sb);
    sb.append(title);
    sb.append(StringUtils.LINE_SEPARATOR);
    for (InvalidAttributeValue iav : invalidAttributeValues) {
      int tag = iav.dataElement.tag;
      VR vr;
      appendAttribute(level, tag, sb);
      Optional<DicomElement> el = attrs.get(tag);
      if (el.isPresent()) {
        el.get().promptTo(sb, 200);
      }
      if (iav.reason != Invalid.Item) {
        sb.append(" Invalid ").append(iav.reason);
        appendIODRef(iav.dataElement.getLineNumber(), sb);
      }
      sb.append(StringUtils.LINE_SEPARATOR);
      if (iav.missingItems != null) {
        for (IOD iod : iav.missingItems) {
          appendPrefixTo(level + 1, sb);
          sb.append("Missing Item");
          appendIODRef(iod.getLineNumber(), sb);
          sb.append(StringUtils.LINE_SEPARATOR);
        }
      }
      if (iav.itemValidationResults != null && el.isPresent()) {
        DicomElement seq = el.get();
        for (int i = 0; i < iav.itemValidationResults.length; i++) {
          ValidationResult itemResult = iav.itemValidationResults[i];
          if (!itemResult.isValid()) {
            appendPrefixTo(level + 1, sb);
            sb.append("Invalid Item ").append(i + 1).append(':').append(StringUtils.LINE_SEPARATOR);
            itemResult.appendTextTo(level + 1, seq.getItem(i), sb);
          }
        }
      }
    }
  }

  private void appendAttribute(int level, int tag, StringBuilder sb) {
    appendPrefixTo(level, sb);
    sb.append(TagUtils.toString(tag)).append(' ').append(ElementDictionary.keywordOf(tag, null));
  }

  private void appendPrefixTo(int level, StringBuilder sb) {
    while (level-- > 0) sb.append('>');
  }
}
