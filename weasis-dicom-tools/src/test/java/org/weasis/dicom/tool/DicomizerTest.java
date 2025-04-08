/*
 * Copyright (c) 2025 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.tool;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.UID;
import org.dcm4che3.data.VR;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class DicomizerTest {

  static final Path OUT_DIR = FileSystems.getDefault().getPath("target/test-out/");

  private static byte[] generateExamplePdfBytes() {
    String pdfContent =
        "%PDF-1.4\n%Test PDF File\n1 0 obj << /Type /Catalog /Pages 2 0 R >> endobj\n"
            + "2 0 obj << /Type /Pages /Count 1 /Kids [3 0 R] >> endobj\n"
            + "3 0 obj << /Type /Page /Parent 2 0 R /MediaBox [0 0 612 792] /Contents 4 0 R >> endobj\n"
            + "4 0 obj << /Length 44 >> stream\nBT /F1 24 Tf 100 700 Td (Hello PDF World) Tj ET\nendstream\n"
            + "endobj\nxref\n0 5\n0000000000 65535 f \n0000000010 00000 n \n0000000060 00000 n \n0000000111 00000 n \n"
            + "0000000213 00000 n \ntrailer << /Size 5 /Root 1 0 R >>\nstartxref\n322\n%%EOF";
    return pdfContent.getBytes();
  }

  @BeforeAll
  static void setUp() {
    try {
      Files.createDirectories(OUT_DIR);
    } catch (IOException e) {
      System.err.println(e.getMessage());
    }
  }

  @Test
  void pdf_createsDicomFileFromPdf() throws IOException {
    Attributes attrs = new Attributes();
    attrs.setSpecificCharacterSet("ISO_IR 144");
    attrs.setString(Tag.Modality, VR.CS, "OT");
    attrs.setString(Tag.PatientID, VR.LO, "123456");
    attrs.setString(Tag.PatientName, VR.PN, "TEST-i18n-Люкceмбypг");
    File pdfFile = new File(OUT_DIR.toFile(), "test.pdf");
    try (FileOutputStream fos = new FileOutputStream(pdfFile)) {
      fos.write(generateExamplePdfBytes());
    }

    File dcmFile = new File(OUT_DIR.toFile(), "pdf.dcm");
    Dicomizer.pdf(attrs, pdfFile, dcmFile);
    Assertions.assertTrue(dcmFile.exists());
    Assertions.assertEquals(UID.EncapsulatedPDFStorage, attrs.getString(Tag.SOPClassUID));
  }

  @Test
  void pdf_throwsIOExceptionForInvalidFile() {
    Attributes attrs = new Attributes();
    File pdfFile = new File("invalid.pdf");
    File dcmFile = new File(OUT_DIR.toFile(), "test.dcm");

    Assertions.assertThrows(IOException.class, () -> Dicomizer.pdf(attrs, pdfFile, dcmFile));
  }
}
