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

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.weasis.dicom.util.Hmac;

class HmacTest {

  @Test
  void generateRandomKeyReturnsExpectedLength() {
    byte[] key = Hmac.generateRandomKey();
    Assertions.assertEquals(Hmac.KEY_BYTE_LENGTH, key.length);
  }

  @Test
  void byteToHexConvertsCorrectly() {
    String hexVal = "5e80b11ce89d46e490a244fde301d339";
    byte[] bytesVal = new byte[]{94, -128, -79, 28, -24, -99, 70, -28, -112, -94, 68, -3, -29, 1, -45, 57};
    byte[] bytes = Hmac.hexToByte(hexVal);
    Assertions.assertArrayEquals(bytesVal, bytes);
    String hex = Hmac.byteToHex(bytes);
    Assertions.assertEquals(hexVal, hex);

    Assertions.assertTrue(Hmac.validateKey(hexVal));

    String showKey = Hmac.showHexKey(hex);
    Assertions.assertEquals("5e80b11c-e89d-46e4-90a2-44fde301d339", showKey);

    Assertions.assertTrue(Hmac.validateKey(hexVal));
    Assertions.assertTrue(Hmac.validateKey(showKey));
    Assertions.assertFalse(Hmac.validateKey("invalid"));
    Assertions.assertThrows(IllegalArgumentException.class, () -> Hmac.hexToByte("0x0g1b2c"));

    Hmac hmac = new Hmac(bytes);
    String uid1 = hmac.uidHash("5e80b11c-e89d-46e4-90a2-44fde301d339");
    Assertions.assertEquals(uid1,  new Hmac(bytes).uidHash("5e80b11c-e89d-46e4-90a2-44fde301d339"));
  }

  @Test
  void scaleHashReturnsExpectedValueForPositiveInput() {
    Hmac hmac = new Hmac();
    double result = hmac.scaleHash("positive", 0, 10);
    Assertions.assertTrue(result >= 0 && result < 10);

    result = hmac.scaleHash("negative", -10, 0);
    Assertions.assertTrue(result >= -10 && result < 0);

    result = hmac.scaleHash("zero", 0, 0);
    Assertions.assertEquals(0, result);

    result = hmac.scaleHash("large", 0, Integer.MAX_VALUE);
    Assertions.assertTrue(result >= 0 && result < Integer.MAX_VALUE);

    result = hmac.scaleHash("small", Integer.MIN_VALUE, Integer.MAX_VALUE);
    Assertions.assertTrue(result >= Integer.MIN_VALUE && result < Integer.MAX_VALUE);
  }


  @Test
  void uidHashReturnsNullForEmptyInput() {
    Hmac hmac = new Hmac();
    String result = hmac.uidHash("");
    Assertions.assertNull(result);

    result = hmac.uidHash("valid");
    Assertions.assertTrue(result.startsWith("2.25."));

    String result1 = hmac.uidHash("5e80b11c-e89d-46e4-90a2-44fde301d339");
    String result2 = hmac.uidHash("5e80b11c-e89d-46e4-90a2-44fde301d339");
    Assertions.assertEquals(result1, result2);
  }
}