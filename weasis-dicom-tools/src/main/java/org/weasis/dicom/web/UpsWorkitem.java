/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.web;

import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Code;
import org.dcm4che3.data.Sequence;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.VR;
import org.dcm4che3.util.DateUtils;
import org.dcm4che3.util.TagUtils;

/**
 * Builds the data set of a Create Workitem request (PS3.18 §11.4): the Type 1 and Type 2 attributes
 * of the UPS N-CREATE (PS3.4 Table CC.2.5-3) and what an IHE AIW-I Task Requestor must send (RAD-80
 * §4.80.4.1.2.1 and §4.80.4.1.2.4): one Scheduled Workitem Code, at least one input instance, the
 * Input Readiness State READY, a priority and the Study Instance UID of the study receiving the
 * results.
 *
 * <p>Typical use: {@code UpsWorkitem.of(code).patient(study).request(study).inputs(instances)
 * .build()}, where {@code study} and {@code instances} are the attributes Weasis holds for the
 * loaded series. The SOP Instance UID of the workitem is not part of the data set: it goes in the
 * {@code workitem} query parameter of {@link UpsRS#createWorkitem}.
 */
public final class UpsWorkitem {

  /** Scheduled Procedure Step Priority (0074,1200). */
  public enum Priority {
    HIGH,
    MEDIUM,
    LOW
  }

  /** Coding Scheme Designator of a station named by its AE Title (AIW-I §4.80.4.1.2.1). */
  static final String LOCAL_SCHEME = "L";

  private static final String DICOM_INSTANCES = "DICOM";
  private static final String INSTANCES_PATH = "/instances/";

  private final Attributes attrs = new Attributes();
  private final Map<String, Attributes> inputs = new LinkedHashMap<>();
  private final Code code;
  private String label;

  private UpsWorkitem(Code code) {
    this.code = Objects.requireNonNull(code, "Workitem code cannot be null");
  }

  /**
   * @param code the Scheduled Workitem Code identifying the task, e.g. one of CID 9231 or a
   *     site-configured code
   */
  public static UpsWorkitem of(Code code) {
    return new UpsWorkitem(code);
  }

  /** Procedure Step Label; the code meaning when not set. */
  public UpsWorkitem label(String label) {
    this.label = label;
    return this;
  }

  public UpsWorkitem priority(Priority priority) {
    attrs.setString(
        Tag.ScheduledProcedureStepPriority, VR.CS, Objects.requireNonNull(priority).name());
    return this;
  }

  public UpsWorkitem worklistLabel(String worklistLabel) {
    attrs.setString(Tag.WorklistLabel, VR.LO, worklistLabel);
    return this;
  }

  /** Scheduled Procedure Step Start DateTime; now when not set. */
  public UpsWorkitem start(Date start) {
    attrs.setString(
        Tag.ScheduledProcedureStepStartDateTime, VR.DT, DateUtils.formatDT(null, start));
    return this;
  }

  public UpsWorkitem expectedCompletion(Date expectedCompletion) {
    attrs.setString(
        Tag.ExpectedCompletionDateTime, VR.DT, DateUtils.formatDT(null, expectedCompletion));
    return this;
  }

  public UpsWorkitem comments(String comments) {
    attrs.setString(Tag.CommentsOnTheScheduledProcedureStep, VR.LT, comments);
    return this;
  }

  /** Copies the patient and visit identification from the attributes of a study or an instance. */
  public UpsWorkitem patient(Attributes source) {
    adoptCharacterSet(source);
    attrs.addSelected(
        source,
        Tag.PatientName,
        Tag.PatientID,
        Tag.IssuerOfPatientID,
        Tag.IssuerOfPatientIDQualifiersSequence,
        Tag.OtherPatientIDsSequence,
        Tag.PatientBirthDate,
        Tag.PatientSex,
        Tag.AdmissionID,
        Tag.IssuerOfAdmissionIDSequence,
        Tag.AdmittingDiagnosesDescription,
        Tag.AdmittingDiagnosesCodeSequence);
    return this;
  }

  /**
   * Sets the Study Instance UID of the study receiving the results and the Referenced Request
   * Sequence item that lets the workitem be found again by Accession Number or Study UID.
   */
  public UpsWorkitem request(Attributes study) {
    String studyUid = study.getString(Tag.StudyInstanceUID);
    if (studyUid == null) {
      throw new IllegalArgumentException("The study has no Study Instance UID");
    }
    adoptCharacterSet(study);
    attrs.setString(Tag.StudyInstanceUID, VR.UI, studyUid);
    // Added to its parent first so that the character set of the workitem applies to the copy
    Attributes item = new Attributes();
    attrs.newSequence(Tag.ReferencedRequestSequence, 1).add(item);
    item.setString(Tag.StudyInstanceUID, VR.UI, studyUid);
    item.addSelected(
        study,
        Tag.AccessionNumber,
        Tag.IssuerOfAccessionNumberSequence,
        Tag.RequestedProcedureID,
        Tag.RequestedProcedureDescription,
        Tag.RequestedProcedureCodeSequence);
    setEmptyIfAbsent(item, Tag.ReferencedStudySequence);
    setEmptyIfAbsent(item, Tag.AccessionNumber, VR.SH);
    setEmptyIfAbsent(item, Tag.RequestedProcedureID, VR.SH);
    setEmptyIfAbsent(item, Tag.RequestedProcedureDescription, VR.LO);
    setEmptyIfAbsent(item, Tag.RequestedProcedureCodeSequence);
    return this;
  }

