/*
 * Copyright (c) 2024 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.hp.filter;

import java.util.Arrays;
import java.util.Collection;
import java.util.Objects;
import org.dcm4che3.data.Tag;
import org.weasis.core.util.MathUtil;
import org.weasis.dicom.macro.Code;

public abstract class HPFilter {
  protected final int numParams;
  protected final String codeString;

  protected HPFilter(String codeString, int numParams) {
    this.codeString = codeString;
    this.numParams = numParams;
  }

  public final int getNumParams() {
    return numParams;
  }

  public final boolean isNumeric() {
    return numParams != 0;
  }

  public boolean op(String[] values, int valueNumber, String[] params) {
    throw new UnsupportedOperationException();
  }

  public boolean op(Collection<Code> values, Collection<Code> params) {
    throw new UnsupportedOperationException();
  }

  public abstract boolean op(int[] values, int valueNumber, int[] params);

  public abstract boolean op(int[] values, int valueNumber, long[] params);

  public abstract boolean op(float[] values, int valueNumber, float[] params);

  public abstract boolean op(double[] values, int valueNumber, double[] params);

  public final String getCodeString() {
    return codeString;
  }

  static boolean memberOf(String value, String[] params) {
    return Arrays.asList(params).contains(value);
  }

  static boolean memberOf(int value, int[] params) {
    return Arrays.stream(params).anyMatch(v -> v == value);
  }

  static boolean memberOf(int value, long[] params) {
    return Arrays.stream(params).anyMatch(v -> v == value);
  }

  static boolean memberOf(float value, float[] params) {
    for (float v : params) {
      if (MathUtil.isEqual(v, value)) {
        return true;
      }
    }
    return false;
  }

  static boolean memberOf(double value, double[] params) {
    return Arrays.stream(params).anyMatch(v -> MathUtil.isEqual(v, value));
  }

  static boolean memberOf(Code value, Collection<Code> params) {
    for (Code param : params) {
      if (codeEquals(param, value)) {
        return true;
      }
    }
    return false;
  }

  static boolean codeEquals(Code item1, Code item2) {
    if (!Objects.equals(item1.getCodeValue(), item2.getCodeValue())) {
      return false;
    }
    if (!Objects.equals(item1.getCodingSchemeDesignator(), item2.getCodingSchemeDesignator())) {
      return false;
    }
    if (!item1.getAttributes().containsValue(Tag.CodingSchemeVersion)
        || !item2.getAttributes().containsValue(Tag.CodingSchemeVersion)) {
      return true;
    }
    return Objects.equals(item1.getCodingSchemeVersion(), item2.getCodingSchemeVersion());
  }
}
