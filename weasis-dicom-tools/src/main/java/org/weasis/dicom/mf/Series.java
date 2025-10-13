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
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Set;
import org.dcm4che3.data.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.util.MathUtil;
import org.weasis.core.util.StringUtil;

/**
 * Represents a DICOM series within a study, containing multiple SOP instances. Provides
 * functionality for XML serialization, instance management, and WADO configuration.
 *
 * <p>A series groups related medical images or objects that share common acquisition parameters and
 * are part of the same imaging procedure or sequence.
 */
public class Series implements Xml, Comparable<Series> {
  private static final Logger LOGGER = LoggerFactory.getLogger(Series.class);

  private static final int MIN_COMPRESSION = 0;
  private static final int MAX_COMPRESSION = 100;
  private final String seriesInstanceUID;
  private final Map<String, SopInstance> sopInstanceMap;

  private String seriesDescription;
  private String modality;
  private String seriesNumber;
  private String wadoTransferSyntaxUID;
  private int wadoCompression = MIN_COMPRESSION;
  private String thumbnail;

  /**
   * Creates a series with the specified instance UID.
   *
   * @param seriesInstanceUID the DICOM Series Instance UID, required
   * @throws NullPointerException if seriesInstanceUID is null
   */
  public Series(String seriesInstanceUID) {
    this.seriesInstanceUID =
        Objects.requireNonNull(seriesInstanceUID, "Series Instance UID cannot be null");
    this.sopInstanceMap = new HashMap<>();
  }

  public String getSeriesInstanceUID() {
    return seriesInstanceUID;
  }

  public String getSeriesDescription() {
    return seriesDescription;
  }

  public void setSeriesDescription(String seriesDescription) {
    this.seriesDescription = seriesDescription;
  }

  public String getSeriesNumber() {
    return seriesNumber;
  }

  public void setSeriesNumber(String seriesNumber) {
    this.seriesNumber = StringUtil.hasText(seriesNumber) ? seriesNumber.trim() : null;
  }

  public String getModality() {
    return modality;
  }

  public void setModality(String modality) {
    this.modality = modality;
  }

  public String getWadoTransferSyntaxUID() {
    return wadoTransferSyntaxUID;
  }

  public void setWadoTransferSyntaxUID(String wadoTransferSyntaxUID) {
    this.wadoTransferSyntaxUID = wadoTransferSyntaxUID;
  }

  /** Returns the WADO compression quality (0-100, where 100 is best quality). */
  public int getWadoCompression() {
    return wadoCompression;
  }

  /**
   * Sets the WADO compression quality, clamped to valid range (0-100).
   *
   * @param wadoCompression compression quality from 0 (lowest) to 100 (highest)
   */
  public void setWadoCompression(int wadoCompression) {
    this.wadoCompression = MathUtil.clamp(wadoCompression, MIN_COMPRESSION, MAX_COMPRESSION);
  }

  /**
   * Sets the WADO compression quality from a string value. Invalid values are logged and ignored.
   *
   * @param wadoCompression compression quality as string
   */
  public void setWadoCompression(String wadoCompression) {
    if (!StringUtil.hasText(wadoCompression)) {
      return;
    }
    try {
      setWadoCompression(Integer.parseInt(wadoCompression.trim()));
    } catch (NumberFormatException e) {
      LOGGER.warn(
          "Invalid compression value '{}', keeping current value: {}",
          wadoCompression,
          this.wadoCompression);
    }
  }

  public String getThumbnail() {
    return thumbnail;
  }

  public void setThumbnail(String thumbnail) {
    this.thumbnail = thumbnail;
  }

  /**
   * Adds a SOP instance to this series.
   *
   * @param sopInstance the SOP instance to add, ignored if null
   */
  public void addSopInstance(SopInstance sopInstance) {
    SopInstance.addSopInstance(sopInstanceMap, sopInstance);
  }

  /**
   * Removes and returns a SOP instance by UID and instance number.
   *
   * @param sopUID the SOP Instance UID, required
   * @param instanceNumber the instance number, may be null
   * @return the removed SOP instance, or null if not found
   */
  public SopInstance removeSopInstance(String sopUID, Integer instanceNumber) {
    return SopInstance.removeSopInstance(sopInstanceMap, sopUID, instanceNumber);
  }

