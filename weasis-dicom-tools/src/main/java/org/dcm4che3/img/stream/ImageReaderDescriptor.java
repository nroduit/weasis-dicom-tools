/*
 * Copyright (c) 2021 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.dcm4che3.img.stream;

import java.util.Optional;

/**
 * Provides access to image metadata and properties for DICOM image readers.
 *
 * <p>This interface serves as a bridge between image input streams and their corresponding image
 * descriptors, allowing implementations to provide image metadata in a consistent manner.
 *
 * @author Nicolas Roduit
 * @since 5.0
 */
public interface ImageReaderDescriptor {
  /**
   * Retrieves the image descriptor containing metadata about the image.
   *
   * <p>The descriptor includes essential image properties such as dimensions, color space, bit
   * depth, and DICOM-specific attributes.
   *
   * @return the image descriptor, or {@code null} if the descriptor cannot be created or accessed
   */
  ImageDescriptor getImageDescriptor();

  /**
   * Retrieves the image descriptor as an Optional.
   *
   * <p>This method provides a null-safe way to access the image descriptor, avoiding potential
   * NullPointerException when the descriptor is not available.
   *
   * @return an Optional containing the image descriptor, or empty if not available
   * @since 5.1
   */
  default Optional<ImageDescriptor> getImageDescriptorOptional() {
    return Optional.ofNullable(getImageDescriptor());
  }

  /**
   * Checks if an image descriptor is available.
   *
   * @return {@code true} if an image descriptor can be provided, {@code false} otherwise
   * @since 5.1
   */
  default boolean hasImageDescriptor() {
    return getImageDescriptor() != null;
  }
}
