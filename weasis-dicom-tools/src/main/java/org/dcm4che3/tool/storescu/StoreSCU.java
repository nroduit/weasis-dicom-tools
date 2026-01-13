/*
 * Copyright (c) 2014-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.dcm4che3.tool.storescu;

import java.io.IOException;
import java.io.InputStream;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.GeneralSecurityException;
import java.text.MessageFormat;
import java.util.List;
import java.util.Objects;
import javax.xml.parsers.ParserConfigurationException;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.UID;
import org.dcm4che3.img.stream.ImageAdapter;
import org.dcm4che3.img.stream.ImageAdapter.AdaptTransferSyntax;
import org.dcm4che3.io.DicomInputStream;
import org.dcm4che3.io.DicomInputStream.IncludeBulkData;
import org.dcm4che3.io.SAXReader;
import org.dcm4che3.net.ApplicationEntity;
import org.dcm4che3.net.Association;
import org.dcm4che3.net.Connection;
import org.dcm4che3.net.DataWriter;
import org.dcm4che3.net.DimseRSPHandler;
import org.dcm4che3.net.IncompatibleConnectionException;
import org.dcm4che3.net.InputStreamDataWriter;
import org.dcm4che3.net.Status;
import org.dcm4che3.net.pdu.AAssociateRQ;
import org.dcm4che3.net.pdu.PresentationContext;
import org.dcm4che3.tool.common.CLIUtils;
import org.dcm4che3.util.StringUtils;
import org.dcm4che3.util.TagUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.dicom.param.AttributeEditor;
import org.weasis.dicom.param.AttributeEditorContext;
import org.weasis.dicom.param.DicomNode;
import org.weasis.dicom.param.DicomProgress;
import org.weasis.dicom.param.DicomState;
import org.weasis.dicom.util.ServiceUtil;
import org.weasis.dicom.util.ServiceUtil.ProgressStatus;
import org.weasis.dicom.util.StoreFromStreamSCU;
import org.xml.sax.SAXException;

/**
 * DICOM Storage Service Class User (SCU) for sending DICOM objects to remote storage servers.
 *
 * <p>This class provides functionality to:
 *
 * <ul>
 *   <li>Scan files and directories for DICOM objects
 *   <li>Establish associations with remote DICOM nodes
 *   <li>Send DICOM objects using C-STORE operations
 *   <li>Handle transfer syntax negotiation and SOP class relationships
 *   <li>Track progress and handle cancellation
 *   <li>Apply attribute editing and UID suffix modifications
 * </ul>
 *
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @author Michael Backhaus <michael.backhaus@agfa.com>
 * @author Nicolas Roduit
 */
public class StoreSCU implements AutoCloseable {
  private static final Logger LOG = LoggerFactory.getLogger(StoreSCU.class);
  private static final String DEFAULT_TMP_PREFIX = "storescu-";

  /** Factory interface for creating custom DICOM response handlers. */
  @FunctionalInterface
  public interface RSPHandlerFactory {

    /**
     * Creates a response handler for the specified file.
     *
     * @param file the file being processed
     * @return the response handler
     */
    DimseRSPHandler createDimseRSPHandler(Path file);
  }

  private final ApplicationEntity ae;
  private final Connection remote;
  private final AAssociateRQ rq = new AAssociateRQ();
  public final RelatedGeneralSOPClasses relSOPClasses = new RelatedGeneralSOPClasses();
  private final List<AttributeEditor> dicomEditors;
  private final DicomState state;
  private Attributes attrs;
  private String uidSuffix;
  private boolean relExtNeg;
  private int priority;
  private String tmpPrefix = DEFAULT_TMP_PREFIX;
  private String tmpSuffix;
  private Path tmpDir;
  private Path tmpFile;
  private Association as;
  private long totalSize = 0;
  private int filesScanned;

  private RSPHandlerFactory rspHandlerFactory = this::createDefaultRspHandler;

