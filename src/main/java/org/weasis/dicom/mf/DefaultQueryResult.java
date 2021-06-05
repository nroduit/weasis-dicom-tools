/*
 * Copyright (c) 2017-2019 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.mf;

import java.util.List;
import java.util.Objects;

public class DefaultQueryResult extends AbstractQueryResult {

  protected final WadoParameters wadoParameters;

  public DefaultQueryResult(List<Patient> patients, WadoParameters wadoParameters) {
    super(patients);
    this.wadoParameters = Objects.requireNonNull(wadoParameters);
  }

  @Override
  public WadoParameters getWadoParameters() {
    return wadoParameters;
  }
}
