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

import org.dcm4che3.net.Status;
import org.hamcrest.core.IsEqual;
import org.junit.Assert;
import org.junit.Test;
import org.weasis.dicom.op.Echo;
import org.weasis.dicom.param.DicomNode;
import org.weasis.dicom.param.DicomState;

public class EchoNetTest {

    @Test
    public void testProcess() {
        DicomNode calling = new DicomNode("WEASIS-SCU");
        DicomNode called = new DicomNode("DICOMSERVER", "dicomserver.co.uk", 11112);
        DicomState state = Echo.process(calling, called);
        // Should never happen
        Assert.assertNotNull(state);
        // see org.dcm4che3.net.Status
        // See server log at http://dicomserver.co.uk/logs/
        Assert.assertThat(state.getMessage(), state.getStatus(), IsEqual.equalTo(Status.Success));
    }

}
