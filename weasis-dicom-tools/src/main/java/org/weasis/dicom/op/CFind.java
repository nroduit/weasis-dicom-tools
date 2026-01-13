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
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.ElementDictionary;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.VR;
import org.dcm4che3.net.Status;
import org.dcm4che3.net.service.QueryRetrieveLevel;
import org.dcm4che3.tool.findscu.FindSCU;
import org.dcm4che3.tool.findscu.FindSCU.InformationModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.util.StringUtil;
import org.weasis.dicom.param.AdvancedParams;
import org.weasis.dicom.param.DeviceOpService;
import org.weasis.dicom.param.DicomNode;
import org.weasis.dicom.param.DicomParam;
import org.weasis.dicom.param.DicomState;
import org.weasis.dicom.util.ServiceUtil;

/**
 * DICOM C-FIND operation utility providing query functionality for patient, study, series, and
 * instance level searches.
 *
 * <p>This class provides static methods to perform DICOM C-FIND queries with configurable
 * parameters including advanced connection options, query levels, and cancellation support.
 *
 * @since 1.0
 */
public final class CFind {

  private static final Logger LOGGER = LoggerFactory.getLogger(CFind.class);

  // Patient level parameters
  public static final DicomParam PatientID = new DicomParam(Tag.PatientID);
  public static final DicomParam IssuerOfPatientID = new DicomParam(Tag.IssuerOfPatientID);
  public static final DicomParam PatientName = new DicomParam(Tag.PatientName);
  public static final DicomParam PatientBirthDate = new DicomParam(Tag.PatientBirthDate);
  public static final DicomParam PatientSex = new DicomParam(Tag.PatientSex);

  // Study level parameters
  public static final DicomParam StudyInstanceUID = new DicomParam(Tag.StudyInstanceUID);
  public static final DicomParam AccessionNumber = new DicomParam(Tag.AccessionNumber);
  public static final DicomParam IssuerOfAccessionNumberSequence =
      new DicomParam(Tag.IssuerOfAccessionNumberSequence);
  public static final DicomParam StudyID = new DicomParam(Tag.StudyID);
  public static final DicomParam ReferringPhysicianName =
      new DicomParam(Tag.ReferringPhysicianName);
  public static final DicomParam StudyDescription = new DicomParam(Tag.StudyDescription);
  public static final DicomParam StudyDate = new DicomParam(Tag.StudyDate);
  public static final DicomParam StudyTime = new DicomParam(Tag.StudyTime);

  // Series level parameters
  public static final DicomParam SeriesInstanceUID = new DicomParam(Tag.SeriesInstanceUID);
  public static final DicomParam Modality = new DicomParam(Tag.Modality);
  public static final DicomParam SeriesNumber = new DicomParam(Tag.SeriesNumber);
  public static final DicomParam SeriesDescription = new DicomParam(Tag.SeriesDescription);
  public static final DicomParam SeriesDate = new DicomParam(Tag.SeriesDate);
  public static final DicomParam SeriesTime = new DicomParam(Tag.SeriesTime);

  // Instance level parameters
  public static final DicomParam SOPInstanceUID = new DicomParam(Tag.SOPInstanceUID);
  public static final DicomParam InstanceNumber = new DicomParam(Tag.InstanceNumber);
  public static final DicomParam SopClassUID = new DicomParam(Tag.SOPClassUID);

  private CFind() {}

  /**
   * Executes a DICOM C-FIND query using default advanced parameters and STUDY query level.
   *
   * @param callingNode the calling DICOM node configuration
   * @param calledNode the called DICOM node configuration
   * @param keys the matching and returning keys (DicomParam with no value is a returning key)
   * @return the DICOM response containing status, results, and processing information
   * @throws IllegalArgumentException if callingNode or calledNode is null
   */
  public static DicomState process(
      DicomNode callingNode, DicomNode calledNode, DicomParam... keys) {
    return process(null, callingNode, calledNode, 0, QueryRetrieveLevel.STUDY, keys);
  }

  /**
   * Executes a DICOM C-FIND query with advanced parameters using default STUDY query level.
   *
   * @param params optional advanced parameters (proxy, authentication, connection and TLS)
   * @param callingNode the calling DICOM node configuration
   * @param calledNode the called DICOM node configuration
   * @param keys the matching and returning keys (DicomParam with no value is a returning key)
   * @return the DICOM response containing status, results, and processing information
   * @throws IllegalArgumentException if callingNode or calledNode is null
   */
  public static DicomState process(
      AdvancedParams params, DicomNode callingNode, DicomNode calledNode, DicomParam... keys) {
    return process(params, callingNode, calledNode, 0, QueryRetrieveLevel.STUDY, keys);
  }

