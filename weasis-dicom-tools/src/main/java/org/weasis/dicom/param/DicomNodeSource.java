/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.param;

import java.util.List;

/**
 * A source of DICOM nodes discovered dynamically rather than configured by hand, grouped under a
 * single display name.
 *
 * <p>Implementations compute their nodes on demand from a backing registry or API (for example the
 * destinations already configured in a gateway), so the returned nodes are read-only views: editing
 * or removing them is the responsibility of whoever owns the backing source. Applications can offer
 * several sources side by side, one group per source, in node pickers, echo and monitoring tools.
 *
 * <p>This is a plain functional contract with no persistence, Spring or UI dependency, so it can be
 * implemented by any application (a gateway reading its database, a client calling a REST endpoint,
 * a static in-memory list, ...) and reused across tools that need to present nodes to verify or
 * query.
 *
 * @since 5.34
 */
public interface DicomNodeSource {

  /**
   * @return the display name of the group these nodes belong to (for example {@code "Gateway
   *     destinations"})
   */
  String getGroupName();

  /**
   * @return the nodes currently exposed by this source, possibly empty; each call may recompute the
   *     list from the backing source, so callers should not assume a stable identity between calls
   */
  List<DicomNode> getNodes();
}
