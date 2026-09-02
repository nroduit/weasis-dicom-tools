/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.ref;

import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;

/**
 * Coarse area imaged, used as a grouper of related images: the <i>Region imaged</i> values of the
 * LOINC/RSNA Radiology Playbook (DICOM CID 102). A region spanning several areas belongs to each of
 * them; a region belonging to none is the Playbook's <i>unspecified</i> (e.g. a vessel).
 *
 * <p>Each group is identified by the SNOMED CT concept that PS3.16 Table L-1 gives to the same
 * area, so it can be written as an Anatomic Region Sequence item or a FHIR {@code bodySite}. {@link
 * #SPINE} is a Weasis extension: the Playbook has no spine region and assigns spine segments to the
 * trunk regions they lie in.
 *
 * @see RegionGroups
 */
public enum RegionGroup {
  HEAD("69536005", true),
  NECK("45048000", true),
  CHEST("816094009", true),
  BREAST("76752008", true),
  ABDOMEN("818981001", true),
  PELVIS("816092008", true),
  /** A limb, whichever it is; includes {@link #UPPER_EXTREMITY} and {@link #LOWER_EXTREMITY}. */
  EXTREMITY("66019005", true),
  UPPER_EXTREMITY("53120007", true),
  LOWER_EXTREMITY("61685007", true),
  /** An image of the whole body lies in every group. */
  WHOLE_BODY("38266002", true),
  SPINE("421060004", false);

  private final String snomedCode;
  private final boolean playbookRegion;

  RegionGroup(String snomedCode, boolean playbookRegion) {
    this.snomedCode = snomedCode;
    this.playbookRegion = playbookRegion;
  }

  /** The SNOMED CT concept of the area (PS3.16 Table L-1), coding scheme {@code SCT}. */
  public String getSnomedCode() {
    return snomedCode;
  }

  /** The localized name of the group. */
  public String getLabel() {
    return MesRegionGroup.getString(name());
  }

  public String getLabel(Locale locale) {
    return MesRegionGroup.getString(name(), locale);
  }

  /** Whether the group is a <i>Region imaged</i> value of the LOINC/RSNA Radiology Playbook. */
  public boolean isPlaybookRegion() {
    return playbookRegion;
  }

  /** Whether an image of the {@code imaged} group lies in this group. */
  public boolean includes(RegionGroup imaged) {
    return this == imaged
        || imaged == WHOLE_BODY
        || (this == EXTREMITY && (imaged == UPPER_EXTREMITY || imaged == LOWER_EXTREMITY));
  }

  /** The group identified by that SNOMED CT concept. */
  public static Optional<RegionGroup> fromSnomedCode(String code) {
    if (code == null) {
      return Optional.empty();
    }
    String value = code.trim();
    return Arrays.stream(values()).filter(g -> g.snomedCode.equals(value)).findFirst();
  }

  /** The group of that name, ignoring case and surrounding spaces. */
  public static Optional<RegionGroup> fromName(String name) {
    if (name == null || name.isBlank()) {
      return Optional.empty();
    }
    try {
      return Optional.of(valueOf(name.trim().toUpperCase(Locale.ROOT)));
    } catch (IllegalArgumentException e) {
      return Optional.empty();
    }
  }
}
