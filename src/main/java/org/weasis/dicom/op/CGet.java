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

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.text.MessageFormat;
import java.util.Map.Entry;
import java.util.Properties;
import org.dcm4che3.net.Connection;
import org.dcm4che3.net.QueryOption;
import org.dcm4che3.net.Status;
import org.dcm4che3.tool.common.CLIUtils;
import org.dcm4che3.tool.getscu.GetSCU;
import org.dcm4che3.tool.getscu.GetSCU.InformationModel;
import org.dcm4che3.util.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.util.FileUtil;
import org.weasis.core.util.StringUtil;
import org.weasis.dicom.param.AdvancedParams;
import org.weasis.dicom.param.DeviceOpService;
import org.weasis.dicom.param.DicomNode;
import org.weasis.dicom.param.DicomParam;
import org.weasis.dicom.param.DicomProgress;
import org.weasis.dicom.param.DicomState;
import org.weasis.dicom.util.ServiceUtil;

public class CGet {

  private static final Logger LOGGER = LoggerFactory.getLogger(CGet.class);

  private CGet() {}

  /**
   * @param callingNode the calling DICOM node configuration
   * @param calledNode the called DICOM node configuration
   * @param progress the progress handler
   * @param keys the matching and returning keys. DicomParam with no value is a returning key.
   * @return The DicomSate instance which contains the DICOM response, the DICOM status, the error
   *     message and the progression.
   */
  public static DicomState process(
      DicomNode callingNode,
      DicomNode calledNode,
      DicomProgress progress,
      File outputDir,
      DicomParam... keys) {
    return process(null, callingNode, calledNode, progress, outputDir, keys);
  }

  /**
   * @param params the optional advanced parameters (proxy, authentication, connection and TLS)
   * @param callingNode the calling DICOM node configuration
   * @param calledNode the called DICOM node configuration
   * @param progress the progress handler
   * @param keys the matching and returning keys. DicomParam with no value is a returning key.
   * @return The DicomSate instance which contains the DICOM response, the DICOM status, the error
   *     message and the progression.
   */
  public static DicomState process(
      AdvancedParams params,
      DicomNode callingNode,
      DicomNode calledNode,
      DicomProgress progress,
      File outputDir,
      DicomParam... keys) {
    return process(params, callingNode, calledNode, progress, outputDir, null, keys);
  }

  /**
   * @param params the optional advanced parameters (proxy, authentication, connection and TLS)
   * @param callingNode the calling DICOM node configuration
   * @param calledNode the called DICOM node configuration
   * @param progress the progress handler
   * @param keys the matching and returning keys. DicomParam with no value is a returning key.
   * @return The DicomSate instance which contains the DICOM response, the DICOM status, the error
   *     message and the progression.
   */
  public static DicomState process(
      AdvancedParams params,
      DicomNode callingNode,
      DicomNode calledNode,
      DicomProgress progress,
      File outputDir,
      URL sopClassURL,
      DicomParam... keys) {
    if (callingNode == null || calledNode == null || outputDir == null) {
      throw new IllegalArgumentException("callingNode, calledNode or outputDir cannot be null!");
    }
    GetSCU getSCU = null;
    AdvancedParams options = params == null ? new AdvancedParams() : params;

    try {
      getSCU = new GetSCU(progress);
      Connection remote = getSCU.getRemoteConnection();
      Connection conn = getSCU.getConnection();
      options.configureConnect(getSCU.getAAssociateRQ(), remote, calledNode);
      options.configureBind(getSCU.getApplicationEntity(), conn, callingNode);
      DeviceOpService service = new DeviceOpService(getSCU.getDevice());

      // configure
      options.configure(conn);
      options.configureTLS(conn, remote);

      getSCU.setPriority(options.getPriority());

      getSCU.setStorageDirectory(outputDir);

      getSCU.setInformationModel(
          getInformationModel(options),
          options.getTsuidOrder(),
          options.getQueryOptions().contains(QueryOption.RELATIONAL));

      configureRelatedSOPClass(getSCU, sopClassURL);

      DicomState dcmState = getSCU.getState();
      for (DicomParam p : keys) {
        String[] values = p.getValues();
        getSCU.addKey(p.getTag(), values);
        if (values != null && values.length > 0) {
          dcmState.addDicomMatchingKeys(p);
        }
      }

      service.start();
      try {
        long t1 = System.currentTimeMillis();
        getSCU.open();
        long t2 = System.currentTimeMillis();
        getSCU.retrieve();
        ServiceUtil.forceGettingAttributes(dcmState, getSCU);
        long t3 = System.currentTimeMillis();
        String timeMsg =
            MessageFormat.format(
                "DICOM C-GET connected in {2}ms from {0} to {1}. Get files in {3}ms.",
                getSCU.getAAssociateRQ().getCallingAET(),
                getSCU.getAAssociateRQ().getCalledAET(),
                t2 - t1,
                t3 - t2);
        dcmState = DicomState.buildMessage(dcmState, timeMsg, null);
        dcmState.addProcessTime(t1, t2, t3);
        dcmState.setBytesSize(getSCU.getTotalSize());
        return dcmState;
      } catch (Exception e) {
        if (e instanceof InterruptedException) {
          Thread.currentThread().interrupt();
        }
        LOGGER.error("getscu", e);
        ServiceUtil.forceGettingAttributes(getSCU.getState(), getSCU);
        return DicomState.buildMessage(getSCU.getState(), null, e);
      } finally {
        FileUtil.safeClose(getSCU);
        service.stop();
      }
    } catch (Exception e) {
      LOGGER.error("getscu", e);
      return DicomState.buildMessage(
          new DicomState(
              Status.UnableToProcess,
              "DICOM Get failed" + StringUtil.COLON_AND_SPACE + e.getMessage(),
              null),
          null,
          e);
    }
  }

  private static void configureRelatedSOPClass(GetSCU getSCU, URL url) throws IOException {
    Properties p = new Properties();
    try {
      if (url == null) {
        p.load(getSCU.getClass().getResourceAsStream("store-tcs.properties"));
      } else {
        try (InputStream in = url.openStream()) {
          p.load(in);
        }
      }
      for (Entry<Object, Object> entry : p.entrySet()) {
        configureStorageSOPClass(getSCU, (String) entry.getKey(), (String) entry.getValue());
      }
    } catch (Exception e) {
      LOGGER.error("Read sop classes", e);
    }
  }

  private static void configureStorageSOPClass(GetSCU getSCU, String cuid, String tsuids) {
    String[] ts = StringUtils.split(tsuids, ';');
    for (int i = 0; i < ts.length; i++) {
      ts[i] = CLIUtils.toUID(ts[i]);
    }
    getSCU.addOfferedStorageSOPClass(CLIUtils.toUID(cuid), ts);
  }

  private static InformationModel getInformationModel(AdvancedParams options) {
    Object model = options.getInformationModel();
    if (model instanceof InformationModel) {
      return (InformationModel) model;
    }
    return InformationModel.StudyRoot;
  }
}
