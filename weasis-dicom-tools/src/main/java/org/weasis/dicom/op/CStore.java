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
import java.io.InputStream;
import java.net.URL;
import java.text.MessageFormat;
import java.util.List;
import java.util.Objects;
import java.util.Properties;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.net.ApplicationEntity;
import org.dcm4che3.net.Connection;
import org.dcm4che3.net.Device;
import org.dcm4che3.net.Status;
import org.dcm4che3.tool.storescu.StoreSCU;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.util.FileUtil;
import org.weasis.core.util.StringUtil;
import org.weasis.dicom.param.AdvancedParams;
import org.weasis.dicom.param.CstoreParams;
import org.weasis.dicom.param.DeviceOpService;
import org.weasis.dicom.param.DicomNode;
import org.weasis.dicom.param.DicomProgress;
import org.weasis.dicom.param.DicomState;
import org.weasis.dicom.util.ServiceUtil;

/**
 * DICOM C-STORE operations utility class for sending DICOM objects to remote storage servers.
 *
 * <p>Provides static methods to perform C-STORE operations with various parameter combinations,
 * supporting progress tracking, advanced connection parameters, and custom store options.
 */
public final class CStore {

  private static final Logger LOGGER = LoggerFactory.getLogger(CStore.class);
  private static final String DEVICE_NAME = "storescu";

  private CStore() {
    // Utility class - prevent instantiation
  }

  /**
   * Performs C-STORE operation with basic parameters.
   *
   * @param callingNode the calling DICOM node configuration
   * @param calledNode the called DICOM node configuration
   * @param files the list of file paths to store
   * @return the DICOM state containing response, status, and error information
   */
  public static DicomState process(
      DicomNode callingNode, DicomNode calledNode, List<String> files) {
    return process(null, callingNode, calledNode, files, null, null);
  }

  /**
   * Performs C-STORE operation with progress tracking.
   *
   * @param callingNode the calling DICOM node configuration
   * @param calledNode the called DICOM node configuration
   * @param files the list of file paths to store
   * @param progress the progress handler
   * @return the DICOM state containing response, status, and error information
   */
  public static DicomState process(
      DicomNode callingNode, DicomNode calledNode, List<String> files, DicomProgress progress) {
    return process(null, callingNode, calledNode, files, progress, null);
  }

  /**
   * Performs C-STORE operation with advanced parameters.
   *
   * @param params optional advanced parameters (proxy, authentication, connection and TLS)
   * @param callingNode the calling DICOM node configuration
   * @param calledNode the called DICOM node configuration
   * @param files the list of file paths to store
   * @return the DICOM state containing response, status, and error information
   */
  public static DicomState process(
      AdvancedParams params, DicomNode callingNode, DicomNode calledNode, List<String> files) {
    return process(params, callingNode, calledNode, files, null, null);
  }

  /**
   * Performs C-STORE operation with advanced parameters and progress tracking.
   *
   * @param params the optional advanced parameters (proxy, authentication, connection and TLS)
   * @param callingNode the calling DICOM node configuration
   * @param calledNode the called DICOM node configuration
   * @param files the list of file paths to store
   * @param progress the progress handler
   * @return the DICOM state containing response, status, and error information
   */
  public static DicomState process(
      AdvancedParams params,
      DicomNode callingNode,
      DicomNode calledNode,
      List<String> files,
      DicomProgress progress) {
    return process(params, callingNode, calledNode, files, progress, null);
  }

  /**
   * Performs C-STORE operation with full parameter control.
   *
   * @param params the optional advanced parameters (proxy, authentication, connection and TLS)
   * @param callingNode the calling DICOM node configuration
   * @param calledNode the called DICOM node configuration
   * @param files the list of file paths to store
   * @param progress the progress handler
   * @param cstoreParams C-STORE specific options
   * @return the DICOM state containing response, status, and error information
   */
  public static DicomState process(
      AdvancedParams params,
      DicomNode callingNode,
      DicomNode calledNode,
      List<String> files,
      DicomProgress progress,
      CstoreParams cstoreParams) {

    validateParameters(callingNode, calledNode);

    var options = Objects.requireNonNullElse(params, new AdvancedParams());
    var storeOptions =
        Objects.requireNonNullElse(cstoreParams, new CstoreParams(null, false, null));

    try {
      return executeStoreOperation(options, storeOptions, callingNode, calledNode, files, progress);
    } catch (Exception e) {
      LOGGER.error("C-STORE operation failed", e);
      return createErrorState(e);
    }
  }

  private static void validateParameters(DicomNode callingNode, DicomNode calledNode) {
    Objects.requireNonNull(callingNode, "callingNode cannot be null");
    Objects.requireNonNull(calledNode, "calledNode cannot be null");
  }

