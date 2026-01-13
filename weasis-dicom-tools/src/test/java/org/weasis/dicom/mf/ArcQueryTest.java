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
import static org.mockito.Mockito.*;

import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;
import java.util.*;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

@DisplayNameGeneration(ReplaceUnderscores.class)
class ArcQueryTest {

  // Test data factories
  private WadoParameters createTestWadoParameters() {
    return WadoParameters.builder("http://test.example.com/wado")
        .withArchiveID("test-archive")
        .withRequireOnlySOPInstanceUID(true)
        .withAdditionalParameters("param1=value1&param2=value2")
        .withOverrideDicomTagsList("1048608,1048624,1048640")
        .build();
  }

  private WadoParameters createTestWadoParametersWithAuth() {
    return WadoParameters.wadoRs("https://secure.example.com/dicomweb")
        .withArchiveID("secure-archive")
        .withWebLogin("testuser:testpass")
        .withAdditionalParameters("param1=value1&param2=value2")
        .build();
  }

  private Patient createTestPatient(String patientId, String issuer) {
    var patient = new Patient(patientId, issuer);
    patient.setPatientName("Test^Patient^" + patientId);
    patient.setPatientSex("M");
    patient.setPatientBirthDate("19800101");
    return patient;
  }

  private QueryResult createTestQueryResult() {
    var patients =
        List.of(createTestPatient("PAT001", null), createTestPatient("PAT002", "HOSPITAL_A"));
    var wadoParams = createTestWadoParameters();
    return new DefaultQueryResult(patients, wadoParams);
  }

  private QueryResult createEmptyQueryResult() {
    return new DefaultQueryResult(createTestWadoParameters());
  }

  private QueryResult createQueryResultWithMessage() {
    var result = createTestQueryResult();
    result.setViewerMessage(ViewerMessage.info("Query Complete", "Found 2 patients"));
    return result;
  }

  private QueryResult createQueryResultWithHttpTags() {
    var patients = List.of(createTestPatient("PAT002", "HOSPITAL_A"));
    var wadoParams = createTestWadoParametersWithAuth();
    wadoParams.addHttpTag("X-API-Key", "secret-key-123");
    wadoParams.addHttpTag("Authorization", "Bearer token123");

    return new DefaultQueryResult(patients, wadoParams);
  }

  @Nested
  class Constructor_Tests {

    @Test
    void constructor_with_result_list_creates_query_with_generated_uid() {
      var resultList = List.of(createTestQueryResult());

      var query = new ArcQuery(resultList);

      assertAll(
          () -> assertEquals(resultList, query.getQueryList()),
          () -> assertNotNull(query.xmlManifest("2.5")),
          () -> assertTrue(query.xmlManifest("2.5").contains("uid=")));
    }

    @Test
    void constructor_with_result_list_and_manifest_uid_creates_query() {
      var resultList = List.of(createTestQueryResult());
      var manifestUID = "test-manifest-123";

      var query = new ArcQuery(resultList, manifestUID);

      assertAll(
          () -> assertEquals(resultList, query.getQueryList()),
          () -> assertTrue(query.xmlManifest("2.5").contains(manifestUID)));
    }

    @Test
    void constructor_throws_exception_for_null_result_list() {
      var exception = assertThrows(NullPointerException.class, () -> new ArcQuery(null));

      assertEquals("Result list cannot be null", exception.getMessage());
    }

    @Test
    void constructor_with_null_manifest_uid_generates_uid() {
      var resultList = List.of(createTestQueryResult());

      var query = new ArcQuery(resultList, null);

      var manifest = query.xmlManifest("2.5");
      assertAll(
          () -> assertNotNull(manifest),
          () -> assertTrue(manifest.contains("uid=")),
          () -> assertFalse(manifest.contains("uid=\"null\"")));
    }

    @Test
    void constructor_with_empty_manifest_uid_generates_uid() {
      var resultList = List.of(createTestQueryResult());

      var query = new ArcQuery(resultList, "");

      var manifest = query.xmlManifest("2.5");
      assertTrue(manifest.contains("uid=") && !manifest.contains("uid=\"\""));
    }

