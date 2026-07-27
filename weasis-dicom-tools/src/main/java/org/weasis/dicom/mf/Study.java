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

import java.io.IOException;
import java.text.Collator;
import java.time.DateTimeException;
import java.time.LocalDateTime;
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
import org.dcm4che3.img.util.DateTimeUtils;
import org.dcm4che3.img.util.DicomObjectUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Represents a DICOM study within a patient, containing multiple series. Provides functionality for
 * XML serialization, series management, and study comparison.
 *
 * <p>A study represents a single imaging procedure or examination performed on a patient, typically
 * at a specific date and time. Studies contain one or more series of images or other DICOM objects.
 */
public class Study implements ManifestNode, Comparable<Study> {
  private static final Logger LOGGER = LoggerFactory.getLogger(Study.class);

  // DICOM keywords resolved once (the element names are constant per tag).
  private static final String KEY_STUDY_INSTANCE_UID = ManifestNode.keyword(Tag.StudyInstanceUID);
  private static final String KEY_STUDY_DESCRIPTION = ManifestNode.keyword(Tag.StudyDescription);
  private static final String KEY_STUDY_DATE = ManifestNode.keyword(Tag.StudyDate);
  private static final String KEY_STUDY_TIME = ManifestNode.keyword(Tag.StudyTime);
  private static final String KEY_ACCESSION_NUMBER = ManifestNode.keyword(Tag.AccessionNumber);
  private static final String KEY_STUDY_ID = ManifestNode.keyword(Tag.StudyID);
  private static final String KEY_REFERRING_PHYSICIAN_NAME =
      ManifestNode.keyword(Tag.ReferringPhysicianName);

  private final String studyInstanceUID;
  private final Map<String, Series> seriesMap;

  private String studyID;
  private String studyDescription;
  private String studyDate;
  private String studyTime;
  private String accessionNumber;
  private String referringPhysicianName;

  /**
   * Creates a study with the specified instance UID.
   *
   * @param studyInstanceUID the DICOM Study Instance UID, required
   * @throws NullPointerException if studyInstanceUID is null
   */
  public Study(String studyInstanceUID) {
    this.studyInstanceUID =
        Objects.requireNonNull(studyInstanceUID, "Study Instance UID cannot be null");
    this.seriesMap = new HashMap<>();
  }

  public String getStudyInstanceUID() {
    return studyInstanceUID;
  }

  public String getStudyID() {
    return studyID;
  }

  public void setStudyID(String studyID) {
    this.studyID = studyID;
  }

  public String getStudyDescription() {
    return studyDescription;
  }

  public void setStudyDescription(String studyDescription) {
    this.studyDescription = studyDescription;
  }

  public String getStudyDate() {
    return studyDate;
  }

  public void setStudyDate(String studyDate) {
    this.studyDate = studyDate;
  }

  public String getStudyTime() {
    return studyTime;
  }

  public void setStudyTime(String studyTime) {
    this.studyTime = studyTime;
  }

  public String getAccessionNumber() {
    return accessionNumber;
  }

  public void setAccessionNumber(String accessionNumber) {
    this.accessionNumber = accessionNumber;
  }

  public String getReferringPhysicianName() {
    return referringPhysicianName;
  }

  public void setReferringPhysicianName(String referringPhysicianName) {
    this.referringPhysicianName = referringPhysicianName;
  }

  /**
   * Adds a series to this study.
   *
   * @param series the series to add, ignored if null
   */
  public void addSeries(Series series) {
    if (series != null) {
      seriesMap.put(series.getSeriesInstanceUID(), series);
    }
  }

  /**
   * Removes and returns a series by UID.
   *
   * @param seriesUID the Series Instance UID, required
   * @return the removed series, or null if not found
   */
  public Series removeSeries(String seriesUID) {
    return seriesMap.remove(seriesUID);
  }

  /**
   * Retrieves a series by UID.
   *
   * @param seriesUID the Series Instance UID, required
   * @return the matching series, or null if not found
   */
  public Series getSeries(String seriesUID) {
    return seriesMap.get(seriesUID);
  }

