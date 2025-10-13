/*
 * Copyright (c) 2014-2019 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.dcm4che3.tool.common;

import java.io.File;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import javax.xml.XMLConstants;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.UID;
import org.dcm4che3.io.ContentHandlerAdapter;
import org.dcm4che3.io.DicomInputStream;
import org.dcm4che3.io.DicomInputStream.IncludeBulkData;

/**
 * Utility class for scanning DICOM files and directories. Supports both binary DICOM files and
 * XML-formatted DICOM files.
 *
 * @author Gunter Zeilinger <gunterze@gmail.com>
 */
public abstract class DicomFiles {

  private static SAXParser saxParser;

  /** Callback interface for processing discovered DICOM files. */
  public interface Callback {
    /**
     * Process a discovered DICOM file.
     *
     * @param f the file being processed
     * @param fmi the file meta information
     * @param dsPos the dataset position in the file (-1 for XML files)
     * @param ds the dataset attributes
     * @return true if processing was successful, false otherwise
     * @throws Exception if processing fails
     */
    boolean dicomFile(File f, Attributes fmi, long dsPos, Attributes ds) throws Exception;
  }

  /**
   * Scans the given file paths for DICOM files with printout enabled.
   *
   * @param fileNames list of file or directory paths to scan
   * @param scb callback to process each discovered DICOM file
   */
  public static void scan(List<String> fileNames, Callback scb) {
    scan(fileNames, true, scb);
  }

  /**
   * Scans the given file paths for DICOM files.
   *
   * @param fileNames list of file or directory paths to scan
   * @param printout whether to print progress indicators
   * @param scb callback to process each discovered DICOM file
   */
  public static void scan(List<String> fileNames, boolean printout, Callback scb) {
    fileNames.forEach(filePath -> scanPath(Path.of(filePath), printout, scb));
  }

  private static void scanPath(Path path, boolean printout, Callback scb) {
    if (Files.isDirectory(path) && Files.isReadable(path)) {
      scanDirectory(path, printout, scb);
    } else if (Files.isRegularFile(path)) {
      scanFile(path, printout, scb);
    }
  }

  private static void scanDirectory(Path dir, boolean printout, Callback scb) {
    try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir)) {
      for (Path entry : stream) {
        scanPath(entry, printout, scb);
      }
    } catch (IOException e) {
      handleError("Failed to scan directory " + dir, e);
    }
  }

  private static void scanFile(Path path, boolean printout, Callback scb) {
    String fileName = path.getFileName().toString();
    if (fileName.endsWith(".xml")) {
      processXmlFile(path, printout, scb);
    } else {
      processBinaryDicomFile(path, printout, scb);
    }
  }

  private static void processXmlFile(Path path, boolean printout, Callback scb) {
    try {
      var parser = getOrCreateSaxParser();
      var ds = new Attributes();
      var ch = new ContentHandlerAdapter(ds);

      parser.parse(path.toFile(), ch);

      var fmi = ch.getFileMetaInformation();
      if (fmi == null) {
        fmi = ds.createFileMetaInformation(UID.ExplicitVRLittleEndian);
      }
      boolean success = scb.dicomFile(path.toFile(), fmi, -1, ds);
      printProgress(printout, success);
    } catch (Exception e) {
      handleError("Failed to parse file " + path, e);
    }
  }

  private static void processBinaryDicomFile(Path path, boolean printout, Callback scb) {
    try (var in = new DicomInputStream(path.toFile())) {
      in.setIncludeBulkData(IncludeBulkData.NO);
      var fmi = in.readFileMetaInformation();
      long dsPos = in.getPosition();
      var ds = in.readDatasetUntilPixelData();

      if (isInvalidFileMetaInformation(fmi)) {
        fmi = ds.createFileMetaInformation(in.getTransferSyntax());
      }

      boolean success = scb.dicomFile(path.toFile(), fmi, dsPos, ds);
      printProgress(printout, success);

    } catch (Exception e) {
      handleError("Failed to scan file " + path, e);
    }
  }

  private static SAXParser getOrCreateSaxParser() throws Exception {
    if (saxParser == null) {
      var factory = SAXParserFactory.newInstance();
      factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
      saxParser = factory.newSAXParser();
    }
    return saxParser;
  }

  private static boolean isInvalidFileMetaInformation(Attributes fmi) {
    return fmi == null
        || !fmi.containsValue(Tag.TransferSyntaxUID)
        || !fmi.containsValue(Tag.MediaStorageSOPClassUID)
        || !fmi.containsValue(Tag.MediaStorageSOPInstanceUID);
  }

  private static void printProgress(boolean printout, boolean success) {
    if (printout) {
      System.out.print(success ? '.' : 'I');
    }
  }

  private static void handleError(String message, Exception e) {
    System.out.println();
    System.out.println(message + ": " + e.getMessage());
    e.printStackTrace(System.out);
  }
}
