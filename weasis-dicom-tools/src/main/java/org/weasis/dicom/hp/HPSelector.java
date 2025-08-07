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

public interface HPSelector {

  boolean matches(Attributes attributes, int frame);

  /**
   * Returns the <tt>Attributes</tt> that backs this <tt>HPSelector</tt>.
   *
   * <p>Direct modifications of the returned <tt>Attributes</tt> is strongly discouraged as it may
   * cause inconsistencies in the internal state of this object.
   *
   * @return the <tt>Attributes</tt> that backs this <tt>HPSelector</tt>
   */
  Attributes getAttributes();

  String getImageSetSelectorUsageFlag();

  String getFilterByCategory();

  String getFilterByAttributePresence();

  Integer getSelectorAttribute();

  String getSelectorAttributeVR();

  Integer getSelectorSequencePointer();

  Integer getFunctionalGroupPointer();

  String getSelectorSequencePointerPrivateCreator();

  String getFunctionalGroupPrivateCreator();

  String getSelectorAttributePrivateCreator();

  Object getSelectorValue();

  Integer getSelectorValueNumber();

  String getFilterByOperator();
}
