/*
 * Copyright (c) 2024 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.mf;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

@DisplayNameGeneration(ReplaceUnderscores.class)
class ArcParametersTest {

  private static final String ARCHIVE_ID = "HOSPITAL_ARCHIVE";
  private static final String BASE_URL = "http://example.com/wado";
  private static final String WEB_LOGIN = "user:password";
  private static final String ADDITIONAL_PARAMS = "param1=value1&param2=value2";
  private static final String OVERRIDE_TAGS = "0x0010,0x0020,0x0008,0x0050";

  @Nested
  class Constructor_Tests {

    @Test
    void constructor_with_valid_parameters_creates_arc_parameters() {
      var params =
          new ArcParameters(ARCHIVE_ID, BASE_URL, ADDITIONAL_PARAMS, OVERRIDE_TAGS, WEB_LOGIN);

      assertAll(
          () -> assertEquals(ARCHIVE_ID, params.getArchiveID()),
          () -> assertEquals(BASE_URL, params.getBaseURL()),
          () -> assertEquals(WEB_LOGIN, params.getWebLogin()),
          () -> assertEquals(ADDITIONAL_PARAMS, params.getAdditionalParameters()),
          () -> assertNotNull(params.getOverrideDicomTagIDList()),
          () -> assertTrue(params.getHttpTaglist().isEmpty()));
    }

    @Test
    void constructor_with_minimal_parameters_creates_arc_parameters() {
      var params = new ArcParameters(ARCHIVE_ID, BASE_URL, null, null, null);

      assertAll(
          () -> assertEquals(ARCHIVE_ID, params.getArchiveID()),
          () -> assertEquals(BASE_URL, params.getBaseURL()),
          () -> assertNull(params.getWebLogin()),
          () -> assertEquals("", params.getAdditionalParameters()),
          () -> assertNull(params.getOverrideDicomTagIDList()));
    }

    @Test
    void constructor_with_null_archive_id_throws_exception() {
      assertThrows(
          NullPointerException.class, () -> new ArcParameters(null, BASE_URL, null, null, null));
    }

    @Test
    void constructor_with_null_base_url_throws_exception() {
      assertThrows(
          NullPointerException.class, () -> new ArcParameters(ARCHIVE_ID, null, null, null, null));
    }

    @Test
    void constructor_trims_web_login() {
      var params = new ArcParameters(ARCHIVE_ID, BASE_URL, null, null, "  " + WEB_LOGIN + "  ");
      assertEquals(WEB_LOGIN, params.getWebLogin());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "\t\n"})
    void constructor_normalizes_additional_parameters(String input) {
      var params = new ArcParameters(ARCHIVE_ID, BASE_URL, input, null, null);
      assertEquals("", params.getAdditionalParameters());
    }
  }

  @Nested
  class Override_Tags_Tests {

    @Test
    void parseOverrideTags_with_valid_hex_tags_parses_correctly() {
      var params = new ArcParameters(ARCHIVE_ID, BASE_URL, null, "0x0010,0x0020", null);
      int[] tags = params.getOverrideDicomTagIDList();

      assertNotNull(tags);
      assertEquals(2, tags.length);
      assertEquals(0x0010, tags[0]);
      assertEquals(0x0020, tags[1]);
    }

    @Test
    void parseOverrideTags_with_decimal_tags_parses_correctly() {
      var params = new ArcParameters(ARCHIVE_ID, BASE_URL, null, "16,32", null);
      int[] tags = params.getOverrideDicomTagIDList();

      assertNotNull(tags);
      assertEquals(2, tags.length);
      assertEquals(16, tags[0]);
      assertEquals(32, tags[1]);
    }

    @Test
    void parseOverrideTags_with_mixed_formats_parses_correctly() {
      var params = new ArcParameters(ARCHIVE_ID, BASE_URL, null, "0x0010,32,0x0040", null);
      int[] tags = params.getOverrideDicomTagIDList();

      assertNotNull(tags);
      assertEquals(3, tags.length);
    }

    @Test
    void parseOverrideTags_filters_invalid_tags() {
      var params = new ArcParameters(ARCHIVE_ID, BASE_URL, null, "0x0010,invalid,0x0020", null);
      int[] tags = params.getOverrideDicomTagIDList();

      assertNotNull(tags);
      assertEquals(2, tags.length);
      assertEquals(0x0010, tags[0]);
      assertEquals(0x0020, tags[1]);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    void parseOverrideTags_with_null_or_invalid_returns_null(String input) {
      var params = new ArcParameters(ARCHIVE_ID, BASE_URL, null, input, null);
      assertNull(params.getOverrideDicomTagIDList());
    }

    @Test
    void getOverrideDicomTagsList_returns_comma_separated_string() {
      var params = new ArcParameters(ARCHIVE_ID, BASE_URL, null, "0x0010,0x0020", null);
      String tagsList = params.getOverrideDicomTagsList();

      assertNotNull(tagsList);
      assertTrue(tagsList.contains("16"));
      assertTrue(tagsList.contains("32"));
      assertTrue(tagsList.contains(","));
    }

    @Test
    void getOverrideDicomTagIDList_returns_defensive_copy() {
      var params = new ArcParameters(ARCHIVE_ID, BASE_URL, null, "0x0010,0x0020", null);
      int[] tags1 = params.getOverrideDicomTagIDList();
      int[] tags2 = params.getOverrideDicomTagIDList();

      assertNotSame(tags1, tags2);
      assertArrayEquals(tags1, tags2);
    }
  }

  @Nested
  class HTTP_Tag_Tests {

    @Test
    void addHttpTag_with_valid_parameters_adds_tag() {
      var params = new ArcParameters(ARCHIVE_ID, BASE_URL, null, null, null);
      params.addHttpTag("Authorization", "Bearer token");

      List<HttpTag> httpTags = params.getHttpTaglist();
      assertEquals(1, httpTags.size());

      HttpTag tag = httpTags.get(0);
      assertEquals("Authorization", tag.getKey());
      assertEquals("Bearer token", tag.getValue());
    }

    @Test
    void addHttpTag_with_null_key_ignores_addition() {
      var params = new ArcParameters(ARCHIVE_ID, BASE_URL, null, null, null);
      params.addHttpTag(null, "value");

      assertTrue(params.getHttpTaglist().isEmpty());
    }

    @Test
    void addHttpTag_with_null_value_ignores_addition() {
      var params = new ArcParameters(ARCHIVE_ID, BASE_URL, null, null, null);
      params.addHttpTag("key", null);

      assertTrue(params.getHttpTaglist().isEmpty());
    }

    @Test
    void getHttpTaglist_returns_unmodifiable_list() {
      var params = new ArcParameters(ARCHIVE_ID, BASE_URL, null, null, null);
      params.addHttpTag("key", "value");

      List<HttpTag> httpTags = params.getHttpTaglist();
      assertThrows(
          UnsupportedOperationException.class, () -> httpTags.add(new HttpTag("key2", "value2")));
    }
  }

  @Nested
  class ToString_Tests {

    @Test
    void toString_includes_all_relevant_information() {
      var params =
          new ArcParameters(ARCHIVE_ID, BASE_URL, ADDITIONAL_PARAMS, "0x0010,0x0020", WEB_LOGIN);
      params.addHttpTag("Authorization", "Bearer token");

      String string = params.toString();

      assertAll(
          () -> assertTrue(string.contains(ARCHIVE_ID)),
          () -> assertTrue(string.contains(BASE_URL)),
          () -> assertTrue(string.contains(WEB_LOGIN)),
          () -> assertTrue(string.contains(ADDITIONAL_PARAMS)),
          () -> assertTrue(string.contains("httpTagCount=1")));
    }

    @Test
    void toString_handles_null_values() {
      var params = new ArcParameters(ARCHIVE_ID, BASE_URL, null, null, null);

      assertDoesNotThrow(
          () -> {
            String string = params.toString();
            assertNotNull(string);
          });
    }
  }
}
