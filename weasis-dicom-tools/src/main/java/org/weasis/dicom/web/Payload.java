/*
 * Copyright (c) 2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.web;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

/**
 * Represents payload data with size information and input stream access. Provides factory methods
 * for common payload types.
 */
public interface Payload {
  /**
   * Returns the size of the payload in bytes.
   *
   * @return size in bytes, or -1 if unknown
   */
  long size();

  /**
   * Creates a new input stream for reading the payload data. Each call should return a fresh stream
   * positioned at the beginning.
   *
   * @return a new input stream for the payload
   */
  InputStream newInputStream();

  /**
   * Creates a payload from a byte array.
   *
   * @param data the byte data
   * @return a new payload instance
   * @throws NullPointerException if data is null
   */
  static Payload ofBytes(byte[] data) {
    Objects.requireNonNull(data, "Data cannot be null");
    return new ByteArrayPayload(data);
  }

  /**
   * Creates a payload from a file path.
   *
   * @param path the file path
   * @return a new payload instance
   * @throws NullPointerException if path is null
   */
  static Payload ofPath(Path path) {
    Objects.requireNonNull(path, "Path cannot be null");
    return new FilePayload(path);
  }

  /**
   * Creates an empty payload.
   *
   * @return an empty payload instance
   */
  static Payload empty() {
    return EmptyPayload.INSTANCE;
  }

  /** Payload implementation for byte arrays. */
  record ByteArrayPayload(byte[] data) implements Payload { // NOSONAR only internal use

    public ByteArrayPayload {
      Objects.requireNonNull(data, "Data cannot be null");
    }

    @Override
    public long size() {
      return data.length;
    }

    @Override
    public InputStream newInputStream() {
      return new ByteArrayInputStream(data);
    }
  }

  /** Payload implementation for files. */
  record FilePayload(Path path) implements Payload {

    public FilePayload {
      Objects.requireNonNull(path, "Path cannot be null");
    }

    @Override
    public long size() {
      try {
        return Files.size(path);
      } catch (IOException e) {
        throw new UncheckedIOException(e);
      }
    }

    @Override
    public InputStream newInputStream() {
      try {
        return Files.newInputStream(path);
      } catch (IOException e) {
        throw new UncheckedIOException(e);
      }
    }
  }

  /** Empty payload implementation. */
  enum EmptyPayload implements Payload {
    INSTANCE;

    @Override
    public long size() {
      return 0;
    }

    @Override
    public InputStream newInputStream() {
      return InputStream.nullInputStream();
    }
  }
}
