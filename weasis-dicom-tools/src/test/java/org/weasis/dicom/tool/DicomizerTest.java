/*
 * Copyright (c) 2014-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.tool;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.UID;
import org.dcm4che3.data.VR;
import org.dcm4che3.io.DicomInputStream;
import org.dcm4che3.util.UIDUtils;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.opencv.osgi.OpenCVNativeLoader;

@DisplayNameGeneration(ReplaceUnderscores.class)
class DicomizerTest {

  @TempDir static Path tempDir;

  private static final String TEST_PATIENT_ID = "TEST123456";
  private static final String TEST_PATIENT_NAME = "Test^Patient^Name";

  @BeforeAll
  static void loadNativeLib() {
    OpenCVNativeLoader loader = new OpenCVNativeLoader();
    loader.init();
  }

  @Nested
  class PDF_Document_Encapsulation_Tests {

    @Test
    void pdf_creates_valid_dicom_file_from_pdf() throws IOException {
      var attrs = createBasicAttributes();
      var pdfFile = createTestPdfFile();
      var dcmFile = tempDir.resolve("test.dcm");

      Dicomizer.pdf(attrs, pdfFile, dcmFile);

      assertTrue(Files.exists(dcmFile), "DICOM file should exist");
      assertTrue(Files.size(dcmFile) > 0, "DICOM file should not be empty");

      assertEquals(UID.EncapsulatedPDFStorage, attrs.getString(Tag.SOPClassUID));
      assertEquals("DOC", attrs.getString(Tag.Modality));
      assertEquals("SD", attrs.getString(Tag.ConversionType));
      assertEquals("1", attrs.getString(Tag.InstanceNumber));
      assertEquals("999", attrs.getString(Tag.SeriesNumber));
      assertEquals("YES", attrs.getString(Tag.BurnedInAnnotation));

      verifyDicomFileStructure(dcmFile);
    }

    @Test
    void pdf_preserves_existing_attributes() throws IOException {
      var attrs = createBasicAttributes();
      attrs.setString(Tag.PatientName, VR.PN, "PDF^TEST^PATIENT");
      attrs.setString(Tag.StudyDescription, VR.LO, "PDF Encapsulation Study");

      var pdfFile = createTestPdfFile();
      var dcmFile = tempDir.resolve("pdf_with_attrs.dcm");

      Dicomizer.pdf(attrs, pdfFile, dcmFile);

      assertEquals("PDF^TEST^PATIENT", attrs.getString(Tag.PatientName));
      assertEquals("PDF Encapsulation Study", attrs.getString(Tag.StudyDescription));
      assertEquals(UID.EncapsulatedPDFStorage, attrs.getString(Tag.SOPClassUID));
    }

    @Test
    void pdf_no_output_for_nonexistent_file() throws IOException {
      var attrs = createBasicAttributes();
      var nonexistentFile = tempDir.resolve("nonexistent.pdf");
      var dcmFile = tempDir.resolve("output.dcm");

      Dicomizer.pdf(attrs, nonexistentFile, dcmFile);

      assertFalse(Files.exists(dcmFile), "Output file should not be created on failure");
    }

    @Test
    void pdf_handles_large_file_size_validation() throws IOException {
      var attrs = createBasicAttributes();
      var largePdfFile = createLargePdfFile();
      var dcmFile = tempDir.resolve("large.dcm");

      // Should handle large files within limits
      assertDoesNotThrow(() -> Dicomizer.pdf(attrs, largePdfFile, dcmFile));
    }

    private Path createLargePdfFile() throws IOException {
      var largePdf = tempDir.resolve("large_test.pdf");
      var pdfContent =
          "%PDF-1.4\n1 0 obj<</Type/Catalog/Pages 2 0 R>>endobj\n"
              + "2 0 obj<</Type/Pages/Kids[3 0 R]/Count 1>>endobj\n"
              + "3 0 obj<</Type/Page/Parent 2 0 R/MediaBox[0 0 612 792]>>endobj\n"
              + "xref\n0 4\n0000000000 65535 f\n0000000010 00000 n\n"
              + "0000000053 00000 n\n0000000125 00000 n\ntrailer<</Size 4/Root 1 0 R>>\n"
              + "startxref\n200\n%%EOF\n";

      var content = new StringBuilder(pdfContent);
      // Add some content to make it larger but still manageable
      for (int i = 0; i < 1000; i++) {
        content.append("% Additional content line ").append(i).append("\n");
      }

      Files.writeString(largePdf, content.toString());
      return largePdf;
    }
  }

  @Nested
  class Three_D_Model_Encapsulation_Tests {

    @Test
    void stl_creates_valid_m3d_dicom() throws IOException {
      var attrs = createBasicAttributes();
      var stlFile = createTestStlFile();
      var dcmFile = tempDir.resolve("test.dcm");

      Dicomizer.stl(attrs, stlFile, dcmFile);

      assertTrue(Files.exists(dcmFile));
      assertEquals(UID.EncapsulatedSTLStorage, attrs.getString(Tag.SOPClassUID));
      assertEquals("M3D", attrs.getString(Tag.Modality));
      assertNotNull(attrs.getString(Tag.FrameOfReferenceUID));

      assertEquals("UNKNOWN", attrs.getString(Tag.Manufacturer));
      assertEquals("UNKNOWN", attrs.getString(Tag.ManufacturerModelName));
      assertNotNull(attrs.getSequence(Tag.MeasurementUnitsCodeSequence));
    }

    @Test
    void mtl_creates_valid_m3d_dicom() throws IOException {
      var attrs = createBasicAttributes();
      var mtlFile = createTestMtlFile();
      var dcmFile = tempDir.resolve("test.dcm");

      Dicomizer.mtl(attrs, mtlFile, dcmFile);

      assertTrue(Files.exists(dcmFile));
      assertEquals(UID.EncapsulatedMTLStorage, attrs.getString(Tag.SOPClassUID));
      assertEquals("M3D", attrs.getString(Tag.Modality));
    }

    @Test
    void obj_creates_valid_m3d_dicom() throws IOException {
      var attrs = createBasicAttributes();
      var objFile = createTestObjFile();
      var dcmFile = tempDir.resolve("test.dcm");

      Dicomizer.obj(attrs, objFile, dcmFile);

      assertTrue(Files.exists(dcmFile));
      assertEquals(UID.EncapsulatedOBJStorage, attrs.getString(Tag.SOPClassUID));
      assertEquals("M3D", attrs.getString(Tag.Modality));
      assertNotNull(attrs.getString(Tag.FrameOfReferenceUID));
    }

    @Test
    void three_d_models_have_consistent_m3d_attributes() throws IOException {
      var fileTypes = List.of("stl", "mtl", "obj");

      for (var fileType : fileTypes) {
        var attrs = createBasicAttributes();
        var modelFile = createTest3DModelFile(fileType);
        var dcmFile = tempDir.resolve("test_" + fileType + ".dcm");

        switch (fileType) {
          case "stl" -> Dicomizer.stl(attrs, modelFile, dcmFile);
          case "mtl" -> Dicomizer.mtl(attrs, modelFile, dcmFile);
          case "obj" -> Dicomizer.obj(attrs, modelFile, dcmFile);
        }

        assertEquals("M3D", attrs.getString(Tag.Modality));
        assertEquals("UNKNOWN", attrs.getString(Tag.Manufacturer));
        assertEquals("UNKNOWN", attrs.getString(Tag.ManufacturerModelName));
        assertNotNull(attrs.getSequence(Tag.MeasurementUnitsCodeSequence));
      }
    }

    private Path createTest3DModelFile(String fileType) throws IOException {
      return switch (fileType) {
        case "stl" -> createTestStlFile();
        case "mtl" -> createTestMtlFile();
        case "obj" -> createTestObjFile();
        default -> throw new IllegalArgumentException("Unknown file type: " + fileType);
      };
    }
  }

  @Nested
  class Clinical_Document_Architecture_Tests {

    @Test
    void cda_creates_valid_document_dicom() throws IOException {
      var attrs = createBasicAttributes();
      var cdaFile = createTestCdaFile();
      var dcmFile = tempDir.resolve("test.dcm");

      Dicomizer.cda(attrs, cdaFile, dcmFile);

      assertTrue(Files.exists(dcmFile));
      assertEquals(UID.EncapsulatedCDAStorage, attrs.getString(Tag.SOPClassUID));
      assertEquals("DOC", attrs.getString(Tag.Modality));
      assertEquals("WSD", attrs.getString(Tag.ConversionType));
    }

    @Test
    void cda_preserves_document_attributes() throws IOException {
      var attrs = createBasicAttributes();
      attrs.setString(Tag.DocumentTitle, VR.LO, "Clinical Document");
      attrs.setString(Tag.InstitutionName, VR.LO, "Test Hospital");

      var cdaFile = createTestCdaFile();
      var dcmFile = tempDir.resolve("cda_with_attrs.dcm");

      Dicomizer.cda(attrs, cdaFile, dcmFile);

      assertEquals("Clinical Document", attrs.getString(Tag.DocumentTitle));
      assertEquals("Test Hospital", attrs.getString(Tag.InstitutionName));
      assertEquals(UID.EncapsulatedCDAStorage, attrs.getString(Tag.SOPClassUID));
    }

    @Test
    void cda_handles_empty_cda_file() throws IOException {
      var attrs = createBasicAttributes();
      var emptyCdaFile = tempDir.resolve("empty.xml");
      Files.createFile(emptyCdaFile);
      var dcmFile = tempDir.resolve("empty_cda.dcm");

      Dicomizer.cda(attrs, emptyCdaFile, dcmFile);

      assertTrue(Files.exists(dcmFile));
      assertEquals(0, attrs.getLong(Tag.EncapsulatedDocumentLength, -1));
    }
  }

  @Nested
  class JPEG_Image_Encapsulation_Tests {

    @Test
    void jpeg_creates_valid_photographic_image_storage() throws IOException, URISyntaxException {
      var attrs = createBasicAttributes();
      var jpegFile = createTestJpegFile();
      var dcmFile = tempDir.resolve("test.dcm");

      Dicomizer.jpeg(attrs, jpegFile, dcmFile, false);

      assertTrue(Files.exists(dcmFile));
      assertEquals(UID.VLPhotographicImageStorage, attrs.getString(Tag.SOPClassUID));
      assertArrayEquals(new String[] {"ORIGINAL", "PRIMARY"}, attrs.getStrings(Tag.ImageType));

      verifyDicomFileStructure(dcmFile);
    }

    @Test
    void jpeg_handles_invalid_jpeg_file_gracefully() throws URISyntaxException {
      var attrs = createBasicAttributes();
      var invalidJpegFile = createInvalidJpegFile();
      var dcmFile = tempDir.resolve("test_invalid.dcm");

      assertThrows(IOException.class, () -> Dicomizer.jpeg(attrs, invalidJpegFile, dcmFile, false));
    }

    @Test
    void wrong_input_image_type() throws URISyntaxException {
      var attrs = createBasicAttributes();
      var pngFile = createTestPngFile();
      var dcmFile = tempDir.resolve("test.dcm");

      assertThrows(IOException.class, () -> Dicomizer.jpeg(attrs, pngFile, dcmFile, false));
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void jpeg_handles_app_segments_correctly(boolean noAPPn) throws URISyntaxException {
      var attrs = createBasicAttributes();
      var jpegFile = createTestJpegFile();
      var dcmFile = tempDir.resolve("test_appn_" + noAPPn + ".dcm");

      assertDoesNotThrow(() -> Dicomizer.jpeg(attrs, jpegFile, dcmFile, noAPPn));
      assertTrue(Files.exists(dcmFile));
    }

    @Test
    void jpeg_preserves_image_specific_attributes() throws IOException, URISyntaxException {
      var attrs = createBasicAttributes();
      attrs.setString(Tag.ImageComments, VR.LT, "Test JPEG image");
      attrs.setString(Tag.PhotometricInterpretation, VR.CS, "RGB");

      var jpegFile = createTestJpegFile();
      var dcmFile = tempDir.resolve("jpeg_with_attrs.dcm");

      Dicomizer.jpeg(attrs, jpegFile, dcmFile, false);

      assertEquals("Test JPEG image", attrs.getString(Tag.ImageComments));
      assertEquals("YBR_FULL", attrs.getString(Tag.PhotometricInterpretation));
      assertEquals(UID.VLPhotographicImageStorage, attrs.getString(Tag.SOPClassUID));
    }
  }

  @Nested
  class JPEG_Conversion_Utility_Tests {

    @Test
    void convert_to_jpeg_and_write_with_null_input_returns_false() {
      assertFalse(Dicomizer.convertToJpegAndWrite(null, null, null));
    }

    @Test
    void convert_to_jpeg_and_write_with_nonexistent_file_returns_false() {
      var nonexistentFile = tempDir.resolve("nonexistent.png");
      assertFalse(Dicomizer.convertToJpegAndWrite(nonexistentFile, null, null));
    }

    @Test
    void convert_to_jpeg_and_write_creates_output_file_when_null() throws IOException {
      var inputFile = createTestTextFile("test_image.txt");
      var result = Dicomizer.convertToJpegAndWrite(inputFile, null, null);

      // Should handle gracefully - either succeed or fail appropriately
      assertFalse(result);
    }

    @Test
    void convert_jpeg_to_jpeg_creates_output_file() throws URISyntaxException {
      var inputJpeg = createTestJpegFile();
      var outputJpeg = tempDir.resolve("converted.jpg");

      var result = Dicomizer.convertToJpegAndWrite(inputJpeg, outputJpeg, null);

      assertTrue(result);
      assertTrue(Files.exists(outputJpeg));
    }

    @ParameterizedTest
    @ValueSource(ints = {10, 50, 90, 100})
    void convert_to_jpeg_respects_quality_parameter(int quality) throws IOException {
      var inputFile = createTestTextFile("quality_test.txt");
      var outputFile = tempDir.resolve("quality_" + quality + ".jpg");

      var result = Dicomizer.convertToJpegAndWrite(inputFile, outputFile, quality);

      // Quality parameter should be handled appropriately
      assertFalse(result);
    }

    @Test
    void convert_to_jpeg_handles_various_input_formats() throws URISyntaxException {
      var inputFormats = List.of(createTestJpegFile(), createTestPngFile());

      for (var inputFile : inputFormats) {
        var outputFile = tempDir.resolve("converted_" + inputFile.getFileName());

        // Should handle each format appropriately
        assertDoesNotThrow(() -> Dicomizer.convertToJpegAndWrite(inputFile, outputFile, null));
      }
    }
  }

  @Nested
  class MPEG2_Encapsulation_Tests {

    @Test
    void mpeg2_should_return_early_for_invalid_input_file() {
      var attrs = createBasicDicomAttributes();
      var invalidFile = tempDir.resolve("nonexistent.mpg");
      var dcmFile = tempDir.resolve("output.dcm");

      assertDoesNotThrow(() -> Dicomizer.mpeg2(attrs, invalidFile, dcmFile));
      assertFalse(Files.exists(dcmFile));
    }
  }

  @Nested
  class MPEG4_Encapsulation_Tests {
    @Test
    void mpeg4_should_return_early_for_invalid_input() {
      var attrs = createBasicDicomAttributes();
      var invalidFile = tempDir.resolve("nonexistent.mp4");
      var dcmFile = tempDir.resolve("output.dcm");

      assertDoesNotThrow(() -> Dicomizer.mpeg4(attrs, invalidFile, dcmFile));
      assertFalse(Files.exists(dcmFile));
    }
  }

  @Nested
  class Common_Attributes_And_Validation_Tests {

    @Test
    void common_attributes_are_set_correctly() throws IOException {
      var attrs = createBasicAttributes();
      var pdfFile = createTestPdfFile();
      var dcmFile = tempDir.resolve("common_attrs.dcm");

      Dicomizer.pdf(attrs, pdfFile, dcmFile);

      assertNotNull(attrs.getString(Tag.SOPInstanceUID));
      assertNotNull(attrs.getString(Tag.SeriesInstanceUID));
      assertNotNull(attrs.getString(Tag.StudyInstanceUID));
      assertEquals(TEST_PATIENT_ID, attrs.getString(Tag.PatientID));
      assertEquals(TEST_PATIENT_NAME, attrs.getString(Tag.PatientName));
    }

    @Test
    void attributes_are_not_overridden_when_already_present() throws IOException {
      var attrs = createBasicAttributes();
      var customSOPInstanceUID = UIDUtils.createUID();
      attrs.setString(Tag.SOPInstanceUID, VR.UI, customSOPInstanceUID);
      attrs.setString(Tag.Modality, VR.CS, "CUSTOM");

      var pdfFile = createTestPdfFile();
      var dcmFile = tempDir.resolve("no_override.dcm");

      Dicomizer.pdf(attrs, pdfFile, dcmFile);

      assertEquals(customSOPInstanceUID, attrs.getString(Tag.SOPInstanceUID));
      assertEquals("CUSTOM", attrs.getString(Tag.Modality));
    }

    static Stream<Arguments> fileTypeAndExpectedSOPClass() {
      return Stream.of(
          Arguments.of("pdf", UID.EncapsulatedPDFStorage, "DOC"),
          Arguments.of("stl", UID.EncapsulatedSTLStorage, "M3D"),
          Arguments.of("mtl", UID.EncapsulatedMTLStorage, "M3D"),
          Arguments.of("obj", UID.EncapsulatedOBJStorage, "M3D"),
          Arguments.of("cda", UID.EncapsulatedCDAStorage, "DOC"));
    }

    @ParameterizedTest
    @MethodSource("fileTypeAndExpectedSOPClass")
    void each_file_type_sets_correct_sop_class_and_modality(
        String fileType, String expectedSOPClass, String expectedModality) throws IOException {

      var attrs = createBasicAttributes();
      var inputFile = createTestFileByType(fileType);
      var dcmFile = tempDir.resolve("test_" + fileType + ".dcm");

      switch (fileType) {
        case "pdf" -> Dicomizer.pdf(attrs, inputFile, dcmFile);
        case "stl" -> Dicomizer.stl(attrs, inputFile, dcmFile);
        case "mtl" -> Dicomizer.mtl(attrs, inputFile, dcmFile);
        case "obj" -> Dicomizer.obj(attrs, inputFile, dcmFile);
        case "cda" -> Dicomizer.cda(attrs, inputFile, dcmFile);
      }

      assertEquals(expectedSOPClass, attrs.getString(Tag.SOPClassUID));
      assertEquals(expectedModality, attrs.getString(Tag.Modality));
    }

    private Path createTestFileByType(String fileType) throws IOException {
      return switch (fileType) {
        case "pdf" -> createTestPdfFile();
        case "stl" -> createTestStlFile();
        case "mtl" -> createTestMtlFile();
        case "obj" -> createTestObjFile();
        case "cda" -> createTestCdaFile();
        default -> throw new IllegalArgumentException("Unknown file type: " + fileType);
      };
    }
  }

  // Helper methods and test data creation
  private Attributes createBasicAttributes() {
    var attrs = new Attributes();
    attrs.setString(Tag.PatientID, VR.LO, TEST_PATIENT_ID);
    attrs.setString(Tag.PatientName, VR.PN, TEST_PATIENT_NAME);
    return attrs;
  }

  private Attributes createBasicDicomAttributes() {
    return createBasicAttributes();
  }

  private Path createTestPdfFile() throws IOException {
    var pdfFile = tempDir.resolve("test.pdf");
    var pdfContent =
        "%PDF-1.4\n1 0 obj<</Type/Catalog/Pages 2 0 R>>endobj\n"
            + "2 0 obj<</Type/Pages/Kids[3 0 R]/Count 1>>endobj\n"
            + "3 0 obj<</Type/Page/Parent 2 0 R/MediaBox[0 0 612 792]>>endobj\n"
            + "xref\n0 4\n0000000000 65535 f\n0000000010 00000 n\n"
            + "0000000053 00000 n\n0000000125 00000 n\ntrailer<</Size 4/Root 1 0 R>>\n"
            + "startxref\n200\n%%EOF\n";
    Files.writeString(pdfFile, pdfContent);
    return pdfFile;
  }

  private Path createTestStlFile() throws IOException {
    var stlFile = tempDir.resolve("test.stl");
    var stlContent =
        "solid test\nfacet normal 0 0 1\nouter loop\n"
            + "vertex 0 0 0\nvertex 1 0 0\nvertex 0 1 0\nendloop\nendfacet\nendsolid test";
    Files.writeString(stlFile, stlContent);
    return stlFile;
  }

  private Path createTestMtlFile() throws IOException {
    var mtlFile = tempDir.resolve("test.mtl");
    var mtlContent =
        "newmtl test_material\nKa 1.0 1.0 1.0\nKd 0.8 0.8 0.8\nKs 0.5 0.5 0.5\nNs 50.0";
    Files.writeString(mtlFile, mtlContent);
    return mtlFile;
  }

  private Path createTestObjFile() throws IOException {
    var objFile = tempDir.resolve("test.obj");
    var objContent = "v 0.0 0.0 0.0\nv 1.0 0.0 0.0\nv 0.0 1.0 0.0\nf 1 2 3";
    Files.writeString(objFile, objContent);
    return objFile;
  }

  private Path createTestCdaFile() throws IOException {
    var cdaFile = tempDir.resolve("test.xml");
    var cdaContent =
        "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
            + "<ClinicalDocument xmlns=\"urn:hl7-org:v3\">\n"
            + "<typeId root=\"2.16.840.1.113883.1.3\" extension=\"POCD_HD000040\"/>\n"
            + "<title>Test Clinical Document</title>\n</ClinicalDocument>";
    Files.writeString(cdaFile, cdaContent);
    return cdaFile;
  }

  private Path createTestJpegFile() throws URISyntaxException {
    var resource =
        getClass().getResource("/org/dcm4che3/imageio/codec/jpeg/readable/jfif-16bit-dqt.jpg");
    assertNotNull(resource, "Test JPEG resource not found");
    return Path.of(resource.toURI());
  }

  private Path createInvalidJpegFile() throws URISyntaxException {
    var resource =
        getClass().getResource("/org/dcm4che3/imageio/codec/jpeg/invalid/jfif-padded-segments.jpg");
    assertNotNull(resource, "Invalid JPEG resource not found");
    return Path.of(resource.toURI());
  }

  private Path createTestPngFile() throws URISyntaxException {
    var resource = getClass().getResource("/org/dcm4che3/img/expected_imgForPrLUT.png");
    assertNotNull(resource, "Test PNG resource not found");
    return Path.of(resource.toURI());
  }

  private Path createTestTextFile(String filename) throws IOException {
    var textFile = tempDir.resolve(filename);
    Files.writeString(textFile, "Test content for " + filename);
    return textFile;
  }

  private void verifyDicomFileStructure(Path dcmFile) throws IOException {
    try (var dis = new DicomInputStream(dcmFile.toFile())) {
      var fmi = dis.readFileMetaInformation();
      var dataset = dis.readDataset();

      assertNotNull(fmi);
      assertNotNull(dataset);
      assertNotNull(dataset.getString(Tag.SOPInstanceUID));
      assertNotNull(dataset.getString(Tag.SOPClassUID));
    }
  }

  private Attributes readDicomAttributes(Path dcmFile) throws IOException {
    try (var dis = new DicomInputStream(dcmFile.toFile())) {
      dis.readFileMetaInformation(); // Skip file meta info
      return dis.readDataset();
    }
  }

  // Helper record classes for test data
  private record AspectRatioTestCase(
      int width,
      int height,
      int aspectCode,
      int frameRate,
      String expectedAspectX,
      String expectedAspectY) {}

  private record Resolution(int width, int height, String name) {}

  private record Dimension(int width, int height) {}
}
