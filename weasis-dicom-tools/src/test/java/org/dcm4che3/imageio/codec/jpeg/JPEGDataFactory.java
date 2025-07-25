/*
 * Copyright (c) 2025 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.dcm4che3.imageio.codec.jpeg;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;

/** Utility class for creating JPEG test data */
public final class JPEGDataFactory {

  private JPEGDataFactory() {
    // Utility class
  }

  // Helper methods for creating test data
  static SeekableByteChannel createByteChannel(byte[] data) {
    return new ByteArraySeekableByteChannel(data);
  }

  static byte[] createMinimalJPEGData(
      int sofMarker, int precision, int height, int width, int components) {
    ByteArrayOutputStream baos = new ByteArrayOutputStream();

    // SOI
    baos.write(0xFF);
    baos.write(JPEG.SOI);

    // SOF
    baos.write(0xFF);
    baos.write(sofMarker);
    int sofLength = 8 + (3 * components);
    baos.write(sofLength >> 8);
    baos.write(sofLength & 0xFF);
    baos.write(precision);
    baos.write(height >> 8);
    baos.write(height & 0xFF);
    baos.write(width >> 8);
    baos.write(width & 0xFF);
    baos.write(components);

    // Component data
    for (int i = 0; i < components; i++) {
      baos.write(i + 1); // Component ID
      baos.write(0x11); // Sampling factors
      baos.write(0); // Quantization table
    }

    // SOS
    baos.write(0xFF);
    baos.write(JPEG.SOS);
    int sosLength = 6 + (2 * components);
    baos.write(sosLength >> 8);
    baos.write(sosLength & 0xFF);
    baos.write(components);

    // Component data for SOS
    for (int i = 0; i < components; i++) {
      baos.write(i + 1); // Component ID
      baos.write(0); // Huffman tables
    }

    baos.write(0); // Start of spectral
    baos.write(63); // End of spectral
    baos.write(0); // Successive approximation

    return baos.toByteArray();
  }

  static byte[] createJPEGDataWithSOF(
      int sofMarker, int precision, int height, int width, int components) {
    return createMinimalJPEGData(sofMarker, precision, height, width, components);
  }

  static byte[] createJPEGDataWithRGBComponents(
      int sofMarker, int precision, int height, int width) {
    ByteArrayOutputStream baos = new ByteArrayOutputStream();

    // SOI
    baos.write(0xFF);
    baos.write(JPEG.SOI);

    // SOF
    baos.write(0xFF);
    baos.write(sofMarker);
    baos.write(0x00);
    baos.write(0x11); // Length = 17
    baos.write(precision);
    baos.write(height >> 8);
    baos.write(height & 0xFF);
    baos.write(width >> 8);
    baos.write(width & 0xFF);
    baos.write(0x03); // 3 components

    // RGB component IDs
    baos.write(0x52); // 'R'
    baos.write(0x11);
    baos.write(0x00);
    baos.write(0x47); // 'G'
    baos.write(0x11);
    baos.write(0x01);
    baos.write(0x42); // 'B'
    baos.write(0x11);
    baos.write(0x02);

    // SOS
    baos.write(0xFF);
    baos.write(JPEG.SOS);
    baos.write(0x00);
    baos.write(0x0C); // Length = 12
    baos.write(0x03); // 3 components
    baos.write(0x01);
    baos.write(0x00);
    baos.write(0x02);
    baos.write(0x11);
    baos.write(0x03);
    baos.write(0x11);
    baos.write(0x00); // Start
    baos.write(0x3F); // End
    baos.write(0x00); // Successive approximation

    return baos.toByteArray();
  }

