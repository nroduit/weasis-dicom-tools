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
import org.dcm4che3.util.SafeClose;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 */
public abstract class DicomFiles {

  private static SAXParser saxParser;

  public interface Callback {
    boolean dicomFile(File f, Attributes fmi, long dsPos, Attributes ds) throws Exception;
  }

  public static void scan(List<String> fnames, Callback scb) {
    scan(fnames, true, scb); // default printout = true
  }

  public static void scan(List<String> fnames, boolean printout, Callback scb) {
    for (String fname : fnames) {
      scan(new File(fname), printout, scb);
    }
  }

  private static void scan(File f, boolean printout, Callback scb) {
    if (f.isDirectory()) {
      for (String s : f.list()) {
        scan(new File(f, s), printout, scb);
      }
      return;
    }
    if (f.getName().endsWith(".xml")) {
      try {
        SAXParser p = saxParser;
        if (p == null) {
          SAXParserFactory factory = SAXParserFactory.newInstance();
          factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
          saxParser = p = factory.newSAXParser();
        }
        Attributes ds = new Attributes();
        ContentHandlerAdapter ch = new ContentHandlerAdapter(ds);
        p.parse(f, ch);
        Attributes fmi = ch.getFileMetaInformation();
        if (fmi == null) {
          fmi = ds.createFileMetaInformation(UID.ExplicitVRLittleEndian);
        }
        boolean b = scb.dicomFile(f, fmi, -1, ds);
        if (printout) {
          System.out.print(b ? '.' : 'I');
        }
      } catch (Exception e) {
        System.out.println();
        System.out.println("Failed to parse file " + f + ": " + e.getMessage());
        e.printStackTrace(System.out);
      }
    } else {
      DicomInputStream in = null;
      try {
        in = new DicomInputStream(f);
        in.setIncludeBulkData(IncludeBulkData.NO);
        Attributes fmi = in.readFileMetaInformation();
        long dsPos = in.getPosition();
        Attributes ds = in.readDataset(-1, Tag.PixelData);
        if (fmi == null
            || !fmi.containsValue(Tag.TransferSyntaxUID)
            || !fmi.containsValue(Tag.MediaStorageSOPClassUID)
            || !fmi.containsValue(Tag.MediaStorageSOPInstanceUID)) {
          fmi = ds.createFileMetaInformation(in.getTransferSyntax());
        }
        boolean b = scb.dicomFile(f, fmi, dsPos, ds);
        if (printout) {
          System.out.print(b ? '.' : 'I');
        }
      } catch (Exception e) {
        System.out.println();
        System.out.println("Failed to scan file " + f + ": " + e.getMessage());
        e.printStackTrace(System.out);
      } finally {
        SafeClose.close(in);
      }
    }
  }
}
