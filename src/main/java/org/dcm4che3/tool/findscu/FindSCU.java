/* ***** BEGIN LICENSE BLOCK *****
 * Version: MPL 1.1/GPL 2.0/LGPL 2.1
 *
 * The contents of this file are subject to the Mozilla Public License Version
 * 1.1 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 * http://www.mozilla.org/MPL/
 *
 * Software distributed under the License is distributed on an "AS IS" basis,
 * WITHOUT WARRANTY OF ANY KIND, either express or implied. See the License
 * for the specific language governing rights and limitations under the
 * License.
 *
 * The Original Code is part of dcm4che, an implementation of DICOM(TM) in
 * Java(TM), hosted at https://github.com/gunterze/dcm4che.
 *
 * The Initial Developer of the Original Code is
 * Agfa Healthcare.
 * Portions created by the Initial Developer are Copyright (C) 2011
 * the Initial Developer. All Rights Reserved.
 *
 * Contributor(s):
 * See @authors listed below
 *
 * Alternatively, the contents of this file may be used under the terms of
 * either the GNU General Public License Version 2 or later (the "GPL"), or
 * the GNU Lesser General Public License Version 2.1 or later (the "LGPL"),
 * in which case the provisions of the GPL or the LGPL are applicable instead
 * of those above. If you wish to allow use of your version of this file only
 * under the terms of either the GPL or the LGPL, and not to allow others to
 * use your version of this file under the terms of the MPL, indicate your
 * decision by deleting the provisions above and replace them with the notice
 * and other provisions required by the GPL or the LGPL. If you do not delete
 * the provisions above, a recipient may use your version of this file under
 * the terms of any one of the MPL, the GPL or the LGPL.
 *
 * ***** END LICENSE BLOCK ***** */

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

import javax.xml.transform.OutputKeys;
import javax.xml.transform.Templates;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.sax.SAXTransformerFactory;
import javax.xml.transform.sax.TransformerHandler;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;

import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.UID;
import org.dcm4che3.data.VR;
import org.dcm4che3.io.DicomInputStream;
import org.dcm4che3.io.DicomOutputStream;
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
import org.weasis.dicom.param.DicomProgress;
import org.weasis.dicom.param.DicomState;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * 
 */
public class FindSCU {

    public static enum InformationModel {
        PatientRoot(UID.PatientRootQueryRetrieveInformationModelFIND, "STUDY"),
        StudyRoot(UID.StudyRootQueryRetrieveInformationModelFIND, "STUDY"),
        PatientStudyOnly(UID.PatientStudyOnlyQueryRetrieveInformationModelFINDRetired, "STUDY"),
        MWL(UID.ModalityWorklistInformationModelFIND, null), UPSPull(UID.UnifiedProcedureStepPullSOPClass, null),
        UPSWatch(UID.UnifiedProcedureStepWatchSOPClass, null), HangingProtocol(UID.HangingProtocolInformationModelFIND,
                                                                               null),
        ColorPalette(UID.ColorPaletteInformationModelFIND, null);

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
    }

    private static SAXTransformerFactory saxtf;

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
    private Attributes keys = new Attributes();

    private boolean catOut = false;
    private boolean xml = false;
    private boolean xmlIndent = false;
    private boolean xmlIncludeKeyword = true;
    private boolean xmlIncludeNamespaceDeclaration = false;
    private File xsltFile;
    private Templates xsltTpls;
    private OutputStream out;

    private Association as;
    private AtomicInteger totNumMatches = new AtomicInteger();

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

    public final void setInformationModel(InformationModel model, String[] tss, EnumSet<QueryOption> queryOptions) {
        this.model = model;
        rq.addPresentationContext(new PresentationContext(1, model.cuid, tss));
        if (!queryOptions.isEmpty()) {
            model.adjustQueryOptions(queryOptions);
            rq.addExtendedNegotiation(new ExtendedNegotiation(model.cuid, QueryOption
                .toExtendedNegotiationInformation(queryOptions)));
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

    public void open() throws IOException, InterruptedException, IncompatibleConnectionException,
        GeneralSecurityException {
        as = ae.connect(conn, remote, rq);
    }

    public void close() throws IOException, InterruptedException {
        if (as != null && as.isReadyForDataTransfer()) {
            as.waitForOutstandingRSP();
            as.release();
        }
        SafeClose.close(out);
        out = null;
    }

    public void query(File f) throws IOException, InterruptedException {
        Attributes attrs;
        DicomInputStream dis = null;
        try {
            attrs = new DicomInputStream(f).readDataset(-1, -1);
            if (inFilter != null) {
                attrs = new Attributes(inFilter.length + 1);
                attrs.addSelected(attrs, inFilter);
            }
        } finally {
            SafeClose.close(dis);
        }
        attrs.addAll(keys);
        query(attrs);
    }

    public void query() throws IOException, InterruptedException {
        query(keys);
    }

    private void query(Attributes keys) throws IOException, InterruptedException {
        DimseRSPHandler rspHandler = new DimseRSPHandler(as.nextMessageID()) {

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
                            e.printStackTrace();
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

    private void query(Attributes keys, DimseRSPHandler rspHandler) throws IOException, InterruptedException {
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
                DicomOutputStream dos = new DicomOutputStream(out, UID.ImplicitVRLittleEndian);
                dos.writeDataset(null, data);
            }
            out.flush();
        } catch (Exception e) {
            e.printStackTrace();
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

    public TransformerHandler getTransformerHandler() throws Exception {
        SAXTransformerFactory tf = saxtf;
        if (tf == null) {
            saxtf = tf = (SAXTransformerFactory) TransformerFactory.newInstance();
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