  // The Specific Character Set (Type 1C) of the source, so that its strings can be copied
  private void adoptCharacterSet(Attributes source) {
    String[] charset = source.getStrings(Tag.SpecificCharacterSet);
    if (charset != null && charset.length > 0 && !attrs.contains(Tag.SpecificCharacterSet)) {
      attrs.setString(Tag.SpecificCharacterSet, VR.CS, charset);
    }
  }

  /**
   * Adds an input instance to the Input Information Sequence, one item per series. A Retrieve URL
   * (0008,1190) on the instance becomes the WADO-RS Retrieval Sequence of its series, cut at the
   * instance path; a Retrieve AE Title (0008,0054) becomes its DICOM Retrieval Sequence.
   */
  public UpsWorkitem input(Attributes instance) {
    String studyUid = required(instance, Tag.StudyInstanceUID);
    String seriesUid = required(instance, Tag.SeriesInstanceUID);
    Attributes series =
        inputs.computeIfAbsent(studyUid + "/" + seriesUid, k -> seriesItem(studyUid, seriesUid));
    Attributes ref = new Attributes(2);
    ref.setString(Tag.ReferencedSOPClassUID, VR.UI, required(instance, Tag.SOPClassUID));
    ref.setString(Tag.ReferencedSOPInstanceUID, VR.UI, required(instance, Tag.SOPInstanceUID));
    series.getSequence(Tag.ReferencedSOPSequence).add(ref);
    String url = instance.getString(Tag.RetrieveURL);
    if (url != null && !series.contains(Tag.WADORSRetrievalSequence)) {
      Attributes retrieval = new Attributes(1);
      retrieval.setString(Tag.RetrieveURL, VR.UR, seriesUrl(url));
      series.newSequence(Tag.WADORSRetrievalSequence, 1).add(retrieval);
    }
    String aet = instance.getString(Tag.RetrieveAETitle);
    if (aet != null && !series.contains(Tag.DICOMRetrievalSequence)) {
      Attributes retrieval = new Attributes(1);
      retrieval.setString(Tag.RetrieveAETitle, VR.AE, aet);
      series.newSequence(Tag.DICOMRetrievalSequence, 1).add(retrieval);
    }
    return this;
  }

  public UpsWorkitem inputs(Iterable<Attributes> instances) {
    instances.forEach(this::input);
    return this;
  }

  /**
   * Addresses the workitem to a Task Performer by its AE Title (Scheduled Station Name Code
   * Sequence, AIW-I §4.80.4.1.2.1); normally left to the Task Manager.
   */
  public UpsWorkitem station(String aeTitle, String name) {
    Objects.requireNonNull(aeTitle, "AE Title cannot be null");
    Code station = new Code(aeTitle, LOCAL_SCHEME, null, name == null ? aeTitle : name);
    attrs.newSequence(Tag.ScheduledStationNameCodeSequence, 1).add(station.toItem());
    return this;
  }

  /** Requests the results to be stored to a DICOM AE (Output Destination Sequence). */
  public UpsWorkitem outputDestination(String aeTitle) {
    Attributes storage = new Attributes(1);
    storage.setString(Tag.DestinationAE, VR.AE, Objects.requireNonNull(aeTitle));
    Attributes item = new Attributes(1);
    item.newSequence(Tag.DICOMStorageSequence, 1).add(storage);
    outputDestinations().add(item);
    return this;
  }

  /** Requests the results to be stored with STOW-RS at that URL (Output Destination Sequence). */
  public UpsWorkitem outputDestinationUrl(String storageUrl) {
    Attributes storage = new Attributes(1);
    storage.setString(Tag.StorageURL, VR.UR, Objects.requireNonNull(storageUrl));
    Attributes item = new Attributes(1);
    item.newSequence(Tag.STOWRSStorageSequence, 1).add(storage);
    outputDestinations().add(item);
    return this;
  }

