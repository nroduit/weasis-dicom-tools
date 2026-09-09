/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.dcm4che3.img;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.opencv.core.CvType;
import org.weasis.opencv.data.ImageCV;

/**
 * One-row images holding every value a pixel of a given type can store, in the order {@code
 * Core.LUT} indexes its table: the unsigned bit pattern, so a signed type runs 0 … max, min … -1.
 */
final class StoredValueRamp {
  private static final Map<Integer, ImageCV> RAMPS = new ConcurrentHashMap<>();

  private StoredValueRamp() {}

  /** Returns the shared ramp of a single-channel 8 or 16-bit integer type, otherwise null. */
  static ImageCV of(int cvType) {
    if (CvType.channels(cvType) != 1 || CvType.depth(cvType) > CvType.CV_16S) {
      return null;
    }
    return RAMPS.computeIfAbsent(cvType, StoredValueRamp::create);
  }

  private static ImageCV create(int cvType) {
    boolean sixteenBits = CvType.depth(cvType) >= CvType.CV_16U;
    int size = sixteenBits ? 1 << 16 : 1 << 8;
    var ramp = new ImageCV(1, size, cvType);
    if (sixteenBits) {
      short[] values = new short[size];
      for (int i = 0; i < size; i++) {
        values[i] = (short) i;
      }
      ramp.put(0, 0, values);
    } else {
      byte[] values = new byte[size];
      for (int i = 0; i < size; i++) {
        values[i] = (byte) i;
      }
      ramp.put(0, 0, values);
    }
    return ramp;
  }
}
