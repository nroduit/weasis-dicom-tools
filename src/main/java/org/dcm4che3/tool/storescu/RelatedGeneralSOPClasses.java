/*
 * Copyright (c) 2014-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.dcm4che3.tool.storescu;

import java.util.HashMap;
import java.util.Properties;
import org.dcm4che3.data.UID;
import org.dcm4che3.net.pdu.CommonExtendedNegotiation;
import org.dcm4che3.util.StringUtils;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 */
public class RelatedGeneralSOPClasses {

  private final HashMap<String, CommonExtendedNegotiation> commonExtNegs = new HashMap<>();

  public void init(Properties props) {
    for (String cuid : props.stringPropertyNames()) {
      commonExtNegs.put(
          cuid,
          new CommonExtendedNegotiation(
              cuid, UID.Storage, StringUtils.split(props.getProperty(cuid), ',')));
    }
  }

  public CommonExtendedNegotiation getCommonExtendedNegotiation(String cuid) {
    CommonExtendedNegotiation commonExtNeg = commonExtNegs.get(cuid);
    return commonExtNeg != null ? commonExtNeg : new CommonExtendedNegotiation(cuid, UID.Storage);
  }
}
