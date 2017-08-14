/*******************************************************************************
 * Copyright (c) 2014 Weasis Team.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Nicolas Roduit - initial API and implementation
 *******************************************************************************/
package org.weasis.dicom.mf;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

public class ArcQuery implements XmlManifest {

    private final List<QueryResult> archiveList;
    private final StringBuilder manifest;

    public ArcQuery(List<QueryResult> resultList) {
        this.archiveList = Objects.requireNonNull(resultList);
        this.manifest = new StringBuilder();
    }

    @Override
    public String getCharsetEncoding() {
        return "UTF-8";
    }

    @Override
    public String xmlManifest(String version) {
        writeHeader(manifest);
        if (version != null && "1".equals(version.trim())) {
            writeWadoQuery(manifest);
            return manifest.toString();
        }
        writeArcQueries(manifest);
        writeEndOfDocument(manifest);

        return manifest.toString();
    }

    /**
     * Use instead xmlManifest(String version)
     * 
     * @return
     */
    @Deprecated
    public String xmlManifest1() {
        writeWadoQuery(manifest);
        return manifest.toString();
    }

    public void writeHeader(StringBuilder mf) {
        mf.setLength(0);
        mf.append("<?xml version=\"1.0\" encoding=\"");
        mf.append(getCharsetEncoding());
        mf.append("\" ?>");
    }

    public void writeEndOfDocument(StringBuilder mf) {
        mf.append("\n</");
        mf.append(ArcParameters.TAG_DOCUMENT_ROOT);
        mf.append(">\n"); // Requires end of line
    }

    public void writeArcQueries(StringBuilder mf) {
        mf.append("\n<");
        mf.append(ArcParameters.TAG_DOCUMENT_ROOT);
        mf.append(" ");
        mf.append(ArcParameters.SCHEMA);
        mf.append(">");

        for (QueryResult archive : archiveList) {
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
            Xml.addXmlAttribute(WadoParameters.WADO_ONLY_SOP_UID, wadoParameters.isRequireOnlySOPInstanceUID(), mf);
            Xml.addXmlAttribute(ArcParameters.ADDITIONNAL_PARAMETERS, wadoParameters.getAdditionnalParameters(), mf);
            Xml.addXmlAttribute(ArcParameters.OVERRIDE_TAGS, wadoParameters.getOverrideDicomTagsList(), mf);
            mf.append(">");

            buildHttpTags(mf, wadoParameters.getHttpTaglist());
            buildViewerMessage(mf, archive.getViewerMessage());
            buildPatient(mf, archive.getPatients());

            mf.append("\n</");
            mf.append(ArcParameters.TAG_ARC_QUERY);
            mf.append(">");
        }
    }

    private void writeWadoQuery(StringBuilder mf) {
        for (QueryResult archive : archiveList) {
            if (archive.getPatients().isEmpty() && archive.getViewerMessage() == null) {
                continue;
            }
            WadoParameters wadoParameters = archive.getWadoParameters();
            mf.append("\n<");
            mf.append(WadoParameters.TAG_WADO_QUERY);
            mf.append(" xmlns=\"http://www.weasis.org/xsd\" xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" ");

            Xml.addXmlAttribute(WadoParameters.WADO_URL, wadoParameters.getBaseURL(), mf);
            Xml.addXmlAttribute(ArcParameters.WEB_LOGIN, wadoParameters.getWebLogin(), mf);
            Xml.addXmlAttribute(WadoParameters.WADO_ONLY_SOP_UID, wadoParameters.isRequireOnlySOPInstanceUID(), mf);
            Xml.addXmlAttribute(ArcParameters.ADDITIONNAL_PARAMETERS, wadoParameters.getAdditionnalParameters(), mf);
            Xml.addXmlAttribute(ArcParameters.OVERRIDE_TAGS, wadoParameters.getOverrideDicomTagsList(), mf);
            mf.append(">");

            buildHttpTags(mf, wadoParameters.getHttpTaglist());
            buildViewerMessage(mf, archive.getViewerMessage());
            buildPatient(mf, archive.getPatients());

            mf.append("\n</");
            mf.append(WadoParameters.TAG_WADO_QUERY);
            mf.append(">\n"); // Requires end of line

            break; // accept only one element
        }
    }

    public static void buildPatient(StringBuilder mf, List<Patient> patientList) {
        if (patientList != null) {
            Collections.sort(patientList, (o1, o2) -> o1.getPatientName().compareTo(o2.getPatientName()));

            for (Patient patient : patientList) {
                mf.append(patient.toXml());
            }
        }
    }

    public static void buildHttpTags(StringBuilder mf, List<HttpTag> list) {
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

    public static void buildViewerMessage(StringBuilder mf, ViewerMessage message) {
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
