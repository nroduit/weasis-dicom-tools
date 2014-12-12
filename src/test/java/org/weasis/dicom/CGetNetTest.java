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

import org.dcm4che3.data.Tag;
import org.dcm4che3.net.Status;
import org.dcm4che3.tool.getscu.GetSCU;
import org.hamcrest.core.IsEqual;
import org.junit.Assert;
import org.junit.Test;
import org.weasis.dicom.op.CGet;
import org.weasis.dicom.param.DicomNode;
import org.weasis.dicom.param.DicomParam;
import org.weasis.dicom.param.DicomProgress;
import org.weasis.dicom.param.DicomState;
import org.weasis.dicom.param.ProgressListener;
import org.weasis.dicom.util.FileUtil;

public class CGetNetTest {

    @Test
    public void testProcess() {
        DicomProgress progress = new DicomProgress();
        progress.addProgressListener(new ProgressListener() {

            @Override
            public void handleProgression(DicomProgress progress) {
                System.out.println("Remaining operations:" + progress.getNumberOfRemainingSuboperations());
                // if (progress.getNumberOfRemainingSuboperations() == 100) {
                // progress.cancel();
                // }
            }
        });

        /**
         * The following parameters must be changed to get a successful test.
         */
        DicomParam[] params = { new DicomParam(Tag.StudyInstanceUID, "2.16.840.1.113669.632.20.1211.10000235106") };
        DicomNode calling = new DicomNode("COMTELIM");
        DicomNode called = new DicomNode("DCM4CHEE", "znfulton.hcuge.ch", 11112);

        // DicomParam[] params =
        // { new DicomParam(Tag.StudyInstanceUID, "1.2.826.0.1.3680043.9.4113.1.2.1754115794.5304.1404814421.494") };
        // DicomNode calling = new DicomNode("WEASIS-SCU");
        // DicomNode called = new DicomNode("DICOMSERVER", "dicomserver.co.uk", 11112);
        GetSCU getSCU = CGet.build(calling, called, progress, FileUtil.getTemporaryDirectory("dicom-cache"), params);

        // Should never happen
        Assert.assertNotNull(getSCU);

        System.out.println("DICOM Status:" + progress.getStatus());
        System.out.println("NumberOfRemainingSuboperations:" + progress.getNumberOfRemainingSuboperations());
        System.out.println("NumberOfCompletedSuboperations:" + progress.getNumberOfCompletedSuboperations());
        System.out.println("NumberOfFailedSuboperations:" + progress.getNumberOfFailedSuboperations());
        System.out.println("NumberOfWarningSuboperations:" + progress.getNumberOfWarningSuboperations());

        DicomState state = getSCU.getState();
        // see org.dcm4che3.net.Status
        // See server log at http://dicomserver.co.uk/logs/
        Assert.assertThat(state.getMessage(), state.getStatus(), IsEqual.equalTo(Status.Success));
        Assert.assertFalse("No DICOM RSP Object", state.getDicomRSP().isEmpty());
    }

}
