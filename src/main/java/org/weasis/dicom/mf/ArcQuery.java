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

public class ArcQuery implements XmlManifest {
  private static final Logger LOGGER = LoggerFactory.getLogger(ArcQuery.class);

  private final List<QueryResult> queryList;
  protected final String manifestUID;

  public ArcQuery(List<QueryResult> resultList) {
    this(resultList, null);
  }

  public ArcQuery(List<QueryResult> resultList, String manifestUID) {
    this.queryList = Objects.requireNonNull(resultList);
    this.manifestUID = StringUtil.hasText(manifestUID) ? manifestUID : UUID.randomUUID().toString();
  }

  public List<QueryResult> getQueryList() {
    return queryList;
  }

  @Override
  public String getCharsetEncoding() {
    return "UTF-8";
  }

  @Override
  public String xmlManifest(String version) {
    try {
      Writer manifest = new StringWriter();
      writeManifest(manifest, version);
      return manifest.toString();
    } catch (Exception e) {
      LOGGER.error("Cann write manifest", e);
    }
    return null;
  }

  public void writeManifest(Writer manifest, String version) throws IOException {
    writeHeader(manifest);
    if (version != null && "1".equals(version.trim())) {
      writeWadoQuery(manifest);
      return;
    }
    writeArcQueries(manifest);
    writeEndOfDocument(manifest);
  }

  public void writeHeader(Writer mf) throws IOException {
    mf.append("<?xml version=\"1.0\" encoding=\"");
    mf.append(getCharsetEncoding());
    mf.append("\" ?>");
  }

  public void writeEndOfDocument(Writer mf) throws IOException {
    mf.append("\n</");
    mf.append(ArcParameters.TAG_DOCUMENT_ROOT);
    mf.append(">\n"); // Requires end of line
  }

  public void writeArcQueries(Writer mf) throws IOException {
    mf.append("\n<");
    mf.append(ArcParameters.TAG_DOCUMENT_ROOT);
    mf.append(" ");
    Xml.addXmlAttribute(ArcParameters.MANIFEST_UID, manifestUID, mf);
    mf.append(ArcParameters.SCHEMA);
    mf.append(">");

    for (QueryResult archive : queryList) {
      if (archive.getPatients().isEmpty() && archive.getViewerMessage() == null) {
        continue;
      }
      WadoParameters wadoParameters = archive.getWadoParameters();
      mf.append("\n<");
      mf.append(ArcParameters.TAG_ARC_QUERY);
      mf.append(" ");

      Xml.addXmlAttribute(ArcParameters.ARCHIVE_ID, wadoParameters.getArchiveID(), mf);
      Xml.addXmlAttribute(ArcParameters.BASE_URL, wadoParameters.getBaseURL(), mf);
      Xml.addXmlAttribute(ArcParameters.WEB_LOGIN, wadoParameters.getWebLogin(), mf);
      Xml.addXmlAttribute(
          WadoParameters.WADO_ONLY_SOP_UID, wadoParameters.isRequireOnlySOPInstanceUID(), mf);
      Xml.addXmlAttribute(
          ArcParameters.ADDITIONNAL_PARAMETERS, wadoParameters.getAdditionnalParameters(), mf);
      Xml.addXmlAttribute(
          ArcParameters.OVERRIDE_TAGS, wadoParameters.getOverrideDicomTagsList(), mf);
      mf.append(">");

      buildHttpTags(mf, wadoParameters.getHttpTaglist());
      buildViewerMessage(mf, archive.getViewerMessage());
      buildPatient(mf, new ArrayList<>(archive.getPatients().values()));

      mf.append("\n</");
      mf.append(ArcParameters.TAG_ARC_QUERY);
      mf.append(">");
    }
  }

  private void writeWadoQuery(Writer mf) throws IOException {
    for (QueryResult archive : queryList) {
      if (archive.getPatients().isEmpty() && archive.getViewerMessage() == null) {
        continue;
      }
      WadoParameters wadoParameters = archive.getWadoParameters();
      mf.append("\n<");
      mf.append(WadoParameters.TAG_WADO_QUERY);
      mf.append(
          " xmlns=\"http://www.weasis.org/xsd\" xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" ");

      Xml.addXmlAttribute(WadoParameters.WADO_URL, wadoParameters.getBaseURL(), mf);
      Xml.addXmlAttribute(ArcParameters.WEB_LOGIN, wadoParameters.getWebLogin(), mf);
      Xml.addXmlAttribute(
          WadoParameters.WADO_ONLY_SOP_UID, wadoParameters.isRequireOnlySOPInstanceUID(), mf);
      Xml.addXmlAttribute(
          ArcParameters.ADDITIONNAL_PARAMETERS, wadoParameters.getAdditionnalParameters(), mf);
      Xml.addXmlAttribute(
          ArcParameters.OVERRIDE_TAGS, wadoParameters.getOverrideDicomTagsList(), mf);
      mf.append(">");

      buildHttpTags(mf, wadoParameters.getHttpTaglist());
      buildViewerMessage(mf, archive.getViewerMessage());
      buildPatient(mf, new ArrayList<>(archive.getPatients().values()));

      mf.append("\n</");
      mf.append(WadoParameters.TAG_WADO_QUERY);
      mf.append(">\n"); // Requires end of line

      break; // accept only one element
    }
  }

  public static void buildPatient(Writer mf, List<Patient> patientList) throws IOException {
    if (patientList != null) {
      Collections.sort(patientList);

      for (Patient patient : patientList) {
        patient.toXml(mf);
      }
    }
  }

  public static void buildHttpTags(Writer mf, List<HttpTag> list) throws IOException {
    if (list != null) {
      for (HttpTag tag : list) {
        mf.append("\n<");
        mf.append(ArcParameters.TAG_HTTP_TAG);
        mf.append(" key=\"");
        mf.append(tag.getKey());
        mf.append("\" value=\"");
        mf.append(tag.getValue());
        mf.append("\" />");
      }
    }
  }

  public static void buildViewerMessage(Writer mf, ViewerMessage message) throws IOException {
    if (message != null) {
      mf.append("\n<");
      mf.append(ViewerMessage.TAG_DOCUMENT_MSG);
      mf.append(" ");
      Xml.addXmlAttribute(ViewerMessage.MSG_ATTRIBUTE_TITLE, message.getTitle(), mf);
      Xml.addXmlAttribute(ViewerMessage.MSG_ATTRIBUTE_DESC, message.getMessage(), mf);
      Xml.addXmlAttribute(ViewerMessage.MSG_ATTRIBUTE_LEVEL, message.getLevel().name(), mf);
      mf.append("/>");
    }
  }
}
