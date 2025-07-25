/*
 * Copyright (c) 2021 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.dcm4che3.img.op;

import java.awt.Color;
import java.awt.Rectangle;
import java.awt.Shape;
import java.util.List;
import java.util.Objects;
import org.opencv.core.Mat;
import org.opencv.core.MatOfPoint;
import org.opencv.core.Rect;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.opencv.data.ImageCV;
import org.weasis.opencv.op.ImageAnalyzer;

/**
 * Represents a mask area that can be applied to images for masking operations. Supports both color
 * filling and blur effects depending on the configuration.
 *
 * <p>When no color is specified, rectangular shapes are blurred instead of filled. When a color is
 * specified, all shapes are filled with that color.
 *
 * @author Nicolas Roduit
 */
public class MaskArea {
  private static final Logger LOGGER = LoggerFactory.getLogger(MaskArea.class);

  /** Default blur kernel size for masking without color */
  private static final Size DEFAULT_BLUR_SIZE = new Size(7, 7);

  /** Minimum dimension threshold for applying blur effects */
  private static final int MIN_BLUR_DIMENSION = 3;

  /** Default mask color (black) when color is null */
  private static final Scalar DEFAULT_MASK_COLOR = new Scalar(0, 0, 0);

  private final Color color;
  private final List<Shape> shapeList;

  /**
   * Creates a mask area with the specified shapes and no color (blur effect).
   *
   * @param shapeList the list of shapes to mask
   * @throws NullPointerException if shapeList is null
   */
  public MaskArea(List<Shape> shapeList) {
    this(shapeList, null);
  }

  /**
   * Creates a mask area with the specified shapes and color.
   *
   * @param shapeList the list of shapes to mask
   * @param color the color to fill shapes with, or null for blur effect
   * @throws NullPointerException if shapeList is null
   */
  public MaskArea(List<Shape> shapeList, Color color) {
    this.shapeList = Objects.requireNonNull(shapeList, "Shape list cannot be null");
    this.color = color;
  }

  /** Returns the mask color, or null if blur effect should be used. */
  public Color getColor() {
    return color;
  }

  /** Returns the list of shapes to be masked. */
  public List<Shape> getShapeList() {
    return shapeList;
  }

  /**
   * Applies the mask area to the source image and returns the result.
   *
   * <p>If the mask area is null or empty, returns a copy of the source image. When color is
   * specified, shapes are filled with that color. When color is null, rectangular shapes are
   * blurred.
   *
   * @param srcImg the source image to apply the mask to
   * @param maskArea the mask area configuration, or null for no masking
   * @return the masked image
   */
  public static ImageCV drawShape(Mat srcImg, MaskArea maskArea) {
    if (maskArea == null || maskArea.getShapeList().isEmpty()) {
      return ImageCV.fromMat(srcImg);
    }

    ImageCV dstImg = createDestinationImage(srcImg);
    applyMaskShapes(srcImg, dstImg, maskArea);
    return dstImg;
  }

  private static ImageCV createDestinationImage(Mat srcImg) {
    ImageCV dstImg = new ImageCV();
    srcImg.copyTo(dstImg);
    return dstImg;
  }

  private static void applyMaskShapes(Mat srcImg, ImageCV dstImg, MaskArea maskArea) {
    Color maskColor = maskArea.getColor();
    Scalar scalarColor = convertToScalar(maskColor);
    for (Shape shape : maskArea.getShapeList()) {
      if (shouldApplyBlur(maskColor, shape)) {
        applyBlurToRectangle(srcImg, dstImg, (Rectangle) shape);
      } else {
        List<MatOfPoint> contours = ImageAnalyzer.transformShapeToContour(shape, true);
        Imgproc.fillPoly(dstImg, contours, scalarColor);
      }
    }
  }

  private static Scalar convertToScalar(Color color) {
    return color == null
        ? DEFAULT_MASK_COLOR
        : new Scalar(color.getBlue(), color.getGreen(), color.getRed());
  }

  private static boolean shouldApplyBlur(Color color, Shape shape) {
    return color == null && shape instanceof Rectangle;
  }

  private static void applyBlurToRectangle(Mat srcImg, ImageCV dstImg, Rectangle rect) {
    Rectangle clippedRect = rect.intersection(new Rectangle(0, 0, srcImg.width(), srcImg.height()));

    if (isRectangleTooSmall(clippedRect)) {
      LOGGER.warn("The masking shape is not applicable: {}", clippedRect);
      return;
    }
    Rect cvRect = new Rect(clippedRect.x, clippedRect.y, clippedRect.width, clippedRect.height);
    Imgproc.blur(srcImg.submat(cvRect), dstImg.submat(cvRect), DEFAULT_BLUR_SIZE);
  }

  private static boolean isRectangleTooSmall(Rectangle rect) {
    return rect.width < MIN_BLUR_DIMENSION || rect.height < MIN_BLUR_DIMENSION;
  }
}
