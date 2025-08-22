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

import java.util.Arrays;
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

    return hasZeroOffsets(source, lookup)
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

    return hasZeroOffsets(colorComponents)
        ? performDirectTransform(source, colorComponents)
        : performLookupTransform(source, colorComponents);
  }

  // Check if DICOM attributes contain all required palette descriptors
  private static boolean hasPaletteColorDescriptors(Attributes ds) {
    return ds != null
        && ds.containsValue(Tag.RedPaletteColorLookupTableDescriptor)
        && ds.containsValue(Tag.GreenPaletteColorLookupTableDescriptor)
        && ds.containsValue(Tag.BluePaletteColorLookupTableDescriptor);
  }

  // Extract palette color components from DICOM attributes
  private static ColorComponents extractColorComponents(Attributes ds) {
    var descriptors =
        new int[][] {
          LookupTableUtils.lutDescriptor(ds, Tag.RedPaletteColorLookupTableDescriptor),
          LookupTableUtils.lutDescriptor(ds, Tag.GreenPaletteColorLookupTableDescriptor),
          LookupTableUtils.lutDescriptor(ds, Tag.BluePaletteColorLookupTableDescriptor)
        };

    var data =
        new byte[][] {
          LookupTableUtils.lutData(
              ds,
              descriptors[0],
              Tag.RedPaletteColorLookupTableData,
              Tag.SegmentedRedPaletteColorLookupTableData),
          LookupTableUtils.lutData(
              ds,
              descriptors[1],
              Tag.GreenPaletteColorLookupTableData,
              Tag.SegmentedGreenPaletteColorLookupTableData),
          LookupTableUtils.lutData(
              ds,
              descriptors[2],
              Tag.BluePaletteColorLookupTableData,
              Tag.SegmentedBluePaletteColorLookupTableData)
        };

    return new ColorComponents(
        descriptors[0], descriptors[1], descriptors[2], data[0], data[1], data[2]);
  }

  // Create LookupTableCV from color components
  private static LookupTableCV createPaletteLookupTable(ColorComponents components) {
    return new LookupTableCV(
        new byte[][] {components.blueData(), components.greenData(), components.redData()},
        new int[] {components.blueDesc()[1], components.greenDesc()[1], components.redDesc()[1]},
        true);
  }

  // Check if lookup table has zero offsets for optimization
  private static boolean hasZeroOffsets(PlanarImage source, LookupTableCV lookup) {
    return source.depth() <= CvType.CV_8S
        && lookup.getOffset(0) == 0
        && lookup.getOffset(1) == 0
        && lookup.getOffset(2) == 0;
  }

  // Check if color components have zero offsets for optimization
  private static boolean hasZeroOffsets(ColorComponents components) {
    return components.redDesc()[1] == 0
        && components.greenDesc()[1] == 0
        && components.blueDesc()[1] == 0;
  }

  // Apply optimized LUT transformation for 8-bit data with zero offsets
  private static PlanarImage performOptimizedTransform(PlanarImage source, LookupTableCV lookup) {
    return ImageTransformer.applyLUT(source.toMat(), lookup.getByteData());
  }

  // Apply general LUT lookup operation
  private static PlanarImage performGeneralLookup(PlanarImage source, LookupTableCV lookup) {
    return lookup.lookup(source.toMat());
  }

  // Apply direct LUT transformation using color components data
  private static PlanarImage performDirectTransform(
      PlanarImage source, ColorComponents components) {
    return ImageTransformer.applyLUT(
        source.toMat(),
        new byte[][] {components.blueData(), components.greenData(), components.redData()});
  }

  // Apply LUT transformation by creating lookup table first
  private static PlanarImage performLookupTransform(
      PlanarImage source, ColorComponents components) {
    var lookup = createPaletteLookupTable(components);
    return lookup.lookup(source.toMat());
  }

  /** Helper record to organize color component data for palette operations. */
  private record ColorComponents(
      int[] redDesc,
      int[] greenDesc,
      int[] blueDesc,
      byte[] redData,
      byte[] greenData,
      byte[] blueData) {
    // Check if all color component data is present and valid
    boolean isValid() {
      return Arrays.stream(new byte[][] {redData, greenData, blueData})
          .allMatch(data -> data != null && data.length > 0);
    }
  }
}
