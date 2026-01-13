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
import org.dcm4che3.net.QueryOption;
import org.dcm4che3.net.Status;
import org.dcm4che3.tool.movescu.MoveSCU;
import org.dcm4che3.tool.movescu.MoveSCU.InformationModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.util.StreamUtil;
import org.weasis.core.util.StringUtil;
import org.weasis.dicom.param.AdvancedParams;
import org.weasis.dicom.param.DeviceOpService;
import org.weasis.dicom.param.DicomNode;
import org.weasis.dicom.param.DicomParam;
import org.weasis.dicom.param.DicomProgress;
import org.weasis.dicom.param.DicomState;
import org.weasis.dicom.util.ServiceUtil;

/**
 * DICOM C-MOVE Service Class User (SCU) operations for retrieving medical images and objects from
 * remote DICOM servers.
 *
 * <p>This utility class provides static methods to perform DICOM C-MOVE operations with optional
 * progress tracking and advanced configuration parameters. The C-MOVE operation instructs a remote
 * DICOM server to send objects to a specified destination.
 */
public final class CMove {

  private static final Logger LOGGER = LoggerFactory.getLogger(CMove.class);

  private CMove() {}

  /**
   * Performs a DICOM C-MOVE operation with default advanced parameters.
   *
   * @param callingNode the calling DICOM node configuration
   * @param calledNode the called DICOM node configuration
   * @param destinationAet the destination AE title where objects should be sent
   * @param progress the progress handler for tracking operation status
   * @param keys matching and returning keys (DicomParam with no value is a returning key)
   * @return the DicomState containing DICOM response, status, error message and progress
   * @throws IllegalArgumentException if any required parameter is null
   */
  public static DicomState process(
      DicomNode callingNode,
      DicomNode calledNode,
      String destinationAet,
      DicomProgress progress,
      DicomParam... keys) {
    return process(null, callingNode, calledNode, destinationAet, progress, keys);
  }

  /**
   * Performs a DICOM C-MOVE operation with advanced configuration parameters.
   *
   * @param params advanced parameters for proxy, authentication, connection and TLS (optional)
   * @param callingNode the calling DICOM node configuration
   * @param calledNode the called DICOM node configuration
   * @param destinationAet the destination AE title where objects should be sent
   * @param progress the progress handler for tracking operation status
   * @param keys matching and returning keys (DicomParam with no value is a returning key)
   * @return the DicomState containing DICOM response, status, error message and progress
   * @throws IllegalArgumentException if any required parameter is null
   */
  public static DicomState process(
      AdvancedParams params,
      DicomNode callingNode,
      DicomNode calledNode,
      String destinationAet,
      DicomProgress progress,
      DicomParam... keys) {
    validateRequiredParameters(callingNode, calledNode, destinationAet);
    var options = params != null ? params : new AdvancedParams();

    try (var moveSCU = new MoveSCU(progress)) {
      var service = configureMoveSCU(moveSCU, options, callingNode, calledNode);
      addKeysToRequest(moveSCU, keys);
      moveSCU.setDestination(destinationAet);

      return executeMove(moveSCU, service);
    } catch (Exception e) {
      return handleTopLevelException(e);
    }
  }

  private static void validateRequiredParameters(
      DicomNode callingNode, DicomNode calledNode, String destinationAet) {
    Objects.requireNonNull(callingNode, "callingNode cannot be null");
    Objects.requireNonNull(calledNode, "calledNode cannot be null");
    Objects.requireNonNull(destinationAet, "destinationAet cannot be null");
  }

  private static DeviceOpService configureMoveSCU(
      MoveSCU moveSCU, AdvancedParams options, DicomNode callingNode, DicomNode calledNode)
      throws IOException {
    var remote = moveSCU.getRemoteConnection();
    var conn = moveSCU.getConnection();
    options.configureConnect(moveSCU.getAAssociateRQ(), remote, calledNode);
    options.configureBind(moveSCU.getApplicationEntity(), conn, callingNode);
    options.configure(conn);
    options.configureTLS(conn, remote);

    moveSCU.setInformationModel(
        getInformationModel(options),
        options.getTsuidOrder(),
        options.getQueryOptions().contains(QueryOption.RELATIONAL));

    return new DeviceOpService(moveSCU);
  }

  private static void addKeysToRequest(MoveSCU moveSCU, DicomParam... keys) {
    var dcmState = moveSCU.getState();
    for (var param : keys) {
      var values = param.getValues();
      moveSCU.addKey(param.getTag(), values);
      if (values != null && values.length > 0) {
        dcmState.addDicomMatchingKeys(param);
      }
    }
  }

  private static DicomState executeMove(MoveSCU moveSCU, DeviceOpService service) {
    service.start();
    try {
      var t1 = System.currentTimeMillis();
      moveSCU.open();
      var t2 = System.currentTimeMillis();
      moveSCU.retrieve();
      ServiceUtil.forceGettingAttributes(moveSCU.getState(), moveSCU);
      var t3 = System.currentTimeMillis();

      var dcmState = moveSCU.getState();
      var timeMsg = createTimeMessage(moveSCU, t1, t2, t3);
      dcmState = DicomState.buildMessage(dcmState, timeMsg, null);
      dcmState.addProcessTime(t1, t2, t3);
      return dcmState;

    } catch (Exception e) {
      return handleMoveException(moveSCU, e);
    } finally {
      cleanupResources(moveSCU, service);
    }
  }

  private static String createTimeMessage(MoveSCU moveSCU, long t1, long t2, long t3) {
    return MessageFormat.format(
        "DICOM C-MOVE connected in {2}ms from {0} to {1}. Sent files in {3}ms.",
        moveSCU.getAAssociateRQ().getCallingAET(),
        moveSCU.getAAssociateRQ().getCalledAET(),
        t2 - t1,
        t3 - t2);
  }

  private static DicomState handleMoveException(MoveSCU moveSCU, Exception e) {
    if (e instanceof InterruptedException) {
      Thread.currentThread().interrupt();
    }
    LOGGER.error("C-MOVE operation failed", e);
    ServiceUtil.forceGettingAttributes(moveSCU.getState(), moveSCU);
    return DicomState.buildMessage(moveSCU.getState(), null, e);
  }

  private static void cleanupResources(MoveSCU moveSCU, DeviceOpService service) {
    StreamUtil.safeClose(moveSCU);
    service.stop();
  }

  private static DicomState handleTopLevelException(Exception e) {
    if (e instanceof InterruptedException) {
      Thread.currentThread().interrupt();
    }
    LOGGER.error("C-MOVE setup failed", e);
    return DicomState.buildMessage(
        new DicomState(
            Status.UnableToProcess,
            "DICOM Move failed" + StringUtil.COLON_AND_SPACE + e.getMessage(),
            null),
        null,
        e);
  }

  private static InformationModel getInformationModel(AdvancedParams options) {
    var model = options.getInformationModel();
    return model instanceof InformationModel informationModel
        ? informationModel
        : InformationModel.StudyRoot;
  }
}
