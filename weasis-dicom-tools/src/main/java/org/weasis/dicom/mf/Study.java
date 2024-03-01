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
import java.io.Writer;
import java.text.Collator;
import java.time.LocalDateTime;
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
import org.weasis.dicom.util.DateUtil;

public class Study implements Xml, Comparable<Study> {

  private final String studyInstanceUID;
  private String studyID = null;
  private String studyDescription = null;
  private String studyDate = null;
  private String studyTime = null;
  private String accessionNumber = null;
  private String referringPhysicianName = null;
  private final Map<String, Series> seriesMap;

  public Study(String studyInstanceUID) {
    this.studyInstanceUID =
        Objects.requireNonNull(studyInstanceUID, "studyInstanceUID cannot be null!");
    this.seriesMap = new HashMap<>();
  }

  public String getStudyInstanceUID() {
    return studyInstanceUID;
  }

  public String getStudyDescription() {
    return studyDescription;
  }

  public String getStudyDate() {
    return studyDate;
  }

  public String getStudyID() {
    return studyID;
  }

  public void setStudyID(String studyID) {
    this.studyID = studyID;
  }

  public String getStudyTime() {
    return studyTime;
  }

  public void setStudyTime(String studyTime) {
    this.studyTime = studyTime;
  }

  public String getReferringPhysicianName() {
    return referringPhysicianName;
  }

  public void setReferringPhysicianName(String referringPhysicianName) {
    this.referringPhysicianName = referringPhysicianName;
  }

  public void setStudyDescription(String studyDesc) {
    this.studyDescription = studyDesc;
  }

  public void setStudyDate(String studyDate) {
    this.studyDate = studyDate;
  }

  public String getAccessionNumber() {
    return accessionNumber;
  }

  public void setAccessionNumber(String accessionNumber) {
    this.accessionNumber = accessionNumber;
  }

  public void addSeries(Series s) {
    if (s != null) {
      seriesMap.put(s.getSeriesInstanceUID(), s);
    }
  }

  public Series removeSeries(String seriesUID) {
    return seriesMap.remove(seriesUID);
  }

  public boolean isEmpty() {
    for (Series s : seriesMap.values()) {
      if (!s.isEmpty()) {
        return false;
      }
    }
    return true;
  }

  public Series getSeries(String seriesUID) {
    return seriesMap.get(seriesUID);
  }

  public Collection<Series> getSeries() {
    return seriesMap.values();
  }

  public Set<Entry<String, Series>> getEntrySet() {
    return seriesMap.entrySet();
  }

  @Override
  public int hashCode() {
    return 31 + studyInstanceUID.hashCode();
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) return true;
    if (obj == null) return false;
    if (getClass() != obj.getClass()) return false;
    Study other = (Study) obj;
    return studyInstanceUID.equals(other.studyInstanceUID);
  }

  @Override
  public void toXml(Writer result) throws IOException {
    if (studyInstanceUID != null) {
      result.append("\n<");
      result.append(Xml.Level.STUDY.getTagName());
      result.append(" ");
      Xml.addXmlAttribute(Tag.StudyInstanceUID, studyInstanceUID, result);
      Xml.addXmlAttribute(Tag.StudyDescription, studyDescription, result);
      Xml.addXmlAttribute(Tag.StudyDate, studyDate, result);
      Xml.addXmlAttribute(Tag.StudyTime, studyTime, result);
      Xml.addXmlAttribute(Tag.AccessionNumber, accessionNumber, result);
      Xml.addXmlAttribute(Tag.StudyID, studyID, result);
      Xml.addXmlAttribute(Tag.ReferringPhysicianName, referringPhysicianName, result);
      result.append(">");

      List<Series> list = new ArrayList<>(seriesMap.values());
      Collections.sort(list);
      for (Series s : list) {
        s.toXml(result);
      }

      result.append("\n</");
      result.append(Xml.Level.STUDY.getTagName());
      result.append(">");
    }
  }

  @Override
  public int compareTo(Study s) {
    LocalDateTime date1 =
        DateUtil.dateTime(
            DateUtil.getDicomDate(getStudyDate()), DateUtil.getDicomTime(getStudyTime()));
    LocalDateTime date2 =
        DateUtil.dateTime(
            DateUtil.getDicomDate(s.getStudyDate()), DateUtil.getDicomTime(s.getStudyTime()));

    int c = -1;
    if (date1 != null && date2 != null) {
      // inverse time
      c = date2.compareTo(date1);
      if (c != 0) {
        return c;
      }
    }

    if (c == 0 || (date1 == null && date2 == null)) {
      String d1 = getStudyDescription();
      String d2 = s.getStudyDescription();
      if (d1 != null && d2 != null) {
        c = Collator.getInstance(Locale.getDefault()).compare(d1, d2);
        if (c != 0) {
          return c;
        }
      }
      if (d1 == null) {
        // Add o1 after o2
        return d2 == null ? 0 : 1;
      }
      // Add o2 after o1
      return -1;
    } else {
      if (date1 == null) {
        // Add o1 after o2
        return 1;
      }
      return -1;
    }
  }
}
