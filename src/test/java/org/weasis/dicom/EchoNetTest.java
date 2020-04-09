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

import java.net.MalformedURLException;
import java.util.OptionalInt;

import org.apache.log4j.BasicConfigurator;
import org.dcm4che6.net.Status;
import org.hamcrest.core.IsEqual;
import org.junit.Assert;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.weasis.dicom.op.Echo;
import org.weasis.dicom.param.DicomNode;
import org.weasis.dicom.param.DicomState;

public class EchoNetTest {

    @BeforeAll
    public static void setLogger() throws MalformedURLException {
        BasicConfigurator.configure();
    }

    @Test
    public void testProcess() {
        DicomNode calling = new DicomNode("WEASIS-SCU");
        DicomNode called = new DicomNode("DICOMSERVER", "dicomserver.co.uk", 11112);
        DicomState state = Echo.process(null, calling, called);
        // Should never happen
        Assert.assertNotNull(state);

        // See server log at http://dicomserver.co.uk/logs/
        System.out.println("DicomState result: ");
        // See org.dcm4ch6.net.Status
        System.out.println("\tDICOM Status: " + String.format("0x%04X", state.getStatus().orElseThrow() & 0xFFFF));
        System.out.println("\t" + state.getMessage());

        Assert.assertThat(state.getMessage(), state.getStatus(), IsEqual.equalTo(OptionalInt.of(Status.Success)));
    }
}
