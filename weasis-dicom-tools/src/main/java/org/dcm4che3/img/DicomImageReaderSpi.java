/*
 * Copyright (c) 2021 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.dcm4che3.img;

import java.io.IOException;
import java.util.Locale;
import javax.imageio.ImageReader;
import javax.imageio.spi.ImageReaderSpi;
import javax.imageio.stream.ImageInputStream;
import org.dcm4che3.data.Implementation;
import org.dcm4che3.img.stream.BytesWithImageDescriptor;
import org.dcm4che3.img.stream.DicomFileInputStream;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 */
public class DicomImageReaderSpi extends ImageReaderSpi {

  private static final String[] dicomFormatNames = {"dicom", "DICOM"};
  private static final String[] dicomExt = {"dcm", "dic", "dicm", "dicom"};
  private static final String[] dicomMimeType = {"application/dicom"};
  private static final Class<?>[] dicomInputTypes = {
    DicomFileInputStream.class, BytesWithImageDescriptor.class
  };

  public DicomImageReaderSpi() {
    super(
        "dcm4che",
        Implementation.getVersionName(),
        dicomFormatNames,
        dicomExt,
        dicomMimeType,
        DicomImageReader.class.getName(),
        dicomInputTypes,
        null, // writerSpiNames
        false, // supportsStandardStreamMetadataFormat
        null, // nativeStreamMetadataFormatName
        null, // nativeStreamMetadataFormatClassName
        null, // extraStreamMetadataFormatNames
        null, // extraStreamMetadataFormatClassNames
        false, // supportsStandardImageMetadataFormat
        null, // nativeImageMetadataFormatName
        null, // nativeImageMetadataFormatClassName
        null, // extraImageMetadataFormatNames
        null); // extraImageMetadataFormatClassNames
  }

  @Override
  public String getDescription(Locale locale) {
    return "DICOM Image Reader (dcm4che)";
  }

  @Override
  public boolean canDecodeInput(Object source) throws IOException {
    ImageInputStream iis = (ImageInputStream) source;
    iis.mark();
    try {
      int tag = iis.read() | (iis.read() << 8) | (iis.read() << 16) | (iis.read() << 24);
      return ((tag >= 0x00080000 && tag <= 0x00080016)
          || (iis.skipBytes(124) == 124
              && iis.read() == 'D'
              && iis.read() == 'I'
              && iis.read() == 'C'
              && iis.read() == 'M'));
    } finally {
      iis.reset();
    }
  }

  @Override
  public ImageReader createReaderInstance(Object extension) {
    return new DicomImageReader(this);
  }
}
