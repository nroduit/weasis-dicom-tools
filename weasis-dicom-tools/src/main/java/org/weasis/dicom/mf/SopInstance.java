/*
 * Copyright (c) 2017-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.mf;

import java.io.IOException;
import java.io.Writer;
import java.util.Arrays;
import java.util.Map;
import java.util.Objects;
import org.dcm4che3.data.Tag;
import org.weasis.core.util.StringUtil;

/**
 * Represents a DICOM SOP (Service-Object Pair) Instance within a series. Provides functionality for
 * XML serialization, comparison, and map operations. Supports both single-frame and multi-frame
 * DICOM instances.
 */
public class SopInstance implements Xml, Comparable<SopInstance> {

  private static final String KEY_SEPARATOR = "?";
  private final String sopInstanceUID;
  private final String sopClassUID;
  private final Integer instanceNumber;
  private String imageComments;
  private String transferSyntaxUID;
  private String directDownloadFile;
  private Object graphicModel;

  /** Creates a SOP instance with the specified UID and instance number. */
  public SopInstance(String sopInstanceUID, Integer instanceNumber) {
    this(sopInstanceUID, null, instanceNumber);
  }

  /**
   * Creates a SOP instance with full DICOM identification. The sopInstanceUID can be the same for
   * multi-frame instances.
   *
   * @param sopInstanceUID the DICOM SOP Instance UID, required
   * @param sopClassUID the DICOM SOP Class UID, may be null
   * @param instanceNumber the DICOM Instance Number or frame position, may be null
   * @throws NullPointerException if sopInstanceUID is null
   */
  public SopInstance(String sopInstanceUID, String sopClassUID, Integer instanceNumber) {
    this.sopInstanceUID = Objects.requireNonNull(sopInstanceUID, "SOP Instance UID cannot be null");
    this.sopClassUID = sopClassUID;
    this.instanceNumber = instanceNumber;
  }

  public String getSopInstanceUID() {
    return sopInstanceUID;
  }

  public String getSopClassUID() {
    return sopClassUID;
  }

  public Integer getInstanceNumber() {
    return instanceNumber;
  }

  public String getStringInstanceNumber() {
    return instanceNumber != null ? instanceNumber.toString() : null;
  }

  public String getTransferSyntaxUID() {
    return transferSyntaxUID;
  }

  public void setTransferSyntaxUID(String transferSyntaxUID) {
    this.transferSyntaxUID =
        StringUtil.hasText(transferSyntaxUID) ? transferSyntaxUID.trim() : null;
  }

  public String getImageComments() {
    return imageComments;
  }

  public void setImageComments(String imageComments) {
    this.imageComments = imageComments;
  }

  public String getDirectDownloadFile() {
    return directDownloadFile;
  }

  public void setDirectDownloadFile(String directDownloadFile) {
    this.directDownloadFile = directDownloadFile;
  }

  public Object getGraphicModel() {
    return graphicModel;
  }

  public void setGraphicModel(Object graphicModel) {
    this.graphicModel = graphicModel;
  }

  @Override
  public void toXml(Writer writer) throws IOException {
    writer.append("\n<").append(Xml.Level.INSTANCE.getTagName()).append(" ");

    Xml.addXmlAttribute(Tag.SOPInstanceUID, sopInstanceUID, writer);
    Xml.addXmlAttribute(Tag.SOPClassUID, sopClassUID, writer);
    Xml.addXmlAttribute(Tag.TransferSyntaxUID, transferSyntaxUID, writer);
    Xml.addXmlAttribute(Tag.ImageComments, imageComments, writer);
    Xml.addXmlAttribute(Tag.InstanceNumber, getStringInstanceNumber(), writer);
    Xml.addXmlAttribute("DirectDownloadFile", directDownloadFile, writer);

    writer.append("/>");
  }

