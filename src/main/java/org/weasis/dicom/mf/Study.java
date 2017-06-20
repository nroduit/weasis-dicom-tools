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

import java.text.Collator;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

import org.dcm4che3.data.Tag;
import org.weasis.dicom.tool.DateUtil;

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

            Collections.sort(seriesList, Series::compareSeries);
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

    public static int compareStudy(Study o1, Study o2) {
        LocalDateTime date1 =
            DateUtil.dateTime(DateUtil.getDicomDate(o1.getStudyDate()), DateUtil.getDicomTime(o1.getStudyTime()));
        LocalDateTime date2 =
            DateUtil.dateTime(DateUtil.getDicomDate(o2.getStudyDate()), DateUtil.getDicomTime(o2.getStudyTime()));

        int c = -1;
        if (date1 != null && date2 != null) {
            // inverse time
            c = date2.compareTo(date1);
            if (c != 0) {
                return c;
            }
        }

        if (c == 0 || (date1 == null && date2 == null)) {
            String d1 = o1.getStudyDescription();
            String d2 = o2.getStudyDescription();
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
            if (date2 == null) {
                return -1;
            }
        }
        return o1.getStudyInstanceUID().compareTo(o2.getStudyInstanceUID());
    }

}