  /**
   * Creates a StoreSCU with specified application entity and progress tracking.
   *
   * @param ae the application entity
   * @param progress the progress tracker, may be null
   */
  public StoreSCU(ApplicationEntity ae, DicomProgress progress) {
    this(ae, progress, null);
  }

  /**
   * Creates a StoreSCU with full configuration options.
   *
   * @param ae the application entity
   * @param progress the progress tracker, may be null
   * @param dicomEditors list of attribute editors to apply, may be null
   */
  public StoreSCU(
      ApplicationEntity ae, DicomProgress progress, List<AttributeEditor> dicomEditors) {
    this.remote = new Connection();
    this.ae = Objects.requireNonNull(ae, "ApplicationEntity cannot be null");
    this.state = new DicomState(progress);
    this.dicomEditors = dicomEditors;
    try {
      this.tmpDir = Files.createTempDirectory(DEFAULT_TMP_PREFIX);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }

    initializeAssociationRequest();
  }

  private void initializeAssociationRequest() {
    rq.addPresentationContext(
        new PresentationContext(1, UID.Verification, UID.ImplicitVRLittleEndian));
  }

  private DimseRSPHandler createDefaultRspHandler(Path file) {
    return new DimseRSPHandler(as.nextMessageID()) {
      @Override
      public void onDimseRSP(Association as, Attributes cmd, Attributes data) {
        super.onDimseRSP(as, cmd, data);
        StoreSCU.this.onCStoreRSP(cmd, file);

        var progress = state.getProgress();
        if (progress != null) {
          progress.setProcessedFile(file);
          progress.setAttributes(cmd);
        }
      }
    };
  }

  // Getters and setters

  public void setRspHandlerFactory(RSPHandlerFactory rspHandlerFactory) {
    this.rspHandlerFactory =
        rspHandlerFactory != null ? rspHandlerFactory : this::createDefaultRspHandler;
  }

  public AAssociateRQ getAAssociateRQ() {
    return rq;
  }

  public Connection getRemoteConnection() {
    return remote;
  }

  public Attributes getAttributes() {
    return attrs;
  }

  public void setAttributes(Attributes attrs) {
    this.attrs = attrs;
  }

  public void setTmpFile(Path tmpFile) {
    this.tmpFile = tmpFile;
  }

  public final void setPriority(int priority) {
    this.priority = priority;
  }

  public final void setUIDSuffix(String uidSuffix) {
    this.uidSuffix = uidSuffix;
  }

  public final void setTmpFilePrefix(String prefix) {
    this.tmpPrefix = prefix != null ? prefix : DEFAULT_TMP_PREFIX;
  }

  public final void setTmpFileSuffix(String suffix) {
    this.tmpSuffix = suffix;
  }

  public final void setTmpFileDirectory(Path tmpDir) {
    if (tmpDir != null && !Files.isDirectory(tmpDir)) {
      throw new IllegalArgumentException("Not a directory: " + tmpDir);
    }
    this.tmpDir = tmpDir;
  }

  public final void enableSOPClassRelationshipExtNeg(boolean enable) {
    relExtNeg = enable;
  }

  /** Scans files with printout enabled. */
  public void scanFiles(List<String> fnames) throws IOException {
    scanFiles(fnames, true);
  }

  /**
   * Scans the specified files/directories for DICOM objects.
   *
   * @param fnames list of file or directory paths to scan
   * @param printout whether to print progress indicators
   * @throws IOException if file operations fail
   */
  public void scanFiles(List<String> fnames, boolean printout) throws IOException {
    tmpFile = createTempFile();

    try (var writer = Files.newBufferedWriter(tmpFile, StandardCharsets.UTF_8)) {
      for (String fname : fnames) {
        scanPath(Paths.get(fname), writer, printout);
      }
    }
  }

  private Path createTempFile() throws IOException {
    return Files.createTempFile(tmpDir, tmpPrefix, tmpSuffix);
  }

