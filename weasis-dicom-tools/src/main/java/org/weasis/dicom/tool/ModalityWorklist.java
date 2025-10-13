/*
 * Copyright (c) 2014-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.tool;

import java.io.IOException;
import java.text.MessageFormat;
import java.util.Objects;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.dcm4che3.net.Connection;
import org.dcm4che3.net.Status;
import org.dcm4che3.tool.findscu.FindSCU;
import org.dcm4che3.tool.findscu.FindSCU.InformationModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.util.StreamUtil;
import org.weasis.core.util.StringUtil;
import org.weasis.dicom.op.CFind;
import org.weasis.dicom.param.AdvancedParams;
import org.weasis.dicom.param.DeviceOpService;
import org.weasis.dicom.param.DicomNode;
import org.weasis.dicom.param.DicomParam;
import org.weasis.dicom.param.DicomState;
import org.weasis.dicom.util.ServiceUtil;

/**
 * Utility class for performing DICOM Modality Worklist queries. Provides static methods to query
 * worklist items from DICOM nodes.
 */
public final class ModalityWorklist {

  private static final Logger LOGGER = LoggerFactory.getLogger(ModalityWorklist.class);

  // Patient-level attributes
  public static final DicomParam PatientWeight = new DicomParam(Tag.PatientWeight);
  public static final DicomParam MedicalAlerts = new DicomParam(Tag.MedicalAlerts);
  public static final DicomParam Allergies = new DicomParam(Tag.Allergies);
  public static final DicomParam PregnancyStatus = new DicomParam(Tag.PregnancyStatus);

  // Request-level attributes
  public static final DicomParam RequestingPhysician = new DicomParam(Tag.RequestingPhysician);
  public static final DicomParam RequestingService = new DicomParam(Tag.RequestingService);
  public static final DicomParam RequestedProcedureDescription =
      new DicomParam(Tag.RequestedProcedureDescription);
  public static final DicomParam RequestedProcedureCodeSequence =
      new DicomParam(Tag.RequestedProcedureCodeSequence);
  public static final DicomParam AdmissionID = new DicomParam(Tag.AdmissionID);
  public static final DicomParam IssuerOfAdmissionIDSequence =
      new DicomParam(Tag.IssuerOfAdmissionIDSequence);
  public static final DicomParam SpecialNeeds = new DicomParam(Tag.SpecialNeeds);
  public static final DicomParam CurrentPatientLocation =
      new DicomParam(Tag.CurrentPatientLocation);
  public static final DicomParam PatientState = new DicomParam(Tag.PatientState);
  public static final DicomParam RequestedProcedureID = new DicomParam(Tag.RequestedProcedureID);
  public static final DicomParam RequestedProcedurePriority =
      new DicomParam(Tag.RequestedProcedurePriority);
  public static final DicomParam PatientTransportArrangements =
      new DicomParam(Tag.PatientTransportArrangements);
  public static final DicomParam PlacerOrderNumberImagingServiceRequest =
      new DicomParam(Tag.PlacerOrderNumberImagingServiceRequest);
  public static final DicomParam FillerOrderNumberImagingServiceRequest =
      new DicomParam(Tag.FillerOrderNumberImagingServiceRequest);
  public static final DicomParam ConfidentialityConstraintOnPatientDataDescription =
      new DicomParam(Tag.ConfidentialityConstraintOnPatientDataDescription);

