/*
 * Copyright (c) 2024 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.hp.plugins;

import org.dcm4che3.data.Attributes;
import org.weasis.dicom.hp.HPComparator;
import org.weasis.dicom.hp.spi.HPComparatorCategoryService;

public class AlongAxisComparatorService extends HPComparatorCategoryService {

  public AlongAxisComparatorService() {
    super("ALONG_AXIS");
  }

  @Override
  public HPComparator createHPComparator(Attributes sortOp) {
    return new AlongAxisComparator(sortOp);
  }
}
