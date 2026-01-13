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

import static org.weasis.dicom.web.MultipartConstants.CONTENT_TYPE;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.SequenceInputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.dcm4che3.img.DicomImageReadParam;
import org.dcm4che3.img.DicomImageReader;
import org.dcm4che3.img.DicomJpegWriteParam;
import org.dcm4che3.img.DicomOutputData;
import org.dcm4che3.img.Transcoder;
import org.dcm4che3.img.stream.BytesWithImageDescriptor;
import org.dcm4che3.img.stream.ImageAdapter.AdaptTransferSyntax;
import org.dcm4che3.img.util.Editable;
import org.dcm4che3.io.DicomOutputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.util.annotations.Generated;
import org.weasis.opencv.data.PlanarImage;

/**
 * DICOM Store Over Web (STOW-RS) client implementation. Provides methods for uploading DICOM data
 * using multipart/related HTTP requests. Supports file uploads, stream uploads, and metadata-only
 * uploads.
 *
 * @see <a href="http://dicom.nema.org/medical/dicom/current/output/html/part18.html">DICOM
 *     PS3.18</a>
 */
public class DicomStowRS implements AutoCloseable {

  private static final Logger LOGGER = LoggerFactory.getLogger(DicomStowRS.class);

  /** Default multipart boundary string */
  public static final String DEFAULT_BOUNDARY = "weasisDicomBoundary";

  private final DicomStowConfig config;
  private final HttpClient httpClient;
  private final ExecutorService executorService;

  /** Creates a STOW-RS client with the specified configuration. */
  public DicomStowRS(DicomStowConfig config) {
    this.config = Objects.requireNonNull(config, "Configuration cannot be null");
    this.executorService = createExecutorService();
    this.httpClient = createHttpClient();
  }

  /** Legacy constructor for backward compatibility. */
  public DicomStowRS(
      String requestURL, ContentType contentType, String agentName, Map<String, String> headers) {
    this(
        DicomStowConfig.builder()
            .requestUrl(requestURL)
            .contentType(contentType)
            .userAgent(agentName)
            .headers(headers)
            .build());
  }

  /** Uploads a DICOM file from the specified path. */
  public void uploadDicom(Path path) throws Exception {
    Objects.requireNonNull(path, "Path cannot be null");
    if (!Files.exists(path)) {
      throw new IllegalArgumentException("File does not exist: " + path);
    }

    uploadPayload(Payload.ofPath(path));
  }

  /** Uploads DICOM data from an input stream with file meta information. */
  public void uploadDicom(InputStream inputStream, Attributes fileMetaInfo) throws Exception {
    Objects.requireNonNull(inputStream, "Input stream cannot be null");
    Objects.requireNonNull(fileMetaInfo, "File meta information cannot be null");

    Payload payload = createStreamPayload(inputStream, fileMetaInfo);
    uploadPayload(payload);
  }

  /** Uploads DICOM metadata as a new dataset. */
  public void uploadDicom(Attributes metadata, String transferSyntaxUid) throws Exception {
    Objects.requireNonNull(metadata, "Metadata cannot be null");
    Objects.requireNonNull(transferSyntaxUid, "Transfer syntax UID cannot be null");

    Payload payload = createMetadataPayload(metadata, transferSyntaxUid);
    uploadPayload(payload);
  }

  /** Uploads a prepared payload. */
  public void uploadPayload(Payload payload) throws Exception {
    Objects.requireNonNull(payload, "Payload cannot be null");

    var multipartBody = new MultipartBody(config.getContentType(), DEFAULT_BOUNDARY);
    multipartBody.addPart(config.getContentType().getType(), payload, null);

    HttpRequest request = buildHttpRequest(multipartBody);
    HttpResponse<String> response = sendRequest(request);

    logResponse(response);
  }

  @Override
  public void close() throws Exception {
    if (executorService != null && !executorService.isShutdown()) {
      executorService.shutdown();
    }
  }

  // Getters for backward compatibility
  public ContentType getContentType() {
    return config.getContentType();
  }

  public String getRequestURL() {
    return config.getRequestUrl();
  }

  public Map<String, String> getHeaders() {
    return config.getHeaders();
  }

  /** Creates a payload for processing compressed DICOM images. */
  public static Payload createCompressedImagePayload(
      Attributes data,
      AdaptTransferSyntax syntax,
      BytesWithImageDescriptor desc,
      Editable<PlanarImage> editable)
      throws IOException {
    return new CompressedImagePayload(data, syntax, desc, editable);
  }

  private ExecutorService createExecutorService() {
    return Executors.newFixedThreadPool(
        config.getThreadPoolSize(),
        r -> {
          Thread thread = new Thread(r, "DicomStow-" + System.currentTimeMillis());
          thread.setDaemon(true);
          return thread;
        });
  }

  private HttpClient createHttpClient() {
    return HttpClient.newBuilder()
        .executor(executorService)
        .followRedirects(HttpClient.Redirect.NORMAL)
        .version(HttpClient.Version.HTTP_1_1)
        .connectTimeout(config.getConnectTimeout())
        .build();
  }

  private HttpRequest buildHttpRequest(MultipartBody multipartBody) throws Exception {
    HttpRequest.Builder builder =
        HttpRequest.newBuilder()
            .uri(URI.create(config.getRequestUrl()))
            .POST(multipartBody.createBodyPublisher())
            .header(CONTENT_TYPE, multipartBody.getContentTypeHeader())
            .header("Accept", MultipartConstants.DicomContentType.XML.getMimeType())
            .header("User-Agent", config.getUserAgent());

    // Add custom headers
    config.getHeaders().forEach(builder::header);

    HttpRequest request = builder.build();

    if (LOGGER.isDebugEnabled()) {
      logRequest(request, multipartBody);
    }

    return request;
  }

