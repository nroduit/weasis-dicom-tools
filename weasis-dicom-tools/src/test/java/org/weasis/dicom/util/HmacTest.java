/*
 * Copyright (c) 2024 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.util;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

@DisplayName("Hmac Tests")
class HmacTest {

  private static final String VALID_HEX_KEY = "5e80b11ce89d46e490a244fde301d339";
  private static final String FORMATTED_HEX_KEY = "5e80b11c-e89d-46e4-90a2-44fde301d339";
  private static final byte[] VALID_BYTE_KEY = {
    94, -128, -79, 28, -24, -99, 70, -28, -112, -94, 68, -3, -29, 1, -45, 57
  };

  private Hmac hmac;

  @BeforeEach
  void setUp() {
    hmac = new Hmac(VALID_BYTE_KEY);
  }

  @Nested
  @DisplayName("Key Generation Tests")
  class KeyGenerationTests {
    @Test
    @DisplayName("Should generate random key with expected length")
    void generateRandomKeyReturnsExpectedLength() {
      byte[] key = Hmac.generateRandomKey();
      assertEquals(Hmac.KEY_BYTE_LENGTH, key.length);
    }

    @Test
    @DisplayName("Should generate different keys on multiple calls")
    void generateRandomKeyReturnsDifferentKeys() {
      byte[] key1 = Hmac.generateRandomKey();
      byte[] key2 = Hmac.generateRandomKey();
      assertFalse(Arrays.equals(key1, key2));
    }

    @Test
    @DisplayName("Should generate keys with proper entropy")
    void generateRandomKeyHasEntropy() {
      // Generate multiple keys and check they're not all zeros or all same value
      for (int i = 0; i < 10; i++) {
        byte[] key = Hmac.generateRandomKey();
        boolean allZeros = IntStream.range(0, key.length).allMatch(b -> key[b] == 0);
        boolean allSame = IntStream.range(0, key.length).allMatch(b -> key[b] == key[0]);
        assertFalse(allZeros, "Generated key should not be all zeros");
        assertFalse(allSame, "Generated key should not have all same values");
      }
    }
  }

  @Nested
  @DisplayName("Hex Conversion Tests")
  class HexConversionTests {

    @Test
    @DisplayName("Should convert bytes to hex correctly")
    void byteToHexConvertsCorrectly() {
      String hex = Hmac.byteToHex(VALID_BYTE_KEY);
      assertEquals(VALID_HEX_KEY, hex);
    }

    @Test
    @DisplayName("Should convert hex to bytes correctly")
    void hexToByteConvertsCorrectly() {
      byte[] bytes = Hmac.hexToByte(VALID_HEX_KEY);
      assertArrayEquals(VALID_BYTE_KEY, bytes);
    }

    @Test
    @DisplayName("Should handle round-trip conversion")
    void roundTripConversionWorksCorrectly() {
      String originalHex = VALID_HEX_KEY;
      byte[] bytes = Hmac.hexToByte(originalHex);
      String convertedHex = Hmac.byteToHex(bytes);
      assertEquals(originalHex, convertedHex);
    }

    @Test
    @DisplayName("Should handle empty byte array")
    void byteToHexHandlesEmptyArray() {
      String hex = Hmac.byteToHex(new byte[0]);
      assertEquals("", hex);
    }

    @Test
    @DisplayName("Should handle empty hex string")
    void hexToByteHandlesEmptyString() {
      byte[] bytes = Hmac.hexToByte("");
      assertArrayEquals(new byte[0], bytes);
    }

    @ParameterizedTest
    @ValueSource(
        strings = {"0x5e80b11ce89d46e490a244fde301d339", "5E80B11CE89D46E490A244FDE301D339"})
    @DisplayName("Should handle hex string variations")
    void hexToByteHandlesVariations(String hexString) {
      byte[] bytes = Hmac.hexToByte(hexString);
      assertArrayEquals(VALID_BYTE_KEY, bytes);
    }

    @Test
    @DisplayName("Should handle odd-length hex strings")
    void hexToByteHandlesOddLength() {
      byte[] bytes = Hmac.hexToByte("5e8");
      assertArrayEquals(new byte[] {5, -24}, bytes);
    }

    @Test
    @DisplayName("Should handle hex strings with dashes")
    void hexToByteHandlesDashes() {
      byte[] bytes = Hmac.hexToByte(FORMATTED_HEX_KEY);
      assertArrayEquals(VALID_BYTE_KEY, bytes);
    }

    @Test
    @DisplayName("Should throw exception for null input")
    void hexToByteThrowsExceptionForNull() {
      assertThrows(NullPointerException.class, () -> Hmac.hexToByte(null));
      assertThrows(NullPointerException.class, () -> Hmac.byteToHex(null));
    }

    @ParameterizedTest
    @ValueSource(strings = {"0x0g1b2c", "invalid", "xyz", "5e80b11ce89d46e490a244fde301d33g"})
    @DisplayName("Should throw exception for invalid hex characters")
    void hexToByteThrowsExceptionForInvalidHex(String invalidHex) {
      assertThrows(IllegalArgumentException.class, () -> Hmac.hexToByte(invalidHex));
    }
  }

  @Nested
  @DisplayName("Key Validation Tests")
  class KeyValidationTests {

    @Test
    @DisplayName("Should validate correct hex key")
    void validateKeyAcceptsValidKey() {
      assertTrue(Hmac.validateKey(VALID_HEX_KEY));
    }

    @Test
    @DisplayName("Should validate formatted hex key")
    void validateKeyAcceptsFormattedKey() {
      assertTrue(Hmac.validateKey(FORMATTED_HEX_KEY));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(
        strings = {
          "invalid",
          "too-short",
          "5e80b11ce89d46e490a244fde301d339a",
          "5e80b11ce89d46e490a244fde301d33"
        })
    @DisplayName("Should reject invalid keys")
    void validateKeyRejectsInvalidKeys(String invalidKey) {
      assertFalse(Hmac.validateKey(invalidKey));
    }

    @Test
    @DisplayName("Should validate keys with mixed case")
    void validateKeyHandlesMixedCase() {
      assertTrue(Hmac.validateKey("5E80B11CE89D46E490A244FDE301D339"));
    }
  }

  @Nested
  @DisplayName("Key Formatting Tests")
  class KeyFormattingTests {

    @Test
    @DisplayName("Should format hex key correctly")
    void showHexKeyFormatsCorrectly() {
      String formatted = Hmac.showHexKey(VALID_HEX_KEY);
      assertEquals(FORMATTED_HEX_KEY, formatted);
    }

    @Test
    @DisplayName("Should throw exception for null key")
    void showHexKeyThrowsExceptionForNull() {
      assertThrows(NullPointerException.class, () -> Hmac.showHexKey(null));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "tooshort", "5e80b11ce89d46e490a244fde301d339a"})
    @DisplayName("Should throw exception for invalid key length")
    void showHexKeyThrowsExceptionForInvalidLength(String invalidKey) {
      assertThrows(IllegalArgumentException.class, () -> Hmac.showHexKey(invalidKey));
    }
  }

  @Nested
  @DisplayName("HMAC Construction Tests")
  class HmacConstructionTests {

    @Test
    @DisplayName("Should create HMAC with default constructor")
    void constructorWithoutParametersWorks() {
      Hmac newHmac = new Hmac();
      assertNotNull(newHmac);
    }

    @Test
    @DisplayName("Should create HMAC with byte array")
    void constructorWithByteArrayWorks() {
      Hmac newHmac = new Hmac(VALID_BYTE_KEY);
      assertNotNull(newHmac);
    }

    @Test
    @DisplayName("Should throw exception for null key")
    void constructorThrowsExceptionForNullKey() {
      assertThrows(NullPointerException.class, () -> new Hmac(null));
    }

    @Test
    @DisplayName("Should create different instances with same key produce same results")
    void sameKeyProducesSameResults() {
      Hmac hmac1 = new Hmac(VALID_BYTE_KEY);
      Hmac hmac2 = new Hmac(VALID_BYTE_KEY);

      String testInput = "test-value";
      assertArrayEquals(hmac1.byteHash(testInput), hmac2.byteHash(testInput));
    }
  }

  @Nested
  @DisplayName("Hash Computation Tests")
  class HashComputationTests {

    @Test
    @DisplayName("Should compute byte hash correctly")
    void byteHashComputesCorrectly() {
      byte[] hash = hmac.byteHash("test");
      assertNotNull(hash);
      assertTrue(hash.length > 0);
    }

    @Test
    @DisplayName("Should compute hex hash correctly")
    void hexHashComputesCorrectly() {
      String hash = hmac.hexHash("test");
      assertNotNull(hash);
      assertFalse(hash.isEmpty());
      // Verify it's valid hex
      assertTrue(hash.matches("^[0-9a-f]+$"));
    }

    @Test
    @DisplayName("Should produce consistent hash for same input")
    void hashIsConsistentForSameInput() {
      String input = "consistent-test";
      byte[] hash1 = hmac.byteHash(input);
      byte[] hash2 = hmac.byteHash(input);
      assertArrayEquals(hash1, hash2);
    }

    @Test
    @DisplayName("Should produce different hash for different inputs")
    void hashIsDifferentForDifferentInputs() {
      byte[] hash1 = hmac.byteHash("input1");
      byte[] hash2 = hmac.byteHash("input2");
      assertFalse(Arrays.equals(hash1, hash2));
    }

    @Test
    @DisplayName("Should handle UTF-8 characters correctly")
    void hashHandlesUTF8Characters() {
      String utf8Input = "测试🔐";
      assertDoesNotThrow(() -> hmac.byteHash(utf8Input));
      assertDoesNotThrow(() -> hmac.hexHash(utf8Input));
    }

    @Test
    @DisplayName("Should throw exception for null input")
    void hashThrowsExceptionForNullInput() {
      assertThrows(NullPointerException.class, () -> hmac.byteHash(null));
      assertThrows(NullPointerException.class, () -> hmac.hexHash(null));
    }
  }

  @Nested
  @DisplayName("Scale Hash Tests")
  class ScaleHashTests {

    @Test
    @DisplayName("Should scale hash to positive range")
    void scaleHashToPositiveRange() {
      double result = hmac.scaleHash("positive", 0, 10);
      assertTrue(result >= 0 && result < 10);
    }

    @Test
    @DisplayName("Should scale hash to negative range")
    void scaleHashToNegativeRange() {
      double result = hmac.scaleHash("negative", -10, 0);
      assertTrue(result >= -10 && result < 0);
    }

    @Test
    @DisplayName("Should handle equal min and max values")
    void scaleHashHandlesEqualMinMax() {
      double result = hmac.scaleHash("equal", 5, 5);
      assertEquals(5.0, result);
    }

    @Test
    @DisplayName("Should handle large ranges")
    void scaleHashHandlesLargeRanges() {
      double result = hmac.scaleHash("large", 0, Integer.MAX_VALUE);
      assertTrue(result >= 0 && result < Integer.MAX_VALUE);
    }

    @Test
    @DisplayName("Should handle full integer range")
    void scaleHashHandlesFullIntegerRange() {
      int min = Integer.MIN_VALUE;
      int max = Integer.MAX_VALUE;
      double result = hmac.scaleHash("large-range", min, max);
      assertTrue(result >= min && result < max);
    }

    @Test
    @DisplayName("Should produce consistent results for same input")
    void scaleHashIsConsistent() {
      String input = "consistent";
      double result1 = hmac.scaleHash(input, 0, 100);
      double result2 = hmac.scaleHash(input, 0, 100);
      assertEquals(result1, result2);
    }

    @Test
    @DisplayName("Should throw exception for null input")
    void scaleHashThrowsExceptionForNullInput() {
      assertThrows(NullPointerException.class, () -> hmac.scaleHash(null, 0, 10));
    }

    @ParameterizedTest
    @MethodSource("provideScaleRanges")
    @DisplayName("Should respect scale ranges")
    void scaleHashRespectsRanges(String input, int min, int max) {
      double result = hmac.scaleHash(input, min, max);
      if (min < max) {
        assertTrue(
            result >= min && result < max,
            String.format("Result %f should be in range [%d, %d)", result, min, max));
      } else {
        assertEquals(min, result);
      }
    }

    static Stream<Arguments> provideScaleRanges() {
      return Stream.of(
          Arguments.of("test1", 0, 10),
          Arguments.of("test2", -5, 5),
          Arguments.of("test3", 100, 200),
          Arguments.of("test4", -100, -50),
          Arguments.of("test5", 0, 1));
    }
  }

  @Nested
  @DisplayName("UID Hash Tests")
  class UidHashTests {

    @Test
    @DisplayName("Should return null for empty input")
    void uidHashReturnsNullForEmptyInput() {
      assertNull(hmac.uidHash(""));
      assertNull(hmac.uidHash("   "));
    }

    @Test
    @DisplayName("Should return null for null input")
    void uidHashReturnsNullForNullInput() {
      assertNull(hmac.uidHash(null));
    }

    @Test
    @DisplayName("Should generate valid UID format")
    void uidHashGeneratesValidFormat() {
      String uid = hmac.uidHash("valid-input");
      assertNotNull(uid);
      assertTrue(uid.startsWith("2.25."));
      assertTrue(uid.length() > 5);
    }

    @Test
    @DisplayName("Should produce consistent UIDs for same input")
    void uidHashIsConsistent() {
      String input = "consistent-uid-test";
      String uid1 = hmac.uidHash(input);
      String uid2 = hmac.uidHash(input);
      assertEquals(uid1, uid2);
    }

    @Test
    @DisplayName("Should produce different UIDs for different inputs")
    void uidHashIsDifferentForDifferentInputs() {
      String uid1 = hmac.uidHash("input1");
      String uid2 = hmac.uidHash("input2");
      assertNotEquals(uid1, uid2);
    }

    @Test
    @DisplayName("Should handle various input formats")
    void uidHashHandlesVariousInputs() {
      String[] inputs = {
        "simple",
        "with-dashes",
        "with.dots",
        "with_underscores",
        "WITH_CAPS",
        "123456789",
        "mixed123ABC"
      };

      for (String input : inputs) {
        String uid = hmac.uidHash(input);
        assertNotNull(uid);
        assertTrue(uid.startsWith("2.25."));
      }
    }

    @Test
    @DisplayName("Should maintain consistency across different HMAC instances with same key")
    void uidHashConsistentAcrossInstances() {
      Hmac hmac1 = new Hmac(VALID_BYTE_KEY);
      Hmac hmac2 = new Hmac(VALID_BYTE_KEY);

      String input = "test-uid-consistency";
      String uid1 = hmac1.uidHash(input);
      String uid2 = hmac2.uidHash(input);

      assertEquals(uid1, uid2);
    }
  }

  @Nested
  @DisplayName("Thread Safety Tests")
  class ThreadSafetyTests {

    @Test
    @DisplayName("Should handle concurrent hash operations safely")
    void concurrentHashOperationsAreSafe() {
      final int threadCount = 10;
      final int operationsPerThread = 100;

      IntStream.range(0, threadCount)
          .parallel()
          .forEach(
              threadId -> {
                for (int i = 0; i < operationsPerThread; i++) {
                  String input = "thread-" + threadId + "-op-" + i;
                  assertDoesNotThrow(() -> hmac.byteHash(input));
                  assertDoesNotThrow(() -> hmac.hexHash(input));
                  assertDoesNotThrow(() -> hmac.uidHash(input));
                  assertDoesNotThrow(() -> hmac.scaleHash(input, 0, 100));
                }
              });
    }
  }

  @Nested
  @DisplayName("Performance Tests")
  class PerformanceTests {

    @Test
    @DisplayName("Should perform hash operations efficiently")
    void hashOperationsAreEfficient() {
      final int iterations = 1000;
      long startTime = System.currentTimeMillis();

      for (int i = 0; i < iterations; i++) {
        hmac.byteHash("performance-test-" + i);
      }

      long endTime = System.currentTimeMillis();
      long duration = endTime - startTime;

      // Should complete 1000 operations in reasonable time (less than 1 second)
      assertTrue(duration < 1000, "Hash operations took too long: " + duration + "ms");
    }
  }
}