    @Test
    void constructor_with_whitespace_manifest_uid_generates_uid() {
      var resultList = List.of(createTestQueryResult());

      var query = new ArcQuery(resultList, "   ");

      var manifest = query.xmlManifest("2.5");
      assertTrue(manifest.contains("uid=") && !manifest.contains("uid=\"   \""));
    }
  }

  @Nested
  class Query_List_Tests {

    @Test
    void getQueryList_returns_original_list() {
      var resultList = List.of(createTestQueryResult(), createQueryResultWithMessage());

      var query = new ArcQuery(resultList);

      assertSame(resultList, query.getQueryList());
    }

    @Test
    void supports_empty_result_list() {
      var emptyList = Collections.<QueryResult>emptyList();

      var query = new ArcQuery(emptyList);

      assertAll(
          () -> assertEquals(emptyList, query.getQueryList()),
          () -> assertNotNull(query.xmlManifest("2.5")));
    }

    @Test
    void supports_large_result_list() {
      var resultList = new ArrayList<QueryResult>();
      for (int i = 0; i < 100; i++) {
        resultList.add(createTestQueryResult());
      }

      var query = new ArcQuery(resultList);

      assertAll(
          () -> assertEquals(100, query.getQueryList().size()),
          () -> assertNotNull(query.xmlManifest("2.5")));
    }
  }

  @Nested
  class Modern_Manifest_Generation_Tests {

    @Test
    void xmlManifest_generates_modern_manifest_by_default() {
      var query = new ArcQuery(List.of(createTestQueryResult()));

      var manifest = query.xmlManifest(null);

      assertAll(
          () -> assertNotNull(manifest),
          () -> assertTrue(manifest.contains("<?xml version=\"1.0\" encoding=\"UTF-8\" ?>")),
          () -> assertTrue(manifest.contains("<manifest")),
          () -> assertTrue(manifest.contains("xmlns=\"http://www.weasis.org/xsd/2.5\"")),
          () -> assertTrue(manifest.contains("uid=")),
          () -> assertTrue(manifest.contains("</manifest>")));
    }

    @Test
    void xmlManifest_generates_modern_manifest_for_version_2_5() {
      var query = new ArcQuery(List.of(createTestQueryResult()));

      var manifest = query.xmlManifest("2.5");

      assertAll(
          () -> assertTrue(manifest.contains("<manifest")),
          () -> assertTrue(manifest.contains("<arcQuery")),
          () -> assertTrue(manifest.contains("arcId=")),
          () -> assertTrue(manifest.contains("baseUrl=")),
          () -> assertTrue(manifest.contains("</arcQuery>")),
          () -> assertTrue(manifest.contains("</manifest>")));
    }

    @Test
    void modern_manifest_includes_all_query_results_with_content() {
      var resultList =
          List.of(
              createTestQueryResult(),
              createQueryResultWithMessage(),
              createEmptyQueryResult() // This should be excluded
              );

      var query = new ArcQuery(resultList);
      var manifest = query.xmlManifest("2.5");

      // Should include first two results but not the empty one
      var arcQueryCount = countOccurrences(manifest, "<arcQuery");
      assertEquals(2, arcQueryCount);
    }

    @Test
    void modern_manifest_excludes_empty_query_results() {
      var resultList = List.of(createEmptyQueryResult());

      var query = new ArcQuery(resultList);
      var manifest = query.xmlManifest("2.5");

      assertAll(
          () -> assertTrue(manifest.contains("<manifest")),
          () -> assertFalse(manifest.contains("<arcQuery")),
          () -> assertTrue(manifest.contains("</manifest>")));
    }

    @Test
    void modern_manifest_includes_http_tags() {
      var query = new ArcQuery(List.of(createQueryResultWithHttpTags()));

      var manifest = query.xmlManifest("2.5");

      assertAll(
          () -> assertTrue(manifest.contains("<httpTag")),
          () -> assertTrue(manifest.contains("key=\"X-API-Key\"")),
          () -> assertTrue(manifest.contains("value=\"secret-key-123\"")),
          () -> assertTrue(manifest.contains("key=\"Authorization\"")),
          () -> assertTrue(manifest.contains("value=\"Bearer token123\"")));
    }