  @Override
  public int compareTo(SopInstance other) {
    // Compare instance numbers first (null values sorted last)
    int instanceComparison = compareInstanceNumbers(this.instanceNumber, other.instanceNumber);
    if (instanceComparison != 0) {
      return instanceComparison;
    }

    // Compare SOP Instance UIDs with length normalization
    return compareNormalizedUIDs(this.sopInstanceUID, other.sopInstanceUID);
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) return true;
    if (obj == null || getClass() != obj.getClass()) return false;

    var other = (SopInstance) obj;
    return Objects.equals(instanceNumber, other.instanceNumber)
        && sopInstanceUID.equals(other.sopInstanceUID);
  }

  @Override
  public int hashCode() {
    return Objects.hash(sopInstanceUID, instanceNumber);
  }

  /**
   * Adds a SOP instance to the specified map using a composite key. Key format: "sopInstanceUID" or
   * "sopInstanceUID?instanceNumber"
   *
   * @param sopInstanceMap the map to add to, ignored if null
   * @param sopInstance the instance to add, ignored if null
   */
  public static void addSopInstance(
      Map<String, SopInstance> sopInstanceMap, SopInstance sopInstance) {
    if (sopInstance != null && sopInstanceMap != null) {
      String key = buildMapKey(sopInstance.getSopInstanceUID(), sopInstance.getInstanceNumber());
      sopInstanceMap.put(key, sopInstance);
    }
  }

  /**
   * Retrieves a SOP instance from the map using UID and optional instance number.
   *
   * @param sopInstanceMap the map to search, returns null if null
   * @param sopUID the SOP Instance UID, required
   * @param instanceNumber the instance number, may be null
   * @return the matching SOP instance, or null if not found
   */
  public static SopInstance getSopInstance(
      Map<String, SopInstance> sopInstanceMap, String sopUID, Integer instanceNumber) {
    if (sopUID == null || sopInstanceMap == null) {
      return null;
    }
    return sopInstanceMap.get(buildMapKey(sopUID, instanceNumber));
  }

  /**
   * Removes and returns a SOP instance from the map.
   *
   * @param sopInstanceMap the map to remove from, returns null if null
   * @param sopUID the SOP Instance UID, required
   * @param instanceNumber the instance number, may be null
   * @return the removed SOP instance, or null if not found
   */
  public static SopInstance removeSopInstance(
      Map<String, SopInstance> sopInstanceMap, String sopUID, Integer instanceNumber) {
    if (sopUID == null || sopInstanceMap == null) {
      return null;
    }
    return sopInstanceMap.remove(buildMapKey(sopUID, instanceNumber));
  }

  // Compares instance numbers with null-safe logic
  private static int compareInstanceNumbers(Integer num1, Integer num2) {
    if (num1 != null && num2 != null) {
      return num1.compareTo(num2);
    }
    if (num1 == null && num2 != null) {
      return 1; // Null values sorted last
    }
    if (num1 != null) {
      return -1; // Non-null values sorted first
    }
    return 0; // Both null
  }

  // Compares UIDs with length normalization for proper sorting
  private static int compareNormalizedUIDs(String uid1, String uid2) {
    int length1 = uid1.length();
    int length2 = uid2.length();

    if (length1 < length2) {
      return normalizeUID(uid1, length2 - length1).compareTo(uid2);
    } else if (length1 > length2) {
      return uid1.compareTo(normalizeUID(uid2, length1 - length2));
    }
    return uid1.compareTo(uid2);
  }

  // Normalizes UID by padding the numeric suffix with zeros
  private static String normalizeUID(String uid, int paddingLength) {
    char[] padding = new char[paddingLength];
    Arrays.fill(padding, '0');

    int lastDotIndex = uid.lastIndexOf('.') + 1;
    return uid.substring(0, lastDotIndex) + new String(padding) + uid.substring(lastDotIndex);
  }

  // Builds composite key for map operations
  private static String buildMapKey(String sopUID, Integer instanceNumber) {
    return instanceNumber != null ? sopUID + KEY_SEPARATOR + instanceNumber : sopUID;
  }
}
