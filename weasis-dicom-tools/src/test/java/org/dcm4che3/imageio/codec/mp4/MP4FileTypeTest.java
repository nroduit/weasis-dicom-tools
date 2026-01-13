/*
 * Copyright (c) 2024 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.dcm4che3.imageio.codec.mp4;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.ByteBuffer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class MP4FileTypeTest {

  @Nested
  @DisplayName("Constructor Tests")
  class ConstructorTests {

    @Test
    @DisplayName("Should create MP4FileType with major brand and minor version only")
    void testConstructor_MajorBrandAndMinorVersionOnly() {
      MP4FileType fileType = new MP4FileType(MP4FileType.ISOM, 512);

      assertEquals(MP4FileType.ISOM, fileType.majorBrand());
      assertEquals(512, fileType.minorVersion());
      assertEquals(0, fileType.compatibleBrands().length);
    }

    @Test
    @DisplayName("Should create MP4FileType with compatible brands")
    void testConstructor_WithCompatibleBrands() {
      MP4FileType fileType = new MP4FileType(MP4FileType.ISOM, 0, MP4FileType.ISOM, MP4FileType.QT);

      assertEquals(MP4FileType.ISOM, fileType.majorBrand());
      assertEquals(0, fileType.minorVersion());
      assertArrayEquals(new int[] {MP4FileType.ISOM, MP4FileType.QT}, fileType.compatibleBrands());
    }

    @Test
    @DisplayName("Should create MP4FileType with single compatible brand")
    void testConstructor_SingleCompatibleBrand() {
      MP4FileType fileType = new MP4FileType(MP4FileType.QT, 1, MP4FileType.ISOM);

      assertEquals(MP4FileType.QT, fileType.majorBrand());
      assertEquals(1, fileType.minorVersion());
      assertArrayEquals(new int[] {MP4FileType.ISOM}, fileType.compatibleBrands());
    }

    @Test
    @DisplayName("Should create MP4FileType with multiple compatible brands")
    void testConstructor_MultipleCompatibleBrands() {
      int[] brands = {0x6D703431, 0x6D703432, 0x69736F6D}; // mp41, mp42, isom
      MP4FileType fileType = new MP4FileType(MP4FileType.ISOM, 1, brands);

      assertEquals(MP4FileType.ISOM, fileType.majorBrand());
      assertEquals(1, fileType.minorVersion());
      assertArrayEquals(brands, fileType.compatibleBrands());
    }
  }

  @Nested
  @DisplayName("Constants Tests")
  class ConstantsTests {

    @Test
    @DisplayName("Should verify QT constant value")
    void testQTConstant() {
      assertEquals(0x71742020, MP4FileType.QT);
    }

    @Test
    @DisplayName("Should verify ISOM constant value")
    void testISOMConstant() {
      assertEquals(0x69736f6d, MP4FileType.ISOM);
    }

    @Test
    @DisplayName("Should verify ISOM_QT predefined instance")
    void testISOM_QTConstant() {
      assertEquals(MP4FileType.ISOM, MP4FileType.ISOM_QT.majorBrand());
      assertEquals(0, MP4FileType.ISOM_QT.minorVersion());
      assertArrayEquals(
          new int[] {MP4FileType.ISOM, MP4FileType.QT}, MP4FileType.ISOM_QT.compatibleBrands());
    }
  }

  @Nested
  @DisplayName("toString Tests")
  class ToStringTests {

    @Test
    @DisplayName("Should format string with major brand and minor version only")
    void testToString_MajorBrandOnly() {
      MP4FileType fileType = new MP4FileType(MP4FileType.ISOM, 512);

      assertEquals("ftyp[isom.512]", fileType.toString());
    }

    @Test
    @DisplayName("Should format string with compatible brands")
    void testToString_WithCompatibleBrands() {
      assertEquals("ftyp[isom.0, isom, qt  ]", MP4FileType.ISOM_QT.toString());
    }

    @Test
    @DisplayName("Should format string with custom brands")
    void testToString_CustomBrands() {
      MP4FileType fileType = new MP4FileType(0x6D703432, 1, 0x6D703431); // mp42, mp41

      assertEquals("ftyp[mp42.1, mp41]", fileType.toString());
    }

    @Test
    @DisplayName("Should handle special characters in brand codes")
    void testToString_SpecialCharacters() {
      MP4FileType fileType = new MP4FileType(0x74657374, 0, 0x20202020); // "test", "    "

      assertEquals("ftyp[test.0,     ]", fileType.toString());
    }
  }

  @Nested
  @DisplayName("toBytes Tests")
  class ToBytesTests {

    @Test
    @DisplayName("Should generate correct byte array for simple file type")
    void testToBytes_Simple() {
      MP4FileType fileType = new MP4FileType(MP4FileType.ISOM, 0);
      byte[] bytes = fileType.toBytes();

      assertEquals(16, bytes.length); // 4 (size) + 4 (ftyp) + 4 (major) + 4 (minor)

      ByteBuffer bb = ByteBuffer.wrap(bytes);
      assertEquals(16, bb.getInt()); // size
      assertEquals(0x66747970, bb.getInt()); // 'ftyp'
      assertEquals(MP4FileType.ISOM, bb.getInt()); // major brand
      assertEquals(0, bb.getInt()); // minor version
    }

    @Test
    @DisplayName("Should generate correct byte array with compatible brands")
    void testToBytes_WithCompatibleBrands() {
      byte[] bytes = MP4FileType.ISOM_QT.toBytes();

      assertEquals(24, bytes.length); // 4 + 4 + 4 + 4 + 4 + 4

      ByteBuffer bb = ByteBuffer.wrap(bytes);
      assertEquals(24, bb.getInt()); // size
      assertEquals(0x66747970, bb.getInt()); // 'ftyp'
      assertEquals(MP4FileType.ISOM, bb.getInt()); // major brand
      assertEquals(0, bb.getInt()); // minor version
      assertEquals(MP4FileType.ISOM, bb.getInt()); // compatible brand 1
      assertEquals(MP4FileType.QT, bb.getInt()); // compatible brand 2
    }

    @Test
    @DisplayName("Should generate correct byte array with multiple compatible brands")
    void testToBytes_MultipleCompatibleBrands() {
      int[] brands = {0x6D703431, 0x6D703432, 0x69736F6D};
      MP4FileType fileType = new MP4FileType(0x6D703432, 1, brands);
      byte[] bytes = fileType.toBytes();

      assertEquals(28, bytes.length); // 4 + 4 + 4 + 4 + 4 + 4 + 4

      ByteBuffer bb = ByteBuffer.wrap(bytes);
      assertEquals(28, bb.getInt());
      assertEquals(0x66747970, bb.getInt());
      assertEquals(0x6D703432, bb.getInt()); // major brand
      assertEquals(1, bb.getInt()); // minor version
      assertEquals(0x6D703431, bb.getInt()); // compatible brand 1
      assertEquals(0x6D703432, bb.getInt()); // compatible brand 2
      assertEquals(0x69736F6D, bb.getInt()); // compatible brand 3
    }
  }

  @Nested
  @DisplayName("Size Calculation Tests")
  class SizeTests {

    @Test
    @DisplayName("Should calculate correct size for simple file type")
    void testSize_Simple() {
      MP4FileType fileType = new MP4FileType(MP4FileType.ISOM, 0);

      assertEquals(16, fileType.size()); // (2 + 2) * 4
    }

    @Test
    @DisplayName("Should calculate correct size with compatible brands")
    void testSize_WithCompatibleBrands() {
      assertEquals(24, MP4FileType.ISOM_QT.size()); // (2 + 4) * 4
    }

    @Test
    @DisplayName("Should calculate correct size with multiple compatible brands")
    void testSize_MultipleCompatibleBrands() {
      MP4FileType fileType =
          new MP4FileType(
              MP4FileType.ISOM, 1, 0x6D703431, 0x6D703432, 0x69736F6D, 0x71742020, 0x64617368);

      assertEquals(36, fileType.size()); // (2 + 7) * 4
    }
  }

  @Nested
  @DisplayName("Getter Tests")
  class GetterTests {

    @Test
    @DisplayName("Should return correct major brand")
    void testMajorBrand() {
      MP4FileType fileType = new MP4FileType(0x12345678, 999);

      assertEquals(0x12345678, fileType.majorBrand());
    }

    @Test
    @DisplayName("Should return correct minor version")
    void testMinorVersion() {
      MP4FileType fileType = new MP4FileType(MP4FileType.ISOM, 123456);

      assertEquals(123456, fileType.minorVersion());
    }

    @Test
    @DisplayName("Should return empty array for no compatible brands")
    void testCompatibleBrands_Empty() {
      MP4FileType fileType = new MP4FileType(MP4FileType.ISOM, 0);

      assertEquals(0, fileType.compatibleBrands().length);
    }

    @Test
    @DisplayName("Should return correct compatible brands array")
    void testCompatibleBrands_Multiple() {
      int[] expectedBrands = {0x6D703431, 0x6D703432, MP4FileType.QT};
      MP4FileType fileType = new MP4FileType(MP4FileType.ISOM, 1, expectedBrands);

      assertArrayEquals(expectedBrands, fileType.compatibleBrands());
    }

    @Test
    @DisplayName("Should return independent copy of compatible brands array")
    void testCompatibleBrands_IndependentCopy() {
      int[] originalBrands = {MP4FileType.ISOM, MP4FileType.QT};
      MP4FileType fileType = new MP4FileType(0x74657374, 0, originalBrands);

      int[] returnedBrands = fileType.compatibleBrands();
      returnedBrands[0] = 0x12345678; // Modify returned array

      // Original should not be affected
      assertArrayEquals(originalBrands, fileType.compatibleBrands());
    }
  }

  @Nested
  @DisplayName("Edge Cases Tests")
  class EdgeCasesTests {

    @Test
    @DisplayName("Should handle zero values")
    void testZeroValues() {
      MP4FileType fileType = new MP4FileType(0, 0, 0, 0);

      assertEquals(0, fileType.majorBrand());
      assertEquals(0, fileType.minorVersion());
      assertArrayEquals(new int[] {0, 0}, fileType.compatibleBrands());
      assertEquals(
          "ftyp[\u0000\u0000\u0000\u0000.0, \u0000\u0000\u0000\u0000, \u0000\u0000\u0000\u0000]",
          fileType.toString());
    }

    @Test
    @DisplayName("Should handle negative values")
    void testNegativeValues() {
      MP4FileType fileType = new MP4FileType(-1, -1, -1);

      assertEquals(-1, fileType.majorBrand());
      assertEquals(-1, fileType.minorVersion());
      assertArrayEquals(new int[] {-1}, fileType.compatibleBrands());
    }

    @Test
    @DisplayName("Should handle maximum integer values")
    void testMaximumValues() {
      MP4FileType fileType =
          new MP4FileType(Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE);

      assertEquals(Integer.MAX_VALUE, fileType.majorBrand());
      assertEquals(Integer.MAX_VALUE, fileType.minorVersion());
      assertArrayEquals(new int[] {Integer.MAX_VALUE}, fileType.compatibleBrands());
    }
  }
}
