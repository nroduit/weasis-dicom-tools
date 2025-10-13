/*
 * Copyright (c) 2014-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.real;

import java.nio.file.Path;
import org.dcm4che3.net.Status;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.weasis.dicom.op.CGetForward;
import org.weasis.dicom.param.*;
import org.weasis.dicom.tool.DicomListener;

@DisplayName("DICOM Listener")
class DicomListenerIT {

  @TempDir public Path testFolder;

  @Test
  void testProcess() {
    AdvancedParams params = new AdvancedParams();
    ConnectOptions connectOptions = new ConnectOptions();
    connectOptions.setConnectTimeout(3000);
    connectOptions.setAcceptTimeout(5000);
    // Concurrent DICOM operations
    connectOptions.setMaxOpsInvoked(15);
    connectOptions.setMaxOpsPerformed(15);
    params.setConnectOptions(connectOptions);
    DicomNode calling = new DicomNode("WEASIS-SCU");
    DicomNode called = new DicomNode("DICOMSERVER", "www.dicomserver.co.uk", 104);
    DicomNode scpNode = new DicomNode("DICOMLISTENER", "localhost", 11113);
    // String TEST_PATTERN =
    // "{00080020,date,yyyy/MM/dd}/{00080030,time,HH}/{0020000D,hash}/{0020000E,hash}/{00080008[1]}/{00080018}.dcm";
    String TEST_PATTERN = "{00020016}/{00020003}.dcm";
    ListenerParams lparams =
        new ListenerParams(params, false, TEST_PATTERN, null, calling.getAet());
    DicomListener listener;
    try {
      listener = new DicomListener(Path.of(testFolder.toString(), "tmp-dcm-listener"));
      listener.start(scpNode, lparams);
    } catch (Exception e) {
      System.err.println(e.getMessage());
    }
    DicomProgress progress = getDicomProgress();
    String studyUID = "1.2.528.1.1001.100.2.3865.6101.93503564261.20070711142700372";
    DicomState state =
        CGetForward.processStudy(params, params, calling, called, scpNode, progress, studyUID);

    // Should never happen
    Assertions.assertNotNull(state);
    System.out.println("DICOM Status:" + state.getStatus());
    System.out.println(state.getMessage());

    // see org.dcm4che3.net.Status
    // See server log at https://dicomserver.co.uk/logs/
    Assertions.assertEquals(Status.Success, state.getStatus(), state.getMessage());
  }

  private static DicomProgress getDicomProgress() {
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
    return progress;
  }
}