  private void scanPath(Path path, Writer writer, boolean printout) {
    if (Files.isDirectory(path) && Files.isReadable(path)) {
      scanDirectory(path, writer, printout);
    } else {
      scanFile(path, writer, printout);
    }
  }

  private void scanDirectory(Path dir, Writer writer, boolean printout) {
    try (var stream = Files.newDirectoryStream(dir)) {
      for (Path entry : stream) {
        scanPath(entry, writer, printout);
      }
    } catch (IOException e) {
      LOG.error("Failed to scan directory {}", dir, e);
    }
  }

  private void scanFile(Path file, Writer writer, boolean printout) {
    try (var in = new DicomInputStream(Files.newInputStream(file))) {
      in.setIncludeBulkData(IncludeBulkData.NO);
      var fmi = in.readFileMetaInformation();
      long dsPos = in.getPosition();
      if (isInvalidFileMetaInformation(fmi)) {
        var ds = in.readDataset(Tag.SOPInstanceUID + 1);
        fmi = ds.createFileMetaInformation(in.getTransferSyntax());
      }

      boolean success = addFile(writer, file, dsPos, fmi);
      if (success) {
        filesScanned++;
      }
      printProgress(printout, success);

    } catch (Exception e) {
      handleScanError(file, e);
    }
  }

  private boolean isInvalidFileMetaInformation(Attributes fmi) {
    return fmi == null
        || !fmi.containsValue(Tag.TransferSyntaxUID)
        || !fmi.containsValue(Tag.MediaStorageSOPClassUID)
        || !fmi.containsValue(Tag.MediaStorageSOPInstanceUID);
  }

  private void printProgress(boolean printout, boolean success) {
    if (printout) {
      System.out.print(success ? '.' : 'I');
    }
  }

  private void handleScanError(Path file, Exception e) {
    System.out.println();
    System.out.println("Failed to scan file " + file + ": " + e.getMessage());
    LOG.error("Failed to scan file {}", file, e);
  }

  /**
   * Sends all scanned files to the remote DICOM node.
   *
   * @throws IOException if file operations or network communication fails
   */
  public void sendFiles() throws IOException {
    try (var reader = Files.newBufferedReader(tmpFile, StandardCharsets.UTF_8)) {
      String line;
      while (as.isReadyForDataTransfer() && (line = reader.readLine()) != null) {
        if (isProgressCancelled()) {
          LOG.info("Aborting C-Store: cancel by progress");
          as.abort();
          break;
        }
        var fileInfo = StringUtils.split(line, '\t');
        sendFileFromInfo(fileInfo);
      }

      waitForOutstandingResponses();
    }
  }

  private boolean isProgressCancelled() {
    var progress = state.getProgress();
    return progress != null && progress.isCancelled();
  }

  private void sendFileFromInfo(String[] fileInfo) {
    try {
      var file = Path.of(fileInfo[4]);
      var dsPos = Long.parseLong(fileInfo[3]);
      var cuid = fileInfo[1];
      var iuid = fileInfo[0];
      var tsuid = fileInfo[2];

      send(file, dsPos, cuid, iuid, tsuid);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      LOG.warn("File send interrupted", e);
    } catch (Exception e) {
      LOG.error("Cannot send file", e);
    }
  }

  private void waitForOutstandingResponses() {
    try {
      as.waitForOutstandingRSP();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      LOG.error("Waiting for RSP interrupted", e);
    }
  }

  /** Adds a file to the association request and temporary file list. */
  public boolean addFile(Writer writer, Path path, long endFmi, Attributes fmi) throws IOException {
    var cuid = fmi.getString(Tag.MediaStorageSOPClassUID);
    var iuid = fmi.getString(Tag.MediaStorageSOPInstanceUID);
    var tsuid = fmi.getString(Tag.TransferSyntaxUID);
    if (cuid == null || iuid == null) {
      return false;
    }

    writeFileInfo(writer, path, endFmi, cuid, iuid, tsuid);
    addPresentationContextsIfNeeded(cuid, tsuid);

    return true;
  }

