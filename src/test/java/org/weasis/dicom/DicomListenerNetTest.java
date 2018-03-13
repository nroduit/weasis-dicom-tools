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

import org.apache.log4j.BasicConfigurator;
import org.dcm4che3.net.Status;
import org.hamcrest.core.IsEqual;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.weasis.dicom.op.CGetForward;
import org.weasis.dicom.param.AdvancedParams;
import org.weasis.dicom.param.ConnectOptions;
import org.weasis.dicom.param.DicomNode;
import org.weasis.dicom.param.DicomProgress;
import org.weasis.dicom.param.DicomState;
import org.weasis.dicom.param.ListenerParams;
import org.weasis.dicom.param.ProgressListener;
import org.weasis.dicom.tool.DicomListener;

public class DicomListenerNetTest {
    @Rule
    public TemporaryFolder testFolder = new TemporaryFolder();
    
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
        ListenerParams lparams = new ListenerParams(params, false, TEST_PATTERN, null, calling.getAet());

        DicomListener listener;
        try {
            listener = new DicomListener(testFolder.newFolder("tmp-dcm-listener"));
            listener.start(scpNode, lparams);
        } catch (Exception e) {
            e.printStackTrace();
        }

        DicomProgress progress = new DicomProgress();
        progress.addProgressListener(new ProgressListener() {

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

        DicomState state = CGetForward.processStudy(params, params, calling, called, scpNode, progress, studyUID);

        // Should never happen
        Assert.assertNotNull(state);

        System.out.println("DICOM Status:" + state.getStatus());
        System.out.println(state.getMessage());

        // see org.dcm4che3.net.Status
        // See server log at http://dicomserver.co.uk/logs/
        Assert.assertThat(state.getMessage(), state.getStatus(), IsEqual.equalTo(Status.Success));
    }

}
