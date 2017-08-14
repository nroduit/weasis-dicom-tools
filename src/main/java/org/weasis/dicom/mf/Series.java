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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.media.data.TagW;
import org.weasis.core.api.util.StringUtil;

public class Series implements Xml {
    private static final Logger LOGGER = LoggerFactory.getLogger(Series.class);

    private final String seriesInstanceUID;
    private String seriesDescription = null;
    private final ArrayList<SOPInstance> sopInstancesList;
    private String modality = null;
    private String seriesNumber = null;
    private String wadoTransferSyntaxUID = null;
    // Image quality within the range 1 to 100, 100 being the best quality.
    private int wadoCompression = 0;
    private String thumbnail = null;

    public Series(String seriesInstanceUID) {
        this.seriesInstanceUID = Objects.requireNonNull(seriesInstanceUID, "seriesInstanceUID is null");
        sopInstancesList = new ArrayList<>();
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

    public void addSOPInstance(SOPInstance s) {
        if (s != null) {
            sopInstancesList.add(s);
        }
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

    public List<SOPInstance> getSopInstancesList() {
        return sopInstancesList;
    }



    @Override
    public String toXml() {
        StringBuilder result = new StringBuilder();
        if (seriesInstanceUID != null) {
            result.append("\n<");
            result.append(Xml.Level.SERIES);
            result.append(" ");
            Xml.addXmlAttribute(Tag.SeriesInstanceUID, seriesInstanceUID, result);
            Xml.addXmlAttribute(Tag.SeriesDescription, seriesDescription, result);
            Xml.addXmlAttribute(Tag.SeriesNumber, seriesNumber, result);
            Xml.addXmlAttribute(Tag.Modality, modality, result);
            Xml.addXmlAttribute(TagW.DirectDownloadThumbnail, thumbnail, result);
            Xml.addXmlAttribute(TagW.WadoTransferSyntaxUID, wadoTransferSyntaxUID, result);
            Xml.addXmlAttribute(TagW.WadoCompressionRate,
                wadoCompression < 1 ? null : Integer.toString(wadoCompression), result);
            result.append(">");
            
            Collections.sort(sopInstancesList, SOPInstance::compareInstanceNumber);
            for (SOPInstance s : sopInstancesList) {
                result.append(s.toXml());
            }
            result.append("\n</");
            result.append(Xml.Level.SERIES);
            result.append(">");
        }
        return result.toString();
    }

    public boolean isEmpty() {
        return sopInstancesList.isEmpty();
    }
    
    public static int compareSeries(Series o1, Series o2) {
        Integer val1 = StringUtil.getInteger(o1.getSeriesNumber());
        Integer val2 = StringUtil.getInteger(o2.getSeriesNumber());

        int c = -1;
        if (val1 != null && val2 != null) {
            c = val1.compareTo(val2);
            if (c != 0) {
                return c;
            }
        }

        if (c == 0 || (val1 == null && val2 == null)) {
            return o1.getSeriesInstanceUID().compareTo(o2.getSeriesInstanceUID());
        } else {
            if (val1 == null) {
                // Add o1 after o2
                return 1;
            }
            if (val2 == null) {
                return -1;
            }
        }

        return o1.getSeriesInstanceUID().compareTo(o2.getSeriesInstanceUID());
    }
}
