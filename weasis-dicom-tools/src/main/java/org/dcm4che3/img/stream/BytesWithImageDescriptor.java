/*
 * Copyright (c) 2021 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.dcm4che3.img.stream;

import java.io.IOException;
import java.nio.ByteBuffer;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.VR;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @since Mar 2018
 */
public interface BytesWithImageDescriptor extends ImageReaderDescriptor {
  ByteBuffer getBytes(int frame) throws IOException;

  String getTransferSyntax();

  default boolean bigEndian() {
    return false;
  }

  default boolean floatPixelData() {
    return false;
  }

  VR getPixelDataVR();

  Attributes getPaletteColorLookupTable();
}
