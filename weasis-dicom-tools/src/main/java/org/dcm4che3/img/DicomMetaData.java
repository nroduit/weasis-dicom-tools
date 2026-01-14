/*
 * Copyright (c) 2021 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.dcm4che3.img;

import java.io.IOException;
import java.util.Objects;
import java.util.Optional;
import javax.imageio.metadata.IIOMetadata;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.UID;
import org.dcm4che3.img.stream.ImageDescriptor;
import org.dcm4che3.io.DicomInputStream;
import org.w3c.dom.Node;

/**
 * DICOM-specific metadata implementation extending Java ImageIO's IIOMetadata.
 *
 * <p>This class provides access to DICOM file metadata, including File Meta Information, DICOM
 * dataset attributes, and derived image descriptors. It serves as a bridge between DICOM data
 * structures and the Java ImageIO framework.
 *
 * <p><strong>Key Features:</strong>
 *
 * <ul>
 *   <li>Access to DICOM File Meta Information (Part 10 header)
 *   <li>Complete DICOM dataset attribute access
 *   <li>Derived image descriptor for pixel data characteristics
 *   <li>Transfer syntax identification and validation
 *   <li>Specialized detection for video, segmentation, and directory storage
 * </ul>
 *
 * <p><strong>Usage Examples:</strong>
 *
 * <pre>{@code
 * // From DICOM stream
 * try (DicomInputStream dis = new DicomInputStream(file)) {
 *     DicomMetaData metadata = new DicomMetaData(dis);
 *     String sopClass = metadata.getMediaStorageSOPClassUID();
 *     boolean isVideo = metadata.isVideoTransferSyntaxUID();
 * }
 *
 * // From existing attributes
 * DicomMetaData metadata = new DicomMetaData(attributes, transferSyntaxUID);
 * ImageDescriptor descriptor = metadata.getImageDescriptor();
 * }</pre>
 *
 * <p><strong>Note:</strong> This metadata implementation is read-only and does not support
 * tree-based manipulation operations defined in the IIOMetadata base class.
 *
 * @author Gunter Zeilinger
 * @author Nicolas Roduit
 * @see IIOMetadata
 * @see ImageDescriptor
 * @see DicomInputStream
 */
public final class DicomMetaData extends IIOMetadata {

  private static final String VIDEO_TRANSFER_SYNTAX_PREFIX = "1.2.840.10008.1.2.4.10";

  private final Attributes fileMetaInformation;
  private final Attributes dcm;
  private final ImageDescriptor desc;
  private final String transferSyntaxUID;

  /**
   * Creates DICOM metadata by reading from a DICOM input stream.
   *
   * <p>This constructor reads both the File Meta Information (if present) and the complete DICOM
   * dataset from the stream. The transfer syntax is determined from the File Meta Information or
   * falls back to the stream's detected syntax.
   *
   * @param dcmStream the DICOM input stream to read from
   * @throws IOException if an I/O error occurs while reading the stream
   * @throws NullPointerException if dcmStream is null
   */
  public DicomMetaData(DicomInputStream dcmStream) throws IOException {
    Objects.requireNonNull(dcmStream, "DICOM input stream cannot be null");

    this.fileMetaInformation = dcmStream.readFileMetaInformation();
    this.dcm = dcmStream.readDataset();
    this.desc = new ImageDescriptor(dcm);
    this.transferSyntaxUID = resolveTransferSyntax(dcmStream);
  }

  /**
   * Creates DICOM metadata from existing DICOM attributes and transfer syntax.
   *
   * <p>This constructor is useful when you already have parsed DICOM data and want to create
   * metadata without File Meta Information. The resulting metadata will have null File Meta
   * Information.
   *
   * @param dcm the DICOM dataset attributes
   * @param transferSyntaxUID the transfer syntax UID
   * @throws NullPointerException if either parameter is null
   */
  public DicomMetaData(Attributes dcm, String transferSyntaxUID) {
    this.fileMetaInformation = null;
    this.dcm = Objects.requireNonNull(dcm, "DICOM attributes cannot be null");
    this.desc = new ImageDescriptor(dcm);
    this.transferSyntaxUID =
        Objects.requireNonNull(transferSyntaxUID, "Transfer syntax UID cannot be null");
  }