  private static DicomState executeStoreOperation(
      AdvancedParams options,
      CstoreParams storeOptions,
      DicomNode callingNode,
      DicomNode calledNode,
      List<String> files,
      DicomProgress progress)
      throws Exception {

    try (var storeSCU = createStoreSCU(options, storeOptions, callingNode, calledNode, progress)) {
      return performStore(storeSCU, files);
    }
  }

  private static StoreSCU createStoreSCU(
      AdvancedParams options,
      CstoreParams storeOptions,
      DicomNode callingNode,
      DicomNode calledNode,
      DicomProgress progress)
      throws IOException {

    var device = new Device(DEVICE_NAME);
    var conn = new Connection();
    device.addConnection(conn);
    var ae = new ApplicationEntity(callingNode.getAet());
    device.addApplicationEntity(ae);
    ae.addConnection(conn);
    var storeSCU = new StoreSCU(ae, progress, storeOptions.getDicomEditors());
    var remote = storeSCU.getRemoteConnection();

    configureConnection(options, storeSCU, conn, remote, callingNode, calledNode);
    configureStoreOptions(storeSCU, storeOptions, options);

    return storeSCU;
  }

  private static void configureConnection(
      AdvancedParams options,
      StoreSCU storeSCU,
      Connection conn,
      Connection remote,
      DicomNode callingNode,
      DicomNode calledNode)
      throws IOException {

    options.configureConnect(storeSCU.getAAssociateRQ(), remote, calledNode);
    options.configureBind(new ApplicationEntity(callingNode.getAet()), conn, callingNode);
    options.configure(conn);
    options.configureTLS(conn, remote);
  }

  private static void configureStoreOptions(
      StoreSCU storeSCU, CstoreParams storeOptions, AdvancedParams options) throws IOException {

    storeSCU.setAttributes(new Attributes());

    if (storeOptions.isExtendNegotiation()) {
      configureRelatedSOPClass(storeSCU, storeOptions.getExtendSopClassesURL());
    }
    storeSCU.setPriority(options.getPriority());
  }

  private static DicomState performStore(StoreSCU storeSCU, List<String> files) throws Exception {
    storeSCU.scanFiles(files, false);

    int filesScanned = storeSCU.getFilesScanned();
    if (filesScanned == 0) {
      return new DicomState(Status.UnableToProcess, "No DICOM files found", null);
    }

    var service = new DeviceOpService(storeSCU.getApplicationEntity().getDevice());
    service.start();
    try {
      return executeTransfer(storeSCU);
    } finally {
      service.stop();
    }
  }

  private static DicomState executeTransfer(StoreSCU storeSCU) throws Exception {
    long startTime = System.currentTimeMillis();
    storeSCU.open();
    long connectTime = System.currentTimeMillis();
    storeSCU.sendFiles();
    long transferTime = System.currentTimeMillis();

    ServiceUtil.forceGettingAttributes(storeSCU.getState(), storeSCU);

    return createSuccessState(storeSCU, startTime, connectTime, transferTime);
  }

  private static DicomState createSuccessState(
      StoreSCU storeSCU, long startTime, long connectTime, long transferTime) {

    var associateRQ = storeSCU.getAAssociateRQ();
    String timeMsg =
        MessageFormat.format(
            "DICOM C-STORE connected in {2}ms from {0} to {1}. Stored files in {3}ms. Total size {4}",
            associateRQ.getCallingAET(),
            associateRQ.getCalledAET(),
            connectTime - startTime,
            transferTime - connectTime,
            FileUtil.humanReadableByte(storeSCU.getTotalSize(), false));
    var dcmState = DicomState.buildMessage(storeSCU.getState(), timeMsg, null);
    dcmState.addProcessTime(startTime, connectTime, transferTime);
    dcmState.setBytesSize(storeSCU.getTotalSize());
    return dcmState;
  }

  private static DicomState createErrorState(Exception e) {
    if (e instanceof InterruptedException) {
      Thread.currentThread().interrupt();
    }
    return DicomState.buildMessage(
        new DicomState(
            Status.UnableToProcess,
            "DICOM Store failed" + StringUtil.COLON_AND_SPACE + e.getMessage(),
            null),
        null,
        e);
  }

  private static void configureRelatedSOPClass(StoreSCU storeSCU, URL url) throws IOException {
    storeSCU.enableSOPClassRelationshipExtNeg(true);

    var properties = new Properties();
    try (InputStream inputStream = getSOPClassesInputStream(storeSCU, url)) {
      properties.load(inputStream);
    } catch (Exception e) {
      LOGGER.error("Failed to read SOP classes configuration", e);
    }

    storeSCU.relSOPClasses.init(properties);
  }

  private static InputStream getSOPClassesInputStream(StoreSCU storeSCU, URL url)
      throws IOException {
    return url != null
        ? url.openStream()
        : storeSCU.getClass().getResourceAsStream("rel-sop-classes.properties");
  }
}
