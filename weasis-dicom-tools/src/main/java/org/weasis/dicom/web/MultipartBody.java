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
import java.io.InputStream;
import java.io.SequenceInputStream;
import java.net.http.HttpRequest;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.concurrent.Flow;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Builder for multipart/related HTTP request bodies. Supports both streaming and buffered
 * approaches for different payload types.
 */
public final class MultipartBody implements Flow.Subscriber<ByteBuffer> {

  private static final Logger LOGGER = LoggerFactory.getLogger(MultipartBody.class);

  private final String boundary;

  private final ContentType contentType;
  private final List<MultipartPart> parts = new ArrayList<>();
  private final AtomicBoolean closed = new AtomicBoolean(false);

  private Flow.Subscription subscription;

  /**
   * Creates a multipart body with the specified content type and boundary.
   *
   * @param contentType the primary content type for the multipart
   * @param boundary the boundary string (without dashes)
   */
  public MultipartBody(ContentType contentType, String boundary) {
    this.contentType = Objects.requireNonNull(contentType, "Content type cannot be null");
    this.boundary = Objects.requireNonNull(boundary, "Boundary cannot be null");
  }

  /** Adds a part with byte array payload. */
  public MultipartBody addPart(String contentType, byte[] data, String location) {
    Objects.requireNonNull(data, "Data cannot be null");
    return addPart(contentType, Payload.ofBytes(data), location);
  }

  /** Adds a part with file payload. */
  public MultipartBody addPart(String contentType, Path path, String location) {
    Objects.requireNonNull(path, "Path cannot be null");
    return addPart(contentType, Payload.ofPath(path), location);
  }

  /** Adds a part with custom payload. */
  public MultipartBody addPart(String contentType, Payload payload, String location) {
    Objects.requireNonNull(contentType, "Content type cannot be null");
    Objects.requireNonNull(payload, "Payload cannot be null");

    if (closed.get()) {
      throw new IllegalStateException("MultipartBody is closed");
    }

    parts.add(new MultipartPart(contentType, location, payload));
    return this;
  }

  /** Creates an HTTP body publisher using a stream supplier. */
  public HttpRequest.BodyPublisher createBodyPublisher(
      Supplier<? extends InputStream> streamSupplier) {
    Objects.requireNonNull(streamSupplier, "Stream supplier cannot be null");
    return HttpRequest.BodyPublishers.ofInputStream(streamSupplier);
  }

  /** Creates an HTTP body publisher using internal stream enumeration. */
  public HttpRequest.BodyPublisher createBodyPublisher() {
    return HttpRequest.BodyPublishers.ofInputStream(
        () -> new SequenceInputStream(createStreamEnumeration()));
  }

  /** Returns the complete Content-Type header value for this multipart body. */
  public String getContentTypeHeader() {
    return contentType.toMultipartContentType(boundary);
  }

  /** Returns the boundary string. */
  public String getBoundary() {
    return boundary;
  }

  /** Returns an unmodifiable view of the parts. */
  public List<MultipartPart> getParts() {
    return Collections.unmodifiableList(parts);
  }

  /** Resets the body for reuse by clearing all parts. */
  public void reset() {
    parts.clear();
    closed.set(false);
  }

  /** Logs debug information about all parts. */
  public void logDebugInfo() {
    if (LOGGER.isDebugEnabled()) {
      parts.forEach(part -> part.logDebugInfo(boundary));
      LOGGER.debug("> --{}--", boundary);
      LOGGER.debug(">");
    }
  }

  /** Creates stream enumeration for all parts and closing delimiter. */
  private Enumeration<InputStream> createStreamEnumeration() {
    return new MultipartStreamEnumeration();
  }

  private String createClosingDelimiter() {
    return "\r\n--" + boundary + "--";
  }

  private InputStream createPartHeaderStream(MultipartPart part) {
    if (closed.get()) {
      return InputStream.nullInputStream();
    }
    return new ByteArrayInputStream(part.generateHeader(boundary).getBytes(StandardCharsets.UTF_8));
  }

  private InputStream createClosingStream() {
    if (closed.getAndSet(true)) {
      return InputStream.nullInputStream();
    }

    return new ByteArrayInputStream(createClosingDelimiter().getBytes(StandardCharsets.UTF_8));
  }

  // Flow.Subscriber implementation for reactive streams

  @Override
  public void onSubscribe(Flow.Subscription subscription) {
    this.subscription = subscription;
    subscription.request(1);
  }

  @Override
  public void onNext(ByteBuffer item) {
    if (subscription != null) {
      subscription.request(1);
    }
  }

  @Override
  public void onError(Throwable throwable) {
    LOGGER.error("Error in multipart body flow", throwable);
  }

  @Override
  public void onComplete() {
    LOGGER.debug("Multipart body flow completed");
    closed.set(true);
  }

  /** Enumeration that provides streams for each part header, data, and final delimiter. */
  private class MultipartStreamEnumeration implements Enumeration<InputStream> {
    private final Iterator<MultipartPart> partIterator = parts.iterator();
    private MultipartPart currentPart;
    private boolean streamClosed = false;
    private boolean needsPartData = false;

    @Override
    public boolean hasMoreElements() {
      return !streamClosed;
    }

    @Override
    public InputStream nextElement() {
      if (streamClosed) {
        throw new NoSuchElementException("No more streams available");
      }

      if (needsPartData) {
        needsPartData = false;
        return currentPart.newInputStream();
      }

      if (partIterator.hasNext()) {
        currentPart = partIterator.next();
        needsPartData = true;
        return createPartHeaderStream(currentPart);
      }

      streamClosed = true;
      return createClosingStream();
    }
  }
}
