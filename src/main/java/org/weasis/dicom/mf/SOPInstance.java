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
import java.util.Objects;

import org.dcm4che3.data.Tag;
import org.weasis.core.api.media.data.TagW;
import org.weasis.core.api.util.StringUtil;

public class SOPInstance implements Xml {

    private final String sopInstanceUID;
    private String transferSyntaxUID = null;
    private String instanceNumber = null;
    private String directDownloadFile = null;

    public SOPInstance(String sopInstanceUID) {
        this.sopInstanceUID = Objects.requireNonNull(sopInstanceUID, "sopInstanceIUID is null");
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
    public void toXml(Writer result) throws IOException {
        result.append("\n<");
        result.append(Xml.Level.INSTANCE.getTagName());
        result.append(" ");
        Xml.addXmlAttribute(Tag.SOPInstanceUID, sopInstanceUID, result);
        // file_tsuid DICOM Transfer Syntax UID (0002,0010)
        Xml.addXmlAttribute(Tag.TransferSyntaxUID, transferSyntaxUID, result);
        Xml.addXmlAttribute(Tag.InstanceNumber, instanceNumber, result);
        Xml.addXmlAttribute(TagW.DirectDownloadFile, directDownloadFile, result);
        result.append("/>");
    }

    public static int compareInstanceNumber(SOPInstance o1, SOPInstance o2) {
        Integer val1 = StringUtil.getInteger(o1.getInstanceNumber());
        Integer val2 = StringUtil.getInteger(o2.getInstanceNumber());

        int c = -1;
        if (val1 != null && val2 != null) {
            c = val1.compareTo(val2);
            if (c != 0) {
                return c;
            }
        }

        if (c == 0 || (val1 == null && val2 == null)) {
            return o1.getSOPInstanceIUID().compareTo(o2.getSOPInstanceIUID());
        } else {
            if (val1 == null) {
                // Add o1 after o2
                return 1;
            }
            if (val2 == null) {
                return -1;
            }
        }

        return o1.getSOPInstanceIUID().compareTo(o2.getSOPInstanceIUID());
    }
}
