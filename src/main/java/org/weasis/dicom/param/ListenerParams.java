package org.weasis.dicom.param;

import java.net.URL;

public class ListenerParams extends AbstractListenerParams {

    private final String storagePattern;

    public ListenerParams(boolean bindCallingAet) {
        this(null, bindCallingAet, null, null);
    }

    public ListenerParams(AdvancedParams params, boolean bindCallingAet) {
        this(params, bindCallingAet, null, null);
    }

    public ListenerParams(AdvancedParams params, boolean bindCallingAet, String storagePattern,
        URL transferCapabilityFile, String... acceptedCallingAETitles) {
        super(params, bindCallingAet, transferCapabilityFile, acceptedCallingAETitles);
        this.storagePattern = storagePattern;
    }

    public String getStoragePattern() {
        return storagePattern;
    }

}
