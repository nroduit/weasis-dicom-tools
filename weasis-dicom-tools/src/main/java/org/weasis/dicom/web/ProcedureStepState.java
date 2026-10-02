/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.web;

/** Procedure Step State (0074,1000) of a UPS workitem (PS3.4 Table CC.1.1-1). */
public enum ProcedureStepState {
  SCHEDULED("SCHEDULED"),
  IN_PROGRESS("IN PROGRESS"),
  CANCELED("CANCELED"),
  COMPLETED("COMPLETED");

  private final String code;

  ProcedureStepState(String code) {
    this.code = code;
  }

  /** Returns the defined term encoded in the dataset. */
  public String code() {
    return code;
  }

  public static ProcedureStepState fromCode(String code) {
    for (ProcedureStepState state : values()) {
      if (state.code.equals(code)) {
        return state;
      }
    }
    throw new IllegalArgumentException("Unknown Procedure Step State: " + code);
  }
}
