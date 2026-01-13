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

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.weasis.core.util.LangUtil;

/**
 * Abstract base implementation of {@link QueryResult} providing common functionality for managing
 * DICOM patient data in a hierarchical structure. This class handles the core operations for
 * patient management, study filtering, and data cleanup.
 *
 * <p>Thread-safe operations are provided through synchronized access to the patient map when
 * necessary. Implementations should extend this class to provide specific WADO parameter handling.
 */
public abstract class AbstractQueryResult implements QueryResult {

  protected final Map<String, Patient> patientMap;
  protected ViewerMessage viewerMessage;

  protected AbstractQueryResult() {
    this(null);
  }

  protected AbstractQueryResult(Collection<Patient> patients) {
    this.patientMap = new ConcurrentHashMap<>();
    Iterable<Patient> safePatients = LangUtil.emptyIfNull(patients);
    safePatients.forEach(this::addPatient);
  }

  @Override
  public void removePatientId(List<String> patientIdList, boolean containsIssuer) {
    if (patientIdList == null || patientIdList.isEmpty()) {
      return;
    }
    if (containsIssuer) {
      patientIdList.forEach(patientMap::remove);
    } else {
      removePatientsByIdWithoutIssuer(patientIdList);
    }
  }

  @Override
  public void removeStudyUid(List<String> studyUidList) {
    if (patientUidListIsEmpty(studyUidList)) {
      return;
    }
    patientMap.values().forEach(patient -> studyUidList.forEach(patient::removeStudy));
    removeItemsWithoutElements();
  }

  @Override
  public void removeAccessionNumber(List<String> accessionNumberList) {
    if (patientUidListIsEmpty(accessionNumberList)) {
      return;
    }
    Set<String> accessionSet = Set.copyOf(accessionNumberList);
    patientMap
        .values()
        .forEach(
            patient ->
                patient
                    .getEntrySet()
                    .removeIf(
                        entry -> !accessionSet.contains(entry.getValue().getAccessionNumber())));
    removeItemsWithoutElements();
  }

  @Override
  public void removeSeriesUid(List<String> seriesUidList) {
    if (patientUidListIsEmpty(seriesUidList)) {
      return;
    }
    patientMap
        .values()
        .forEach(
            patient ->
                patient.getStudies().forEach(study -> seriesUidList.forEach(study::removeSeries)));
    removeItemsWithoutElements();
  }

  @Override
  public void removeItemsWithoutElements() {
    patientMap.values().forEach(this::removeEmptyStudiesAndSeries);
    patientMap.entrySet().removeIf(entry -> entry.getValue().isEmpty());
  }

  /**
   * Adds a patient to this query result.
   *
   * @param patient the patient to add, ignored if null
   */
  public void addPatient(Patient patient) {
    if (patient != null) {
      patientMap.put(patient.getPseudoPatientUID(), patient);
    }
  }

  /**
   * Removes and returns a patient by ID and optional issuer.
   *
   * @param patientID the patient ID, required
   * @param issuerOfPatientID the issuer of patient ID, optional
   * @return the removed patient, or null if not found or patientID is null
   */
  public Patient removePatient(String patientID, String issuerOfPatientID) {
    String key = buildPatientKey(patientID, issuerOfPatientID);
    return key != null ? patientMap.remove(key) : null;
  }

  /**
   * Retrieves a patient by ID and optional issuer.
   *
   * @param patientID the patient ID, required
   * @param issuerOfPatientID the issuer of patient ID, optional
   * @return the patient, or null if not found or patientID is null
   */
  public Patient getPatient(String patientID, String issuerOfPatientID) {
    String key = buildPatientKey(patientID, issuerOfPatientID);
    return key != null ? patientMap.get(key) : null;
  }

  @Override
  public Map<String, Patient> getPatients() {
    return Collections.unmodifiableMap(patientMap);
  }

  @Override
  public ViewerMessage getViewerMessage() {
    return viewerMessage;
  }

  @Override
  public void setViewerMessage(ViewerMessage viewerMessage) {
    this.viewerMessage = viewerMessage;
  }

  // Finds and removes patients by ID when issuer information is not included
  private void removePatientsByIdWithoutIssuer(List<String> patientIdList) {
    List<String> pseudoUidsToRemove =
        patientIdList.stream()
            .map(this::findPseudoUidForPatientId)
            .filter(Objects::nonNull)
            .toList();

    pseudoUidsToRemove.forEach(patientMap::remove);
  }

  // Finds the pseudo UID for a given patient ID
  private String findPseudoUidForPatientId(String patientID) {
    return patientMap.values().stream()
        .filter(patient -> patientID.equals(patient.getPatientID()))
        .map(Patient::getPseudoPatientUID)
        .findFirst()
        .orElse(null);
  }

  // Removes empty studies and series from a patient

  private void removeEmptyStudiesAndSeries(Patient patient) {
    patient
        .getEntrySet()
        .removeIf(
            studyEntry -> {
              Study study = studyEntry.getValue();
              study.getEntrySet().removeIf(seriesEntry -> seriesEntry.getValue().isEmpty());
              return study.isEmpty();
            });
  }

  // Builds a patient key from ID and optional issuer
  private String buildPatientKey(String patientID, String issuerOfPatientID) {
    if (patientID == null) {
      return null;
    }
    return issuerOfPatientID != null ? patientID + issuerOfPatientID : patientID;
  }

  // Checks if a UID list is null or empty
  private boolean patientUidListIsEmpty(List<String> uidList) {
    return uidList == null || uidList.isEmpty();
  }
}