  // Scheduled Procedure Step Sequence attributes
  private static final int[] SPS_PATH = {Tag.ScheduledProcedureStepSequence};
  public static final DicomParam Modality = new DicomParam(SPS_PATH, Tag.Modality);
  public static final DicomParam RequestedContrastAgent =
      new DicomParam(SPS_PATH, Tag.RequestedContrastAgent);
  public static final DicomParam ScheduledStationAETitle =
      new DicomParam(SPS_PATH, Tag.ScheduledStationAETitle);
  public static final DicomParam ScheduledProcedureStepStartDate =
      new DicomParam(SPS_PATH, Tag.ScheduledProcedureStepStartDate);
  public static final DicomParam ScheduledProcedureStepStartTime =
      new DicomParam(SPS_PATH, Tag.ScheduledProcedureStepStartTime);
  public static final DicomParam ScheduledPerformingPhysicianName =
      new DicomParam(SPS_PATH, Tag.ScheduledPerformingPhysicianName);
  public static final DicomParam ScheduledProcedureStepDescription =
      new DicomParam(SPS_PATH, Tag.ScheduledProcedureStepDescription);
  public static final DicomParam ScheduledProtocolCodeSequence =
      new DicomParam(SPS_PATH, Tag.ScheduledProtocolCodeSequence);
  public static final DicomParam ScheduledProcedureStepID =
      new DicomParam(SPS_PATH, Tag.ScheduledProcedureStepID);
  public static final DicomParam ScheduledStationName =
      new DicomParam(SPS_PATH, Tag.ScheduledStationName);
  public static final DicomParam ScheduledProcedureStepLocation =
      new DicomParam(SPS_PATH, Tag.ScheduledProcedureStepLocation);
  public static final DicomParam PreMedication = new DicomParam(SPS_PATH, Tag.PreMedication);
  public static final DicomParam ScheduledProcedureStepStatus =
      new DicomParam(SPS_PATH, Tag.ScheduledProcedureStepStatus);

  // Scheduled Protocol Code Sequence attributes
  private static final int[] SPC_PATH = {
    Tag.ScheduledProcedureStepSequence, Tag.ScheduledProtocolCodeSequence
  };
  public static final DicomParam ScheduledProtocolCodeMeaning =
      new DicomParam(SPC_PATH, Tag.CodeMeaning);

  private ModalityWorklist() {
    // Utility class - prevent instantiation
  }

  /**
   * Performs a DICOM Modality Worklist query with default parameters.
   *
   * @param callingNode the calling DICOM node configuration
   * @param calledNode the called DICOM node configuration
   * @param keys the matching and returning keys. DicomParam with no value is a returning key.
   * @return The DicomState instance containing the DICOM response, status, error message and
   *     progression
   */
  public static DicomState process(
      DicomNode callingNode, DicomNode calledNode, DicomParam... keys) {
    return process(null, callingNode, calledNode, 0, keys);
  }

  /**
   * Performs a DICOM Modality Worklist query with advanced parameters.
   *
   * @param params optional advanced parameters (proxy, authentication, connection and TLS)
   * @param callingNode the calling DICOM node configuration
   * @param calledNode the called DICOM node configuration
   * @param keys the matching and returning keys. DicomParam with no value is a returning key.
   * @return The DicomState instance containing the DICOM response, status, error message and
   *     progression
   */
  public static DicomState process(
      AdvancedParams params, DicomNode callingNode, DicomNode calledNode, DicomParam... keys) {
    return process(params, callingNode, calledNode, 0, keys);
  }

  /**
   * Performs a DICOM Modality Worklist query with full parameter control.
   *
   * @param params optional advanced parameters (proxy, authentication, connection and TLS)
   * @param callingNode the calling DICOM node configuration
   * @param calledNode the called DICOM node configuration
   * @param cancelAfter cancel the query request after receiving the specified number of matches (0
   *     = no limit)
   * @param keys the matching and returning keys. DicomParam with no value is a returning key.
   * @return The DicomState instance containing the DICOM response, status, error message and
   *     progression
   */
  public static DicomState process(
      AdvancedParams params,
      DicomNode callingNode,
      DicomNode calledNode,
      int cancelAfter,
      DicomParam... keys) {
    Objects.requireNonNull(callingNode, "callingNode cannot be null");
    Objects.requireNonNull(calledNode, "calledNode cannot be null");

    var options = Objects.requireNonNullElse(params, new AdvancedParams());

    try (var findSCU = new FindSCU()) {
      configureFindSCU(findSCU, options, callingNode, calledNode, cancelAfter, keys);

      return executeQuery(findSCU);
    } catch (Exception e) {
      return handleException(e, "DICOM Find failed");
    }
  }

