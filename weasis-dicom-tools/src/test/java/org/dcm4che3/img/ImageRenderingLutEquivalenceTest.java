/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.dcm4che3.img;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.function.Consumer;
import java.util.stream.Stream;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.VR;
import org.dcm4che3.image.PhotometricInterpretation;
import org.dcm4che3.img.data.PrDicomObject;
import org.dcm4che3.img.lut.WindLevelParameters;
import org.dcm4che3.img.stream.ImageDescriptor;
import org.dcm4che3.img.util.LookupTableUtils;
import org.dcm4che3.img.util.LutTestDataBuilder;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.weasis.opencv.data.ImageCV;
import org.weasis.opencv.data.PlanarImage;
import org.weasis.opencv.natives.NativeLibrary;
import org.weasis.opencv.op.lut.LutShape;

/**
 * The window/level rendering applies one composed table natively; the chain of Java lookups it
 * replaces stays as the reference, and both must give the same pixels, bit for bit.
 */
@DisplayNameGeneration(ReplaceUnderscores.class)
class ImageRenderingLutEquivalenceTest {
  // Odd sizes, so a row stride mistake cannot hide; room for every 16-bit value once
  private static final int WIDTH = 263;
  private static final int HEIGHT = 251;

  private record PixelFormat(String name, int cvType, int bitsStored, boolean signed) {
    int bitsAllocated() {
      return CvType.depth(cvType) <= CvType.CV_8S ? 8 : 16;
    }

    @Override
    public String toString() {
      return name;
    }
  }

  private record Variant(String name, Consumer<Attributes> attributes) {
    @Override
    public String toString() {
      return name;
    }
  }

  private static final List<PixelFormat> FORMATS =
      List.of(
          new PixelFormat("8-bit unsigned", CvType.CV_8UC1, 8, false),
          new PixelFormat("8-bit signed", CvType.CV_8SC1, 8, true),
          new PixelFormat("16-bit unsigned, 10 stored", CvType.CV_16UC1, 10, false),
          new PixelFormat("16-bit unsigned, 12 stored", CvType.CV_16UC1, 12, false),
          new PixelFormat("16-bit unsigned, 16 stored", CvType.CV_16UC1, 16, false),
          new PixelFormat("16-bit signed, 12 stored", CvType.CV_16SC1, 12, true),
          new PixelFormat("16-bit signed, 16 stored", CvType.CV_16SC1, 16, true));

  private static final List<Variant> VARIANTS =
      List.of(
          new Variant("no modality transformation", a -> {}),
          new Variant("rescale to Hounsfield units", a -> rescale(a, 1.0, -1024.0)),
          new Variant("fractional rescale", a -> rescale(a, 2.5, -100.5)),
          new Variant("MONOCHROME1", a -> photometric(a, PhotometricInterpretation.MONOCHROME1)),
          new Variant(
              "MONOCHROME1 with rescale",
              a -> {
                photometric(a, PhotometricInterpretation.MONOCHROME1);
                rescale(a, 1.0, 200.0);
              }),
          new Variant(
              "pixel padding value",
              a -> {
                rescale(a, 1.0, -1024.0);
                a.setInt(Tag.PixelPaddingValue, VR.US, 0);
              }),
          new Variant(
              "pixel padding range",
              a -> {
                rescale(a, 1.0, -1024.0);
                a.setInt(Tag.PixelPaddingValue, VR.US, 0);
                a.setInt(Tag.PixelPaddingRangeLimit, VR.US, 40);
              }));

  private static final List<LutShape> SHAPES =
      List.of(
          LutShape.LINEAR,
          LutShape.LINEAR_EXACT,
          LutShape.SIGMOID,
          LutShape.SIGMOID_NORM,
          LutShape.LOG,
          LutShape.LOG_INV);

  // width, center; null is the default window of the image
  private static final double[][] WINDOWS = {
    null, {1, 0}, {80, 40}, {400, -600}, {2000, 300}, {70000, 0}, {350, 40000}, {1.5, 127.25}
  };

  @BeforeAll
  static void loadNativeLibrary() {
    NativeLibrary.loadLibraryFromLibraryName();
  }

  static Stream<Arguments> formatsAndVariants() {
    return FORMATS.stream().flatMap(f -> VARIANTS.stream().map(v -> Arguments.of(f, v)));
  }

