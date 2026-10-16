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

import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.VR;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;

@DisplayNameGeneration(ReplaceUnderscores.class)
class SearchQueryTest {

  @Test
  void tag_keys_are_hexadecimal() {
    String query =
        new QidoQuery()
            .match(Tag.PatientName, "Doe^J*")
            .match(Tag.StudyDate, "20260101-20261016")
            .toQueryString();

    assertEquals("00100010=Doe%5EJ*&00080020=20260101-20261016", query);
  }

  @Test
  void value_list_keeps_comma_separator() {
    String query = new QidoQuery().match(Tag.ModalitiesInStudy, "CT", "MR").toQueryString();

    assertEquals("00080061=CT,MR", query);
  }

  @Test
  void comma_inside_a_value_is_encoded() {
    assertEquals(
        "StudyDescription=Head%2C%20Neck",
        new QidoQuery().match("StudyDescription", "Head, Neck").toQueryString());
  }

  @Test
  void key_without_value_is_an_include_field() {
    String query =
        new QidoQuery().match(Tag.PatientID).match("PatientSex", "", null).toQueryString();

    assertEquals("includefield=00100020&includefield=PatientSex", query);
  }

  @Test
  void fuzzy_matching_is_sent_only_when_requested() {
    assertEquals(
        "PatientName=Muller&fuzzymatching=true&limit=10&offset=20",
        new QidoQuery()
            .match("PatientName", "Muller")
            .fuzzyMatching(true)
            .limit(10)
            .offset(20)
            .toQueryString());
    assertEquals(
        "PatientName=Muller",
        new QidoQuery().match("PatientName", "Muller").fuzzyMatching(false).toQueryString());
  }

  @Test
  void multi_valued_attribute_is_a_value_list() {
    Attributes keys = new Attributes();
    keys.setString(Tag.ModalitiesInStudy, VR.CS, "CT", "MR");

    assertEquals("ModalitiesInStudy=CT,MR", new QidoQuery().match(keys).toQueryString());
    assertEquals("ModalitiesInStudy=CT,MR", UpsQuery.toFilter(keys));
  }
}
