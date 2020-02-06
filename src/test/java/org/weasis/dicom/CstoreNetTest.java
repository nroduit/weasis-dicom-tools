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

import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.OptionalInt;

import org.apache.log4j.BasicConfigurator;
import org.dcm4che6.data.DicomObject;
import org.dcm4che6.data.Tag;
import org.dcm4che6.data.VR;
import org.dcm4che6.net.Status;
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
        progress.addProgressListener(progress1 -> {
            System.out.println("DICOM Status:" + progress1.getStatus());
            if (progress1.isLastFailed()) {
                System.out.println("Last file has failed:" + progress1.getProcessedFile());
            }
        });

        DicomNode calling = new DicomNode("WEASIS-SCU");
        DicomNode called = new DicomNode("DICOMSERVER", "dicomserver.co.uk", 11112);
     //    DicomNode called = new DicomNode("DCM4CHEE", "localhost", 11117);
        List<Path> files = new ArrayList<>();
        try {
            files.add(Path.of(getClass().getResource("mr.dcm").toURI()));
        } catch (URISyntaxException e) {
            e.printStackTrace();
        }

        DicomObject attrs = DicomObject.newDicomObject();
        attrs.setString(Tag.PatientName, VR.PN, "Override^Patient^Name");
        attrs.setString(Tag.PatientID, VR.LO, "ModifiedPatientID");
        DefaultAttributeEditor editor = new DefaultAttributeEditor(true, attrs);
        CstoreParams cstoreParams = new CstoreParams(editor, false, null);

        DicomState state = CStore.process(params, calling, called, files, progress, cstoreParams);
        // Should never happen
        Assert.assertNotNull(state);

        // See server log at http://dicomserver.co.uk/logs/
        System.out.println("DicomState result: ");
        System.out.println("\tDICOM Status: " + state.getStatus()); // see org.dcm4ch6.net.Status
        System.out.println("\t" + state.getMessage());

        Assert.assertThat(state.getMessage(), state.getStatus(), IsEqual.equalTo(OptionalInt.of(Status.Success)));
    }

}