  /**
   * Executes a DICOM C-FIND query with full configuration options.
   *
   * @param params optional advanced parameters (proxy, authentication, connection and TLS)
   * @param callingNode the calling DICOM node configuration
   * @param calledNode the called DICOM node configuration
   * @param cancelAfter cancel query after receiving this number of matches (0 = no limit)
   * @param level query retrieve level (defaults to STUDY if null)
   * @param keys the matching and returning keys (DicomParam with no value is a returning key)
   * @return the DICOM response containing status, results, and processing information
   * @throws IllegalArgumentException if callingNode or calledNode is null
   */
  public static DicomState process(
      AdvancedParams params,
      DicomNode callingNode,
      DicomNode calledNode,
      int cancelAfter,
      QueryRetrieveLevel level,
      DicomParam... keys) {
    validateNodes(callingNode, calledNode);
    var options = Objects.requireNonNullElse(params, new AdvancedParams());

    try (var findSCU = new FindSCU()) {
      configureFindSCU(findSCU, options, callingNode, calledNode, level);
      addQueryKeys(findSCU, keys);
      findSCU.setCancelAfter(cancelAfter);
      findSCU.setPriority(options.getPriority());

      return executeQuery(findSCU);
    } catch (Exception e) {
      return handleException(e, "DICOM Find failed");
    }
  }

  private static void validateNodes(DicomNode callingNode, DicomNode calledNode) {
    if (callingNode == null || calledNode == null) {
      throw new IllegalArgumentException("callingNode and calledNode cannot be null");
    }
  }

  private static void configureFindSCU(
      FindSCU findSCU,
      AdvancedParams options,
      DicomNode callingNode,
      DicomNode calledNode,
      QueryRetrieveLevel level)
      throws IOException {

    var remote = findSCU.getRemoteConnection();
    var connection = findSCU.getConnection();
    options.configureConnect(findSCU.getAAssociateRQ(), remote, calledNode);
    options.configureBind(findSCU.getApplicationEntity(), connection, callingNode);
    options.configure(connection);
    options.configureTLS(connection, remote);

    findSCU.setInformationModel(
        getInformationModel(options), options.getTsuidOrder(), options.getQueryOptions());
    if (level != null) {
      findSCU.addLevel(level.name());
    }
  }

  private static void addQueryKeys(FindSCU findSCU, DicomParam... keys) {
    var dcmState = findSCU.getState();

    for (var param : keys) {
      addAttributes(findSCU.getKeys(), param);
      var values = param.getValues();
      if (values != null && values.length > 0) {
        dcmState.addDicomMatchingKeys(param);
      }
    }
  }

  private static DicomState executeQuery(FindSCU findSCU) throws Exception {
    try {
      var service = new DeviceOpService(findSCU.getDevice());
      service.start();
      var startTime = System.currentTimeMillis();
      findSCU.open();
      var connectTime = System.currentTimeMillis();
      findSCU.query();
      ServiceUtil.forceGettingAttributes(findSCU.getState(), findSCU);
      var queryTime = System.currentTimeMillis();

      var dcmState = findSCU.getState();
      var timeMessage = formatTimeMessage(findSCU, startTime, connectTime, queryTime);
      dcmState = DicomState.buildMessage(dcmState, timeMessage, null);
      dcmState.addProcessTime(startTime, connectTime, queryTime);
      return dcmState;
    } catch (Exception e) {
      if (e instanceof InterruptedException) {
        Thread.currentThread().interrupt();
      }
      LOGGER.error("C-FIND query execution failed", e);
      ServiceUtil.forceGettingAttributes(findSCU.getState(), findSCU);
      return DicomState.buildMessage(findSCU.getState(), null, e);
    }
  }

  private static String formatTimeMessage(
      FindSCU findSCU, long startTime, long connectTime, long queryTime) {
    return MessageFormat.format(
        "DICOM C-Find connected in {2}ms from {0} to {1}. Query in {3}ms.",
        findSCU.getAAssociateRQ().getCallingAET(),
        findSCU.getAAssociateRQ().getCalledAET(),
        connectTime - startTime,
        queryTime - connectTime);
  }

  private static DicomState handleException(Exception e, String message) {
    if (e instanceof InterruptedException) {
      Thread.currentThread().interrupt();
    }
    LOGGER.error("C-FIND operation failed", e);
    return DicomState.buildMessage(
        new DicomState(
            Status.UnableToProcess, message + StringUtil.COLON_AND_SPACE + e.getMessage(), null),
        null,
        e);
  }

  private static InformationModel getInformationModel(AdvancedParams options) {
    var model = options.getInformationModel();
    return model instanceof InformationModel informationModel
        ? informationModel
        : InformationModel.StudyRoot;
  }

  /**
   * Adds DICOM attributes to the query keys based on the provided parameter.
   *
   * @param attrs the attributes object to modify
   * @param param the DICOM parameter containing tag and values
   */
  public static void addAttributes(Attributes attrs, DicomParam param) {
    var tag = param.getTag();
    var values = param.getValues();
    var vr = ElementDictionary.vrOf(tag, attrs.getPrivateCreator(tag));

    if (values == null || values.length == 0) {
      addReturnKey(attrs, tag, vr);
    } else {
      attrs.setString(tag, vr, values);
    }
  }

  private static void addReturnKey(Attributes attrs, int tag, VR vr) {
    if (vr == VR.SQ) {
      attrs.newSequence(tag, 1).add(new Attributes(0));
    } else {
      attrs.setNull(tag, vr);
    }
  }
}
