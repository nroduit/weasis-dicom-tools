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
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Set;
import org.dcm4che3.data.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.util.StringUtil;

public class Series implements Xml, Comparable<Series> {
  private static final Logger LOGGER = LoggerFactory.getLogger(Series.class);

  private final String seriesInstanceUID;
  private final Map<String, SopInstance> sopInstanceMap;

  private String seriesDescription = null;
  private String modality = null;
  private String seriesNumber = null;
  private String wadoTransferSyntaxUID = null;
  // Image quality within the range 1 to 100, 100 being the best quality.
  private int wadoCompression = 0;
  private String thumbnail = null;

  public Series(String seriesInstanceUID) {
    this.seriesInstanceUID = Objects.requireNonNull(seriesInstanceUID, "seriesInstanceUID is null");
    this.sopInstanceMap = new HashMap<>();
  }

  public String getSeriesInstanceUID() {
    return seriesInstanceUID;
  }

  public String getSeriesDescription() {
    return seriesDescription;
  }

  public String getSeriesNumber() {
    return seriesNumber;
  }

  public void setSeriesNumber(String seriesNumber) {
    this.seriesNumber = StringUtil.hasText(seriesNumber) ? seriesNumber.trim() : null;
  }

  public String getWadoTransferSyntaxUID() {
    return wadoTransferSyntaxUID;
  }

  public void setWadoTransferSyntaxUID(String wadoTransferSyntaxUID) {
    this.wadoTransferSyntaxUID = wadoTransferSyntaxUID;
  }

  public int getWadoCompression() {
    return wadoCompression;
  }

  public void setWadoCompression(int wadoCompression) {
    this.wadoCompression = wadoCompression > 100 ? 100 : wadoCompression < 0 ? 0 : wadoCompression;
  }

  public void setWadoCompression(String wadoCompression) {
    try {
      setWadoCompression(Integer.parseInt(wadoCompression));
    } catch (NumberFormatException e) {
      LOGGER.warn("Invalid compression value: {}", wadoCompression);
    }
  }

  public void setSeriesDescription(String s) {
    seriesDescription = s;
  }

  public void addSopInstance(SopInstance s) {
    SopInstance.addSopInstance(sopInstanceMap, s);
  }

  public SopInstance removeSopInstance(String sopUID, Integer instanceNumber) {
    return SopInstance.removeSopInstance(sopInstanceMap, sopUID, instanceNumber);
  }

  public SopInstance getSopInstance(String sopUID, Integer instanceNumber) {
    return SopInstance.getSopInstance(sopInstanceMap, sopUID, instanceNumber);
  }

  public Set<Entry<String, SopInstance>> getEntrySet() {
    return sopInstanceMap.entrySet();
  }

  public String getModality() {
    return modality;
  }

  public void setModality(String modality) {
    this.modality = modality;
  }

  public String getThumbnail() {
    return thumbnail;
  }

  public void setThumbnail(String thumbnail) {
    this.thumbnail = thumbnail;
  }

  public Collection<SopInstance> getSopInstances() {
    return sopInstanceMap.values();
  }

  public boolean isEmpty() {
    return sopInstanceMap.isEmpty();
  }

  @Override
  public int hashCode() {
    return 31 + seriesInstanceUID.hashCode();
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) return true;
    if (obj == null) return false;
    if (getClass() != obj.getClass()) return false;
    Series other = (Series) obj;
    return seriesInstanceUID.equals(other.seriesInstanceUID);
  }

  @Override
  public void toXml(Writer result) throws IOException {
    if (seriesInstanceUID != null) {
      result.append("\n<");
      result.append(Xml.Level.SERIES.getTagName());
      result.append(" ");
      Xml.addXmlAttribute(Tag.SeriesInstanceUID, seriesInstanceUID, result);
      Xml.addXmlAttribute(Tag.SeriesDescription, seriesDescription, result);
      Xml.addXmlAttribute(Tag.SeriesNumber, seriesNumber, result);
      Xml.addXmlAttribute(Tag.Modality, modality, result);
      Xml.addXmlAttribute("DirectDownloadThumbnail", thumbnail, result);
      Xml.addXmlAttribute("WadoTransferSyntaxUID", wadoTransferSyntaxUID, result);
      Xml.addXmlAttribute(
          "WadoCompressionRate",
          wadoCompression < 1 ? null : Integer.toString(wadoCompression),
          result);
      result.append(">");

      List<SopInstance> list = new ArrayList<>(sopInstanceMap.values());
      Collections.sort(list);
      for (SopInstance s : list) {
        s.toXml(result);
      }
      result.append("\n</");
      result.append(Xml.Level.SERIES.getTagName());
      result.append(">");
    }
  }

  @Override
  public int compareTo(Series s) {
    Integer val1 = StringUtil.getInteger(getSeriesNumber());
    Integer val2 = StringUtil.getInteger(s.getSeriesNumber());

    int c = -1;
    if (val1 != null && val2 != null) {
      c = val1.compareTo(val2);
      if (c != 0) {
        return c;
      }
    }

    if (c == 0 || (val1 == null && val2 == null)) {
      return getSeriesInstanceUID().compareTo(s.getSeriesInstanceUID());
    } else {
      if (val1 == null) {
        // Add o1 after o2
        return 1;
      }
      return -1;
    }
  }
}
