/*******************************************************************************
 * Copyright (c) 2009-2019 Weasis Team and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 *
 * Contributors:
 *     Nicolas Roduit - initial API and implementation
 *******************************************************************************/
package org.weasis.dicom.op;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;

import org.dcm4che6.conf.model.Connection;
import org.dcm4che6.data.DicomElement;
import org.dcm4che6.data.DicomObject;
import org.dcm4che6.data.Tag;
import org.dcm4che6.io.DicomInputHandler;
import org.dcm4che6.io.DicomInputStream;
import org.dcm4che6.net.AAssociate;
import org.dcm4che6.net.Association;
import org.dcm4che6.net.DicomServiceRegistry;
import org.dcm4che6.net.DimseRSP;
import org.dcm4che6.net.Status;
import org.dcm4che6.net.TCPConnector;
import org.dcm4che6.util.TagUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.util.FileUtil;
import org.weasis.core.api.util.StringUtil;
import org.weasis.dicom.param.AdvancedParams;
import org.weasis.dicom.param.CstoreParams;
import org.weasis.dicom.param.DicomNode;
import org.weasis.dicom.param.DicomProgress;
import org.weasis.dicom.param.DicomState;
import org.weasis.dicom.util.ServiceUtil;
import org.weasis.dicom.util.ServiceUtil.ProgressStatus;

public class CStore {

    private static final Logger LOGGER = LoggerFactory.getLogger(CStore.class);

    private CStore() {
    }

    /**
     * @param callingNode
     *            the calling DICOM node configuration
     * @param calledNode
     *            the called DICOM node configuration
     * @param files
     *            the list of file paths
     * @return The DicomSate instance which contains the DICOM response, the DICOM status, the error message and the
     *         progression.
     */
    public static DicomState process(DicomNode callingNode, DicomNode calledNode, List<Path> files) {
        return process(null, callingNode, calledNode, files);
    }

    /**
     * @param callingNode
     *            the calling DICOM node configuration
     * @param calledNode
     *            the called DICOM node configuration
     * @param files
     *            the list of file paths
     * @param progress
     *            the progress handler
     * @return The DicomSate instance which contains the DICOM response, the DICOM status, the error message and the
     *         progression.
     */

    public static DicomState process(DicomNode callingNode, DicomNode calledNode, List<Path> files,
        DicomProgress progress) {
        return process(null, callingNode, calledNode, files, progress);
    }

    /**
     * @param params
     *            optional advanced parameters (proxy, authentication, connection and TLS)
     * @param callingNode
     *            the calling DICOM node configuration
     * @param calledNode
     *            the called DICOM node configuration
     * @param files
     *            the list of file paths
     * @return The DicomSate instance which contains the DICOM response, the DICOM status, the error message and the
     *         progression.
     */
    public static DicomState process(AdvancedParams params, DicomNode callingNode, DicomNode calledNode,
        List<Path> files) {
        return process(params, callingNode, calledNode, files, null);
    }

    /**
     * @param params
     *            the optional advanced parameters (proxy, authentication, connection and TLS)
     * @param callingNode
     *            the calling DICOM node configuration
     * @param calledNode
     *            the called DICOM node configuration
     * @param files
     *            the list of file paths
     * @param progress
     *            the progress handler
     * @return The DicomSate instance which contains the DICOM response, the DICOM status, the error message and the
     *         progression.
     */
    public static DicomState process(AdvancedParams params, DicomNode callingNode, DicomNode calledNode,
        List<Path> files, DicomProgress progress) {
        return process(params, callingNode, calledNode, files, progress, null);
    }

