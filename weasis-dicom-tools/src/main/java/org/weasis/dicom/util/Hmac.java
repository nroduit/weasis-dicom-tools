/*
 * Copyright (c) 2020-2021 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.util;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Objects;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.weasis.core.util.StringUtil;

/**
 * Utility class for HMAC (Hash-based Message Authentication Code) operations using HMAC-SHA256.
 * Provides methods for key generation, hashing, and hex encoding/decoding for generating consistent
 * hash values and UUID-like identifiers.
 */
public class Hmac {

  public static final int KEY_BYTE_LENGTH = 16;
  private static final String HMAC_SHA256 = "HmacSHA256";
  private static final int EXPECTED_HEX_KEY_LENGTH = 32;
  private static final String HEX_DIGITS = "0123456789abcdef";

  // Pre-computed lookup table for hex conversion (more efficient than array access)
  private static final char[] HEX_LOOKUP = HEX_DIGITS.toCharArray();

  private final Mac mac;

  public Hmac() {
    this(generateRandomKey());
  }

  public Hmac(byte[] hmacKey) {
    this.mac = initHMAC(Objects.requireNonNull(hmacKey, "HMAC key cannot be null"));
  }

  private static Mac initHMAC(byte[] keyValue) {
    try {
      SecretKeySpec key = new SecretKeySpec(keyValue, HMAC_SHA256);
      Mac mac = Mac.getInstance(HMAC_SHA256);
      mac.init(key);
      return mac;
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("HMAC algorithm not available", e);
    } catch (InvalidKeyException e) {
      throw new IllegalArgumentException("Invalid HMAC key", e);
    }
  }

  /** Generate a random secret key of 16 bytes (128 bits) */
  public static byte[] generateRandomKey() {
    SecureRandom random = new SecureRandom();
    byte[] bytes = new byte[KEY_BYTE_LENGTH];
    random.nextBytes(bytes);
    return bytes;
  }

  /** Convert byte array to lowercase hexadecimal string */
  public static String byteToHex(byte[] byteArray) {
    Objects.requireNonNull(byteArray, "Byte array cannot be null");
    final char[] hexChars = new char[byteArray.length * 2];
    for (int i = 0; i < byteArray.length; i++) {
      int byteValue = byteArray[i] & 0xFF;
      // Extract the upper 4 bits and lower 4 bits
      hexChars[i * 2] = HEX_LOOKUP[byteValue >>> 4];
      hexChars[i * 2 + 1] = HEX_LOOKUP[byteValue & 0x0F];
    }
    return new String(hexChars);
  }

  /** Format hex key for display (UUID-like format) */
  public static String showHexKey(String key) {
    Objects.requireNonNull(key, "Key cannot be null");
    if (key.length() != EXPECTED_HEX_KEY_LENGTH) {
      throw new IllegalArgumentException(
          "Key must be exactly " + EXPECTED_HEX_KEY_LENGTH + " characters long");
    }
    return String.format(
        "%s-%s-%s-%s-%s",
        key.substring(0, 8),
        key.substring(8, 12),
        key.substring(12, 16),
        key.substring(16, 20),
        key.substring(20));
  }

  /** Convert hexadecimal string to byte array */
  public static byte[] hexToByte(String hexString) {
    Objects.requireNonNull(hexString, "Hex string cannot be null");

    // Remove optional 0x prefix
    String cleanHex = hexString.startsWith("0x") ? hexString.substring(2) : hexString;

    // Remove dashes (for UUID-like format)
    cleanHex = cleanHex.replace("-", "");

    if (cleanHex.isEmpty()) {
      return new byte[0];
    }

    // Handle odd length by padding with leading zero
    if (cleanHex.length() % 2 != 0) {
      cleanHex = "0" + cleanHex;
    }

    byte[] result = new byte[cleanHex.length() / 2];
    for (int i = 0; i < result.length; i++) {
      int index = i * 2;
      try {
        result[i] = (byte) Integer.parseInt(cleanHex.substring(index, index + 2), 16);
      } catch (NumberFormatException e) {
        throw new IllegalArgumentException(
            "Invalid hex character at position " + index + " in: " + hexString, e);
      }
    }
    return result;
  }

  /** Validate if the given hex key is valid (32 hex characters, with or without dashes) */
  public static boolean validateKey(String hexKey) {
    if (hexKey == null) {
      return false;
    }
    String cleanHexKey = hexKey.replace("-", "");
    if (cleanHexKey.length() != EXPECTED_HEX_KEY_LENGTH) {
      return false;
    }

    try {
      hexToByte(cleanHexKey);
      return true;
    } catch (IllegalArgumentException e) {
      return false;
    }
  }

  /** Scale hash value to specified range [scaleMin, scaleMax) */
  public double scaleHash(String value, int scaledMin, int scaledMax) {
    final byte[] hash = new byte[6];
    final double max = 0x1000000000000L;
    final double scale = scaledMax - (double) scaledMin;

    System.arraycopy(byteHash(value), 0, hash, 0, 6);
    double fraction = new BigInteger(1, hash).doubleValue() / max;
    return (int) (fraction * scale) + (double) scaledMin;
  }

  /** Generate a UUID-like identifier from input UID using HMAC hash */
  public String uidHash(String inputUID) {
    if (!StringUtil.hasText(inputUID)) {
      return null;
    }
    byte[] hash = byteHash(inputUID);
    byte[] uuid = new byte[16];
    System.arraycopy(hash, 0, uuid, 0, Math.min(16, hash.length));

    // Format as UUID version 4 (random)
    // Set version bits (4 bits starting at bit 48)
    uuid[6] = (byte) ((uuid[6] & 0x0F) | 0x40);

    // Set variant bits (2 bits starting at bit 64)
    uuid[8] = (byte) ((uuid[8] & 0x3F) | 0x80);
    return "2.25." + new BigInteger(1, uuid);
  }

  /** Compute HMAC hash of the input value */
  public byte[] byteHash(String value) {
    Objects.requireNonNull(value, "Value cannot be null");

    synchronized (mac) {
      return mac.doFinal(value.getBytes(StandardCharsets.UTF_8));
    }
  }

  /** Compute HMAC hash and return as hex string */
  public String hexHash(String value) {
    return byteToHex(byteHash(value));
  }
}