  static byte[] createLosslessJPEGData(
      int sofMarker, int precision, int height, int width, int components, int ss) {
    ByteArrayOutputStream baos = new ByteArrayOutputStream();

    // SOI
    baos.write(0xFF);
    baos.write(JPEG.SOI);

    // SOF
    baos.write(0xFF);
    baos.write(sofMarker);
    int sofLength = 8 + (3 * components);
    baos.write(sofLength >> 8);
    baos.write(sofLength & 0xFF);
    baos.write(precision);
    baos.write(height >> 8);
    baos.write(height & 0xFF);
    baos.write(width >> 8);
    baos.write(width & 0xFF);
    baos.write(components);

    // Component data
    for (int i = 0; i < components; i++) {
      baos.write(i + 1);
      baos.write(0x11);
      baos.write(0);
    }

    // SOS - Fixed structure
    baos.write(0xFF);
    baos.write(JPEG.SOS);
    int sosLength = 6 + (2 * components);
    baos.write(sosLength >> 8);
    baos.write(sosLength & 0xFF);
    baos.write(components);
    baos.write(0x00); // Start of spectral selection (Ss)
    baos.write(63); // End of spectral selection (Se) - 0 for lossless
    baos.write(ss); // Successive approximation (Ah << 4 | Al)
    for (int i = 0; i < components; i++) {
      baos.write(i + 1); // Component ID
      baos.write(0); // Huffman table
    }

    return baos.toByteArray();
  }

  static byte[] createJPEGWithAPP0Marker() throws IOException {
    ByteArrayOutputStream baos = new ByteArrayOutputStream();

    // SOI
    baos.write(0xFF);
    baos.write(JPEG.SOI);

    // APP0 (JFIF)
    baos.write(0xFF);
    baos.write(JPEG.APP0);
    baos.write(0x00);
    baos.write(0x10); // Length = 16
    baos.write("JFIF\0".getBytes()); // JFIF identifier
    baos.write(0x01);
    baos.write(0x01); // Version 1.1
    baos.write(0x00); // Units
    baos.write(0x00);
    baos.write(0x48); // X density
    baos.write(0x00);
    baos.write(0x48); // Y density
    baos.write(0x00); // Thumbnail width
    baos.write(0x00); // Thumbnail height

    // SOF0
    baos.write(0xFF);
    baos.write(JPEG.SOF0);
    baos.write(0x00);
    baos.write(0x11); // Length = 17
    baos.write(0x08); // Precision
    baos.write(0x00);
    baos.write(0x64); // Height = 100
    baos.write(0x00);
    baos.write(0x64); // Width = 100
    baos.write(0x03); // 3 components

    // Component data
    baos.write(0x01);
    baos.write(0x22);
    baos.write(0x00);
    baos.write(0x02);
    baos.write(0x11);
    baos.write(0x01);
    baos.write(0x03);
    baos.write(0x11);
    baos.write(0x01);

    // SOS
    baos.write(0xFF);
    baos.write(JPEG.SOS);
    baos.write(0x00);
    baos.write(0x0C); // Length = 12
    baos.write(0x03); // 3 components
    baos.write(0x01);
    baos.write(0x00);
    baos.write(0x02);
    baos.write(0x11);
    baos.write(0x03);
    baos.write(0x11);
    baos.write(0x00); // Start
    baos.write(0x3F); // End
    baos.write(0x00); // Successive approximation

    return baos.toByteArray();
  }

  static byte[] createJPEGWithAdobeAPP14() throws IOException {
    ByteArrayOutputStream baos = new ByteArrayOutputStream();

    // SOI
    baos.write(0xFF);
    baos.write(JPEG.SOI);

    // APP14 (Adobe)
    baos.write(0xFF);
    baos.write(JPEG.APP14);
    baos.write(0x00);
    baos.write(0x0E); // Length = 14
    baos.write("Adobe".getBytes()); // Adobe identifier
    baos.write(0x00);
    baos.write(0x64); // Version
    baos.write(0x00);
    baos.write(0x00); // Flags0
    baos.write(0x00);
    baos.write(0x00); // Flags1
    baos.write(0x00); // Color transform = RGB

    // SOF0
    baos.write(0xFF);
    baos.write(JPEG.SOF0);
    baos.write(0x00);
    baos.write(0x11); // Length = 17
    baos.write(0x08); // Precision
    baos.write(0x00);
    baos.write(0x64); // Height = 100
    baos.write(0x00);
    baos.write(0x64); // Width = 100
    baos.write(0x03); // 3 components

    // Component data
    baos.write(0x01);
    baos.write(0x11);
    baos.write(0x00);
    baos.write(0x02);
    baos.write(0x11);
    baos.write(0x01);
    baos.write(0x03);
    baos.write(0x11);
    baos.write(0x01);

    // SOS
    baos.write(0xFF);
    baos.write(JPEG.SOS);
    baos.write(0x00);
    baos.write(0x0C); // Length = 12
    baos.write(0x03); // 3 components
    baos.write(0x01);
    baos.write(0x00);
    baos.write(0x02);
    baos.write(0x11);
    baos.write(0x03);
    baos.write(0x11);
    baos.write(0x00); // Start
    baos.write(0x3F); // End
    baos.write(0x00); // Successive approximation

    return baos.toByteArray();
  }

