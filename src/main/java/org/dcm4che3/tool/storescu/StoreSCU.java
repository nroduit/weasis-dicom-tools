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

package org.dcm4che3.tool.storescu;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.security.GeneralSecurityException;
import java.text.MessageFormat;
import java.util.HashMap;
import java.util.List;
import java.util.Set;

import javax.xml.parsers.ParserConfigurationException;

import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.UID;
import org.dcm4che3.data.VR;
import org.dcm4che3.imageio.codec.Decompressor;
import org.dcm4che3.io.DicomInputStream;
import org.dcm4che3.io.DicomInputStream.IncludeBulkData;
import org.dcm4che3.io.SAXReader;
import org.dcm4che3.net.ApplicationEntity;
import org.dcm4che3.net.Association;
import org.dcm4che3.net.Connection;
import org.dcm4che3.net.DataWriterAdapter;
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
import org.dcm4che3.util.UIDUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.dicom.param.DicomProgress;
import org.weasis.dicom.param.DicomState;
import org.xml.sax.SAXException;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @author Michael Backhaus <michael.backhaus@agfa.com>
 */
public class StoreSCU {
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
    private int filesSent = 0;
    private boolean generateUIDs = false;
    private HashMap<String, String> uidMap;
    private final DicomState state;

    private RSPHandlerFactory rspHandlerFactory = new RSPHandlerFactory() {

        @Override
        public DimseRSPHandler createDimseRSPHandler(final File f) {

            return new DimseRSPHandler(as.nextMessageID()) {

                @Override
                public void onDimseRSP(Association as, Attributes cmd, Attributes data) {
                    super.onDimseRSP(as, cmd, data);
                    StoreSCU.this.onCStoreRSP(cmd, f);

                    DicomProgress p = state.getProgress();
                    if (p != null) {
                        if (cmd != null) {
                            p.setAttributes(cmd);
                        }
                    }
                }
            };
        }
    };

