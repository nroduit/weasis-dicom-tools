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
import java.io.StringWriter;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.util.StringUtil;

/**
 * Implementation of XML manifest generation for DICOM archive queries. Supports both legacy version
 * 1.0 and modern version 2.5 manifest formats.
 */
public class ArcQuery implements XmlManifest {
  private static final Logger LOGGER = LoggerFactory.getLogger(ArcQuery.class);

  private static final String VERSION_1 = "1";
  private static final String XML_DECLARATION = "<?xml version=\"1.0\" encoding=\"%s\" ?>";
  private static final String LEGACY_SCHEMA =
      "xmlns=\"http://www.weasis.org/xsd\" xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"";
  private final List<QueryResult> queryList;
  private final String manifestUID;

  /** Creates an archive query with the specified result list and a generated UUID. */
  public ArcQuery(List<QueryResult> resultList) {
    this(resultList, null);
  }

  /** Creates an archive query with the specified result list and manifest UID. */
  public ArcQuery(List<QueryResult> resultList, String manifestUID) {
    this.queryList = Objects.requireNonNull(resultList, "Result list cannot be null");
    this.manifestUID = StringUtil.hasText(manifestUID) ? manifestUID : UUID.randomUUID().toString();
  }

  public List<QueryResult> getQueryList() {
    return queryList;
  }

  @Override
  public String xmlManifest(String version) {
    try (var manifest = new StringWriter()) {
      writeManifest(manifest, version);
      return manifest.toString();
    } catch (IOException e) {
      LOGGER.error("Cannot write manifest", e);
      return null;
    }
  }

  @Override
  public void writeManifest(Writer writer, String version) throws IOException {
    writeXmlHeader(writer);

    if (isLegacyVersion(version)) {
      writeLegacyManifest(writer);
    } else {
      writeModernManifest(writer);
    }
  }

  public void writeXmlHeader(Writer writer) throws IOException {
    writer.append(String.format(XML_DECLARATION, getCharsetEncoding()));
  }

  private boolean isLegacyVersion(String version) {
    return version != null && VERSION_1.equals(version.trim());
  }

  private void writeLegacyManifest(Writer writer) throws IOException {
    for (QueryResult archive : queryList) {
      if (hasContent(archive)) {
        writeLegacyQuery(writer, archive);
        break; // Legacy format accepts only one element
      }
    }
  }

  private void writeModernManifest(Writer writer) throws IOException {
    writeManifestRoot(writer);
    writeArchiveQueries(writer);
    writeDocumentEnd(writer);
  }

  public void writeManifestRoot(Writer writer) throws IOException {
    writer.append("\n<").append(ArcParameters.TAG_DOCUMENT_ROOT).append(" ");

    Xml.addXmlAttribute(ArcParameters.MANIFEST_UID, manifestUID, writer);
    writer.append(ArcParameters.SCHEMA).append(">");
  }

  public void writeDocumentEnd(Writer writer) throws IOException {
    writer.append("\n</").append(ArcParameters.TAG_DOCUMENT_ROOT).append(">\n");
  }

  public void writeArchiveQueries(Writer writer) throws IOException {
    for (QueryResult archive : queryList) {
      if (hasContent(archive)) {
        writeArchiveQuery(writer, archive);
      }
    }
  }

  public void writeArchiveQuery(Writer writer, QueryResult archive) throws IOException {
    WadoParameters wadoParams = archive.getWadoParameters();

    writeQueryStart(writer, ArcParameters.TAG_ARC_QUERY);
    writeQueryAttributes(writer, wadoParams, false);
    writeQueryContent(writer, archive, wadoParams);
    writeQueryEnd(writer, ArcParameters.TAG_ARC_QUERY);
  }

  private void writeLegacyQuery(Writer writer, QueryResult archive) throws IOException {
    WadoParameters wadoParams = archive.getWadoParameters();

    writeLegacyQueryStart(writer);
    writeQueryAttributes(writer, wadoParams, true);
    writeQueryContent(writer, archive, wadoParams);
    writeLegacyQueryEnd(writer);
  }

