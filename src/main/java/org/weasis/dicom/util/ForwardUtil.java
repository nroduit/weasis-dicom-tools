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
package org.weasis.dicom.util;

import java.io.InputStream;
import java.util.Set;
import org.dcm4che3.data.UID;
import org.dcm4che3.net.Association;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ForwardUtil {

    private ForwardUtil() {
    }

    public static String selectTransferSyntax(Association as, String cuid, String filets) {
        Set<String> tss = as.getTransferSyntaxesFor(cuid);
        if (tss.contains(filets)) {
            return filets;
        }

        if (tss.contains(UID.ExplicitVRLittleEndian)) {
            return UID.ExplicitVRLittleEndian;
        }

        return UID.ImplicitVRLittleEndian;
    }
}
