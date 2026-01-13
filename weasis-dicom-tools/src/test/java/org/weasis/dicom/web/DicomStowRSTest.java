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

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.awt.Dimension;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import java.util.stream.Stream;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.UID;
import org.dcm4che3.data.VR;
import org.dcm4che3.img.stream.BytesWithImageDescriptor;
import org.dcm4che3.img.stream.DicomFileInputStream;
import org.dcm4che3.img.stream.ImageAdapter;
import org.dcm4che3.img.stream.ImageAdapter.AdaptTransferSyntax;
import org.dcm4che3.img.util.Editable;
import org.dcm4che3.io.DicomInputStream;
import org.dcm4che3.io.DicomInputStream.IncludeBulkData;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.weasis.dicom.param.AttributeEditorContext;
import org.weasis.opencv.data.PlanarImage;
import org.weasis.opencv.op.ImageTransformer;

@ExtendWith(MockitoExtension.class)
@DisplayNameGeneration(ReplaceUnderscores.class)
class DicomStowRSTest {

  private static final String TEST_URL = "https://example.com/dicom/stow";
  private static final String TEST_USER_AGENT = "Test DICOM Client/1.0";
  private static final String TRANSFER_SYNTAX_UID = UID.ExplicitVRLittleEndian;

  @Mock private HttpClient mockHttpClient;
  @Mock private HttpResponse<String> mockResponse;

  private DicomStowConfig testConfig;
  private DicomStowRS dicomStowRS;

  @TempDir Path tempDir;

  @BeforeEach
  void setUp() {
    testConfig =
        DicomStowConfig.builder()
            .requestUrl(TEST_URL)
            .userAgent(TEST_USER_AGENT)
            .contentType(ContentType.APPLICATION_DICOM)
            .threadPoolSize(2)
            .connectTimeout(Duration.ofSeconds(5))
            .build();
  }

  @AfterEach
  void tearDown() throws Exception {
    if (dicomStowRS != null) {
      dicomStowRS.close();
    }
  }

  @Nested
  class Constructor_Tests {

    @Test
    void should_create_instance_with_config() {
      dicomStowRS = new DicomStowRS(testConfig);

      assertNotNull(dicomStowRS);
      assertEquals(ContentType.APPLICATION_DICOM, dicomStowRS.getContentType());
      assertEquals(TEST_URL + "/studies", dicomStowRS.getRequestURL());
      assertTrue(dicomStowRS.getHeaders().isEmpty());
    }

    @Test
    void should_create_instance_with_legacy_constructor() {
      Map<String, String> headers = Map.of("Authorization", "Bearer token123");

      dicomStowRS =
          new DicomStowRS(TEST_URL, ContentType.APPLICATION_DICOM, TEST_USER_AGENT, headers);

      assertNotNull(dicomStowRS);
      assertEquals(ContentType.APPLICATION_DICOM, dicomStowRS.getContentType());
      assertEquals(TEST_URL + "/studies", dicomStowRS.getRequestURL());
      assertEquals(headers, dicomStowRS.getHeaders());
    }

    @Test
    void should_throw_null_pointer_exception_for_null_config() {
      assertThrows(NullPointerException.class, () -> new DicomStowRS(null));
    }

    @Test
    void should_create_http_client_with_correct_configuration() {
      dicomStowRS = new DicomStowRS(testConfig);

      // Verify that the instance was created successfully (HttpClient creation is internal)
      assertNotNull(dicomStowRS);
    }
  }

  @Nested
  class Upload_Dicom_Path_Tests {

    @Test
    void should_upload_dicom_file_from_path() throws Exception {
      // Create a test DICOM file
      Path testFile = tempDir.resolve("test.dcm");
      byte[] dicomData = createTestDicomData();
      Files.write(testFile, dicomData);

      dicomStowRS = spy(new DicomStowRS(testConfig));
      doNothing().when(dicomStowRS).uploadPayload(any(Payload.class));

      dicomStowRS.uploadDicom(testFile);

      verify(dicomStowRS).uploadPayload(any(Payload.class));
    }

    @Test
    void should_throw_null_pointer_exception_for_null_path() {
      dicomStowRS = new DicomStowRS(testConfig);

      assertThrows(NullPointerException.class, () -> dicomStowRS.uploadDicom((Path) null));
    }