  /**
   * Retrieves a SOP instance by UID and instance number.
   *
   * @param sopUID the SOP Instance UID, required
   * @param instanceNumber the instance number, may be null
   * @return the matching SOP instance, or null if not found
   */
  public SopInstance getSopInstance(String sopUID, Integer instanceNumber) {
    return SopInstance.getSopInstance(sopInstanceMap, sopUID, instanceNumber);
  }

  /**
   * Returns all SOP instances in this series.
   *
   * @return unmodifiable collection of SOP instances
   */
  public Collection<SopInstance> getSopInstances() {
    return sopInstanceMap.values();
  }

  /**
   * Returns the entry set of the SOP instance map for iteration.
   *
   * @return set of map entries containing key-value pairs
   */
  public Set<Entry<String, SopInstance>> getEntrySet() {
    return sopInstanceMap.entrySet();
  }

  /**
   * Checks if this series contains any SOP instances.
   *
   * @return true if the series is empty, false otherwise
   */
  public boolean isEmpty() {
    return sopInstanceMap.isEmpty();
  }

  @Override
  public void toXml(Writer writer) throws IOException {
    writeSeriesStart(writer);
    writeSopInstances(writer);
    writeSeriesEnd(writer);
  }

  @Override
  public int compareTo(Series other) {
    // Compare by series number first, then by series instance UID
    int numberComparison = compareSeriesNumbers(this.seriesNumber, other.seriesNumber);
    return numberComparison != 0
        ? numberComparison
        : this.seriesInstanceUID.compareTo(other.seriesInstanceUID);
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) return true;
    if (obj == null || getClass() != obj.getClass()) return false;

    var other = (Series) obj;
    return seriesInstanceUID.equals(other.seriesInstanceUID);
  }

  @Override
  public int hashCode() {
    return Objects.hash(seriesInstanceUID);
  }

  // Writes the opening series tag with attributes
  private void writeSeriesStart(Writer writer) throws IOException {
    writer.append("\n<").append(Xml.Level.SERIES.getTagName()).append(" ");

    Xml.addXmlAttribute(Tag.SeriesInstanceUID, seriesInstanceUID, writer);
    Xml.addXmlAttribute(Tag.SeriesDescription, seriesDescription, writer);
    Xml.addXmlAttribute(Tag.SeriesNumber, seriesNumber, writer);
    Xml.addXmlAttribute(Tag.Modality, modality, writer);
    Xml.addXmlAttribute("DirectDownloadThumbnail", thumbnail, writer);
    Xml.addXmlAttribute("WadoTransferSyntaxUID", wadoTransferSyntaxUID, writer);
    Xml.addXmlAttribute("WadoCompressionRate", getCompressionRateString(), writer);

    writer.append(">");
  }

  // Writes all SOP instances in sorted order
  private void writeSopInstances(Writer writer) throws IOException {
    var sortedInstances = new ArrayList<>(sopInstanceMap.values());
    Collections.sort(sortedInstances);

    for (SopInstance instance : sortedInstances) {
      instance.toXml(writer);
    }
  }

  // Writes the closing series tag
  private void writeSeriesEnd(Writer writer) throws IOException {
    writer.append("\n</").append(Xml.Level.SERIES.getTagName()).append(">");
  }

  // Returns compression rate as string, null if not set
  private String getCompressionRateString() {
    return wadoCompression > MIN_COMPRESSION ? String.valueOf(wadoCompression) : null;
  }

  // Compares series numbers with null-safe logic
  private static int compareSeriesNumbers(String number1, String number2) {
    Integer val1 = StringUtil.getInteger(number1);
    Integer val2 = StringUtil.getInteger(number2);

    if (val1 != null && val2 != null) {
      return val1.compareTo(val2);
    }
    if (val1 == null && val2 == null) {
      return 0; // Both null, continue to UID comparison
    }

    // Null values sorted after non-null values
    return val1 == null ? 1 : -1;
  }
}
