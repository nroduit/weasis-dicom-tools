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

import java.io.IOException;
import java.text.MessageFormat;
import java.util.Objects;
import org.dcm4che3.data.Tag;
import org.dcm4che3.net.ApplicationEntity;
import org.dcm4che3.net.Connection;
import org.dcm4che3.net.Device;
import org.dcm4che3.net.Status;
import org.dcm4che3.tool.storescu.StoreSCU;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.dicom.param.AdvancedParams;
import org.weasis.dicom.param.DeviceOpService;
import org.weasis.dicom.param.DicomNode;
import org.weasis.dicom.param.DicomState;

/**
 * DICOM Echo Service Class User (SCU) for testing connectivity to DICOM nodes.
 *
 * <p>Provides C-ECHO operations to verify DICOM association establishment and basic connectivity
 * with remote DICOM Application Entities.
 */
public final class Echo {

  private static final Logger LOGGER = LoggerFactory.getLogger(Echo.class);
  private static final String DEVICE_NAME = "echo-scu";

  private Echo() {}

  /**
   * Performs a DICOM echo operation using the calling AET and target node.
   *
   * @param callingAET the calling AET title
   * @param calledNode the target DICOM node
   * @return the DICOM operation state
   * @throws IllegalArgumentException if parameters are null
   */
  public static DicomState process(String callingAET, DicomNode calledNode) {
    Objects.requireNonNull(callingAET, "callingAET cannot be null");
    Objects.requireNonNull(calledNode, "calledNode cannot be null");
    return process(new DicomNode(callingAET), calledNode);
  }

  /**
   * Performs a DICOM echo operation between two nodes.
   *
   * @param callingNode the calling DICOM node
   * @param calledNode the target DICOM node
   * @return the DICOM operation state
   * @throws IllegalArgumentException if parameters are null
   */
  public static DicomState process(DicomNode callingNode, DicomNode calledNode) {
    return process(null, callingNode, calledNode);
  }

  /**
   * Performs a DICOM echo operation with advanced parameters.
   *
   * @param params optional advanced parameters (proxy, authentication, connection and TLS)
   * @param callingNode the calling DICOM node
   * @param calledNode the target DICOM node
   * @return the DICOM operation state with timing and response information
   * @throws IllegalArgumentException if required parameters are null
   */
  public static DicomState process(
      AdvancedParams params, DicomNode callingNode, DicomNode calledNode) {
    Objects.requireNonNull(callingNode, "callingNode cannot be null");
    Objects.requireNonNull(calledNode, "calledNode cannot be null");

    var options = Objects.requireNonNullElse(params, new AdvancedParams());

    try {
      return performEcho(options, callingNode, calledNode);
    } catch (Exception e) {
      return handleException(e);
    }
  }

  private static DicomState performEcho(
      AdvancedParams options, DicomNode callingNode, DicomNode calledNode) throws Exception {
    var device = createDevice(callingNode);
    var storeSCU = createStoreSCU(device, callingNode);
    var service = new DeviceOpService(device);

    configureConnections(options, storeSCU, callingNode, calledNode);

    service.start();
    try (storeSCU) {
      return executeEcho(storeSCU);
    } finally {
      service.stop();
    }
  }

  private static Device createDevice(DicomNode callingNode) {
    var device = new Device(DEVICE_NAME);
    var conn = new Connection();
    device.addConnection(conn);
    var ae = new ApplicationEntity(callingNode.getAet());
    device.addApplicationEntity(ae);
    ae.addConnection(conn);
    return device;
  }

  private static StoreSCU createStoreSCU(Device device, DicomNode callingNode) throws IOException {
    var ae = device.getApplicationEntity(callingNode.getAet());
    return new StoreSCU(ae, null);
  }

  private static void configureConnections(
      AdvancedParams options, StoreSCU storeSCU, DicomNode callingNode, DicomNode calledNode)
      throws IOException {
    var remote = storeSCU.getRemoteConnection();
    var ae = storeSCU.getApplicationEntity();
    var conn = ae.getConnections().get(0);

    options.configureConnect(storeSCU.getAAssociateRQ(), remote, calledNode);
    options.configureBind(ae, conn, callingNode);

    options.configure(conn);
    options.configureTLS(conn, remote);

    storeSCU.setPriority(options.getPriority());
  }

  private static DicomState executeEcho(StoreSCU storeSCU) throws Exception {
    var connectStart = System.currentTimeMillis();
    storeSCU.open();
    var echoStart = System.currentTimeMillis();
    var response = storeSCU.echo();
    var echoEnd = System.currentTimeMillis();

    var message = formatSuccessMessage(storeSCU, connectStart, echoStart, echoEnd);
    var state = new DicomState(response.getInt(Tag.Status, Status.Success), message, null);
    state.addProcessTime(connectStart, echoStart, echoEnd);

    return state;
  }

  private static String formatSuccessMessage(
      StoreSCU storeSCU, long connectStart, long echoStart, long echoEnd) {
    var rq = storeSCU.getAAssociateRQ();
    return MessageFormat.format(
        "Successful DICOM Echo. Connected in {2}ms from {0} to {1}. Service execution in {3}ms.",
        rq.getCallingAET(), rq.getCalledAET(), echoStart - connectStart, echoEnd - echoStart);
  }

  private static DicomState handleException(Exception e) {
    if (e instanceof InterruptedException) {
      Thread.currentThread().interrupt();
    }
    var message = "DICOM Echo failed: " + e.getMessage();
    LOGGER.error(message, e);
    return DicomState.buildMessage(new DicomState(Status.UnableToProcess, message, null), null, e);
  }
}
