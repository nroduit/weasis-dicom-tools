/*
 * Copyright (c) 2009-2021 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.dcm4che3.imageio.codec;

import org.dcm4che3.data.Attributes;
import org.dcm4che3.imageio.codec.mp4.MP4FileType;
import org.weasis.core.util.annotations.Generated;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @since Jun 2019
 */
@Generated
public interface XPEGParser {
  long getCodeStreamPosition();

  long getPositionAfterAPPSegments();

  MP4FileType getMP4FileType();

  Attributes getAttributes(Attributes attrs);

  String getTransferSyntaxUID(boolean fragmented) throws XPEGParserException;

  default String getTransferSyntaxUID() throws XPEGParserException {
    return getTransferSyntaxUID(false);
  }
}
