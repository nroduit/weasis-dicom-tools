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
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.Deque;
import org.weasis.core.util.EscapeChars;
import org.weasis.core.util.StringUtil;

/**
 * Emits a manifest as XML, reproducing the historical element/attribute layout. Arrays are
 * transparent: each item simply repeats its element within the parent.
 */
public class XmlManifestSerializer implements ManifestSerializer {

  private final Writer writer;
  private final String charsetEncoding;
  private final Deque<Element> stack = new ArrayDeque<>();

  public XmlManifestSerializer(Writer writer) {
    this(writer, StandardCharsets.UTF_8.name());
  }

  public XmlManifestSerializer(Writer writer, String charsetEncoding) {
    this.writer = writer;
    this.charsetEncoding = charsetEncoding;
  }

  @Override
  public void beginDocument() throws IOException {
    writer.append("<?xml version=\"1.0\" encoding=\"").append(charsetEncoding).append("\" ?>");
  }

  @Override
  public void endDocument() throws IOException {
    writer.append("\n");
  }

  @Override
  public void beginObject(String name) throws IOException {
    startElement(name);
  }

  @Override
  public void beginLeaf(String name) throws IOException {
    startElement(name);
  }

  @Override
  public void attribute(String name, String value) throws IOException {
    if (StringUtil.hasText(name) && StringUtil.hasText(value)) {
      writer.append(name).append("=\"").append(EscapeChars.forXML(value)).append("\" ");
    }
  }

  @Override
  public void attribute(String name, boolean value) throws IOException {
    if (StringUtil.hasText(name)) {
      writer.append(name).append("=\"").append(Boolean.toString(value)).append("\" ");
    }
  }

  @Override
  public void schema(String namespace) throws IOException {
    if (StringUtil.hasText(namespace)) {
      writer.append(namespace);
    }
  }

  @Override
  public void beginArray(String name) {
    // Transparent in XML: items repeat their own element.
  }

  @Override
  public void endArray() {
    // Transparent in XML.
  }

  @Override
  public void endObject() throws IOException {
    Element element = stack.pop();
    if (element.startTagOpen) {
      writer.append(">");
    }
    writer.append("\n</").append(element.name).append(">");
  }

  @Override
  public void endLeaf() throws IOException {
    stack.pop();
    writer.append("/>");
  }

  private void startElement(String name) throws IOException {
    closeParentStartTag();
    writer.append("\n<").append(name).append(" ");
    stack.push(new Element(name));
  }

  private void closeParentStartTag() throws IOException {
    Element parent = stack.peek();
    if (parent != null && parent.startTagOpen) {
      writer.append(">");
      parent.startTagOpen = false;
    }
  }

  private static final class Element {
    private final String name;
    private boolean startTagOpen = true;

    private Element(String name) {
      this.name = name;
    }
  }
}