  HttpResponse<String> sendRequest(HttpRequest request) throws Exception {
    HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

    int statusCode = response.statusCode();
    if (statusCode >= 400) {
      throw HttpException.fromResponse(response);
    }

    return response;
  }

  @Generated
  private void logRequest(HttpRequest request, MultipartBody multipartBody) {
    URI uri = request.uri();
    LOGGER.debug(
        "> POST {} {}", uri.getPath(), request.version().orElse(HttpClient.Version.HTTP_1_1));
    LOGGER.debug("> Host: {}:{}", uri.getHost(), uri.getPort());
    logHeaders("> ", request.headers());
    multipartBody.logDebugInfo();
  }

  @Generated
  private void logResponse(HttpResponse<String> response) {
    if (LOGGER.isDebugEnabled()) {
      LOGGER.debug("< {} response code: {}", response.version(), response.statusCode());
      logHeaders("< ", response.headers());
    }

    // Log response body lines
    response.body().lines().forEach(LOGGER::info);
  }

  @Generated
  private static void logHeaders(String prefix, HttpHeaders headers) {
    headers
        .map()
        .forEach(
            (name, values) ->
                values.forEach(value -> LOGGER.debug("{}{}: {}", prefix, name, value)));
    LOGGER.debug(prefix.trim());
  }

  private Payload createStreamPayload(InputStream inputStream, Attributes fileMetaInfo) {
    return new Payload() {
      @Override
      public long size() {
        return -1; // Unknown size for streams
      }

      @Override
      public InputStream newInputStream() {
        try {
          var metaOutput = new ByteArrayOutputStream();
          try (var dicomOut =
              new DicomOutputStream(metaOutput, fileMetaInfo.getString(Tag.TransferSyntaxUID))) {
            dicomOut.writeFileMetaInformation(fileMetaInfo);
          }

          List<InputStream> streams = new ArrayList<>();
          streams.add(new ByteArrayInputStream(metaOutput.toByteArray()));
          streams.add(inputStream);

          return new SequenceInputStream(Collections.enumeration(streams));
        } catch (IOException e) {
          LOGGER.error("Failed to create stream payload", e);
          return InputStream.nullInputStream();
        }
      }
    };
  }

  private Payload createMetadataPayload(Attributes metadata, String transferSyntaxUid) {
    return new Payload() {
      @Override
      public long size() {
        return -1;
      }

      @Override
      public InputStream newInputStream() {
        try {
          Attributes fileMetaInfo = metadata.createFileMetaInformation(transferSyntaxUid);
          var output = new ByteArrayOutputStream();

          try (var dicomOut = new DicomOutputStream(output, transferSyntaxUid)) {
            dicomOut.writeDataset(fileMetaInfo, metadata);
          }

          return new ByteArrayInputStream(output.toByteArray());
        } catch (IOException e) {
          LOGGER.error("Failed to create metadata payload", e);
          return InputStream.nullInputStream();
        }
      }
    };
  }

  /** Payload for compressed DICOM images. */
  private record CompressedImagePayload(
      Attributes data,
      AdaptTransferSyntax syntax,
      BytesWithImageDescriptor descriptor,
      Editable<PlanarImage> editable)
      implements Payload {
    private CompressedImagePayload(
        Attributes data,
        AdaptTransferSyntax syntax,
        BytesWithImageDescriptor descriptor,
        Editable<PlanarImage> editable) {
      this.data = Objects.requireNonNull(data);
      this.syntax = Objects.requireNonNull(syntax);
      this.descriptor = Objects.requireNonNull(descriptor);
      this.editable = Objects.requireNonNull(editable);
    }

    @Override
    public long size() {
      return -1; // Compressed size is unknown
    }

    @Override
    public InputStream newInputStream() {
      try {
        return createCompressedStream();
      } catch (IOException e) {
        LOGGER.error("Failed to create compressed image stream", e);
        return InputStream.nullInputStream();
      }
    }

    private InputStream createCompressedStream() throws IOException {
      var reader = new DicomImageReader(Transcoder.dicomImageReaderSpi);

      try {
        reader.setInput(descriptor);

        var readParam = new DicomImageReadParam();
        readParam.setReleaseImageAfterProcessing(true);

        var images = reader.getLazyPlanarImages(readParam, editable);
        var outputData =
            new DicomOutputData(images, descriptor.getImageDescriptor(), syntax.getRequested());

        if (!syntax.getRequested().equals(outputData.getTsuid())) {
          syntax.setSuitable(outputData.getTsuid());
          LOGGER.warn(
              "Transcoding to {} not possible, using {} instead",
              syntax.getRequested(),
              syntax.getSuitable());
        }

        Attributes dataset = new Attributes(data);
        dataset.remove(Tag.PixelData);

        var output = new ByteArrayOutputStream();
        try (var dicomOut = new DicomOutputStream(output, outputData.getTsuid())) {
          dicomOut.writeFileMetaInformation(
              dataset.createFileMetaInformation(outputData.getTsuid()));

          if (DicomOutputData.isNativeSyntax(outputData.getTsuid())) {
            outputData.writeRawImageData(dicomOut, dataset);
          } else {
            int[] writeParams =
                outputData.adaptTagsToCompressedImage(
                    dataset,
                    outputData.getFirstImage().get(),
                    descriptor.getImageDescriptor(),
                    DicomJpegWriteParam.buildDicomImageWriteParam(outputData.getTsuid()));
            outputData.writeCompressedImageData(dicomOut, dataset, writeParams);
          }
        }

        return new ByteArrayInputStream(output.toByteArray());
      } finally {
        reader.dispose();
      }
    }
  }
}