  @ParameterizedTest(name = "{0}, {1}")
  @MethodSource("formatsAndVariants")
  void composed_table_matches_the_lookup_chain(PixelFormat format, Variant variant) {
    var attributes = baseAttributes(format);
    variant.attributes().accept(attributes);
    var desc = new ImageDescriptor(attributes);
    PlanarImage image = imageOfEveryValue(format.cvType());
    var adapter = new DicomImageAdapter(image, desc, 0);

    List<String> differences = new ArrayList<>();
    for (LutShape shape : SHAPES) {
      for (double[] window : windowsOf(shape)) {
        for (boolean inverse : new boolean[] {false, true}) {
          for (boolean padding : paddingChoices(attributes)) {
            for (int outputBits : new int[] {8, 16}) {
              var params = params(shape, window, inverse, padding, outputBits);
              compare(image, adapter, params)
                  .ifPresent(
                      d ->
                          differences.add(
                              "%s window=%s inverse=%b padding=%b bits=%d: %s"
                                  .formatted(
                                      shape,
                                      window == null ? "default" : window[0] + "/" + window[1],
                                      inverse,
                                      padding,
                                      outputBits,
                                      d)));
            }
          }
        }
      }
    }
    image.release();
    assertTrue(
        differences.isEmpty(),
        () -> differences.size() + " difference(s), first: " + differences.get(0));
  }

