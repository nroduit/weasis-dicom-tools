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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import org.dcm4che3.data.Tag;
import org.weasis.core.api.util.StringUtil;

public class Study implements Xml {

    private final String studyInstanceUID;
    private String studyID = null;
    private String studyDescription = null;
    private String studyDate = null;
    private String studyTime = null;
    private String accessionNumber = null;
    private String referringPhysicianName = null;
    private final List<Series> seriesList;

    public Study(String studyInstanceUID) {
        this.studyInstanceUID = Objects.requireNonNull(studyInstanceUID, "studyInstanceUID cannot be null!");
        this.seriesList = new ArrayList<>();
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
        if (!seriesList.contains(s)) {
            seriesList.add(s);
        }
    }

    @Override
    public String toXml() {
        StringBuilder result = new StringBuilder();
        if (studyInstanceUID != null) {
            result.append("\n<");
            result.append(Xml.Level.STUDY);
            result.append(" ");
            Xml.addXmlAttribute(Tag.StudyInstanceUID, studyInstanceUID, result);
            Xml.addXmlAttribute(Tag.StudyDescription, studyDescription, result);
            Xml.addXmlAttribute(Tag.StudyDate, studyDate, result);
            Xml.addXmlAttribute(Tag.StudyTime, studyTime, result);
            Xml.addXmlAttribute(Tag.AccessionNumber, accessionNumber, result);
            Xml.addXmlAttribute(Tag.StudyID, studyID, result);
            Xml.addXmlAttribute(Tag.ReferringPhysicianName, referringPhysicianName, result);
            result.append(">");
            Collections.sort(seriesList, (o1, o2) -> {
                int nubmer1 = 0;
                int nubmer2 = 0;
                try {
                    if (StringUtil.hasText(o1.getSeriesNumber())) {
                        nubmer1 = Integer.parseInt(o1.getSeriesNumber());
                    }
                    if (StringUtil.hasText(o2.getSeriesNumber())) {
                        nubmer2 = Integer.parseInt(o2.getSeriesNumber());
                    }
                } catch (NumberFormatException e) {
                    // Do nothing
                }
                int rep = Integer.compare(nubmer1, nubmer2);
                if (rep != 0) {
                    return rep;
                }
                return o1.getSeriesInstanceUID().compareTo(o2.getSeriesInstanceUID());
            });
            for (Series s : seriesList) {
                result.append(s.toXml());
            }

            result.append("\n</");
            result.append(Xml.Level.STUDY);
            result.append(">");
        }
        return result.toString();
    }

    public boolean isEmpty() {
        for (Series s : seriesList) {
            if (!s.isEmpty()) {
                return false;
            }
        }
        return true;
    }

    public Series getSeries(String uid) {
        for (Series s : seriesList) {
            if (s.getSeriesInstanceUID().equals(uid)) {
                return s;
            }
        }
        return null;
    }

    public List<Series> getSeriesList() {
        return seriesList;
    }

}
