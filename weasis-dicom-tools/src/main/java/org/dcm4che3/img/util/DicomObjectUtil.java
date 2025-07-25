/*
 * Copyright (c) 2021 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.dcm4che3.img.util;

import java.awt.Color;
import java.awt.Polygon;
import java.awt.geom.Area;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Rectangle2D;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Collections;
import java.util.List;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Sequence;
import org.dcm4che3.data.Tag;
import org.dcm4che3.img.data.CIELab;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.util.MathUtil;
import org.weasis.core.util.StringUtil;

/**
 * Utility class for DICOM object manipulation and data extraction.
 *
 * <p>This class provides static methods for common DICOM operations including:
 *
 * <ul>
 *   <li>Sequence extraction and validation
 *   <li>Date and time parsing with DICOM format support
 *   <li>Display shutter shape creation and color extraction
 *   <li>Memoization utilities for expensive operations
 *   <li>Color conversion from DICOM formats to AWT colors
 * </ul>
 *
 * @author Nicolas Roduit
 */
public class DicomObjectUtil {
  private static final Logger LOGGER = LoggerFactory.getLogger(DicomObjectUtil.class);

  private DicomObjectUtil() {}

  /**
   * Extracts a DICOM sequence from the specified attributes.
   *
   * @param dicom the DICOM attributes to search, may be {@code null}
   * @param tagSeq the sequence tag identifier
   * @return the sequence as a list of attributes, or an empty list if not found or null input
   */
  public static List<Attributes> getSequence(Attributes dicom, int tagSeq) {
    if (dicom != null) {
      Sequence item = dicom.getSequence(tagSeq);
      if (item != null) {
        return item;
      }
    }
    return Collections.emptyList();
  }

  /**
   * Determines if a specific image frame is applicable to a referenced image sequence.
   *
   * <p>This method validates frame applicability by checking SOP Instance UID matching and frame
   * number presence in the referenced frames list. When no frames are specified in the reference,
   * all frames are considered applicable.
   *
   * @param refImgSeq the referenced image sequence to check against
   * @param childTag the tag containing frame numbers (e.g., Tag.ReferencedFrameNumber)
   * @param sopInstanceUID the SOP Instance UID to match
   * @param frame the frame number to validate (1-based)
   * @param required whether the sequence is required (affects empty sequence handling)
   * @return {@code true} if the frame is applicable, {@code false} otherwise
   */
  public static boolean isImageFrameApplicableToReferencedImageSequence(
      List<Attributes> refImgSeq,
      int childTag,
      String sopInstanceUID,
      int frame,
      boolean required) {
    if (!required && (refImgSeq == null || refImgSeq.isEmpty())) {
      return true;
    }
    if (!StringUtil.hasText(sopInstanceUID)) {
      return false;
    }

    return refImgSeq.stream()
        .filter(sopUID -> sopInstanceUID.equals(sopUID.getString(Tag.ReferencedSOPInstanceUID)))
        .findFirst()
        .map(sopUID -> isFrameInReferencedFrames(sopUID, childTag, frame))
        .orElse(false);
  }

  private static boolean isFrameInReferencedFrames(Attributes sopUID, int childTag, int frame) {
    int[] frames = sopUID.getInts(childTag);
    if (frames == null || frames.length == 0) {
      return true; // No specific frames means all frames are applicable
    }
    for (int f : frames) {
      if (f == frame) {
        return true;
      }
    }
    return false; // Frame explicitly excluded
  }

  /**
   * Creates a memoized version of the given supplier that caches the result after first invocation.
   *
   * <p>This is thread-safe and ensures the original supplier is called only once, even under
   * concurrent access. Subsequent calls return the cached value without re-evaluation.
   *
   * @param <T> the type of results supplied by this supplier
   * @param <E> the type of exception that may be thrown
   * @param original the supplier to memoize
   * @return a memoized supplier that caches the result of the first call
   */
  public static <T, E extends Exception> SupplierEx<T, E> memoize(SupplierEx<T, E> original) {
    return new SupplierEx<>() {
      SupplierEx<T, E> delegate = this::firstTime;
      boolean initialized;

      @Override
      public T get() throws E {
        return delegate.get();
      }

      private synchronized T firstTime() throws E {
        if (!initialized) {
          T value = original.get();
          delegate = () -> value;
          initialized = true;
        }
        return delegate.get();
      }
    };
  }

