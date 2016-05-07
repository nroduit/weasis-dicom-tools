/*******************************************************************************
 * Copyright (c) 2014 Weasis Team.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Nicolas Roduit - initial API and implementation
 *******************************************************************************/
package org.weasis.dicom.param;

import org.dcm4che3.net.Connection;

public class ConnectOptions {
    /* Maximum number of operations this AE may perform asynchronously, unlimited is 0 and not asynchronously is 1 */
    private int maxOpsInvoked = Connection.SYNCHRONOUS_MODE;
    private int maxOpsPerformed = Connection.SYNCHRONOUS_MODE;

    private int maxPdulenRcv = Connection.DEF_MAX_PDU_LENGTH;
    private int maxPdulenSnd = Connection.DEF_MAX_PDU_LENGTH;

    private boolean packPDV = true;
    private int backlog = Connection.DEF_BACKLOG;
    private int connectTimeout = Connection.NO_TIMEOUT;
    private int requestTimeout = Connection.NO_TIMEOUT;
    private int acceptTimeout = Connection.NO_TIMEOUT;
    private int releaseTimeout = Connection.NO_TIMEOUT;
    private int responseTimeout = Connection.NO_TIMEOUT;
    private int retrieveTimeout = Connection.NO_TIMEOUT;
    private int idleTimeout = Connection.NO_TIMEOUT;
    private int socloseDelay = Connection.DEF_SOCKETDELAY;
    private int sosndBuffer = Connection.DEF_BUFFERSIZE;
    private int sorcvBuffer = Connection.DEF_BUFFERSIZE;
    private boolean tcpNoDelay = true;

    public ConnectOptions() {
        super();
    }

    public int getMaxOpsInvoked() {
        return maxOpsInvoked;
    }

    public void setMaxOpsInvoked(int maxOpsInvoked) {
        this.maxOpsInvoked = maxOpsInvoked;
    }

    public int getMaxOpsPerformed() {
        return maxOpsPerformed;
    }

    public void setMaxOpsPerformed(int maxOpsPerformed) {
        this.maxOpsPerformed = maxOpsPerformed;
    }

    public int getMaxPdulenRcv() {
        return maxPdulenRcv;
    }

    public void setMaxPdulenRcv(int maxPdulenRcv) {
        this.maxPdulenRcv = maxPdulenRcv;
    }

    public int getMaxPdulenSnd() {
        return maxPdulenSnd;
    }

    public void setMaxPdulenSnd(int maxPdulenSnd) {
        this.maxPdulenSnd = maxPdulenSnd;
    }

    public boolean isPackPDV() {
        return packPDV;
    }

    public void setPackPDV(boolean packPDV) {
        this.packPDV = packPDV;
    }

    public int getConnectTimeout() {
        return connectTimeout;
    }

    public void setConnectTimeout(int connectTimeout) {
        this.connectTimeout = connectTimeout;
    }

    public int getRequestTimeout() {
        return requestTimeout;
    }

    public void setRequestTimeout(int requestTimeout) {
        this.requestTimeout = requestTimeout;
    }

    public int getAcceptTimeout() {
        return acceptTimeout;
    }

    public void setAcceptTimeout(int acceptTimeout) {
        this.acceptTimeout = acceptTimeout;
    }

    public int getReleaseTimeout() {
        return releaseTimeout;
    }

    public void setReleaseTimeout(int releaseTimeout) {
        this.releaseTimeout = releaseTimeout;
    }

    public int getResponseTimeout() {
        return responseTimeout;
    }

    public void setResponseTimeout(int responseTimeout) {
        this.responseTimeout = responseTimeout;
    }

    public int getRetrieveTimeout() {
        return retrieveTimeout;
    }

    public void setRetrieveTimeout(int retrieveTimeout) {
        this.retrieveTimeout = retrieveTimeout;
    }

    public int getIdleTimeout() {
        return idleTimeout;
    }

    public void setIdleTimeout(int idleTimeout) {
        this.idleTimeout = idleTimeout;
    }

    public int getSocloseDelay() {
        return socloseDelay;
    }

    public void setSocloseDelay(int socloseDelay) {
        this.socloseDelay = socloseDelay;
    }

    public int getSosndBuffer() {
        return sosndBuffer;
    }

    public void setSosndBuffer(int sosndBuffer) {
        this.sosndBuffer = sosndBuffer;
    }

    public int getSorcvBuffer() {
        return sorcvBuffer;
    }

    public void setSorcvBuffer(int sorcvBuffer) {
        this.sorcvBuffer = sorcvBuffer;
    }

    public boolean isTcpNoDelay() {
        return tcpNoDelay;
    }

    public void setTcpNoDelay(boolean tcpNoDelay) {
        this.tcpNoDelay = tcpNoDelay;
    }

    public int getBacklog() {
        return backlog;
    }

    public void setBacklog(int backlog) {
        this.backlog = backlog;
    }

}
