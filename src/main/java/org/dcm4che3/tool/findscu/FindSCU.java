/*
 * Copyright (c) 2014-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.dcm4che3.tool.findscu;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.security.GeneralSecurityException;
import java.text.DecimalFormat;
import java.util.EnumSet;
import java.util.concurrent.atomic.AtomicInteger;
import javax.xml.XMLConstants;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Templates;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.sax.SAXTransformerFactory;
import javax.xml.transform.sax.TransformerHandler;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Sequence;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.UID;
import org.dcm4che3.data.VR;
import org.dcm4che3.io.DicomInputStream;
import org.dcm4che3.io.DicomOutputStream;
import org.dcm4che3.io.SAXReader;
import org.dcm4che3.io.SAXWriter;
import org.dcm4che3.net.ApplicationEntity;
import org.dcm4che3.net.Association;
import org.dcm4che3.net.Connection;
import org.dcm4che3.net.Device;
import org.dcm4che3.net.DimseRSPHandler;
import org.dcm4che3.net.IncompatibleConnectionException;
import org.dcm4che3.net.QueryOption;
import org.dcm4che3.net.Status;
import org.dcm4che3.net.pdu.AAssociateRQ;
import org.dcm4che3.net.pdu.ExtendedNegotiation;
import org.dcm4che3.net.pdu.PresentationContext;
import org.dcm4che3.util.SafeClose;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.dicom.param.DicomProgress;
import org.weasis.dicom.param.DicomState;

/**
 * The findscu application implements a Service Class User (SCU) for the Query/Retrieve, the
 * Modality Worklist Management, the Unified Worklist and Procedure Step, the Hanging Protocol
 * Query/Retrieve and the Color Palette Query/Retrieve Service Class. findscu only supports query
 * functionality using the C-FIND message. It sends query keys to an Service Class Provider (SCP)
 * and waits for responses.
 *
 * @author Gunter Zeilinger <gunterze@gmail.com>
 */
public class FindSCU implements AutoCloseable {
  private static final Logger LOGGER = LoggerFactory.getLogger(FindSCU.class);

  public enum InformationModel {
    PatientRoot(UID.PatientRootQueryRetrieveInformationModelFind, "STUDY"),
    StudyRoot(UID.StudyRootQueryRetrieveInformationModelFind, "STUDY"),
    PatientStudyOnly(UID.PatientStudyOnlyQueryRetrieveInformationModelFind, "STUDY"),
    MWL(UID.ModalityWorklistInformationModelFind, null),
    UPSPull(UID.UnifiedProcedureStepPull, null),
    UPSWatch(UID.UnifiedProcedureStepWatch, null),
    UPSQuery(UID.UnifiedProcedureStepQuery, null),
    HangingProtocol(UID.HangingProtocolInformationModelFind, null),
    ColorPalette(UID.ColorPaletteQueryRetrieveInformationModelFind, null);

    final String cuid;
    final String level;

    InformationModel(String cuid, String level) {
      this.cuid = cuid;
      this.level = level;
    }

    public void adjustQueryOptions(EnumSet<QueryOption> queryOptions) {
      if (level == null) {
        queryOptions.add(QueryOption.RELATIONAL);
        queryOptions.add(QueryOption.DATETIME);
      }
    }

    public String getCuid() {
      return cuid;
    }
  }

  private SAXTransformerFactory saxtf;

  private final Device device = new Device("findscu");
  private final ApplicationEntity ae = new ApplicationEntity("FINDSCU");
  private final Connection conn = new Connection();
  private final Connection remote = new Connection();
  private final AAssociateRQ rq = new AAssociateRQ();
  private int priority;
  private int cancelAfter;
  private InformationModel model;

  private File outDir;
  private DecimalFormat outFileFormat;
  private int[] inFilter;
  private final Attributes keys = new Attributes();

  private boolean catOut = false;
  private boolean xml = false;
  private boolean xmlIndent = false;
  private boolean xmlIncludeKeyword = true;
  private boolean xmlIncludeNamespaceDeclaration = false;
  private File xsltFile;
  private Templates xsltTpls;
  private OutputStream out;

  private Association as;
  private final AtomicInteger totNumMatches = new AtomicInteger();

  private final DicomState state;

  public FindSCU() throws IOException {
    device.addConnection(conn);
    device.addApplicationEntity(ae);
    ae.addConnection(conn);
    state = new DicomState(new DicomProgress());
  }

  public final void setPriority(int priority) {
    this.priority = priority;
  }

  public final void setInformationModel(
      InformationModel model, String[] tss, EnumSet<QueryOption> queryOptions) {
    this.model = model;
    rq.addPresentationContext(new PresentationContext(1, model.cuid, tss));
    if (!queryOptions.isEmpty()) {
      model.adjustQueryOptions(queryOptions);
      rq.addExtendedNegotiation(
          new ExtendedNegotiation(
              model.cuid, QueryOption.toExtendedNegotiationInformation(queryOptions)));
    }
    if (model.level != null) {
      addLevel(model.level);
    }
  }

  public void addLevel(String s) {
    keys.setString(Tag.QueryRetrieveLevel, VR.CS, s);
  }

  public final void setCancelAfter(int cancelAfter) {
    this.cancelAfter = cancelAfter;
  }

  public final void setOutputDirectory(File outDir) {
    outDir.mkdirs();
    this.outDir = outDir;
  }

  public final void setOutputFileFormat(String outFileFormat) {
    this.outFileFormat = new DecimalFormat(outFileFormat);
  }

  public final void setXSLT(File xsltFile) {
    this.xsltFile = xsltFile;
  }

