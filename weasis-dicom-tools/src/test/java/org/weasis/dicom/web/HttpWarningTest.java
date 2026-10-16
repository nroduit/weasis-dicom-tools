/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;

@DisplayNameGeneration(ReplaceUnderscores.class)
class HttpWarningTest {

  @Test
  void parses_quoted_text() {
    assertEquals(
        List.of(
            new HttpWarning(
                299,
                "pacs.example:8080",
                "The fuzzymatching parameter is not supported. Only literal matching has been"
                    + " performed.")),
        HttpWarning.parse(
            "299 pacs.example:8080 \"The fuzzymatching parameter is not supported. Only literal"
                + " matching has been performed.\""));
  }

  @Test
  void parses_comma_joined_values_with_escapes_and_dates() {
    List<HttpWarning> warnings =
        HttpWarning.parse(
            "299 pacs \"a, \\\"quoted\\\" text\" \"Fri, 16 Oct 2026 08:00:00 GMT\", 299 - \"b\"");

    assertEquals(
        List.of(new HttpWarning(299, "pacs", "a, \"quoted\" text"), new HttpWarning(299, "-", "b")),
        warnings);
  }

  @Test
  void unquoted_text_runs_to_the_end() {
    assertEquals(
        List.of(new HttpWarning(299, "Orthanc:", "fuzzymatching is not supported")),
        HttpWarning.parse("299 Orthanc: fuzzymatching is not supported"));
  }

  @Test
  void collects_every_header_value() {
    assertEquals(2, HttpWarning.parse(List.of("299 a \"x\"", "299 b \"y\"")).size());
    assertTrue(HttpWarning.parse((String) null).isEmpty());
  }
}
