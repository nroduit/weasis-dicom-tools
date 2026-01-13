/*
 * Copyright (c) 2014-2018 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.dcm4che3.tool.common;

import java.io.File;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;

/**
 * Information container for DICOM fileset metadata. Contains fileset identifier, UID, descriptor
 * file path, and character set information.
 *
 * @author Gunter Zeilinger <gunterze@gmail.com>
 */
public class FilesetInfo {

  private String uid;
  private String id;
  private Path descriptorFile;
  private String descriptorFileCharset;

  /**
   * Creates a fileset info with basic identifiers.
   *
   * @param uid the fileset UID
   * @param id the fileset ID
   */
  public FilesetInfo(String uid, String id) {
    this.uid = uid;
    this.id = id;
  }

  /**
   * Creates a fully populated fileset info.
   *
   * @param uid the fileset UID
   * @param id the fileset ID
   * @param descriptorFile the path to the descriptor file
   * @param descriptorFileCharset the character set for the descriptor file
   */
  public FilesetInfo(String uid, String id, Path descriptorFile, String descriptorFileCharset) {
    this.uid = uid;
    this.id = id;
    this.descriptorFile = descriptorFile;
    this.descriptorFileCharset = descriptorFileCharset;
  }

  /**
   * Gets the fileset unique identifier.
   *
   * @return the fileset UID, may be null
   */
  public String getFilesetUID() {
    return uid;
  }

  /**
   * Sets the fileset unique identifier.
   *
   * @param uid the fileset UID
   * @return this instance for method chaining
   */
  public FilesetInfo setFilesetUID(String uid) {
    this.uid = uid;
    return this;
  }

  /**
   * Gets the fileset identifier.
   *
   * @return the fileset ID, may be null
   */
  public String getFilesetID() {
    return id;
  }

  /**
   * Sets the fileset identifier.
   *
   * @param id the fileset ID
   * @return this instance for method chaining
   */
  public FilesetInfo setFilesetID(String id) {
    this.id = id;
    return this;
  }

  /**
   * Gets the descriptor file path.
   *
   * @return optional containing the descriptor file path
   */
  public Optional<Path> getDescriptorFile() {
    return Optional.ofNullable(descriptorFile);
  }

  /**
   * Sets the descriptor file path.
   *
   * @param descriptorFile the path to the descriptor file
   * @return this instance for method chaining
   */
  public FilesetInfo setDescriptorFile(Path descriptorFile) {
    this.descriptorFile = descriptorFile;
    return this;
  }

  /**
   * Gets the descriptor file character set.
   *
   * @return optional containing the character set name
   */
  public Optional<String> getDescriptorFileCharset() {
    return Optional.ofNullable(descriptorFileCharset);
  }

  /**
   * Sets the descriptor file character set.
   *
   * @param descriptorFileCharset the character set name
   * @return this instance for method chaining
   */
  public FilesetInfo setDescriptorFileCharset(String descriptorFileCharset) {
    this.descriptorFileCharset = descriptorFileCharset;
    return this;
  }

  /**
   * Checks if this fileset info has complete metadata.
   *
   * @return true if UID, ID, and descriptor file are all present
   */
  public boolean isComplete() {
    return uid != null
        && !uid.trim().isEmpty()
        && id != null
        && !id.trim().isEmpty()
        && descriptorFile != null;
  }

  /**
   * Checks if this fileset info has valid identifiers.
   *
   * @return true if both UID and ID are present and non-empty
   */
  public boolean hasValidIdentifiers() {
    return uid != null && !uid.trim().isEmpty() && id != null && !id.trim().isEmpty();
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) return true;
    if (obj == null || getClass() != obj.getClass()) return false;

    var other = (FilesetInfo) obj;
    return Objects.equals(uid, other.uid)
        && Objects.equals(id, other.id)
        && Objects.equals(descriptorFile, other.descriptorFile)
        && Objects.equals(descriptorFileCharset, other.descriptorFileCharset);
  }

  @Override
  public int hashCode() {
    return Objects.hash(uid, id, descriptorFile, descriptorFileCharset);
  }

  @Override
  public String toString() {
    return "FilesetInfo{"
        + "uid='"
        + uid
        + "', "
        + "id='"
        + id
        + "', "
        + "descriptorFile="
        + descriptorFile
        + ", "
        + "descriptorFileCharset='"
        + descriptorFileCharset
        + "'"
        + '}';
  }

  // Legacy compatibility methods

  /**
   * Gets the descriptor file as legacy File object.
   *
   * @return the descriptor file as File, or null if not set
   * @deprecated Use {@link #getDescriptorFile()} instead
   */
  @Deprecated(since = "5.34.0.3", forRemoval = true)
  public File getDescriptorFileAsLegacy() {
    return descriptorFile != null ? descriptorFile.toFile() : null;
  }

  /**
   * Sets the descriptor file using legacy File object.
   *
   * @param descriptorFile the descriptor file as File
   * @deprecated Use {@link #setDescriptorFile(Path)} instead
   */
  @Deprecated(since = "5.34.0.3", forRemoval = true)
  public void setDescriptorFileFromLegacy(File descriptorFile) {
    this.descriptorFile = descriptorFile != null ? descriptorFile.toPath() : null;
  }
}