  /**
   * Parses a DICOM date string (DA format) to LocalDate.
   *
   * @param date the DICOM date string in YYYYMMDD format, may be {@code null} or empty
   * @return the parsed LocalDate, or {@code null} if parsing fails or input is invalid
   */
  public static LocalDate getDicomDate(String date) {
    if (StringUtil.hasText(date)) {
      try {
        return DateTimeUtils.parseDA(date);
      } catch (Exception e) {
        LOGGER.error("Parse DICOM date", e);
      }
    }
    return null;
  }

  /**
   * Parses a DICOM time string (TM format) to LocalTime.
   *
   * @param time the DICOM time string in HHMMSS.FFFFFF format, may be {@code null} or empty
   * @return the parsed LocalTime, or {@code null} if parsing fails or input is invalid
   */
  public static LocalTime getDicomTime(String time) {
    if (StringUtil.hasText(time)) {
      try {
        return DateTimeUtils.parseTM(time);
      } catch (Exception e1) {
        LOGGER.error("Parse DICOM time", e1);
      }
    }
    return null;
  }

  /**
   * Combines DICOM date and time attributes into a LocalDateTime.
   *
   * <p>If only date is available, returns the date at start of day (00:00:00). If date is missing,
   * returns {@code null}.
   *
   * @param dcm the DICOM attributes containing date and time information
   * @param dateID the tag identifier for the date attribute
   * @param timeID the tag identifier for the time attribute
   * @return the combined LocalDateTime, or {@code null} if date is not available
   */
  public static LocalDateTime dateTime(Attributes dcm, int dateID, int timeID) {
    if (dcm == null) {
      return null;
    }
    LocalDate date = getDicomDate(dcm.getString(dateID));
    if (date == null) {
      return null;
    }
    LocalTime time = getDicomTime(dcm.getString(timeID));
    return time == null ? date.atStartOfDay() : LocalDateTime.of(date, time);
  }

  /**
   * Creates a display shutter shape from DICOM Display Shutter Module attributes.
   *
   * <p>Supports rectangular, circular, and polygonal shutter shapes as defined in DICOM standard.
   * Multiple shapes are combined using intersection to create the final shutter area.
   *
   * @param dcm the DICOM attributes containing shutter information
   * @return the combined shutter shape as an Area, or {@code null} if no valid shutter found
   * @see <a
   *     href="http://dicom.nema.org/MEDICAL/DICOM/current/output/chtml/part03/sect_C.7.6.11.html">
   *     C.7.6.11 Display Shutter Module</a>
   */
  public static Area getShutterShape(Attributes dcm) {
    String shutterShape = DicomUtils.getStringFromDicomElement(dcm, Tag.ShutterShape);
    if (shutterShape == null) {
      return null;
    }
    Area shape = null;
    if (shutterShape.contains("RECTANGULAR") || shutterShape.contains("RECTANGLE")) {
      shape = createRectangularShutter(dcm);
    }

    if (shutterShape.contains("CIRCULAR")) {
      Area circularShutter = createCircularShutter(dcm);
      shape = combineShutterAreas(shape, circularShutter);
    }

    if (shutterShape.contains("POLYGONAL")) {
      Area polygonalShutter = createPolygonalShutter(dcm);
      shape = combineShutterAreas(shape, polygonalShutter);
    }

    return shape;
  }

  private static Area createRectangularShutter(Attributes dcm) {
    Rectangle2D rect = new Rectangle2D.Double();
    rect.setFrameFromDiagonal(
        dcm.getInt(Tag.ShutterLeftVerticalEdge, 0),
        dcm.getInt(Tag.ShutterUpperHorizontalEdge, 0),
        dcm.getInt(Tag.ShutterRightVerticalEdge, 0),
        dcm.getInt(Tag.ShutterLowerHorizontalEdge, 0));
    if (rect.isEmpty()) {
      LOGGER.error("Shutter rectangle has an empty area!");
      return null;
    }
    return new Area(rect);
  }

