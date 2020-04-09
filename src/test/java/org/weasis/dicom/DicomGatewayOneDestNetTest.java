package org.weasis.dicom;

import java.io.IOException;

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

import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.log4j.BasicConfigurator;
import org.dcm4che6.data.DicomObject;
import org.dcm4che6.data.Tag;
import org.dcm4che6.data.VR;
import org.dcm4che6.net.Status;
import org.hamcrest.core.IsEqual;
import org.junit.Assert;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.weasis.dicom.op.CStore;
import org.weasis.dicom.param.AdvancedParams;
import org.weasis.dicom.param.ConnectOptions;
import org.weasis.dicom.param.CstoreParams;
import org.weasis.dicom.param.DefaultAttributeEditor;
import org.weasis.dicom.param.DicomForwardDestination;
import org.weasis.dicom.param.DicomNode;
import org.weasis.dicom.param.DicomProgress;
import org.weasis.dicom.param.DicomState;
import org.weasis.dicom.param.ForwardDestination;
import org.weasis.dicom.param.ForwardDicomNode;
import org.weasis.dicom.param.GatewayParams;
import org.weasis.dicom.tool.DicomGateway;

public class DicomGatewayOneDestNetTest {

    @BeforeAll
    public static void setLogger() throws MalformedURLException {
        BasicConfigurator.configure();
    }

    @Test
    public void testProcess() {
        AdvancedParams params = new AdvancedParams();
        ConnectOptions connectOptions = new ConnectOptions();
        connectOptions.setConnectTimeout(3000);
        connectOptions.setAcceptTimeout(5000);
        // Concurrent DICOM operations
        connectOptions.setMaxOpsInvoked(15);
        connectOptions.setMaxOpsPerformed(15);
        params.setConnectOptions(connectOptions);

        ForwardDicomNode calling = new ForwardDicomNode("FWD-AET", "localhost");
        DicomNode called = new DicomNode("DICOMSERVER", "dicomserver.co.uk", 11112);
        DicomNode destination = new DicomNode("DCM4CHEE", "localhost", 11112);
        DicomNode scpNode = new DicomNode("KARNAK", "localhost", 11113);
        DicomNode fwNode = new DicomNode("EXT-TEST", "127.0.0.1", 11113);

        DicomObject dcm = DicomObject.newDicomObject();
        dcm.setString(Tag.PatientName, VR.PN, "Override^Patient^Name");
        dcm.setString(Tag.PatientID, VR.LO, "ModifiedPatientID");
        DefaultAttributeEditor editor = new DefaultAttributeEditor(true, dcm);

        GatewayParams gparams = new GatewayParams(params, false, null, calling.getAet());

        DicomGateway gateway;
        try {
            Map<ForwardDicomNode, List<ForwardDestination>> destinations = new HashMap<>();
            List<ForwardDestination> list = new ArrayList<>();
            ForwardDicomNode fwdSrcNode = new ForwardDicomNode(fwNode.getAet());
            fwdSrcNode.addAcceptedSourceNode(calling.getAet(), "localhost");
            DicomForwardDestination dest = new DicomForwardDestination(null, fwdSrcNode, destination, editor);
            list.add(dest);
            destinations.put(fwdSrcNode, list);
            gateway = new DicomGateway(destinations);
            gateway.start(scpNode, gparams);
        } catch (Exception e) {
            e.printStackTrace();
            throw new IllegalStateException("Cannot start the DICOM gateway");
        }

        DicomProgress progress = new DicomProgress();
        progress.addProgressListener(progress1 -> {
            System.out.println("DICOM Status:" + progress1.getStatus());
            System.out.println("NumberOfRemainingSuboperations:" + progress1.getNumberOfRemainingSuboperations());
            System.out.println("NumberOfCompletedSuboperations:" + progress1.getNumberOfCompletedSuboperations());
            System.out.println("NumberOfFailedSuboperations:" + progress1.getNumberOfFailedSuboperations());
            System.out.println("NumberOfWarningSuboperations:" + progress1.getNumberOfWarningSuboperations());
            if (progress1.isLastFailed()) {
                System.out.println("Last file has failed:" + progress1.getProcessedFile());
            }
        });

        // String studyUID = "1.2.826.0.1.3680043.11.111";
        // DicomNode calling2 = new DicomNode("WEASIS-SCU");
        // DicomState state = CGetForward.processStudy(params, params, calling2, called, scpNode, progress, studyUID);
        List<Path> files = new ArrayList<>();
        try {
    //        files.add(Path.of(getClass().getResource("mr.dcm").toURI()));
            files.add(Path.of(getClass().getResource("jpeg2000-multiframe-multifragments.dcm").toURI()));
        } catch (URISyntaxException e) {
            e.printStackTrace();
        }
        CstoreParams cstoreParams = new CstoreParams(null, false, null);
        DicomState state = CStore.process(params, calling, fwNode, files, progress, cstoreParams);
        try {
            gateway.stop();
        } catch (IOException e) {
            e.printStackTrace();
        }

        // Should never happen
        Assert.assertNotNull(state);

        System.out.println("DICOM Status:" + state.getStatus());
        System.out.println(state.getMessage());

        // see org.dcm4che3.net.Status
        // See server log at http://dicomserver.co.uk/logs/
        Assert.assertThat(state.getMessage(), state.getStatus(), IsEqual.equalTo(Status.Pending));
    }

}
