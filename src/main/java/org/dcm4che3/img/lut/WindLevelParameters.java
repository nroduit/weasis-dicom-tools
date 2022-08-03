/*
 * Copyright (c) 2021 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.dcm4che3.img.lut;

import java.util.Objects;
import org.dcm4che3.img.DicomImageAdapter;
import org.dcm4che3.img.DicomImageReadParam;
import org.dcm4che3.img.data.PrDicomObject;
import org.weasis.opencv.op.lut.DefaultWlPresentation;
import org.weasis.opencv.op.lut.LutShape;
import org.weasis.opencv.op.lut.WlParams;

/**
 * @author Nicolas Roduit
 */
public class WindLevelParameters implements WlParams {

  private final double window;
  private final double level;
  private final double levelMin;
  private final double levelMax;
  private final boolean pixelPadding;
  private final boolean inverseLut;
  private final boolean fillOutsideLutRange;
  private final boolean allowWinLevelOnColorImage;
  private final LutShape lutShape;
  private final PrDicomObject dcmPR;

  public WindLevelParameters(DicomImageAdapter adapter) {
    this(adapter, null);
  }

  public WindLevelParameters(DicomImageAdapter adapter, DicomImageReadParam params) {
    Objects.requireNonNull(adapter);
    if (params == null) {
      this.dcmPR = null;
      this.fillOutsideLutRange = false;
      this.allowWinLevelOnColorImage = false;
      this.pixelPadding = true;
      this.inverseLut = false;
      DefaultWlPresentation def = new DefaultWlPresentation(dcmPR, pixelPadding);
      this.window = adapter.getDefaultWindow(def);
      this.level = adapter.getDefaultLevel(def);
      this.lutShape = adapter.getDefaultShape(def);

      this.levelMin = Math.min(level - window / 2.0, adapter.getMinValue(def));
      this.levelMax = Math.max(level + window / 2.0, adapter.getMaxValue(def));
    } else {
      this.dcmPR = params.getPresentationState().orElse(null);
      this.fillOutsideLutRange = params.getFillOutsideLutRange().orElse(false);
      this.allowWinLevelOnColorImage = params.getApplyWindowLevelToColorImage().orElse(false);
      this.pixelPadding = params.getApplyPixelPadding().orElse(true);
      this.inverseLut = params.getInverseLut().orElse(false);
      DefaultWlPresentation def = new DefaultWlPresentation(dcmPR, pixelPadding);
      this.window = params.getWindowWidth().orElseGet(() -> adapter.getDefaultWindow(def));
      this.level = params.getWindowCenter().orElseGet(() -> adapter.getDefaultLevel(def));
      this.lutShape = params.getVoiLutShape().orElseGet(() -> adapter.getDefaultShape(def));
      this.levelMin =
          Math.min(
              params.getLevelMin().orElseGet(() -> level - window / 2.0), adapter.getMinValue(def));
      this.levelMax =
          Math.max(
              params.getLevelMax().orElseGet(() -> level + window / 2.0), adapter.getMaxValue(def));
    }
  }

  @Override
  public double getWindow() {
    return window;
  }

  @Override
  public double getLevel() {
    return level;
  }

  @Override
  public double getLevelMin() {
    return levelMin;
  }

  @Override
  public double getLevelMax() {
    return levelMax;
  }

  @Override
  public boolean isPixelPadding() {
    return pixelPadding;
  }

  @Override
  public boolean isInverseLut() {
    return inverseLut;
  }

  @Override
  public boolean isFillOutsideLutRange() {
    return fillOutsideLutRange;
  }

  @Override
  public boolean isAllowWinLevelOnColorImage() {
    return allowWinLevelOnColorImage;
  }

  @Override
  public LutShape getLutShape() {
    return lutShape;
  }

  @Override
  public PrDicomObject getPresentationState() {
    return dcmPR;
  }
}
