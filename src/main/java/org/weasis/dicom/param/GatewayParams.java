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

import java.net.URL;
import java.util.List;
import java.util.Map;
import java.util.Set;

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

    public static String[] getAcceptedCallingAETitles(Map<ForwardDicomNode, List<ForwardDestination>> destinations) {
        Set<ForwardDicomNode> set = destinations.keySet();
        if (set.stream().anyMatch(s -> s.getAcceptedSourceNodes().isEmpty())) {
            return null;
        }
        return set.stream().map(ForwardDicomNode::getAcceptedSourceNodes).flatMap(Set<DicomNode>::stream).map(DicomNode::getAet)
            .distinct().toArray(String[]::new);
    }
}
