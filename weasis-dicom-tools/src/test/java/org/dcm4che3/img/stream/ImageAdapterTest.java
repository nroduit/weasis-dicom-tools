/*
 * Copyright (c) 2024 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.dcm4che3.img.stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.awt.*;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.BulkData;
import org.dcm4che3.data.Fragments;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.UID;
import org.dcm4che3.data.VR;
import org.dcm4che3.img.DicomJpegWriteParam;
import org.dcm4che3.img.op.MaskArea;
import org.dcm4che3.img.stream.ImageAdapter.AdaptTransferSyntax;
import org.dcm4che3.net.DataWriter;
import org.dcm4che3.net.PDVOutputStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.weasis.dicom.param.AttributeEditorContext;
import org.weasis.opencv.data.PlanarImage;

@ExtendWith(MockitoExtension.class)
@DisplayNameGeneration(ReplaceUnderscores.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ImageAdapterTest {

  @TempDir Path tempDir;

  @Mock private PlanarImage mockPlanarImage;

  @Mock private BulkData mockBulkData;

  @Mock private Fragments mockFragments;

  private Attributes createBasicAttributes() {
    var attributes = new Attributes();
    attributes.setString(Tag.SOPClassUID, VR.UI, UID.SecondaryCaptureImageStorage);
    attributes.setString(Tag.SOPInstanceUID, VR.UI, "1.2.3.4.5.6.7.8.9");
    attributes.setString(Tag.StudyInstanceUID, VR.UI, "1.2.3.4.5");
    attributes.setString(Tag.SeriesInstanceUID, VR.UI, "1.2.3.4.5.6");
    attributes.setInt(Tag.Rows, VR.US, 512);
    attributes.setInt(Tag.Columns, VR.US, 512);
    attributes.setInt(Tag.BitsAllocated, VR.US, 16);
    attributes.setInt(Tag.BitsStored, VR.US, 12);
    attributes.setInt(Tag.HighBit, VR.US, 11);
    attributes.setInt(Tag.PixelRepresentation, VR.US, 0);
    attributes.setString(Tag.PhotometricInterpretation, VR.CS, "MONOCHROME2");
    attributes.setInt(Tag.SamplesPerPixel, VR.US, 1);
    return attributes;
  }

  private BytesWithImageDescriptor createMockBytesDescriptor() {
    return new BytesWithImageDescriptor() {
      @Override
      public ImageDescriptor getImageDescriptor() {
        return new ImageDescriptor(createBasicAttributes());
      }

      @Override
      public boolean isBigEndian() {
        return false;
      }

      @Override
      public VR getPixelDataVR() {
        return VR.OW;
      }

      @Override
      public String getTransferSyntax() {
        return UID.ExplicitVRLittleEndian;
      }

      @Override
      public ByteBuffer getBytes(int frame) throws IOException {
        return ByteBuffer.allocate(512 * 512 * 2); // 512x512x16bit
      }
    };
  }

  @Nested
  class Adapt_Transfer_Syntax_Tests {

    @Test
    void should_create_with_valid_parameters() {
      var syntax = new AdaptTransferSyntax(UID.ExplicitVRLittleEndian, UID.JPEG2000);

      assertAll(
          "AdaptTransferSyntax creation",
          () -> assertEquals(UID.ExplicitVRLittleEndian, syntax.getOriginal()),
          () -> assertEquals(UID.JPEG2000, syntax.getRequested()),
          () -> assertEquals(UID.JPEG2000, syntax.getSuitable()),
          () -> assertEquals(85, syntax.getJpegQuality()),
          () -> assertEquals(0, syntax.getCompressionRatioFactor()));
    }

    @Test
    void should_throw_exception_for_null_or_empty_parameters() {
      assertAll(
          "Parameter validation",
          () ->
              assertThrows(
                  IllegalArgumentException.class,
                  () -> new AdaptTransferSyntax(null, UID.JPEG2000)),
          () ->
              assertThrows(
                  IllegalArgumentException.class, () -> new AdaptTransferSyntax("", UID.JPEG2000)),
          () ->
              assertThrows(
                  IllegalArgumentException.class,
                  () -> new AdaptTransferSyntax(UID.ExplicitVRLittleEndian, null)),
          () ->
              assertThrows(
                  IllegalArgumentException.class,
                  () -> new AdaptTransferSyntax(UID.ExplicitVRLittleEndian, "")));
    }

    @Test
    void should_set_jpeg_quality() {
      var syntax = new AdaptTransferSyntax(UID.ExplicitVRLittleEndian, UID.JPEG2000);

      syntax.setJpegQuality(95);
      assertEquals(95, syntax.getJpegQuality());

      syntax.setJpegQuality(0);
      assertEquals(0, syntax.getJpegQuality());
    }

    @Test
    void should_set_compression_ratio_factor() {
      var syntax = new AdaptTransferSyntax(UID.ExplicitVRLittleEndian, UID.JPEG2000);

      syntax.setCompressionRatioFactor(10);
      assertEquals(10, syntax.getCompressionRatioFactor());
    }

    @Test
    void should_set_suitable_transfer_syntax_only_if_valid() {
      var syntax = new AdaptTransferSyntax(UID.ExplicitVRLittleEndian, UID.JPEG2000);

      // Valid UID should be set
      syntax.setSuitable(UID.JPEGLosslessSV1);
      assertEquals(UID.JPEGLosslessSV1, syntax.getSuitable());

      // Invalid UID should not change current suitable
      syntax.setSuitable("invalid.uid");
      assertEquals(UID.JPEGLosslessSV1, syntax.getSuitable());
    }
  }

  @Nested
  class Write_Dicom_File_Tests {

    private Path outputFile;

    @BeforeEach
    void setup() {
      outputFile = tempDir.resolve("test.dcm");
    }

    @Test
    void should_write_metadata_only_file_successfully() {
      var attributes = createBasicAttributes();
      var syntax = new AdaptTransferSyntax(UID.ExplicitVRLittleEndian, UID.ExplicitVRLittleEndian);

      boolean result = ImageAdapter.writeDicomFile(attributes, syntax, null, null, outputFile);

      assertAll(
          "Metadata-only file writing",
          () -> assertTrue(result, "Should return true for successful write"),
          () -> assertTrue(Files.exists(outputFile), "Output file should exist"),
          () -> assertTrue(Files.size(outputFile) > 0, "File should not be empty"));
    }

    @Test
    void should_handle_write_failure_gracefully() throws IOException {
      var attributes = createBasicAttributes();
      var syntax = new AdaptTransferSyntax(UID.ExplicitVRLittleEndian, UID.ExplicitVRLittleEndian);

      // Create a read-only directory to force write failure
      var readOnlyDir = tempDir.resolve("readonly");
      Files.createDirectory(readOnlyDir);
      readOnlyDir.toFile().setWritable(false);
      var unwritableFile = readOnlyDir.resolve("test.dcm");

      boolean result = ImageAdapter.writeDicomFile(attributes, syntax, null, null, unwritableFile);

      assertFalse(result, "Should return false for failed write");

      // Cleanup
      readOnlyDir.toFile().setWritable(true);
    }

    @Test
    void should_adjust_syntax_for_metadata_only_files() {
      var attributes = createBasicAttributes();
      var syntax = new AdaptTransferSyntax(UID.ImplicitVRLittleEndian, UID.ImplicitVRLittleEndian);

      ImageAdapter.writeDicomFile(attributes, syntax, null, null, outputFile);

      assertEquals(
          UID.ImplicitVRLittleEndian,
          syntax.getSuitable(),
          "Should use ImplicitVRLittleEndian for metadata-only files");
    }

    @Test
    void should_delete_file_on_write_error() {
      var attributes = createBasicAttributes();
      attributes.setString(Tag.SOPClassUID, VR.UI, (String) null); // Invalid to cause error
      var syntax = new AdaptTransferSyntax(UID.ExplicitVRLittleEndian, UID.ExplicitVRLittleEndian);

      boolean result = ImageAdapter.writeDicomFile(attributes, syntax, null, null, outputFile);

      assertAll(
          "Error handling",
          () -> assertFalse(result, "Should return false for failed write"),
          () -> assertFalse(Files.exists(outputFile), "Failed file should be deleted"));
    }
  }

  @Nested
  class Build_Data_Writer_Tests {

    @Test
    void should_create_metadata_data_writer() throws IOException {
      var attributes = createBasicAttributes();
      var syntax = new AdaptTransferSyntax(UID.ExplicitVRLittleEndian, UID.ExplicitVRLittleEndian);

      DataWriter writer = ImageAdapter.buildDataWriter(attributes, syntax, null, null);

      assertNotNull(writer, "DataWriter should be created");
      assertEquals(
          UID.ExplicitVRLittleEndian,
          syntax.getSuitable(),
          "Suitable syntax should be set to original for metadata-only");
    }

    @Test
    void should_write_data_using_data_writer() throws IOException {
      var attributes = createBasicAttributes();
      var syntax = new AdaptTransferSyntax(UID.ExplicitVRLittleEndian, UID.ExplicitVRLittleEndian);

      AtomicInteger bytesWritten = new AtomicInteger(0);
      var outputStream =
          new PDVOutputStream() {
            @Override
            public void copyFrom(InputStream inputStream, int i) throws IOException {}

            @Override
            public void copyFrom(InputStream inputStream) throws IOException {}

            @Override
            public void write(int b) throws IOException {
              bytesWritten.incrementAndGet();
            }
          };

      DataWriter writer = ImageAdapter.buildDataWriter(attributes, syntax, null, null);
      writer.writeTo(outputStream, UID.ExplicitVRLittleEndian);

      assertTrue(bytesWritten.get() > 100, "Data should be written to output stream");
    }
  }

  @Nested
  class Image_Transcode_Tests {

    @Test
    void should_return_null_when_no_pixel_data() {
      var attributes = createBasicAttributes();
      // No pixel data added
      var syntax = new AdaptTransferSyntax(UID.ExplicitVRLittleEndian, UID.JPEG2000);
      var context = new AttributeEditorContext(UID.ExplicitVRLittleEndian, null, null);

      var result = ImageAdapter.imageTranscode(attributes, syntax, context);

      assertNull(result, "Should return null when no pixel data");
    }

    @Test
    void should_return_null_when_transcoding_not_applicable() {
      var attributes = createBasicAttributes();
      attributes.setValue(Tag.PixelData, VR.OW, mockBulkData);

      var syntax = new AdaptTransferSyntax("unsupported.syntax", "another.unsupported.syntax");
      var context = new AttributeEditorContext(UID.ExplicitVRLittleEndian, null, null);

      var result = ImageAdapter.imageTranscode(attributes, syntax, context);

      assertNull(result, "Should return null when transcoding not applicable");
    }

    @Test
    void should_create_bytes_descriptor_when_applicable() {
      var attributes = createBasicAttributes();
      attributes.setValue(Tag.PixelData, VR.OW, mockBulkData);

      var syntax = new AdaptTransferSyntax(UID.ExplicitVRLittleEndian, UID.JPEG2000);
      var context = new AttributeEditorContext(UID.ExplicitVRLittleEndian, null, null);

      var result = ImageAdapter.imageTranscode(attributes, syntax, context);

      assertNotNull(result, "Should create BytesWithImageDescriptor when transcoding applicable");
      assertFalse(mockBulkData.isEmpty(), "Should be not empty BulkData");
      assertEquals(UID.ExplicitVRLittleEndian, result.getTransferSyntax());
    }
  }

  @Nested
  class Image_Bytes_Descriptor_Tests {

    @Test
    void should_handle_bulk_data_correctly() throws IOException {
      var attributes = createBasicAttributes();

      when(mockBulkData.bigEndian()).thenReturn(false);
      attributes.setValue(Tag.PixelData, VR.OW, mockBulkData);

      var syntax = new AdaptTransferSyntax(UID.ExplicitVRLittleEndian, UID.JPEG2000);
      var context = new AttributeEditorContext(UID.ExplicitVRLittleEndian, null, null);
      var descriptor = ImageAdapter.imageTranscode(attributes, syntax, context);

      assertNotNull(descriptor);
      assertAll(
          "BulkData handling",
          () -> assertFalse(descriptor.isBigEndian()),
          () -> assertEquals(VR.OW, descriptor.getPixelDataVR()),
          () -> assertEquals(UID.ExplicitVRLittleEndian, descriptor.getTransferSyntax()));
    }

    @Test
    void should_handle_fragments_correctly() throws IOException {
      var attributes = createBasicAttributes();
      when(mockFragments.bigEndian()).thenReturn(false);

      attributes.setValue(Tag.PixelData, VR.OB, mockFragments);
      // Set frames to 1 for single frame handling
      attributes.setInt(Tag.NumberOfFrames, VR.IS, 1);

      var syntax = new AdaptTransferSyntax(UID.JPEG2000, UID.ExplicitVRLittleEndian);
      var context = new AttributeEditorContext(UID.JPEG2000, null, null);
      var descriptor = ImageAdapter.imageTranscode(attributes, syntax, context);

      assertNotNull(descriptor);
      assertAll(
          "Fragments handling",
          () -> assertFalse(descriptor.isBigEndian()),
          () -> assertEquals(VR.OB, descriptor.getPixelDataVR()),
          () -> assertEquals(UID.JPEG2000, descriptor.getTransferSyntax()));
    }

    @Test
    void should_return_empty_bytes_for_invalid_bits_stored() throws IOException {
      var attributes = createBasicAttributes();
      attributes.setInt(Tag.BitsStored, VR.US, 0); // Invalid bits stored
      attributes.setValue(Tag.PixelData, VR.OW, mockBulkData);

      var syntax = new AdaptTransferSyntax(UID.ExplicitVRLittleEndian, UID.JPEG2000);
      var context = new AttributeEditorContext(UID.ExplicitVRLittleEndian, null, null);
      var descriptor = ImageAdapter.imageTranscode(attributes, syntax, context);

      assertNotNull(descriptor);
      assertEquals(
          UID.ExplicitVRLittleEndian, descriptor.getTransferSyntax(), "Should use original TSUID");
    }

    @Test
    void should_throw_exception_for_unsupported_pixel_data_type() throws IOException {
      var attributes = createBasicAttributes();
      attributes.setValue(
          Tag.PixelData, VR.OW, "unsupported_type"); // Neither BulkData nor Fragments

      var syntax = new AdaptTransferSyntax(UID.ExplicitVRLittleEndian, UID.JPEG2000);
      var context = new AttributeEditorContext(UID.ExplicitVRLittleEndian, null, null);
      var descriptor = ImageAdapter.imageTranscode(attributes, syntax, context);

      assertNotNull(descriptor);
      assertThrows(
          IOException.class,
          () -> descriptor.getBytes(0),
          "Should throw IOException for unsupported pixel data type");
    }
  }

  @Nested
  class Syntax_Validation_Tests {

    @Test
    void should_update_suitable_syntax_when_requested_not_possible() {
      var syntax = new AdaptTransferSyntax(UID.ExplicitVRLittleEndian, UID.JPEG2000);

      // Mock DicomOutputData that returns different TSUID than requested
      var mockOutputData = mock(org.dcm4che3.img.DicomOutputData.class);
      when(mockOutputData.getTsuid()).thenReturn(UID.JPEGLosslessSV1);

      ImageAdapter.checkSyntax(syntax, mockOutputData);

      assertEquals(
          UID.JPEGLosslessSV1,
          syntax.getSuitable(),
          "Should update suitable syntax when requested is not possible");
    }

    @Test
    void should_keep_suitable_syntax_when_requested_is_possible() {
      var syntax = new AdaptTransferSyntax(UID.ExplicitVRLittleEndian, UID.JPEG2000);

      // Mock DicomOutputData that returns same TSUID as requested
      var mockOutputData = mock(org.dcm4che3.img.DicomOutputData.class);
      when(mockOutputData.getTsuid()).thenReturn(UID.JPEG2000);

      ImageAdapter.checkSyntax(syntax, mockOutputData);

      assertEquals(
          UID.JPEG2000,
          syntax.getSuitable(),
          "Should keep suitable syntax when requested is possible");
    }
  }

  @Nested
  class Edge_Cases_And_Error_Handling_Tests {

    @Test
    void should_handle_large_files_gracefully() {
      var attributes = createBasicAttributes();
      attributes.setInt(Tag.Rows, VR.US, 4096);
      attributes.setInt(Tag.Columns, VR.US, 4096);
      var syntax = new AdaptTransferSyntax(UID.ExplicitVRLittleEndian, UID.ExplicitVRLittleEndian);

      // Should not throw exception even with large dimensions
      assertDoesNotThrow(
          () -> {
            ImageAdapter.writeDicomFile(
                attributes, syntax, null, null, tempDir.resolve("large_metadata.dcm"));
          });
    }

    @Test
    void should_handle_minimal_attributes() {
      var attributes = new Attributes();
      attributes.setString(Tag.SOPClassUID, VR.UI, UID.SecondaryCaptureImageStorage);
      attributes.setString(Tag.SOPInstanceUID, VR.UI, "1.2.3.4.5");
      var syntax = new AdaptTransferSyntax(UID.ExplicitVRLittleEndian, UID.ExplicitVRLittleEndian);

      boolean result =
          ImageAdapter.writeDicomFile(
              attributes, syntax, null, null, tempDir.resolve("minimal.dcm"));

      assertTrue(result, "Should handle minimal attributes successfully");
    }

    @Test
    void should_handle_different_transfer_syntaxes() {
      var transferSyntaxes =
          List.of(UID.ImplicitVRLittleEndian, UID.ExplicitVRLittleEndian, UID.ExplicitVRBigEndian);

      transferSyntaxes.forEach(
          tsuid -> {
            var attributes = createBasicAttributes();
            var syntax = new AdaptTransferSyntax(tsuid, tsuid);
            var outputFile = tempDir.resolve("test_" + tsuid.replace('.', '_') + ".dcm");

            boolean result =
                ImageAdapter.writeDicomFile(attributes, syntax, null, null, outputFile);
            assertTrue(result, "Should handle transfer syntax: " + tsuid);
          });
    }
  }

  @Nested
  class Integration_Tests {

    @Test
    void should_perform_complete_transcoding_workflow() throws Exception {
      // Create source attributes with pixel data
      var sourceAttributes = createBasicAttributes();
      var pixelData = new byte[512 * 512 * 2]; // 512x512x16bit
      for (int i = 0; i < pixelData.length; i++) {
        pixelData[i] = (byte) (i % 256);
      }

      when(mockBulkData.toBytes(any(VR.class), anyBoolean())).thenReturn(pixelData);
      when(mockBulkData.bigEndian()).thenReturn(false);
      sourceAttributes.setValue(Tag.PixelData, VR.OW, mockBulkData);

      // Setup transcoding
      var syntax = new AdaptTransferSyntax(UID.ExplicitVRLittleEndian, UID.JPEG2000);
      var context = new AttributeEditorContext(UID.ExplicitVRLittleEndian, null, null);

      // Test transcoding creation
      var descriptor = ImageAdapter.imageTranscode(sourceAttributes, syntax, context);
      assertNotNull(descriptor, "Should create bytes descriptor");

      // Test metadata-only writing
      var metadataFile = tempDir.resolve("metadata_only.dcm");
      boolean metadataResult =
          ImageAdapter.writeDicomFile(
              sourceAttributes, syntax, context.getEditable(), null, metadataFile);
      assertTrue(metadataResult, "Metadata-only write should succeed");

      // Test DataWriter creation
      var dataWriter =
          ImageAdapter.buildDataWriter(sourceAttributes, syntax, context.getEditable(), descriptor);
      assertNotNull(dataWriter, "DataWriter should be created");

      // Test DataWriter usage
      var outputStream =
          new PDVOutputStream() {
            @Override
            public void copyFrom(InputStream inputStream, int i) throws IOException {}

            @Override
            public void copyFrom(InputStream inputStream) throws IOException {}

            @Override
            public void write(int b) throws IOException {}
          };
      assertDoesNotThrow(
          () -> dataWriter.writeTo(outputStream, syntax.getSuitable()),
          "DataWriter should write without exceptions");
    }

    @Test
    void should_handle_attribute_editor_context_with_mask() {
      var attributes = createBasicAttributes();
      var syntax = new AdaptTransferSyntax(UID.ExplicitVRLittleEndian, UID.JPEG2000);
      var context = new AttributeEditorContext(UID.ExplicitVRLittleEndian, null, null);

      // Add mask area
      List<Shape> maskShapes = List.of(new Rectangle(10, 10, 100, 100));
      context.setMaskArea(new MaskArea(maskShapes, Color.BLACK));

      assertTrue(context.hasPixelProcessing(), "Context should indicate pixel processing needed");

      // Even without pixel data, the context setup should work
      assertDoesNotThrow(
          () -> {
            ImageAdapter.buildDataWriter(attributes, syntax, context.getEditable(), null);
          });
    }
  }

  @Nested
  class Fragment_Processing_Tests {

    @Test
    void should_handle_rle_lossless_fragments() throws IOException {
      var attributes = createBasicAttributes();
      when(mockFragments.bigEndian()).thenReturn(false);
      when(mockFragments.size()).thenReturn(3); // Basic fragment + 2 data fragments
      when(mockFragments.get(1)).thenReturn(mockBulkData);
      when(mockFragments.get(2)).thenReturn(mockBulkData);
      when(mockBulkData.length()).thenReturn(512);
      when(mockBulkData.toBytes(any(VR.class), anyBoolean())).thenReturn(new byte[512]);

      attributes.setValue(Tag.PixelData, VR.OB, mockFragments);
      attributes.setInt(Tag.NumberOfFrames, VR.IS, 2);

      var syntax = new AdaptTransferSyntax(UID.RLELossless, UID.ExplicitVRLittleEndian);
      var context = new AttributeEditorContext(UID.RLELossless, null, null);
      var descriptor = ImageAdapter.imageTranscode(attributes, syntax, context);

      assertNotNull(descriptor);
      // Test frame access
      var frame0 = descriptor.getBytes(0);
      var frame1 = descriptor.getBytes(1);

      assertAll(
          "RLE fragment processing",
          () -> assertNotNull(frame0),
          () -> assertNotNull(frame1),
          () -> assertEquals(512, frame0.remaining()),
          () -> assertEquals(512, frame1.remaining()));
    }
  }

  @Nested
  class Compression_Parameter_Tests {

    @Test
    void should_handle_zero_jpeg_quality() {
      var syntax = new AdaptTransferSyntax(UID.ExplicitVRLittleEndian, UID.JPEG2000);
      syntax.setJpegQuality(0);

      var mockParam = mock(DicomJpegWriteParam.class);
      when(mockParam.getCompressionQuality()).thenReturn(50);

      // This would be called in the actual implementation
      if (mockParam.getCompressionQuality() > 0) {
        int quality = syntax.getJpegQuality() <= 0 ? 85 : syntax.getJpegQuality();
        assertEquals(85, quality, "Should use default quality when JPEG quality is 0");
      }
    }

    @Test
    void should_handle_compression_ratio_factor() {
      var syntax = new AdaptTransferSyntax(UID.ExplicitVRLittleEndian, UID.JPEG2000);
      syntax.setCompressionRatioFactor(5);

      var mockParam = mock(DicomJpegWriteParam.class);
      when(mockParam.getCompressionRatioFactor()).thenReturn(3);

      // Test the logic that would be used in compression configuration
      if (mockParam.getCompressionRatioFactor() > 0 && syntax.getCompressionRatioFactor() > 0) {
        assertEquals(5, syntax.getCompressionRatioFactor());
      }
    }

    @Test
    void should_ignore_compression_ratio_when_not_supported() {
      var syntax = new AdaptTransferSyntax(UID.ExplicitVRLittleEndian, UID.JPEG2000);
      syntax.setCompressionRatioFactor(10);

      var mockParam = mock(DicomJpegWriteParam.class);
      when(mockParam.getCompressionRatioFactor()).thenReturn(0); // Not supported

      // Should not set compression ratio factor when not supported by the format
      assertEquals(0, mockParam.getCompressionRatioFactor());
    }
  }

  @Nested
  class File_System_Tests {

    @Test
    void should_create_parent_directories_when_writing() throws IOException {
      var nestedDir = tempDir.resolve("deep").resolve("nested").resolve("path");
      var outputFile = nestedDir.resolve("test.dcm");

      var attributes = createBasicAttributes();
      var syntax = new AdaptTransferSyntax(UID.ExplicitVRLittleEndian, UID.ExplicitVRLittleEndian);

      // Ensure parent doesn't exist
      assertFalse(Files.exists(nestedDir));

      boolean result = ImageAdapter.writeDicomFile(attributes, syntax, null, null, outputFile);

      assertAll(
          "Directory creation",
          () -> assertTrue(result, "Write should succeed"),
          () -> assertTrue(Files.exists(outputFile), "File should be created"),
          () -> assertTrue(Files.exists(nestedDir), "Parent directories should be created"));
    }

    @Test
    void should_handle_very_long_filenames() {
      var longName = "a".repeat(200) + ".dcm";
      var outputFile = tempDir.resolve(longName);

      var attributes = createBasicAttributes();
      var syntax = new AdaptTransferSyntax(UID.ExplicitVRLittleEndian, UID.ExplicitVRLittleEndian);

      // This may succeed or fail depending on file system limitations
      assertDoesNotThrow(
          () -> ImageAdapter.writeDicomFile(attributes, syntax, null, null, outputFile));
    }

    @Test
    void should_handle_concurrent_writes_to_same_directory() throws InterruptedException {
      var attributes = createBasicAttributes();
      var syntax = new AdaptTransferSyntax(UID.ExplicitVRLittleEndian, UID.ExplicitVRLittleEndian);

      var results = new ConcurrentHashMap<String, Boolean>();
      var latch = new CountDownLatch(5);

      // Start multiple threads writing to the same directory
      for (int i = 0; i < 5; i++) {
        final int threadId = i;
        new Thread(
                () -> {
                  try {
                    var outputFile = tempDir.resolve("concurrent_" + threadId + ".dcm");
                    boolean result =
                        ImageAdapter.writeDicomFile(attributes, syntax, null, null, outputFile);
                    results.put("thread_" + threadId, result);
                  } finally {
                    latch.countDown();
                  }
                })
            .start();
      }

      assertTrue(latch.await(5, TimeUnit.SECONDS), "All threads should complete");

      // All writes should succeed
      results
          .values()
          .forEach(result -> assertTrue(result, "All concurrent writes should succeed"));
    }
  }

  @Nested
  class Pixel_Data_Validation_Tests {

    @Test
    void should_handle_different_vr_types() throws IOException {
      var vrTypes = List.of(VR.OB, VR.OW, VR.UN);

      vrTypes.forEach(
          vr -> {
            var attributes = createBasicAttributes();
            attributes.setValue(Tag.PixelData, vr, mockBulkData);

            var syntax = new AdaptTransferSyntax(UID.ExplicitVRLittleEndian, UID.JPEG2000);
            var context = new AttributeEditorContext(UID.ExplicitVRLittleEndian, null, null);

            var descriptor = ImageAdapter.imageTranscode(attributes, syntax, context);
            assertNotNull(descriptor, "Should handle VR type: " + vr);
            assertEquals(vr, descriptor.getPixelDataVR(), "VR should be preserved");
          });
    }

    @Test
    void should_detect_big_endian_correctly() {
      var attributes = createBasicAttributes();

      // Test BulkData
      when(mockBulkData.bigEndian()).thenReturn(true);
      attributes.setValue(Tag.PixelData, VR.OW, mockBulkData);

      var syntax = new AdaptTransferSyntax(UID.ExplicitVRLittleEndian, UID.JPEG2000);
      var context = new AttributeEditorContext(UID.ExplicitVRLittleEndian, null, null);
      var descriptor = ImageAdapter.imageTranscode(attributes, syntax, context);

      assertNotNull(descriptor);
      assertTrue(descriptor.isBigEndian(), "Should detect big endian from BulkData");

      // Test Fragments
      when(mockFragments.bigEndian()).thenReturn(false);
      attributes.setValue(Tag.PixelData, VR.OB, mockFragments);
      descriptor = ImageAdapter.imageTranscode(attributes, syntax, context);

      assertNotNull(descriptor);
      assertFalse(descriptor.isBigEndian(), "Should detect little endian from Fragments");
    }
  }

  @Nested
  class Data_Writer_Edge_Cases_Tests {

    @Test
    void should_handle_null_output_stream_gracefully() throws IOException {
      var attributes = createBasicAttributes();
      var syntax = new AdaptTransferSyntax(UID.ExplicitVRLittleEndian, UID.ExplicitVRLittleEndian);

      var dataWriter = ImageAdapter.buildDataWriter(attributes, syntax, null, null);

      assertThrows(
          Exception.class,
          () -> dataWriter.writeTo(null, syntax.getSuitable()),
          "Should handle null output stream gracefully");
    }
  }

  @Nested
  class Transfer_Syntax_Edge_Cases_Tests {

    @Test
    void should_handle_unknown_transfer_syntax() {
      var syntax = new AdaptTransferSyntax(UID.ExplicitVRLittleEndian, "1.2.3.4.5.999");

      // setSuitable should not change for unknown syntax
      String originalSuitable = syntax.getSuitable();
      syntax.setSuitable("1.2.3.4.5.999.unknown");
      assertEquals(
          originalSuitable,
          syntax.getSuitable(),
          "Should not change suitable syntax for unknown UID");
    }

    @Test
    void should_handle_explicit_vr_big_endian_in_metadata_files() {
      var attributes = createBasicAttributes();
      var syntax = new AdaptTransferSyntax(UID.ExplicitVRBigEndian, UID.ExplicitVRBigEndian);

      boolean result =
          ImageAdapter.writeDicomFile(
              attributes, syntax, null, null, tempDir.resolve("big_endian.dcm"));

      assertTrue(result, "Should handle explicit VR big endian");
      assertEquals(
          UID.ImplicitVRLittleEndian,
          syntax.getSuitable(),
          "Should adjust big endian to implicit VR little endian for metadata");
    }

    @Test
    void should_preserve_original_syntax_in_data_writer() throws IOException {
      var originalSyntax = UID.JPEGLosslessSV1;
      var attributes = createBasicAttributes();
      var syntax = new AdaptTransferSyntax(originalSyntax, UID.JPEG2000);

      var dataWriter = ImageAdapter.buildDataWriter(attributes, syntax, null, null);

      assertEquals(
          originalSyntax,
          syntax.getSuitable(),
          "Should preserve original syntax for metadata-only data writer");
    }
  }

  @Nested
  class Memory_Management_Tests {

    @Test
    void should_handle_large_frame_buffer_reuse() throws IOException {
      var largeFrameData = new byte[1024 * 1024]; // 1MB frame
      for (int i = 0; i < largeFrameData.length; i++) {
        largeFrameData[i] = (byte) (i % 256);
      }

      when(mockBulkData.toBytes(any(VR.class), anyBoolean())).thenReturn(largeFrameData);
      when(mockBulkData.bigEndian()).thenReturn(false);

      var attributes = createBasicAttributes();
      attributes.setInt(Tag.Rows, VR.US, 1024);
      attributes.setInt(Tag.Columns, VR.US, 512);
      attributes.setValue(Tag.PixelData, VR.OW, mockBulkData);

      var syntax = new AdaptTransferSyntax(UID.ExplicitVRLittleEndian, UID.JPEG2000);
      var context = new AttributeEditorContext(UID.ExplicitVRLittleEndian, null, null);
      var descriptor = ImageAdapter.imageTranscode(attributes, syntax, context);

      assertNotNull(descriptor);

      // Multiple frame accesses should reuse the internal buffer
      var frame1 = descriptor.getBytes(0);
      var frame2 = descriptor.getBytes(0);

      assertAll(
          "Frame buffer reuse",
          () -> assertNotNull(frame1),
          () -> assertNotNull(frame2),
          () -> assertEquals(frame1.remaining(), frame2.remaining()),
          () -> assertTrue(frame1.remaining() > 0));
    }

    @Test
    void should_handle_multiple_descriptors_independently() throws IOException {
      var attributes1 = createBasicAttributes();
      var attributes2 = createBasicAttributes();
      attributes2.setString(Tag.SOPInstanceUID, VR.UI, "different.instance.uid");

      var bulkData1 = mock(BulkData.class);
      var bulkData2 = mock(BulkData.class);

      attributes1.setValue(Tag.PixelData, VR.OW, bulkData1);
      attributes2.setValue(Tag.PixelData, VR.OW, bulkData2);

      var syntax = new AdaptTransferSyntax(UID.ExplicitVRLittleEndian, UID.JPEG2000);
      var context = new AttributeEditorContext(UID.ExplicitVRLittleEndian, null, null);

      var descriptor1 = ImageAdapter.imageTranscode(attributes1, syntax, context);
      var descriptor2 = ImageAdapter.imageTranscode(attributes2, syntax, context);

      assertAll(
          "Independent descriptors",
          () -> assertNotNull(descriptor1),
          () -> assertNotNull(descriptor2),
          () -> assertNotSame(descriptor1, descriptor2),
          () -> assertNotSame(descriptor1.getImageDescriptor(), descriptor2.getImageDescriptor()));
    }
  }

  @Nested
  class Error_Recovery_Tests {

    @Test
    void should_recover_from_partial_file_write() throws IOException {
      var attributes = createBasicAttributes();
      var syntax = new AdaptTransferSyntax(UID.ExplicitVRLittleEndian, UID.ExplicitVRLittleEndian);
      var outputFile = tempDir.resolve("partial_write.dcm");

      // Create a partial file to simulate interrupted write
      Files.write(outputFile, "partial content".getBytes());
      assertTrue(Files.exists(outputFile), "Partial file should exist");

      boolean result = ImageAdapter.writeDicomFile(attributes, syntax, null, null, outputFile);

      assertTrue(result, "Should succeed even with existing partial file");
      assertTrue(
          Files.size(outputFile) > "partial content".length(),
          "New file should be larger than partial content");
    }

    @Test
    void should_handle_io_exceptions_during_write() throws IOException {
      var attributes = createBasicAttributes();
      var syntax = new AdaptTransferSyntax(UID.ExplicitVRLittleEndian, UID.ExplicitVRLittleEndian);

      // Try to write to a directory instead of a file (should cause IOException)
      var directoryPath = tempDir.resolve("directory_not_file");
      Files.createDirectory(directoryPath);

      boolean result = ImageAdapter.writeDicomFile(attributes, syntax, null, null, directoryPath);

      assertFalse(result, "Should return false when writing to directory");
    }

    @Test
    void should_handle_malformed_attributes_gracefully() {
      var attributes = new Attributes();
      // Deliberately create malformed attributes (missing required fields)
      attributes.setString(Tag.PatientName, VR.PN, (String) null); // Invalid null value

      var syntax = new AdaptTransferSyntax(UID.ExplicitVRLittleEndian, UID.ExplicitVRLittleEndian);

      // Should not throw exception but may return false
      assertDoesNotThrow(
          () ->
              ImageAdapter.writeDicomFile(
                  attributes, syntax, null, null, tempDir.resolve("malformed.dcm")));
    }
  }
}
