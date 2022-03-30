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

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.security.GeneralSecurityException;
import java.text.MessageFormat;
import java.util.List;
import javax.xml.parsers.ParserConfigurationException;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.UID;
import org.dcm4che3.img.stream.BytesWithImageDescriptor;
import org.dcm4che3.img.stream.ImageAdapter;
import org.dcm4che3.img.stream.ImageAdapter.AdaptTransferSyntax;
import org.dcm4che3.io.DicomInputStream;
import org.dcm4che3.io.DicomInputStream.IncludeBulkData;
import org.dcm4che3.io.SAXReader;
import org.dcm4che3.net.ApplicationEntity;
import org.dcm4che3.net.Association;
import org.dcm4che3.net.Connection;
import org.dcm4che3.net.DataWriter;
import org.dcm4che3.net.DimseRSP;
import org.dcm4che3.net.DimseRSPHandler;
import org.dcm4che3.net.IncompatibleConnectionException;
import org.dcm4che3.net.InputStreamDataWriter;
import org.dcm4che3.net.Status;
import org.dcm4che3.net.pdu.AAssociateRQ;
import org.dcm4che3.net.pdu.PresentationContext;
import org.dcm4che3.tool.common.CLIUtils;
import org.dcm4che3.tool.common.DicomFiles;
import org.dcm4che3.util.SafeClose;
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
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @author Michael Backhaus <michael.backhaus@agfa.com>
 */
public class StoreSCU implements AutoCloseable {
  private static final Logger LOG = LoggerFactory.getLogger(StoreSCU.class);

  public interface RSPHandlerFactory {

    DimseRSPHandler createDimseRSPHandler(File f);
  }

  private final ApplicationEntity ae;
  private final Connection remote;
  private final AAssociateRQ rq = new AAssociateRQ();
  public final RelatedGeneralSOPClasses relSOPClasses = new RelatedGeneralSOPClasses();
  private Attributes attrs;
  private String uidSuffix;
  private boolean relExtNeg;
  private int priority;
  private String tmpPrefix = "storescu-";
  private String tmpSuffix;
  private File tmpDir;
  private File tmpFile;
  private Association as;
  private long totalSize = 0;
  private int filesScanned;

  private final List<AttributeEditor> dicomEditors;
  private final DicomState state;

  private RSPHandlerFactory rspHandlerFactory =
      file ->
          new DimseRSPHandler(as.nextMessageID()) {

            @Override
            public void onDimseRSP(Association as, Attributes cmd, Attributes data) {
              super.onDimseRSP(as, cmd, data);
              StoreSCU.this.onCStoreRSP(cmd, file);

              DicomProgress progress = state.getProgress();
              if (progress != null) {
                progress.setProcessedFile(file);
                progress.setAttributes(cmd);
              }
            }
          };

  public StoreSCU(ApplicationEntity ae, DicomProgress progress) throws IOException {
    this(ae, progress, null);
  }

  public StoreSCU(ApplicationEntity ae, DicomProgress progress, List<AttributeEditor> dicomEditors)
      throws IOException {
    this.remote = new Connection();
    this.ae = ae;
    rq.addPresentationContext(
        new PresentationContext(1, UID.Verification, UID.ImplicitVRLittleEndian));
    this.state = new DicomState(progress);
    this.dicomEditors = dicomEditors;
  }

