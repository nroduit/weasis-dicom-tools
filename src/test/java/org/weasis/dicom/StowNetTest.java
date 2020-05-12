/*******************************************************************************
 * Copyright (c) 2009-2019 Weasis Team and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 *
 * Contributors:
 *     Nicolas Roduit - initial API and implementation
 *******************************************************************************/
package org.weasis.dicom;

import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.apache.log4j.BasicConfigurator;
import org.dcm4che6.data.DicomObject;
import org.dcm4che6.data.Tag;
import org.dcm4che6.data.VR;
import org.dcm4che6.io.DicomInputStream;
import org.dcm4che6.util.UIDUtils;
import org.hamcrest.core.IsNull;
import org.junit.Assert;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.weasis.dicom.param.DicomState;
import org.weasis.dicom.web.ContentType;
import org.weasis.dicom.web.DicomStowRS;

public class StowNetTest {
    
    @BeforeAll
    public static void setLogger() throws MalformedURLException {
        BasicConfigurator.configure();
    }

    @Test
    public void testProcess() {
        List<Path> files = new ArrayList<>();
        try {
            files.add(Path.of(getClass().getResource("mr.dcm").toURI()));
        } catch (URISyntaxException e) {
            e.printStackTrace();
        }

        String stowService = "http://192.168.0.31:8080/dcm4chee-arc/aets/DCM4CHEE/rs/studies";
        DicomState state = null;

        // Upload files
//        try (DicomStowRS stowRS = new DicomStowRS(stowService, ContentType.APPLICATION_DICOM, null, null)) {
//            state = stowRS.uploadDicom(files, true);
//        } catch (Exception e) {
//            System.out.println("StowRS error: " + e.getMessage());
//        }
//
//        Assert.assertThat("DicomState cannot be null", state, IsNull.notNullValue());
//        Assert.assertThat(state.getMessage(), state.getStatus(), IsEqual.equalTo(Status.Success));

        String message = null;
        // Upload a modify file
        Path p1 = files.get(0);
        try (DicomStowRS stowRS = new DicomStowRS(stowService, ContentType.APPLICATION_DICOM, null, null);
                        DicomInputStream dis = new DicomInputStream(Files.newInputStream(p1))) {
            dis.withBulkData(DicomInputStream::isBulkData).withBulkDataURI(p1);
            DicomObject data = dis.readDataSet();
            data.setString(Tag.PatientName, VR.PN, "Override^Patient^Name");
            data.setString(Tag.PatientID, VR.LO, "ModifiedPatientID");
            data.setString(Tag.StudyInstanceUID, VR.UI, UIDUtils.randomUID());
            data.setString(Tag.SeriesInstanceUID, VR.UI, UIDUtils.randomUID());
            data.setString(Tag.SOPInstanceUID, VR.UI, UIDUtils.randomUID());

            stowRS.uploadDicom(data, dis.getEncoding().transferSyntaxUID);
        } catch (Exception e) {
            message = e.getMessage();
        }
        Assert.assertThat(message, message, IsNull.nullValue());
    }
}