    public StoreSCU(ApplicationEntity ae, DicomProgress progress) throws IOException {
        this.remote = new Connection();
        this.ae = ae;
        rq.addPresentationContext(new PresentationContext(1, UID.VerificationSOPClass, UID.ImplicitVRLittleEndian));
        state = new DicomState(progress);
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
        final BufferedWriter fileInfos = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(tmpFile)));
        try {
            DicomFiles.scan(fnames, printout, new DicomFiles.Callback() {

                @Override
                public boolean dicomFile(File f, Attributes fmi, long dsPos, Attributes ds) throws IOException {
                    if (!addFile(fileInfos, f, dsPos, fmi, ds)) {
                        return false;
                    }

                    filesScanned++;
                    return true;
                }
            });
        } finally {
            fileInfos.close();
        }
    }

    public void sendFiles() throws IOException {
        BufferedReader fileInfos = new BufferedReader(new InputStreamReader(new FileInputStream(tmpFile)));
        try {
            String line;
            while (as.isReadyForDataTransfer() && (line = fileInfos.readLine()) != null) {
                DicomProgress p = state.getProgress();
                if (p != null) {
                    if (p.isCancel()) {
                        LOG.info("Aborting C-Store: ");
                        as.abort();
                        break;
                    }
                }
                String[] ss = StringUtils.split(line, '\t');
                try {
                    send(new File(ss[4]), Long.parseLong(ss[3]), ss[1], ss[0], ss[2]);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
            try {
                as.waitForOutstandingRSP();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        } finally {
            SafeClose.close(fileInfos);
        }
    }

    public boolean addFile(BufferedWriter fileInfos, File f, long endFmi, Attributes fmi, Attributes ds)
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
                rq.addPresentationContext(new PresentationContext(rq.getNumberOfPresentationContexts() * 2 + 1, cuid,
                    UID.ExplicitVRLittleEndian));
            }
            if (!ts.equals(UID.ImplicitVRLittleEndian)) {
                rq.addPresentationContext(new PresentationContext(rq.getNumberOfPresentationContexts() * 2 + 1, cuid,
                    UID.ImplicitVRLittleEndian));
            }
        }
        rq.addPresentationContext(new PresentationContext(rq.getNumberOfPresentationContexts() * 2 + 1, cuid, ts));
        return true;
    }

    public Attributes echo() throws IOException, InterruptedException {
        DimseRSP response = as.cecho();
        response.next();
        return response.getCommand();
    }

    public void send(final File f, long fmiEndPos, String cuid, String iuid, String filets)
        throws IOException, InterruptedException, ParserConfigurationException, SAXException {
        String ts = selectTransferSyntax(cuid, filets);

        if (f.getName().endsWith(".xml")) {
            Attributes parsedDicomFile = SAXReader.parse(new FileInputStream(f));
            if (CLIUtils.updateAttributes(parsedDicomFile, attrs, uidSuffix)) {
                iuid = parsedDicomFile.getString(Tag.SOPInstanceUID);
            }
            if (!ts.equals(filets)) {
                Decompressor.decompress(parsedDicomFile, filets);
            }
            as.cstore(cuid, iuid, priority, new DataWriterAdapter(parsedDicomFile), ts,
                rspHandlerFactory.createDimseRSPHandler(f));
        } else if (uidSuffix == null && attrs.isEmpty() && ts.equals(filets) && !generateUIDs) {
            FileInputStream in = new FileInputStream(f);
            try {
                in.skip(fmiEndPos);
                InputStreamDataWriter data = new InputStreamDataWriter(in);
                as.cstore(cuid, iuid, priority, data, ts, rspHandlerFactory.createDimseRSPHandler(f));
            } finally {
                SafeClose.close(in);
            }
        } else {
            DicomInputStream in = new DicomInputStream(f);
            try {
                in.setIncludeBulkData(IncludeBulkData.URI);
                Attributes data = in.readDataset(-1, -1);
                if (generateUIDs) {
                    // New Study UID
                    String oldStudyUID = data.getString(Tag.StudyInstanceUID);
                    String studyUID = uidMap.get(oldStudyUID);
                    if (studyUID == null) {
                        studyUID = UIDUtils.createUID("2.25.35");
                        uidMap.put(oldStudyUID, studyUID);
                    }
                    data.setString(Tag.StudyInstanceUID, VR.UI, studyUID);

                    // New Series UID
                    String oldSeriesUID = data.getString(Tag.SeriesInstanceUID);
                    String seriesUID = uidMap.get(oldSeriesUID);
                    if (seriesUID == null) {
                        seriesUID = UIDUtils.createUID("2.25.35");
                        uidMap.put(oldSeriesUID, seriesUID);
                    }
                    data.setString(Tag.SeriesInstanceUID, VR.UI, seriesUID);

                    // New Sop UID
                    iuid = UIDUtils.createUID("2.25.35");
                    data.setString(Tag.SOPInstanceUID, VR.UI, iuid);
                }
                if (CLIUtils.updateAttributes(data, attrs, uidSuffix)) {
                    iuid = data.getString(Tag.SOPInstanceUID);
                }
                if (!ts.equals(filets)) {
                    Decompressor.decompress(data, filets);
                }
                as.cstore(cuid, iuid, priority, new DataWriterAdapter(data), ts,
                    rspHandlerFactory.createDimseRSPHandler(f));
            } finally {
                SafeClose.close(in);
            }
        }
    }

    private String selectTransferSyntax(String cuid, String filets) {
        Set<String> tss = as.getTransferSyntaxesFor(cuid);
        if (tss.contains(filets)) {
            return filets;
        }

        if (tss.contains(UID.ExplicitVRLittleEndian)) {
            return UID.ExplicitVRLittleEndian;
        }

        return UID.ImplicitVRLittleEndian;
    }

    public void close() throws IOException, InterruptedException {
        if (as != null) {
            if (as.isReadyForDataTransfer()) {
                as.release();
            }
            as.waitForSocketClose();
        }
    }

    public void open()
        throws IOException, InterruptedException, IncompatibleConnectionException, GeneralSecurityException {
        as = ae.connect(remote, rq);
    }

    private void onCStoreRSP(Attributes cmd, File f) {
        int status = cmd.getInt(Tag.Status, -1);
        switch (status) {
            case Status.Success:
                totalSize += f.length();
                ++filesSent;
                // System.out.print('.');
                break;
            case Status.CoercionOfDataElements:
            case Status.ElementsDiscarded:
            case Status.DataSetDoesNotMatchSOPClassWarning:
                totalSize += f.length();
                ++filesSent;
                System.err.println(MessageFormat.format("WARNING: Received C-STORE-RSP with Status {0}H for {1}",
                    TagUtils.shortToHexString(status), f));
                System.err.println(cmd);
                break;
            default:
                System.out.print('E');
                System.err.println(MessageFormat.format("ERROR: Received C-STORE-RSP with Status {0}H for {1}",
                    TagUtils.shortToHexString(status), f));
                System.err.println(cmd);
        }
    }

    public int getFilesScanned() {
        return filesScanned;
    }

    public DicomState getState() {
        return state;
    }
}
