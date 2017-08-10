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
    private final List<String> presentationList;
    private final List<String> selectionList;
    private final StringBuilder manifest;

    public ArcQuery(List<QueryResult> resultList) {
        this(resultList, null);
    }

    public ArcQuery(List<QueryResult> resultList, List<String> presentationList) {
        this(resultList, presentationList, null);
    }

    public ArcQuery(List<QueryResult> resultList, List<String> presentationList, List<String> selectionList) {
        this.archiveList = Objects.requireNonNull(resultList);
        this.presentationList = presentationList;
        this.selectionList = selectionList;
        this.manifest = new StringBuilder();
    }

    @Override
    public String getCharsetEncoding() {
        return "UTF-8";
    }

    @Override
    public String xmlManifest(String version) {
        manifest.append("<?xml version=\"1.0\" encoding=\"");
        manifest.append(getCharsetEncoding());
        manifest.append("\" ?>");

        if (version != null && "1".equals(version.trim())) {
            return xmlManifest1();
        }

        manifest.append("\n<");
        manifest.append(ArcParameters.TAG_DOCUMENT_ROOT);
        manifest.append(" ");
        manifest.append(ArcParameters.SCHEMA);
        manifest.append(">");

        for (QueryResult archive : archiveList) {
            if (archive.getPatients().isEmpty() && archive.getViewerMessage() == null) {
                continue;
            }
            WadoParameters wadoParameters = archive.getWadoParameters();
            manifest.append("\n<");
            manifest.append(ArcParameters.TAG_ARC_QUERY);
            manifest.append(" ");

            Xml.addXmlAttribute(ArcParameters.ARCHIVE_ID, wadoParameters.getArchiveID(), manifest);
            Xml.addXmlAttribute(ArcParameters.BASE_URL, wadoParameters.getBaseURL(), manifest);
            Xml.addXmlAttribute(ArcParameters.WEB_LOGIN, wadoParameters.getWebLogin(), manifest);
            Xml.addXmlAttribute(WadoParameters.WADO_ONLY_SOP_UID, wadoParameters.isRequireOnlySOPInstanceUID(),
                manifest);
            Xml.addXmlAttribute(ArcParameters.ADDITIONNAL_PARAMETERS, wadoParameters.getAdditionnalParameters(),
                manifest);
            Xml.addXmlAttribute(ArcParameters.OVERRIDE_TAGS, wadoParameters.getOverrideDicomTagsList(), manifest);
            manifest.append(">");

            buildHttpTags(manifest, wadoParameters.getHttpTaglist());
            buildViewerMessage(manifest, archive.getViewerMessage());
            buildPatient(manifest, archive.getPatients());

            manifest.append("\n</");
            manifest.append(ArcParameters.TAG_ARC_QUERY);
            manifest.append(">");
        }

        if (presentationList != null && !presentationList.isEmpty()) {
            manifest.append("\n<");
            manifest.append(ArcParameters.TAG_PR_ROOT);
            manifest.append(">\n");

            for (String pr : presentationList) {
                manifest.append(pr);
                manifest.append("\n");
            }

            manifest.append("\n</");
            manifest.append(ArcParameters.TAG_PR_ROOT);
            manifest.append(">");
        }

        manifest.append("\n</");
        manifest.append(ArcParameters.TAG_DOCUMENT_ROOT);
        manifest.append(">\n"); // Requires end of line

        return manifest.toString();
    }

    /**
     * Use instead xmlManifest(String version)
     * 
     * @return
     */
    @Deprecated
    public String xmlManifest1() {
        for (QueryResult archive : archiveList) {
            if (archive.getPatients().isEmpty() && archive.getViewerMessage() == null) {
                continue;
            }
            WadoParameters wadoParameters = archive.getWadoParameters();
            manifest.append("\n<");
            manifest.append(WadoParameters.TAG_WADO_QUERY);
            manifest.append(
                " xmlns=\"http://www.weasis.org/xsd\" xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" ");

            Xml.addXmlAttribute(WadoParameters.WADO_URL, wadoParameters.getBaseURL(), manifest);
            Xml.addXmlAttribute(ArcParameters.WEB_LOGIN, wadoParameters.getWebLogin(), manifest);
            Xml.addXmlAttribute(WadoParameters.WADO_ONLY_SOP_UID, wadoParameters.isRequireOnlySOPInstanceUID(),
                manifest);
            Xml.addXmlAttribute(ArcParameters.ADDITIONNAL_PARAMETERS, wadoParameters.getAdditionnalParameters(),
                manifest);
            Xml.addXmlAttribute(ArcParameters.OVERRIDE_TAGS, wadoParameters.getOverrideDicomTagsList(), manifest);
            manifest.append(">");

            buildHttpTags(manifest, wadoParameters.getHttpTaglist());
            buildViewerMessage(manifest, archive.getViewerMessage());
            buildPatient(manifest, archive.getPatients());

            manifest.append("\n</");
            manifest.append(WadoParameters.TAG_WADO_QUERY);
            manifest.append(">\n"); // Requires end of line

            break; // accept only one element
        }
        return manifest.toString();
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
