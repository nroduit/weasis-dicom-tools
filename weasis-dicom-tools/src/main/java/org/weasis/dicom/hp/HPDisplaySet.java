/*
 * Copyright (c) 2024 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.hp;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Sequence;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.VR;
import org.dcm4che3.img.util.DicomUtils;
import org.weasis.dicom.geom.PatientOrientation.Biped;
import org.weasis.dicom.geom.PatientOrientation.Quadruped;
import org.weasis.dicom.macro.Module;

public class HPDisplaySet extends Module {

  private HPImageSet imageSet;
  private final List<HPImageBox> imageBoxes;
  private final List<HPSelector> filters;
  private final List<HPComparator> cmps;

  protected HPDisplaySet(Attributes attributes, HPImageSet imageSet) {
    super(attributes);
    this.imageSet = imageSet;
    Sequence imageBoxesSeq = attributes.getSequence(Tag.ImageBoxesSequence);
    if (imageBoxesSeq == null || imageBoxesSeq.isEmpty()) {
      throw new IllegalArgumentException("Missing (0072,0300) Image Boxes Sequence");
    }
    int numImageBoxes = imageBoxesSeq.size();
    this.imageBoxes = new ArrayList<>(numImageBoxes);
    for (int i = 0; i < numImageBoxes; i++) {
      imageBoxes.add(createHPImageBox(imageBoxesSeq.get(i), numImageBoxes));
    }
    Sequence filterOpSeq = attributes.getSequence(Tag.FilterOperationsSequence);
    if (filterOpSeq == null || filterOpSeq.isEmpty()) {
      this.filters = new ArrayList<>(0);
    } else {
      int n = filterOpSeq.size();
      this.filters = new ArrayList<>(n);
      for (int i = 0; i < n; i++) {
        filters.add(HPSelectorFactory.createDisplaySetFilter(filterOpSeq.get(i)));
      }
    }
    Sequence sortingOpSeq = attributes.getSequence(Tag.SortingOperationsSequence);
    if (sortingOpSeq == null || sortingOpSeq.isEmpty()) {
      this.cmps = new ArrayList<>(0);
    } else {
      int n = sortingOpSeq.size();
      this.cmps = new ArrayList<>(n);
      for (int i = 0; i < n; i++) {
        cmps.add(HPComparatorFactory.createHPComparator(sortingOpSeq.get(i)));
      }
    }
  }

  public HPDisplaySet() {
    super(new Attributes());
    imageBoxes = new ArrayList<>();
    filters = new ArrayList<>();
    cmps = new ArrayList<>();
  }

  protected HPImageBox createHPImageBox(Attributes item, int numImageBoxes) {
    return new HPImageBox(item, numImageBoxes);
  }

  public final HPImageSet getImageSet() {
    return imageSet;
  }

  public void setImageSet(HPImageSet imageSet) {
    dcmItems.setInt(Tag.ImageSetNumber, VR.US, imageSet.getImageSetNumber());
    this.imageSet = imageSet;
  }

  public List<HPImageBox> getImageBoxes() {
    return Collections.unmodifiableList(imageBoxes);
  }

  public void addImageBox(HPImageBox imageBox) {
    imageBox.setImageBoxNumber(imageBoxes.size() + 1);
    dcmItems.ensureSequence(Tag.ImageBoxesSequence, 1).add(imageBox.getAttributes());
    imageBoxes.add(imageBox);
  }

  public boolean removeImageBox(HPImageBox imageBox) {
    int index = imageBoxes.indexOf(imageBox);
    if (index == -1) {
      return false;
    }
    removeSequenceItem(Tag.ImageBoxesSequence, index);
    imageBoxes.remove(index);
    for (; index < imageBoxes.size(); ++index) {
      imageBoxes.get(index).setImageBoxNumber(index + 1);
    }
    return true;
  }

  public void removeAllImageBoxes() {
    removeAllSequenceItems(Tag.ImageBoxesSequence);
    imageBoxes.clear();
  }

  public List<HPSelector> getFilterOperations() {
    return Collections.unmodifiableList(filters);
  }

  public void addFilterOperation(HPSelector selector) {
    dcmItems.ensureSequence(Tag.FilterOperationsSequence, 1).add(selector.getAttributes());
    filters.add(selector);
  }

  public boolean removeFilterOperation(HPSelector cmp) {
    int index = filters.indexOf(cmp);
    if (index == -1) {
      return false;
    }
    removeSequenceItem(Tag.FilterOperationsSequence, index);
    filters.remove(index);
    return true;
  }

  public void removeAllFilterOperations() {
    removeAllSequenceItems(Tag.FilterOperationsSequence);
    filters.clear();
  }

  public List<HPComparator> getSortingOperations() {
    return Collections.unmodifiableList(cmps);
  }

  public void addSortingOperation(HPComparator cmp) {
    dcmItems.ensureSequence(Tag.SortingOperationsSequence, 1).add(cmp.getAttributes());
    cmps.add(cmp);
  }

  public boolean removeSortingOperation(HPComparator cmp) {
    int index = cmps.indexOf(cmp);
    if (index == -1) {
      return false;
    }
    removeSequenceItem(Tag.SortingOperationsSequence, index);
    cmps.remove(index);
    return true;
  }

  public void removeAllSortingOperations() {
    removeAllSequenceItems(Tag.SortingOperationsSequence);
    cmps.clear();
  }

  public boolean contains(Attributes o, int frame) {
    for (HPSelector selector : filters) {
      if (!selector.matches(o, frame)) {
        return false;
      }
    }
    return true;
  }

  public int compare(Attributes o1, int frame1, Attributes o2, int frame2) {
    int result = 0;
    for (int i = 0, n = cmps.size(); result == 0 && i < n; i++) {
      HPComparator cmp = cmps.get(i);
      result = cmp.compare(o1, frame1, o2, frame2);
    }
    return result;
  }

  public Integer getDisplaySetNumber() {
    return DicomUtils.getIntegerFromDicomElement(dcmItems, Tag.DisplaySetNumber, null);
  }

  public void setDisplaySetNumber(int displaySetNumber) {
    dcmItems.setInt(Tag.DisplaySetNumber, VR.US, displaySetNumber);
  }

  public void setDisplaySetLabel(String label) {
    dcmItems.setString(Tag.DisplaySetLabel, VR.LO, label);
  }

  public String getDisplaySetLabel() {
    return dcmItems.getString(Tag.DisplaySetLabel);
  }

  public Integer getDisplaySetPresentationGroup() {
    return DicomUtils.getIntegerFromDicomElement(dcmItems, Tag.DisplaySetPresentationGroup, null);
  }

  public void setDisplaySetPresentationGroup(int group) {
    dcmItems.setInt(Tag.DisplaySetPresentationGroup, VR.US, group);
  }

  public String getBlendingOperationType() {
    return dcmItems.getString(Tag.BlendingOperationType);
  }

  public void setBlendingOperationType(String type) {
    dcmItems.setString(Tag.BlendingOperationType, VR.CS, type);
  }

  public String getReformattingOperationType() {
    return dcmItems.getString(Tag.ReformattingOperationType);
  }

  public void setReformattingOperationType(String type) {
    dcmItems.setString(Tag.ReformattingOperationType, VR.CS, type);
  }

  public Double getReformattingThickness() {
    return DicomUtils.getDoubleFromDicomElement(dcmItems, Tag.ReformattingThickness, null);
  }

  public void setReformattingThickness(double thickness) {
    dcmItems.setDouble(Tag.ReformattingThickness, VR.FD, thickness);
  }

  public Double getReformattingInterval() {
    return DicomUtils.getDoubleFromDicomElement(dcmItems, Tag.ReformattingInterval, null);
  }

  public void setReformattingInterval(double interval) {
    dcmItems.setDouble(Tag.ReformattingInterval, VR.FD, interval);
  }

  public String getReformattingOperationInitialViewDirection() {
    return dcmItems.getString(Tag.ReformattingOperationInitialViewDirection);
  }

  public void setReformattingOperationInitialViewDirection(String direction) {
    dcmItems.setString(Tag.ReformattingOperationInitialViewDirection, VR.CS, direction);
  }

  public String[] get3DRenderingType() {
    return dcmItems.getStrings(Tag.ThreeDRenderingType);
  }

  public void set3DRenderingType(String[] type) {
    dcmItems.setString(Tag.ThreeDRenderingType, VR.CS, type);
  }

  public String[] getRowDisplaySetPatientOrientation() {
    return dcmItems.getStrings(Tag.DisplaySetPatientOrientation);
  }

  public void setDisplaySetPatientOrientation(Biped row, Biped col) {
    dcmItems.setString(Tag.DisplaySetPatientOrientation, VR.CS, row.name(), col.name());
  }

  public void setDisplaySetPatientOrientation(Quadruped row, Quadruped col) {
    dcmItems.setString(Tag.DisplaySetPatientOrientation, VR.CS, row.name(), col.name());
  }

  public String getVOIType() {
    return dcmItems.getString(Tag.VOIType);
  }

  public void setVOIType(String type) {
    dcmItems.setString(Tag.VOIType, VR.CS, type);
  }

  public String getPseudoColorType() {
    return dcmItems.getString(Tag.PseudoColorType);
  }

  public void setPseudoColorType(String type) {
    dcmItems.setString(Tag.PseudoColorType, VR.CS, type);
  }

  public String getShowGrayscaleInverted() {
    return dcmItems.getString(Tag.ShowGrayscaleInverted);
  }

  public void setShowGrayscaleInverted(String flag) {
    dcmItems.setString(Tag.ShowGrayscaleInverted, VR.CS, flag);
  }

  public String getShowImageTrueSizeFlag() {
    return dcmItems.getString(Tag.ShowImageTrueSizeFlag);
  }

  public void setShowImageTrueSizeFlag(String flag) {
    dcmItems.setString(Tag.ShowImageTrueSizeFlag, VR.CS, flag);
  }

  public String getShowGraphicAnnotationFlag() {
    return dcmItems.getString(Tag.ShowGraphicAnnotationFlag);
  }

  public void setShowGraphicAnnotationFlag(String flag) {
    dcmItems.setString(Tag.ShowGraphicAnnotationFlag, VR.CS, flag);
  }

  public String getShowAcquisitionTechniquesFlag() {
    return dcmItems.getString(Tag.ShowAcquisitionTechniquesFlag);
  }

  public void setShowAcquisitionTechniquesFlag(String flag) {
    dcmItems.setString(Tag.ShowAcquisitionTechniquesFlag, VR.CS, flag);
  }

  public String getDisplaySetPresentationGroupDescription() {
    return dcmItems.getString(Tag.DisplaySetPresentationGroupDescription);
  }

  public void setDisplaySetPresentationGroupDescription(String description) {
    dcmItems.setString(Tag.DisplaySetPresentationGroupDescription, VR.CS, description);
  }
}
