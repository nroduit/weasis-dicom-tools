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
import java.nio.channels.ClosedChannelException;
import java.nio.channels.SeekableByteChannel;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * A {@link SeekableByteChannel} implementation backed by an in-memory byte array.
 *
 * <p>This implementation provides a seekable byte channel that stores all data in memory. When used
 * for writing, the internal buffer automatically grows to accommodate incoming data. The maximum
 * supported size is {@link Integer#MAX_VALUE} bytes.
 *
 * <p><strong>Thread Safety:</strong> This class is not thread-safe and should not be used
 * concurrently from multiple threads without external synchronization.
 *
 * <p><strong>Contract Violations:</strong> This implementation intentionally violates some {@link
 * SeekableByteChannel} contracts for performance reasons:
 *
 * <ul>
 *   <li>{@link #position()} and {@link #size()} do not throw exceptions on closed channels
 *   <li>{@link #truncate(long)} does not throw exceptions on closed channels
 * </ul>
 */
public class SeekableInMemoryByteChannel implements SeekableByteChannel {

  /** Threshold for switching from exponential to linear growth strategy */
  private static final int EXPONENTIAL_GROWTH_LIMIT = Integer.MAX_VALUE >> 1;

  /** Initial buffer size for empty constructor */
  private static final int INITIAL_CAPACITY = 32;

  private byte[] data;
  private final AtomicBoolean closed = new AtomicBoolean(false);
  private int position;
  private int size;

  /**
   * Creates a channel backed by the provided byte array.
   *
   * <p>The initial size and capacity are set to the array length. The array is used directly (not
   * copied) and may be modified by write operations.
   *
   * @param data the backing byte array, must not be null
   * @throws IllegalArgumentException if data is null
   */
  public SeekableInMemoryByteChannel(byte[] data) {
    if (data == null) {
      throw new IllegalArgumentException("Data array cannot be null");
    }
    this.data = data;
    this.size = data.length;
    this.position = 0;
  }

  /** Creates an empty channel with default initial capacity. */
  public SeekableInMemoryByteChannel() {
    this(INITIAL_CAPACITY);
  }

  /**
   * Creates a channel with the specified initial capacity.
   *
   * @param initialCapacity the initial buffer capacity in bytes
   * @throws IllegalArgumentException if initialCapacity is negative
   */
  public SeekableInMemoryByteChannel(int initialCapacity) {
    if (initialCapacity < 0) {
      throw new IllegalArgumentException("Initial capacity cannot be negative: " + initialCapacity);
    }
    this.data = new byte[initialCapacity];
    this.size = 0;
    this.position = 0;
  }

  /**
   * Returns the backing byte array.
   *
   * <p><strong>Warning:</strong> Direct modification of the returned array may lead to inconsistent
   * channel state.
   *
   * @return the internal byte array
   */
  public byte[] array() {
    return data;
  }

  @Override
  public long position() {
    return position;
  }

  @Override
  public SeekableByteChannel position(long newPosition) throws IOException {
    ensureOpen();
    validatePosition(newPosition);
    this.position = (int) newPosition;
    return this;
  }

  @Override
  public long size() {
    return size;
  }

  @Override
  public SeekableByteChannel truncate(long newSize) {
    validateSize(newSize);

    int truncatedSize = (int) newSize;
    if (size > truncatedSize) {
      size = truncatedSize;
    }
    if (position > truncatedSize) {
      position = truncatedSize;
    }
    return this;
  }

  @Override
  public int read(ByteBuffer buffer) throws IOException {
    ensureOpen();
    int bytesToRead = Math.min(buffer.remaining(), size - position);
    if (bytesToRead <= 0) {
      return -1; // EOF
    }
    buffer.put(data, position, bytesToRead);
    position += bytesToRead;
    return bytesToRead;
  }

  @Override
  public int write(ByteBuffer buffer) throws IOException {
    ensureOpen();

    int bytesToWrite = buffer.remaining();
    int requiredCapacity = position + bytesToWrite;

    // Handle potential overflow
    if (requiredCapacity < 0) {
      bytesToWrite = Integer.MAX_VALUE - position;
      requiredCapacity = Integer.MAX_VALUE;
    }

    ensureCapacity(requiredCapacity);

    buffer.get(data, position, bytesToWrite);
    position += bytesToWrite;
    size = Math.max(size, position);

    return bytesToWrite;
  }

  @Override
  public boolean isOpen() {
    return !closed.get();
  }

  @Override
  public void close() {
    closed.set(true);
  }

  /** Validates that the position is within acceptable bounds */
  private void validatePosition(long position) {
    if (position < 0L || position > Integer.MAX_VALUE) {
      throw new IllegalArgumentException(
          String.format("Position must be in range [0, %d], got: %d", Integer.MAX_VALUE, position));
    }
  }

  /** Validates that the size is within acceptable bounds */
  private void validateSize(long size) {
    if (size < 0L || size > Integer.MAX_VALUE) {
      throw new IllegalArgumentException(
          String.format("Size must be in range [0, %d], got: %d", Integer.MAX_VALUE, size));
    }
  }

  /** Ensures the channel is open, throwing ClosedChannelException if not */
  private void ensureOpen() throws ClosedChannelException {
    if (!isOpen()) {
      throw new ClosedChannelException();
    }
  }

  /** Ensures the internal buffer has at least the specified capacity */
  private void ensureCapacity(int requiredCapacity) {
    if (requiredCapacity <= data.length) {
      return;
    }

    int newCapacity = calculateNewCapacity(data.length, requiredCapacity);
    data = Arrays.copyOf(data, newCapacity);
  }

  /** Calculates optimal new capacity using exponential growth up to a limit, then linear */
  private int calculateNewCapacity(int currentCapacity, int requiredCapacity) {
    int newCapacity = Math.max(currentCapacity, 1);

    if (requiredCapacity < EXPONENTIAL_GROWTH_LIMIT) {
      // Use exponential growth for smaller sizes
      while (newCapacity < requiredCapacity) {
        newCapacity <<= 1;
      }
    } else {
      // Use exact size for large allocations to avoid waste
      newCapacity = requiredCapacity;
    }

    return newCapacity;
  }
}