  private static Area createCircularShutter(Attributes dcm) {
    int[] centerOfCircularShutter = dcm.getInts(Tag.CenterOfCircularShutter);
    if (centerOfCircularShutter == null || centerOfCircularShutter.length < 2) {
      return null;
    }
    Ellipse2D ellipse = new Ellipse2D.Double();
    double radius = dcm.getInt(Tag.RadiusOfCircularShutter, 0);
    // DICOM uses row,column ordering instead of x,y
    ellipse.setFrameFromCenter(
        centerOfCircularShutter[1],
        centerOfCircularShutter[0],
        centerOfCircularShutter[1] + radius,
        centerOfCircularShutter[0] + radius);
    if (ellipse.isEmpty()) {
      LOGGER.error("Shutter ellipse has an empty area!");
      return null;
    }
    return new Area(ellipse);
  }

  private static Area createPolygonalShutter(Attributes dcm) {
    int[] points = dcm.getInts(Tag.VerticesOfThePolygonalShutter);
    if (points == null || points.length < 6) {
      return null;
    }
    Polygon polygon = new Polygon();
    for (int i = 0; i < points.length / 2; i++) {
      // DICOM uses row,column ordering instead of x,y
      polygon.addPoint(points[i * 2 + 1], points[i * 2]);
    }
    if (!isPolygonValid(polygon)) {
      LOGGER.error("Shutter polygon has an empty area!");
      return null;
    }
    return new Area(polygon);
  }

  private static Area combineShutterAreas(Area existing, Area newArea) {
    if (newArea == null) {
      return existing;
    }
    if (existing == null) {
      return newArea;
    }
    existing.intersect(newArea);
    return existing;
  }

  private static boolean isPolygonValid(Polygon polygon) {
    if (polygon.npoints <= 2) {
      return false;
    }
    int[] xPoints = polygon.xpoints;
    int[] yPoints = polygon.ypoints;
    double area = 0;
    for (int i = 0; i < polygon.npoints; i++) {
      int nextIndex = (i + 1) % polygon.npoints;
      area += (xPoints[i] * yPoints[nextIndex]) - (xPoints[nextIndex] * yPoints[i]);
    }
    return area != 0; // Non-zero area indicates a valid polygon, not a line
  }

  /**
   * Extracts the display shutter color from DICOM presentation attributes.
   *
   * <p>Prioritizes CIE Lab color values over grayscale presentation values. Falls back to grayscale
   * if Lab values are not available.
   *
   * @param dcm the DICOM attributes containing shutter color information
   * @return the shutter color, defaults to black if no color information found
   */
  public static Color getShutterColor(Attributes dcm) {
    int[] rgb = CIELab.dicomLab2rgb(dcm.getInts(Tag.ShutterPresentationColorCIELabValue));
    return getRGBColor(dcm.getInt(Tag.ShutterPresentationValue, 0x0000), rgb);
  }

  /**
   * Converts DICOM color representations to AWT Color objects.
   *
   * <p>Supports both RGB/RGBA arrays and grayscale P-Values. RGB values are clamped to valid ranges
   * (0-255). P-Values are converted from 16-bit to 8-bit grayscale.
   *
   * @param pGray grayscale P-Value (0x0000=black to 0xFFFF=white) used when RGB is unavailable
   * @param rgbaColour RGB or RGBA color array, values should be 0-255 (alpha optional)
   * @return the converted Color object with proper RGBA components
   */
  public static Color getRGBColor(int pGray, int[] rgbaColour) {
    int r, g, b, a = 255;
    if (rgbaColour != null && rgbaColour.length >= 3) {
      r = MathUtil.clamp(rgbaColour[0], 0, 255);
      g = MathUtil.clamp(rgbaColour[1], 0, 255);
      b = MathUtil.clamp(rgbaColour[2], 0, 255);
      if (rgbaColour.length > 3) {
        a = MathUtil.clamp(rgbaColour[3], 0, 255);
      }
    } else {
      // Convert 16-bit P-Value to 8-bit grayscale
      r = g = b = MathUtil.clamp(pGray >> 8, 0, 255);
    }
    return new Color(r, g, b, a);
  }
}