  private void writeFileInfo(
      Writer writer, Path path, long endFmi, String cuid, String iuid, String tsuid)
      throws IOException {
    writer.write(String.join("\t", iuid, cuid, tsuid, Long.toString(endFmi), path.toString()));
    writer.write('\n');
  }

  private void addPresentationContextsIfNeeded(String cuid, String tsuid) {
    if (rq.containsPresentationContextFor(cuid, tsuid)) {
      return;
    }
    if (!rq.containsPresentationContextFor(cuid)) {
      addRelatedExtendedNegotiation(cuid);
      addStandardTransferSyntaxes(cuid, tsuid);
    }

    addPresentationContext(cuid, tsuid);
  }

  private void addRelatedExtendedNegotiation(String cuid) {
    if (relExtNeg) {
      rq.addCommonExtendedNegotiation(relSOPClasses.getCommonExtendedNegotiation(cuid));
    }
  }

  private void addStandardTransferSyntaxes(String cuid, String currentTsuid) {
    if (!currentTsuid.equals(UID.ExplicitVRLittleEndian)) {
      addPresentationContext(cuid, UID.ExplicitVRLittleEndian);
    }
    if (!currentTsuid.equals(UID.ImplicitVRLittleEndian)) {
      addPresentationContext(cuid, UID.ImplicitVRLittleEndian);
    }
  }

  private void addPresentationContext(String cuid, String tsuid) {
    var pc = new PresentationContext(rq.getNumberOfPresentationContexts() * 2 + 1, cuid, tsuid);
    rq.addPresentationContext(pc);
  }

  /** Performs a DICOM echo operation. */
  public Attributes echo() throws IOException, InterruptedException {
    var response = as.cecho();
    response.next();
    return response.getCommand();
  }

  /** Sends a single DICOM file to the remote node. */
  public void send(final Path path, long fmiEndPos, String cuid, String iuid, String tsuid)
      throws IOException, InterruptedException, ParserConfigurationException, SAXException {
    var syntax =
        new AdaptTransferSyntax(tsuid, StoreFromStreamSCU.selectTransferSyntax(as, cuid, tsuid));
    var isNoChange = shouldSkipProcessing(syntax, tsuid);

    DataWriter dataWriter = null;
    Attributes data = null;
    try (InputStream in = createInputStream(path, fmiEndPos, isNoChange)) {
      if (path.getFileName().toString().endsWith(".xml")) {
        data = SAXReader.parse(in);
        isNoChange = false;
      } else if (isNoChange) {
        dataWriter = new InputStreamDataWriter(in);
      } else {
        data = readDicomDataset((DicomInputStream) in);
      }

      if (!isNoChange) {
        data = processAttributes(data, syntax);
        var updatedIds = updateInstanceIdentifiers(data);
        cuid = updatedIds[0];
        iuid = updatedIds[1];

        var desc = ImageAdapter.imageTranscode(data, syntax, createEditorContext(syntax));
        dataWriter =
            ImageAdapter.buildDataWriter(
                data, syntax, createEditorContext(syntax).getEditable(), desc);
      }

      as.cstore(
          cuid,
          iuid,
          priority,
          dataWriter,
          syntax.getSuitable(),
          rspHandlerFactory.createDimseRSPHandler(path));
    }
  }

  private boolean shouldSkipProcessing(AdaptTransferSyntax syntax, String tsuid) {
    return uidSuffix == null
        && (attrs == null || attrs.isEmpty())
        && syntax.getRequested().equals(tsuid)
        && (dicomEditors == null || dicomEditors.isEmpty());
  }

