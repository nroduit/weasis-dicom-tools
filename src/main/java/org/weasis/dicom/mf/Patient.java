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

import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

import org.dcm4che3.data.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.util.StringUtil;

public class Patient implements Xml {

    private static final Logger LOGGER = LoggerFactory.getLogger(Patient.class);

    private final String patientID;
    private String issuerOfPatientID = null;
    private String patientName = null;
    private String patientBirthDate = null;
    private String patientBirthTime = null;
    private String patientSex = null;
    private final List<Study> studiesList;

    public Patient(String patientID) {
        this(patientID, null);
    }

    public Patient(String patientID, String issuerOfPatientID) {
        this.patientID = Objects.requireNonNull(patientID, "PaientID cannot be null!");
        this.issuerOfPatientID = issuerOfPatientID;
        studiesList = new ArrayList<>();
    }

    public boolean hasSameUniqueID(String patientID, String issuerOfPatientID) {
        return this.patientID.equals(patientID) && Objects.equals(this.issuerOfPatientID, issuerOfPatientID);
    }

    public String getPatientID() {
        return patientID;
    }

    public String getPatientName() {
        return patientName;
    }

    public List<Study> getStudies() {
        return studiesList;
    }

    public boolean isEmpty() {
        for (Study s : studiesList) {
            if (!s.isEmpty()) {
                return false;
            }
        }
        return true;
    }

    public String getPatientBirthTime() {
        return patientBirthTime;
    }

    public void setPatientBirthTime(String patientBirthTime) {
        this.patientBirthTime = patientBirthTime;
    }

    public String getPatientBirthDate() {
        return patientBirthDate;
    }

    public void setPatientBirthDate(String patientBirthDate) {
        this.patientBirthDate = patientBirthDate;
    }

    public String getPatientSex() {
        return patientSex;
    }

    public void setPatientSex(String patientSex) {
        if (patientSex == null) {
            this.patientSex = null;
        } else {
            String val = patientSex.toUpperCase(Locale.getDefault());
            this.patientSex = val.startsWith("M") ? "M" : val.startsWith("F") ? "F" : "O";
        }
    }

    public void setPatientName(String patientName) {
        this.patientName = StringUtil.getEmptyStringIfNull(patientName);
    }

    public void addStudy(Study study) {
        if (!studiesList.contains(study)) {
            studiesList.add(study);
        }
    }

    @Override
    public void toXml(Writer result) throws IOException {
        if (patientID != null && patientName != null) {
            result.append("\n<");
            result.append(Xml.Level.PATIENT.getTagName());
            result.append(" ");

            Xml.addXmlAttribute(Tag.PatientID, patientID, result);
            Xml.addXmlAttribute(Tag.IssuerOfPatientID, issuerOfPatientID, result);
            Xml.addXmlAttribute(Tag.PatientName, patientName, result);
            Xml.addXmlAttribute(Tag.PatientBirthDate, patientBirthDate, result);
            Xml.addXmlAttribute(Tag.PatientBirthTime, patientBirthTime, result);
            Xml.addXmlAttribute(Tag.PatientSex, patientSex, result);
            result.append(">");

            Collections.sort(studiesList, Study::compareStudy);
            for (Study s : studiesList) {
                s.toXml(result);
            }
            result.append("\n</");
            result.append(Xml.Level.PATIENT.getTagName());
            result.append(">");
        }
    }

    public Study getStudy(String uid) {
        for (Study s : studiesList) {
            if (s.getStudyInstanceUID().equals(uid)) {
                return s;
            }
        }
        return null;
    }

}
