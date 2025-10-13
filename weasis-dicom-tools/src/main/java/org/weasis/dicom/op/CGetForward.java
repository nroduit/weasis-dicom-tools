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
import java.io.Serial;
import java.security.GeneralSecurityException;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Properties;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.ElementDictionary;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.UID;
import org.dcm4che3.data.VR;
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
import org.weasis.core.util.StreamUtil;
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

/**
 * DICOM C-GET forward service that retrieves DICOM objects from one node and forwards them to
 * another destination. This class implements the C-GET SCU functionality while acting as a C-STORE
 * SCP for forwarding data.
 */
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

        private static class Fmi {
          String tsuid;
          String cuid;
          String iuid;

          public Fmi(String tsuid, String cuid, String iuid) {
            this.tsuid = tsuid;
            this.cuid = cuid;
            this.iuid = iuid;
          }
        }

        private static class AbortException extends IllegalStateException {
          @Serial private static final long serialVersionUID = -1153741718853819887L;

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

          var progress = streamSCU.getState().getProgress();
          if (progress != null && progress.isCancelled()) {
            StreamUtil.safeClose(CGetForward.this);
            return;
          }

          try {
            var cuid = rq.getString(Tag.AffectedSOPClassUID);
            var iuid = rq.getString(Tag.AffectedSOPInstanceUID);
            var tsuid = pc.getTransferSyntax();
            Fmi fmi = new Fmi(tsuid, cuid, iuid);

            ensureStreamSCUReady(cuid, tsuid);
            var dataWriter = createDataWriter(as, data, fmi);

            streamSCU.cstore(cuid, iuid, priority, dataWriter, tsuid);

          } catch (AbortException e) {
            notifyProgressionFailure(rq);
            throw e;
          } catch (Exception e) {
            handleStoreException(e, rq);
          }
        }

        private void ensureStreamSCUReady(String cuid, String tsuid) throws IOException {
          if (streamSCU.hasAssociation()) {
            handleExistingAssociation(cuid, tsuid);
          } else {
            initializeNewAssociation(cuid, tsuid);
          }
        }

        private void handleExistingAssociation(String cuid, String tsuid) throws IOException {
          var supportedSyntaxes = streamSCU.getTransferSyntaxesFor(cuid);
          if (!supportedSyntaxes.contains(tsuid)) {
            streamSCU.close(true);
          }

          streamSCU.addData(cuid, tsuid);

          if (!streamSCU.isReadyForDataTransfer()) {
            streamSCU.open();
          }
        }

        private void initializeNewAssociation(String cuid, String tsuid) throws IOException {
          streamSCUService.start();
          streamSCU.addData(cuid, tsuid);
          streamSCU.open();
        }

        private DataWriter createDataWriter(Association as, PDVInputStream data, Fmi fmi)
            throws IOException {
          if (!streamSCU.isReadyForDataTransfer()) {
            throw new IllegalStateException("Association not ready for transfer.");
          }
          var syntax =
              new AdaptTransferSyntax(
                  fmi.tsuid, streamSCU.selectTransferSyntax(fmi.cuid, fmi.tsuid));

          if (canUseDirectStream(syntax, fmi.tsuid)) {
            return new InputStreamDataWriter(data);
          } else {
            return createProcessedDataWriter(as, data, syntax, fmi);
          }
        }

        private boolean canUseDirectStream(AdaptTransferSyntax syntax, String tsuid) {
          return (cstoreParams == null || !cstoreParams.hasDicomEditors())
              && syntax.getRequested().equals(tsuid);
        }

        private DataWriter createProcessedDataWriter(
            Association as, PDVInputStream data, AdaptTransferSyntax syntax, Fmi fmi)
            throws IOException {
          var context = createAttributeEditorContext(as, syntax);

          try (var dicomInputStream = new DicomInputStream(data, syntax.getOriginal())) {
            dicomInputStream.setIncludeBulkData(IncludeBulkData.URI);
            var attributes = dicomInputStream.readDataset();

            if (cstoreParams != null && cstoreParams.hasDicomEditors()) {
              cstoreParams.getDicomEditors().forEach(editor -> editor.apply(attributes, context));
              fmi.cuid = attributes.getString(Tag.SOPClassUID);
              fmi.iuid = attributes.getString(Tag.SOPInstanceUID);
            }

            handleAbortConditions(context, data, as);

            var desc = ImageAdapter.imageTranscode(attributes, syntax, context);
            return ImageAdapter.buildDataWriter(attributes, syntax, context.getEditable(), desc);
          }
        }

        private AttributeEditorContext createAttributeEditorContext(
            Association as, AdaptTransferSyntax syntax) {
          return new AttributeEditorContext(
              syntax.getOriginal(),
              DicomNode.buildRemoteDicomNode(as),
              streamSCU.getRemoteDicomNode());
        }

        private void handleAbortConditions(
            AttributeEditorContext context, PDVInputStream data, Association as)
            throws IOException {
          var abortType = context.getAbort();
          if (abortType == Abort.FILE_EXCEPTION) {
            data.skipAll();
            throw new IllegalStateException(context.getAbortMessage());
          } else if (abortType == Abort.CONNECTION_EXCEPTION) {
            as.abort();
            throw new AbortException("DICOM association abort. " + context.getAbortMessage());
          }
        }

        private void notifyProgressionFailure(Attributes rq) {
          ServiceUtil.notifyProgression(
              streamSCU.getState(),
              rq.getString(Tag.AffectedSOPInstanceUID),
              rq.getString(Tag.AffectedSOPClassUID),
              Status.ProcessingFailure,
              ProgressStatus.FAILED,
              streamSCU.getNumberOfSuboperations());
        }

        private void handleStoreException(Exception e, Attributes rq) throws DicomServiceException {
          if (e instanceof InterruptedException) {
            Thread.currentThread().interrupt();
          }
          LOGGER.error("Error when forwarding to the final destination", e);
          notifyProgressionFailure(rq);
          throw new DicomServiceException(Status.ProcessingFailure, e);
        }
      };

  public CGetForward(DicomNode callingNode, DicomNode destinationNode, DicomProgress progress)
      throws IOException {
    this(null, callingNode, destinationNode, progress, null);
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
   * Creates a C-GET forward instance.
   *
   * @param forwardParams optional advanced parameters for the final destination
   * @param callingNode the calling DICOM node configuration
   * @param destinationNode the final DICOM node configuration
   * @param progress the progress handler
   * @param cstoreParams the cstore parameters
   * @throws IOException if device initialization fails
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
    var serviceRegistry = new DicomServiceRegistry();
    serviceRegistry.addDicomService(storageSCP);
    return serviceRegistry;
  }

  public void setPriority(int priority) {
    this.priority = priority;
  }

  public void setInformationModel(InformationModel model, String[] tss, boolean relational) {
    this.model = model;
    rq.addPresentationContext(new PresentationContext(1, model.cuid, tss));
    if (relational) {
      rq.addExtendedNegotiation(new ExtendedNegotiation(model.cuid, new byte[] {1}));
    }
    if (model.level != null) {
      addLevel(model.level);
    }
  }

  public void addLevel(String level) {
    keys.setString(Tag.QueryRetrieveLevel, VR.CS, level);
  }

  public void addKey(int tag, String... values) {
    var vr = ElementDictionary.vrOf(tag, keys.getPrivateCreator(tag));
    keys.setString(tag, vr, values);
  }

  public void addOfferedStorageSOPClass(String cuid, String... tsuids) {
    if (!rq.containsPresentationContextFor(cuid)) {
      rq.addRoleSelection(new RoleSelection(cuid, false, true));
    }
    rq.addPresentationContext(
        new PresentationContext(2 * rq.getNumberOfPresentationContexts() + 1, cuid, tsuids));
  }

  public void open()
      throws IOException,
          InterruptedException,
          IncompatibleConnectionException,
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
    var rspHandler = createResponseHandler();
    as.cget(model.cuid, priority, keys, null, rspHandler);
  }

  private DimseRSPHandler createResponseHandler() {
    return new DimseRSPHandler(as.nextMessageID()) {
      @Override
      public void onDimseRSP(Association as, Attributes cmd, Attributes data) {
        super.onDimseRSP(as, cmd, data);
        var progress = streamSCU.getState().getProgress();
        if (progress != null) {
          if (streamSCU.getNumberOfSuboperations() == 0) {
            streamSCU.setNumberOfSuboperations(ServiceUtil.getTotalOfSuboperations(cmd));
          }
          if (progress.isCancelled()) {
            try {
              this.cancel(as);
            } catch (IOException e) {
              LOGGER.error("Cancel C-GET", e);
            }
          }
        }
      }
    };
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

  // Static convenience methods for processing studies and series
  public static DicomState processStudy(
      DicomNode callingNode,
      DicomNode calledNode,
      DicomNode destinationNode,
      DicomProgress progress,
      String studyUID) {
    return process(
        null, null, callingNode, calledNode, destinationNode, progress, "STUDY", studyUID, null);
  }

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

  public static DicomState processSeries(
      DicomNode callingNode,
      DicomNode calledNode,
      DicomNode destinationNode,
      DicomProgress progress,
      String seriesUID) {
    return process(
        null, null, callingNode, calledNode, destinationNode, progress, "SERIES", seriesUID, null);
  }

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
    validateProcessParameters(callingNode, calledNode, destinationNode);
    var options = Objects.requireNonNullElse(getParams, new AdvancedParams());

    try (var forward =
        new CGetForward(forwardParams, callingNode, destinationNode, progress, cstoreParams)) {

      setupForward(forward, options, callingNode, calledNode, queryRetrieveLevel, queryUID);

      forward.getStreamSCUService().start();
      forward.open();
      forward.retrieve();

      return forward.getState();

    } catch (Exception e) {
      LOGGER.error("DICOM C-GET forward operation failed", e);
      return new DicomState(
          Status.UnableToProcess,
          "DICOM Get failed" + StringUtil.COLON_AND_SPACE + e.getMessage(),
          null);
    }
  }

  private static void validateProcessParameters(
      DicomNode callingNode, DicomNode calledNode, DicomNode destinationNode) {
    if (callingNode == null || calledNode == null || destinationNode == null) {
      throw new IllegalArgumentException(
          "callingNode, calledNode or destinationNode cannot be null!");
    }
  }

  private static void setupForward(
      CGetForward forward,
      AdvancedParams options,
      DicomNode callingNode,
      DicomNode calledNode,
      String queryRetrieveLevel,
      String queryUID)
      throws IOException {

    configureConnection(options, forward, callingNode, calledNode);
    configureInformationModel(forward, options);

    setQueryAttributes(forward, queryRetrieveLevel, queryUID);
  }

  private static void configureConnection(
      AdvancedParams options, CGetForward forward, DicomNode callingNode, DicomNode calledNode)
      throws IOException {
    Connection remote = forward.getRemoteConnection();
    Connection conn = forward.getConnection();
    options.configureConnect(forward.getAAssociateRQ(), remote, calledNode);
    options.configureBind(forward.getApplicationEntity(), conn, callingNode);

    // configure
    options.configure(conn);
    options.configureTLS(conn, remote);
  }

  private static void configureInformationModel(CGetForward forward, AdvancedParams options) {
    forward.setInformationModel(
        getInformationModel(options),
        options.getTsuidOrder(),
        options.getQueryOptions().contains(QueryOption.RELATIONAL));

    configureRelatedSOPClass(forward);
  }

  private static void setQueryAttributes(
      CGetForward forward, String queryRetrieveLevel, String queryUID) {
    forward.addLevel(queryRetrieveLevel);

    switch (queryRetrieveLevel) {
      case "STUDY":
        forward.addKey(Tag.StudyInstanceUID, queryUID);
        break;
      case "SERIES":
        forward.addKey(Tag.SeriesInstanceUID, queryUID);
        break;
      case "IMAGE":
        forward.addKey(Tag.SOPInstanceUID, queryUID);
        break;
      default:
        throw new IllegalArgumentException(
            "Unsupported query retrieve level: " + queryRetrieveLevel);
    }
  }

  private static void configureRelatedSOPClass(CGetForward getSCU) {
    Properties p = new Properties();
    try {
      p.load(GetSCU.class.getResourceAsStream("store-tcs.properties"));
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
