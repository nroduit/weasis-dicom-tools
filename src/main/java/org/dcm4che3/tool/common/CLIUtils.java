/* ***** BEGIN LICENSE BLOCK *****
 * Version: MPL 1.1/GPL 2.0/LGPL 2.1
 *
 * The contents of this file are subject to the Mozilla Public License Version
 * 1.1 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 * http://www.mozilla.org/MPL/
 *
 * Software distributed under the License is distributed on an "AS IS" basis,
 * WITHOUT WARRANTY OF ANY KIND, either express or implied. See the License
 * for the specific language governing rights and limitations under the
 * License.
 *
 * The Original Code is part of dcm4che, an implementation of DICOM(TM) in
 * Java(TM), hosted at https://github.com/dcm4che.
 *
 * The Initial Developer of the Original Code is
 * Agfa Healthcare.
 * Portions created by the Initial Developer are Copyright (C) 2011
 * the Initial Developer. All Rights Reserved.
 *
 * Contributor(s):
 * See @authors listed below
 *
 * Alternatively, the contents of this file may be used under the terms of
 * either the GNU General Public License Version 2 or later (the "GPL"), or
 * the GNU Lesser General Public License Version 2.1 or later (the "LGPL"),
 * in which case the provisions of the GPL or the LGPL are applicable instead
 * of those above. If you wish to allow use of your version of this file only
 * under the terms of either the GPL or the LGPL, and not to allow others to
 * use your version of this file under the terms of the MPL, indicate your
 * decision by deleting the provisions above and replace them with the notice
 * and other provisions required by the GPL or the LGPL. If you do not delete
 * the provisions above, a recipient may use your version of this file under
 * the terms of any one of the MPL, the GPL or the LGPL.
 *
 * ***** END LICENSE BLOCK ***** */

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
import org.dcm4che3.util.SafeClose;
import org.dcm4che3.util.StreamUtils;
import org.dcm4che3.util.StringUtils;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 *
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
        if (ss.length == 0) {
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
        if (optVals != null) {
            for (int i = 1; i < optVals.length; i++, i++) {
                addAttributes(attrs, toTags(StringUtils.split(optVals[i - 1], '/')),
                    StringUtils.split(optVals[i], '/'));
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
            data.setString(Tag.SeriesInstanceUID, VR.UI, data.getString(Tag.SeriesInstanceUID) + uidSuffix);
            data.setString(Tag.SOPInstanceUID, VR.UI, data.getString(Tag.SOPInstanceUID) + uidSuffix);
        }
        data.update(Attributes.UpdatePolicy.OVERWRITE, attrs, null);
        return true;
    }

    public static String[] toUIDs(String s) {
        if (s.equals("*")) {
            return new String[] { "*" };
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
