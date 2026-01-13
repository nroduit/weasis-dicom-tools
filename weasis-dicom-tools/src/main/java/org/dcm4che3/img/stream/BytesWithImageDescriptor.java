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

import java.io.IOException;
import java.nio.ByteBuffer;
import org.dcm4che3.data.VR;
import org.weasis.core.util.annotations.Generated;

/**
 * Interface for accessing DICOM image data as raw bytes along with comprehensive image metadata.
 *
 * <p>This interface extends {@link ImageReaderDescriptor} to provide direct access to pixel data
 * bytes while maintaining all image descriptor functionality. It is designed for scenarios where
 * raw pixel data access is required alongside metadata inspection.
 *
 * <p><strong>Key Features:</strong>
 *
 * <ul>
 *   <li>Frame-based pixel data access via {@link ByteBuffer}
 *   <li>Transfer syntax information for proper data interpretation
 *   <li>Pixel data value representation (VR) details
 *   <li>Palette color lookup table support
 *   <li>Endianness and data type detection
 * </ul>
 *
 * <p><strong>Usage Example:</strong>
 *
 * <pre>{@code
 * BytesWithImageDescriptor descriptor = ...;
 * ByteBuffer frameData = descriptor.getBytes(0); // Get first frame
 * String transferSyntax = descriptor.getTransferSyntax();
 * boolean isFloatingPoint = descriptor.isFloatPixelData();
 * }</pre>
 *
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @author Nicolas Roduit
 * @since Mar 2018
 */
public interface BytesWithImageDescriptor extends ImageReaderDescriptor {
  /**
   * Retrieves the raw pixel data for the specified frame.
   *
   * <p>The returned {@link ByteBuffer} contains the pixel data in the format specified by the
   * transfer syntax and pixel data VR. The buffer position is set to the beginning of the frame
   * data, and the limit is set to the end of the frame.
   *
   * @param frame the zero-based frame index to retrieve
   * @return a ByteBuffer containing the frame's pixel data, positioned at the start of the data
   * @throws IOException if an I/O error occurs while reading the pixel data
   * @throws IllegalArgumentException if the frame index is negative or exceeds the number of
   *     available frames
   */
  ByteBuffer getBytes(int frame) throws IOException;

  /**
   * Returns the DICOM transfer syntax UID used for encoding the pixel data.
   *
   * @return the transfer syntax UID, never null
   * @throws IllegalStateException if the transfer syntax cannot be determined
   */
  String getTransferSyntax();

  /**
   * Indicates whether the pixel data uses big-endian byte ordering.
   *
   * <p>Most DICOM images use little-endian encoding. This method returns true only for transfer
   * syntaxes that explicitly specify big-endian encoding.
   *
   * @return true if pixel data is big-endian, false for little-endian (default)
   */
  @Generated
  default boolean isBigEndian() {
    return false;
  }

  /**
   * Indicates whether the pixel data represents floating-point values.
   *
   * @return true if pixel data contains floating-point values, false for integer values (default)
   */
  default boolean isFloatPixelData() {
    return false;
  }

  /**
   * Returns the Value Representation (VR) of the pixel data element.
   *
   * @return the pixel data VR, never null
   * @throws IllegalStateException if the VR cannot be determined
   */
  VR getPixelDataVR();

  /**
   * @deprecated Use {@link #isBigEndian()} instead. This method will be removed in a future
   *     version.
   */
  @Deprecated(since = "5.34.3", forRemoval = true)
  @Generated
  default boolean bigEndian() {
    return isBigEndian();
  }

  /**
   * @deprecated Use {@link #isFloatPixelData()} instead. This method will be removed in a future
   *     version.
   */
  @Deprecated(since = "5.34.3", forRemoval = true)
  @Generated
  default boolean floatPixelData() {
    return isFloatPixelData();
  }
}