  static Stream<Arguments> sequenceLuts() {
    return Stream.of(
        Arguments.of(FORMATS.get(0), LutTestDataBuilder.createLinearLut8Bit(), 0, 255),
        Arguments.of(FORMATS.get(3), LutTestDataBuilder.createContrastLut12Bit(), 0, 1023),
        Arguments.of(FORMATS.get(5), LutTestDataBuilder.createCtHounsfieldLut(), -1024, 3071),
        Arguments.of(FORMATS.get(6), LutTestDataBuilder.createCtHounsfieldLut(), -1024, 3071));
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("sequenceLuts")
  void composed_table_matches_with_a_modality_lut_sequence(
      PixelFormat format, Attributes lut, int min, int max) {
    var attributes = baseAttributes(format);
    attributes.newSequence(Tag.ModalityLUTSequence, 1).add(lut);
    // The sequence is only applied when the pixel values stay inside its table
    PlanarImage image = imageOf(format.cvType(), min, max);
    assertAllWindowsMatch(image, new ImageDescriptor(attributes), p -> {});
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("sequenceLuts")
  void composed_table_matches_with_a_voi_lut_sequence(
      PixelFormat format, Attributes lut, int min, int max) {
    var voiLut = LookupTableUtils.createLut(lut).orElseThrow();
    var attributes = baseAttributes(format);
    rescale(attributes, 1.0, -50.0);
    assertAllWindowsMatch(
        imageOfEveryValue(format.cvType()),
        new ImageDescriptor(attributes),
        p -> p.setVoiLutShape(new LutShape(voiLut, "sequence")));
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("sequenceLuts")
  void composed_table_matches_with_a_presentation_lut(
      PixelFormat format, Attributes ignored, int min, int max) {
    var attributes = baseAttributes(format);
    rescale(attributes, 1.0, -1024.0);
    var desc = new ImageDescriptor(attributes);

    var lutState = new Attributes();
    lutState.setString(Tag.SOPClassUID, VR.UI, "1.2.840.10008.5.1.4.1.1.11.1");
    lutState
        .newSequence(Tag.PresentationLUTSequence, 1)
        .add(LutTestDataBuilder.createLinearLut8Bit());
    var withLut = new PrDicomObject(lutState, desc);
    assertTrue(withLut.getPrLut().isPresent());
    assertAllWindowsMatch(
        imageOfEveryValue(format.cvType()), desc, p -> p.setPresentationState(withLut));

    var shapeState = new Attributes();
    shapeState.setString(Tag.SOPClassUID, VR.UI, "1.2.840.10008.5.1.4.1.1.11.1");
    shapeState.setString(Tag.PresentationLUTShape, VR.CS, "INVERSE");
    var withShape = new PrDicomObject(shapeState, desc);
    assertAllWindowsMatch(
        imageOfEveryValue(format.cvType()), desc, p -> p.setPresentationState(withShape));
  }

  @Test
  void types_without_a_ramp_keep_the_lookup_chain() {
    assertNull(StoredValueRamp.of(CvType.CV_8UC3));
    assertNull(StoredValueRamp.of(CvType.CV_32SC1));
    assertNull(StoredValueRamp.of(CvType.CV_32FC1));
    FORMATS.forEach(f -> assertNotNull(StoredValueRamp.of(f.cvType()), f.name()));
  }

  @Test
  void ramp_holds_every_stored_value_in_bit_pattern_order() {
    ImageCV ramp = StoredValueRamp.of(CvType.CV_16SC1);
    assertSame(ramp, StoredValueRamp.of(CvType.CV_16SC1));
    assertEquals(1 << 16, ramp.width());
    short[] values = new short[1 << 16];
    ramp.get(0, 0, values);
    assertEquals(0, values[0]);
    assertEquals(Short.MAX_VALUE, values[0x7FFF]);
    assertEquals(Short.MIN_VALUE, values[0x8000]);
    assertEquals(-1, values[0xFFFF]);
    assertEquals(1 << 8, StoredValueRamp.of(CvType.CV_8UC1).width());
  }

  private static void assertAllWindowsMatch(
      PlanarImage image, ImageDescriptor desc, Consumer<DicomImageReadParam> customizer) {
    var adapter = new DicomImageAdapter(image, desc, 0);
    List<String> differences = new ArrayList<>();
    for (double[] window : WINDOWS) {
      for (boolean inverse : new boolean[] {false, true}) {
        var params = params(null, window, inverse, true, 8);
        customizer.accept(params);
        compare(image, adapter, params).ifPresent(differences::add);
      }
    }
    image.release();
    assertTrue(
        differences.isEmpty(),
        () -> differences.size() + " difference(s), first: " + differences.get(0));
  }

  private static Optional<String> compare(
      PlanarImage image, DicomImageAdapter adapter, DicomImageReadParam params) {
    Mat expected =
        ImageRendering.applyLookupChain(image, adapter, new WindLevelParameters(adapter, params));
    Mat actual = ImageRendering.getVoiLutImage(image, adapter, params).toMat();
    try {
      if (expected.type() != actual.type() || !expected.size().equals(actual.size())) {
        return Optional.of(
            "type or size: expected %s, got %s"
                .formatted(
                    CvType.typeToString(expected.type()), CvType.typeToString(actual.type())));
      }
      Mat different = new Mat();
      Core.compare(expected, actual, different, Core.CMP_NE);
      int count = Core.countNonZero(different);
      different.release();
      return count == 0 ? Optional.empty() : Optional.of(count + " pixel(s) differ");
    } finally {
      if (actual != image.toMat()) {
        actual.release();
      }
      if (expected != image.toMat()) {
        expected.release();
      }
    }
  }

  // The equivalence is structural (types, ranges, table sizes): the window only changes the table
  // content, so the costly non-linear tables are built for a few windows only
  private static double[][] windowsOf(LutShape shape) {
    boolean linear = shape == LutShape.LINEAR || shape == LutShape.LINEAR_EXACT;
    return linear ? WINDOWS : new double[][] {WINDOWS[0], WINDOWS[3], WINDOWS[5]};
  }

  // Switching the padding off only changes something when the image declares one
  private static boolean[] paddingChoices(Attributes attributes) {
    return attributes.contains(Tag.PixelPaddingValue)
        ? new boolean[] {true, false}
        : new boolean[] {true};
  }

  private static DicomImageReadParam params(
      LutShape shape, double[] window, boolean inverse, boolean padding, int outputBits) {
    var params = new DicomImageReadParam();
    if (shape != null) {
      params.setVoiLutShape(shape);
    }
    if (window != null) {
      params.setWindowWidth(window[0]);
      params.setWindowCenter(window[1]);
    }
    params.setInverseLut(inverse);
    params.setApplyPixelPadding(padding);
    params.setOutputBits(outputBits);
    return params;
  }

  private static Attributes baseAttributes(PixelFormat format) {
    var attributes = new Attributes();
    attributes.setInt(Tag.BitsAllocated, VR.US, format.bitsAllocated());
    attributes.setInt(Tag.BitsStored, VR.US, format.bitsStored());
    attributes.setInt(Tag.PixelRepresentation, VR.US, format.signed() ? 1 : 0);
    photometric(attributes, PhotometricInterpretation.MONOCHROME2);
    return attributes;
  }

  private static void photometric(Attributes attributes, PhotometricInterpretation value) {
    attributes.setString(Tag.PhotometricInterpretation, VR.CS, value.toString());
  }

  private static void rescale(Attributes attributes, double slope, double intercept) {
    attributes.setDouble(Tag.RescaleSlope, VR.DS, slope);
    attributes.setDouble(Tag.RescaleIntercept, VR.DS, intercept);
  }

  // Every bit pattern of the type once, shuffled, including those above Bits Stored that a
  // conformant file would not hold: the two paths must agree on them too
  private static PlanarImage imageOfEveryValue(int cvType) {
    boolean eightBits = CvType.depth(cvType) <= CvType.CV_8S;
    return imageOf(cvType, 0, eightBits ? 0xFF : 0xFFFF);
  }

  // Cycles through [min, max], stored in the bit pattern of the type, then shuffles
  private static PlanarImage imageOf(int cvType, int min, int max) {
    int[] values = new int[WIDTH * HEIGHT];
    for (int i = 0; i < values.length; i++) {
      values[i] = min + i % (max - min + 1);
    }
    var random = new Random(cvType);
    for (int i = values.length - 1; i > 0; i--) {
      int j = random.nextInt(i + 1);
      int swap = values[i];
      values[i] = values[j];
      values[j] = swap;
    }
    var image = new ImageCV(HEIGHT, WIDTH, cvType);
    if (CvType.depth(cvType) <= CvType.CV_8S) {
      byte[] data = new byte[values.length];
      for (int i = 0; i < data.length; i++) {
        data[i] = (byte) values[i];
      }
      image.put(0, 0, data);
    } else {
      short[] data = new short[values.length];
      for (int i = 0; i < data.length; i++) {
        data[i] = (short) values[i];
      }
      image.put(0, 0, data);
    }
    return image;
  }
}
