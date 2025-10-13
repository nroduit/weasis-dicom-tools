/*
 * Copyright (c) 2019-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.web;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * High-performance multipart stream reader for processing multipart/related HTTP content. Provides
 * boundary detection, header parsing, and streaming access to individual parts.
 */
public final class MultipartReader implements AutoCloseable {
  private static final Logger LOGGER = LoggerFactory.getLogger(MultipartReader.class);

  /** Maximum size for multipart headers (16KB) */
  public static final int MAX_HEADER_SIZE = 16_384;

  private static final int DEFAULT_BUFFER_SIZE = 4_096;

  private static final int MIN_BUFFER_SIZE = 256;
  private static final int MAX_BUFFER_SIZE = 1_048_576; // 1MB

  private final InputStream inputStream;
  private final byte[] boundary;
  private final StreamBuffer buffer;
  private Charset headerEncoding = StandardCharsets.UTF_8;
  private int currentBoundaryLength;

  /** Creates a multipart reader with default buffer size. */
  public MultipartReader(InputStream inputStream, byte[] boundary) {
    this(inputStream, boundary, DEFAULT_BUFFER_SIZE);
  }

  /** Creates a multipart reader with specified buffer size. */
  public MultipartReader(InputStream inputStream, byte[] boundary, int bufferSize) {
    this.inputStream = Objects.requireNonNull(inputStream, "Input stream cannot be null");
    Objects.requireNonNull(boundary, "Boundary cannot be null");
    validateBufferSize(bufferSize);
    this.buffer = new StreamBuffer(inputStream, bufferSize);
    this.boundary = createBoundaryWithPrefix(boundary);
    this.currentBoundaryLength = this.boundary.length;
  }

  public Charset getHeaderEncoding() {
    return headerEncoding;
  }

  public void setHeaderEncoding(String encoding) {
    try {
      this.headerEncoding = encoding != null ? Charset.forName(encoding) : StandardCharsets.UTF_8;
    } catch (IllegalArgumentException e) {
      LOGGER.warn("Invalid encoding '{}', using UTF-8", encoding);
      this.headerEncoding = StandardCharsets.UTF_8;
    }
  }

  /** Skips the first boundary which has no CRLF prefix. */
  public boolean skipFirstBoundary() throws IOException {
    return processBoundary(true);
  }

  /** Reads the next boundary and determines if more parts follow. */
  public boolean readBoundary() throws IOException {
    return processBoundary(false);
  }

  /** Reads headers for the current part. */
  public String readHeaders() throws IOException {
    try (var output = new ByteArrayOutputStream()) {
      readHeadersToStream(output);
      return output.toString(headerEncoding);
    }
  }

  /** Creates a new input stream for reading the current part's data. */
  public PartInputStream newPartInputStream() {
    return new PartInputStream();
  }

  @Override
  public void close() throws IOException {
    inputStream.close();
  }

  private void validateBufferSize(int size) {
    if (size < MIN_BUFFER_SIZE || size > MAX_BUFFER_SIZE) {
      throw new IllegalArgumentException(
          "Buffer size must be between %d and %d bytes"
              .formatted(MIN_BUFFER_SIZE, MAX_BUFFER_SIZE));
    }
  }

  private byte[] createBoundaryWithPrefix(byte[] boundary) {
    var prefix = MultipartConstants.Separator.BOUNDARY.getBytes();
    var fullBoundary = new byte[boundary.length + prefix.length];
    System.arraycopy(prefix, 0, fullBoundary, 0, prefix.length);
    System.arraycopy(boundary, 0, fullBoundary, prefix.length, boundary.length);
    return fullBoundary;
  }

  private boolean processBoundary(boolean isFirst) throws IOException {
    var originalBoundary = boundary.clone();
    int originalLength = currentBoundaryLength;
    try {
      if (isFirst) {
        adjustBoundaryForFirst();
        discardDataUntilBoundary();
      } else {
        buffer.skip(currentBoundaryLength);
      }

      return readBoundaryTerminator();
    } finally {
      if (isFirst) {
        restoreBoundary(originalBoundary, originalLength);
      }
    }
  }

  private void adjustBoundaryForFirst() {
    System.arraycopy(boundary, 2, boundary, 0, boundary.length - 2);
    currentBoundaryLength -= 2;
  }

  private void restoreBoundary(byte[] original, int originalLength) {
    System.arraycopy(original, 0, boundary, 0, original.length);
    currentBoundaryLength = originalLength;
  }

  private boolean readBoundaryTerminator() throws IOException {
    byte[] terminator = {buffer.readByte(), buffer.readByte()};

    if (Arrays.equals(terminator, MultipartConstants.Separator.STREAM.getBytes())) {
      return false; // End of stream
    } else if (Arrays.equals(terminator, MultipartConstants.Separator.FIELD.getBytes())) {
      return true; // More parts follow
    } else {
      throw new MultipartStreamException(
          "Invalid boundary terminator: " + Arrays.toString(terminator));
    }
  }

  private void readHeadersToStream(ByteArrayOutputStream output) throws IOException {
    var separator = MultipartConstants.Separator.HEADER.getBytes();
    int matchCount = 0;
    int totalBytes = 0;

    while (matchCount < separator.length) {
      byte currentByte = buffer.readByte();

      if (++totalBytes > MAX_HEADER_SIZE) {
        throw MultipartStreamException.headerSizeExceeded(totalBytes, MAX_HEADER_SIZE);
      }

      matchCount = (currentByte == separator[matchCount]) ? matchCount + 1 : 0;
      output.write(currentByte);
    }
  }

