/*
 * Copyright (c) 2014-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom;

import java.util.List;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.VR;
import org.dcm4che3.net.Status;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.weasis.dicom.op.CGetForward;
import org.weasis.dicom.param.*;

@DisplayName("DICOM C-GET forward")
class CGetForwardIT {

  @Test
  void testProcess() {
    DicomProgress progress = new DicomProgress();
    progress.addProgressListener(
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
    AdvancedParams params = new AdvancedParams();
    ConnectOptions connectOptions = new ConnectOptions();
    connectOptions.setConnectTimeout(3000);
    connectOptions.setAcceptTimeout(5000);
    params.setConnectOptions(connectOptions);
    DicomNode calling = new DicomNode("WEASIS-SCU");
    DicomNode called = new DicomNode("DICOMSERVER", "www.dicomserver.co.uk", 104);
    DicomNode destination = new DicomNode("DCM4CHEE", "localhost", 11112);
    String studyUID = "1.2.528.1.1001.100.2.3865.6101.93503564261.20070711142700372";
    Attributes attrs = new Attributes();
    attrs.setString(Tag.PatientName, VR.PN, "Override^Patient^Name");
    attrs.setString(Tag.PatientID, VR.LO, "ModifiedPatientID");
    DefaultAttributeEditor editor = new DefaultAttributeEditor(true, attrs);
    CstoreParams cstoreParams = new CstoreParams(List.of(editor), false, null);
    DicomState state =
        CGetForward.processStudy(
            params, params, calling, called, destination, progress, studyUID, cstoreParams);
    // Should never happen
    Assertions.assertNotNull(state);
    System.out.println("DICOM Status:" + state.getStatus());
    System.out.println(state.getMessage());
    System.out.println(
        "NumberOfRemainingSuboperations:" + progress.getNumberOfRemainingSuboperations());
    System.out.println(
        "NumberOfCompletedSuboperations:" + progress.getNumberOfCompletedSuboperations());
    System.out.println("NumberOfFailedSuboperations:" + progress.getNumberOfFailedSuboperations());
    System.out.println(
        "NumberOfWarningSuboperations:" + progress.getNumberOfWarningSuboperations());
    // see org.dcm4che3.net.Status
    // See server log at https://dicomserver.co.uk/logs/
    Assertions.assertEquals(Status.Success, state.getStatus(), state.getMessage());
  }
}
