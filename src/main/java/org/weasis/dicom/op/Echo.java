/*
 * Copyright (c) 2014-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at http://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.op;

import java.text.MessageFormat;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.dcm4che6.conf.model.Connection;
import org.dcm4che6.data.UID;
import org.dcm4che6.net.AAssociate;
import org.dcm4che6.net.Association;
import org.dcm4che6.net.DicomServiceRegistry;
import org.dcm4che6.net.DimseRSP;
import org.dcm4che6.net.Status;
import org.dcm4che6.net.TCPConnector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.util.StringUtil;
import org.weasis.dicom.param.AdvancedParams;
import org.weasis.dicom.param.ConnectOptions;
import org.weasis.dicom.param.DicomNode;
import org.weasis.dicom.param.DicomState;
import org.weasis.dicom.util.ServiceUtil;

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

    try {
      AdvancedParams options = params == null ? new AdvancedParams() : params;
      ConnectOptions connectOptions = options.getConnectOptions();
      if (connectOptions == null) {
        connectOptions = new ConnectOptions();
      }
      DicomServiceRegistry serviceRegistry = new DicomServiceRegistry();
      TCPConnector<Association> inst =
          new TCPConnector<>(
              (connector, role) -> new Association(connector, role, serviceRegistry));
      CompletableFuture<Void> task = CompletableFuture.runAsync(inst);
      AAssociate.RQ rq = new AAssociate.RQ();
      rq.setCallingAETitle(callingNode.getAet());
      rq.setCalledAETitle(calledNode.getAet());
      rq.putPresentationContext((byte) 1, UID.VerificationSOPClass, UID.ImplicitVRLittleEndian);
      Connection local = ServiceUtil.getConnection(callingNode);
      Connection remote = ServiceUtil.getConnection(calledNode);
      options.configureTLS(local, remote);
      long t1 = System.currentTimeMillis();
      Association as =
          inst.connect(local, remote)
              .thenCompose(as1 -> as1.open(rq))
              .get(connectOptions.getConnectTimeout(), TimeUnit.MILLISECONDS);
      long t2 = System.currentTimeMillis();
      CompletableFuture<DimseRSP> echo = as.cecho();
      DimseRSP resp = echo.get(connectOptions.getResponseTimeout(), TimeUnit.MILLISECONDS);
      as.release().get(connectOptions.getReleaseTimeout(), TimeUnit.MILLISECONDS);
      long t3 = System.currentTimeMillis();
      as.onClose().get(connectOptions.getReleaseTimeout(), TimeUnit.MILLISECONDS);
      task.cancel(true);
      String message =
          MessageFormat.format(
              "Successful DICOM Echo. Connected in {2}ms from {0} to {1}. Service execution in {3}ms.",
              rq.getCallingAETitle(), rq.getCalledAETitle(), t2 - t1, t3 - t2);
      return new DicomState(ServiceUtil.getStatus(resp), message, null);
    } catch (Exception e) {
      String message = e.getMessage();
      if (!StringUtil.hasText(message) && e.getClass() == TimeoutException.class) {
        message = "Timeout exception";
      }
      message = "DICOM Echo failed: " + message;
      LOGGER.error(message, e);
      return new DicomState(Status.UnableToProcess, message, null);
    }
  }
}