  public void setRspHandlerFactory(RSPHandlerFactory rspHandlerFactory) {
    this.rspHandlerFactory = rspHandlerFactory;
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

  public void setTmpFile(File tmpFile) {
    this.tmpFile = tmpFile;
  }

  public final void setPriority(int priority) {
    this.priority = priority;
  }

  public final void setUIDSuffix(String uidSuffix) {
    this.uidSuffix = uidSuffix;
  }

  public final void setTmpFilePrefix(String prefix) {
    this.tmpPrefix = prefix;
  }

  public final void setTmpFileSuffix(String suffix) {
    this.tmpSuffix = suffix;
  }

  public final void setTmpFileDirectory(File tmpDir) {
    this.tmpDir = tmpDir;
  }

  public final void enableSOPClassRelationshipExtNeg(boolean enable) {
    relExtNeg = enable;
  }

  public void scanFiles(List<String> fnames) throws IOException {
    this.scanFiles(fnames, true);
  }

  public void scanFiles(List<String> fnames, boolean printout) throws IOException {
    tmpFile = File.createTempFile(tmpPrefix, tmpSuffix, tmpDir);
    tmpFile.deleteOnExit();
    try (BufferedWriter fileInfos =
        new BufferedWriter(new OutputStreamWriter(new FileOutputStream(tmpFile)))) {
      DicomFiles.scan(
          fnames,
          printout,
          (f, fmi, dsPos, ds) -> {
            if (!addFile(fileInfos, f, dsPos, fmi, ds)) {
              return false;
            }

            filesScanned++;
            return true;
          });
    }
  }

  public void sendFiles() throws IOException {
    BufferedReader fileInfos =
        new BufferedReader(new InputStreamReader(new FileInputStream(tmpFile)));
    try {
      String line;
      while (as.isReadyForDataTransfer() && (line = fileInfos.readLine()) != null) {
        DicomProgress p = state.getProgress();
        if (p != null) {
          if (p.isCancel()) {
            LOG.info("Aborting C-Store: {}", "cancel by progress");
            as.abort();
            break;
          }
        }
        String[] ss = StringUtils.split(line, '\t');
        try {
          send(new File(ss[4]), Long.parseLong(ss[3]), ss[1], ss[0], ss[2]);
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
        } catch (Exception e) {
          LOG.error("Cannot send file", e);
        }
      }
      try {
        as.waitForOutstandingRSP();
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        LOG.error("Waiting for RSP", e);
      }
    } finally {
      SafeClose.close(fileInfos);
    }
  }

  public boolean addFile(
      BufferedWriter fileInfos, File f, long endFmi, Attributes fmi, Attributes ds)
      throws IOException {
    String cuid = fmi.getString(Tag.MediaStorageSOPClassUID);
    String iuid = fmi.getString(Tag.MediaStorageSOPInstanceUID);
    String ts = fmi.getString(Tag.TransferSyntaxUID);
    if (cuid == null || iuid == null) {
      return false;
    }

    fileInfos.write(iuid);
    fileInfos.write('\t');
    fileInfos.write(cuid);
    fileInfos.write('\t');
    fileInfos.write(ts);
    fileInfos.write('\t');
    fileInfos.write(Long.toString(endFmi));
    fileInfos.write('\t');
    fileInfos.write(f.getPath());
    fileInfos.newLine();

    if (rq.containsPresentationContextFor(cuid, ts)) {
      return true;
    }

    if (!rq.containsPresentationContextFor(cuid)) {
      if (relExtNeg) {
        rq.addCommonExtendedNegotiation(relSOPClasses.getCommonExtendedNegotiation(cuid));
      }
      if (!ts.equals(UID.ExplicitVRLittleEndian)) {
        rq.addPresentationContext(
            new PresentationContext(
                rq.getNumberOfPresentationContexts() * 2 + 1, cuid, UID.ExplicitVRLittleEndian));
      }
      if (!ts.equals(UID.ImplicitVRLittleEndian)) {
        rq.addPresentationContext(
            new PresentationContext(
                rq.getNumberOfPresentationContexts() * 2 + 1, cuid, UID.ImplicitVRLittleEndian));
      }
    }
    rq.addPresentationContext(
        new PresentationContext(rq.getNumberOfPresentationContexts() * 2 + 1, cuid, ts));
    return true;
  }

  public Attributes echo() throws IOException, InterruptedException {
    DimseRSP response = as.cecho();
    response.next();
    return response.getCommand();
  }

  public void send(final File f, long fmiEndPos, String cuid, String iuid, String tsuid)
      throws IOException, InterruptedException, ParserConfigurationException, SAXException {
    AdaptTransferSyntax syntax =
        new AdaptTransferSyntax(tsuid, StoreFromStreamSCU.selectTransferSyntax(as, cuid, tsuid));
    boolean noChange =
        uidSuffix == null
            && attrs.isEmpty()
            && syntax.getRequested().equals(tsuid)
            && dicomEditors == null;
    DataWriter dataWriter = null;
    InputStream in = null;
    Attributes data = null;
    try {
      if (f.getName().endsWith(".xml")) {
        in = new FileInputStream(f);
        data = SAXReader.parse(in);
        noChange = false;
      } else if (noChange) {
        in = new FileInputStream(f);
        in.skip(fmiEndPos);
        dataWriter = new InputStreamDataWriter(in);
      } else {
        in = new DicomInputStream(f);
        ((DicomInputStream) in).setIncludeBulkData(IncludeBulkData.URI);
        data = ((DicomInputStream) in).readDataset();
      }

      if (!noChange) {
        AttributeEditorContext context =
            new AttributeEditorContext(
                syntax.getOriginal(),
                DicomNode.buildLocalDicomNode(as),
                DicomNode.buildRemoteDicomNode(as));
        if (dicomEditors != null && !dicomEditors.isEmpty()) {
          final Attributes attributes = data;
          dicomEditors.forEach(e -> e.apply(attributes, context));
          iuid = data.getString(Tag.SOPInstanceUID);
          cuid = data.getString(Tag.SOPClassUID);
        }
        if (CLIUtils.updateAttributes(data, attrs, uidSuffix)) {
          iuid = data.getString(Tag.SOPInstanceUID);
        }

        BytesWithImageDescriptor desc = ImageAdapter.imageTranscode(data, syntax, context);
        dataWriter = ImageAdapter.buildDataWriter(data, syntax, context.getEditable(), desc);
      }
      as.cstore(
          cuid,
          iuid,
          priority,
          dataWriter,
          syntax.getSuitable(),
          rspHandlerFactory.createDimseRSPHandler(f));
    } finally {
      SafeClose.close(in);
    }
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

  public void open()
      throws IOException, InterruptedException, IncompatibleConnectionException,
          GeneralSecurityException {
    as = ae.connect(remote, rq);
  }

  private void onCStoreRSP(Attributes cmd, File f) {
    int status = cmd.getInt(Tag.Status, -1);
    state.setStatus(status);
    ProgressStatus ps;

    switch (status) {
      case Status.Success:
        totalSize += f.length();
        ps = ProgressStatus.COMPLETED;
        break;
      case Status.CoercionOfDataElements:
      case Status.ElementsDiscarded:
      case Status.DataSetDoesNotMatchSOPClassWarning:
        totalSize += f.length();
        ps = ProgressStatus.WARNING;
        System.err.println(
            MessageFormat.format(
                "WARNING: Received C-STORE-RSP with Status {0}H for {1}",
                TagUtils.shortToHexString(status), f));
        System.err.println(cmd);
        break;
      default:
        ps = ProgressStatus.FAILED;
        System.err.println(
            MessageFormat.format(
                "ERROR: Received C-STORE-RSP with Status {0}H for {1}",
                TagUtils.shortToHexString(status), f));
        System.err.println(cmd);
    }
    ServiceUtil.notifyProgession(state.getProgress(), cmd, ps, filesScanned);
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
