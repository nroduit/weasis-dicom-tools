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

import java.text.MessageFormat;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Sequence;
import org.dcm4che3.data.Tag;
import org.dcm4che3.net.Connection;
import org.dcm4che3.net.Status;
import org.dcm4che3.tool.findscu.FindSCU;
import org.dcm4che3.tool.findscu.FindSCU.InformationModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.util.FileUtil;
import org.weasis.core.util.StringUtil;
import org.weasis.dicom.op.CFind;
import org.weasis.dicom.param.AdvancedParams;
import org.weasis.dicom.param.DeviceOpService;
import org.weasis.dicom.param.DicomNode;
import org.weasis.dicom.param.DicomParam;
import org.weasis.dicom.param.DicomState;
import org.weasis.dicom.util.ServiceUtil;

public class ModalityWorklist {

  private static final Logger LOGGER = LoggerFactory.getLogger(ModalityWorklist.class);

  public static final DicomParam PatientWeight = new DicomParam(Tag.PatientWeight);
  public static final DicomParam MedicalAlerts = new DicomParam(Tag.MedicalAlerts);
  public static final DicomParam Allergies = new DicomParam(Tag.Allergies);
  public static final DicomParam PregnancyStatus = new DicomParam(Tag.PregnancyStatus);
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

  // Attributes in Scheduled Procedure Step Sequence
  private static final int[] sps = {Tag.ScheduledProcedureStepSequence};
  public static final DicomParam Modality = new DicomParam(sps, Tag.Modality);
  public static final DicomParam RequestedContrastAgent =
      new DicomParam(sps, Tag.RequestedContrastAgent);
  public static final DicomParam ScheduledStationAETitle =
      new DicomParam(sps, Tag.ScheduledStationAETitle);
  public static final DicomParam ScheduledProcedureStepStartDate =
      new DicomParam(sps, Tag.ScheduledProcedureStepStartDate);
  public static final DicomParam ScheduledProcedureStepStartTime =
      new DicomParam(sps, Tag.ScheduledProcedureStepStartTime);
  public static final DicomParam ScheduledPerformingPhysicianName =
      new DicomParam(sps, Tag.ScheduledPerformingPhysicianName);
  public static final DicomParam ScheduledProcedureStepDescription =
      new DicomParam(sps, Tag.ScheduledProcedureStepDescription);
  public static final DicomParam ScheduledProtocolCodeSequence =
      new DicomParam(sps, Tag.ScheduledProtocolCodeSequence);
  public static final DicomParam ScheduledProcedureStepID =
      new DicomParam(sps, Tag.ScheduledProcedureStepID);
  public static final DicomParam ScheduledStationName =
      new DicomParam(sps, Tag.ScheduledStationName);
  public static final DicomParam ScheduledProcedureStepLocation =
      new DicomParam(sps, Tag.ScheduledProcedureStepLocation);
  public static final DicomParam PreMedication = new DicomParam(sps, Tag.PreMedication);
  public static final DicomParam ScheduledProcedureStepStatus =
      new DicomParam(sps, Tag.ScheduledProcedureStepStatus);
  // Attributes in Scheduled Procedure Step Sequence / Scheduled Protocol Code Sequence
  private static final int[] spc = {
    Tag.ScheduledProcedureStepSequence, Tag.ScheduledProtocolCodeSequence
  };
  public static final DicomParam ScheduledProtocolCodeMeaning =
      new DicomParam(spc, Tag.CodeMeaning);

  private ModalityWorklist() {}

  /**
   * @param callingNode the calling DICOM node configuration
   * @param calledNode the called DICOM node configuration
   * @param keys the matching and returning keys. DicomParam with no value is a returning key.
   * @return The DicomSate instance which contains the DICOM response, the DICOM status, the error
   *     message and the progression.
   */
  public static DicomState process(
      DicomNode callingNode, DicomNode calledNode, DicomParam... keys) {
    return process(null, callingNode, calledNode, 0, keys);
  }

  /**
   * @param params optional advanced parameters (proxy, authentication, connection and TLS)
   * @param callingNode the calling DICOM node configuration
   * @param calledNode the called DICOM node configuration
   * @param keys the matching and returning keys. DicomParam with no value is a returning key.
   * @return The DicomSate instance which contains the DICOM response, the DICOM status, the error
   *     message and the progression.
   */
  public static DicomState process(
      AdvancedParams params, DicomNode callingNode, DicomNode calledNode, DicomParam... keys) {
    return process(params, callingNode, calledNode, 0, keys);
  }

