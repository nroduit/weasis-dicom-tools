package org.weasis.dicom.param;

import java.net.URL;

public abstract class AbstractListenerParams {

    protected final AdvancedParams params;
    protected final boolean bindCallingAet;
    protected final URL transferCapabilityFile;
    protected final String[] acceptedCallingAETitles;

    public AbstractListenerParams(boolean bindCallingAet) {
        this(null, bindCallingAet, null);
    }

    public AbstractListenerParams(AdvancedParams params, boolean bindCallingAet) {
        this(params, bindCallingAet, null);
    }

    public AbstractListenerParams(AdvancedParams params, boolean bindCallingAet, URL transferCapabilityFile,
        String... acceptedCallingAETitles) {
        this.params = params == null ? new AdvancedParams() : params;
        this.bindCallingAet = bindCallingAet;
        this.transferCapabilityFile = transferCapabilityFile;
        this.acceptedCallingAETitles = acceptedCallingAETitles;
        if (params == null) {
            // Concurrent DICOM operations
            this.params.getConnectOptions().setMaxOpsInvoked(15);
            this.params.getConnectOptions().setMaxOpsPerformed(15);
        }
    }

    public boolean isBindCallingAet() {
        return bindCallingAet;
    }

    public URL getTransferCapabilityFile() {
        return transferCapabilityFile;
    }

    public String[] getAcceptedCallingAETitles() {
        return acceptedCallingAETitles;
    }

    public AdvancedParams getParams() {
        return params;
    }

}