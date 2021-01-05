/*
 * Copyright (c) 2017-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at http://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.util;

import java.util.Set;
import org.dcm4che3.data.UID;
import org.dcm4che3.net.Association;

public class ForwardUtil {

  private ForwardUtil() {}

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
