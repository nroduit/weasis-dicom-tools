/*
 * Copyright (c) 2022 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.geom;

import java.awt.Color;

/**
 * Contract for anatomical orientations in medical imaging contexts.
 *
 * <p>This interface defines the common properties that all orientation types must provide,
 * including display names and color coding for visualization. Implementations include biped (human)
 * and quadruped (animal) anatomical orientations.
 *
 * <p>The color coding follows a standard convention:
 *
 * <ul>
 *   <li><b>Blue</b> - Left/Right orientations (lateral axis)
 *   <li><b>Red</b> - Anterior/Posterior orientations (sagittal axis)
 *   <li><b>Green</b> - Head/Foot or Superior/Inferior orientations (longitudinal axis)
 * </ul>
 *
 * <p>Usage in DICOM context:
 *
 * <pre>{@code
 * // Get orientation from direction cosine vector
 * var orientation = getPatientOrientation(vector, threshold, false);
 * String displayName = orientation.getFullName();  // "Left", "Right", etc.
 * Color visualColor = orientation.getColor();      // Color for UI display
 * String code = orientation.name();                // "L", "R", etc.
 * }</pre>
 *
 * @author Nicolas Roduit
 * @since 1.0
 * @see PatientOrientation.Biped
 * @see PatientOrientation.Quadruped
 */
public sealed interface Orientation permits PatientOrientation.Biped, PatientOrientation.Quadruped {

  /**
   * Returns the standard code name for this orientation.
   *
   * @return the orientation code (typically 1-2 characters)
   */
  String name();

  /**
   * Returns the full descriptive name for this orientation.
   *
   * @return the full orientation name for display purposes
   */
  String getFullName();

  /**
   * Returns the color associated with this orientation for visualization.
   *
   * @return the color for visual representation of this orientation
   */
  Color getColor();
}
