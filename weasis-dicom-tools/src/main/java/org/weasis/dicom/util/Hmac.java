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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.util.StringUtil;

public class Hmac {
  private static final Logger LOGGER = LoggerFactory.getLogger(Hmac.class);

  public static final int KEY_BYTE_LENGTH = 16;
  private static final char[] LOOKUP_TABLE_LOWER =
      new char[] {
        0x30, 0x31, 0x32, 0x33, 0x34, 0x35, 0x36, 0x37, 0x38, 0x39, 0x61, 0x62, 0x63, 0x64, 0x65,
        0x66
      };
  private static final String HMAC_SHA256 = "HmacSHA256";

  private Mac mac;

  public Hmac() {
    initHMAC(generateRandomKey());
  }

  public Hmac(byte[] hmacKey) {
    initHMAC(hmacKey);
  }

  private void initHMAC(byte[] keyValue) {
    try {
      SecretKeySpec key = new SecretKeySpec(keyValue, HMAC_SHA256);
      this.mac = Mac.getInstance(HMAC_SHA256);
      this.mac.init(key);
    } catch (NoSuchAlgorithmException e) {
      LOGGER.error("Invalid algorithm for the HMAC", e);
    } catch (InvalidKeyException e) {
      LOGGER.error("Invalid key for the HMAC init", e);
    }
  }

  /*
   * Generate a random secret key of 32bytes
   * */
  public static byte[] generateRandomKey() {
    SecureRandom random = new SecureRandom();
    byte[] bytes = new byte[KEY_BYTE_LENGTH];
    random.nextBytes(bytes);
    return bytes;
  }

  public static String byteToHex(byte[] byteArray) {
    final char[] buffer = new char[byteArray.length * 2];
    for (int i = 0; i < byteArray.length; i++) {
      // extract the upper 4 bit and look up char (0-A)
      buffer[i << 1] = LOOKUP_TABLE_LOWER[(byteArray[i] >> 4) & 0xF];
      // extract the lower 4 bit and look up char (0-A)
      buffer[(i << 1) + 1] = LOOKUP_TABLE_LOWER[(byteArray[i] & 0xF)];
    }
    return new String(buffer);
  }

  public static String showHexKey(String key) {
    return String.format(
        "%s-%s-%s-%s-%s",
        key.substring(0, 8),
        key.substring(8, 12),
        key.substring(12, 16),
        key.substring(16, 20),
        key.substring(20));
  }

  public static byte[] hexToByte(String hexString) {
    int start;
    if (Objects.requireNonNull(hexString).length() > 2
        && hexString.charAt(0) == '0'
        && hexString.charAt(1) == 'x') {
      start = 2;
    } else {
      start = 0;
    }

    int len = hexString.length();
    boolean isOddLength = len % 2 != 0;
    if (isOddLength) {
      start--;
    }

    byte[] data = new byte[(len - start) / 2];
    int first4Bits;
    int second4Bits;
    for (int i = start; i < len; i += 2) {
      if (i == start && isOddLength) {
        first4Bits = 0;
      } else {
        first4Bits = Character.digit(hexString.charAt(i), 16);
      }
      second4Bits = Character.digit(hexString.charAt(i + 1), 16);

      if (first4Bits == -1 || second4Bits == -1) {
        if (i == start && isOddLength) {
          throw new IllegalArgumentException(
              "'" + hexString.charAt(i + 1) + "' at index " + (i + 1) + " is not hex formatted");
        } else {
          throw new IllegalArgumentException(
              "'"
                  + hexString.charAt(i)
                  + hexString.charAt(i + 1)
                  + "' at index "
                  + i
                  + " is not hex formatted");
        }
      }

      data[(i - start) / 2] = (byte) ((first4Bits << 4) + second4Bits);
    }
    return data;
  }

  public static boolean validateKey(String hexKey) {
    String cleanHexKey = hexKey.replace("-", "");
    if (cleanHexKey.length() == 32) {
      hexToByte(cleanHexKey);
      return true;
    }
    return false;
  }

  // returns value in [scaleMin..scaleMax)
  public double scaleHash(String value, int scaledMin, int scaledMax) {
    final byte[] hash = new byte[6];
    final double max = 0x1000000000000L;
    final double scale = scaledMax - (double) scaledMin;

    System.arraycopy(byteHash(value), 0, hash, 0, 6);
    double fraction = new BigInteger(1, hash).doubleValue() / max;
    return (int) (fraction * scale) + (double) scaledMin;
  }

  public String uidHash(String inputUID) {
    if (!StringUtil.hasText(inputUID)) {
      return null;
    }
    byte[] uuid = new byte[16];
    System.arraycopy(byteHash(inputUID), 0, uuid, 0, 16);
    // https://en.wikipedia.org/wiki/Universally_unique_identifier
    // GUID type 4
    // Version -> 4
    uuid[6] &= 0x0F;
    uuid[6] |= 0x40;
    // Variant 1 -> 10b
    uuid[8] &= 0x3F;
    uuid[8] |= 0x80;
    return "2.25." + new BigInteger(1, uuid);
  }

  public byte[] byteHash(String value) {
    return mac.doFinal(value.getBytes(StandardCharsets.US_ASCII));
  }
}