  private InputStream createInputStream(Path path, long fmiEndPos, boolean isNoChange)
      throws IOException {
    InputStream in;
    if (path.getFileName().toString().endsWith(".xml") || !isNoChange) {
      in = new DicomInputStream(Files.newInputStream(path));
      if (!path.getFileName().toString().endsWith(".xml")) {
        ((DicomInputStream) in).setIncludeBulkData(IncludeBulkData.URI);
      }
    } else {
      in = Files.newInputStream(path);
      in.skip(fmiEndPos);
    }
    return in;
  }

  private Attributes readDicomDataset(DicomInputStream in) throws IOException {
    return in.readDataset();
  }

  private Attributes processAttributes(Attributes data, AdaptTransferSyntax syntax) {
    var context = createEditorContext(syntax);

    if (dicomEditors != null && !dicomEditors.isEmpty()) {
      dicomEditors.forEach(editor -> editor.apply(data, context));
    }

    if (attrs != null && !attrs.isEmpty()) {
      CLIUtils.updateAttributes(data, attrs, uidSuffix);
    }

    return data;
  }

  private AttributeEditorContext createEditorContext(AdaptTransferSyntax syntax) {
    return new AttributeEditorContext(
        syntax.getOriginal(),
        DicomNode.buildLocalDicomNode(as),
        DicomNode.buildRemoteDicomNode(as));
  }

  private String[] updateInstanceIdentifiers(Attributes data) {
    return new String[] {data.getString(Tag.SOPClassUID), data.getString(Tag.SOPInstanceUID)};
  }

  @Override
  public void close() throws IOException, InterruptedException {
    if (as != null) {
      if (as.isReadyForDataTransfer()) {
        as.release();
      }
      as.waitForSocketClose();
    }
  }

  /** Opens the association to the remote DICOM node. */
  public void open()
      throws IOException,
          InterruptedException,
          IncompatibleConnectionException,
          GeneralSecurityException {
    as = ae.connect(remote, rq);
  }

  private void onCStoreRSP(Attributes cmd, Path file) {
    int status = cmd.getInt(Tag.Status, -1);
    state.setStatus(status);
    var progressStatus = determineProgressStatus(status, file, cmd);
    updateTotalSize(status, file);

    ServiceUtil.notifyProgression(state.getProgress(), cmd, progressStatus, filesScanned);
  }

  private ProgressStatus determineProgressStatus(int status, Path file, Attributes cmd) {
    return switch (status) {
      case Status.Success -> ProgressStatus.COMPLETED;
      case Status.CoercionOfDataElements,
          Status.ElementsDiscarded,
          Status.DataSetDoesNotMatchSOPClassWarning -> {
        logWarning(status, file, cmd);
        yield ProgressStatus.WARNING;
      }
      default -> {
        logError(status, file, cmd);
        yield ProgressStatus.FAILED;
      }
    };
  }

  private void updateTotalSize(int status, Path file) {
    if (status == Status.Success
        || status == Status.CoercionOfDataElements
        || status == Status.ElementsDiscarded
        || status == Status.DataSetDoesNotMatchSOPClassWarning) {
      try {
        totalSize += Files.size(file);
      } catch (IOException e) {
        LOG.warn("Cannot get file size for {}", file, e);
      }
    }
  }

  private void logWarning(int status, Path file, Attributes cmd) {
    System.err.println(
        MessageFormat.format(
            "WARNING: Received C-STORE-RSP with Status {0}H for {1}",
            TagUtils.shortToHexString(status), file));
    System.err.println(cmd);
  }

  private void logError(int status, Path file, Attributes cmd) {
    System.err.println(
        MessageFormat.format(
            "ERROR: Received C-STORE-RSP with Status {0}H for {1}",
            TagUtils.shortToHexString(status), file));
    System.err.println(cmd);
  }

  // Public getters

  public ApplicationEntity getApplicationEntity() {
    return ae;
  }

  public int getFilesScanned() {
    return filesScanned;
  }

  public long getTotalSize() {
    return totalSize;
  }

  public DicomState getState() {
    return state;
  }
}
