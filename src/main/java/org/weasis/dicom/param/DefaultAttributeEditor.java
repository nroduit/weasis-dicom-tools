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
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.VR;
import org.dcm4che3.data.Value;
import org.weasis.core.util.StringUtil;
import org.weasis.dicom.util.Hmac;

public class DefaultAttributeEditor implements AttributeEditor {

  protected static final List<Integer> uids =
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

  public DefaultAttributeEditor(Attributes tagToOverride) {
    this(false, tagToOverride);
  }

  /**
   * @param generateUIDs generate new UIDS for Study, Series and Instance
   * @param tagToOverride list of DICOM attributes to override
   */
  public DefaultAttributeEditor(boolean generateUIDs, Attributes tagToOverride) {
    this(generateUIDs, null, tagToOverride);
  }

  public DefaultAttributeEditor(boolean generateUIDs, String globalKey, Attributes tagToOverride) {
    this.generateUIDs = generateUIDs;
    this.tagToOverride = tagToOverride;
    byte[] key =
        StringUtil.hasText(globalKey) ? Hmac.hexToByte(globalKey) : Hmac.generateRandomKey();
    this.hmac = generateUIDs ? new Hmac(key) : null;
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
    if (data != null) {
      if (generateUIDs) {
        UidVisitor visitor = new UidVisitor(hmac);
        try {
          data.accept(visitor, true);
        } catch (Exception e) {
          throw new RuntimeException(e);
        }
      }
      if (tagToOverride != null && !tagToOverride.isEmpty()) {
        data.update(Attributes.UpdatePolicy.OVERWRITE, tagToOverride, null);
      }
    }
  }

  private static class UidVisitor implements Attributes.Visitor {
    private final Hmac hmac;

    public UidVisitor(Hmac hmac) {
      this.hmac = hmac;
    }

    @Override
    public boolean visit(Attributes attrs, int tag, VR vr, Object val) {
      if (vr == VR.UI && val != Value.NULL && uids.contains(tag)) {
        String[] ss;
        if (val instanceof byte[]) {
          ss = attrs.getStrings(tag);
          val = ss.length == 1 ? ss[0] : ss;
        }
        if (val instanceof String[]) {
          ss = (String[]) val;
          for (int i = 0; i < ss.length; i++) {
            String uid = hmac.uidHash(ss[i]);
            if (uid != null) {
              ss[i] = uid;
            }
          }
        } else {
          String uid = hmac.uidHash(val.toString());
          if (uid != null) {
            attrs.setString(tag, VR.UI, uid);
          }
        }
      }
      return true;
    }
  }
}
