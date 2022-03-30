/*
 * Copyright (c) 2017-2020 Weasis Team and other contributors.
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
import java.security.GeneralSecurityException;
import java.text.MessageFormat;
import java.util.Map.Entry;
import java.util.Properties;
import java.util.Set;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.ElementDictionary;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.UID;
import org.dcm4che3.data.VR;
import org.dcm4che3.img.stream.BytesWithImageDescriptor;
import org.dcm4che3.img.stream.ImageAdapter;
import org.dcm4che3.img.stream.ImageAdapter.AdaptTransferSyntax;
import org.dcm4che3.io.DicomInputStream;
import org.dcm4che3.io.DicomInputStream.IncludeBulkData;
import org.dcm4che3.net.ApplicationEntity;
import org.dcm4che3.net.Association;
import org.dcm4che3.net.Connection;
import org.dcm4che3.net.DataWriter;
import org.dcm4che3.net.Device;
import org.dcm4che3.net.DimseRSPHandler;
import org.dcm4che3.net.IncompatibleConnectionException;
import org.dcm4che3.net.InputStreamDataWriter;
import org.dcm4che3.net.PDVInputStream;
import org.dcm4che3.net.QueryOption;
import org.dcm4che3.net.Status;
import org.dcm4che3.net.pdu.AAssociateRQ;
import org.dcm4che3.net.pdu.ExtendedNegotiation;
import org.dcm4che3.net.pdu.PresentationContext;
import org.dcm4che3.net.pdu.RoleSelection;
import org.dcm4che3.net.service.BasicCStoreSCP;
import org.dcm4che3.net.service.DicomServiceException;
import org.dcm4che3.net.service.DicomServiceRegistry;
import org.dcm4che3.tool.common.CLIUtils;
import org.dcm4che3.tool.getscu.GetSCU;
import org.dcm4che3.util.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.util.FileUtil;
import org.weasis.core.util.StringUtil;
import org.weasis.dicom.param.AdvancedParams;
import org.weasis.dicom.param.AttributeEditorContext;
import org.weasis.dicom.param.AttributeEditorContext.Abort;
import org.weasis.dicom.param.CstoreParams;
import org.weasis.dicom.param.DeviceOpService;
import org.weasis.dicom.param.DicomNode;
import org.weasis.dicom.param.DicomProgress;
import org.weasis.dicom.param.DicomState;
import org.weasis.dicom.util.ServiceUtil;
import org.weasis.dicom.util.ServiceUtil.ProgressStatus;
import org.weasis.dicom.util.StoreFromStreamSCU;

public class CGetForward implements AutoCloseable {

  private static final Logger LOGGER = LoggerFactory.getLogger(CGetForward.class);

  public enum InformationModel {
    PatientRoot(UID.PatientRootQueryRetrieveInformationModelGet, "STUDY"),
    StudyRoot(UID.StudyRootQueryRetrieveInformationModelGet, "STUDY"),
    PatientStudyOnly(UID.PatientStudyOnlyQueryRetrieveInformationModelGet, "STUDY"),
    CompositeInstanceRoot(UID.CompositeInstanceRootRetrieveGet, "IMAGE"),
    WithoutBulkData(UID.CompositeInstanceRetrieveWithoutBulkDataGet, null),
    HangingProtocol(UID.HangingProtocolInformationModelGet, null),
    ColorPalette(UID.ColorPaletteQueryRetrieveInformationModelGet, null);

    final String cuid;
    final String level;

    InformationModel(String cuid, String level) {
      this.cuid = cuid;
      this.level = level;
    }
  }

  private final Device device = new Device("getscu");
  private final ApplicationEntity ae;
  private final Connection conn = new Connection();
  private final Connection remote = new Connection();
  private final AAssociateRQ rq = new AAssociateRQ();
  private int priority;
  private InformationModel model;

  private final Attributes keys = new Attributes();
  private Association as;

  private final StoreFromStreamSCU streamSCU;
  private final DeviceOpService streamSCUService;
  private final CstoreParams cstoreParams;

  private final BasicCStoreSCP storageSCP =
      new BasicCStoreSCP("*") {
        class AbortException extends IllegalStateException {
          private static final long serialVersionUID = -1153741718853819887L;

          public AbortException(String s) {
            super(s);
          }
        }

        @Override
        protected void store(
            Association as,
            PresentationContext pc,
            Attributes rq,
            PDVInputStream data,
            Attributes rsp)
            throws IOException {

          DicomProgress p = streamSCU.getState().getProgress();
          if (p != null) {
            if (p.isCancel()) {
              FileUtil.safeClose(CGetForward.this);
              return;
            }
          }

          try {
            String cuid = rq.getString(Tag.AffectedSOPClassUID);
            String iuid = rq.getString(Tag.AffectedSOPInstanceUID);
            String tsuid = pc.getTransferSyntax();

            if (streamSCU.hasAssociation()) {
              // Handle dynamically new SOPClassUID
              Set<String> tss = streamSCU.getTransferSyntaxesFor(cuid);
              if (!tss.contains(tsuid)) {
                streamSCU.close(true);
              }

              // Add Presentation Context for the association
              streamSCU.addData(cuid, tsuid);

              if (!streamSCU.isReadyForDataTransfer()) {
                // If connection has been closed just reopen
                streamSCU.open();
              }
            } else {
              streamSCUService.start();
              // Add Presentation Context for the association
              streamSCU.addData(cuid, tsuid);
              streamSCU.open();
            }

            DicomInputStream in = null;
            try {
              if (!streamSCU.isReadyForDataTransfer()) {
                throw new IllegalStateException("Association not ready for transfer.");
              }
              DataWriter dataWriter;
              AdaptTransferSyntax syntax =
                  new AdaptTransferSyntax(tsuid, streamSCU.selectTransferSyntax(cuid, tsuid));
              if ((cstoreParams == null || !cstoreParams.hasDicomEditors())
                  && syntax.getRequested().equals(tsuid)) {
                dataWriter = new InputStreamDataWriter(data);
              } else {
                AttributeEditorContext context =
                    new AttributeEditorContext(
                        syntax.getOriginal(),
                        DicomNode.buildRemoteDicomNode(as),
                        streamSCU.getRemoteDicomNode());
                in = new DicomInputStream(data, tsuid);
                in.setIncludeBulkData(IncludeBulkData.URI);
                Attributes attributes = in.readDataset();
                if (cstoreParams != null && cstoreParams.hasDicomEditors()) {
                  cstoreParams.getDicomEditors().forEach(e -> e.apply(attributes, context));
                  iuid = attributes.getString(Tag.SOPInstanceUID);
                  cuid = attributes.getString(Tag.SOPClassUID);
                }

                if (context.getAbort() == Abort.FILE_EXCEPTION) {
                  data.skipAll();
                  throw new IllegalStateException(context.getAbortMessage());
                } else if (context.getAbort() == Abort.CONNECTION_EXCEPTION) {
                  as.abort();
                  throw new AbortException(
                      "DICOM associtation abort. " + context.getAbortMessage());
                }

                BytesWithImageDescriptor desc =
                    ImageAdapter.imageTranscode(attributes, syntax, context);
                dataWriter =
                    ImageAdapter.buildDataWriter(attributes, syntax, context.getEditable(), desc);
              }

              streamSCU.cstore(cuid, iuid, priority, dataWriter, syntax.getSuitable());
            } catch (AbortException e) {
              ServiceUtil.notifyProgession(
                  streamSCU.getState(),
                  rq.getString(Tag.AffectedSOPInstanceUID),
                  rq.getString(Tag.AffectedSOPClassUID),
                  Status.ProcessingFailure,
                  ProgressStatus.FAILED,
                  streamSCU.getNumberOfSuboperations());
              throw e;
            } catch (Exception e) {
              if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
              }
              LOGGER.error("Error when forwarding to the final destination", e);
              ServiceUtil.notifyProgession(
                  streamSCU.getState(),
                  rq.getString(Tag.AffectedSOPInstanceUID),
                  rq.getString(Tag.AffectedSOPClassUID),
                  Status.ProcessingFailure,
                  ProgressStatus.FAILED,
                  streamSCU.getNumberOfSuboperations());
            } finally {
              FileUtil.safeClose(in);
              // Force to clean if tmp bulk files
              ServiceUtil.safeClose(in);
            }

          } catch (Exception e) {
            throw new DicomServiceException(Status.ProcessingFailure, e);
          }
        }
      };

  public CGetForward(DicomNode callingNode, DicomNode destinationNode, DicomProgress progress)
      throws IOException {
    this(callingNode, destinationNode, progress, null);
  }

  public CGetForward(
      DicomNode callingNode,
      DicomNode destinationNode,
      DicomProgress progress,
      CstoreParams cstoreParams)
      throws IOException {
    this(null, callingNode, destinationNode, progress, cstoreParams);
  }

  /**
   * @param forwardParams the optional advanced parameters (proxy, authentication, connection and
   *     TLS) for the final destination
   * @param callingNode the calling DICOM node configuration
   * @param destinationNode the final DICOM node configuration
   * @param progress the progress handler
   * @param cstoreParams the cstore parameters
   * @throws IOException
   */
  public CGetForward(
      AdvancedParams forwardParams,
      DicomNode callingNode,
      DicomNode destinationNode,
      DicomProgress progress,
      CstoreParams cstoreParams)
      throws IOException {
    this.cstoreParams = cstoreParams;
    this.ae = new ApplicationEntity("GETSCU");
    device.addConnection(conn);
    device.addApplicationEntity(ae);
    ae.addConnection(conn);
    device.setDimseRQHandler(createServiceRegistry());

    this.streamSCU = new StoreFromStreamSCU(forwardParams, callingNode, destinationNode, progress);
    this.streamSCUService = new DeviceOpService(streamSCU.getDevice());
  }

  public ApplicationEntity getApplicationEntity() {
    return ae;
  }

  public Connection getRemoteConnection() {
    return remote;
  }

  public AAssociateRQ getAAssociateRQ() {
    return rq;
  }

  public Association getAssociation() {
    return as;
  }

  public Device getDevice() {
    return device;
  }

  public Attributes getKeys() {
    return keys;
  }

  private DicomServiceRegistry createServiceRegistry() {
    DicomServiceRegistry serviceRegistry = new DicomServiceRegistry();
    serviceRegistry.addDicomService(storageSCP);
    return serviceRegistry;
  }

  public final void setPriority(int priority) {
    this.priority = priority;
  }

  public final void setInformationModel(InformationModel model, String[] tss, boolean relational) {
    this.model = model;
    rq.addPresentationContext(new PresentationContext(1, model.cuid, tss));
    if (relational) {
      rq.addExtendedNegotiation(new ExtendedNegotiation(model.cuid, new byte[] {1}));
    }
    if (model.level != null) {
      addLevel(model.level);
    }
  }

  public void addLevel(String s) {
    keys.setString(Tag.QueryRetrieveLevel, VR.CS, s);
  }

  public void addKey(int tag, String... ss) {
    VR vr = ElementDictionary.vrOf(tag, keys.getPrivateCreator(tag));
    keys.setString(tag, vr, ss);
  }

  public void addOfferedStorageSOPClass(String cuid, String... tsuids) {
    if (!rq.containsPresentationContextFor(cuid)) {
      rq.addRoleSelection(new RoleSelection(cuid, false, true));
    }
    rq.addPresentationContext(
        new PresentationContext(2 * rq.getNumberOfPresentationContexts() + 1, cuid, tsuids));
  }

  public void open()
      throws IOException, InterruptedException, IncompatibleConnectionException,
          GeneralSecurityException {
    as = ae.connect(conn, remote, rq);
  }

  @Override
  public void close() throws IOException, InterruptedException {
    if (as != null && as.isReadyForDataTransfer()) {
      as.waitForOutstandingRSP();
      as.release();
    }
    streamSCU.close(true);
  }

  public void retrieve() throws IOException, InterruptedException {
    retrieve(keys);
  }

  private void retrieve(Attributes keys) throws IOException, InterruptedException {
    DimseRSPHandler rspHandler =
        new DimseRSPHandler(as.nextMessageID()) {

          @Override
          public void onDimseRSP(Association as, Attributes cmd, Attributes data) {
            super.onDimseRSP(as, cmd, data);
            DicomProgress p = streamSCU.getState().getProgress();
            if (p != null) {
              // Set only the initial state
              if (streamSCU.getNumberOfSuboperations() == 0) {
                streamSCU.setNumberOfSuboperations(ServiceUtil.getTotalOfSuboperations(cmd));
              }
              if (p.isCancel()) {
                try {
                  this.cancel(as);
                } catch (IOException e) {
                  LOGGER.error("Cancel C-GET", e);
                }
              }
            }
          }
        };

    retrieve(keys, rspHandler);
  }

  private void retrieve(Attributes keys, DimseRSPHandler rspHandler)
      throws IOException, InterruptedException {
    as.cget(model.cuid, priority, keys, null, rspHandler);
  }

  public Connection getConnection() {
    return conn;
  }

  public DeviceOpService getStreamSCUService() {
    return streamSCUService;
  }

  public StoreFromStreamSCU getStreamSCU() {
    return streamSCU;
  }

  public DicomState getState() {
    return streamSCU.getState();
  }

  /**
   * @param callingNode the calling DICOM node configuration
   * @param calledNode the called DICOM node configuration
   * @param destinationNode the final destination DICOM node configuration
   * @param progress the progress handler
   * @param studyUID the study instance UID to retrieve
   * @return The DicomSate instance which contains the DICOM response, the DICOM status, the error
   *     message and the progression.
   */
  public static DicomState processStudy(
      DicomNode callingNode,
      DicomNode calledNode,
      DicomNode destinationNode,
      DicomProgress progress,
      String studyUID) {
    return process(
        null, null, callingNode, calledNode, destinationNode, progress, "STUDY", studyUID, null);
  }

  /**
   * @param getParams the C-GET optional advanced parameters (proxy, authentication, connection and
   *     TLS)
   * @param forwardParams the C-Store optional advanced parameters (proxy, authentication,
   *     connection and TLS)
   * @param callingNode the calling DICOM node configuration
   * @param calledNode the called DICOM node configuration
   * @param destinationNode the final destination DICOM node configuration
   * @param progress the progress handler
   * @param studyUID the study instance UID to retrieve
   * @return The DicomSate instance which contains the DICOM response, the DICOM status, the error
   *     message and the progression.
   */
  public static DicomState processStudy(
      AdvancedParams getParams,
      AdvancedParams forwardParams,
      DicomNode callingNode,
      DicomNode calledNode,
      DicomNode destinationNode,
      DicomProgress progress,
      String studyUID) {
    return process(
        getParams,
        forwardParams,
        callingNode,
        calledNode,
        destinationNode,
        progress,
        "STUDY",
        studyUID,
        null);
  }

  /**
   * @param getParams the C-GET optional advanced parameters (proxy, authentication, connection and
   *     TLS)
   * @param forwardParams the C-Store optional advanced parameters (proxy, authentication,
   *     connection and TLS)
   * @param callingNode the calling DICOM node configuration
   * @param calledNode the called DICOM node configuration
   * @param destinationNode the final destination DICOM node configuration
   * @param progress the progress handler
   * @param studyUID the study instance UID to retrieve
   * @param cstoreParams
   * @return The DicomSate instance which contains the DICOM response, the DICOM status, the error
   *     message and the progression.
   */
  public static DicomState processStudy(
      AdvancedParams getParams,
      AdvancedParams forwardParams,
      DicomNode callingNode,
      DicomNode calledNode,
      DicomNode destinationNode,
      DicomProgress progress,
      String studyUID,
      CstoreParams cstoreParams) {
    return process(
        getParams,
        forwardParams,
        callingNode,
        calledNode,
        destinationNode,
        progress,
        "STUDY",
        studyUID,
        cstoreParams);
  }

  /**
   * @param callingNode the calling DICOM node configuration
   * @param calledNode the called DICOM node configuration
   * @param destinationNode the final destination DICOM node configuration
   * @param progress the progress handler
   * @param seriesUID the series instance UID to retrieve
   * @return The DicomSate instance which contains the DICOM response, the DICOM status, the error
   *     message and the progression.
   */
  public static DicomState processSeries(
      DicomNode callingNode,
      DicomNode calledNode,
      DicomNode destinationNode,
      DicomProgress progress,
      String seriesUID) {
    return process(
        null, null, callingNode, calledNode, destinationNode, progress, "SERIES", seriesUID, null);
  }

  /**
   * @param getParams the C-GET optional advanced parameters (proxy, authentication, connection and
   *     TLS)
   * @param forwardParams the C-Store optional advanced parameters (proxy, authentication,
   *     connection and TLS)
   * @param callingNode the calling DICOM node configuration
   * @param calledNode the called DICOM node configuration
   * @param destinationNode the final destination DICOM node configuration
   * @param progress the progress handler
   * @param seriesUID the series instance UID to retrieve
   * @return The DicomSate instance which contains the DICOM response, the DICOM status, the error
   *     message and the progression.
   */
  public static DicomState processSeries(
      AdvancedParams getParams,
      AdvancedParams forwardParams,
      DicomNode callingNode,
      DicomNode calledNode,
      DicomNode destinationNode,
      DicomProgress progress,
      String seriesUID) {
    return process(
        getParams,
        forwardParams,
        callingNode,
        calledNode,
        destinationNode,
        progress,
        "SERIES",
        seriesUID,
        null);
  }

  /**
   * @param getParams the C-GET optional advanced parameters (proxy, authentication, connection and
   *     TLS)
   * @param forwardParams the C-Store optional advanced parameters (proxy, authentication,
   *     connection and TLS)
   * @param callingNode the calling DICOM node configuration
   * @param calledNode the called DICOM node configuration
   * @param destinationNode the final destination DICOM node configuration
   * @param progress the progress handler
   * @param seriesUID the series instance UID to retrieve
   * @param cstoreParams
   * @return The DicomSate instance which contains the DICOM response, the DICOM status, the error
   *     message and the progression.
   */
  public static DicomState processSeries(
      AdvancedParams getParams,
      AdvancedParams forwardParams,
      DicomNode callingNode,
      DicomNode calledNode,
      DicomNode destinationNode,
      DicomProgress progress,
      String seriesUID,
      CstoreParams cstoreParams) {
    return process(
        getParams,
        forwardParams,
        callingNode,
        calledNode,
        destinationNode,
        progress,
        "SERIES",
        seriesUID,
        cstoreParams);
  }

  private static DicomState process(
      AdvancedParams getParams,
      AdvancedParams forwardParams,
      DicomNode callingNode,
      DicomNode calledNode,
      DicomNode destinationNode,
      DicomProgress progress,
      String queryRetrieveLevel,
      String queryUID,
      CstoreParams cstoreParams) {
    if (callingNode == null || calledNode == null || destinationNode == null) {
      throw new IllegalArgumentException(
          "callingNode, calledNode or destinationNode cannot be null!");
    }
    CGetForward forward = null;
    AdvancedParams options = getParams == null ? new AdvancedParams() : getParams;

    try {
      forward =
          new CGetForward(forwardParams, callingNode, destinationNode, progress, cstoreParams);
      Connection remote = forward.getRemoteConnection();
      Connection conn = forward.getConnection();
      options.configureConnect(forward.getAAssociateRQ(), remote, calledNode);
      options.configureBind(forward.getApplicationEntity(), conn, callingNode);
      DeviceOpService service = new DeviceOpService(forward.getDevice());

      // configure
      options.configure(conn);
      options.configureTLS(conn, remote);

      forward.setPriority(options.getPriority());

      forward.setInformationModel(
          getInformationModel(options),
          options.getTsuidOrder(),
          options.getQueryOptions().contains(QueryOption.RELATIONAL));

      configureRelatedSOPClass(forward, null);

      if ("SERIES".equals(queryRetrieveLevel)) {
        forward.addKey(Tag.QueryRetrieveLevel, "SERIES");
        forward.addKey(Tag.SeriesInstanceUID, queryUID);
      } else if ("STUDY".equals(queryRetrieveLevel)) {
        forward.addKey(Tag.QueryRetrieveLevel, "STUDY");
        forward.addKey(Tag.StudyInstanceUID, queryUID);
      } else {
        throw new IllegalArgumentException(
            queryRetrieveLevel + " is not supported as query retrieve level!");
      }

      service.start();
      try {
        DicomState dcmState = forward.getState();
        long t1 = System.currentTimeMillis();
        forward.open();
        long t2 = System.currentTimeMillis();
        forward.retrieve();
        ServiceUtil.forceGettingAttributes(dcmState, forward);
        long t3 = System.currentTimeMillis();
        String timeMsg =
            MessageFormat.format(
                "DICOM C-GET connected in {2}ms from {0} to {1}. Get files in {3}ms.",
                forward.getAAssociateRQ().getCallingAET(),
                forward.getAAssociateRQ().getCalledAET(),
                t2 - t1,
                t3 - t2);
        return DicomState.buildMessage(dcmState, timeMsg, null);
      } catch (Exception e) {
        if (e instanceof InterruptedException) {
          Thread.currentThread().interrupt();
        }
        LOGGER.error("getscu", e);
        ServiceUtil.forceGettingAttributes(forward.getState(), forward);
        return DicomState.buildMessage(forward.getState(), null, e);
      } finally {
        FileUtil.safeClose(forward);
        service.stop();
        forward.getStreamSCUService().stop();
      }
    } catch (Exception e) {
      LOGGER.error("getscu", e);
      return new DicomState(
          Status.UnableToProcess,
          "DICOM Get failed" + StringUtil.COLON_AND_SPACE + e.getMessage(),
          null);
    }
  }

  private static void configureRelatedSOPClass(CGetForward getSCU, URL url) throws IOException {
    Properties p = new Properties();
    try {
      if (url == null) {
        p.load(GetSCU.class.getResourceAsStream("store-tcs.properties"));
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

  private static void configureStorageSOPClass(CGetForward getSCU, String cuid, String tsuids) {
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
