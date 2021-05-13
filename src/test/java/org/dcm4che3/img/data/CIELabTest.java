/*
 * Copyright (c) 2009-2021 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at http://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.dcm4che3.img.data;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertNull;

import java.awt.Color;
import org.junit.Test;

public class CIELabTest {

  private static final int[] rgb1 = new int[] {255, 200, 0};
  private static final int[] dcmLab1 = new int[] {54534, 34371, 54623};

  private static final int[] rgb2 = new int[] {1, 7, 9};
  private static final int[] dcmLab2 = new int[] {1054, 32564, 32559};

  @Test
  public void testDicomLab2rgb() {
    assertNull(CIELab.dicomLab2rgb(new int[] {1, 1, 1, 1}));
    assertNull(CIELab.dicomLab2rgb(null));

    assertArrayEquals(rgb1, CIELab.dicomLab2rgb(dcmLab1));
    assertArrayEquals(rgb2, CIELab.dicomLab2rgb(dcmLab2));
  }

  @Test
  public void testRgbToDicomLab() {
    assertArrayEquals(dcmLab1, CIELab.rgbToDicomLab(rgb1[0], rgb1[1], rgb1[2]));
    assertArrayEquals(dcmLab2, CIELab.rgbToDicomLab(rgb2[0], rgb2[1], rgb2[2]));
  }

  @Test
  public void testRgbToDicomLab2() throws NumberFormatException {
    assertArrayEquals(dcmLab1, CIELab.rgbToDicomLab(new Color(rgb1[0], rgb1[1], rgb1[2])));
    assertArrayEquals(dcmLab2, CIELab.rgbToDicomLab(new Color(rgb2[0], rgb2[1], rgb2[2])));
  }
}
