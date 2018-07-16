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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.log4j.BasicConfigurator;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.VR;
import org.dcm4che3.net.Status;
import org.hamcrest.core.IsEqual;
import org.junit.Assert;
import org.junit.Test;
import org.weasis.dicom.op.CGetForward;
import org.weasis.dicom.param.AdvancedParams;
import org.weasis.dicom.param.ConnectOptions;
import org.weasis.dicom.param.DefaultAttributeEditor;
import org.weasis.dicom.param.DicomNode;
import org.weasis.dicom.param.DicomProgress;
import org.weasis.dicom.param.DicomState;
import org.weasis.dicom.param.ForwardDestination;
import org.weasis.dicom.param.ForwardDicomNode;
import org.weasis.dicom.param.GatewayParams;
import org.weasis.dicom.param.ProgressListener;
import org.weasis.dicom.tool.DicomGateway;
import org.weasis.dicom.web.WebForwardDestination;

public class DicomGatewayMultiDestNetTest {

    @Test
    public void testProcess() {
        BasicConfigurator.configure();

        AdvancedParams params = new AdvancedParams();
        ConnectOptions connectOptions = new ConnectOptions();
        connectOptions.setConnectTimeout(3000);
        connectOptions.setAcceptTimeout(5000);
        // Concurrent DICOM operations
        connectOptions.setMaxOpsInvoked(15);
        connectOptions.setMaxOpsPerformed(15);
        params.setConnectOptions(connectOptions);

        DicomNode calling = new DicomNode("WEASIS-SCU");
        DicomNode called = new DicomNode("DICOMSERVER", "dicomserver.co.uk", 11112);
        DicomNode scpNode = new DicomNode("DICOMLISTENER", "localhost", 11113);
        DicomNode destination = new DicomNode("FWD-AET", "localhost", 11113);

        Map<ForwardDicomNode, List<ForwardDestination>> destinations = new HashMap<>();
        Attributes attrs = new Attributes();
        attrs.setString(Tag.PatientName, VR.PN, "Override^Patient^Name");
        attrs.setString(Tag.PatientID, VR.LO, "ModifiedPatientID");
        DefaultAttributeEditor editor = new DefaultAttributeEditor(true, attrs);

        DicomProgress progress = new DicomProgress();
        progress.addProgressListener(p -> {
            if (p.isLastFailed()) {
                System.out.println("Failed: DICOM Status:" + p.getStatus());
            }
        });
        List<ForwardDestination> list = new ArrayList<>();
        ForwardDicomNode fwdSrcNode = new ForwardDicomNode(destination.getAet());
        fwdSrcNode.addAcceptedSourceNode(calling.getAet(), "localhost");
        WebForwardDestination web = new WebForwardDestination(fwdSrcNode,
            "http://localhost:8080/dcm4chee-arc/aets/DCM4CHEE/rs/studies", progress, editor);
        list.add(web);
        destinations.put(fwdSrcNode, list);

        GatewayParams gparams = new GatewayParams(params, false, null, GatewayParams.getAcceptedCallingAETitles(destinations));

        DicomGateway gateway;
        try {
            gateway = new DicomGateway(destinations);
            gateway.start(scpNode, gparams);
        } catch (Exception e) {
            e.printStackTrace();
        }

        DicomProgress progress2 = new DicomProgress();
        progress2.addProgressListener(new ProgressListener() {

            @Override
            public void handleProgression(DicomProgress progress) {
                System.out.println("DICOM Status:" + progress.getStatus());
                System.out.println("NumberOfRemainingSuboperations:" + progress.getNumberOfRemainingSuboperations());
                System.out.println("NumberOfCompletedSuboperations:" + progress.getNumberOfCompletedSuboperations());
                System.out.println("NumberOfFailedSuboperations:" + progress.getNumberOfFailedSuboperations());
                System.out.println("NumberOfWarningSuboperations:" + progress.getNumberOfWarningSuboperations());
                if (progress.isLastFailed()) {
                    System.out.println("Last file has failed:" + progress.getProcessedFile());
                }
            }
        });

        String studyUID = "1.2.826.0.1.3680043.11.105";
        DicomState state = CGetForward.processStudy(params, params, calling, called, destination, progress2, studyUID);
        // String seriesUID = "1.2.528.1.1001.100.3.3865.6101.93503564261.20070711142700388";
        // DicomState state = CGetForward.processSeries(params, params, calling, called, destination, progress2, seriesUID);

        // Force to write endmarks and stop the connection
        web.stop();

        // Should never happen
        Assert.assertNotNull(state);

        System.out.println("DICOM Status for retrieving:" + state.getStatus());
        // see org.dcm4che3.net.Status
        // See server log at http://dicomserver.co.uk/logs/
        Assert.assertThat(state.getMessage(), state.getStatus(), IsEqual.equalTo(Status.Success));
        
        System.out.println("DICOM Status for forwarding:" + web.getState().getStatus());
        Assert.assertThat(web.getState().getMessage(), web.getState().getStatus(), IsEqual.equalTo(Status.Success));
        
    }

}
