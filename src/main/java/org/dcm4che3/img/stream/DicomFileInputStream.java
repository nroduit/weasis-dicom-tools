/*
 * Copyright (c) 2021 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.dcm4che3.img.stream;

import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import org.dcm4che3.img.DicomMetaData;
import org.dcm4che3.io.DicomInputStream;

/**
 * @author Nicolas Roduit
 */
public class DicomFileInputStream extends DicomInputStream implements ImageReaderDescriptor {

  private final Path path;
  private DicomMetaData metadata;

  public DicomFileInputStream(Path path) throws IOException {
    super(Files.newInputStream(path));
    this.path = path;
  }

  public DicomFileInputStream(String path) throws IOException {
    this(FileSystems.getDefault().getPath(path));
  }

  public Path getPath() {
    return path;
  }

  public DicomMetaData getMetadata() throws IOException {
    if (metadata == null) {
      this.metadata = new DicomMetaData(this);
    }
    return metadata;
  }

  @Override
  public ImageDescriptor getImageDescriptor() {
    try {
      getMetadata();
    } catch (IOException e) {
      return null;
    }
    return metadata.getImageDescriptor();
  }
}
