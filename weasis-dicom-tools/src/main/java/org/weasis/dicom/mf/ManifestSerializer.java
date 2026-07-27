/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.mf;

import java.io.IOException;

/**
 * Format-agnostic writer for a manifest document. The manifest tree is walked once and emitted
 * through this interface, so a single traversal can produce either XML or JSON (see {@link
 * XmlManifestSerializer} and {@link JsonManifestSerializer}).
 *
 * <p>Contract: {@code beginDocument} first, then a single root object. Inside an object, emit
 * {@link #attribute(String, String) attributes} before any nested object, array or leaf. Repeated
 * siblings (studies, series, instances, http tags) are wrapped in an {@link #beginArray(String)
 * array} whose items are {@link #beginObject(String) objects} (or {@link #beginLeaf(String)
 * leaves}); in XML the array is transparent and each item repeats the element, in JSON it becomes a
 * named array.
 *
 * @since 5.34.3
 */
public interface ManifestSerializer {

  /** Opens the document (XML prolog / JSON root wrapper). */
  void beginDocument() throws IOException;

  /** Closes the document. */
  void endDocument() throws IOException;

  /** Starts an element that may contain attributes and children. */
  void beginObject(String name) throws IOException;

  /** Ends the current object opened by {@link #beginObject(String)}. */
  void endObject() throws IOException;

  /** Starts a childless element (rendered self-closing in XML). */
  void beginLeaf(String name) throws IOException;

  /** Ends the current leaf opened by {@link #beginLeaf(String)}. */
  void endLeaf() throws IOException;

  /** Writes an attribute on the current object; ignored when {@code value} is null or blank. */
  void attribute(String name, String value) throws IOException;

  /**
   * Writes a boolean attribute on the current object. Rendered as a JSON boolean, or as the string
   * {@code "true"}/{@code "false"} in XML.
   */
  void attribute(String name, boolean value) throws IOException;

  /** Emits an XML namespace declaration; a no-op for formats without namespaces. */
  void schema(String namespace) throws IOException;

  /** Starts a named collection of sibling items. */
  void beginArray(String name) throws IOException;

  /** Ends the current array opened by {@link #beginArray(String)}. */
  void endArray() throws IOException;
}
