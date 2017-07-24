package org.weasis.dicom.param;

import java.net.URL;

public class GatewayParams extends AbstractListenerParams {
    public GatewayParams(boolean bindCallingAet) {
        super(null, bindCallingAet, null);
    }

    public GatewayParams(AdvancedParams params, boolean bindCallingAet) {
        super(params, bindCallingAet, null);
    }

    public GatewayParams(AdvancedParams params, boolean bindCallingAet, URL transferCapabilityFile,
        String... acceptedCallingAETitles) {
        super(params, bindCallingAet, transferCapabilityFile, acceptedCallingAETitles);
    }

}
