/*
 * Copyright (c) 2014-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at http://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom;

import java.net.MalformedURLException;
import java.util.*;
import org.apache.log4j.BasicConfigurator;
import org.dcm4che6.data.DicomObject;
import org.dcm4che6.data.Tag;
import org.dcm4che6.data.VR;
import org.dcm4che6.net.Status;
import org.hamcrest.core.IsEqual;
import org.junit.Assert;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
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
import org.weasis.dicom.tool.DicomGateway;
import org.weasis.dicom.web.WebForwardDestination;

public class DicomGatewayMultiDestNetTest {

  @BeforeAll
  public static void setLogger() throws MalformedURLException {
    BasicConfigurator.configure();
  }

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
    DicomObject dcm = DicomObject.newDicomObject();
    dcm.setString(Tag.PatientName, VR.PN, "Override^Patient^Name");
    dcm.setString(Tag.PatientID, VR.LO, "ModifiedPatientID");
    DefaultAttributeEditor editor = new DefaultAttributeEditor(true, dcm);

    DicomProgress progress = new DicomProgress();
    progress.addProgressListener(
        p -> {
          if (p.isLastFailed()) {
            System.out.println("Failed: DICOM Status:" + p.getStatus());
          }
        });
    List<ForwardDestination> list = new ArrayList<>();
    ForwardDicomNode fwdSrcNode = new ForwardDicomNode(destination.getAet());
    fwdSrcNode.addAcceptedSourceNode(calling.getAet(), "localhost");
    WebForwardDestination web =
        new WebForwardDestination(
            fwdSrcNode,
            "http://localhost:8080/dcm4chee-arc/aets/DCM4CHEE/rs/studies",
            progress,
            Arrays.asList(editor));
    list.add(web);
    destinations.put(fwdSrcNode, list);

    GatewayParams gparams =
        new GatewayParams(
            params, false, null, GatewayParams.getAcceptedCallingAETitles(destinations));

    DicomGateway gateway;
    try {
      gateway = new DicomGateway(destinations);
      gateway.start(scpNode, gparams);
    } catch (Exception e) {
      e.printStackTrace();
    }

    DicomProgress progress2 = new DicomProgress();
    progress2.addProgressListener(
        progress1 -> {
          System.out.println("DICOM Status:" + progress1.getStatus());
          System.out.println(
              "NumberOfRemainingSuboperations:" + progress1.getNumberOfRemainingSuboperations());
          System.out.println(
              "NumberOfCompletedSuboperations:" + progress1.getNumberOfCompletedSuboperations());
          System.out.println(
              "NumberOfFailedSuboperations:" + progress1.getNumberOfFailedSuboperations());
          System.out.println(
              "NumberOfWarningSuboperations:" + progress1.getNumberOfWarningSuboperations());
          if (progress1.isLastFailed()) {
            System.out.println("Last file has failed:" + progress1.getProcessedFile());
          }
        });

    String studyUID = "1.2.826.0.1.3680043.11.111";
    DicomState state =
        CGetForward.processStudy(params, params, calling, called, destination, progress2, studyUID);
    // String seriesUID = "1.2.528.1.1001.100.3.3865.6101.93503564261.20070711142700388";
    // DicomState state = CGetForward.processSeries(params, params, calling, called, destination,
    // progress2, seriesUID);

    // Force to write endmarks and stop the connection
    web.stop();

    // Should never happen
    Assert.assertNotNull(state);

    System.out.println("DICOM Status for retrieving:" + state.getStatus());
    // see org.dcm4che3.net.Status
    // See server log at http://dicomserver.co.uk/logs/
    Assert.assertThat(state.getMessage(), state.getStatus(), IsEqual.equalTo(Status.Pending));

    System.out.println("DICOM Status for forwarding:" + web.getState().getStatus());
    Assert.assertThat(
        web.getState().getMessage(), web.getState().getStatus(), IsEqual.equalTo(Status.Pending));
  }
}
