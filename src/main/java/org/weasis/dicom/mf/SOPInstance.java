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

import org.dcm4che3.data.Tag;
import org.weasis.core.api.media.data.TagW;
import org.weasis.core.api.util.StringUtil;

public class SOPInstance implements Xml {

    private final String sopInstanceUID;
    private String transferSyntaxUID = null;
    private String instanceNumber = null;
    private String directDownloadFile = null;

    public SOPInstance(String sopInstanceUID) {
        if (sopInstanceUID == null) {
            throw new IllegalArgumentException("sopInstanceIUID is null");
        }
        this.sopInstanceUID = sopInstanceUID;
    }

    public String getTransferSyntaxUID() {
        return transferSyntaxUID;
    }

    public void setTransferSyntaxUID(String transferSyntaxUID) {
        this.transferSyntaxUID = transferSyntaxUID;
    }

    public String getSOPInstanceIUID() {
        return sopInstanceUID;
    }

    public String getInstanceNumber() {
        return instanceNumber;
    }

    public void setInstanceNumber(String instanceNumber) {
        this.instanceNumber = StringUtil.hasText(instanceNumber) ? instanceNumber.trim() : null;
    }

    public String getDirectDownloadFile() {
        return directDownloadFile;
    }

    public void setDirectDownloadFile(String directDownloadFile) {
        this.directDownloadFile = directDownloadFile;
    }

    @Override
    public String toXml() {
        StringBuilder result = new StringBuilder();
        result.append("\n<");
        result.append(Xml.Level.INSTANCE);
        result.append(" ");
        Xml.addXmlAttribute(Tag.SOPInstanceUID, sopInstanceUID, result);
        // file_tsuid DICOM Transfer Syntax UID (0002,0010)
        Xml.addXmlAttribute(Tag.TransferSyntaxUID, transferSyntaxUID, result);
        Xml.addXmlAttribute(Tag.InstanceNumber, instanceNumber, result);
        Xml.addXmlAttribute(TagW.DirectDownloadFile, directDownloadFile, result);
        result.append("/>");

        return result.toString();
    }

}
