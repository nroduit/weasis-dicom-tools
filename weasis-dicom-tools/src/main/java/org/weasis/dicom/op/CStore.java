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

public class CStore {

  private static final Logger LOGGER = LoggerFactory.getLogger(CStore.class);

  private CStore() {}

  /**
   * @param callingNode the calling DICOM node configuration
   * @param calledNode the called DICOM node configuration
   * @param files the list of file paths
   * @return The DicomSate instance which contains the DICOM response, the DICOM status, the error
   *     message and the progression.
   */
  public static DicomState process(
      DicomNode callingNode, DicomNode calledNode, List<String> files) {
    return process(null, callingNode, calledNode, files);
  }

  /**
   * @param callingNode the calling DICOM node configuration
   * @param calledNode the called DICOM node configuration
   * @param files the list of file paths
   * @param progress the progress handler
   * @return The DicomSate instance which contains the DICOM response, the DICOM status, the error
   *     message and the progression.
   */
  public static DicomState process(
      DicomNode callingNode, DicomNode calledNode, List<String> files, DicomProgress progress) {
    return process(null, callingNode, calledNode, files, progress);
  }

  /**
   * @param params optional advanced parameters (proxy, authentication, connection and TLS)
   * @param callingNode the calling DICOM node configuration
   * @param calledNode the called DICOM node configuration
   * @param files the list of file paths
   * @return The DicomSate instance which contains the DICOM response, the DICOM status, the error
   *     message and the progression.
   */
  public static DicomState process(
      AdvancedParams params, DicomNode callingNode, DicomNode calledNode, List<String> files) {
    return process(params, callingNode, calledNode, files, null);
  }

  /**
   * @param params the optional advanced parameters (proxy, authentication, connection and TLS)
   * @param callingNode the calling DICOM node configuration
   * @param calledNode the called DICOM node configuration
   * @param files the list of file paths
   * @param progress the progress handler
   * @return The DicomSate instance which contains the DICOM response, the DICOM status, the error
   *     message and the progression.
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
   * @param params the optional advanced parameters (proxy, authentication, connection and TLS)
   * @param callingNode the calling DICOM node configuration
   * @param calledNode the called DICOM node configuration
   * @param files the list of file paths
   * @param progress the progress handler
   * @param cstoreParams c-store options, see CstoreParams
   * @return The DicomSate instance which contains the DICOM response, the DICOM status, the error
   *     message and the progression.
   */
  public static DicomState process(
      AdvancedParams params,
      DicomNode callingNode,
      DicomNode calledNode,
      List<String> files,
      DicomProgress progress,
      CstoreParams cstoreParams) {
    if (callingNode == null || calledNode == null) {
      throw new IllegalArgumentException("callingNode or calledNode cannot be null!");
    }

    AdvancedParams options = params == null ? new AdvancedParams() : params;
    CstoreParams storeOptions =
        cstoreParams == null ? new CstoreParams(null, false, null) : cstoreParams;

    StoreSCU storeSCU = null;

    try {
      Device device = new Device("storescu");
      Connection conn = new Connection();
      device.addConnection(conn);
      ApplicationEntity ae = new ApplicationEntity(callingNode.getAet());
      device.addApplicationEntity(ae);
      ae.addConnection(conn);
      storeSCU = new StoreSCU(ae, progress, storeOptions.getDicomEditors());
      Connection remote = storeSCU.getRemoteConnection();
      DeviceOpService service = new DeviceOpService(device);

      options.configureConnect(storeSCU.getAAssociateRQ(), remote, calledNode);
      options.configureBind(ae, conn, callingNode);

      // configure
      options.configure(conn);
      options.configureTLS(conn, remote);

      storeSCU.setAttributes(new Attributes());

      if (storeOptions.isExtendNegociation()) {
        configureRelatedSOPClass(storeSCU, storeOptions.getExtendSopClassesURL());
      }
      // storeSCU.setUIDSuffix(cl.getOptionValue("uid-suffix"));
      storeSCU.setPriority(options.getPriority());

      storeSCU.scanFiles(files, false);

      DicomState dcmState = storeSCU.getState();

      int n = storeSCU.getFilesScanned();
      if (n == 0) {
        return new DicomState(Status.UnableToProcess, "No DICOM file has been found!", null);
      } else {
        service.start();
        try {
          long t1 = System.currentTimeMillis();
          storeSCU.open();
          long t2 = System.currentTimeMillis();
          storeSCU.sendFiles();
          ServiceUtil.forceGettingAttributes(dcmState, storeSCU);
          long t3 = System.currentTimeMillis();
          String timeMsg =
              MessageFormat.format(
                  "DICOM C-STORE connected in {2}ms from {0} to {1}. Stored files in {3}ms. Total size {4}",
                  storeSCU.getAAssociateRQ().getCallingAET(),
                  storeSCU.getAAssociateRQ().getCalledAET(),
                  t2 - t1,
                  t3 - t2,
                  FileUtil.humanReadableByte(storeSCU.getTotalSize(), false));
          dcmState = DicomState.buildMessage(dcmState, timeMsg, null);
          dcmState.addProcessTime(t1, t2, t3);
          dcmState.setBytesSize(storeSCU.getTotalSize());
          return dcmState;
        } catch (Exception e) {
          if (e instanceof InterruptedException) {
            Thread.currentThread().interrupt();
          }
          LOGGER.error("storescu", e);
          ServiceUtil.forceGettingAttributes(storeSCU.getState(), storeSCU);
          return DicomState.buildMessage(storeSCU.getState(), null, e);
        } finally {
          FileUtil.safeClose(storeSCU);
          service.stop();
        }
      }
    } catch (Exception e) {
      LOGGER.error("storescu", e);
      return DicomState.buildMessage(
          new DicomState(
              Status.UnableToProcess,
              "DICOM Store failed" + StringUtil.COLON_AND_SPACE + e.getMessage(),
              null),
          null,
          e);
    } finally {
      FileUtil.safeClose(storeSCU);
    }
  }

  private static void configureRelatedSOPClass(StoreSCU storescu, URL url) throws IOException {
    storescu.enableSOPClassRelationshipExtNeg(true);
    Properties p = new Properties();
    try {
      if (url == null) {
        p.load(storescu.getClass().getResourceAsStream("rel-sop-classes.properties"));
      } else {
        try (InputStream in = url.openStream()) {
          p.load(in);
        }
      }
    } catch (Exception e) {
      LOGGER.error("Read sop classes", e);
    }
    storescu.relSOPClasses.init(p);
  }
}
