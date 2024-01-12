/*
 * Copyright (c) 2014-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.op;

import java.text.MessageFormat;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.dcm4che3.net.ApplicationEntity;
import org.dcm4che3.net.Connection;
import org.dcm4che3.net.Device;
import org.dcm4che3.net.Status;
import org.dcm4che3.tool.storescu.StoreSCU;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.util.FileUtil;
import org.weasis.dicom.param.AdvancedParams;
import org.weasis.dicom.param.DeviceOpService;
import org.weasis.dicom.param.DicomNode;
import org.weasis.dicom.param.DicomState;

public class Echo {

  private static final Logger LOGGER = LoggerFactory.getLogger(Echo.class);

  private Echo() {}

  /**
   * @param callingAET the calling AET
   * @param calledNode the called DICOM node configuration
   * @return The DicomSate instance which contains the DICOM response, the DICOM status, the error
   *     message and the progression.
   */
  public static DicomState process(String callingAET, DicomNode calledNode) {
    return process(new DicomNode(callingAET), calledNode);
  }

  /**
   * @param callingNode the calling DICOM node configuration
   * @param calledNode the called DICOM node configuration
   * @return The DicomSate instance which contains the DICOM response, the DICOM status, the error
   *     message and the progression.
   */
  public static DicomState process(DicomNode callingNode, DicomNode calledNode) {
    return process(null, callingNode, calledNode);
  }

  /**
   * @param params the optional advanced parameters (proxy, authentication, connection and TLS)
   * @param callingNode the calling DICOM node configuration
   * @param calledNode the called DICOM node configuration
   * @return The DicomSate instance which contains the DICOM response, the DICOM status, the error
   *     message and the progression.
   */
  public static DicomState process(
      AdvancedParams params, DicomNode callingNode, DicomNode calledNode) {
    if (callingNode == null || calledNode == null) {
      throw new IllegalArgumentException("callingNode or calledNode cannot be null!");
    }

    AdvancedParams options = params == null ? new AdvancedParams() : params;

    try {
      Device device = new Device("storescu");
      Connection conn = new Connection();
      device.addConnection(conn);
      ApplicationEntity ae = new ApplicationEntity(callingNode.getAet());
      device.addApplicationEntity(ae);
      ae.addConnection(conn);
      StoreSCU storeSCU = new StoreSCU(ae, null);
      Connection remote = storeSCU.getRemoteConnection();
      DeviceOpService service = new DeviceOpService(device);

      options.configureConnect(storeSCU.getAAssociateRQ(), remote, calledNode);
      options.configureBind(ae, conn, callingNode);

      // configure
      options.configure(conn);
      options.configureTLS(conn, remote);

      storeSCU.setPriority(options.getPriority());

      service.start();
      try {
        long t1 = System.currentTimeMillis();
        storeSCU.open();
        long t2 = System.currentTimeMillis();
        Attributes rsp = storeSCU.echo();
        long t3 = System.currentTimeMillis();
        String message =
            MessageFormat.format(
                "Successful DICOM Echo. Connected in {2}ms from {0} to {1}. Service execution in {3}ms.",
                storeSCU.getAAssociateRQ().getCallingAET(),
                storeSCU.getAAssociateRQ().getCalledAET(),
                t2 - t1,
                t3 - t2);
        DicomState dcmState = new DicomState(rsp.getInt(Tag.Status, Status.Success), message, null);
        dcmState.addProcessTime(t1, t2, t3);
        return dcmState;
      } finally {
        FileUtil.safeClose(storeSCU);
        service.stop();
      }
    } catch (Exception e) {
      if (e instanceof InterruptedException) {
        Thread.currentThread().interrupt();
      }
      String message = "DICOM Echo failed, storescu: " + e.getMessage();
      LOGGER.error(message, e);
      return DicomState.buildMessage(
          new DicomState(Status.UnableToProcess, message, null), null, e);
    }
  }
}