  /**
   * The data set, with the Type 1 defaults filled (SCHEDULED, start now, priority MEDIUM, readiness
   * READY, label from the code) and every Type 2 attribute present, empty when unknown.
   *
   * @throws IllegalStateException without any input instance
   */
  public Attributes build() {
    if (inputs.isEmpty()) {
      throw new IllegalStateException("A workitem needs at least one input instance");
    }
    Attributes result = new Attributes(attrs);
    result.setString(Tag.ProcedureStepState, VR.CS, ProcedureStepState.SCHEDULED.code());
    result.setString(Tag.InputReadinessState, VR.CS, "READY");
    result.setString(Tag.ProcedureStepLabel, VR.LO, label != null ? label : code.getCodeMeaning());
    setIfAbsent(result, Tag.ScheduledProcedureStepPriority, VR.CS, Priority.MEDIUM.name());
    setIfAbsent(
        result,
        Tag.ScheduledProcedureStepStartDateTime,
        VR.DT,
        DateUtils.formatDT(null, new Date()));
    result.newSequence(Tag.ScheduledWorkitemCodeSequence, 1).add(code.toItem());
    Sequence inputSequence = result.newSequence(Tag.InputInformationSequence, inputs.size());
    inputs.values().forEach(item -> inputSequence.add(new Attributes(item)));
    // Type 2 of the N-CREATE: present, empty when the requester knows nothing
    setEmptyIfAbsent(result, Tag.TransactionUID, VR.UI);
    setEmptyIfAbsent(result, Tag.WorklistLabel, VR.LO);
    setEmptyIfAbsent(result, Tag.ScheduledProcessingParametersSequence);
    setEmptyIfAbsent(result, Tag.ScheduledStationNameCodeSequence);
    setEmptyIfAbsent(result, Tag.ScheduledStationClassCodeSequence);
    setEmptyIfAbsent(result, Tag.ScheduledStationGeographicLocationCodeSequence);
    setEmptyIfAbsent(result, Tag.ScheduledHumanPerformersSequence);
    setEmptyIfAbsent(result, Tag.CommentsOnTheScheduledProcedureStep, VR.LT);
    setEmptyIfAbsent(result, Tag.PatientName, VR.PN);
    setEmptyIfAbsent(result, Tag.IssuerOfPatientID, VR.LO);
    setEmptyIfAbsent(result, Tag.IssuerOfPatientIDQualifiersSequence);
    setEmptyIfAbsent(result, Tag.OtherPatientIDsSequence);
    setEmptyIfAbsent(result, Tag.PatientBirthDate, VR.DA);
    setEmptyIfAbsent(result, Tag.PatientSex, VR.CS);
    setEmptyIfAbsent(result, Tag.AdmissionID, VR.LO);
    setEmptyIfAbsent(result, Tag.IssuerOfAdmissionIDSequence);
    setEmptyIfAbsent(result, Tag.AdmittingDiagnosesDescription, VR.LO);
    setEmptyIfAbsent(result, Tag.AdmittingDiagnosesCodeSequence);
    setEmptyIfAbsent(result, Tag.ReferencedRequestSequence);
    setEmptyIfAbsent(result, Tag.ProcedureStepProgressInformationSequence);
    setEmptyIfAbsent(result, Tag.UnifiedProcedureStepPerformedProcedureSequence);
    return result;
  }

  private Sequence outputDestinations() {
    return attrs.ensureSequence(Tag.OutputDestinationSequence, 1);
  }

  private static Attributes seriesItem(String studyUid, String seriesUid) {
    Attributes item = new Attributes();
    item.setString(Tag.TypeOfInstances, VR.CS, DICOM_INSTANCES);
    item.setString(Tag.StudyInstanceUID, VR.UI, studyUid);
    item.setString(Tag.SeriesInstanceUID, VR.UI, seriesUid);
    item.newSequence(Tag.ReferencedSOPSequence, 4);
    return item;
  }

  /** The series resource of a WADO-RS instance URL, the URL itself otherwise. */
  static String seriesUrl(String url) {
    int at = url.indexOf(INSTANCES_PATH);
    return at < 0 ? url : url.substring(0, at);
  }

  private static String required(Attributes attrs, int tag) {
    String value = attrs.getString(tag);
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException(
          "Missing " + TagUtils.toString(tag) + " on the input instance");
    }
    return value;
  }

  private static void setIfAbsent(Attributes attrs, int tag, VR vr, String value) {
    if (!attrs.containsValue(tag)) {
      attrs.setString(tag, vr, value);
    }
  }

  private static void setEmptyIfAbsent(Attributes attrs, int tag, VR vr) {
    if (!attrs.contains(tag)) {
      attrs.setNull(tag, vr);
    }
  }

  private static void setEmptyIfAbsent(Attributes attrs, int sequenceTag) {
    if (!attrs.contains(sequenceTag)) {
      attrs.newSequence(sequenceTag, 0);
    }
  }

  /** The items a builder accumulated, for tests. */
  List<Attributes> inputItems() {
    return List.copyOf(inputs.values());
  }
}