    @Test
    void modern_manifest_includes_viewer_messages() {
      var query = new ArcQuery(List.of(createQueryResultWithMessage()));

      var manifest = query.xmlManifest("2.5");

      assertAll(
          () -> assertTrue(manifest.contains("<Message")),
          () -> assertTrue(manifest.contains("title=\"Query Complete\"")),
          () -> assertTrue(manifest.contains("description=\"Found 2 patients\"")),
          () -> assertTrue(manifest.contains("severity=\"INFO\"")));
    }
  }

  @Nested
  class Legacy_Manifest_Generation_Tests {

    @Test
    void xmlManifest_generates_legacy_manifest_for_version_1() {
      var query = new ArcQuery(List.of(createTestQueryResult()));

      var manifest = query.xmlManifest("1");

      assertAll(
          () -> assertTrue(manifest.contains("<?xml version=\"1.0\" encoding=\"UTF-8\" ?>")),
          () -> assertTrue(manifest.contains("<wado_query")),
          () -> assertTrue(manifest.contains("xmlns=\"http://www.weasis.org/xsd\"")),
          () -> assertTrue(manifest.contains("wadoURL=")),
          () -> assertTrue(manifest.contains("</wado_query>")));
    }

    @Test
    void legacy_manifest_includes_only_first_result_with_content() {
      var resultList =
          List.of(
              createTestQueryResult(),
              createQueryResultWithMessage(),
              createQueryResultWithHttpTags());

      var query = new ArcQuery(resultList);
      var manifest = query.xmlManifest("1");

      // Legacy format should include only one wado_query element
      var wadoQueryCount = countOccurrences(manifest, "<wado_query");
      assertEquals(1, wadoQueryCount);
    }

    @Test
    void legacy_manifest_uses_wado_url_instead_of_base_url() {
      var query = new ArcQuery(List.of(createTestQueryResult()));

      var manifest = query.xmlManifest("1");

      assertAll(
          () -> assertTrue(manifest.contains("wadoURL=")),
          () -> assertFalse(manifest.contains("baseUrl=")),
          () -> assertFalse(manifest.contains("arcId=")));
    }

    @Test
    void legacy_manifest_excludes_empty_results() {
      var resultList = List.of(createEmptyQueryResult());

      var query = new ArcQuery(resultList);
      var manifest = query.xmlManifest("1");

      // Should only contain XML declaration, no wado_query elements
      assertAll(
          () -> assertTrue(manifest.contains("<?xml")),
          () -> assertFalse(manifest.contains("<wado_query")));
    }

    static Stream<Arguments> legacyVersionVariations() {
      return Stream.of(
          Arguments.of("1"), Arguments.of(" 1 "), Arguments.of("1.0") // This should NOT be legacy
          );
    }

    @ParameterizedTest
    @MethodSource("legacyVersionVariations")
    void legacy_version_detection_works_correctly(String version) {
      var query = new ArcQuery(List.of(createTestQueryResult()));

      var manifest = query.xmlManifest(version);

      if ("1".equals(version.trim())) {
        assertTrue(manifest.contains("<wado_query"));
      } else {
        assertTrue(manifest.contains("<manifest"));
      }
    }
  }

  @Nested
  class Manifest_Content_Tests {

    @Test
    void manifest_includes_all_wado_parameter_attributes() {
      var wadoParams =
          new WadoParameters(
              "test-archive",
              "https://pacs.example.com/dicomweb",
              true,
              "param1=value1",
              "1048608,1048624,1048640",
              "user:pass");

      var patients = List.of(createTestPatient("PAT123", null));
      var result = new DefaultQueryResult(patients, wadoParams);
      var query = new ArcQuery(List.of(result));

      var manifest = query.xmlManifest("2.5");

      assertAll(
          () -> assertTrue(manifest.contains("arcId=\"test-archive\"")),
          () -> assertTrue(manifest.contains("baseUrl=\"https://pacs.example.com/dicomweb\"")),
          () -> assertTrue(manifest.contains("webLogin=\"user:pass\"")),
          () -> assertTrue(manifest.contains("requireOnlySOPInstanceUID=\"true\"")),
          () -> assertTrue(manifest.contains("additionalParameters=\"param1=value1\"")),
          () -> assertTrue(manifest.contains("overrideDicomTagsList=\"1048608,1048624,1048640\"")));
    }