  static byte[] createJPEGWithUnsupportedSOF() {
    ByteArrayOutputStream baos = new ByteArrayOutputStream();

    // SOI
    baos.write(0xFF);
    baos.write(JPEG.SOI);

    // Unsupported SOF marker (SOF5 - Differential Sequential DCT)
    baos.write(0xFF);
    baos.write(JPEG.SOF5);
    baos.write(0x00);
    baos.write(0x0B); // Length = 11
    baos.write(0x08); // Precision
    baos.write(0x00);
    baos.write(0x64); // Height = 100
    baos.write(0x00);
    baos.write(0x64); // Width = 100
    baos.write(0x01); // 1 component
    baos.write(0x01);
    baos.write(0x11);
    baos.write(0x00);

    return baos.toByteArray();
  }

  static byte[] createLosslessJPEGWithInsufficientSOS() {
    ByteArrayOutputStream baos = new ByteArrayOutputStream();

    // SOI
    baos.write(0xFF);
    baos.write(JPEG.SOI);

    // SOF3 (Lossless)
    baos.write(0xFF);
    baos.write(JPEG.SOF3);
    baos.write(0x00);
    baos.write(0x0B); // Length = 11
    baos.write(0x08); // Precision
    baos.write(0x00);
    baos.write(0x64); // Height = 100
    baos.write(0x00);
    baos.write(0x64); // Width = 100
    baos.write(0x01); // 1 component
    baos.write(0x01);
    baos.write(0x11);
    baos.write(0x00);

    // SOS with insufficient data
    baos.write(0xFF);
    baos.write(JPEG.SOS);
    baos.write(0x00);
    baos.write(0x04); // Length = 4 (too small)
    baos.write(0x00);
    baos.write(0x00);
    return baos.toByteArray();
  }

  static byte[] createJPEGWithInvalidByteSequence() {
    ByteArrayOutputStream baos = new ByteArrayOutputStream();

    // SOI
    baos.write(0xFF);
    baos.write(JPEG.SOI);

    // Invalid byte sequence (not 0xFF)
    baos.write(0x00); // Should be 0xFF
    baos.write(JPEG.SOF0);

    return baos.toByteArray();
  }