  private String resolveTransferSyntax(DicomInputStream dcmStream) {
    return Optional.ofNullable(fileMetaInformation)
        .map(fmi -> fmi.getString(Tag.TransferSyntaxUID, dcmStream.getTransferSyntax()))
        .orElseGet(dcmStream::getTransferSyntax);
  }

  /**
   * Gets the DICOM File Meta Information attributes.
   *
   * <p>The File Meta Information contains metadata about the DICOM file itself, including Media
   * Storage SOP Class UID, Media Storage SOP Instance UID, and Transfer Syntax UID. This may be
   * null if the metadata was created from attributes without File Meta Information.
   *
   * @return the File Meta Information attributes, or null if not available
   */
  public Attributes getFileMetaInformation() {
    return fileMetaInformation;
  }

  /**
   * Gets the main DICOM dataset attributes.
   *
   * <p>This contains all the DICOM data elements that describe the image and associated medical
   * information, excluding the File Meta Information.
   *
   * @return the DICOM dataset attributes, never null
   */
  public Attributes getDicomObject() {
    return dcm;
  }

  /**
   * Gets the derived image descriptor containing pixel data characteristics.
   *
   * <p>The image descriptor provides convenient access to image-related attributes such as
   * dimensions, photometric interpretation, bits per pixel, and other properties derived from the
   * DICOM dataset.
   *
   * @return the image descriptor, never null
   */
  public ImageDescriptor getImageDescriptor() {
    return desc;
  }

  /**
   * Gets the transfer syntax UID used for encoding this DICOM data.
   *
   * <p>The transfer syntax defines how the DICOM data is encoded, including byte ordering,
   * compression method, and value representation handling.
   *
   * @return the transfer syntax UID, never null
   */
  public String getTransferSyntaxUID() {
    return transferSyntaxUID;
  }

  /** Gets the Media Storage SOP Class UID from File Meta Information. */
  public Optional<String> getMediaStorageSOPClassUID() {
    return Optional.ofNullable(fileMetaInformation)
        .map(fmi -> fmi.getString(Tag.MediaStorageSOPClassUID));
  }

  /**
   * Checks if this DICOM uses a video transfer syntax.
   *
   * <p>Video transfer syntaxes are used for DICOM video objects and have UIDs starting with
   * "1.2.840.10008.1.2.4.10". These include MPEG2, MPEG4, and H.264 video compression formats.
   *
   * @return true if the transfer syntax is for video encoding, false otherwise
   */
  public boolean isVideoTransferSyntaxUID() {
    return transferSyntaxUID != null && transferSyntaxUID.startsWith(VIDEO_TRANSFER_SYNTAX_PREFIX);
  }

  /** Checks if this DICOM represents a Media Storage Directory. */
  public boolean isMediaStorageDirectory() {
    return getMediaStorageSOPClassUID()
        .filter(UID.MediaStorageDirectoryStorage::equals)
        .isPresent();
  }

  /** Checks if this DICOM represents a Segmentation Storage object. */
  public boolean isSegmentationStorage() {
    return getMediaStorageSOPClassUID().filter(UID.SegmentationStorage::equals).isPresent();
  }

  /** Checks if File Meta Information is available. */
  public boolean hasFileMetaInformation() {
    return fileMetaInformation != null;
  }

  /** Gets the number of frames in this DICOM image. */
  public int getNumberOfFrames() {
    return desc.getFrames();
  }

  // ======== IIOMetadata implementation ========
  @Override
  public boolean isReadOnly() {
    return true;
  }

  @Override
  public Node getAsTree(String formatName) {
    throw new UnsupportedOperationException("DICOM metadata does not support tree representation");
  }

  @Override
  public void mergeTree(String formatName, Node root) {
    throw new UnsupportedOperationException("DICOM metadata is read-only");
  }

  @Override
  public void reset() {
    throw new UnsupportedOperationException("DICOM metadata is read-only");
  }

  @Override
  public String toString() {
    return "DicomMetaData{transferSyntax='%s', sopClass='%s', frames=%d, hasFileMetaInfo=%s}"
        .formatted(
            transferSyntaxUID,
            getMediaStorageSOPClassUID().orElse("N/A"),
            getNumberOfFrames(),
            hasFileMetaInformation());
  }
}
