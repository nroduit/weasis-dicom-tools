/*
 * Copyright (c) 2017-2019 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.tool;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Objects;
import org.dcm4che3.net.Connection;
import org.dcm4che3.net.TransferCapability;
import org.dcm4che3.tool.storescp.StoreSCP;
import org.weasis.core.util.annotations.Generated;
import org.weasis.dicom.param.AdvancedParams;
import org.weasis.dicom.param.DeviceListenerService;
import org.weasis.dicom.param.DicomNode;
import org.weasis.dicom.param.DicomProgress;
import org.weasis.dicom.param.ListenerParams;

/**
 * A DICOM listener that provides SCP (Service Class Provider) functionality for receiving and
 * storing DICOM files.
 */
public class DicomListener {
  private final StoreSCP storeSCP;
  private final DeviceListenerService deviceService;

  /**
   * Creates a DICOM listener with the specified storage directory.
   *
   * @param storageDir the directory where received DICOM files will be stored
   */
  public DicomListener(Path storageDir) {
    this(storageDir, null);
  }

  /**
   * Creates a DICOM listener with the specified storage directory and progress monitoring.
   *
   * @param storageDir the directory where received DICOM files will be stored
   * @param dicomProgress optional progress monitor for DICOM operations
   */
  public DicomListener(Path storageDir, DicomProgress dicomProgress) {
    this.storeSCP = new StoreSCP(storageDir, null, dicomProgress);
    this.deviceService = new DeviceListenerService(storeSCP.getDevice());
  }

  public boolean isRunning() {
    return storeSCP.getConnection().isListening();
  }

  public StoreSCP getStoreSCP() {
    return storeSCP;
  }

  /**
   * Starts the DICOM listener with default parameters.
   *
   * @param scpNode the listener DICOM node (set hostname to null for binding all network
   *     interfaces)
   * @throws IOException if the listener is already running or cannot be started
   * @throws Exception for other initialization errors
   */
  public void start(DicomNode scpNode) throws Exception {
    start(scpNode, new ListenerParams(true));
  }

  /**
   * Starts the DICOM listener with specified parameters.
   *
   * @param scpNode the listener DICOM node (set hostname to null for binding all network
   *     interfaces)
   * @param params the listener configuration parameters
   * @throws IOException if the listener is already running or cannot be started
   * @throws Exception for other initialization errors
   */
  public synchronized void start(DicomNode scpNode, ListenerParams params) throws Exception {
    validateCanStart();

    initializeStoreSCP(params);
    configureConnection(scpNode, params);
    configureTransferCapabilities(params);

    deviceService.start();
  }

  public synchronized void stop() {
    deviceService.stop();
  }

  private void validateCanStart() throws IOException {
    if (isRunning()) {
      throw new IOException("Cannot start a DICOM Listener because it is already running.");
    }
  }

  private void initializeStoreSCP(ListenerParams params) {
    storeSCP.setStatus(0);
    storeSCP.setStorageFilePathFormat(params.getStoragePattern());
  }

  private void configureConnection(DicomNode scpNode, ListenerParams params) throws Exception {
    var options = Objects.requireNonNull(params, "params cannot be null").getParams();
    var connection = storeSCP.getConnection();

    configureBinding(options, connection, scpNode, params);
    options.configure(connection);
    options.configureTLS(connection, null);

    storeSCP.getApplicationEntity().setAcceptedCallingAETitles(params.getAcceptedCallingAETitles());
  }

  private void configureBinding(
      AdvancedParams options, Connection connection, DicomNode scpNode, ListenerParams params) {
    if (params.isBindCallingAet()) {
      options.configureBind(storeSCP.getApplicationEntity(), connection, scpNode);
    } else {
      options.configureBind(connection, scpNode);
    }
  }

  private void configureTransferCapabilities(ListenerParams params) {
    var transferCapabilityFile = params.getTransferCapabilityFile();

    if (transferCapabilityFile != null) {
      storeSCP.loadDefaultTransferCapability(transferCapabilityFile);
    } else {
      addDefaultTransferCapability();
    }
  }

  private void addDefaultTransferCapability() {
    storeSCP
        .getApplicationEntity()
        .addTransferCapability(new TransferCapability(null, "*", TransferCapability.Role.SCP, "*"));
  }

  // Legacy method kept for backward compatibility

  /**
   * @deprecated use {@link #DicomListener(Path, DicomProgress)} instead
   */
  @Generated
  @Deprecated(since = "5.34.0.3", forRemoval = true)
  public DicomListener(File storageDir) {
    this(storageDir.toPath(), null);
  }

  /**
   * @deprecated use {@link #DicomListener(Path, DicomProgress)} instead
   */
  @Generated
  @Deprecated(since = "5.34.0.3", forRemoval = true)
  public DicomListener(File storageDir, DicomProgress dicomProgress) {
    this(storageDir.toPath(), dicomProgress);
  }
}
