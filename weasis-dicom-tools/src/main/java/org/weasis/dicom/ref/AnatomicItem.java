/*
 * Copyright (c) 2024 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.ref;

import org.weasis.dicom.macro.ItemCode;

/**
 * Represents an anatomical item in DICOM that can be used in anatomical regions. This interface
 * extends ItemCode to include anatomical-specific properties such as legacy code compatibility and
 * pairing characteristics.
 *
 * <p>Anatomical items can represent body parts, surface parts, or other anatomical structures that
 * may appear in DICOM anatomical region sequences.
 *
 * @see org.weasis.dicom.ref.BodyPart
 * @see org.weasis.dicom.ref.SurfacePart
 * @see org.weasis.dicom.ref.OtherPart
 */
public interface AnatomicItem extends ItemCode {

  /**
   * Returns the legacy DICOM code used in the BodyPartExamined tag. This maintains compatibility
   * with older DICOM implementations.
   *
   * @return the legacy code, or {@code null} if no legacy code exists
   */
  String getLegacyCode();

  /**
   * Indicates whether this anatomical item represents a paired structure. Paired structures are
   * those that naturally occur in pairs in the human body, such as eyes, hands, lungs, etc.
   *
   * @return {@code true} if the anatomical item is paired, {@code false} otherwise
   */
  boolean isPaired();
}
