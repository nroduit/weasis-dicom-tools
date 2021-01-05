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

import java.io.File;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.apache.log4j.BasicConfigurator;
import org.dcm4che6.net.Status;
import org.hamcrest.core.IsEqual;
import org.junit.Assert;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.weasis.dicom.op.CStore;
import org.weasis.dicom.param.AdvancedParams;
import org.weasis.dicom.param.ConnectOptions;
import org.weasis.dicom.param.CstoreParams;
import org.weasis.dicom.param.DicomNode;
import org.weasis.dicom.param.DicomProgress;
import org.weasis.dicom.param.DicomState;
import org.weasis.dicom.param.ListenerParams;
import org.weasis.dicom.tool.DicomListener;

public class DicomListenerNetTest {
  @TempDir File testFolder;

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

    // String TEST_PATTERN =
    // "{00080020,date,yyyy/MM/dd}/{00080030,time,HH}/{0020000D,hash}/{0020000E,hash}/{00080008[1]}/{00080018}.dcm";
    String TEST_PATTERN = "{00020016}/{00020003}.dcm";
    ListenerParams lparams =
        new ListenerParams(params, false, TEST_PATTERN, null, calling.getAet());

    DicomListener listener;
    try {
      listener = new DicomListener(testFolder.toPath());
      listener.start(scpNode, lparams);
    } catch (Exception e) {
      e.printStackTrace();
      throw new IllegalStateException("Cannot start the DICOM listener");
    }

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

    //        String studyUID = "1.2.826.0.1.3680043.11.111";
    //        DicomState state = CGetForward.processStudy(params, params, calling, called, scpNode,
    // progress, studyUID);

    List<Path> files = new ArrayList<>();
    try {
      files.add(Path.of(getClass().getResource("mr.dcm").toURI()));
    } catch (URISyntaxException e) {
      e.printStackTrace();
    }
    CstoreParams cstoreParams = new CstoreParams(null, false, null);
    DicomState state = CStore.process(params, calling, scpNode, files, progress, cstoreParams);
    scpNode = new DicomNode("DICOMLISTENER", "localhost", 11119);
    try {
      listener.stop();
      listener.start(scpNode, lparams);
    } catch (Exception e) {
      e.printStackTrace();
      throw new IllegalStateException("Cannot start the DICOM listener");
    }
    state = CStore.process(params, calling, scpNode, files, progress, cstoreParams);

    // Should never happen
    Assert.assertNotNull(state);

    System.out.println("DICOM Status:" + state.getStatus());
    System.out.println(state.getMessage());

    // see org.dcm4che3.net.Status
    // See server log at http://dicomserver.co.uk/logs/
    Assert.assertThat(state.getMessage(), state.getStatus(), IsEqual.equalTo(Status.Pending));
  }
}
