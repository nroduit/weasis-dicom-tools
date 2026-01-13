/*
 * Copyright (c) 2017-2019 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.mf;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * Defines the contract for DICOM query results, providing operations for managing patient data,
 * studies, series, and associated metadata. Implementations handle the hierarchical structure of
 * DICOM data and provide filtering capabilities.
 *
 * <p>The query result maintains a patient-centric view where each patient contains studies, which
 * in turn contain series of DICOM instances.
 */
public interface QueryResult {

  /**
   * Returns the WADO parameters associated with this query result.
   *
   * @return the WADO parameters for web access to DICOM objects
   */
  WadoParameters getWadoParameters();

  /**
   * Removes patients from the result based on their patient IDs.
   *
   * @param patientIdList the list of patient IDs to remove
   * @param containsIssuer whether the patient IDs include issuer information
   */
  void removePatientId(List<String> patientIdList, boolean containsIssuer);

  /**
   * Removes studies from all patients based on study instance UIDs.
   *
   * @param studyUidList the list of study UIDs to remove
   */
  void removeStudyUid(List<String> studyUidList);

  /**
   * Removes studies based on accession numbers, keeping only studies with matching accession
   * numbers.
   *
   * @param accessionNumberList the list of accession numbers to keep
   */
  void removeAccessionNumber(List<String> accessionNumberList);

  /**
   * Removes series from all studies based on series instance UIDs.
   *
   * @param seriesUidList the list of series UIDs to remove
   */
  void removeSeriesUid(List<String> seriesUidList);

  /**
   * Removes empty containers from the hierarchy. This includes patients without studies, studies
   * without series, and series without instances.
   */
  void removeItemsWithoutElements();

  /**
   * Returns all patients in this query result.
   *
   * @return an unmodifiable map of patient pseudo-UID to patient objects
   */
  Map<String, Patient> getPatients();

  /**
   * Returns the viewer message associated with this query result.
   *
   * @return the viewer message, or {@code null} if none is set
   */
  ViewerMessage getViewerMessage();

  /**
   * Sets the viewer message for this query result.
   *
   * @param viewerMessage the viewer message to set
   */
  void setViewerMessage(ViewerMessage viewerMessage);

  /**
   * Convenience method to remove patients by ID without issuer information.
   *
   * @param patientIdList the list of patient IDs to remove
   * @since 5.34.0.3
   */
  default void removePatientId(Collection<String> patientIdList) {
    if (patientIdList instanceof List<String> list) {
      removePatientId(list, false);
    } else {
      removePatientId(List.copyOf(patientIdList), false);
    }
  }

  /**
   * Convenience method to remove study UIDs from a collection.
   *
   * @param studyUidCollection the collection of study UIDs to remove
   * @since 5.34.0.3
   */
  default void removeStudyUid(Collection<String> studyUidCollection) {
    if (studyUidCollection instanceof List<String> list) {
      removeStudyUid(list);
    } else {
      removeStudyUid(List.copyOf(studyUidCollection));
    }
  }

  /**
   * Convenience method to remove series UIDs from a collection.
   *
   * @param seriesUidCollection the collection of series UIDs to remove
   * @since 5.34.0.3
   */
  default void removeSeriesUid(Collection<String> seriesUidCollection) {
    if (seriesUidCollection instanceof List<String> list) {
      removeSeriesUid(list);
    } else {
      removeSeriesUid(List.copyOf(seriesUidCollection));
    }
  }

  /**
   * Convenience method to filter by accession numbers from a collection.
   *
   * @param accessionNumberCollection the collection of accession numbers to keep
   * @since 5.34.0.3
   */
  default void removeAccessionNumber(Collection<String> accessionNumberCollection) {
    if (accessionNumberCollection instanceof List<String> list) {
      removeAccessionNumber(list);
    } else {
      removeAccessionNumber(List.copyOf(accessionNumberCollection));
    }
  }

  /**
   * Returns whether this query result contains any patients.
   *
   * @return {@code true} if this result contains patients, {@code false} otherwise
   * @since 5.34.0.3
   */
  default boolean hasPatients() {
    Map<String, Patient> patients = getPatients();
    return patients != null && !patients.isEmpty();
  }

  /**
   * Returns the number of patients in this query result.
   *
   * @return the patient count
   * @since 5.34.0.3
   */
  default int getPatientCount() {
    Map<String, Patient> patients = getPatients();
    return patients != null ? patients.size() : 0;
  }
}