  private static void configureFindSCU(
      FindSCU findSCU,
      AdvancedParams options,
      DicomNode callingNode,
      DicomNode calledNode,
      int cancelAfter,
      DicomParam[] keys)
      throws IOException {
    Connection remote = findSCU.getRemoteConnection();
    Connection conn = findSCU.getConnection();
    options.configureConnect(findSCU.getAAssociateRQ(), remote, calledNode);
    options.configureBind(findSCU.getApplicationEntity(), conn, callingNode);
    options.configure(conn);
    options.configureTLS(conn, remote);

    findSCU.setInformationModel(
        getInformationModel(options), options.getTsuidOrder(), options.getQueryOptions());

    addKeys(findSCU, keys);

    findSCU.setCancelAfter(cancelAfter);
    findSCU.setPriority(options.getPriority());
  }

  private static DicomState executeQuery(FindSCU findSCU) {
    var service = new DeviceOpService(findSCU.getDevice());
    service.start();
    try {
      var dcmState = findSCU.getState();
      var timings = performQuery(findSCU);

      ServiceUtil.forceGettingAttributes(dcmState, findSCU);
      var timeMsg = formatTimingMessage(findSCU, timings);

      return DicomState.buildMessage(dcmState, timeMsg, null);
    } catch (Exception e) {
      return handleQueryException(findSCU, e);
    } finally {
      StreamUtil.safeClose(findSCU);
      service.stop();
    }
  }

  private static QueryTimings performQuery(FindSCU findSCU) throws Exception {
    long startTime = System.currentTimeMillis();
    findSCU.open();
    long connectTime = System.currentTimeMillis();
    findSCU.query();
    long queryTime = System.currentTimeMillis();

    return new QueryTimings(startTime, connectTime, queryTime);
  }

  private static String formatTimingMessage(FindSCU findSCU, QueryTimings timings) {
    return MessageFormat.format(
        "DICOM C-Find connected in {2}ms from {0} to {1}. Query in {3}ms.",
        findSCU.getAAssociateRQ().getCallingAET(),
        findSCU.getAAssociateRQ().getCalledAET(),
        timings.connectionTime(),
        timings.queryTime());
  }

  private static DicomState handleQueryException(FindSCU findSCU, Exception e) {
    if (e instanceof InterruptedException) {
      Thread.currentThread().interrupt();
    }
    LOGGER.error("DICOM C-Find query failed", e);
    ServiceUtil.forceGettingAttributes(findSCU.getState(), findSCU);
    return DicomState.buildMessage(findSCU.getState(), null, e);
  }

  private static DicomState handleException(Exception e, String message) {
    if (e instanceof InterruptedException) {
      Thread.currentThread().interrupt();
    }
    LOGGER.error("DICOM operation failed", e);
    return new DicomState(
        Status.UnableToProcess, message + StringUtil.COLON_AND_SPACE + e.getMessage(), null);
  }

  private static void addKeys(FindSCU findSCU, DicomParam[] keys) {
    for (DicomParam param : keys) {
      var parentSeq = param.getParentSeqTags();

      if (parentSeq == null || parentSeq.length == 0) {
        CFind.addAttributes(findSCU.getKeys(), param);
      } else {
        var parent = navigateToSequenceLevel(findSCU.getKeys(), parentSeq);
        CFind.addAttributes(parent, param);
      }
    }
  }

  private static Attributes navigateToSequenceLevel(Attributes root, int[] sequencePath) {
    Attributes current = root;

    for (int tag : sequencePath) {
      var sequence = current.getSequence(tag);
      if (sequence == null || sequence.isEmpty()) {
        sequence = current.newSequence(tag, 1);
        sequence.add(new Attributes());
      }
      current = sequence.get(0);
    }
    return current;
  }

  private static InformationModel getInformationModel(AdvancedParams options) {
    var model = options.getInformationModel();
    return model instanceof InformationModel im ? im : InformationModel.MWL;
  }

  private record QueryTimings(long start, long connected, long completed) {
    long connectionTime() {
      return connected - start;
    }

    long queryTime() {
      return completed - connected;
    }
  }
}
