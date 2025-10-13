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
import java.net.URL;
import java.nio.file.Path;
import java.text.MessageFormat;
import java.util.Objects;
import java.util.Properties;
import org.dcm4che3.net.QueryOption;
import org.dcm4che3.net.Status;
import org.dcm4che3.tool.common.CLIUtils;
import org.dcm4che3.tool.getscu.GetSCU;
import org.dcm4che3.tool.getscu.GetSCU.InformationModel;
import org.dcm4che3.util.StringUtils;
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
 * DICOM C-GET service for retrieving DICOM objects from remote nodes. Provides static methods to
 * initiate DICOM C-GET operations with support for advanced parameters including TLS,
 * authentication, and custom SOP class configurations.
 *
 * <p>The C-GET service allows a DICOM SCU (Service Class User) to retrieve DICOM objects from a
 * remote DICOM SCP (Service Class Provider). Retrieved objects are stored in a specified output
 * directory.
 */
public final class CGet {

  private static final Logger LOGGER = LoggerFactory.getLogger(CGet.class);
  private static final String DEFAULT_STORE_TCS_PROPERTIES = "store-tcs.properties";

  private CGet() {}

  /**
   * Performs a DICOM C-GET operation with basic parameters.
   *
   * @param callingNode the calling DICOM node configuration
   * @param calledNode the called DICOM node configuration
   * @param progress the progress handler for monitoring operation status
   * @param outputDir the directory where retrieved DICOM files will be stored
   * @param keys the query parameters and return keys
   * @return the DICOM state containing response status and progress information
   * @throws IllegalArgumentException if any required parameter is null
   */
  public static DicomState process(
      DicomNode callingNode,
      DicomNode calledNode,
      DicomProgress progress,
      Path outputDir,
      DicomParam... keys) {
    return process(null, callingNode, calledNode, progress, outputDir, null, keys);
  }

  /**
   * Performs a DICOM C-GET operation with advanced parameters.
   *
   * @param params the advanced parameters (proxy, authentication, connection and TLS options)
   * @param callingNode the calling DICOM node configuration
   * @param calledNode the called DICOM node configuration
   * @param progress the progress handler for monitoring operation status
   * @param outputDir the directory where retrieved DICOM files will be stored
   * @param keys the query parameters and return keys
   * @return the DICOM state containing response status and progress information
   * @throws IllegalArgumentException if any required parameter is null
   */
  public static DicomState process(
      AdvancedParams params,
      DicomNode callingNode,
      DicomNode calledNode,
      DicomProgress progress,
      Path outputDir,
      DicomParam... keys) {
    return process(params, callingNode, calledNode, progress, outputDir, null, keys);
  }

  /**
   * Performs a DICOM C-GET operation with custom SOP class configuration.
   *
   * @param params the advanced parameters (may be null for defaults)
   * @param callingNode the calling DICOM node configuration
   * @param calledNode the called DICOM node configuration
   * @param progress the progress handler for monitoring operation status
   * @param outputDir the directory where retrieved DICOM files will be stored
   * @param sopClassURL optional URL to custom SOP class configuration
   * @param keys the query parameters and return keys
   * @return the DICOM state containing response status and progress information
   * @throws IllegalArgumentException if any required parameter is null
   */
  public static DicomState process(
      AdvancedParams params,
      DicomNode callingNode,
      DicomNode calledNode,
      DicomProgress progress,
      Path outputDir,
      URL sopClassURL,
      DicomParam... keys) {
    validateRequiredParameters(callingNode, calledNode, outputDir);
    var options = Objects.requireNonNullElse(params, new AdvancedParams());

    try {
      return executeGetOperation(
          options, callingNode, calledNode, progress, outputDir, sopClassURL, keys);
    } catch (Exception e) {
      LOGGER.error("DICOM C-GET operation failed", e);
      return createErrorState(e);
    }
  }

  private static void validateRequiredParameters(
      DicomNode callingNode, DicomNode calledNode, Path outputDir) {
    Objects.requireNonNull(callingNode, "callingNode cannot be null");
    Objects.requireNonNull(calledNode, "calledNode cannot be null");
    Objects.requireNonNull(outputDir, "outputDir cannot be null");
  }

  private static DicomState executeGetOperation(
      AdvancedParams options,
      DicomNode callingNode,
      DicomNode calledNode,
      DicomProgress progress,
      Path outputDir,
      URL sopClassURL,
      DicomParam... keys)
      throws Exception {

    var getSCU = new GetSCU(progress);
    var service = new DeviceOpService(getSCU.getDevice());

    try {
      configureGetSCU(getSCU, options, callingNode, calledNode, outputDir, sopClassURL);
      addQueryKeys(getSCU, keys);

      service.start();
      return performRetrievalOperation(getSCU);

    } finally {
      StreamUtil.safeClose(getSCU);
      service.stop();
    }
  }