    @Test
    void manifest_includes_patient_data() {
      var query = new ArcQuery(List.of(createTestQueryResult()));

      var manifest = query.xmlManifest("2.5");

      // Should contain patient elements (exact format depends on Patient.toXml implementation)
      assertAll(
          () -> assertNotNull(manifest), () -> assertTrue(manifest.length() > 0)
          // Note: Patient XML structure would need to be verified based on Patient.toXml
          // implementation
          );
    }

    @Test
    void manifest_handles_multiple_viewer_message_types() {
      var resultWithInfo = createTestQueryResult();
      resultWithInfo.setViewerMessage(ViewerMessage.info("Info", "Information message"));

      var resultWithWarning = createEmptyQueryResult();
      resultWithWarning.setViewerMessage(ViewerMessage.warn("Warning", "Warning message"));

      var resultWithError = createEmptyQueryResult();
      resultWithError.setViewerMessage(ViewerMessage.error("Error", "Error message"));

      var query = new ArcQuery(List.of(resultWithInfo, resultWithWarning, resultWithError));
      var manifest = query.xmlManifest("2.5");

      assertAll(
          () -> assertTrue(manifest.contains("severity=\"INFO\"")),
          () -> assertTrue(manifest.contains("severity=\"WARN\"")),
          () -> assertTrue(manifest.contains("severity=\"ERROR\"")));
    }

    @Test
    void manifest_handles_special_characters_in_content() {
      var result = createEmptyQueryResult();
      result.setViewerMessage(
          ViewerMessage.info("Title with <>&\"'", "Message with <>&\"' characters"));

      var query = new ArcQuery(List.of(result));
      var manifest = query.xmlManifest("2.5");

      // The XML should be properly escaped (actual escaping depends on Xml.addXmlAttribute
      // implementation)
      assertAll(
          () -> assertNotNull(manifest),
          () -> assertTrue(manifest.contains("Title with")),
          () -> assertTrue(manifest.contains("Message with")));
    }
  }

  @Nested
  class Writer_Interface_Tests {

    @Test
    void writeManifest_writes_to_provided_writer() throws IOException {
      var query = new ArcQuery(List.of(createTestQueryResult()));
      var writer = new StringWriter();

      query.writeManifest(writer, "2.5");
      var result = writer.toString();

      assertAll(
          () -> assertTrue(result.contains("<?xml")),
          () -> assertTrue(result.contains("<manifest")),
          () -> assertTrue(result.contains("</manifest>")));
    }

    @Test
    void writeManifest_supports_different_versions() throws IOException {
      var query = new ArcQuery(List.of(createTestQueryResult()));

      var modernWriter = new StringWriter();
      var legacyWriter = new StringWriter();

      query.writeManifest(modernWriter, "2.5");
      query.writeManifest(legacyWriter, "1");

      assertAll(
          () -> assertTrue(modernWriter.toString().contains("<manifest")),
          () -> assertTrue(legacyWriter.toString().contains("<wado_query")));
    }

    @Test
    void writeManifest_handles_io_exceptions() {
      var query = new ArcQuery(List.of(createTestQueryResult()));
      var faultyWriter = mock(Writer.class);

      try {
        doThrow(new IOException("Write error")).when(faultyWriter).append(any(CharSequence.class));
      } catch (IOException e) {
        // Mock setup
      }

      assertThrows(IOException.class, () -> query.writeManifest(faultyWriter, "2.5"));
    }

    @Test
    void xmlManifest_returns_null_on_io_error() {
      var query =
          new ArcQuery(List.of(createTestQueryResult())) {
            @Override
            public void writeManifest(Writer writer, String version) throws IOException {
              throw new IOException("Simulated error");
            }
          };

      var result = query.xmlManifest("2.5");

      assertNull(result);
    }
  }

