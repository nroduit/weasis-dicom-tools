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

public class SopInstance implements Xml, Comparable<SopInstance> {

  private final String sopInstanceUID;
  private final String sopClassUID;
  private final Integer instanceNumber;
  private String imageComments;
  private String transferSyntaxUID;
  private String directDownloadFile;
  private Object graphicModel;

  public SopInstance(String sopInstanceUID, Integer instanceNumber) {
    this(sopInstanceUID, null, instanceNumber);
  }

  /**
   * Create a new SopInstance. The sopInstanceUID can be the same for a multi-frame.
   *
   * @param sopInstanceUID the DICOM SOP Instance UID (it cannot be null).
   * @param sopClassUID the DICOM SOP Class UID (it can be null).
   * @param instanceNumber the DICOM Instance Number (it can be null). The frame position for a
   *     multi-frame.
   */
  public SopInstance(String sopInstanceUID, String sopClassUID, Integer instanceNumber) {
    this.sopInstanceUID = Objects.requireNonNull(sopInstanceUID, "sopInstanceIUID is null");
    this.sopClassUID = sopClassUID;
    this.instanceNumber = instanceNumber;
  }

  public String getTransferSyntaxUID() {
    return transferSyntaxUID;
  }

  public void setTransferSyntaxUID(String transferSyntaxUID) {
    this.transferSyntaxUID =
        StringUtil.hasText(transferSyntaxUID) ? transferSyntaxUID.trim() : null;
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
    return instanceNumber == null ? null : String.valueOf(instanceNumber);
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

  public String getImageComments() {
    return imageComments;
  }

  public void setImageComments(String imageComments) {
    this.imageComments = imageComments;
  }

  @Override
  public int hashCode() {
    final int prime = 31;
    int result = 1;
    result = prime * result + ((instanceNumber == null) ? 0 : instanceNumber.hashCode());
    result = prime * result + sopInstanceUID.hashCode();
    return result;
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) return true;
    if (obj == null) return false;
    if (getClass() != obj.getClass()) return false;
    SopInstance other = (SopInstance) obj;
    if (instanceNumber == null) {
      if (other.instanceNumber != null) return false;
    } else if (!instanceNumber.equals(other.instanceNumber)) return false;
    return sopInstanceUID.equals(other.sopInstanceUID);
  }

  @Override
  public void toXml(Writer result) throws IOException {
    result.append("\n<");
    result.append(Xml.Level.INSTANCE.getTagName());
    result.append(" ");
    Xml.addXmlAttribute(Tag.SOPInstanceUID, sopInstanceUID, result);
    Xml.addXmlAttribute(Tag.SOPClassUID, sopClassUID, result);
    // File DICOM Transfer Syntax UID (0002,0010)
    Xml.addXmlAttribute(Tag.TransferSyntaxUID, transferSyntaxUID, result);
    Xml.addXmlAttribute(Tag.ImageComments, imageComments, result);
    Xml.addXmlAttribute(Tag.InstanceNumber, getStringInstanceNumber(), result);
    Xml.addXmlAttribute("DirectDownloadFile", directDownloadFile, result);
    result.append("/>");
  }

  @Override
  public int compareTo(SopInstance o) {
    // Step 1: Compare Instance Numbers
    Integer num1 = getInstanceNumber();
    Integer num2 = o.getInstanceNumber();

    if (num1 != null && num2 != null) {
      int cmp = num1.compareTo(num2);
      if (cmp != 0) {
        return cmp;
      }
    } else if (num1 == null && num2 != null) {
      return 1; // Null instance number is placed after
    } else if (num1 != null) {
      return -1; // Non-null instance number is placed before
    }

    // Step 2: Compare SOP Instance UIDs
    return compareSopInstanceUID(getSopInstanceUID(), o.getSopInstanceUID());
  }

  private static int compareSopInstanceUID(String s1, String s2) {
    int length1 = s1.length();
    int length2 = s2.length();
    if (length1 < length2) {
      char[] c = new char[length2 - length1];
      return adaptSopInstanceUID(s1, c).compareTo(s2);
    } else if (length1 > length2) {
      char[] c = new char[length1 - length2];
      return s1.compareTo(adaptSopInstanceUID(s2, c));
    }
    return s1.compareTo(s2);
  }

  private static String adaptSopInstanceUID(String s, char[] c) {
    Arrays.fill(c, '0');
    int index = s.lastIndexOf('.') + 1;
    return s.substring(0, index) + String.valueOf(c) + s.substring(index);
  }

  public static void addSopInstance(Map<String, SopInstance> sopInstanceMap, SopInstance s) {
    if (s != null && sopInstanceMap != null) {
      StringBuilder key = new StringBuilder(s.getSopInstanceUID());
      if (s.getInstanceNumber() != null) {
        key.append("?");
        key.append(s.getInstanceNumber());
      }
      sopInstanceMap.put(key.toString(), s);
    }
  }

  public static SopInstance removeSopInstance(
      Map<String, SopInstance> sopInstanceMap, String sopUID, Integer instanceNumber) {
    if (sopUID == null || sopInstanceMap == null) {
      return null;
    }
    StringBuilder key = new StringBuilder(sopUID);
    if (instanceNumber != null) {
      key.append("?");
      key.append(instanceNumber);
    }
    return sopInstanceMap.remove(key.toString());
  }

  public static SopInstance getSopInstance(
      Map<String, SopInstance> sopInstanceMap, String sopUID, Integer instanceNumber) {
    if (sopUID == null || sopInstanceMap == null) {
      return null;
    }
    StringBuilder key = new StringBuilder(sopUID);
    if (instanceNumber != null) {
      key.append("?");
      key.append(instanceNumber);
    }
    return sopInstanceMap.get(key.toString());
  }
}
