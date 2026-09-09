/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.dcm4che3.img.data;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Sequence;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.UID;
import org.dcm4che3.data.VR;
import org.dcm4che3.img.data.PrDicomObject.BlendingLayer;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;

@DisplayNameGeneration(ReplaceUnderscores.class)
class PrDicomObjectBlendingTest {

  private static Attributes layer(String position, String seriesUid, double center, double width) {
    Attributes item = new Attributes();
    item.setString(Tag.BlendingPosition, VR.CS, position);
    item.setString(Tag.StudyInstanceUID, VR.UI, "1.2.3");
    Sequence series = item.newSequence(Tag.ReferencedSeriesSequence, 1);
    Attributes ref = new Attributes();
    ref.setString(Tag.SeriesInstanceUID, VR.UI, seriesUid);
    series.add(ref);
    Sequence voi = item.newSequence(Tag.SoftcopyVOILUTSequence, 1);
    Attributes window = new Attributes();
    window.setDouble(Tag.WindowCenter, VR.DS, center);
    window.setDouble(Tag.WindowWidth, VR.DS, width);
    voi.add(window);
    return item;
  }

  private static Attributes blendingState() {
    Attributes ds = new Attributes();
    ds.setString(Tag.SOPClassUID, VR.UI, UID.BlendingSoftcopyPresentationStateStorage);
    ds.setString(Tag.SOPInstanceUID, VR.UI, "1.2.3.4");
    ds.setString(Tag.ContentLabel, VR.CS, "PET_CT");
    ds.setDouble(Tag.RelativeOpacity, VR.FL, 0.4);
    ds.setString(Tag.PaletteColorLookupTableUID, VR.UI, "1.2.840.10008.1.5.2");
    Sequence blending = ds.newSequence(Tag.BlendingSequence, 2);
    blending.add(layer(BlendingLayer.UNDERLYING, "1.2.3.10", 40, 400));
    blending.add(layer(BlendingLayer.SUPERIMPOSED, "1.2.3.20", 5000, 10000));
    int[] descriptor = {256, 0, 8};
    ds.setInt(Tag.RedPaletteColorLookupTableDescriptor, VR.US, descriptor);
    ds.setInt(Tag.GreenPaletteColorLookupTableDescriptor, VR.US, descriptor);
    ds.setInt(Tag.BluePaletteColorLookupTableDescriptor, VR.US, descriptor);
    byte[] ramp = new byte[256];
    for (int i = 0; i < 256; i++) {
      ramp[i] = (byte) i;
    }
    ds.setBytes(Tag.RedPaletteColorLookupTableData, VR.OW, ramp);
    ds.setBytes(Tag.GreenPaletteColorLookupTableData, VR.OW, new byte[256]);
    ds.setBytes(Tag.BluePaletteColorLookupTableData, VR.OW, new byte[256]);
    return ds;
  }

  @Test
  void blending_layers_opacity_and_palette_are_exposed() {
    PrDicomObject pr = new PrDicomObject(blendingState());
    BlendingLayer under = pr.getUnderlyingLayer().orElseThrow();
    BlendingLayer over = pr.getSuperimposedLayer().orElseThrow();
    assertAll(
        () -> assertEquals(2, pr.getBlendingLayers().size()),
        () -> assertTrue(under.references("1.2.3.10")),
        () -> assertTrue(over.references("1.2.3.20")),
        () -> assertEquals(5000.0, over.voiLut().getWindowCenter().get(0)),
        () -> assertEquals(10000.0, over.voiLut().getWindowWidth().get(0)),
        () -> assertEquals(0.4, pr.getRelativeOpacity().orElseThrow(), 1e-6),
        () -> assertEquals("1.2.840.10008.1.5.2", pr.getPaletteColorLutUid().orElseThrow()),
        () -> assertTrue(pr.getVoiLUT().isPresent(), "the underlying window is the view's VOI"),
        () -> assertEquals(40.0, pr.getVoiLUT().orElseThrow().getWindowCenter().get(0)),
        () -> assertTrue(pr.getPaletteColorLut().isPresent()),
        () -> assertEquals(1, pr.getReferencedSeriesSequence().size(), "only the underlying layer"),
        () ->
            assertEquals(
                "1.2.3.10",
                pr.getReferencedSeriesSequence().get(0).getString(Tag.SeriesInstanceUID)),
        () ->
            assertTrue(
                pr.getBlendingLayers().stream().noneMatch(l -> l.studyInstanceUid() == null)));
  }

  @Test
  void other_states_have_no_layers() {
    Attributes ds = new Attributes();
    ds.setString(Tag.SOPClassUID, VR.UI, UID.GrayscaleSoftcopyPresentationStateStorage);
    PrDicomObject pr = new PrDicomObject(ds);
    assertAll(
        () -> assertTrue(pr.getBlendingLayers().isEmpty()),
        () -> assertTrue(pr.getRelativeOpacity().isEmpty()),
        () -> assertTrue(pr.getSuperimposedLayer().isEmpty()));
  }
}
