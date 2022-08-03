/*
 * Copyright (c) 2014-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.dcm4che3.tool.common;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.ElementDictionary;
import org.dcm4che3.data.Sequence;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.UID;
import org.dcm4che3.data.VR;
import org.dcm4che3.util.StreamUtils;
import org.dcm4che3.util.StringUtils;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 */
public class CLIUtils {

  public static Properties loadProperties(String url, Properties p) throws IOException {
    if (p == null) {
      p = new Properties();
    }
    try (InputStream in = StreamUtils.openFileOrURL(url)) {
      p.load(in);
    }
    return p;
  }

  public static int[] toTags(String[] tagOrKeywords) {
    int[] tags = new int[tagOrKeywords.length];
    for (int i = 0; i < tags.length; i++) {
      tags[i] = toTag(tagOrKeywords[i]);
    }
    return tags;
  }

  public static int toTag(String tagOrKeyword) {
    try {
      return Integer.parseInt(tagOrKeyword, 16);
    } catch (IllegalArgumentException e) {
      int tag = ElementDictionary.tagForKeyword(tagOrKeyword, null);
      if (tag == -1) {
        throw new IllegalArgumentException(tagOrKeyword);
      }
      return tag;
    }
  }

  public static void addAttributes(Attributes attrs, int[] tags, String... ss) {
    Attributes item = attrs;
    for (int i = 0; i < tags.length - 1; i++) {
      int tag = tags[i];
      Sequence sq = item.getSequence(tag);
      if (sq == null) {
        sq = item.newSequence(tag, 1);
      }
      if (sq.isEmpty()) {
        sq.add(new Attributes());
      }
      item = sq.get(0);
    }
    int tag = tags[tags.length - 1];
    VR vr = ElementDictionary.vrOf(tag, item.getPrivateCreator(tag));
    if (ss.length == 0 || ss.length == 1 && ss[0].isEmpty()) {
      if (vr == VR.SQ) {
        item.newSequence(tag, 1).add(new Attributes(0));
      } else {
        item.setNull(tag, vr);
      }
    } else {
      item.setString(tag, vr, ss);
    }
  }

  public static void addAttributes(Attributes attrs, String[] optVals) {
    if (optVals != null)
      for (String optVal : optVals) {
        int delim = optVal.indexOf('=');
        if (delim < 0) {
          addAttributes(attrs, toTags(StringUtils.split(optVal, '/')));
        } else {
          addAttributes(
              attrs,
              toTags(StringUtils.split(optVal.substring(0, delim), '/')),
              optVal.substring(delim + 1));
        }
      }
  }

  public static void addEmptyAttributes(Attributes attrs, String[] optVals) {
    if (optVals != null) {
      for (int i = 0; i < optVals.length; i++) {
        addAttributes(attrs, toTags(StringUtils.split(optVals[i], '/')));
      }
    }
  }

  public static boolean updateAttributes(Attributes data, Attributes attrs, String uidSuffix) {
    if (attrs.isEmpty() && uidSuffix == null) {
      return false;
    }
    if (uidSuffix != null) {
      data.setString(Tag.StudyInstanceUID, VR.UI, data.getString(Tag.StudyInstanceUID) + uidSuffix);
      data.setString(
          Tag.SeriesInstanceUID, VR.UI, data.getString(Tag.SeriesInstanceUID) + uidSuffix);
      data.setString(Tag.SOPInstanceUID, VR.UI, data.getString(Tag.SOPInstanceUID) + uidSuffix);
    }
    data.update(Attributes.UpdatePolicy.OVERWRITE, attrs, null);
    return true;
  }

  public static String[] toUIDs(String s) {
    if (s.equals("*")) {
      return new String[] {"*"};
    }

    String[] uids = StringUtils.split(s, ',');
    for (int i = 0; i < uids.length; i++) {
      uids[i] = toUID(uids[i]);
    }
    return uids;
  }

  public static String toUID(String uid) {
    uid = uid.trim();
    return (uid.equals("*") || Character.isDigit(uid.charAt(0))) ? uid : UID.forName(uid);
  }
}
