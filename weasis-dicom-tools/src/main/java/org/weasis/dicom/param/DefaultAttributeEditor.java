/*
 * Copyright (c) 2017-2019 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.param;

import java.util.List;
import java.util.Objects;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.VR;
import org.dcm4che3.data.Value;
import org.weasis.core.util.StringUtil;
import org.weasis.dicom.util.Hmac;

/**
 * Default implementation of {@link AttributeEditor} that provides UID generation and attribute
 * overriding capabilities.
 *
 * <p>This editor can perform two main operations:
 *
 * <ul>
 *   <li>Generate new UIDs for DICOM studies, series, and instances using HMAC-based hashing
 *   <li>Override existing attributes with predefined values
 * </ul>
 *
 * <p>The UID generation uses a cryptographic hash to ensure consistency - the same original UID
 * will always produce the same new UID when using the same key.
 *
 * @since 1.0
 */
public class DefaultAttributeEditor implements AttributeEditor {

  /** DICOM tags that contain UIDs and should be processed during UID generation. */
  protected static final List<Integer> SUPPORTED_UID_TAGS =
      List.of(
          Tag.StudyInstanceUID,
          Tag.SeriesInstanceUID,
          Tag.SOPInstanceUID,
          Tag.AffectedSOPInstanceUID,
          Tag.FailedSOPInstanceUIDList,
          Tag.MediaStorageSOPInstanceUID,
          Tag.ReferencedSOPInstanceUID,
          Tag.ReferencedSOPInstanceUIDInFile,
          Tag.RequestedSOPInstanceUID,
          Tag.MultiFrameSourceSOPInstanceUID);

  private final boolean generateUIDs;
  private final Attributes tagToOverride;
  private final Hmac hmac;

  /**
   * Creates an editor that only overrides attributes without generating new UIDs.
   *
   * @param tagToOverride DICOM attributes to override (may be null)
   */
  public DefaultAttributeEditor(Attributes tagToOverride) {
    this(false, tagToOverride);
  }

  /**
   * Creates an editor with UID generation and attribute override capabilities.
   *
   * @param generateUIDs whether to generate new UIDs for Study, Series and Instance
   * @param tagToOverride DICOM attributes to override (may be null)
   */
  public DefaultAttributeEditor(boolean generateUIDs, Attributes tagToOverride) {
    this(generateUIDs, null, tagToOverride);
  }

  /**
   * Creates an editor with full configuration options.
   *
   * @param generateUIDs whether to generate new UIDs
   * @param globalKey hex-encoded key for UID generation (null for random key)
   * @param tagToOverride DICOM attributes to override (may be null)
   */
  public DefaultAttributeEditor(boolean generateUIDs, String globalKey, Attributes tagToOverride) {
    this.generateUIDs = generateUIDs;
    this.tagToOverride = tagToOverride;
    this.hmac = generateUIDs ? createHmac(globalKey) : null;
  }

  private Hmac createHmac(String globalKey) {
    byte[] key =
        StringUtil.hasText(globalKey) ? Hmac.hexToByte(globalKey) : Hmac.generateRandomKey();
    return new Hmac(key);
  }

  public boolean isGenerateUIDs() {
    return generateUIDs;
  }

  public Attributes getTagToOverride() {
    return tagToOverride;
  }

  public Hmac getHmac() {
    return hmac;
  }

  @Override
  public void apply(Attributes data, AttributeEditorContext context) {
    if (data == null) {
      return;
    }
    if (generateUIDs) {
      generateNewUIDs(data);
    }

    if (shouldOverrideAttributes()) {
      data.update(Attributes.UpdatePolicy.OVERWRITE, tagToOverride, null);
    }
  }

  private void generateNewUIDs(Attributes data) {
    var visitor = new UidVisitor(hmac);
    try {
      data.accept(visitor, true);
    } catch (Exception e) {
      throw new RuntimeException("Failed to generate UIDs", e);
    }
  }

  private boolean shouldOverrideAttributes() {
    return tagToOverride != null && !tagToOverride.isEmpty();
  }

  /** Visitor that processes DICOM attributes to replace UIDs with generated ones. */
  private static class UidVisitor implements Attributes.Visitor {
    private final Hmac hmac;

    UidVisitor(Hmac hmac) {
      this.hmac = Objects.requireNonNull(hmac, "HMAC cannot be null");
    }

    @Override
    public boolean visit(Attributes attrs, int tag, VR vr, Object val) {
      if (isUidAttribute(vr, val, tag)) {
        processUidValue(attrs, tag, val);
      }
      return true;
    }

    private boolean isUidAttribute(VR vr, Object val, int tag) {
      return vr == VR.UI && val != Value.NULL && SUPPORTED_UID_TAGS.contains(tag);
    }

    private void processUidValue(Attributes attrs, int tag, Object val) {
      if (val instanceof byte[]) {
        processStringArrayValue(attrs, tag);
      } else if (val instanceof String[] stringArray) {
        processStringArray(stringArray);
      } else if (val instanceof String singleString) {
        processSingleString(attrs, tag, singleString);
      } else {
        processSingleString(attrs, tag, val.toString());
      }
    }

    private void processStringArrayValue(Attributes attrs, int tag) {
      String[] strings = attrs.getStrings(tag);
      if (strings != null) {
        if (strings.length == 1) {
          processSingleString(attrs, tag, strings[0]);
        } else {
          processStringArray(strings);
        }
      }
    }

    private void processStringArray(String[] strings) {
      for (int i = 0; i < strings.length; i++) {
        String hashedUid = hmac.uidHash(strings[i]);
        if (hashedUid != null) {
          strings[i] = hashedUid;
        }
      }
    }

    private void processSingleString(Attributes attrs, int tag, String uid) {
      String hashedUid = hmac.uidHash(uid);
      if (hashedUid != null) {
        attrs.setString(tag, VR.UI, hashedUid);
      }
    }
  }
}