  static byte[] createMinimalJPEG2000Data() {
    ByteArrayOutputStream baos = new ByteArrayOutputStream();

    // SOC
    baos.write(0xFF);
    baos.write(JPEG.SOC);

    // SIZ marker
    baos.write(0xFF);
    baos.write(JPEG.SIZ);
    baos.write(0x00);
    baos.write(0x29); // Length = 41

    // SIZ parameters (simplified)
    baos.write(0x00);
    baos.write(0x00); // Rsiz
    baos.write(0x00);
    baos.write(0x00);
    baos.write(0x00);
    baos.write(0x64); // Xsiz = 100
    baos.write(0x00);
    baos.write(0x00);
    baos.write(0x00);
    baos.write(0x64); // Ysiz = 100
    baos.write(0x00);
    baos.write(0x00);
    baos.write(0x00);
    baos.write(0x00); // XOsiz = 0
    baos.write(0x00);
    baos.write(0x00);
    baos.write(0x00);
    baos.write(0x00); // YOsiz = 0
    baos.write(0x00);
    baos.write(0x00);
    baos.write(0x00);
    baos.write(0x64); // XTsiz = 100
    baos.write(0x00);
    baos.write(0x00);
    baos.write(0x00);
    baos.write(0x64); // YTsiz = 100
    baos.write(0x00);
    baos.write(0x00);
    baos.write(0x00);
    baos.write(0x00); // XTOsiz = 0
    baos.write(0x00);
    baos.write(0x00);
    baos.write(0x00);
    baos.write(0x00); // YTOsiz = 0
    baos.write(0x00);
    baos.write(0x01); // Csiz = 1 component
    baos.write(0x07); // Ssiz = 8 bits
    baos.write(0x01); // XRsiz = 1
    baos.write(0x01); // YRsiz = 1

    // COD marker
    baos.write(0xFF);
    baos.write(JPEG.COD);
    baos.write(0x00);
    baos.write(0x0C); // Length = 12
    baos.write(0x00); // Scod
    baos.write(0x00); // Progression order
    baos.write(0x00);
    baos.write(0x01); // Number of layers
    baos.write(0x00); // Multiple component transform
    baos.write(0x05); // Number of decomposition levels
    baos.write(0x04); // Code-block width
    baos.write(0x04); // Code-block height
    baos.write(0x00); // Code-block style
    baos.write(0x01); // Wavelet transformation

    // SOT marker
    baos.write(0xFF);
    baos.write(JPEG.SOT);
    baos.write(0x00);
    baos.write(0x0A); // Length = 10
    baos.write(0x00);
    baos.write(0x00); // Tile index
    baos.write(0x00);
    baos.write(0x00);
    baos.write(0x00);
    baos.write(0x00); // Tile-part length
    baos.write(0x00); // Tile-part index
    baos.write(0x01); // Number of tile-parts

    return baos.toByteArray();
  }

  static byte[] createAdvancedJPEG2000Data() {
    ByteArrayOutputStream baos = new ByteArrayOutputStream();

    // SOC
    baos.write(0xFF);
    baos.write(JPEG.SOC);

    // SIZ marker with 3 components
    baos.write(0xFF);
    baos.write(JPEG.SIZ);
    baos.write(0x00);
    baos.write(0x2F); // Length = 47

    // SIZ parameters
    baos.write(0x00);
    baos.write(0x00); // Rsiz
    baos.write(0x00);
    baos.write(0x00);
    baos.write(0x02);
    baos.write(0x00); // Xsiz = 512
    baos.write(0x00);
    baos.write(0x00);
    baos.write(0x02);
    baos.write(0x00); // Ysiz = 512
    baos.write(0x00);
    baos.write(0x00);
    baos.write(0x00);
    baos.write(0x00); // XOsiz = 0
    baos.write(0x00);
    baos.write(0x00);
    baos.write(0x00);
    baos.write(0x00); // YOsiz = 0
    baos.write(0x00);
    baos.write(0x00);
    baos.write(0x02);
    baos.write(0x00); // XTsiz = 512
    baos.write(0x00);
    baos.write(0x00);
    baos.write(0x02);
    baos.write(0x00); // YTsiz = 512
    baos.write(0x00);
    baos.write(0x00);
    baos.write(0x00);
    baos.write(0x00); // XTOsiz = 0
    baos.write(0x00);
    baos.write(0x00);
    baos.write(0x00);
    baos.write(0x00); // YTOsiz = 0
    baos.write(0x00);
    baos.write(0x03); // Csiz = 3 components
    // Component 1
    baos.write(0x07);
    baos.write(0x01);
    baos.write(0x01);
    // Component 2
    baos.write(0x07);
    baos.write(0x01);
    baos.write(0x01);
    // Component 3
    baos.write(0x07);
    baos.write(0x01);
    baos.write(0x01);

    // COD marker
    baos.write(0xFF);
    baos.write(JPEG.COD);
    baos.write(0x00);
    baos.write(0x0C); // Length = 12
    baos.write(0x00); // Scod
    baos.write(0x00); // Progression order
    baos.write(0x00);
    baos.write(0x01); // Number of layers
    baos.write(0x01); // Multiple component transform (RCT)
    baos.write(0x05); // Number of decomposition levels
    baos.write(0x04); // Code-block width
    baos.write(0x04); // Code-block height
    baos.write(0x00); // Code-block style
    baos.write(0x01); // Wavelet transformation

    // **FIX: Add SOT marker to terminate the parsing loop**
    baos.write(0xFF);
    baos.write(JPEG.SOT);
    baos.write(0x00);
    baos.write(0x0A); // Length = 10
    baos.write(0x00);
    baos.write(0x00); // Tile index = 0
    baos.write(0x00);
    baos.write(0x00);
    baos.write(0x00);
    baos.write(0x20); // Tile-part length = 32
    baos.write(0x00); // Tile-part index = 0
    baos.write(0x01); // Number of tile-parts = 1
    return baos.toByteArray();
  }

