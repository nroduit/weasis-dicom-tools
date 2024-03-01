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
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Set;
import org.dcm4che3.data.Tag;
import org.weasis.core.util.StringUtil;

public class Patient implements Xml, Comparable<Patient> {

  private final String patientID;
  private final String issuerOfPatientID;
  private String patientName;
  private String patientBirthDate = null;
  private String patientBirthTime = null;
  private String patientSex = null;
  private final Map<String, Study> studiesMap;

  /**
   * Create a new Patient. The patientID is associated to the Issuer Of PatientID to compose a
   * pseudo PatientUID (the patient global identifier).
   *
   * @param patientID
   * @param issuerOfPatientID the Issuer Of PatientID. It can be null.
   */
  public Patient(String patientID, String issuerOfPatientID) {
    this.patientID = Objects.requireNonNull(patientID, "PaientID cannot be null!");
    this.issuerOfPatientID = issuerOfPatientID;
    this.patientName = "";
    this.studiesMap = new HashMap<>();
  }

  public String getPatientID() {
    return patientID;
  }

  public String getPseudoPatientUID() {
    StringBuilder key = new StringBuilder(patientID);
    if (issuerOfPatientID != null) {
      key.append(issuerOfPatientID);
    }
    return key.toString();
  }

  public String getPatientName() {
    return patientName;
  }

  public Collection<Study> getStudies() {
    return studiesMap.values();
  }

  public boolean isEmpty() {
    for (Study s : studiesMap.values()) {
      if (!s.isEmpty()) {
        return false;
      }
    }
    return true;
  }

  public String getPatientBirthTime() {
    return patientBirthTime;
  }

  public void setPatientBirthTime(String patientBirthTime) {
    this.patientBirthTime = patientBirthTime;
  }

  public String getPatientBirthDate() {
    return patientBirthDate;
  }

  public void setPatientBirthDate(String patientBirthDate) {
    this.patientBirthDate = patientBirthDate;
  }

  public String getPatientSex() {
    return patientSex;
  }

  public void setPatientSex(String patientSex) {
    if (patientSex == null) {
      this.patientSex = null;
    } else {
      String val = patientSex.toUpperCase(Locale.getDefault());
      this.patientSex = val.startsWith("M") ? "M" : val.startsWith("F") ? "F" : "O";
    }
  }

  public void setPatientName(String patientName) {
    this.patientName = StringUtil.getEmptyStringIfNull(patientName);
  }

  public void addStudy(Study study) {
    if (study != null) {
      studiesMap.put(study.getStudyInstanceUID(), study);
    }
  }

  public Study removeStudy(String studyUID) {
    return studiesMap.remove(studyUID);
  }

  public Study getStudy(String studyUID) {
    return studiesMap.get(studyUID);
  }

  public Set<Entry<String, Study>> getEntrySet() {
    return studiesMap.entrySet();
  }

  @Override
  public int hashCode() {
    final int prime = 31;
    int result = 1;
    result = prime * result + ((issuerOfPatientID == null) ? 0 : issuerOfPatientID.hashCode());
    result = prime * result + patientID.hashCode();
    return result;
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) return true;
    if (obj == null) return false;
    if (getClass() != obj.getClass()) return false;
    Patient other = (Patient) obj;
    if (issuerOfPatientID == null) {
      if (other.issuerOfPatientID != null) return false;
    } else if (!issuerOfPatientID.equals(other.issuerOfPatientID)) return false;
    return patientID.equals(other.patientID);
  }

  @Override
  public void toXml(Writer result) throws IOException {
    if (patientID != null && patientName != null) {
      result.append("\n<");
      result.append(Xml.Level.PATIENT.getTagName());
      result.append(" ");

      Xml.addXmlAttribute(Tag.PatientID, patientID, result);
      Xml.addXmlAttribute(Tag.IssuerOfPatientID, issuerOfPatientID, result);
      Xml.addXmlAttribute(Tag.PatientName, patientName, result);
      Xml.addXmlAttribute(Tag.PatientBirthDate, patientBirthDate, result);
      Xml.addXmlAttribute(Tag.PatientBirthTime, patientBirthTime, result);
      Xml.addXmlAttribute(Tag.PatientSex, patientSex, result);
      result.append(">");

      List<Study> list = new ArrayList<>(studiesMap.values());
      Collections.sort(list);
      for (Study s : list) {
        s.toXml(result);
      }
      result.append("\n</");
      result.append(Xml.Level.PATIENT.getTagName());
      result.append(">");
    }
  }

  @Override
  public int compareTo(Patient p) {
    return getPatientName().compareTo(p.getPatientName());
  }
}
