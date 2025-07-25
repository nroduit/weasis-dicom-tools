/*
 * Copyright (c) 2009-2021 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.dcm4che3.imageio.codec.jpeg;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 */
public class JPEG {

  /** For temporary use in arithmetic coding */
  public static final int TEM = 0x01;

  // Codes 0x02 - 0xBF are reserved

  // JPEG 2000 markers
  private static final int JPEG2000_STANDALONE = 0x30;

  /** Start of codestream */
  public static final int FF_SOC = 0xFF4F;

  public static final int SOC = 0x4F;

  /** Image and tile size */
  public static final int SIZ = 0x51;

  /** Coding style default */
  public static final int COD = 0x52;

  /** Tile-part lengths */
  public static final int TLM = 0x55;

  /** Start of tile-part */
  public static final int SOT = 0x90;

  /** Start of data */
  public static final int SOD = 0x93;

  // SOF markers for Nondifferential Huffman coding
  /** Baseline DCT */
  public static final int SOF0 = 0xC0;

  /** Extended Sequential DCT */
  public static final int SOF1 = 0xC1;

  /** Progressive DCT */
  public static final int SOF2 = 0xC2;

  /** Lossless Sequential */
  public static final int SOF3 = 0xC3;

  /** Define Huffman Tables */
  public static final int DHT = 0xC4;

  // SOF markers for Differential Huffman coding
  /** Differential Sequential DCT */
  public static final int SOF5 = 0xC5;

  /** Differential Progressive DCT */
  public static final int SOF6 = 0xC6;

  /** Differential Lossless */
  public static final int SOF7 = 0xC7;

  /** Reserved for JPEG extensions */
  public static final int JPG = 0xC8;

  // SOF markers for Nondifferential arithmetic coding
  /** Extended Sequential DCT, Arithmetic coding */
  public static final int SOF9 = 0xC9;

  /** Progressive DCT, Arithmetic coding */
  public static final int SOF10 = 0xCA;

  /** Lossless Sequential, Arithmetic coding */
  public static final int SOF11 = 0xCB;

  /** Define Arithmetic conditioning tables */
  public static final int DAC = 0xCC;

  // SOF markers for Differential arithmetic coding
  /** Differential Sequential DCT, Arithmetic coding */
  public static final int SOF13 = 0xCD;

  /** Differential Progressive DCT, Arithmetic coding */
  public static final int SOF14 = 0xCE;

  /** Differential Lossless, Arithmetic coding */
  public static final int SOF15 = 0xCF;

  // Restart Markers
  public static final int RST0 = 0xD0;
  public static final int RST1 = 0xD1;
  public static final int RST2 = 0xD2;
  public static final int RST3 = 0xD3;
  public static final int RST4 = 0xD4;
  public static final int RST5 = 0xD5;
  public static final int RST6 = 0xD6;
  public static final int RST7 = 0xD7;

  /** Number of restart markers */
  public static final int RESTART_RANGE = 8;

  /** Start of Image */
  public static final int FF_SOI = 0xFFD8;

  public static final int SOI = 0xD8;

  /** End of Image */
  public static final int EOI = 0xD9;

  /** Start of Scan */
  public static final int SOS = 0xDA;

  /** Define Quantization Tables */
  public static final int DQT = 0xDB;

  /** Define Number of lines */
  public static final int DNL = 0xDC;

  /** Define Restart Interval */
  public static final int DRI = 0xDD;

  /** Define Hierarchical progression */
  public static final int DHP = 0xDE;

  /** Expand reference image(s) */
  public static final int EXP = 0xDF;

  // Application markers
  /** APP0 used by JFIF */
  public static final int APP0 = 0xE0;

  public static final int APP1 = 0xE1;
  public static final int APP2 = 0xE2;
  public static final int APP3 = 0xE3;
  public static final int APP4 = 0xE4;
  public static final int APP5 = 0xE5;
  public static final int APP6 = 0xE6;
  public static final int APP7 = 0xE7;
  public static final int APP8 = 0xE8;
  public static final int APP9 = 0xE9;
  public static final int APP10 = 0xEA;
  public static final int APP11 = 0xEB;
  public static final int APP12 = 0xEC;
  public static final int APP13 = 0xED;

  /** APP14 used by Adobe */
  public static final int APP14 = 0xEE;

  public static final int APP15 = 0xEF;

  // codes 0xF0 to 0xFD are reserved
  /** JPEG-LS coding */
  public static final int SOF55 = 0xF7;

  /** JPEG-LS parameters */
  public static final int LSE = 0xF8;

  /** Comment marker */
  public static final int COM = 0xFE;

  private JPEG() {}

  public static boolean isStandalone(int marker) {
    return switch (marker) {
      case TEM, RST0, RST1, RST2, RST3, RST4, RST5, RST6, RST7, SOI, EOI -> true;
      default -> (marker & 0xF0) == JPEG2000_STANDALONE;
    };
  }

  public static boolean isSOF(int marker) {
    return switch (marker) {
      case SOF0,
              SOF1,
              SOF2,
              SOF3,
              SOF5,
              SOF6,
              SOF7,
              SOF9,
              SOF10,
              SOF11,
              SOF13,
              SOF14,
              SOF15,
              SOF55 ->
          true;
      default -> false;
    };
  }

  public static boolean isAPP(int marker) {
    return (marker & 0xF0) == APP0;
  }
}