  public final void setXML(boolean xml) {
    this.xml = xml;
  }

  public final void setXMLIndent(boolean indent) {
    this.xmlIndent = indent;
  }

  public final void setXMLIncludeKeyword(boolean includeKeyword) {
    this.xmlIncludeKeyword = includeKeyword;
  }

  public final void setXMLIncludeNamespaceDeclaration(boolean includeNamespaceDeclaration) {
    this.xmlIncludeNamespaceDeclaration = includeNamespaceDeclaration;
  }

  public final void setConcatenateOutputFiles(boolean catOut) {
    this.catOut = catOut;
  }

  public final void setInputFilter(int[] inFilter) {
    this.inFilter = inFilter;
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
    SafeClose.close(out);
    out = null;
  }

  public void query(File f) throws Exception {
    Attributes attrs;
    String filePath = f.getPath();
    String fileExt = filePath.substring(filePath.lastIndexOf('.') + 1).toLowerCase();

    if (fileExt.equals("xml")) {
      attrs = SAXReader.parse(filePath);
    } else {
      try (DicomInputStream dis = new DicomInputStream(f)) {
        attrs = dis.readDataset(-1, -1);
      }
    }
    if (inFilter != null) {
      attrs = new Attributes(inFilter.length + 1);
      attrs.addSelected(attrs, inFilter);
    }
    mergeKeys(attrs, keys);
    query(attrs);
  }

  private static class MergeNested implements Attributes.Visitor {
    private final Attributes keys;

    MergeNested(Attributes keys) {
      this.keys = keys;
    }

    @Override
    public boolean visit(Attributes attrs, int tag, VR vr, Object val) {
      if (isNotEmptySequence(val)) {
        Object o = keys.remove(tag);
        if (isNotEmptySequence(o)) ((Sequence) val).get(0).addAll(((Sequence) o).get(0));
      }
      return true;
    }

    private static boolean isNotEmptySequence(Object val) {
      return val instanceof Sequence && !((Sequence) val).isEmpty();
    }
  }

  static void mergeKeys(Attributes attrs, Attributes keys) {
    try {
      attrs.accept(new MergeNested(keys), false);
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
    attrs.addAll(keys);
  }

  public void query() throws IOException, InterruptedException {
    query(keys);
  }

  private void query(Attributes keys) throws IOException, InterruptedException {
    DimseRSPHandler rspHandler =
        new DimseRSPHandler(as.nextMessageID()) {

          int cancelAfter = FindSCU.this.cancelAfter;
          int numMatches;

          @Override
          public void onDimseRSP(Association as, Attributes cmd, Attributes data) {
            super.onDimseRSP(as, cmd, data);
            int status = cmd.getInt(Tag.Status, -1);
            if (Status.isPending(status)) {
              FindSCU.this.onResult(data);
              ++numMatches;
              if (cancelAfter != 0 && numMatches >= cancelAfter) {
                try {
                  cancel(as);
                  cancelAfter = 0;
                } catch (IOException e) {
                  LOGGER.error("Building response", e);
                }
              }
            } else {
              state.setStatus(status);
            }
          }
        };

    query(keys, rspHandler);
  }

  public void query(DimseRSPHandler rspHandler) throws IOException, InterruptedException {
    query(keys, rspHandler);
  }

  private void query(Attributes keys, DimseRSPHandler rspHandler)
      throws IOException, InterruptedException {
    as.cfind(model.cuid, priority, keys, null, rspHandler);
  }

  private void onResult(Attributes data) {
    state.addDicomRSP(data);
    int numMatches = totNumMatches.incrementAndGet();
    if (outDir == null) {
      return;
    }

    try {
      if (out == null) {
        File f = new File(outDir, fname(numMatches));
        out = new BufferedOutputStream(new FileOutputStream(f));
      }
      if (xml) {
        writeAsXML(data, out);
      } else {
        // Do not close DicomOutputStream until catOut is false. Only "out" needs to be closed
        DicomOutputStream dos = new DicomOutputStream(out, UID.ImplicitVRLittleEndian); // NOSONAR
        dos.writeDataset(null, data);
      }
      out.flush();
    } catch (Exception e) {
      LOGGER.error("Building response", e);
      SafeClose.close(out);
      out = null;
    } finally {
      if (!catOut) {
        SafeClose.close(out);
        out = null;
      }
    }
  }

  private String fname(int i) {
    synchronized (outFileFormat) {
      return outFileFormat.format(i);
    }
  }

  private void writeAsXML(Attributes attrs, OutputStream out) throws Exception {
    TransformerHandler th = getTransformerHandler();
    th.getTransformer().setOutputProperty(OutputKeys.INDENT, xmlIndent ? "yes" : "no");
    th.setResult(new StreamResult(out));
    SAXWriter saxWriter = new SAXWriter(th);
    saxWriter.setIncludeKeyword(xmlIncludeKeyword);
    saxWriter.setIncludeNamespaceDeclaration(xmlIncludeNamespaceDeclaration);
    saxWriter.write(attrs);
  }

  private TransformerHandler getTransformerHandler() throws Exception {
    SAXTransformerFactory tf = saxtf;
    if (tf == null) {
      saxtf = tf = (SAXTransformerFactory) TransformerFactory.newInstance();
      tf.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
    }
    if (xsltFile == null) {
      return tf.newTransformerHandler();
    }

    Templates tpls = xsltTpls;
    if (tpls == null) {
      xsltTpls = tpls = tf.newTemplates(new StreamSource(xsltFile));
    }

    return tf.newTransformerHandler(tpls);
  }

  public Connection getConnection() {
    return conn;
  }

  public DicomState getState() {
    return state;
  }
}
