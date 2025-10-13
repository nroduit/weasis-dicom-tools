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
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Set;
import org.dcm4che3.data.Tag;
import org.weasis.core.util.StringUtil;

/**
 * Represents a DICOM patient containing multiple studies. Provides functionality for XML
 * serialization, study management, and patient identification.
 *
 * <p>A patient is identified by a combination of Patient ID and optional Issuer of Patient ID,
 * which together form a pseudo Patient UID for global identification across different systems.
 */
public class Patient implements Xml, Comparable<Patient> {

  /** Valid patient sex values according to DICOM standard */
  private enum PatientSex {
    MALE("M"),
    FEMALE("F"),
    OTHER("O");

    private final String code;

    PatientSex(String code) {
      this.code = code;
    }

    public String getCode() {
      return code;
    }

    public static String normalize(String sex) {
      if (sex == null) {
        return null;
      }

      String upperSex = sex.toUpperCase(Locale.getDefault());
      if (upperSex.startsWith("M")) {
        return MALE.getCode();
      } else if (upperSex.startsWith("F")) {
        return FEMALE.getCode();
      } else {
        return OTHER.getCode();
      }
    }
  }

  private final String patientID;
  private final String issuerOfPatientID;
  private final Map<String, Study> studiesMap;
  private String patientName;
  private String patientBirthDate;
  private String patientBirthTime;
  private String patientSex;

  /**
   * Creates a new patient with the specified ID and optional issuer. The combination of patient ID
   * and issuer forms a pseudo Patient UID for global identification.
   *
   * @param patientID the DICOM Patient ID, required
   * @param issuerOfPatientID the Issuer of Patient ID, may be null
   * @throws NullPointerException if patientID is null
   */
  public Patient(String patientID, String issuerOfPatientID) {
    this.patientID = Objects.requireNonNull(patientID, "Patient ID cannot be null");
    this.issuerOfPatientID = issuerOfPatientID;
    this.patientName = "";
    this.studiesMap = new HashMap<>();
  }

  public String getPatientID() {
    return patientID;
  }

  public String getIssuerOfPatientID() {
    return issuerOfPatientID;
  }

  /**
   * Returns the pseudo Patient UID formed by combining Patient ID and Issuer of Patient ID. This
   * provides a globally unique identifier across different systems.
   *
   * @return the pseudo Patient UID
   */
  public String getPseudoPatientUID() {
    return issuerOfPatientID != null ? patientID + issuerOfPatientID : patientID;
  }

  public String getPatientName() {
    return patientName;
  }

  public void setPatientName(String patientName) {
    this.patientName = StringUtil.getEmptyStringIfNull(patientName);
  }

  public String getPatientBirthDate() {
    return patientBirthDate;
  }

  public void setPatientBirthDate(String patientBirthDate) {
    this.patientBirthDate = patientBirthDate;
  }

  public String getPatientBirthTime() {
    return patientBirthTime;
  }

  public void setPatientBirthTime(String patientBirthTime) {
    this.patientBirthTime = patientBirthTime;
  }

  public String getPatientSex() {
    return patientSex;
  }

  /**
   * Sets the patient sex using DICOM standard values (M, F, O). Input values starting with 'M' map
   * to 'M', starting with 'F' map to 'F', and all others map to 'O' (Other).
   *
   * @param patientSex the patient sex value, may be null
   */
  public void setPatientSex(String patientSex) {
    this.patientSex = PatientSex.normalize(patientSex);
  }

  /**
   * Adds a study to this patient.
   *
   * @param study the study to add, ignored if null
   */
  public void addStudy(Study study) {
    if (study != null) {
      studiesMap.put(study.getStudyInstanceUID(), study);
    }
  }

  /**
   * Removes and returns a study by UID.
   *
   * @param studyUID the Study Instance UID, required
   * @return the removed study, or null if not found
   */
  public Study removeStudy(String studyUID) {
    return studiesMap.remove(studyUID);
  }

  /**
   * Retrieves a study by UID.
   *
   * @param studyUID the Study Instance UID, required
   * @return the matching study, or null if not found
   */
  public Study getStudy(String studyUID) {
    return studiesMap.get(studyUID);
  }

  /**
   * Returns all studies for this patient.
   *
   * @return collection of studies
   */
  public Collection<Study> getStudies() {
    return studiesMap.values();
  }

  /**
   * Returns the entry set of the studies map for iteration.
   *
   * @return set of map entries containing key-value pairs
   */
  public Set<Entry<String, Study>> getEntrySet() {
    return studiesMap.entrySet();
  }

  /**
   * Checks if this patient contains any non-empty studies.
   *
   * @return true if all studies are empty or no studies exist, false otherwise
   */
  public boolean isEmpty() {
    return studiesMap.values().stream().allMatch(Study::isEmpty);
  }

  @Override
  public void toXml(Writer writer) throws IOException {
    writePatientStart(writer);
    writeStudiesInOrder(writer);
    writePatientEnd(writer);
  }

  @Override
  public int compareTo(Patient other) {
    return this.patientName.compareTo(other.patientName);
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) return true;
    if (obj == null || getClass() != obj.getClass()) return false;

    var other = (Patient) obj;
    return Objects.equals(patientID, other.patientID)
        && Objects.equals(issuerOfPatientID, other.issuerOfPatientID);
  }

  @Override
  public int hashCode() {
    return Objects.hash(patientID, issuerOfPatientID);
  }

  // Writes the opening patient tag with attributes
  private void writePatientStart(Writer writer) throws IOException {
    writer.append("\n<").append(Xml.Level.PATIENT.getTagName()).append(" ");

    Xml.addXmlAttribute(Tag.PatientID, patientID, writer);
    Xml.addXmlAttribute(Tag.IssuerOfPatientID, issuerOfPatientID, writer);
    Xml.addXmlAttribute(Tag.PatientName, patientName, writer);
    Xml.addXmlAttribute(Tag.PatientBirthDate, patientBirthDate, writer);
    Xml.addXmlAttribute(Tag.PatientBirthTime, patientBirthTime, writer);
    Xml.addXmlAttribute(Tag.PatientSex, patientSex, writer);

    writer.append(">");
  }

  // Writes all studies in sorted order
  private void writeStudiesInOrder(Writer writer) throws IOException {
    var sortedStudies = new ArrayList<>(studiesMap.values());
    Collections.sort(sortedStudies);

    for (Study study : sortedStudies) {
      study.toXml(writer);
    }
  }

  // Writes the closing patient tag
  private void writePatientEnd(Writer writer) throws IOException {
    writer.append("\n</").append(Xml.Level.PATIENT.getTagName()).append(">");
  }
}