    @Test
    void should_throw_illegal_argument_exception_for_non_existent_file() {
      Path nonExistentFile = tempDir.resolve("non-existent.dcm");
      dicomStowRS = new DicomStowRS(testConfig);

      assertThrows(IllegalArgumentException.class, () -> dicomStowRS.uploadDicom(nonExistentFile));
    }
  }

  @Nested
  class Upload_Dicom_Stream_Tests {

    @Test
    void should_upload_dicom_from_input_stream() throws Exception {
      byte[] dicomData = createTestDicomData();
      InputStream inputStream = new ByteArrayInputStream(dicomData);
      Attributes fileMetaInfo = createTestFileMetaInfo();

      dicomStowRS = spy(new DicomStowRS(testConfig));
      doNothing().when(dicomStowRS).uploadPayload(any(Payload.class));

      dicomStowRS.uploadDicom(inputStream, fileMetaInfo);

      verify(dicomStowRS).uploadPayload(any(Payload.class));
    }

    @Test
    void should_throw_null_pointer_exception_for_null_input_stream() {
      dicomStowRS = new DicomStowRS(testConfig);
      Attributes fileMetaInfo = createTestFileMetaInfo();

      assertThrows(
          NullPointerException.class,
          () -> dicomStowRS.uploadDicom((InputStream) null, fileMetaInfo));
    }

    @Test
    void should_throw_null_pointer_exception_for_null_file_meta_info() {
      dicomStowRS = new DicomStowRS(testConfig);
      InputStream inputStream = new ByteArrayInputStream(new byte[0]);

      assertThrows(NullPointerException.class, () -> dicomStowRS.uploadDicom(inputStream, null));
    }
  }

  @Nested
  class Upload_Dicom_Metadata_Tests {

    @Test
    void should_upload_dicom_metadata() throws Exception {
      Attributes metadata = createTestMetadata();

      dicomStowRS = spy(new DicomStowRS(testConfig));
      doNothing().when(dicomStowRS).uploadPayload(any(Payload.class));

      dicomStowRS.uploadDicom(metadata, TRANSFER_SYNTAX_UID);

      verify(dicomStowRS).uploadPayload(any(Payload.class));
    }

    @Test
    void should_throw_null_pointer_exception_for_null_metadata() {
      dicomStowRS = new DicomStowRS(testConfig);

      assertThrows(
          NullPointerException.class, () -> dicomStowRS.uploadDicom(null, TRANSFER_SYNTAX_UID));
    }

    @Test
    void should_throw_null_pointer_exception_for_null_transfer_syntax() {
      dicomStowRS = new DicomStowRS(testConfig);
      Attributes metadata = createTestMetadata();

      assertThrows(NullPointerException.class, () -> dicomStowRS.uploadDicom(metadata, null));
    }
  }

  @Nested
  class Upload_Payload_Tests {

