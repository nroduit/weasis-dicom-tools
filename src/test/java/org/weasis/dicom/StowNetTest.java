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
package org.weasis.dicom;

import java.io.FileInputStream;
import java.util.ArrayList;
import java.util.List;

import org.apache.log4j.BasicConfigurator;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.VR;
import org.dcm4che3.io.DicomInputStream;
import org.dcm4che3.io.DicomInputStream.IncludeBulkData;
import org.dcm4che3.util.UIDUtils;
import org.junit.Assert;
import org.junit.Test;
import org.weasis.dicom.web.StowRS;
import org.weasis.dicom.web.StowRS.ContentType;

public class StowNetTest {

    @Test
    public void testProcess() {
        BasicConfigurator.configure();

        List<String> files = new ArrayList<>();
        files.add("/home/nicolas/Data/Downloads/stow-dcm");
        // try {
        // files.add(new File(getClass().getResource("mr.dcm").toURI()).getPath());
        // } catch (URISyntaxException e) {
        // e.printStackTrace();
        // }

        String stowService = "http://192.168.172.130:8080/dcm4chee-arc/aets/DCM4CHEE/rs/studies";
        String message = null;
        
        try (StowRS stowRS = new StowRS(stowService, ContentType.DICOM)) {
            message = stowRS.uploadDicom(files, true);
        } catch (Exception e) {
            message = e.getMessage();
            System.out.println("StowRS error: {}" + message);
        }
        Assert.assertNull(message, message);
        
        try (StowRS stowRS = new StowRS(stowService, ContentType.DICOM)) {
            DicomInputStream in = new DicomInputStream(new FileInputStream(files.get(0)));
            in.setIncludeBulkData(IncludeBulkData.URI);
            Attributes attributes = in.readDataset(-1, Tag.PixelData); 
            attributes.setString(Tag.PatientName, VR.PN, "Override^Patient^Name");
            attributes.setString(Tag.PatientID, VR.LO, "ModifiedPatientID");
            attributes.setString(Tag.StudyInstanceUID, VR.UI, UIDUtils.createUID());
            attributes.setString(Tag.SeriesInstanceUID, VR.UI, UIDUtils.createUID());
            attributes.setString(Tag.SOPInstanceUID, VR.UI, UIDUtils.createUID());
            if (in.tag() == Tag.PixelData) {
                in.readValue(in, attributes);
                in.readAttributes(attributes, -1, -1);
            }
            stowRS.uploadDicom(attributes, in.getTransferSyntax());
            message = stowRS.writeEndMarkers();
        } catch (Exception e) {
            message = e.getMessage();
            System.out.println("StowRS error: {}" + message);
        }
        Assert.assertNull(message, message);
    }

}
