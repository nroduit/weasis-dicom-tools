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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.dicom.param.AdvancedParams;
import org.weasis.dicom.param.DicomNode;
import org.weasis.dicom.param.DicomParam;
import org.weasis.dicom.param.DicomProgress;
import org.weasis.dicom.param.DicomState;

public class CMove {

  private static final Logger LOGGER = LoggerFactory.getLogger(CMove.class);

  private CMove() {}

  /**
   * @param callingNode the calling DICOM node configuration
   * @param calledNode the called DICOM node configuration
   * @param destinationAet the destination AET
   * @param progress the progress handler
   * @param keys the matching and returning keys. DicomParam with no value is a returning key.
   * @return The DicomSate instance which contains the DICOM response, the DICOM status, the error
   *     message and the progression.
   */
  public static DicomState process(
      DicomNode callingNode,
      DicomNode calledNode,
      String destinationAet,
      DicomProgress progress,
      DicomParam... keys) {
    return CMove.process(null, callingNode, calledNode, destinationAet, progress, keys);
  }

  /**
   * @param params the optional advanced parameters (proxy, authentication, connection and TLS)
   * @param callingNode the calling DICOM node configuration
   * @param calledNode the called DICOM node configuration
   * @param destinationAet the destination AET
   * @param progress the progress handler
   * @param keys the matching and returning keys. DicomParam with no value is a returning key.
   * @return The DicomSate instance which contains the DICOM response, the DICOM status, the error
   *     message and the progression.
   */
  public static DicomState process(
      AdvancedParams params,
      DicomNode callingNode,
      DicomNode calledNode,
      String destinationAet,
      DicomProgress progress,
      DicomParam... keys) {
    if (callingNode == null || calledNode == null || destinationAet == null) {
      throw new IllegalArgumentException(
          "callingNode, calledNode or destinationAet cannot be null!");
    }
    AdvancedParams options = params == null ? new AdvancedParams() : params;

    // try (MoveSCU moveSCU = new MoveSCU(progress);) {
    // Connection remote = moveSCU.getRemoteConnection();
    // Connection conn = moveSCU.getConnection();
    // options.configureConnect(moveSCU.getAAssociateRQ(), remote, calledNode);
    // options.configureBind(moveSCU.getApplicationEntity(), conn, callingNode);
    // DeviceOpService service = new DeviceOpService(moveSCU);
    //
    // // configure
    // options.configure(conn);
    // options.configureTLS(conn, remote);
    //
    // moveSCU.setInformationModel(getInformationModel(options), options.getTsuidOrder(),
    // options.getQueryOptions().contains(QueryOption.RELATIONAL));
    //
    // for (DicomParam p : keys) {
    // moveSCU.addKey(p.getTag(), p.getValues());
    // }
    // moveSCU.setDestination(destinationAet);
    //
    // service.start();
    // try {
    // DicomState dcmState = moveSCU.getState();
    // long t1 = System.currentTimeMillis();
    // moveSCU.open();
    // long t2 = System.currentTimeMillis();
    // moveSCU.retrieve();
    // ServiceUtil.forceGettingAttributes(dcmState, moveSCU);
    // long t3 = System.currentTimeMillis();
    // String timeMsg =
    // MessageFormat.format("DICOM C-MOVE connected in {2}ms from {0} to {1}. Sent files in {3}ms.",
    // moveSCU.getAAssociateRQ().getCallingAET(), moveSCU.getAAssociateRQ().getCalledAET(), t2 - t1,
    // t3 - t2);
    // return DicomState.buildMessage(dcmState, timeMsg, null);
    // } catch (Exception e) {
    // LOGGER.error("movescu", e);
    // ServiceUtil.forceGettingAttributes(moveSCU.getState(), moveSCU);
    // return DicomState.buildMessage(moveSCU.getState(), null, e);
    // } finally {
    // FileUtil.safeClose(moveSCU);
    // service.stop();
    // }
    // } catch (Exception e) {
    // LOGGER.error("movescu", e);
    // return new DicomState(Status.UnableToProcess,
    // "DICOM Move failed" + StringUtil.COLON_AND_SPACE + e.getMessage(), null);
    // }

    return null;
  }

  // private static InformationModel getInformationModel(AdvancedParams options) {
  // Object model = options.getInformationModel();
  // if (model instanceof InformationModel) {
  // return (InformationModel) model;
  // }
  // return InformationModel.StudyRoot;
  // }

}