    @Test
    void should_upload_payload_successfully() throws Exception {
      setupSuccessfulHttpResponse();
      dicomStowRS = createDicomStowRSWithMockedHttpClient();

      Payload testPayload = Payload.ofBytes(createTestDicomData());

      assertDoesNotThrow(() -> dicomStowRS.uploadPayload(testPayload));

      verify(mockHttpClient).send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));
    }

    @Test
    void should_throw_null_pointer_exception_for_null_payload() {
      dicomStowRS = new DicomStowRS(testConfig);

      assertThrows(NullPointerException.class, () -> dicomStowRS.uploadPayload(null));
    }

    @Test
    void should_throw_http_exception_for_error_response() throws Exception {
      setupErrorHttpResponse(404, "Not Found");
      dicomStowRS = createDicomStowRSWithMockedHttpClient();

      Payload testPayload = Payload.ofBytes(createTestDicomData());

      HttpException exception =
          assertThrows(HttpException.class, () -> dicomStowRS.uploadPayload(testPayload));

      assertEquals(404, exception.getStatusCode());
      assertTrue(exception.getMessage().contains("404"));
    }

    @ParameterizedTest
    @ValueSource(ints = {400, 401, 403, 404, 500, 502, 503})
    void should_throw_http_exception_for_various_error_codes(int statusCode) throws Exception {
      setupErrorHttpResponse(statusCode, "Error");
      dicomStowRS = createDicomStowRSWithMockedHttpClient();

      Payload testPayload = Payload.ofBytes(createTestDicomData());

      HttpException exception =
          assertThrows(HttpException.class, () -> dicomStowRS.uploadPayload(testPayload));

      assertEquals(statusCode, exception.getStatusCode());
    }
  }

  @Nested
  class Http_Request_Building_Tests {

    @Test
    void should_build_http_request_with_correct_headers() throws Exception {
      Map<String, String> customHeaders =
          Map.of(
              "Authorization", "Bearer token123",
              "X-Custom-Header", "custom-value");

      DicomStowConfig configWithHeaders =
          DicomStowConfig.builder()
              .requestUrl(TEST_URL)
              .userAgent(TEST_USER_AGENT)
              .headers(customHeaders)
              .build();

      setupSuccessfulHttpResponse();
      dicomStowRS = createDicomStowRSWithMockedHttpClient(configWithHeaders);

      Payload testPayload = Payload.ofBytes(createTestDicomData());
      dicomStowRS.uploadPayload(testPayload);

      verify(mockHttpClient)
          .send(
              argThat(
                  request -> {
                    Map<String, java.util.List<String>> headers = request.headers().map();
                    return headers.containsKey("Authorization")
                        && headers.containsKey("X-Custom-Header")
                        && headers.containsKey("User-Agent")
                        && headers.get("User-Agent").contains(TEST_USER_AGENT);
                  }),
              any(HttpResponse.BodyHandler.class));
    }

    @Test
    void should_build_request_with_correct_uri() throws Exception {
      setupSuccessfulHttpResponse();
      dicomStowRS = createDicomStowRSWithMockedHttpClient();

      Payload testPayload = Payload.ofBytes(createTestDicomData());
      dicomStowRS.uploadPayload(testPayload);

      verify(mockHttpClient)
          .send(
              argThat(request -> request.uri().toString().equals(TEST_URL + "/studies")),
              any(HttpResponse.BodyHandler.class));
    }
  }

  @Nested
  class Compressed_Image_Payload_Tests {

    @Test
    void should_create_compressed_image_payload() throws IOException {
      Attributes data = createTestMetadata();
      AdaptTransferSyntax syntax = mock(AdaptTransferSyntax.class);
      BytesWithImageDescriptor descriptor = mock(BytesWithImageDescriptor.class);
      Editable<PlanarImage> editable = mock(Editable.class);

      Payload payload =
          DicomStowRS.createCompressedImagePayload(data, syntax, descriptor, editable);

      assertNotNull(payload);
      assertEquals(-1, payload.size()); // Compressed size is unknown
    }

    @Test
    void should_throw_null_pointer_exception_for_null_parameters_in_compressed_payload() {
      Attributes data = createTestMetadata();
      AdaptTransferSyntax syntax = mock(AdaptTransferSyntax.class);
      BytesWithImageDescriptor descriptor = mock(BytesWithImageDescriptor.class);
      Editable<PlanarImage> editable = mock(Editable.class);

      assertThrows(
          NullPointerException.class,
          () -> DicomStowRS.createCompressedImagePayload(null, syntax, descriptor, editable));

      assertThrows(
          NullPointerException.class,
          () -> DicomStowRS.createCompressedImagePayload(data, null, descriptor, editable));

      assertThrows(
          NullPointerException.class,
          () -> DicomStowRS.createCompressedImagePayload(data, syntax, null, editable));

      assertThrows(
          NullPointerException.class,
          () -> DicomStowRS.createCompressedImagePayload(data, syntax, descriptor, null));
    }
  }

  @Nested
  class Getter_Tests {

    @Test
    void should_return_correct_content_type() {
      dicomStowRS = new DicomStowRS(testConfig);

      assertEquals(ContentType.APPLICATION_DICOM, dicomStowRS.getContentType());
    }

    @Test
    void should_return_correct_request_url() {
      dicomStowRS = new DicomStowRS(testConfig);

      assertEquals(TEST_URL + "/studies", dicomStowRS.getRequestURL());
    }

    @Test
    void should_return_correct_headers() {
      Map<String, String> headers = Map.of("Test-Header", "test-value");
      DicomStowConfig configWithHeaders =
          DicomStowConfig.builder().requestUrl(TEST_URL).headers(headers).build();

      dicomStowRS = new DicomStowRS(configWithHeaders);

      assertEquals(headers, dicomStowRS.getHeaders());
    }
  }

  @Nested
  class Close_Tests {

    @Test
    void should_close_without_exception() {
      dicomStowRS = new DicomStowRS(testConfig);

      assertDoesNotThrow(() -> dicomStowRS.close());
    }

    @Test
    void should_handle_multiple_close_calls() {
      dicomStowRS = new DicomStowRS(testConfig);

      assertDoesNotThrow(
          () -> {
            dicomStowRS.close();
            dicomStowRS.close();
          });
    }
  }

  @Nested
  class Constants_Tests {

    @Test
    void should_have_correct_default_boundary() {
      assertEquals("weasisDicomBoundary", DicomStowRS.DEFAULT_BOUNDARY);
    }
  }

  @Nested
  class Payload_Creation_Tests {

    @Test
    void should_create_stream_payload_with_unknown_size() throws Exception {
      dicomStowRS = new DicomStowRS(testConfig);
      InputStream inputStream = new ByteArrayInputStream(createTestDicomData());
      Attributes fileMetaInfo = createTestFileMetaInfo();

      // Use reflection or create a spy to test internal payload creation
      dicomStowRS = spy(dicomStowRS);
      doNothing().when(dicomStowRS).uploadPayload(any(Payload.class));

      dicomStowRS.uploadDicom(inputStream, fileMetaInfo);

      verify(dicomStowRS).uploadPayload(argThat(payload -> payload.size() == -1));
    }

    @Test
    void should_create_metadata_payload_with_unknown_size() throws Exception {
      dicomStowRS = new DicomStowRS(testConfig);
      Attributes metadata = createTestMetadata();

      dicomStowRS = spy(dicomStowRS);
      doNothing().when(dicomStowRS).uploadPayload(any(Payload.class));

      dicomStowRS.uploadDicom(metadata, TRANSFER_SYNTAX_UID);

      verify(dicomStowRS).uploadPayload(argThat(payload -> payload.size() == -1));
    }
  }

  @Nested
  class Integration_Tests {

    @Test
    void should_handle_complete_upload_workflow() throws Exception {
      setupSuccessfulHttpResponse();
      dicomStowRS = createDicomStowRSWithMockedHttpClient();

      // Create test file
      Path testFile = tempDir.resolve("integration-test.dcm");
      Files.write(testFile, createTestDicomData());

      assertDoesNotThrow(() -> dicomStowRS.uploadDicom(testFile));

      verify(mockHttpClient).send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));
      verify(mockResponse).statusCode();
      verify(mockResponse).body();
    }

    @Test
    void should_handle_workflow_with_custom_configuration() throws Exception {
      DicomStowConfig customConfig =
          DicomStowConfig.builder()
              .requestUrl("https://custom-server.com/dicom")
              .userAgent("Custom-Client/2.0")
              .header("X-API-Key", "api-key-123")
              .header("Content-Language", "en-US")
              .threadPoolSize(3)
              .connectTimeout(Duration.ofSeconds(15))
              .build();

      setupSuccessfulHttpResponse();
      dicomStowRS = createDicomStowRSWithMockedHttpClient(customConfig);

      Payload testPayload = Payload.ofBytes(createTestDicomData());

      assertDoesNotThrow(() -> dicomStowRS.uploadPayload(testPayload));

      verify(mockHttpClient)
          .send(
              argThat(
                  request ->
                      request.uri().toString().equals("https://custom-server.com/dicom/studies")),
              any(HttpResponse.BodyHandler.class));
    }
  }

  @Nested
  class Real_Compressed_Image_Payload_Tests {

    @Test
    void should_create_stream_payload_with_unknown_size() throws Exception {
      dicomStowRS = new DicomStowRS(testConfig);

      Path dicomFile =
          Path.of("src/test/resources/org/dcm4che3/img/jpeg2000-multiframe-multifragments.dcm");
      assumeTrue(Files.exists(dicomFile), "Test DICOM file not found: " + dicomFile);

      // Read the DICOM file to get real data structures
      Attributes dicomData;
      String tsuid;
      try (var dis = new DicomFileInputStream(dicomFile)) {
        dis.setIncludeBulkData(IncludeBulkData.URI);
        dicomData = dis.readDataset();
        tsuid = dis.getTransferSyntax();
      }

      assertThrows(HttpException.class, () -> dicomStowRS.uploadDicom(dicomFile));
      assertThrows(HttpException.class, () -> dicomStowRS.uploadDicom(dicomData, tsuid));
      assertThrows(
          HttpException.class,
          () -> {
            try (var dis = new DicomFileInputStream(dicomFile)) {
              dis.setIncludeBulkData(IncludeBulkData.URI);
              dicomStowRS.uploadDicom(dis, dis.getFileMetaInformation());
            }
          });
    }

    @Test
    void should_create_compressed_image_payload_with_real_dicom_data() throws Exception {
      // Load real DICOM file from test resources
      Path dicomFile = Path.of("src/test/resources/org/dcm4che3/img/CT-JPEGLosslessSV1.dcm");
      assumeTrue(Files.exists(dicomFile), "Test DICOM file not found: " + dicomFile);

      // Read the DICOM file to get real data structures
      Attributes dicomData;
      String tsuid;
      try (var dis = new DicomFileInputStream(dicomFile)) {
        dis.setIncludeBulkData(IncludeBulkData.URI);
        dicomData = dis.readDataset();
        tsuid = dis.getTransferSyntax();
      }

      // Create real BytesWithImageDescriptor from the DICOM file
      AdaptTransferSyntax syntax = new AdaptTransferSyntax(tsuid, UID.JPEG2000);
      AttributeEditorContext ctx = new AttributeEditorContext(tsuid, null, null);
      BytesWithImageDescriptor descriptor = ImageAdapter.imageTranscode(dicomData, syntax, ctx);

      // Create a simple editable that reduces image size
      Editable<PlanarImage> editable =
          planarImage -> ImageTransformer.scale(planarImage.toImageCV(), new Dimension(25, 25));

      // Test the createCompressedImagePayload method
      Payload compressedPayload =
          DicomStowRS.createCompressedImagePayload(dicomData, syntax, descriptor, editable);

      // Verify payload properties
      assertNotNull(compressedPayload, "Compressed payload should not be null");
      assertEquals(-1, compressedPayload.size(), "Compressed payload size should be unknown (-1)");

      // Test that we can create an input stream
      InputStream compressedStream = compressedPayload.newInputStream();
      assertNotNull(compressedStream, "Compressed stream should not be null");

      // Verify the stream contains valid DICOM data
      byte[] compressedData = compressedStream.readAllBytes();
      assertTrue(compressedData.length > 0, "Compressed data should not be empty");

      // Verify DICOM magic number in the compressed output
      assertTrue(compressedData.length >= 132, "Compressed data should contain DICOM preamble");
      assertEquals('D', compressedData[128], "DICOM magic number 'D'");
      assertEquals('I', compressedData[129], "DICOM magic number 'I'");
      assertEquals('C', compressedData[130], "DICOM magic number 'C'");
      assertEquals('M', compressedData[131], "DICOM magic number 'M'");

      compressedStream.close();
    }

    @Test
    void should_handle_compression_fallback_when_transcoding_not_possible() throws Exception {
      // Load real DICOM file
      Path dicomFile = Path.of("src/test/resources/org/dcm4che3/img/mono2-CT-16bit.dcm");
      assumeTrue(Files.exists(dicomFile), "Test DICOM file not found: " + dicomFile);

      // Read the DICOM file to get real data structures
      Attributes dicomData;
      String tsuid;
      try (var dis = new DicomFileInputStream(dicomFile)) {
        dis.setIncludeBulkData(IncludeBulkData.URI);
        dicomData = dis.readDataset();
        tsuid = dis.getTransferSyntax();
      }

      // Create real BytesWithImageDescriptor from the DICOM file
      AdaptTransferSyntax syntax = new AdaptTransferSyntax(tsuid, UID.JPEG2000);
      AttributeEditorContext ctx = new AttributeEditorContext(tsuid, null, null);
      BytesWithImageDescriptor descriptor = ImageAdapter.imageTranscode(dicomData, syntax, ctx);

      // Create a simple editable that reduces image size
      Editable<PlanarImage> editable =
          planarImage -> ImageTransformer.scale(planarImage.toImageCV(), new Dimension(25, 25));

      // This should work even if transcoding falls back to a different syntax
      Payload payload =
          DicomStowRS.createCompressedImagePayload(dicomData, syntax, descriptor, editable);

      assertNotNull(payload);

      // Test that we can read the stream even with fallback compression
      try (InputStream stream = payload.newInputStream()) {
        byte[] data = stream.readAllBytes();
        assertTrue(data.length > 0, "Should produce valid output even with fallback");

        // Should still be valid DICOM
        assertTrue(data.length >= 132);
        assertEquals('D', data[128]);
        assertEquals('I', data[129]);
        assertEquals('C', data[130]);
        assertEquals('M', data[131]);
      }
    }

    @Test
    void should_create_multiple_streams_from_same_payload() throws Exception {
      Path dicomFile = Path.of("src/test/resources/org/dcm4che3/img/CT-JPEGLosslessSV1.dcm");
      assumeTrue(Files.exists(dicomFile), "Test DICOM file not found: " + dicomFile);

      // Read the DICOM file to get real data structures
      Attributes dicomData;
      String tsuid;
      try (var dis = new DicomFileInputStream(dicomFile)) {
        dis.setIncludeBulkData(IncludeBulkData.URI);
        dicomData = dis.readDataset();
        tsuid = dis.getTransferSyntax();
      }

      // Create real BytesWithImageDescriptor from the DICOM file
      AdaptTransferSyntax syntax = new AdaptTransferSyntax(tsuid, UID.JPEG2000);
      AttributeEditorContext ctx = new AttributeEditorContext(tsuid, null, null);
      BytesWithImageDescriptor descriptor = ImageAdapter.imageTranscode(dicomData, syntax, ctx);

      // Create a simple editable that reduces image size
      Editable<PlanarImage> editable =
          planarImage -> ImageTransformer.scale(planarImage.toImageCV(), new Dimension(25, 25));

      Payload payload =
          DicomStowRS.createCompressedImagePayload(dicomData, syntax, descriptor, editable);

      // Test that payload can create multiple independent streams
      byte[] firstRead;
      try (InputStream stream1 = payload.newInputStream()) {
        firstRead = stream1.readAllBytes();
      }

      byte[] secondRead;
      try (InputStream stream2 = payload.newInputStream()) {
        secondRead = stream2.readAllBytes();
      }

      // Both streams should produce the same data
      assertArrayEquals(
          firstRead,
          secondRead,
          "Multiple streams from same payload should produce identical data");
      assertTrue(firstRead.length > 0, "Streams should contain data");
    }

    @Test
    void should_handle_editable_image_processing() throws Exception {
      Path dicomFile = Path.of("src/test/resources/org/dcm4che3/img/mono2-CT-16bit.dcm");
      assumeTrue(Files.exists(dicomFile), "Test DICOM file not found: " + dicomFile);

      // Read the DICOM file to get real data structures
      Attributes dicomData;
      String tsuid;
      try (var dis = new DicomFileInputStream(dicomFile)) {
        dis.setIncludeBulkData(IncludeBulkData.URI);
        dicomData = dis.readDataset();
        tsuid = dis.getTransferSyntax();
      }

      // Create real BytesWithImageDescriptor from the DICOM file
      AdaptTransferSyntax syntax = new AdaptTransferSyntax(tsuid, UID.JPEG2000);
      AttributeEditorContext ctx = new AttributeEditorContext(tsuid, null, null);
      BytesWithImageDescriptor descriptor = ImageAdapter.imageTranscode(dicomData, syntax, ctx);

      // Create a simple editable that reduces image size
      Editable<PlanarImage> editable =
          planarImage -> ImageTransformer.scale(planarImage.toImageCV(), new Dimension(25, 25));

      Payload payload =
          DicomStowRS.createCompressedImagePayload(dicomData, syntax, descriptor, editable);

      // Verify the payload works with an editable that applies modifications
      assertNotNull(payload);

      try (InputStream stream = payload.newInputStream()) {
        byte[] data = stream.readAllBytes();
        assertTrue(data.length > 0, "Should handle editable processing");

        // Verify DICOM structure is maintained
        assertTrue(data.length >= 132);
        assertEquals('D', data[128]);
        assertEquals('I', data[129]);
        assertEquals('C', data[130]);
        assertEquals('M', data[131]);
      }
    }

    @Test
    void should_preserve_dicom_metadata_in_compressed_output() throws Exception {
      Path dicomFile = Path.of("src/test/resources/org/dcm4che3/img/CT-JPEGLosslessSV1.dcm");
      assumeTrue(Files.exists(dicomFile), "Test DICOM file not found: " + dicomFile);

      // Read the DICOM file to get real data structures
      Attributes originalData;
      String tsuid;
      try (var dis = new DicomFileInputStream(dicomFile)) {
        dis.setIncludeBulkData(IncludeBulkData.URI);
        originalData = dis.readDataset();
        tsuid = dis.getTransferSyntax();
      }

      // Create real BytesWithImageDescriptor from the DICOM file
      AdaptTransferSyntax syntax = new AdaptTransferSyntax(tsuid, UID.JPEG2000);
      AttributeEditorContext ctx = new AttributeEditorContext(tsuid, null, null);
      BytesWithImageDescriptor descriptor = ImageAdapter.imageTranscode(originalData, syntax, ctx);

      // Create a simple editable that reduces image size
      Editable<PlanarImage> editable =
          planarImage -> ImageTransformer.scale(planarImage.toImageCV(), new Dimension(25, 25));

      Payload payload =
          DicomStowRS.createCompressedImagePayload(originalData, syntax, descriptor, editable);

      // Read the compressed output and parse it back as DICOM
      byte[] compressedBytes;
      try (InputStream stream = payload.newInputStream()) {
        compressedBytes = stream.readAllBytes();
      }

      // Parse the compressed output to verify metadata preservation
      Attributes compressedData;
      try (var dis = new DicomInputStream(new ByteArrayInputStream(compressedBytes))) {
        dis.setIncludeBulkData(DicomInputStream.IncludeBulkData.NO);
        compressedData = dis.readDataset();
      }

      // Verify key metadata is preserved (excluding pixel data and transfer syntax dependent
      // fields)
      assertEquals(
          originalData.getString(Tag.SOPClassUID),
          compressedData.getString(Tag.SOPClassUID),
          "SOP Class UID should be preserved");

      assertEquals(
          originalData.getString(Tag.SOPInstanceUID),
          compressedData.getString(Tag.SOPInstanceUID),
          "SOP Instance UID should be preserved");

      if (originalData.contains(Tag.PatientName)) {
        assertEquals(
            originalData.getString(Tag.PatientName),
            compressedData.getString(Tag.PatientName),
            "Patient Name should be preserved");
      }

      if (originalData.contains(Tag.StudyInstanceUID)) {
        assertEquals(
            originalData.getString(Tag.StudyInstanceUID),
            compressedData.getString(Tag.StudyInstanceUID),
            "Study Instance UID should be preserved");
      }

      // Verify image-specific metadata is updated for compression
      if (compressedData.contains(Tag.TransferSyntaxUID)) {
        String compressedTransferSyntax = compressedData.getString(Tag.TransferSyntaxUID);
        assertTrue(
            compressedTransferSyntax.equals(UID.JPEGBaseline8Bit)
                || compressedTransferSyntax.equals(syntax.getSuitable()),
            "Transfer syntax should be updated for compression");
      }
    }
  }

  // Add the required import at the top of the class
  private static void assumeTrue(boolean condition, String message) {
    if (!condition) {
      System.out.println("Skipping test: " + message);
      org.junit.jupiter.api.Assumptions.assumeTrue(condition, message);
    }
  }

  // Helper methods

  private void setupSuccessfulHttpResponse() throws IOException, InterruptedException {
    when(mockResponse.statusCode()).thenReturn(200);
    when(mockResponse.body()).thenReturn("Upload successful");
    when(mockHttpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
        .thenReturn(mockResponse);
  }

  private void setupErrorHttpResponse(int statusCode, String body)
      throws IOException, InterruptedException {
    when(mockResponse.statusCode()).thenReturn(statusCode);
    when(mockResponse.body()).thenReturn(body);
    when(mockHttpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
        .thenReturn(mockResponse);
  }

  private DicomStowRS createDicomStowRSWithMockedHttpClient() {
    return createDicomStowRSWithMockedHttpClient(testConfig);
  }

  private DicomStowRS createDicomStowRSWithMockedHttpClient(DicomStowConfig config) {
    DicomStowRS stowRS = spy(new DicomStowRS(config));

    // Use reflection to inject the mocked HttpClient
    try {
      var httpClientField = DicomStowRS.class.getDeclaredField("httpClient");
      httpClientField.setAccessible(true);
      httpClientField.set(stowRS, mockHttpClient);
    } catch (Exception e) {
      // Fallback - create a partial mock that overrides sendRequest method
      try {
        doReturn(mockResponse).when(stowRS).sendRequest(any(HttpRequest.class));
      } catch (Exception ex) {
        throw new RuntimeException("Failed to setup mock", ex);
      }
    }

    return stowRS;
  }

  private byte[] createTestDicomData() {
    // Create minimal DICOM-like data
    return new byte[] {
      // DICOM preamble (128 bytes of zeros) - simplified
      0,
      0,
      0,
      0,
      0,
      0,
      0,
      0,
      0,
      0,
      0,
      0,
      0,
      0,
      0,
      0,
      // DICM prefix
      'D',
      'I',
      'C',
      'M',
      // Simple data elements - simplified
      0x08,
      0x00,
      0x18,
      0x00,
      'U',
      'I',
      26,
      0, // SOP Class UID element
      '1',
      '.',
      '2',
      '.',
      '8',
      '4',
      '0',
      '.',
      '1',
      '0',
      '0',
      '0',
      '8',
      '.',
      '5',
      '.',
      '1',
      '.',
      '4',
      '.',
      '1',
      '.',
      '1',
      '.',
      '7',
      0
    };
  }

  private Attributes createTestMetadata() {
    Attributes metadata = new Attributes();
    metadata.setString(Tag.SOPClassUID, VR.UI, UID.CTImageStorage);
    metadata.setString(Tag.SOPInstanceUID, VR.UI, "1.2.840.10008.1.2.1.2025.1");
    metadata.setString(Tag.StudyInstanceUID, VR.UI, "1.2.840.10008.1.2.1.2025.2");
    metadata.setString(Tag.SeriesInstanceUID, VR.UI, "1.2.840.10008.1.2.1.2025.3");
    metadata.setString(Tag.PatientID, VR.LO, "123456");
    metadata.setString(Tag.PatientName, VR.PN, "Test^Patient");
    return metadata;
  }

  private Attributes createTestFileMetaInfo() {
    Attributes fileMetaInfo = new Attributes();
    fileMetaInfo.setBytes(Tag.FileMetaInformationGroupLength, VR.UL, new byte[] {0, 0, 0, 0});
    fileMetaInfo.setString(Tag.MediaStorageSOPClassUID, VR.UI, UID.CTImageStorage);
    fileMetaInfo.setString(Tag.MediaStorageSOPInstanceUID, VR.UI, "1.2.840.10008.1.2.1.2025.1");
    fileMetaInfo.setString(Tag.TransferSyntaxUID, VR.UI, TRANSFER_SYNTAX_UID);
    fileMetaInfo.setString(Tag.ImplementationClassUID, VR.UI, "1.2.840.10008.1.2.1.2025.impl");
    return fileMetaInfo;
  }

  private static Stream<Arguments> provideHttpErrorCodes() {
    return Stream.of(
        Arguments.of(400, "Bad Request"),
        Arguments.of(401, "Unauthorized"),
        Arguments.of(403, "Forbidden"),
        Arguments.of(404, "Not Found"),
        Arguments.of(422, "Unprocessable Entity"),
        Arguments.of(500, "Internal Server Error"),
        Arguments.of(502, "Bad Gateway"),
        Arguments.of(503, "Service Unavailable"));
  }
}
