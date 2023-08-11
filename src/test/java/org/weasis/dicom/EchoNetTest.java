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
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.weasis.dicom.op.Echo;
import org.weasis.dicom.param.DicomNode;
import org.weasis.dicom.param.DicomState;

@DisplayName("DICOM Echo")
class EchoNetTest {

  @Test
  void testProcess() {
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
    DicomNode called = new DicomNode("DICOMSERVER", "www.dicomserver.co.uk", 104);
    DicomState state = Echo.process(null, calling, called);
    // Should never happen
    Assertions.assertNotNull(state);
    System.out.println("DICOM Status:" + state.getStatus());
    System.out.println(state.getMessage());

    // see org.dcm4che3.net.Status
    // See server log at https://dicomserver.co.uk/logs/
    Assertions.assertEquals(Status.Success, state.getStatus(), state.getMessage());
  }
}
