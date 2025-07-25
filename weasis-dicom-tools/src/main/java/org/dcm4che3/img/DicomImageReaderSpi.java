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
 * Service Provider Interface (SPI) for DICOM image readers.
 *
 * <p>This class provides the necessary metadata and factory methods to create {@link
 * DicomImageReader} instances through the Java Image I/O framework. It supports DICOM files and
 * byte streams with various extensions and MIME types.
 *
 * <p>Supported input types:
 *
 * <ul>
 *   <li>{@link DicomFileInputStream} - Direct DICOM file streams
 *   <li>{@link BytesWithImageDescriptor} - Byte arrays with DICOM metadata
 * </ul>
 *
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @author Nicolas Roduit
 * @see DicomImageReader
 * @see ImageReaderSpi
 */
public class DicomImageReaderSpi extends ImageReaderSpi {

  /** Supported DICOM format names for identification */
  private static final String[] DICOM_FORMAT_NAMES = {"dicom", "DICOM"};

  /** Common DICOM file extensions */
  private static final String[] DICOM_EXTENSIONS = {"dcm", "dic", "dicm", "dicom"};

  /** DICOM MIME type according to RFC 3240 */
  private static final String[] DICOM_MIME_TYPES = {"application/dicom"};

  /** Supported input stream types for DICOM reading */
  private static final Class<?>[] DICOM_INPUT_TYPES = {
    DicomFileInputStream.class, BytesWithImageDescriptor.class
  };

  // DICOM tag ranges for format detection
  private static final int DICOM_TAG_MIN = 0x00080000;
  private static final int DICOM_TAG_MAX = 0x00080016;

  /**
   * Constructs a new DICOM Image Reader SPI with all necessary metadata.
   *
   * <p>Initializes the service provider with format names, file extensions, MIME types, and
   * supported input types. This constructor sets up all metadata required by the Java Image I/O
   * framework for proper service discovery and instantiation.
   */
  public DicomImageReaderSpi() {
    super(
        "dcm4che", // vendorName
        Implementation.getVersionName(), // version
        DICOM_FORMAT_NAMES, // names
        DICOM_EXTENSIONS, // suffixes
        DICOM_MIME_TYPES, // MIMETypes
        DicomImageReader.class.getName(), // readerClassName
        DICOM_INPUT_TYPES, // inputTypes
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

  /** Returns a description of this image reader. */
  @Override
  public String getDescription(Locale locale) {
    return "DICOM Image Reader (dcm4che)";
  }

  /**
   * Determines whether the given input source can be decoded by this reader.
   *
   * <p>This method checks for DICOM format by examining:
   *
   * <ol>
   *   <li>DICOM tags in the expected range at the beginning of the stream
   *   <li>The standard DICOM preamble and "DICM" prefix at offset 128
   * </ol>
   *
   * The stream position is restored after the check.
   *
   * @param source the input source to test, must be an {@link ImageInputStream}
   * @return {@code true} if the input appears to be a valid DICOM file
   * @throws IOException if an I/O error occurs while reading the input
   * @throws IllegalArgumentException if source is not an ImageInputStream
   */
  @Override
  public boolean canDecodeInput(Object source) throws IOException {
    if (!(source instanceof ImageInputStream iis)) {
      return false;
    }
    iis.mark();
    try {
      return isDicomByTag(iis) || isDicomByPreamble(iis);
    } finally {
      iis.reset();
    }
  }

  /**
   * Creates a new instance of the DICOM image reader.
   *
   * <p>The extension parameter is currently ignored as no reader-specific extensions are supported.
   *
   * @param extension an optional extension object (currently unused)
   * @return a new {@link DicomImageReader} instance configured with this SPI
   */
  @Override
  public ImageReader createReaderInstance(Object extension) {
    return new DicomImageReader(this);
  }

  /** Checks if input starts with a valid DICOM tag. */
  private boolean isDicomByTag(ImageInputStream iis) throws IOException {
    int tag = readLittleEndianInt(iis);
    return tag >= DICOM_TAG_MIN && tag <= DICOM_TAG_MAX;
  }

  /** Checks if input has valid DICOM preamble and prefix. */
  private boolean isDicomByPreamble(ImageInputStream iis) throws IOException {
    // Since DICOM files have a 128-byte preamble, we skip the first 124 bytes after inial read of 4
    // bytes
    return iis.skipBytes(124) == 124 && hasValidDicomPrefix(iis);
  }

  /** Reads a 32-bit little-endian integer from the stream. */
  private int readLittleEndianInt(ImageInputStream iis) throws IOException {
    return iis.read() | (iis.read() << 8) | (iis.read() << 16) | (iis.read() << 24);
  }

  /** Verifies the presence of "DICM" prefix. */
  private boolean hasValidDicomPrefix(ImageInputStream iis) throws IOException {
    return iis.read() == 'D' && iis.read() == 'I' && iis.read() == 'C' && iis.read() == 'M';
  }
}