  /**
   * @param params optional advanced parameters (proxy, authentication, connection and TLS)
   * @param callingNode the calling DICOM node configuration
   * @param calledNode the called DICOM node configuration
   * @param cancelAfter cancel the query request after the receive of the specified number of
   *     matches.
   * @param keys the matching and returning keys. DicomParam with no value is a returning key.
   * @return The DicomSate instance which contains the DICOM response, the DICOM status, the error
   *     message and the progression.
   */
  public static DicomState process(
      AdvancedParams params,
      DicomNode callingNode,
      DicomNode calledNode,
      int cancelAfter,
      DicomParam... keys) {
    if (callingNode == null || calledNode == null) {
      throw new IllegalArgumentException("callingNode or calledNode cannot be null!");
    }

    AdvancedParams options = params == null ? new AdvancedParams() : params;

    try (FindSCU findSCU = new FindSCU()) {
      Connection remote = findSCU.getRemoteConnection();
      Connection conn = findSCU.getConnection();
      options.configureConnect(findSCU.getAAssociateRQ(), remote, calledNode);
      options.configureBind(findSCU.getApplicationEntity(), conn, callingNode);
      DeviceOpService service = new DeviceOpService(findSCU.getDevice());

      // configure
      options.configure(conn);
      options.configureTLS(conn, remote);

      findSCU.setInformationModel(
          getInformationModel(options), options.getTsuidOrder(), options.getQueryOptions());

      addKeys(findSCU, keys);

      findSCU.setCancelAfter(cancelAfter);
      findSCU.setPriority(options.getPriority());

      service.start();
      try {
        DicomState dcmState = findSCU.getState();
        long t1 = System.currentTimeMillis();
        findSCU.open();
        long t2 = System.currentTimeMillis();
        findSCU.query();
        ServiceUtil.forceGettingAttributes(dcmState, findSCU);
        long t3 = System.currentTimeMillis();
        String timeMsg =
            MessageFormat.format(
                "DICOM C-Find connected in {2}ms from {0} to {1}. Query in {3}ms.",
                findSCU.getAAssociateRQ().getCallingAET(),
                findSCU.getAAssociateRQ().getCalledAET(),
                t2 - t1,
                t3 - t2);
        return DicomState.buildMessage(dcmState, timeMsg, null);
      } catch (Exception e) {
        if (e instanceof InterruptedException) {
          Thread.currentThread().interrupt();
        }
        LOGGER.error("findscu", e);
        ServiceUtil.forceGettingAttributes(findSCU.getState(), findSCU);
        return DicomState.buildMessage(findSCU.getState(), null, e);
      } finally {
        FileUtil.safeClose(findSCU);
        service.stop();
      }
    } catch (Exception e) {
      if (e instanceof InterruptedException) {
        Thread.currentThread().interrupt();
      }
      LOGGER.error("findscu", e);
      return new DicomState(
          Status.UnableToProcess,
          "DICOM Find failed" + StringUtil.COLON_AND_SPACE + e.getMessage(),
          null);
    }
  }

  private static void addKeys(FindSCU findSCU, DicomParam[] keys) {
    for (DicomParam p : keys) {
      int[] pSeq = p.getParentSeqTags();
      if (pSeq == null || pSeq.length == 0) {
        CFind.addAttributes(findSCU.getKeys(), p);
      } else {
        Attributes parent = findSCU.getKeys();
        for (int value : pSeq) {
          Sequence lastSeq = parent.getSequence(value);
          if (lastSeq == null || lastSeq.isEmpty()) {
            lastSeq = parent.newSequence(value, 1);
            lastSeq.add(new Attributes());
          }
          parent = lastSeq.get(0);
        }

        CFind.addAttributes(parent, p);
      }
    }
  }

  private static InformationModel getInformationModel(AdvancedParams options) {
    Object model = options.getInformationModel();
    if (model instanceof InformationModel) {
      return (InformationModel) model;
    }
    return InformationModel.MWL;
  }
}
