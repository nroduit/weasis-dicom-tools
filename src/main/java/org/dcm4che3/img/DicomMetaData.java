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
import javax.imageio.metadata.IIOMetadata;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.dcm4che3.img.stream.ImageDescriptor;
import org.dcm4che3.io.DicomInputStream;
import org.w3c.dom.Node;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 */
public class DicomMetaData extends IIOMetadata {

  private final Attributes fileMetaInformation;
  private final Attributes dcm;
  private final ImageDescriptor desc;
  private final String transferSyntaxUID;

  public DicomMetaData(DicomInputStream dcmStream) throws IOException {
    this.fileMetaInformation = Objects.requireNonNull(dcmStream).readFileMetaInformation();
    this.dcm = dcmStream.readDataset();
    this.desc = new ImageDescriptor(dcm);
    this.transferSyntaxUID =
        fileMetaInformation.getString(Tag.TransferSyntaxUID, dcmStream.getTransferSyntax());
  }

  public DicomMetaData(Attributes dcm, String transferSyntaxUID) {
    this.fileMetaInformation = null;
    this.dcm = Objects.requireNonNull(dcm);
    this.desc = new ImageDescriptor(dcm);
    this.transferSyntaxUID = Objects.requireNonNull(transferSyntaxUID);
  }

  public final Attributes getFileMetaInformation() {
    return fileMetaInformation;
  }

  public final Attributes getDicomObject() {
    return dcm;
  }

  public final ImageDescriptor getImageDescriptor() {
    return desc;
  }

  @Override
  public boolean isReadOnly() {
    return true;
  }

  @Override
  public Node getAsTree(String formatName) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void mergeTree(String formatName, Node root) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void reset() {
    throw new UnsupportedOperationException();
  }

  public String getTransferSyntaxUID() {
    return transferSyntaxUID;
  }

  public boolean isVideoTransferSyntaxUID() {
    return transferSyntaxUID != null && transferSyntaxUID.startsWith("1.2.840.10008.1.2.4.10");
  }

  public boolean isDMediaStorageDirectoryStorage() {
    String mediaStorageSOPClassUID =
        fileMetaInformation == null
            ? null
            : fileMetaInformation.getString(Tag.MediaStorageSOPClassUID);
    return "1.2.840.10008.1.3.10".equals(mediaStorageSOPClassUID);
  }
}
