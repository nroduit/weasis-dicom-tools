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
import java.util.Arrays;
import java.util.Objects;
import java.util.Properties;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.ElementDictionary;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.UID;
import org.dcm4che3.data.VR;
import org.dcm4che3.util.StreamUtils;
import org.dcm4che3.util.StringUtils;

/**
 * Utility class providing common CLI operations for DICOM tools including property loading, tag
 * conversion, and attribute manipulation.
 *
 * @author Gunter Zeilinger <gunterze@gmail.com>
 */
public final class CLIUtils {

  private CLIUtils() {}

  /**
   * Loads properties from a URL or file path.
   *
   * @param url the URL or file path to load properties from
   * @param properties the properties object to load into, or null to create a new one
   * @return the properties object containing the loaded data
   * @throws IOException if the properties cannot be loaded
   */
  public static Properties loadProperties(String url, Properties properties) throws IOException {
    var props = Objects.requireNonNullElseGet(properties, Properties::new);
    try (InputStream in = StreamUtils.openFileOrURL(url)) {
      props.load(in);
    }
    return props;
  }

  /**
   * Converts an array of tag strings or keywords to integer tag values.
   *
   * @param tagOrKeywords array of tag strings or keywords to convert
   * @return array of integer tag values
   * @throws IllegalArgumentException if any tag or keyword is invalid
   */
  public static int[] toTags(String[] tagOrKeywords) {
    return Arrays.stream(tagOrKeywords).mapToInt(CLIUtils::toTag).toArray();
  }

  /**
   * Converts a tag string or keyword to an integer tag value.
   *
   * @param tagOrKeyword the tag string (hex) or keyword to convert
   * @return the integer tag value
   * @throws IllegalArgumentException if the tag or keyword is invalid
   */
  public static int toTag(String tagOrKeyword) {
    try {
      return Integer.parseInt(tagOrKeyword, 16);
    } catch (NumberFormatException e) {
      int tag = ElementDictionary.tagForKeyword(tagOrKeyword, null);
      if (tag == -1) {
        throw new IllegalArgumentException("Unknown tag or keyword: " + tagOrKeyword, e);
      }
      return tag;
    }
  }

  /**
   * Adds attributes to a DICOM Attributes object using a tag path and values.
   *
   * @param attrs the attributes object to modify
   * @param tags the tag path (may include sequence tags)
   * @param values the string values to set (empty for null/empty attributes)
   */
  public static void addAttributes(Attributes attrs, int[] tags, String... values) {
    if (tags.length == 0) {
      return;
    }

    Attributes targetItem = navigateToTargetItem(attrs, tags);
    setAttributeValue(targetItem, tags[tags.length - 1], values);
  }

  private static Attributes navigateToTargetItem(Attributes attrs, int[] tags) {
    Attributes item = attrs;
    for (int i = 0; i < tags.length - 1; i++) {
      item = getOrCreateSequenceItem(item, tags[i]);
    }
    return item;
  }

  private static Attributes getOrCreateSequenceItem(Attributes item, int tag) {
    var sequence = item.getSequence(tag);
    if (sequence == null) {
      sequence = item.newSequence(tag, 1);
    }
    if (sequence.isEmpty()) {
      sequence.add(new Attributes());
    }
    return sequence.get(0);
  }

  private static void setAttributeValue(Attributes item, int tag, String[] values) {
    VR vr = ElementDictionary.vrOf(tag, item.getPrivateCreator(tag));
    if (isEmptyValue(values)) {
      if (vr == VR.SQ) {
        item.newSequence(tag, 1).add(new Attributes(0));
      } else {
        item.setNull(tag, vr);
      }
    } else {
      item.setString(tag, vr, values);
    }
  }

  private static boolean isEmptyValue(String[] values) {
    return values.length == 0 || (values.length == 1 && values[0].isEmpty());
  }

  /**
   * Adds attributes from option value strings in the format "tag=value" or "tag/subtag=value".
   *
   * @param attrs the attributes object to modify
   * @param optVals array of option value strings, may be null
   */
  public static void addAttributes(Attributes attrs, String[] optVals) {
    if (optVals == null) {
      return;
    }

    Arrays.stream(optVals).forEach(optVal -> processOptionValue(attrs, optVal));
  }

  private static void processOptionValue(Attributes attrs, String optVal) {
    int delimIndex = optVal.indexOf('=');
    if (delimIndex < 0) {
      // No value specified, add empty attribute
      addAttributes(attrs, toTags(StringUtils.split(optVal, '/')));
    } else {
      // Value specified, add attribute with value
      String tagPath = optVal.substring(0, delimIndex);
      String value = optVal.substring(delimIndex + 1);
      addAttributes(attrs, toTags(StringUtils.split(tagPath, '/')), value);
    }
  }

  /**
   * Adds empty attributes from option value strings.
   *
   * @param attrs the attributes object to modify
   * @param optVals array of option value strings, may be null
   */
  public static void addEmptyAttributes(Attributes attrs, String[] optVals) {
    if (optVals == null) {
      return;
    }
    Arrays.stream(optVals)
        .map(optVal -> StringUtils.split(optVal, '/'))
        .map(CLIUtils::toTags)
        .forEach(tags -> addAttributes(attrs, tags));
  }

  /**
   * Updates DICOM attributes with new values and optionally appends a UID suffix.
   *
   * @param data the DICOM attributes to update
   * @param attrs the new attributes to merge
   * @param uidSuffix optional suffix to append to UIDs
   * @return true if any updates were made, false otherwise
   */
  public static boolean updateAttributes(Attributes data, Attributes attrs, String uidSuffix) {
    if (attrs.isEmpty() && uidSuffix == null) {
      return false;
    }
    if (uidSuffix != null) {
      updateInstanceUIDs(data, uidSuffix);
    }
    data.update(Attributes.UpdatePolicy.OVERWRITE, attrs, null);
    return true;
  }

  private static void updateInstanceUIDs(Attributes data, String uidSuffix) {
    updateUID(data, Tag.StudyInstanceUID, uidSuffix);
    updateUID(data, Tag.SeriesInstanceUID, uidSuffix);
    updateUID(data, Tag.SOPInstanceUID, uidSuffix);
  }

  private static void updateUID(Attributes data, int tag, String suffix) {
    String currentUID = data.getString(tag);
    if (currentUID != null) {
      data.setString(tag, VR.UI, currentUID + suffix);
    }
  }

  /**
   * Converts a comma-separated string of UIDs to an array.
   *
   * @param uidString the string containing UIDs separated by commas
   * @return array of UID strings
   */
  public static String[] toUIDs(String uidString) {
    if ("*".equals(uidString)) {
      return new String[] {"*"};
    }

    return Arrays.stream(StringUtils.split(uidString, ','))
        .map(CLIUtils::toUID)
        .toArray(String[]::new);
  }

  /**
   * Converts a UID string or name to a valid UID.
   *
   * @param uid the UID string or name to convert
   * @return the valid UID string
   */
  public static String toUID(String uid) {
    String trimmedUID = uid.trim();
    return ("*".equals(trimmedUID) || Character.isDigit(trimmedUID.charAt(0)))
        ? trimmedUID
        : UID.forName(trimmedUID);
  }
}