  private void writeLegacyQueryStart(Writer writer) throws IOException {
    writer
        .append("\n<")
        .append(WadoParameters.TAG_WADO_QUERY)
        .append(" ")
        .append(LEGACY_SCHEMA)
        .append(" ");
  }

  private void writeLegacyQueryEnd(Writer writer) throws IOException {
    writer.append("\n</").append(WadoParameters.TAG_WADO_QUERY).append(">\n");
  }

  public void writeQueryStart(Writer writer, String tagName) throws IOException {
    writer.append("\n<").append(tagName).append(" ");
  }

  public void writeQueryEnd(Writer writer, String tagName) throws IOException {
    writer.append("\n</").append(tagName).append(">");
  }

  public void writeQueryAttributes(Writer writer, WadoParameters wadoParams, boolean isLegacy)
      throws IOException {
    if (isLegacy) {
      Xml.addXmlAttribute(WadoParameters.WADO_URL, wadoParams.getBaseURL(), writer);
    } else {
      Xml.addXmlAttribute(ArcParameters.ARCHIVE_ID, wadoParams.getArchiveID(), writer);
      Xml.addXmlAttribute(ArcParameters.BASE_URL, wadoParams.getBaseURL(), writer);
    }

    Xml.addXmlAttribute(ArcParameters.WEB_LOGIN, wadoParams.getWebLogin(), writer);
    Xml.addXmlAttribute(
        WadoParameters.WADO_ONLY_SOP_UID, wadoParams.isRequireOnlySOPInstanceUID(), writer);
    Xml.addXmlAttribute(
        ArcParameters.ADDITIONAL_PARAMETERS, wadoParams.getAdditionalParameters(), writer);
    Xml.addXmlAttribute(
        ArcParameters.OVERRIDE_TAGS, wadoParams.getOverrideDicomTagIDList(), writer);

    writer.append(">");
  }

  public void writeQueryContent(Writer writer, QueryResult archive, WadoParameters wadoParams)
      throws IOException {
    writeHttpTags(writer, wadoParams.getHttpTaglist());
    writeViewerMessage(writer, archive.getViewerMessage());
    writePatients(writer, new ArrayList<>(archive.getPatients().values()));
  }

  public static boolean hasContent(QueryResult archive) {
    return !archive.getPatients().isEmpty() || archive.getViewerMessage() != null;
  }

  private static void writePatients(Writer writer, List<Patient> patientList) throws IOException {
    if (patientList.isEmpty()) {
      return;
    }

    Collections.sort(patientList);

    for (Patient patient : patientList) {
      patient.toXml(writer);
    }
  }

  public static void writeHttpTags(Writer writer, List<HttpTag> httpTags) throws IOException {
    if (httpTags == null || httpTags.isEmpty()) {
      return;
    }

    for (HttpTag tag : httpTags) {
      writer
          .append("\n<")
          .append(ArcParameters.TAG_HTTP_TAG)
          .append(" key=\"")
          .append(tag.getKey())
          .append("\" value=\"")
          .append(tag.getValue())
          .append("\" />");
    }
  }

  public static void writeViewerMessage(Writer writer, ViewerMessage message) throws IOException {
    if (message == null) {
      return;
    }

    writer.append("\n<").append(ViewerMessage.TAG_DOCUMENT_MSG).append(" ");

    Xml.addXmlAttribute(ViewerMessage.MSG_ATTRIBUTE_TITLE, message.title(), writer);
    Xml.addXmlAttribute(ViewerMessage.MSG_ATTRIBUTE_DESC, message.message(), writer);
    Xml.addXmlAttribute(ViewerMessage.MSG_ATTRIBUTE_LEVEL, message.level().name(), writer);

    writer.append("/>");
  }
}
