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

import jakarta.json.Json;
import jakarta.json.stream.JsonGenerator;
import java.io.Writer;
import java.util.ArrayDeque;
import java.util.Deque;
import org.weasis.core.util.StringUtil;

/**
 * Emits a manifest as JSON, mirroring the XML element and attribute names. Objects become JSON
 * objects, arrays become named JSON arrays whose items are anonymous objects. The whole document is
 * wrapped in a root object (e.g. {@code {"manifest": { ... }}}).
 */
public class JsonManifestSerializer implements ManifestSerializer {

  private final JsonGenerator generator;
  // true when the enclosing container is an array (its items are anonymous)
  private final Deque<Boolean> arrayContext = new ArrayDeque<>();

  public JsonManifestSerializer(Writer writer) {
    this.generator = Json.createGenerator(writer);
  }

  @Override
  public void beginDocument() {
    generator.writeStartObject();
    arrayContext.push(Boolean.FALSE);
  }

  @Override
  public void endDocument() {
    generator.writeEnd();
    arrayContext.pop();
    generator.flush();
  }

  @Override
  public void beginObject(String name) {
    startObject(name);
  }

  @Override
  public void beginLeaf(String name) {
    startObject(name);
  }

  @Override
  public void attribute(String name, String value) {
    if (StringUtil.hasText(name) && StringUtil.hasText(value)) {
      generator.write(name, value);
    }
  }

  @Override
  public void attribute(String name, boolean value) {
    if (StringUtil.hasText(name)) {
      generator.write(name, value);
    }
  }

  @Override
  public void schema(String namespace) {
    // No namespaces in JSON.
  }

  @Override
  public void beginArray(String name) {
    generator.writeStartArray(name);
    arrayContext.push(Boolean.TRUE);
  }

  @Override
  public void endArray() {
    endElement();
  }

  @Override
  public void endObject() {
    endElement();
  }

  private void endElement() {
    generator.writeEnd();
    arrayContext.pop();
  }

  @Override
  public void endLeaf() {
    endObject();
  }

  private void startObject(String name) {
    if (Boolean.TRUE.equals(arrayContext.peek())) {
      generator.writeStartObject();
    } else {
      generator.writeStartObject(name);
    }
    arrayContext.push(Boolean.FALSE);
  }
}