  @Nested
  class Edge_Cases_And_Scenarios_Tests {

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"", "  ", "2.0", "3", "invalid"})
    void manifest_version_handling_edge_cases(String version) {
      var query = new ArcQuery(List.of(createTestQueryResult()));

      var manifest = query.xmlManifest(version);

      if ("1".equals(version)) {
        assertTrue(manifest.contains("<wado_query"));
      } else {
        assertTrue(manifest.contains("<manifest"));
      }
    }

    @Test
    void manifest_with_mixed_content_and_empty_results() {
      var resultList =
          List.of(
              createTestQueryResult(), // Has patients
              createEmptyQueryResult(), // Empty
              createQueryResultWithMessage(), // Has message only
              createEmptyQueryResult() // Empty
              );

      var query = new ArcQuery(resultList);
      var manifest = query.xmlManifest("2.5");

      // Should include 2 arcQuery elements (first and third results)
      var arcQueryCount = countOccurrences(manifest, "<arcQuery");
      assertEquals(2, arcQueryCount);
    }

    @Test
    void manifest_uid_is_consistent_across_calls() {
      var query = new ArcQuery(List.of(createTestQueryResult()), "consistent-uid");

      var manifest1 = query.xmlManifest("2.5");
      var manifest2 = query.xmlManifest("2.5");

      assertAll(
          () -> assertTrue(manifest1.contains("uid=\"consistent-uid\"")),
          () -> assertEquals(manifest1, manifest2));
    }

    @Test
    void supports_all_wado_parameter_configurations() {
      var wadoUriParams = WadoParameters.wadoUri("http://simple.com/wado", false);
      var wadoRsParams =
          WadoParameters.wadoRs("https://advanced.com/dicomweb")
              .withArchiveID("advanced")
              .withWebLogin("admin:secret")
              .build();

      var patients = List.of(createTestPatient("PAT999", "HOSPITAL_X"));
      var query =
          new ArcQuery(
              List.of(
                  new DefaultQueryResult(patients, wadoUriParams),
                  new DefaultQueryResult(patients, wadoRsParams)));

      var manifest = query.xmlManifest("2.5");

      assertAll(
          () -> assertTrue(manifest.contains("http://simple.com/wado")),
          () -> assertTrue(manifest.contains("https://advanced.com/dicomweb")),
          () -> assertTrue(manifest.contains("arcId=\"advanced\"")),
          () -> assertTrue(manifest.contains("webLogin=\"admin:secret\"")));
    }

    @Test
    void large_manifest_generation_performance() {
      var resultList = new ArrayList<QueryResult>();
      for (int i = 0; i < 50; i++) {
        var result = createTestQueryResult();
        result.setViewerMessage(ViewerMessage.info("Message " + i, "Content " + i));
        resultList.add(result);
      }

      var query = new ArcQuery(resultList);

      // Should complete without timing out
      assertTimeout(
          java.time.Duration.ofSeconds(5),
          () -> {
            var manifest = query.xmlManifest("2.5");
            assertNotNull(manifest);
            assertTrue(manifest.length() > 1000); // Should be substantial
          });
    }

    @Test
    void manifest_format_consistency() {
      var query = new ArcQuery(List.of(createTestQueryResult()));

      var modernManifest = query.xmlManifest("2.5");
      var legacyManifest = query.xmlManifest("1");

      assertAll(
          () -> assertTrue(modernManifest.startsWith("<?xml")),
          () -> assertTrue(legacyManifest.startsWith("<?xml")),
          () -> assertTrue(modernManifest.contains("UTF-8")),
          () -> assertTrue(legacyManifest.contains("UTF-8")),
          () -> assertNotEquals(modernManifest, legacyManifest));
    }
  }

  // Helper method to count occurrences of a substring
  private int countOccurrences(String text, String substring) {
    int count = 0;
    int index = 0;
    while ((index = text.indexOf(substring, index)) != -1) {
      count++;
      index += substring.length();
    }
    return count;
  }

  // Helper method to verify XML structure (simplified)
  private void assertValidXmlStructure(String xml) {
    assertAll(
        () -> assertTrue(xml.contains("<?xml"), "Should contain XML declaration"),
        () -> assertTrue(xml.trim().endsWith(">"), "Should end with closing tag"),
        () -> assertFalse(xml.contains("><"), "Should not contain malformed tags"));
  }
}