  /**
   * Returns all series in this study.
   *
   * @return collection of series
   */
  public Collection<Series> getSeries() {
    return seriesMap.values();
  }

  /**
   * Returns the entry set of the series map for iteration.
   *
   * @return set of map entries containing key-value pairs
   */
  public Set<Entry<String, Series>> getEntrySet() {
    return seriesMap.entrySet();
  }

  /**
   * Checks if this study contains any non-empty series.
   *
   * @return true if all series are empty or no series exist, false otherwise
   */
  public boolean isEmpty() {
    return seriesMap.values().stream().allMatch(Series::isEmpty);
  }

  @Override
  public void write(ManifestSerializer serializer) throws IOException {
    serializer.beginObject(ManifestNode.Level.STUDY.getTagName());
    serializer.attribute(KEY_STUDY_INSTANCE_UID, studyInstanceUID);
    serializer.attribute(KEY_STUDY_DESCRIPTION, studyDescription);
    serializer.attribute(KEY_STUDY_DATE, studyDate);
    serializer.attribute(KEY_STUDY_TIME, studyTime);
    serializer.attribute(KEY_ACCESSION_NUMBER, accessionNumber);
    serializer.attribute(KEY_STUDY_ID, studyID);
    serializer.attribute(KEY_REFERRING_PHYSICIAN_NAME, referringPhysicianName);
    writeSeriesInOrder(serializer);
    serializer.endObject();
  }

  @Override
  public int compareTo(Study other) {
    // Primary comparison: study datetime (most recent first)
    int datetimeComparison = compareStudyDateTimes(this, other);
    if (datetimeComparison != 0) {
      return datetimeComparison;
    }

    // Secondary comparison: study description
    return compareStudyDescriptions(this.studyDescription, other.studyDescription);
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) return true;
    if (obj == null || getClass() != obj.getClass()) return false;

    var other = (Study) obj;
    return studyInstanceUID.equals(other.studyInstanceUID);
  }

  @Override
  public int hashCode() {
    return Objects.hash(studyInstanceUID);
  }

  // Writes all series in sorted order, wrapped in an array
  private void writeSeriesInOrder(ManifestSerializer serializer) throws IOException {
    if (seriesMap.isEmpty()) {
      return;
    }
    var sortedSeries = new ArrayList<>(seriesMap.values());
    Collections.sort(sortedSeries);

    serializer.beginArray(ManifestNode.Level.SERIES.getTagName());
    for (Series series : sortedSeries) {
      series.write(serializer);
    }
    serializer.endArray();
  }

  // Compares study date/time with null-safe logic (most recent first)
  private static int compareStudyDateTimes(Study study1, Study study2) {
    LocalDateTime datetime1 = parseStudyDateTime(study1.studyDate, study1.studyTime);
    LocalDateTime datetime2 = parseStudyDateTime(study2.studyDate, study2.studyTime);

    if (datetime1 != null && datetime2 != null) {
      return datetime2.compareTo(datetime1); // Reverse order - most recent first
    }
    if (datetime1 == null && datetime2 == null) {
      return 0; // Both null, continue to description comparison
    }

    // Null datetime sorted after non-null datetime
    return datetime1 == null ? 1 : -1;
  }

  // Compares study descriptions with locale-aware collation
  private static int compareStudyDescriptions(String desc1, String desc2) {
    if (desc1 != null && desc2 != null) {
      return Collator.getInstance(Locale.ROOT).compare(desc1, desc2);
    }
    if (desc1 == null && desc2 == null) {
      return 0; // Both null
    }
    // Null descriptions sorted after non-null descriptions
    return desc1 == null ? 1 : -1;
  }

  private static LocalDateTime parseStudyDateTime(String studyDate, String studyTime) {
    try {
      return DateTimeUtils.dateTime(
          DicomObjectUtil.getDicomDate(studyDate), DicomObjectUtil.getDicomTime(studyTime));
    } catch (DateTimeException | NumberFormatException e) {
      LOGGER.debug("Cannot parse study date/time: {} / {}", studyDate, studyTime);
      return null;
    }
  }
}
