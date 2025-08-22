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
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import org.dcm4che3.img.DicomMetaData;
import org.dcm4che3.io.DicomInputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.util.StringUtil;

/**
 * A specialized {@link DicomInputStream} that reads DICOM data from a file path.
 *
 * <p>This class extends the standard DICOM input stream functionality by providing file-based
 * access and caching of metadata for improved performance. It implements the {@link
 * ImageReaderDescriptor} interface to provide image descriptor information.
 *
 * <p><b>Thread Safety:</b> This class is not thread-safe. External synchronization is required if
 * instances are accessed concurrently.
 *
 * @author Nicolas Roduit
 */
public class DicomFileInputStream extends DicomInputStream implements ImageReaderDescriptor {
  private static final Logger LOGGER = LoggerFactory.getLogger(DicomFileInputStream.class);

  private final Path path;
  private volatile DicomMetaData metadata;

  /**
   * Constructs a new DICOM file input stream from the specified file path.
   *
   * @param path the path to the DICOM file, must not be null and must exist
   * @throws IOException if an I/O error occurs while opening the file
   * @throws IllegalArgumentException if the path is null
   */
  public DicomFileInputStream(Path path) throws IOException {
    super(Files.newInputStream(Objects.requireNonNull(path, "Path cannot be null")));
    this.path = path;
  }

  /**
   * Constructs a new DICOM file input stream from the specified file path string.
   *
   * @param pathString the string representation of the path to the DICOM file, must not be null
   * @throws IOException if an I/O error occurs while opening the file
   * @throws IllegalArgumentException if the path is null or empty
   */
  public DicomFileInputStream(String pathString) throws IOException {
    this(createPath(pathString));
  }

  /**
   * Returns the file path associated with this input stream.
   *
   * @return the file path, never null
   */
  public Path getPath() {
    return path;
  }

  /**
   * Returns the DICOM metadata for this file, creating and caching it if necessary.
   *
   * <p>The metadata is lazily loaded and cached for subsequent calls using double-checked locking.
   *
   * @return the DICOM metadata, never null
   * @throws IOException if an I/O error occurs while reading the metadata
   */
  public DicomMetaData getMetadata() throws IOException {
    var result = metadata;
    if (result == null) {
      synchronized (this) {
        result = metadata;
        if (result == null) {
          result = new DicomMetaData(this);
          metadata = result;
        }
      }
    }
    return result;
  }

  /**
   * Returns the image descriptor for this DICOM file.
   *
   * <p>If metadata cannot be read due to I/O errors, null is returned.
   *
   * @return the image descriptor, or null if metadata cannot be read
   */
  @Override
  public ImageDescriptor getImageDescriptor() {
    try {
      return getMetadata().getImageDescriptor();
    } catch (IOException e) {
      LOGGER.error("Error reading image descriptor from path: {}", path, e);
      return null;
    }
  }

  private static Path createPath(String pathString) {
    if (!StringUtil.hasText(pathString)) {
      throw new IllegalArgumentException("Path string cannot be null or empty");
    }
    return Path.of(pathString);
  }

  @Override
  public String toString() {
    return "DicomFileInputStream{path=" + path + "}";
  }
}