  static byte[] createJPEG2000WithBoxStructure() {
    ByteArrayOutputStream baos = new ByteArrayOutputStream();

    // JPEG2000 Signature Box
    baos.write(0x00);
    baos.write(0x00);
    baos.write(0x00);
    baos.write(0x0C); // Box length = 12
    baos.write(0x6A);
    baos.write(0x50);
    baos.write(0x20);
    baos.write(0x20); // 'jP  '
    baos.write(0x0D);
    baos.write(0x0A);
    baos.write(0x87);
    baos.write(0x0A); // Signature

    // File Type Box
    baos.write(0x00);
    baos.write(0x00);
    baos.write(0x00);
    baos.write(0x14); // Box length = 20
    baos.write(0x66);
    baos.write(0x74);
    baos.write(0x79);
    baos.write(0x70); // 'ftyp'
    baos.write(0x6A);
    baos.write(0x70);
    baos.write(0x32);
    baos.write(0x20); // 'jp2 ' brand
    baos.write(0x00);
    baos.write(0x00);
    baos.write(0x00);
    baos.write(0x00); // Minor version
    baos.write(0x6A);
    baos.write(0x70);
    baos.write(0x32);
    baos.write(0x20); // Compatible brand

    // Contiguous Codestream Box
    baos.write(0x00);
    baos.write(0x00);
    baos.write(0x00);
    baos.write(0x30); // Box length = 48
    baos.write(0x6A);
    baos.write(0x70);
    baos.write(0x32);
    baos.write(0x63); // 'jp2c'

    // Add minimal JPEG2000 codestream
    byte[] codestream = createMinimalJPEG2000Data();
    baos.write(codestream, 0, codestream.length);
    return baos.toByteArray();
  }