    /**
     * @param params
     *            the optional advanced parameters (proxy, authentication, connection and TLS)
     * @param callingNode
     *            the calling DICOM node configuration
     * @param calledNode
     *            the called DICOM node configuration
     * @param files
     *            the list of file paths
     * @param progress
     *            the progress handler
     * @param cstoreParams
     *            c-store options, see CstoreParams
     * @return The DicomSate instance which contains the DICOM response, the DICOM status, the error message and the
     *         progression.
     */
    public static DicomState process(AdvancedParams params, DicomNode callingNode, DicomNode calledNode,
        List<Path> files, DicomProgress progress, CstoreParams cstoreParams) {
        if (callingNode == null || calledNode == null) {
            throw new IllegalArgumentException("callingNode or calledNode cannot be null!");
        }
        if (files == null || files.isEmpty()) {
            throw new IllegalArgumentException("No files to store!");
        }

        AdvancedParams options = params == null ? new AdvancedParams() : params;
        CstoreParams storeOptions = cstoreParams == null ? new CstoreParams(null, false, null) : cstoreParams;

        List<FileInfo> fileInfos = new ArrayList<>();
        long totalSize = 0;
        try {
            for (Path path : files) {
                try (Stream<Path> walk = Files.walk(path)) {
                    walk.forEach(p -> fileInfos.add(scanFile(p)));
                }
            }
            DicomServiceRegistry serviceRegistry = new DicomServiceRegistry();
            AAssociate.RQ rq = new AAssociate.RQ();
            rq.setCallingAETitle(callingNode.getAet());
            rq.setCalledAETitle(calledNode.getAet());
            fileInfos.forEach(info -> rq.findOrAddPresentationContext(info.sopClassUID, info.transferSyntax));
            TCPConnector<Association> inst =
                new TCPConnector<>((connector, role) -> new Association(connector, role, serviceRegistry));
            Connection local = ServiceUtil.getConnection(callingNode);
            Connection remote = ServiceUtil.getConnection(calledNode);
            options.configureTLS(local, remote);
            CompletableFuture<Void> task = CompletableFuture.runAsync(inst);
            long t1 = System.currentTimeMillis();
            Association as = inst.connect(local, remote).thenCompose(as1 -> as1.open(rq)).join();
            long t2 = System.currentTimeMillis();
            DicomState state = new DicomState(progress);
            for (FileInfo fileInfo : fileInfos) {
                CompletableFuture<DimseRSP> s =
                    as.cstore(fileInfo.sopClassUID, fileInfo.sopInstanceUID, fileInfo, fileInfo.transferSyntax);
               
                ProgressStatus ps = ServiceUtil.setDicomRSP(s.get(), state, fileInfos.size());
                switch (ps) {
                    case COMPLETED:
                        ps = ProgressStatus.COMPLETED;
                        totalSize += fileInfo.length;
                        break;
                    case WARNING:
                        ps = ProgressStatus.WARNING;
                        totalSize += fileInfo.length;
                         System.err.println(MessageFormat.format("WARNING: Received C-STORE-RSP with Status {0}H for {1}",
                         TagUtils.shortToHexString(state.getStatus().orElse(Status.UnableToProcess)), fileInfo.path));
                        System.err.println(s.get().command);
                        break;
                    default:
                         System.err.println(MessageFormat.format("ERROR: Received C-STORE-RSP with Status {0}H for {1}",
                         TagUtils.shortToHexString(state.getStatus().orElse(Status.UnableToProcess)), fileInfo.path));
                        System.err.println(s.get().command);
                }
            }
            as.release();
            long t3 = System.currentTimeMillis();
            as.onClose().join();
            task.cancel(true);

            String timeMsg = MessageFormat.format(
                "DICOM C-STORE connected in {2}ms from {0} to {1}. Stored files in {3}ms. Total size {4}",
                rq.getCallingAETitle(), rq.getCalledAETitle(), t2 - t1, t3 - t2,
                FileUtil.humanReadableByte(totalSize, false));
            state.setMessage(timeMsg);
            return state;
        } catch (Exception e) {
            LOGGER.error("storescu", e);
            return new DicomState(Status.UnableToProcess,
                "DICOM Store failed" + StringUtil.COLON_AND_SPACE + e.getMessage(), null);
        }
    }

    private static FileInfo scanFile(Path path) {
        try (DicomInputStream dis = new DicomInputStream(Files.newInputStream(path))) {
            FileInfo fileInfo = new FileInfo();
            fileInfo.path = path;
            fileInfo.length = Files.size(path);
            DicomObject fmi = dis.readFileMetaInformation();
            if (fmi != null) {
                fileInfo.sopClassUID = fmi.getStringOrElseThrow(Tag.MediaStorageSOPClassUID);
                fileInfo.sopInstanceUID = fmi.getStringOrElseThrow(Tag.MediaStorageSOPInstanceUID);
                fileInfo.transferSyntax = fmi.getStringOrElseThrow(Tag.TransferSyntaxUID);
                fileInfo.position = dis.getStreamPosition();
            } else {
                fileInfo.transferSyntax = dis.getEncoding().transferSyntaxUID;
                dis.withInputHandler(fileInfo).readDataSet();
            }
            return fileInfo;
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }

    private static class FileInfo implements Association.DataWriter, DicomInputHandler {
        Path path;
        String sopClassUID;
        String sopInstanceUID;
        String transferSyntax;
        long position;
        long length;

        @Override
        public void writeTo(OutputStream out, String tsuid) throws IOException {
            try (InputStream in = Files.newInputStream(path)) {
                in.skipNBytes(position);
                in.transferTo(out);
            }
        }

        @Override
        public boolean endElement(DicomInputStream dis, DicomElement dcmElm, boolean bulkData) throws IOException {
            switch (dcmElm.tag()) {
                case Tag.SOPInstanceUID:
                    sopInstanceUID = dcmElm.stringValue(0).get();
                    return false;
                case Tag.SOPClassUID:
                    sopClassUID = dcmElm.stringValue(0).get();
            }
            return true;
        }
    }
}