  private static void configureGetSCU(
      GetSCU getSCU,
      AdvancedParams options,
      DicomNode callingNode,
      DicomNode calledNode,
      Path outputDir,
      URL sopClassURL)
      throws IOException {

    var remote = getSCU.getRemoteConnection();
    var conn = getSCU.getConnection();

    // Configure connection parameters
    options.configureConnect(getSCU.getAAssociateRQ(), remote, calledNode);
    options.configureBind(getSCU.getApplicationEntity(), conn, callingNode);
    options.configure(conn);
    options.configureTLS(conn, remote);

    // Configure operation parameters
    getSCU.setPriority(options.getPriority());

    getSCU.setStorageDirectory(outputDir);

    getSCU.setInformationModel(
        getInformationModel(options),
        options.getTsuidOrder(),
        options.getQueryOptions().contains(QueryOption.RELATIONAL));

    configureRelatedSOPClass(getSCU, sopClassURL);
  }

  private static void addQueryKeys(GetSCU getSCU, DicomParam... keys) {
    var dcmState = getSCU.getState();
    for (var param : keys) {
      var values = param.getValues();
      getSCU.addKey(param.getTag(), values);
      if (values != null && values.length > 0) {
        dcmState.addDicomMatchingKeys(param);
      }
    }
  }

  private static DicomState performRetrievalOperation(GetSCU getSCU) throws Exception {
    var startTime = System.currentTimeMillis();
    getSCU.open();
    var connectTime = System.currentTimeMillis();
    try {
      getSCU.retrieve();
      ServiceUtil.forceGettingAttributes(getSCU.getState(), getSCU);
      var endTime = System.currentTimeMillis();

      return buildSuccessState(getSCU, startTime, connectTime, endTime);
    } catch (Exception e) {
      if (e instanceof InterruptedException) {
        Thread.currentThread().interrupt();
      }
      LOGGER.error("DICOM C-GET retrieval failed", e);
      ServiceUtil.forceGettingAttributes(getSCU.getState(), getSCU);
      return DicomState.buildMessage(getSCU.getState(), null, e);
    }
  }

  private static DicomState buildSuccessState(
      GetSCU getSCU, long startTime, long connectTime, long endTime) {
    var timeMsg =
        MessageFormat.format(
            "DICOM C-GET connected in {2}ms from {0} to {1}. Retrieved files in {3}ms.",
            getSCU.getAAssociateRQ().getCallingAET(),
            getSCU.getAAssociateRQ().getCalledAET(),
            connectTime - startTime,
            endTime - connectTime);

    var dcmState = DicomState.buildMessage(getSCU.getState(), timeMsg, null);
    dcmState.addProcessTime(startTime, connectTime, endTime);
    dcmState.setBytesSize(getSCU.getTotalSize());
    return dcmState;
  }

  private static DicomState createErrorState(Exception e) {
    return DicomState.buildMessage(
        new DicomState(
            Status.UnableToProcess,
            "DICOM Get failed" + StringUtil.COLON_AND_SPACE + e.getMessage(),
            null),
        null,
        e);
  }

  private static void configureRelatedSOPClass(GetSCU getSCU, URL url) throws IOException {
    var properties = new Properties();
    try {
      if (url == null) {
        try (var inputStream =
            getSCU.getClass().getResourceAsStream(DEFAULT_STORE_TCS_PROPERTIES)) {
          if (inputStream != null) {
            properties.load(inputStream);
          }
        }
      } else {
        try (var inputStream = url.openStream()) {
          properties.load(inputStream);
        }
      }
      for (var entry : properties.entrySet()) {
        configureStorageSOPClass(getSCU, (String) entry.getKey(), (String) entry.getValue());
      }
    } catch (Exception e) {
      LOGGER.error("Failed to read SOP class configuration", e);
    }
  }

  private static void configureStorageSOPClass(GetSCU getSCU, String cuid, String tsuids) {
    var transferSyntaxes = StringUtils.split(tsuids, ';');
    for (int i = 0; i < transferSyntaxes.length; i++) {
      transferSyntaxes[i] = CLIUtils.toUID(transferSyntaxes[i]);
    }
    getSCU.addOfferedStorageSOPClass(CLIUtils.toUID(cuid), transferSyntaxes);
  }

  private static InformationModel getInformationModel(AdvancedParams options) {
    var model = options.getInformationModel();
    return model instanceof InformationModel im ? im : InformationModel.StudyRoot;
  }
}
