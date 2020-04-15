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
package org.weasis.dicom.tool;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.util.Objects;
import java.util.Properties;
import java.util.concurrent.CompletableFuture;

import org.dcm4che6.conf.model.ApplicationEntity;
import org.dcm4che6.conf.model.Connection;
import org.dcm4che6.conf.model.Device;
import org.dcm4che6.conf.model.TransferCapability;
import org.dcm4che6.data.DicomObject;
import org.dcm4che6.data.Tag;
import org.dcm4che6.data.UID;
import org.dcm4che6.io.DicomOutputStream;
import org.dcm4che6.net.Association;
import org.dcm4che6.net.DicomServiceException;
import org.dcm4che6.net.DicomServiceRegistry;
import org.dcm4che6.net.Dimse;
import org.dcm4che6.net.DimseHandler;
import org.dcm4che6.net.Status;
import org.dcm4che6.net.TCPConnector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.dicom.param.AdvancedParams;
import org.weasis.dicom.param.DicomNode;
import org.weasis.dicom.param.ListenerParams;
import org.weasis.dicom.util.ServiceUtil;

public class DicomListener implements DimseHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(DicomListener.class);

    private final Path directory;
    private final DicomServiceRegistry serviceRegistry;
    private TCPConnector<Association> tcp;
    private CompletableFuture<Void> task;

    public DicomListener(Path storageDir) throws IOException {
        this.directory = Objects.requireNonNull(storageDir);
        this.serviceRegistry = new DicomServiceRegistry().setDefaultRQHandler(this);

    }

    /**
     * Start the DICOM Listener
     * 
     * @param scpNode
     *            the listener DICOM node. Set hostname to null for binding all the network interface. For binding all
     *            the AETs see ListenerParams.
     * @throws IOException
     * @throws GeneralSecurityException
     */
    public void start(DicomNode scpNode) throws Exception {
        start(scpNode, new ListenerParams(true));
    }

    /**
     * Start the DICOM Listener
     * 
     * @param scpNode
     *            the listener DICOM node. Set hostname to null for binding all the network interface. For binding all
     *            the AETs see ListenerParams.
     * @param params
     *            the listener parameters
     * @throws IOException
     * @throws GeneralSecurityException
     */
    public synchronized void start(DicomNode scpNode, ListenerParams params) throws Exception {
        stop();
        
        if (directory != null) {
            Files.createDirectories(directory);
        }

        // if (StringUtil.hasText(params.getStoragePattern())) {
        // this.filePathFormat = new AttributesFormat(params.getStoragePattern());
        // this.regex = Pattern.compile("\\{(.*?)\\}");
        // } else {
        // this.filePathFormat = null;
        // this.regex = null;
        // }

        AdvancedParams options = Objects.requireNonNull(params).getParams();

        Connection local = ServiceUtil.getConnection(scpNode);
        ApplicationEntity ae = new ApplicationEntity().addConnection(local);
        // configure
        if (params.isBindCallingAet()) {
            options.configureBind(ae, local, scpNode);
        } else {
            ae.setAETitle("*");
            options.configureBind(local, scpNode);
        }
        options.configure(local);
        options.configureTLS(local, null);

        URL transferCapabilityFile = params.getTransferCapabilityFile();
        if (transferCapabilityFile != null) {
            loadDefaultTransferCapability(ae, transferCapabilityFile);
        } else {
            ae.addTransferCapability(new TransferCapability().setSOPClass("*").setTransferSyntaxes("*")
                .setRole(TransferCapability.Role.SCP));
        }
        // Limit the calling AETs
        // storeSCP.getApplicationEntity().setAcceptedCallingAETitles(params.getAcceptedCallingAETitles());

        new Device().addApplicationEntity(ae);
        tcp = new TCPConnector<>((connector, role) -> new Association(connector, role, serviceRegistry));
        task = CompletableFuture.runAsync(tcp);
        tcp.bind(local);
    }

    public static void loadDefaultTransferCapability(ApplicationEntity ae, URL transferCapabilityFile) {
        Properties p = new Properties();
        try {
            if (transferCapabilityFile != null) {
                p.load(transferCapabilityFile.openStream());
            } else {
                p.setProperty("*", "*");
            }
        } catch (IOException e) {
            LOGGER.error("Cannot read sop-classes", e);
        }

        for (String cuid : p.stringPropertyNames()) {
            String ts = p.getProperty(cuid);
            ae.addTransferCapability(new TransferCapability().setSOPClass(toUID(cuid)).setTransferSyntaxes(toUIDs(ts))
                .setRole(TransferCapability.Role.SCP));
        }
    }

    public synchronized void stop() throws IOException {
        if (task != null) {
            task.cancel(true);
        }
        if (tcp != null && tcp.getSelector().isOpen()) {
            tcp.getSelector().keys().forEach(k -> {
                try {
                    k.channel().close();
                } catch (IOException e) {
                    LOGGER.error("Closing socket channel", e);
                }
                k.cancel();
            });
            tcp.getSelector().close();
        }
    }

    @Override
    public void accept(Association as, Byte pcid, Dimse dimse, DicomObject commandSet, InputStream dataStream)
        throws IOException {
        if (dimse != Dimse.C_STORE_RQ) {
            throw new DicomServiceException(Status.UnrecognizedOperation);
        }
        if (directory == null) {
            dataStream.transferTo(OutputStream.nullOutputStream());
        } else {
            Path file = directory.resolve(commandSet.getStringOrElseThrow(Tag.AffectedSOPInstanceUID));
            LOGGER.info("Start M-WRITE {}", file);
            try (DicomOutputStream dos = new DicomOutputStream(Files.newOutputStream(file))) {
                dos.writeFileMetaInformation(
                    DicomObject.createFileMetaInformation(commandSet.getStringOrElseThrow(Tag.AffectedSOPClassUID),
                        commandSet.getStringOrElseThrow(Tag.AffectedSOPInstanceUID), as.getTransferSyntax(pcid)));
                dataStream.transferTo(dos);
            }
            LOGGER.info("Finished M-WRITE {}", file);
        }
        as.writeDimse(pcid, Dimse.C_STORE_RSP, dimse.mkRSP(commandSet));
    }

    public static String[] toUIDs(String s) {
        if (s.equals("*")) {
            return new String[] { "*" };
        }

        String[] uids = s.split(",");
        for (int i = 0; i < uids.length; i++) {
            uids[i] = toUID(uids[i]);
        }
        return uids;
    }

    public static String toUID(String uid) {
        uid = uid.trim();
        return (uid.equals("*") || Character.isDigit(uid.charAt(0))) ? uid : UID.forName(uid);
    }

    public boolean isRunning() {
        return tcp != null && tcp.getSelector().isOpen();
    }
}
