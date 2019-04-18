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

import java.io.File;
import java.io.FileInputStream;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;

import org.apache.log4j.BasicConfigurator;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.VR;
import org.dcm4che3.io.DicomInputStream;
import org.dcm4che3.io.DicomInputStream.IncludeBulkData;
import org.dcm4che3.util.UIDUtils;
import org.junit.Test;
import org.weasis.core.api.util.StringUtil;
import org.weasis.dicom.param.DicomState;
import org.weasis.dicom.web.AbstractStowrs.ContentType;
import org.weasis.dicom.web.StowrsMultiFiles;
import org.weasis.dicom.web.StowrsSingleFile;
import org.weasis.dicom.web.UploadSingleFile;

public class StowNetTest {

    @Test
    public void testProcess() {
        BasicConfigurator.configure();

        List<String> files = new ArrayList<>();
        try {
            files.add(new File(getClass().getResource("mr.dcm").toURI()).getPath());
        } catch (URISyntaxException e) {
            e.printStackTrace();
        }

        String stowService = "http://localhost:8080/dcm4chee-arc/aets/DCM4CHEE/rs/studies";
        String message = null;

        // Upload files
        try (StowrsMultiFiles stowRS = new StowrsMultiFiles(stowService, ContentType.DICOM, null, null)) {
            DicomState state = stowRS.uploadDicom(files, true);
            message = state.getMessage();
        } catch (Exception e) {
            message = e.getMessage();
        }
        
        if (StringUtil.hasText(message)) {
            System.out.println("StowRS error: " + message);
        }

        // Upload a modify file
        try (UploadSingleFile stowRS = new StowrsSingleFile(stowService, ContentType.DICOM);
                        DicomInputStream in = new DicomInputStream(new FileInputStream(files.get(0)))) {
            in.setIncludeBulkData(IncludeBulkData.URI);
            Attributes attributes = in.readDataset(-1, -1);
            attributes.setString(Tag.PatientName, VR.PN, "Override^Patient^Name");
            attributes.setString(Tag.PatientID, VR.LO, "ModifiedPatientID");
            attributes.setString(Tag.StudyInstanceUID, VR.UI, UIDUtils.createUID());
            attributes.setString(Tag.SeriesInstanceUID, VR.UI, UIDUtils.createUID());
            attributes.setString(Tag.SOPInstanceUID, VR.UI, UIDUtils.createUID());

            stowRS.uploadDicom(attributes, in.getTransferSyntax());
        } catch (Exception e) {
            message = e.getMessage();
            System.out.println("StowRS error: " + message);
        }
    }

}
