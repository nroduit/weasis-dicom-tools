/*
 * Copyright (c) 2024 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.hp;

import org.dcm4che3.data.Attributes;

public interface HPComparator {

  int compare(Attributes o1, int frame1, Attributes o2, int frame2);

  /**
   * Get the attributes. Direct modifications of the returned <tt>Attributes</tt> is strongly
   * discouraged as it may cause inconsistencies in the internal state of this object.
   *
   * @return the attributes
   */
  Attributes getAttributes();

  String getImageSetSelectorUsageFlag();

  Integer getSelectorAttribute();

  Integer getSelectorSequencePointer();

  Integer getFunctionalGroupPointer();

  String getSelectorSequencePointerPrivateCreator();

  String getFunctionalGroupPrivateCreator();

  String getSelectorAttributePrivateCreator();

  Integer getSelectorValueNumber();

  String getSortByCategory();

  String getSortingDirection();
}
