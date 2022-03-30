/*
 * Copyright (c) 2014-2019 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
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
    // DicomNode calling = new DicomNode("WEA-SCU");
    // DicomNode called = new DicomNode("DCM4CHEE", "localhost", 11112);
    // AdvancedParams params = new AdvancedParams();
    // TlsOptions tls =
    // new TlsOptions(true, "/home/dcm4chee/dcm4chee-2.18.0-mysql/server/default/conf/keystore.jks",
    // "JKS",
    // "keypwd", "keypwd", "/home/dcm4chee/dcm4chee-2.18.0-mysql/server/default/conf/trust.jks",
    // "JKS",
    // "trustpwd");
    // params.setTlsOptions(tls);
    // DicomState state = Echo.process(params, calling, called);

    DicomNode calling = new DicomNode("WEASIS-SCU");
    DicomNode called = new DicomNode("DICOMSERVER", "dicomserver.co.uk", 11112);
    DicomState state = Echo.process(null, calling, called);
    // Should never happen
    Assert.assertNotNull(state);

    System.out.println("DICOM Status:" + state.getStatus());
    System.out.println(state.getMessage());
    // see org.dcm4che3.net.Status
    // See server log at http://dicomserver.co.uk/logs/
    Assert.assertThat(state.getMessage(), state.getStatus(), IsEqual.equalTo(Status.Success));
  }
}
