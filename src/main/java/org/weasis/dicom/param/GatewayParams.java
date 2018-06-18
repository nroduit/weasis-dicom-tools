package org.weasis.dicom.param;

import java.net.URL;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class GatewayParams extends AbstractListenerParams {
    /**
     * {@inheritDoc}
     */
    public GatewayParams(boolean bindCallingAet) {
        super(null, bindCallingAet, null);
    }

    /**
     * {@inheritDoc}
     */
    public GatewayParams(AdvancedParams params, boolean bindCallingAet) {
        super(params, bindCallingAet, null);
    }

    /**
     * {@inheritDoc}
     */
    public GatewayParams(AdvancedParams params, boolean bindCallingAet, URL transferCapabilityFile,
        String... acceptedCallingAETitles) {
        super(params, bindCallingAet, transferCapabilityFile, acceptedCallingAETitles);
    }

    public static String[] getAcceptedCallingAETitles(Map<ForwardDicomNode, List<ForwardDestination>> destinations) {
        Set<ForwardDicomNode> set = destinations.keySet();
        if (set.stream().anyMatch(s -> s.getAcceptedSourceNodes().isEmpty())) {
            return null;
        }
        return set.stream().map(s -> s.getAcceptedSourceNodes()).flatMap(l -> l.stream()).map(n -> n.getAet())
            .distinct().toArray(String[]::new);
    }
}