  static byte[] createHTJ2KLosslessRPCLData() {
    ByteArrayOutputStream baos = new ByteArrayOutputStream();

    // JPEG 2000 codestream signature
    baos.write(0xFF);
    baos.write(JPEG.SOC); // Start of Codestream (0x4F)

    // SIZ (Image and tile size) marker - Required main header marker
    baos.write(0xFF);
    baos.write(JPEG.SIZ); // 0x51
    baos.write(0x00);
    baos.write(0x2F); // Length = 47 bytes

    // SIZ marker segment parameters with HTJ2K capability flag
    baos.write(0x40);
    baos.write(0x00); // Rsiz = 0x4000 (HTJ2K capability flag)
    baos.write(0x00);
    baos.write(0x00);
    baos.write(0x02);
    baos.write(0x00); // Xsiz = 512
    baos.write(0x00);
    baos.write(0x00);
    baos.write(0x02);
    baos.write(0x00); // Ysiz = 512
    baos.write(0x00);
    baos.write(0x00);
    baos.write(0x00);
    baos.write(0x00); // XOsiz = 0
    baos.write(0x00);
    baos.write(0x00);
    baos.write(0x00);
    baos.write(0x00); // YOsiz = 0
    baos.write(0x00);
    baos.write(0x00);
    baos.write(0x02);
    baos.write(0x00); // XTsiz = 512
    baos.write(0x00);
    baos.write(0x00);
    baos.write(0x02);
    baos.write(0x00); // YTsiz = 512
    baos.write(0x00);
    baos.write(0x00);
    baos.write(0x00);
    baos.write(0x00); // XTOsiz = 0
    baos.write(0x00);
    baos.write(0x00);
    baos.write(0x00);
    baos.write(0x00); // YTOsiz = 0
    baos.write(0x00);
    baos.write(0x03); // Csiz = 3 components (RGB)

    // Component 0 (Red) - 8-bit unsigned
    baos.write(0x07);
    baos.write(0x01);
    baos.write(0x01);
    // Component 1 (Green) - 8-bit unsigned
    baos.write(0x07);
    baos.write(0x01);
    baos.write(0x01);
    // Component 2 (Blue) - 8-bit unsigned
    baos.write(0x07);
    baos.write(0x01);
    baos.write(0x01);

    // COD (Coding style default) marker - Required main header marker
    baos.write(0xFF);
    baos.write(JPEG.COD); // 0x52
    baos.write(0x00);
    baos.write(0x0C); // Length = 12 bytes

    // COD marker segment parameters for HTJ2K lossless RPCL
    baos.write(0x00); // Scod = 0 (default coding style)
    baos.write(0x03); // SGcod[0] = RPCL progression order (0x03)
    baos.write(0x00);
    baos.write(0x01); // SGcod[1-2] = Number of layers = 1
    baos.write(0x00); // SGcod[3] = Multiple component transform = 0 (no MCT, lossless)
    baos.write(0x05); // SPcod[0] = Number of decomposition levels = 5
    baos.write(0x04); // SPcod[1] = Code-block width = 64 (2^(4+2))
    baos.write(0x04); // SPcod[2] = Code-block height = 64 (2^(4+2))
    baos.write(0x00); // SPcod[3] = Code-block style = 0
    baos.write(0x01); // SPcod[4] = Wavelet transformation = 1 (5/3 reversible, lossless)

    // SOT (Start of tile-part) marker - Required to start tile-part data
    baos.write(0xFF);
    baos.write(JPEG.SOT); // 0x90
    baos.write(0x00);
    baos.write(0x0A); // Length = 10 bytes
    baos.write(0x00);
    baos.write(0x00); // Isot = 0 (tile index)
    baos.write(0x00);
    baos.write(0x00);
    baos.write(0x00);
    baos.write(0x14); // Psot = 20 (tile-part length)
    baos.write(0x00); // TPsot = 0 (tile-part index)
    baos.write(0x01); // TNsot = 1 (number of tile-parts)

    // SOD (Start of data) marker - Indicates start of bit stream
    baos.write(0xFF);
    baos.write(JPEG.SOD); // 0x93

    // Minimal packet data - just packet header and termination
    baos.write(0xFF);
    baos.write(0x4F); // Packet header indicator
    baos.write(0x00);
    baos.write(0x00); // Empty data
    baos.write(0xFF);
    baos.write(0xD9); // EOI (End of Image) - terminate codestream

    return baos.toByteArray();
  }

