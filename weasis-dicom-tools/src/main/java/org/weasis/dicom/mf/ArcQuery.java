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
 * Manifest generation for DICOM archive queries. A single tree traversal feeds a {@link
 * ManifestSerializer}, so the same content can be emitted as XML (version 2.5) or as JSON.
 */
public class ArcQuery implements XmlManifest {
  private static final Logger LOGGER = LoggerFactory.getLogger(ArcQuery.class);

  private static final String ATTR_HTTP_TAG_KEY = "key";
  private static final String ATTR_HTTP_TAG_VALUE = "value";

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
    try (var writer = new StringWriter()) {
      writeManifest(writer, version);
      return writer.toString();
    } catch (IOException e) {
      LOGGER.error("Cannot write manifest", e);
      return null;
    }
  }

  /** Generates a JSON manifest (always the modern structure, mirroring the 2.5 element names). */
  public String jsonManifest(String version) {
    try (var writer = new StringWriter()) {
      writeManifest(writer, version, ManifestFormat.JSON);
      return writer.toString();
    } catch (IOException e) {
      LOGGER.error("Cannot write manifest", e);
      return null;
    }
  }

  /** Generates a manifest string in the requested format, or null if generation fails. */
  public String manifest(String version, ManifestFormat format) {
    return format == ManifestFormat.JSON ? jsonManifest(version) : xmlManifest(version);
  }

  @Override
  public void writeManifest(Writer writer, String version) throws IOException {
    // Only the modern 2.5 structure is emitted; the version argument is accepted for API
    // compatibility but no longer selects a legacy variant.
    writeModernManifest(new XmlManifestSerializer(writer, getCharsetEncoding()));
  }

  /** Writes the manifest to {@code writer} in the requested format. */
  public void writeManifest(Writer writer, String version, ManifestFormat format)
      throws IOException {
    if (format == ManifestFormat.JSON) {
      // JSON has no legacy variant: always emit the modern structure.
      writeModernManifest(new JsonManifestSerializer(writer));
    } else {
      writeManifest(writer, version);
    }
  }

  public static boolean hasContent(QueryResult archive) {
    return !archive.getPatients().isEmpty() || !archive.getViewerMessages().isEmpty();
  }

  private void writeModernManifest(ManifestSerializer serializer) throws IOException {
    serializer.beginDocument();
    serializer.beginObject(ArcParameters.TAG_DOCUMENT_ROOT);
    serializer.attribute(ArcParameters.MANIFEST_UID, manifestUID);
    serializer.schema(ArcParameters.SCHEMA);

    serializer.beginArray(ArcParameters.TAG_ARC_QUERY);
    for (QueryResult archive : queryList) {
      if (hasContent(archive)) {
        writeArchiveQuery(serializer, archive);
      }
    }
    serializer.endArray();

    serializer.endObject();
    serializer.endDocument();
  }

  private void writeArchiveQuery(ManifestSerializer serializer, QueryResult archive)
      throws IOException {
    WadoParameters wadoParams = archive.getWadoParameters();
    serializer.beginObject(ArcParameters.TAG_ARC_QUERY);

    serializer.attribute(ArcParameters.ARCHIVE_ID, wadoParams.getArchiveID());
    serializer.attribute(ArcParameters.BASE_URL, wadoParams.getBaseURL());
    QueryMode queryMode = wadoParams.getQueryMode();
    if (queryMode != QueryMode.DEFAULT) {
      serializer.attribute(ArcParameters.QUERY_MODE, queryMode.name());
    }
    writeCommonQueryAttributes(serializer, wadoParams);

    writeQueryContent(serializer, archive, wadoParams);
    serializer.endObject();
  }

  private void writeCommonQueryAttributes(ManifestSerializer serializer, WadoParameters wadoParams)
      throws IOException {
    serializer.attribute(ArcParameters.WEB_LOGIN, wadoParams.getWebLogin());
    serializer.attribute(
        WadoParameters.WADO_ONLY_SOP_UID, wadoParams.isRequireOnlySOPInstanceUID());
    serializer.attribute(ArcParameters.ADDITIONAL_PARAMETERS, wadoParams.getAdditionalParameters());
    serializer.attribute(ArcParameters.OVERRIDE_TAGS, wadoParams.getOverrideDicomTagsList());
  }

  private void writeQueryContent(
      ManifestSerializer serializer, QueryResult archive, WadoParameters wadoParams)
      throws IOException {
    writeHttpTags(serializer, wadoParams.getHttpTaglist());
    writeViewerMessages(serializer, archive.getViewerMessages());
    writePatients(serializer, new ArrayList<>(archive.getPatients().values()));
  }

  private static void writePatients(ManifestSerializer serializer, List<Patient> patientList)
      throws IOException {
    if (patientList.isEmpty()) {
      return;
    }
    Collections.sort(patientList);

    serializer.beginArray(ManifestNode.Level.PATIENT.getTagName());
    for (Patient patient : patientList) {
      patient.write(serializer);
    }
    serializer.endArray();
  }

  private static void writeHttpTags(ManifestSerializer serializer, List<HttpTag> httpTags)
      throws IOException {
    if (httpTags == null || httpTags.isEmpty()) {
      return;
    }
    serializer.beginArray(ArcParameters.TAG_HTTP_TAG);
    for (HttpTag tag : httpTags) {
      serializer.beginLeaf(ArcParameters.TAG_HTTP_TAG);
      serializer.attribute(ATTR_HTTP_TAG_KEY, tag.getKey());
      serializer.attribute(ATTR_HTTP_TAG_VALUE, tag.getValue());
      serializer.endLeaf();
    }
    serializer.endArray();
  }

  private static void writeViewerMessages(
      ManifestSerializer serializer, List<ViewerMessage> messages) throws IOException {
    if (messages == null || messages.isEmpty()) {
      return;
    }
    serializer.beginArray(ViewerMessage.TAG_DOCUMENT_MSG);
    for (ViewerMessage message : messages) {
      serializer.beginLeaf(ViewerMessage.TAG_DOCUMENT_MSG);
      serializer.attribute(ViewerMessage.MSG_ATTRIBUTE_TITLE, message.title());
      serializer.attribute(ViewerMessage.MSG_ATTRIBUTE_DESC, message.message());
      serializer.attribute(ViewerMessage.MSG_ATTRIBUTE_LEVEL, message.level().name());
      serializer.endLeaf();
    }
    serializer.endArray();
  }
}
