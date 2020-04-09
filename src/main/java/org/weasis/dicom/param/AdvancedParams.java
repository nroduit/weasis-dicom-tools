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
package org.weasis.dicom.param;

import java.io.IOException;

import org.dcm4che6.conf.model.ApplicationEntity;
import org.dcm4che6.conf.model.Connection;
import org.dcm4che6.data.UID;



public class AdvancedParams {
    public static String[] IVR_LE_FIRST =
        { UID.ImplicitVRLittleEndian, UID.ExplicitVRLittleEndian, UID.ExplicitVRBigEndianRetired };
    public static String[] EVR_LE_FIRST =
        { UID.ExplicitVRLittleEndian, UID.ExplicitVRBigEndianRetired, UID.ImplicitVRLittleEndian };
    public static String[] EVR_BE_FIRST =
        { UID.ExplicitVRBigEndianRetired, UID.ExplicitVRLittleEndian, UID.ImplicitVRLittleEndian };
    public static String[] IVR_LE_ONLY = { UID.ImplicitVRLittleEndian };

    private Object informationModel;
 //   private EnumSet<QueryOption> queryOptions = EnumSet.noneOf(QueryOption.class);
    private String[] tsuidOrder = IVR_LE_FIRST;

    /*
     * Configure proxy <[user:password@]host:port> specify host and port of the HTTP Proxy to tunnel the DICOM
     * connection.
     */
    private String proxy;



    private ConnectOptions connectOptions;
    private TlsOptions tlsOptions;

    public AdvancedParams() {
        super();
    }

    public Object getInformationModel() {
        return informationModel;
    }

    public void setInformationModel(Object informationModel) {
        this.informationModel = informationModel;
    }

//    public EnumSet<QueryOption> getQueryOptions() {
//        return queryOptions;
//    }
//
//    public void setQueryOptions(EnumSet<QueryOption> queryOptions) {
//        this.queryOptions = queryOptions;
//    }

    public String[] getTsuidOrder() {
        return tsuidOrder;
    }

    public void setTsuidOrder(String[] tsuidOrder) {
        this.tsuidOrder = tsuidOrder;
    }

    public String getProxy() {
        return proxy;
    }

    public void setProxy(String proxy) {
        this.proxy = proxy;
    }

    public ConnectOptions getConnectOptions() {
        return connectOptions;
    }

    public void setConnectOptions(ConnectOptions connectOptions) {
        this.connectOptions = connectOptions;
    }

    public TlsOptions getTlsOptions() {
        return tlsOptions;
    }

    public void setTlsOptions(TlsOptions tlsOptions) {
        this.tlsOptions = tlsOptions;
    }




    
    /**
     * Bind the connection and applicationEntity with the callingNode 
     * 
     * @param applicationEntity
     * @param connection
     * @param callingNode
     */
    public void configureBind(ApplicationEntity applicationEntity, Connection connection, DicomNode callingNode) {
        applicationEntity.setAETitle(callingNode.getAet());
        configureBind(connection, callingNode);
    }
    
    /**
     * Bind the connection with the callingNode 
     * 
     * @param connection
     * @param callingNode
     */
    public void configureBind(Connection connection, DicomNode callingNode) {
        if (callingNode.getHostname() != null) {
            connection.setHostname(callingNode.getHostname());
        }
        if (callingNode.getPort() != null) {
            connection.setPort(callingNode.getPort());
        }
    }

    public void configure(Connection conn) throws IOException {
        if (connectOptions != null) {
//            conn.setBacklog(connectOptions.getBacklog());
//            conn.setConnectTimeout(connectOptions.getConnectTimeout());
//            conn.setRequestTimeout(connectOptions.getRequestTimeout());
//            conn.setAcceptTimeout(connectOptions.getAcceptTimeout());
//            conn.setReleaseTimeout(connectOptions.getReleaseTimeout());
//            conn.setResponseTimeout(connectOptions.getResponseTimeout());
//            conn.setRetrieveTimeout(connectOptions.getRetrieveTimeout());
//            conn.setIdleTimeout(connectOptions.getIdleTimeout());
//            conn.setSocketCloseDelay(connectOptions.getSocloseDelay());
//            conn.setReceiveBufferSize(connectOptions.getSorcvBuffer());
//            conn.setSendBufferSize(connectOptions.getSosndBuffer());
//            conn.setReceivePDULength(connectOptions.getMaxPdulenRcv());
//            conn.setSendPDULength(connectOptions.getMaxPdulenSnd());
//            conn.setMaxOpsInvoked(connectOptions.getMaxOpsInvoked());
//            conn.setMaxOpsPerformed(connectOptions.getMaxOpsPerformed());
//            conn.setPackPDV(connectOptions.isPackPDV());
//            conn.setTcpNoDelay(connectOptions.isTcpNoDelay());
        }
    }

    public void configureTLS(Connection local, Connection remote) throws IOException {
        if (tlsOptions != null) {
            local.setTlsCipherSuites(tlsOptions.getCipherSuites());
//            local.setTlsProtocols(tlsOptions.getTlsProtocols());
//            local.setTlsNeedClientAuth(tlsOptions.isTlsNeedClientAuth());

//            Device device = local.getDevice();
//            try {
//                device.setKeyManager(SSLManagerFactory.createKeyManager(tlsOptions.getKeystoreType(),
//                    tlsOptions.getKeystoreURL(), tlsOptions.getKeystorePass(), tlsOptions.getKeyPass()));
//                device.setTrustManager(SSLManagerFactory.createTrustManager(tlsOptions.getTruststoreType(),
//                    tlsOptions.getTruststoreURL(), tlsOptions.getTruststorePass()));
//                if (remote != null) {
//                    remote.setTlsProtocols(local.getTlsProtocols());
//                    remote.setTlsCipherSuites(local.getTlsCipherSuites());
//                }
//            } catch (GeneralSecurityException e) {
//                throw new IOException(e);
//            }
        }
    }

}
