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
import java.net.URL;
import java.security.GeneralSecurityException;
import java.util.Objects;
import org.dcm4che3.net.Connection;
import org.dcm4che3.net.TransferCapability;
import org.dcm4che3.tool.storescp.StoreSCP;
import org.weasis.dicom.param.AdvancedParams;
import org.weasis.dicom.param.DeviceListenerService;
import org.weasis.dicom.param.DicomNode;
import org.weasis.dicom.param.DicomProgress;
import org.weasis.dicom.param.ListenerParams;

public class DicomListener {
  private final StoreSCP storeSCP;
  private final DeviceListenerService deviceService;

  public DicomListener(File storageDir) {
    this(storageDir, null);
  }

  public DicomListener(File storageDir, DicomProgress dicomProgress) {
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
   * Start the DICOM Listener
   *
   * @param scpNode the listener DICOM node. Set hostname to null for binding all the network
   *     interface. For binding all the AETs see ListenerParams.
   * @throws IOException
   * @throws GeneralSecurityException
   */
  public void start(DicomNode scpNode) throws Exception {
    start(scpNode, new ListenerParams(true));
  }

  /**
   * Start the DICOM Listener
   *
   * @param scpNode the listener DICOM node. Set hostname to null for binding all the network
   *     interface. For binding all the AETs see ListenerParams.
   * @param params the listener parameters
   * @throws IOException
   * @throws GeneralSecurityException
   */
  public synchronized void start(DicomNode scpNode, ListenerParams params) throws Exception {
    if (isRunning()) {
      throw new IOException("Cannot start a DICOM Listener because it is already running.");
    }
    storeSCP.setStatus(0);
    storeSCP.setStorageFilePathFormat(params.getStoragePattern());

    AdvancedParams options = Objects.requireNonNull(params).getParams();
    Connection conn = storeSCP.getConnection();
    if (params.isBindCallingAet()) {
      options.configureBind(storeSCP.getApplicationEntity(), conn, scpNode);
    } else {
      options.configureBind(conn, scpNode);
    }
    // configure
    options.configure(conn);
    options.configureTLS(conn, null);

    // Limit the calling AETs
    storeSCP.getApplicationEntity().setAcceptedCallingAETitles(params.getAcceptedCallingAETitles());

    URL transferCapabilityFile = params.getTransferCapabilityFile();
    if (transferCapabilityFile != null) {
      storeSCP.loadDefaultTransferCapability(transferCapabilityFile);
    } else {
      storeSCP
          .getApplicationEntity()
          .addTransferCapability(
              new TransferCapability(null, "*", TransferCapability.Role.SCP, "*"));
    }

    deviceService.start();
  }

  public synchronized void stop() {
    deviceService.stop();
  }
}
