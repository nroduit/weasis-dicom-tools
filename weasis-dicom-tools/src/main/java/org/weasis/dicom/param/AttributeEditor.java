/*
 * Copyright (c) 2017-2019 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.param;

import org.dcm4che3.data.Attributes;

/**
 * A functional interface for editing DICOM attributes during DICOM operations.
 *
 * <p>This interface allows customization of DICOM attributes before they are sent or processed.
 * Implementations can modify, add, or remove attributes based on the provided context information.
 *
 * <p>Common use cases include:
 *
 * <ul>
 *   <li>Anonymizing patient data by removing or modifying sensitive attributes
 *   <li>Generating new UIDs for studies, series, or instances
 *   <li>Adding or modifying metadata based on destination requirements
 *   <li>Applying institutional policies or compliance rules
 * </ul>
 *
 * @since 1.0
 */
@FunctionalInterface
public interface AttributeEditor {

  /**
   * Applies modifications to the DICOM attributes.
   *
   * <p>This method is called during DICOM operations to allow custom processing of attributes.
   * Implementations should modify the attributes parameter directly and can use the context to make
   * informed decisions about what changes to apply.
   *
   * <p>The context provides information about the transfer operation including source and
   * destination nodes, transfer syntax, and abort control.
   *
   * @param attributes the DICOM attributes to be modified (not null)
   * @param context the editing context providing operation details (not null)
   * @throws RuntimeException if attribute processing fails and should abort the operation
   */
  void apply(Attributes attributes, AttributeEditorContext context);
}
