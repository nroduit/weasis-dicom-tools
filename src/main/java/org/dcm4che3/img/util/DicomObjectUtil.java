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
import org.weasis.core.util.StringUtil;

/**
 * @author Nicolas Roduit
 */
public class DicomObjectUtil {
  private static final Logger LOGGER = LoggerFactory.getLogger(DicomObjectUtil.class);

  private DicomObjectUtil() {}

  public static List<Attributes> getSequence(Attributes dicom, int tagSeq) {
    if (dicom != null) {
      Sequence item = dicom.getSequence(tagSeq);
      if (item != null) {
        return item;
      }
    }
    return Collections.emptyList();
  }

  public static boolean isImageFrameApplicableToReferencedImageSequence(
      List<Attributes> refImgSeq,
      int childTag,
      String sopInstanceUID,
      int frame,
      boolean required) {
    if (!required && (refImgSeq == null || refImgSeq.isEmpty())) {
      return true;
    }
    if (StringUtil.hasText(sopInstanceUID)) {
      for (Attributes sopUID : refImgSeq) {
        if (sopInstanceUID.equals(sopUID.getString(Tag.ReferencedSOPInstanceUID))) {
          int[] frames = sopUID.getInts(childTag);
          if (frames == null || frames.length == 0) {
            return true;
          }
          for (int f : frames) {
            if (f == frame) {
              return true;
            }
          }
          // if the frame has been excluded
          return false;
        }
      }
    }
    return false;
  }

  public static <T, E extends Exception> SupplierEx<T, E> memoize(SupplierEx<T, E> original) {
    return new SupplierEx<>() {
      SupplierEx<T, E> delegate = this::firstTime;
      boolean initialized;

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

  public static LocalDate getDicomDate(String date) {
    if (StringUtil.hasText(date)) {
      try {
        return DateTimeUtils.parseDA(date);
      } catch (Exception e) {
        LOGGER.error("Parse DICOM date", e); // $NON-NLS-1$
      }
    }
    return null;
  }

  public static LocalTime getDicomTime(String time) {
    if (StringUtil.hasText(time)) {
      try {
        return DateTimeUtils.parseTM(time);
      } catch (Exception e1) {
        LOGGER.error("Parse DICOM time", e1); // $NON-NLS-1$
      }
    }
    return null;
  }

  public static LocalDateTime dateTime(Attributes dcm, int dateID, int timeID) {
    if (dcm == null) {
      return null;
    }
    LocalDate date = getDicomDate(dcm.getString(dateID));
    if (date == null) {
      return null;
    }
    LocalTime time = getDicomTime(dcm.getString(timeID));
    if (time == null) {
      return date.atStartOfDay();
    }
    return LocalDateTime.of(date, time);
  }

  /**
   * Build the shape from DICOM Shutter
   *
   * @see <a
   *     href="http://dicom.nema.org/MEDICAL/DICOM/current/output/chtml/part03/sect_C.7.6.11.html">C.7.6.11
   *     Display Shutter Module</a>
   * @param dcm
   */
  public static Area getShutterShape(Attributes dcm) {
    Area shape = null;
    String shutterShape = DicomUtils.getStringFromDicomElement(dcm, Tag.ShutterShape);
    if (shutterShape != null) {
      if (shutterShape.contains("RECTANGULAR")
          || shutterShape.contains("RECTANGLE")) { // $NON-NLS-1$ //$NON-NLS-2$
        Rectangle2D rect = new Rectangle2D.Double();
        rect.setFrameFromDiagonal(
            dcm.getInt(Tag.ShutterLeftVerticalEdge, 0),
            dcm.getInt(Tag.ShutterUpperHorizontalEdge, 0),
            dcm.getInt(Tag.ShutterRightVerticalEdge, 0),
            dcm.getInt(Tag.ShutterLowerHorizontalEdge, 0));
        if (rect.isEmpty()) {
          LOGGER.error("Shutter rectangle has an empty area!");
        } else {
          shape = new Area(rect);
        }
      }
      if (shutterShape.contains("CIRCULAR")) { // $NON-NLS-1$
        int[] centerOfCircularShutter = dcm.getInts(Tag.CenterOfCircularShutter);
        if (centerOfCircularShutter != null && centerOfCircularShutter.length >= 2) {
          Ellipse2D ellipse = new Ellipse2D.Double();
          double radius = dcm.getInt(Tag.RadiusOfCircularShutter, 0);
          // Thanks DICOM for reversing x,y by row,column
          ellipse.setFrameFromCenter(
              centerOfCircularShutter[1],
              centerOfCircularShutter[0],
              centerOfCircularShutter[1] + radius,
              centerOfCircularShutter[0] + radius);
          if (ellipse.isEmpty()) {
            LOGGER.error("Shutter ellipse has an empty area!");
          } else {
            if (shape == null) {
              shape = new Area(ellipse);
            } else {
              shape.intersect(new Area(ellipse));
            }
          }
        }
      }
      if (shutterShape.contains("POLYGONAL")) { // $NON-NLS-1$
        int[] points = dcm.getInts(Tag.VerticesOfThePolygonalShutter);
        if (points != null) {
          Polygon polygon = new Polygon();
          for (int i = 0; i < points.length / 2; i++) {
            // Thanks DICOM for reversing x,y by row,column
            polygon.addPoint(points[i * 2 + 1], points[i * 2]);
          }
          if (isPolygonValid(polygon)) {
            if (shape == null) {
              shape = new Area(polygon);
            } else {
              shape.intersect(new Area(polygon));
            }
          } else {
            LOGGER.error("Shutter rectangle has an empty area!");
          }
        }
      }
    }
    return shape;
  }

  private static boolean isPolygonValid(Polygon polygon) {
    int[] xPoints = polygon.xpoints;
    int[] yPoints = polygon.ypoints;
    double area = 0;
    for (int i = 0; i < polygon.npoints; i++) {
      area += (xPoints[i] * yPoints[i + 1]) - (xPoints[i + 1] * yPoints[i]);
      if (area > 0) {
        return true;
      }
    }
    return false;
  }

  /**
   * Extract the shutter color from ShutterPresentationColorCIELabValue or ShutterPresentationValue
   *
   * @param dcm the DICOM attributes
   * @return
   */
  public static Color getShutterColor(Attributes dcm) {
    int[] rgb = CIELab.dicomLab2rgb(dcm.getInts(Tag.ShutterPresentationColorCIELabValue));
    return getRGBColor(dcm.getInt(Tag.ShutterPresentationValue, 0x0000), rgb);
  }

  /**
   * Get RGB color form rgbColour array or pGray value
   *
   * @param pGray A single gray unsigned value when rendered on a monochrome display. The units are
   *     specified in P-Values, from a minimum of 0x0000 (black) up to a maximum of 0xFFFF (white).
   * @param rgbColour
   * @return the color
   */
  public static Color getRGBColor(int pGray, int[] rgbColour) {
    int r, g, b;
    if (rgbColour != null && rgbColour.length >= 3) {
      r = Math.min(rgbColour[0], 255);
      g = Math.min(rgbColour[1], 255);
      b = Math.min(rgbColour[2], 255);
    } else {
      r = g = b = pGray >> 8;
    }
    r &= 0xFF;
    g &= 0xFF;
    b &= 0xFF;
    int conv = (r << 16) | (g << 8) | b | 0x1000000;
    return new Color(conv);
  }
}
