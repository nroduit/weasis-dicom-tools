/*
 * Copyright (c) 2014-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.dcm4che3.tool.movescu;

import java.io.File;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.concurrent.TimeUnit;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.ElementDictionary;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.UID;
import org.dcm4che3.data.VR;
import org.dcm4che3.io.DicomInputStream;
import org.dcm4che3.net.ApplicationEntity;
import org.dcm4che3.net.Association;
import org.dcm4che3.net.Connection;
import org.dcm4che3.net.Device;
import org.dcm4che3.net.DimseRSPHandler;
import org.dcm4che3.net.IncompatibleConnectionException;
import org.dcm4che3.net.pdu.AAssociateRQ;
import org.dcm4che3.net.pdu.ExtendedNegotiation;
import org.dcm4che3.net.pdu.PresentationContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.dicom.param.DicomProgress;
import org.weasis.dicom.param.DicomState;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 */
public class MoveSCU extends Device implements AutoCloseable {
  private static final Logger LOGGER = LoggerFactory.getLogger(MoveSCU.class);

  public enum InformationModel {
    PatientRoot(UID.PatientRootQueryRetrieveInformationModelMove, "STUDY"),
    StudyRoot(UID.StudyRootQueryRetrieveInformationModelMove, "STUDY"),
    PatientStudyOnly(UID.PatientStudyOnlyQueryRetrieveInformationModelMove, "STUDY"),
    CompositeInstanceRoot(UID.CompositeInstanceRootRetrieveMove, "IMAGE"),
    HangingProtocol(UID.HangingProtocolInformationModelMove, null),
    ColorPalette(UID.ColorPaletteQueryRetrieveInformationModelMove, null);

    final String cuid;
    final String level;

    InformationModel(String cuid, String level) {
      this.cuid = cuid;
      this.level = level;
    }

    public String getCuid() {
      return cuid;
    }
  }

  private static final int[] DEF_IN_FILTER = {
    Tag.SOPInstanceUID, Tag.StudyInstanceUID, Tag.SeriesInstanceUID
  };

  private final ApplicationEntity ae = new ApplicationEntity("MOVESCU");
  private final Connection conn = new Connection();
  private final Connection remote = new Connection();
  private final transient AAssociateRQ rq = new AAssociateRQ();
  private int priority;
  private String destination;
  private InformationModel model;
  private final Attributes keys = new Attributes();
  private int[] inFilter = DEF_IN_FILTER;
  private transient Association as;
  private int cancelAfter;
  private boolean releaseEager;
  private final transient DicomState state;

  public MoveSCU() throws IOException {
    this(null);
  }

  public MoveSCU(DicomProgress progress) throws IOException {
    super("movescu");
    addConnection(conn);
    addApplicationEntity(ae);
    ae.addConnection(conn);
    state = new DicomState(progress);
  }

  public final void setPriority(int priority) {
    this.priority = priority;
  }

  public void setCancelAfter(int cancelAfter) {
    this.cancelAfter = cancelAfter;
  }

  public void setReleaseEager(boolean releaseEager) {
    this.releaseEager = releaseEager;
  }

  public final void setInformationModel(InformationModel model, String[] tss, boolean relational) {
    this.model = model;
    rq.addPresentationContext(new PresentationContext(1, model.cuid, tss));
    if (relational) {
      rq.addExtendedNegotiation(new ExtendedNegotiation(model.cuid, new byte[] {1}));
    }
    if (model.level != null) {
      addLevel(model.level);
    }
  }

  public void addLevel(String s) {
    keys.setString(Tag.QueryRetrieveLevel, VR.CS, s);
  }

  public final void setDestination(String destination) {
    this.destination = destination;
  }

  public void addKey(int tag, String... ss) {
    VR vr = ElementDictionary.vrOf(tag, keys.getPrivateCreator(tag));
    keys.setString(tag, vr, ss);
  }

  public final void setInputFilter(int[] inFilter) {
    this.inFilter = inFilter;
  }

  public ApplicationEntity getApplicationEntity() {
    return ae;
  }

  public Connection getRemoteConnection() {
    return remote;
  }

  public AAssociateRQ getAAssociateRQ() {
    return rq;
  }

  public Association getAssociation() {
    return as;
  }

  public Attributes getKeys() {
    return keys;
  }

  public void open()
      throws IOException, InterruptedException, IncompatibleConnectionException,
          GeneralSecurityException {
    as = ae.connect(conn, remote, rq);
  }

  @Override
  public void close() throws IOException, InterruptedException {
    if (as != null && as.isReadyForDataTransfer()) {
      as.waitForOutstandingRSP();
      as.release();
    }
  }

  public void retrieve(File f) throws IOException, InterruptedException {
    Attributes attrs = new Attributes();
    try (DicomInputStream dis = new DicomInputStream(f)) {
      attrs.addSelected(dis.readDataset(-1, -1), inFilter);
    }
    attrs.addAll(keys);
    retrieve(attrs);
  }

  public void retrieve() throws IOException, InterruptedException {
    retrieve(keys);
  }

  private void retrieve(Attributes keys) throws IOException, InterruptedException {
    DimseRSPHandler rspHandler =
        new DimseRSPHandler(as.nextMessageID()) {

          @Override
          public void onDimseRSP(Association as, Attributes cmd, Attributes data) {
            super.onDimseRSP(as, cmd, data);
            DicomProgress p = state.getProgress();
            if (p != null) {
              p.setAttributes(cmd);
              if (p.isCancel()) {
                try {
                  this.cancel(as);
                } catch (IOException e) {
                  LOGGER.error("Cancel C-MOVE", e);
                }
              }
            }
          }
        };
    as.cmove(model.cuid, priority, keys, null, destination, rspHandler);
    if (cancelAfter > 0) {
      schedule(
          () -> {
            try {
              rspHandler.cancel(as);
              if (releaseEager) {
                as.release();
              }
            } catch (IOException e) {
              LOGGER.error("Cancel after C-MOVE", e);
            }
          },
          cancelAfter,
          TimeUnit.MILLISECONDS);
    }
  }

  public Connection getConnection() {
    return conn;
  }

  public DicomState getState() {
    return state;
  }
}
