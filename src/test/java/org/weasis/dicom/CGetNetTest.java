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

import java.io.IOException;

import org.apache.log4j.BasicConfigurator;
import org.dcm4che3.data.Tag;
import org.dcm4che3.net.Status;
import org.hamcrest.MatcherAssert;
import org.hamcrest.core.IsEqual;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.weasis.dicom.op.CGet;
import org.weasis.dicom.param.DicomNode;
import org.weasis.dicom.param.DicomParam;
import org.weasis.dicom.param.DicomProgress;
import org.weasis.dicom.param.DicomState;
import org.weasis.dicom.param.ProgressListener;

public class CGetNetTest {
    @Rule
    public TemporaryFolder testFolder = new TemporaryFolder();

    @Test
    public void testProcess() throws IOException {
        BasicConfigurator.configure();

        DicomProgress progress = new DicomProgress();
        progress.addProgressListener(progress1 -> {
            System.out.println("Remaining operations:" + progress1.getNumberOfRemainingSuboperations());
            // if (progress.getNumberOfRemainingSuboperations() == 100) {
            // progress.cancel();
            // }
        });

        /**
         * The following parameters must be changed to get a successful test.
         */

        DicomParam[] params = { new DicomParam(Tag.StudyInstanceUID, "1.2.528.1.1001.100.2.3865.6101.93503564261.20070711142700372") };
        DicomNode calling = new DicomNode("WEASIS-SCU");
        DicomNode called = new DicomNode("DICOMSERVER", "dicomserver.co.uk", 11112);

        DicomState state = CGet.process(calling, called, progress, testFolder.newFolder("c-get"), params);

        // Should never happen
        Assert.assertNotNull(state);

        System.out.println("DICOM Status:" + state.getStatus());
        System.out.println(state.getMessage());
        System.out.println("NumberOfRemainingSuboperations:" + progress.getNumberOfRemainingSuboperations());
        System.out.println("NumberOfCompletedSuboperations:" + progress.getNumberOfCompletedSuboperations());
        System.out.println("NumberOfFailedSuboperations:" + progress.getNumberOfFailedSuboperations());
        System.out.println("NumberOfWarningSuboperations:" + progress.getNumberOfWarningSuboperations());

        // see org.dcm4che3.net.Status
        // See server log at http://dicomserver.co.uk/logs/
        MatcherAssert.assertThat(state.getMessage(), state.getStatus(), IsEqual.equalTo(Status.Success));
    }

}
