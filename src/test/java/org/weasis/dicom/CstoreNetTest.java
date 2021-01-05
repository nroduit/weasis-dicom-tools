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
import java.util.ArrayList;
import java.util.List;
import org.apache.log4j.BasicConfigurator;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.VR;
import org.dcm4che3.net.Status;
import org.hamcrest.core.IsEqual;
import org.junit.Assert;
import org.junit.Test;
import org.weasis.dicom.op.CStore;
import org.weasis.dicom.param.AdvancedParams;
import org.weasis.dicom.param.ConnectOptions;
import org.weasis.dicom.param.CstoreParams;
import org.weasis.dicom.param.DefaultAttributeEditor;
import org.weasis.dicom.param.DicomNode;
import org.weasis.dicom.param.DicomProgress;
import org.weasis.dicom.param.DicomState;

public class CstoreNetTest {

  @Test
  public void testProcess() {
    BasicConfigurator.configure();

    AdvancedParams params = new AdvancedParams();
    ConnectOptions connectOptions = new ConnectOptions();
    connectOptions.setConnectTimeout(3000);
    connectOptions.setAcceptTimeout(5000);
    params.setConnectOptions(connectOptions);

    DicomProgress progress = new DicomProgress();
    progress.addProgressListener(
        progress1 -> {
          System.out.println("DICOM Status:" + progress1.getStatus());
          if (progress1.isLastFailed()) {
            System.out.println("Last file has failed:" + progress1.getProcessedFile());
          }
        });

    DicomNode calling = new DicomNode("WEASIS-SCU");
    DicomNode called = new DicomNode("DICOMSERVER", "dicomserver.co.uk", 11112);
    // DicomNode called = new DicomNode("DCM4CHEE", "localhost", 11112);
    List<String> files = new ArrayList<>();
    try {
      files.add(new File(getClass().getResource("mr.dcm").toURI()).getPath());
    } catch (URISyntaxException e) {
      e.printStackTrace();
    }

    Attributes attrs = new Attributes();
    attrs.setString(Tag.PatientName, VR.PN, "Override^Patient^Name");
    attrs.setString(Tag.PatientID, VR.LO, "ModifiedPatientID");
    DefaultAttributeEditor editor = new DefaultAttributeEditor(false, attrs);
    CstoreParams cstoreParams = new CstoreParams(editor, false, null);

    DicomState state = CStore.process(params, calling, called, files, progress, cstoreParams);
    // Should never happen
    Assert.assertNotNull(state);

    System.out.println("DICOM Status:" + state.getStatus());
    System.out.println(state.getMessage());

    // see org.dcm4che3.net.Status
    // See server log at http://dicomserver.co.uk/logs/
    Assert.assertThat(state.getMessage(), state.getStatus(), IsEqual.equalTo(Status.Success));
  }
}
