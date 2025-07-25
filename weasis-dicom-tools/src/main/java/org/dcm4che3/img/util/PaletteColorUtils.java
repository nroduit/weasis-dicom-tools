/*
 * Copyright (c) 2025 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.dcm4che3.img.util;

import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.opencv.core.CvType;
import org.weasis.opencv.data.LookupTableCV;
import org.weasis.opencv.data.PlanarImage;
import org.weasis.opencv.op.ImageTransformer;

/**
 * Utility class for DICOM palette color operations and transformations.
 *
 * <p>This class provides comprehensive methods for handling DICOM palette color lookup tables,
 * including extraction from DICOM attributes and conversion of palette-based images to RGB format.
 * All methods handle the DICOM standard palette color model as defined in PS3.3.
 *
 * <p>The palette color model uses separate lookup tables for red, green, and blue color components,
 * each with their own descriptor and data attributes.
 *
 * @author Nicolas Roduit
 * @see <a
 *     href="https://dicom.nema.org/medical/dicom/current/output/chtml/part03/sect_C.7.6.3.html#sect_C.7.6.3.1.5">
 *     C.7.6.3.1.5 Palette Color Lookup Table Descriptor</a>
 */
public final class PaletteColorUtils {

  private PaletteColorUtils() {
    // Prevent instantiation
  }

  /**
   * Creates a palette color lookup table from DICOM attributes.
   *
   * <p>This method extracts the red, green, and blue palette color lookup table descriptors and
   * data from the DICOM attributes to create a combined RGB lookup table. The method validates that
   * all required palette components are present before creating the lookup table.
   *
   * @param ds the DICOM attributes containing palette color information
   * @return a LookupTableCV for palette color transformation, or null if invalid or missing data
   */
  public static LookupTableCV getPaletteColorLookupTable(Attributes ds) {
    if (!hasPaletteColorDescriptors(ds)) {
      return null;
    }

    var colorComponents = extractColorComponents(ds);
    return colorComponents.isValid() ? createPaletteLookupTable(colorComponents) : null;
  }

  /**
   * Converts a palette-based image to RGB using a lookup table.
   *
   * <p>This method applies palette color transformation to convert indexed color images to RGB
   * format. It uses optimized transformation paths when possible, falling back to general lookup
   * operations when needed.
   *
   * @param source the source image with palette color data
   * @param lookup the palette color lookup table
   * @return the RGB image, or the original image if conversion is not needed or possible
   */
  public static PlanarImage getRGBImageFromPaletteColorModel(
      PlanarImage source, LookupTableCV lookup) {
    if (lookup == null) {
      return source;
    }

    return canUseOptimizedTransform(source, lookup)
        ? performOptimizedTransform(source, lookup)
        : performGeneralLookup(source, lookup);
  }

  /**
   * Converts a palette-based image to RGB using DICOM attributes directly.
   *
   * <p><strong>Deprecated:</strong> This method is marked for removal in future versions. Use
   * {@link #getRGBImageFromPaletteColorModel(PlanarImage, LookupTableCV)} with {@link
   * #getPaletteColorLookupTable(Attributes)} instead.
   *
   * @param source the source image with palette color data
   * @param ds the DICOM attributes containing palette information
   * @return the RGB image, or the original image if conversion is not needed
   * @deprecated Use the two-step approach with separate lookup table creation for better
   *     performance
   */
  @Deprecated(since = "4.12", forRemoval = true)
  public static PlanarImage getRGBImageFromPaletteColorModel(PlanarImage source, Attributes ds) {
    if (ds == null) {
      return source;
    }

    var colorComponents = extractColorComponents(ds);
    if (!colorComponents.isValid()) {
      return source;
    }

    return canUseDirectTransform(source, colorComponents)
        ? performDirectTransform(source, colorComponents)
        : performLookupTransform(source, colorComponents);
  }

  // Private helper methods

  private static boolean hasPaletteColorDescriptors(Attributes ds) {
    return ds != null
        && ds.containsValue(Tag.RedPaletteColorLookupTableDescriptor)
        && ds.containsValue(Tag.GreenPaletteColorLookupTableDescriptor)
        && ds.containsValue(Tag.BluePaletteColorLookupTableDescriptor);
  }

  private static ColorComponents extractColorComponents(Attributes ds) {
    var redDesc = LookupTableUtils.lutDescriptor(ds, Tag.RedPaletteColorLookupTableDescriptor);
    var greenDesc = LookupTableUtils.lutDescriptor(ds, Tag.GreenPaletteColorLookupTableDescriptor);
    var blueDesc = LookupTableUtils.lutDescriptor(ds, Tag.BluePaletteColorLookupTableDescriptor);

    var redData =
        LookupTableUtils.lutData(
            ds,
            redDesc,
            Tag.RedPaletteColorLookupTableData,
            Tag.SegmentedRedPaletteColorLookupTableData);
    var greenData =
        LookupTableUtils.lutData(
            ds,
            greenDesc,
            Tag.GreenPaletteColorLookupTableData,
            Tag.SegmentedGreenPaletteColorLookupTableData);
    var blueData =
        LookupTableUtils.lutData(
            ds,
            blueDesc,
            Tag.BluePaletteColorLookupTableData,
            Tag.SegmentedBluePaletteColorLookupTableData);

    return new ColorComponents(redDesc, greenDesc, blueDesc, redData, greenData, blueData);
  }

  private static LookupTableCV createPaletteLookupTable(ColorComponents components) {
    return new LookupTableCV(
        new byte[][] {components.blueData(), components.greenData(), components.redData()},
        new int[] {components.blueDesc()[1], components.greenDesc()[1], components.redDesc()[1]},
        true);
  }

  private static boolean canUseOptimizedTransform(PlanarImage source, LookupTableCV lookup) {
    return source.depth() <= CvType.CV_8S
        && lookup.getOffset(0) == 0
        && lookup.getOffset(1) == 0
        && lookup.getOffset(2) == 0;
  }

  private static PlanarImage performOptimizedTransform(PlanarImage source, LookupTableCV lookup) {
    return ImageTransformer.applyLUT(source.toMat(), lookup.getByteData());
  }

  private static PlanarImage performGeneralLookup(PlanarImage source, LookupTableCV lookup) {
    return lookup.lookup(source.toMat());
  }

  private static boolean canUseDirectTransform(PlanarImage source, ColorComponents components) {
    return source.depth() <= CvType.CV_8S
        && components.redDesc()[1] == 0
        && components.greenDesc()[1] == 0
        && components.blueDesc()[1] == 0;
  }

  private static PlanarImage performDirectTransform(
      PlanarImage source, ColorComponents components) {
    return ImageTransformer.applyLUT(
        source.toMat(),
        new byte[][] {components.blueData(), components.greenData(), components.redData()});
  }

  private static PlanarImage performLookupTransform(
      PlanarImage source, ColorComponents components) {
    var lookup = createPaletteLookupTable(components);
    return lookup.lookup(source.toMat());
  }

  /** Helper record to organize color component data. */
  private record ColorComponents(
      int[] redDesc,
      int[] greenDesc,
      int[] blueDesc,
      byte[] redData,
      byte[] greenData,
      byte[] blueData) {
    boolean isValid() {
      return redData != null && greenData != null && blueData != null;
    }
  }
}
