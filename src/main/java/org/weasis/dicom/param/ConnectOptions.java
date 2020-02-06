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

public class ConnectOptions {

    
    /* Maximum number of operations this AE may perform asynchronously, unlimited is 0 and not asynchronously is 1 */
    private int maxOpsInvoked = 0;
    private int maxOpsPerformed = 0;

    private int maxPdulenRcv =0;
    private int maxPdulenSnd = 0;

    private boolean packPDV = true;
    private int backlog = 0;
    private int connectTimeout = 0;
    private int requestTimeout = 0;
    private int acceptTimeout = 0;
    private int releaseTimeout = 0;
    private int responseTimeout = 0;
    private int retrieveTimeout = 0;
    private int idleTimeout = 0;
    private int socloseDelay = 0;
    private int sosndBuffer = 0;
    private int sorcvBuffer = 0;
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
