/*
 * Copyright (c) 2014-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at http://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.op;

import java.nio.file.Files;
import java.nio.file.Path;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Stream;
import org.dcm4che6.conf.model.Connection;
import org.dcm4che6.net.AAssociate;
import org.dcm4che6.net.Association;
import org.dcm4che6.net.DicomServiceRegistry;
import org.dcm4che6.net.DimseRSP;
import org.dcm4che6.net.Status;
import org.dcm4che6.net.TCPConnector;
import org.dcm4che6.util.TagUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.util.FileUtil;
import org.weasis.core.util.StringUtil;
import org.weasis.dicom.param.AdvancedParams;
import org.weasis.dicom.param.AttributeEditorContext;
import org.weasis.dicom.param.ConnectOptions;
import org.weasis.dicom.param.CstoreParams;
import org.weasis.dicom.param.DicomFileStream;
import org.weasis.dicom.param.DicomForwardDestination;
import org.weasis.dicom.param.DicomNode;
import org.weasis.dicom.param.DicomProgress;
import org.weasis.dicom.param.DicomState;
import org.weasis.dicom.param.ForwardDicomNode;
import org.weasis.dicom.util.ServiceUtil;
import org.weasis.dicom.util.ServiceUtil.ProgressStatus;

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
  public static DicomState process(DicomNode callingNode, DicomNode calledNode, List<Path> files) {
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
      DicomNode callingNode, DicomNode calledNode, List<Path> files, DicomProgress progress) {
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
      AdvancedParams params, DicomNode callingNode, DicomNode calledNode, List<Path> files) {
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
      List<Path> files,
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
      List<Path> files,
      DicomProgress progress,
      CstoreParams cstoreParams) {
    if (callingNode == null || calledNode == null) {
      throw new IllegalArgumentException("callingNode or calledNode cannot be null!");
    }
    if (files == null || files.isEmpty()) {
      throw new IllegalArgumentException("No files to store!");
    }

    AdvancedParams options = params == null ? new AdvancedParams() : params;
    CstoreParams storeOptions =
        cstoreParams == null ? new CstoreParams(null, false, null) : cstoreParams;
    ConnectOptions connectOptions = options.getConnectOptions();
    if (connectOptions == null) {
      connectOptions = new ConnectOptions();
    }

    List<DicomFileStream> fileInfos = new ArrayList<>();
    long totalSize = 0;
    try {
      AttributeEditorContext context =
          new AttributeEditorContext(
              new DicomForwardDestination(
                  options,
                  new ForwardDicomNode(callingNode),
                  calledNode,
                  false,
                  progress,
                  storeOptions.getDicomEditors()));
      for (Path path : files) {
        try (Stream<Path> walk = Files.walk(path)) {
          walk.forEach(
              p -> {
                DicomFileStream fileInfo = new DicomFileStream(path, context);
                if (fileInfo.isValid()) {
                  fileInfos.add(fileInfo);
                }
              });
        }
      }
      DicomServiceRegistry serviceRegistry = new DicomServiceRegistry();
      AAssociate.RQ rq = new AAssociate.RQ();
      rq.setCallingAETitle(callingNode.getAet());
      rq.setCalledAETitle(calledNode.getAet());
      fileInfos.forEach(
          info -> rq.findOrAddPresentationContext(info.getSopClassUID(), info.getTransferSyntax()));
      TCPConnector<Association> inst =
          new TCPConnector<>(
              (connector, role) -> new Association(connector, role, serviceRegistry));
      Connection local = ServiceUtil.getConnection(callingNode);
      Connection remote = ServiceUtil.getConnection(calledNode);
      options.configureTLS(local, remote);
      CompletableFuture<Void> task = CompletableFuture.runAsync(inst);
      long t1 = System.currentTimeMillis();
      Association as =
          inst.connect(local, remote)
              .thenCompose(as1 -> as1.open(rq))
              .get(connectOptions.getConnectTimeout(), TimeUnit.MILLISECONDS);
      long t2 = System.currentTimeMillis();
      DicomState state = new DicomState(progress);
      for (DicomFileStream fileInfo : fileInfos) {
        CompletableFuture<DimseRSP> s =
            as.cstore(
                fileInfo.getSopClassUID(),
                fileInfo.getSopInstanceUID(),
                fileInfo,
                fileInfo.getTransferSyntax());

        ProgressStatus ps = ServiceUtil.setDicomRSP(s.get(), state, fileInfos.size());
        switch (ps) {
          case COMPLETED:
            ps = ProgressStatus.COMPLETED;
            totalSize += fileInfo.getLength();
            break;
          case WARNING:
            ps = ProgressStatus.WARNING;
            totalSize += fileInfo.getLength();
            System.err.println(
                MessageFormat.format(
                    "WARNING: Received C-STORE-RSP with Status {0}H for {1}",
                    TagUtils.shortToHexString(state.getStatus().orElse(Status.UnableToProcess)),
                    fileInfo.getPath()));
            System.err.println(s.get().command);
            break;
          default:
            System.err.println(
                MessageFormat.format(
                    "ERROR: Received C-STORE-RSP with Status {0}H for {1}",
                    TagUtils.shortToHexString(state.getStatus().orElse(Status.UnableToProcess)),
                    fileInfo.getPath()));
            System.err.println(s.get().command);
        }
      }
      as.release().get(connectOptions.getReleaseTimeout(), TimeUnit.MILLISECONDS);
      long t3 = System.currentTimeMillis();
      as.onClose().get(connectOptions.getReleaseTimeout(), TimeUnit.MILLISECONDS);
      task.cancel(true);

      String timeMsg =
          MessageFormat.format(
              "DICOM C-STORE connected in {2}ms from {0} to {1}. Stored files in {3}ms. Total size {4}",
              rq.getCallingAETitle(),
              rq.getCalledAETitle(),
              t2 - t1,
              t3 - t2,
              FileUtil.humanReadableByte(totalSize, false));
      state.setMessage(timeMsg);
      return state;
    } catch (Exception e) {
      String message = e.getMessage();
      if (!StringUtil.hasText(message) && e.getClass() == TimeoutException.class) {
        message = "Timeout exception";
      }
      message = "DICOM Store failed: " + message;
      LOGGER.error(message, e);
      return new DicomState(Status.UnableToProcess, message, null);
    }
  }
}