  static byte[] createHTJ2KLosslessRPCLMonochromeData() {
    ByteArrayOutputStream baos = new ByteArrayOutputStream();

    // JPEG 2000 codestream signature
    baos.write(0xFF);
    baos.write(JPEG.SOC); // Start of Codestream (0x4F)

    // SIZ (Image and tile size) marker - Required main header marker
    baos.write(0xFF);
    baos.write(JPEG.SIZ); // 0x51
    baos.write(0x00);
    baos.write(0x29); // Length = 41 bytes (smaller for single component)

    // SIZ marker segment parameters with HTJ2K capability flag
    baos.write(0x40);
    baos.write(0x00); // Rsiz = 0x4000 (HTJ2K capability flag)
    baos.write(0x00);
    baos.write(0x00);
    baos.write(0x02);
    baos.write(0x00); // Xsiz = 512
    baos.write(0x00);
    baos.write(0x00);
    baos.write(0x02);
    baos.write(0x00); // Ysiz = 512
    baos.write(0x00);
    baos.write(0x00);
    baos.write(0x00);
    baos.write(0x00); // XOsiz = 0
    baos.write(0x00);
    baos.write(0x00);
    baos.write(0x00);
    baos.write(0x00); // YOsiz = 0
    baos.write(0x00);
    baos.write(0x00);
    baos.write(0x02);
    baos.write(0x00); // XTsiz = 512
    baos.write(0x00);
    baos.write(0x00);
    baos.write(0x02);
    baos.write(0x00); // YTsiz = 512
    baos.write(0x00);
    baos.write(0x00);
    baos.write(0x00);
    baos.write(0x00); // XTOsiz = 0
    baos.write(0x00);
    baos.write(0x00);
    baos.write(0x00);
    baos.write(0x00); // YTOsiz = 0
    baos.write(0x00);
    baos.write(0x01); // Csiz = 1 component (Monochrome)

    // Component 0 (Gray) - 8-bit unsigned
    baos.write(0x07);
    baos.write(0x01);
    baos.write(0x01);

    // COD (Coding style default) marker - Required main header marker
    baos.write(0xFF);
    baos.write(JPEG.COD); // 0x52
    baos.write(0x00);
    baos.write(0x0C); // Length = 12 bytes

    // COD marker segment parameters for HTJ2K lossless RPCL
    baos.write(0x00); // Scod = 0 (default coding style)
    baos.write(0x02); // SGcod[0] = RPCL progression order (0x02 for RPCL)
    baos.write(0x00);
    baos.write(0x01); // SGcod[1-2] = Number of layers = 1
    baos.write(0x00); // SGcod[3] = Multiple component transform = 0 (no MCT for monochrome)
    baos.write(0x05); // SPcod[0] = Number of decomposition levels = 5
    baos.write(0x04); // SPcod[1] = Code-block width = 64 (2^(4+2))
    baos.write(0x04); // SPcod[2] = Code-block height = 64 (2^(4+2))
    baos.write(0x00); // SPcod[3] = Code-block style = 0
    baos.write(0x01); // SPcod[4] = Wavelet transformation = 1 (5/3 reversible, lossless)

    // TLM (Tile-part lengths) marker - Required for HTJ2K RPCL
    baos.write(0xFF);
    baos.write(JPEG.TLM); // 0x55
    baos.write(0x00);
    baos.write(0x04); // Length = 4 bytes (minimal TLM)
    baos.write(0x00); // Ztlm = 0 (TLM index)
    baos.write(0x40); // Stlm = 0x40 (ST=1, SP=0 - 16-bit tile index, 16-bit length)

    // SOT (Start of tile-part) marker - Required to start tile-part data
    baos.write(0xFF);
    baos.write(JPEG.SOT); // 0x90
    baos.write(0x00);
    baos.write(0x0A); // Length = 10 bytes
    baos.write(0x00);
    baos.write(0x00); // Isot = 0 (tile index)
    baos.write(0x00);
    baos.write(0x00);
    baos.write(0x00);
    baos.write(0x0C); // Psot = 12 (tile-part length)
    baos.write(0x00); // TPsot = 0 (tile-part index)
    baos.write(0x01); // TNsot = 1 (number of tile-parts)

    // SOD (Start of data) marker - Indicates start of bit stream
    baos.write(0xFF);
    baos.write(JPEG.SOD); // 0x93

    return baos.toByteArray();
  }

  /** Helper class for in-memory byte channel implementation */
  // Helper class for in-memory byte channel
  static class ByteArraySeekableByteChannel implements SeekableByteChannel {
    private final byte[] data;
    private int position = 0;
    private boolean open = true;

    public ByteArraySeekableByteChannel(byte[] data) {
      this.data = data;
    }

    @Override
    public int read(ByteBuffer dst) throws IOException {
      if (!open) throw new IOException("Channel is closed");
      int remaining = Math.min(dst.remaining(), data.length - position);
      if (remaining <= 0) return -1;

      dst.put(data, position, remaining);
      position += remaining;
      return remaining;
    }

    @Override
    public int write(ByteBuffer src) throws IOException {
      throw new UnsupportedOperationException("Read-only channel");
    }

    @Override
    public long position() throws IOException {
      if (!open) throw new IOException("Channel is closed");
      return position;
    }

    @Override
    public SeekableByteChannel position(long newPosition) throws IOException {
      if (!open) throw new IOException("Channel is closed");
      this.position = (int) Math.max(0, Math.min(newPosition, data.length));
      return this;
    }

    @Override
    public long size() throws IOException {
      if (!open) throw new IOException("Channel is closed");
      return data.length;
    }

    @Override
    public SeekableByteChannel truncate(long size) throws IOException {
      throw new UnsupportedOperationException("Read-only channel");
    }

    @Override
    public boolean isOpen() {
      return open;
    }

    @Override
    public void close() throws IOException {
      open = false;
    }
  }
}