  private void discardDataUntilBoundary() throws IOException {
    try (var partStream = newPartInputStream()) {
      partStream.transferTo(OutputStream.nullOutputStream());
    }
  }

  /** Input stream for reading individual multipart sections. */
  public final class PartInputStream extends InputStream {

    private final BoundaryDetector detector;
    private boolean closed = false;

    PartInputStream() {
      this.detector = new BoundaryDetector();
    }

    @Override
    public int read() throws IOException {
      validateNotClosed();
      return detector.hasReachedBoundary() ? -1 : buffer.readByte() & 0xFF;
    }

    @Override
    public int read(byte[] b, int off, int len) throws IOException {
      validateNotClosed();
      Objects.checkFromIndexSize(off, len, b.length);
      if (len == 0) return 0;
      if (detector.hasReachedBoundary()) return -1;

      int bytesToRead = Math.min(len, detector.getAvailableBytes());
      if (bytesToRead <= 0) {
        return read() == -1 ? -1 : 1; // Fallback to single byte read
      }

      buffer.readBytes(b, off, bytesToRead);
      detector.updatePosition(bytesToRead);

      return bytesToRead;
    }

    @Override
    public int available() throws IOException {
      validateNotClosed();

      return detector.hasReachedBoundary() ? 0 : detector.getAvailableBytes();
    }

    @Override
    public long skip(long n) throws IOException {
      validateNotClosed();

      if (n <= 0 || detector.hasReachedBoundary()) return 0;

      int available = detector.getAvailableBytes();
      long toSkip = Math.min(n, available);
      buffer.skip((int) toSkip);
      detector.updatePosition((int) toSkip);
      return toSkip;
    }

    @Override
    public void close() throws IOException {
      if (!closed) {

        // Consume remaining data in this part
        while (!detector.hasReachedBoundary() && buffer.readByte() != -1) {
          // Continue reading until boundary
        }
        closed = true;
      }
    }

    public boolean isClosed() {
      return closed;
    }

    private void validateNotClosed() throws IOException {
      if (closed) {
        throw new IOException("PartInputStream is closed");
      }
    }
  }

  /** Detects boundaries in the stream and manages available byte counting. */
  private class BoundaryDetector {
    private int boundaryPosition = -1;
    private boolean boundaryFound = false;

    BoundaryDetector() {
      updateBoundaryPosition();
    }

    boolean hasReachedBoundary() {
      return boundaryFound || (boundaryPosition != -1 && buffer.getPosition() >= boundaryPosition);
    }

    int getAvailableBytes() {
      if (boundaryPosition == -1) {
        return buffer.available() - currentBoundaryLength;
      }
      return Math.max(0, boundaryPosition - buffer.getPosition());
    }

    void updatePosition(int bytesProcessed) {
      // Recalculate boundary position after reading data
      updateBoundaryPosition();
    }

    private void updateBoundaryPosition() {
      boundaryPosition = buffer.findPattern(boundary);
      if (boundaryPosition == -1) {
        // Ensure we don't read past potential boundary bytes at buffer end
        int safeBytes = Math.max(0, buffer.available() - currentBoundaryLength);
        boundaryPosition = buffer.getPosition() + safeBytes;
      }
    }
  }

  /** Buffered stream wrapper with pattern matching capabilities. */
  private static class StreamBuffer {
    private final InputStream inputStream;
    private final byte[] buffer;
    private final int bufferSize;
    private int position = 0;
    private int limit = 0;

    StreamBuffer(InputStream inputStream, int bufferSize) {
      this.inputStream = inputStream;
      this.bufferSize = bufferSize;
      this.buffer = new byte[bufferSize];
    }

    byte readByte() throws IOException {
      if (position >= limit) {
        fillBuffer();
      }
      return buffer[position++];
    }

    void readBytes(byte[] dest, int offset, int length) throws IOException {
      int remaining = length;
      int destOffset = offset;

      while (remaining > 0) {
        if (position >= limit) {
          fillBuffer();
        }

        int available = Math.min(remaining, limit - position);
        System.arraycopy(buffer, position, dest, destOffset, available);
        position += available;
        destOffset += available;
        remaining -= available;
      }
    }

    void skip(int bytes) throws IOException {
      int remaining = bytes;

      while (remaining > 0) {
        if (position >= limit) {
          fillBuffer();
        }

        int available = Math.min(remaining, limit - position);
        position += available;
        remaining -= available;
      }
    }

    int available() {
      return limit - position;
    }

    int getPosition() {
      return position;
    }

    int findPattern(byte[] pattern) {
      // Simple pattern matching in current buffer
      for (int i = position; i <= limit - pattern.length; i++) {
        if (matchesPattern(i, pattern)) {
          return i;
        }
      }
      return -1;
    }

    private boolean matchesPattern(int start, byte[] pattern) {
      for (int i = 0; i < pattern.length; i++) {
        if (buffer[start + i] != pattern[i]) {
          return false;
        }
      }
      return true;
    }

    private void fillBuffer() throws IOException {
      if (position < limit) {
        // Compact buffer by moving unused data to beginning
        int remaining = limit - position;
        System.arraycopy(buffer, position, buffer, 0, remaining);
        limit = remaining;
        position = 0;
      } else {
        limit = 0;
        position = 0;
      }

      int bytesRead = inputStream.read(buffer, limit, bufferSize - limit);
      if (bytesRead == -1) {
        throw MultipartStreamException.unexpectedEndOfStream();
      }

      limit += bytesRead;
    }
  }
}
