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

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Sequence;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.UID;
import org.dcm4che3.data.VR;
import org.dcm4che3.img.util.DicomUtils;
import org.dcm4che3.io.DicomOutputStream;
import org.dcm4che3.util.UIDUtils;
import org.weasis.core.util.FileUtil;
import org.weasis.core.util.StringUtil;
import org.weasis.dicom.hp.spi.HPCategoryService;
import org.weasis.dicom.hp.spi.HPComparatorCategoryService;
import org.weasis.dicom.hp.spi.HPRegistry;
import org.weasis.dicom.hp.spi.HPSelectorCategoryService;
import org.weasis.dicom.macro.Code;
import org.weasis.dicom.macro.Module;
import org.weasis.dicom.macro.SOPInstanceReference;

public class HangingProtocol extends Module {

  private final List<HPDefinition> definitions = new ArrayList<>();
  private final List<HPScreenDefinition> screenDefs = new ArrayList<>();
  private final List<HPImageSet> imageSets = new ArrayList<>();
  private final List<HPDisplaySet> displaySets = new ArrayList<>();
  private final List<HPScrollingGroup> scrollingGroups = new ArrayList<>();
  private final List<HPNavigationGroup> navigationGroups = new ArrayList<>();
  private int maxPresGroup = 0;

  public HangingProtocol(Attributes attributes) {
    super(attributes);
    init();
  }

  public HangingProtocol() {
    super(new Attributes());
    initDocument(null, null);
  }

  public HangingProtocol(HangingProtocol source) {
    super(new Attributes(source.getAttributes()));
    this.maxPresGroup = source.maxPresGroup;
    initDocument(getSOPClassUID(), getSOPInstanceUID());
    init();
  }

  protected void initDocument(String cuid, String refUid) {
    SOPInstanceReference refSOP = new SOPInstanceReference();
    if (refUid != null) {
      refSOP.setReferencedSOPInstanceUID(refUid);
    }
    refSOP.setReferencedSOPClassUID(cuid != null ? cuid : UID.HangingProtocolStorage);
    setSourceHangingProtocol(refSOP);
    setSOPInstanceUID(UIDUtils.createUID());
  }

  private void init() {
    initHangingProtocolDefinition();
    initNominalScreenDefinition();
    initImageSets();
    initDisplaySets();
    initScrollingGroups();
    initNavigationGroups();
  }

  public void writeToDicomFile(Path outputPath) throws IOException {
    validateOutputPath(outputPath);
    FileUtil.prepareToWriteFile(outputPath);

    try (DicomOutputStream dos = new DicomOutputStream(outputPath.toFile())) {
      Attributes fmi = dcmItems.createFileMetaInformation(UID.ExplicitVRLittleEndian);
      dos.writeDataset(fmi, dcmItems);
    }
  }

  private static void validateOutputPath(Path outputPath) {
    if (StringUtil.hasText(outputPath.toString())) {
      throw new IllegalArgumentException("Output path cannot be null or empty");
    }
  }

  protected HPImageSet createImageSet(List<HPSelector> selectors, Attributes dcmobj) {
    return new HPImageSet(selectors, dcmobj);
  }

  protected HPDisplaySet createDisplaySet(Attributes ds, HPImageSet is) {
    return new HPDisplaySet(ds, is);
  }

  protected HPDefinition createHangingProtocolDefinition(Attributes dcmobj) {
    return new HPDefinition(dcmobj);
  }

  protected HPScreenDefinition createNominalScreenDefinition(Attributes item) {
    return new HPScreenDefinition(item);
  }

  protected HPNavigationGroup createNavigationGroup(Attributes dcmobj) {
    return new HPNavigationGroup(dcmobj, displaySets);
  }

  protected HPScrollingGroup createScrollingGroup(Attributes dssg) {
    return new HPScrollingGroup(dssg, displaySets);
  }

  public String getHangingProtocolName() {
    return dcmItems.getString(Tag.HangingProtocolName);
  }

  public void setHangingProtocolName(String name) {
    dcmItems.setString(Tag.HangingProtocolName, VR.SH, name);
  }

  public String getHangingProtocolDescription() {
    return dcmItems.getString(Tag.HangingProtocolDescription);
  }

  public void setHangingProtocolDescription(String description) {
    dcmItems.setString(Tag.HangingProtocolDescription, VR.LO, description);
  }

  public String getHangingProtocolLevel() {
    return dcmItems.getString(Tag.HangingProtocolLevel);
  }

  public void setHangingProtocolLevel(String level) {
    dcmItems.setString(Tag.HangingProtocolLevel, VR.CS, level);
  }

  public String getHangingProtocolCreator() {
    return dcmItems.getString(Tag.HangingProtocolCreator);
  }

  public void setHangingProtocolCreator(String creator) {
    dcmItems.setString(Tag.HangingProtocolCreator, VR.LO, creator);
  }

  public Date getHangingProtocolCreationDateTime() {
    return dcmItems.getDate(Tag.HangingProtocolCreationDateTime);
  }

  public void setHangingProtocolCreationDateTime(Date datetime) {
    dcmItems.setDate(Tag.HangingProtocolCreationDateTime, VR.DT, datetime);
  }

  public Integer getNumberOfPriorsReferenced() {
    return DicomUtils.getIntegerFromDicomElement(dcmItems, Tag.NumberOfPriorsReferenced, null);
  }

  public void setNumberOfPriorsReferenced(int priors) {
    dcmItems.setInt(Tag.NumberOfPriorsReferenced, VR.US, priors);
  }

  public Integer getNumberOfScreens() {
    return DicomUtils.getIntegerFromDicomElement(dcmItems, Tag.NumberOfScreens, null);
  }

  public void setNumberOfScreens(int screens) {
    dcmItems.setInt(Tag.NumberOfScreens, VR.US, screens);
  }

  public Code getHangingProtocolUserIdentificationCode() {
    return Code.getNestedCode(dcmItems, Tag.HangingProtocolUserIdentificationCodeSequence);
  }

  public void setHangingProtocolUserIdentificationCodeSequence(Code user) {
    dcmItems.ensureSequence(Tag.AbstractPriorCodeSequence, 1).add(user.getAttributes());
  }

  public SOPInstanceReference getSourceHangingProtocol() {
    Attributes item = dcmItems.getNestedDataset(Tag.SourceHangingProtocolSequence);
    return item != null ? new SOPInstanceReference(item) : null;
  }

  public void setSourceHangingProtocol(SOPInstanceReference sop) {
    dcmItems.ensureSequence(Tag.SourceHangingProtocolSequence, 1).add(sop.getAttributes());
  }

  public String getHangingProtocolUserGroupName() {
    return dcmItems.getString(Tag.HangingProtocolUserGroupName);
  }

  public void setHangingProtocolUserGroupName(String name) {
    dcmItems.setString(Tag.HangingProtocolUserGroupName, VR.LO, name);
  }

  public String getPartialDataDisplayHandling() {
    return dcmItems.getString(Tag.PartialDataDisplayHandling);
  }

  public void setPartialDataDisplayHandling(String type) {
    dcmItems.setString(Tag.PartialDataDisplayHandling, VR.CS, type);
  }

  public List<HPDefinition> getHangingProtocolDefinitions() {
    return Collections.unmodifiableList(definitions);
  }

  public void addHangingProtocolDefinition(HPDefinition def) {
    if (def == null) {
      throw new NullPointerException();
    }
    dcmItems.ensureSequence(Tag.HangingProtocolDefinitionSequence, 1).add(def.getAttributes());
    definitions.add(def);
  }

  public boolean removeHangingProtocolDefinition(HPDefinition def) {
    if (def == null) {
      throw new NullPointerException();
    }

    int index = definitions.indexOf(def);
    if (index == -1) {
      return false;
    }

    dcmItems.ensureSequence(Tag.HangingProtocolDefinitionSequence, 1).add(def.getAttributes());
    definitions.remove(index);
    return true;
  }

  public void removeAllHangingProtocolDefinition() {
    removeAllSequenceItems(Tag.HangingProtocolDefinitionSequence);
    definitions.clear();
  }

  public List<HPImageSet> getImageSets() {
    return Collections.unmodifiableList(imageSets);
  }

  public HPImageSet addNewImageSet(HPImageSet shareSelectors) {
    int number = imageSets.size() + 1;
    HPImageSet is;
    if (shareSelectors != null) {
      is = new HPImageSet(shareSelectors, number);
    } else {
      is = new HPImageSet(number);
    }
    is.setImageSetNumber(imageSets.size() + 1);
    imageSets.add(is);
    return is;
  }

  public boolean removeImageSet(HPImageSet imageSet) {
    if (imageSet == null) {
      throw new NullPointerException();
    }

    int index = imageSets.indexOf(imageSet);
    if (index == -1) {
      return false;
    }

    for (HPDisplaySet hpDisplaySet : getDisplaySetsOfImageSet(imageSet)) {
      removeDisplaySet(hpDisplaySet);
    }

    Attributes tbis = imageSet.getAttributes();
    Attributes is = tbis.getParent();
    Sequence tbissq = is.getSequence(Tag.TimeBasedImageSetsSequence);
    if (tbissq != null) {
      tbissq.remove(tbis);
      if (tbissq.isEmpty()) {
        removeSequenceItem(Tag.ImageSetsSequence, is);
      }
    }
    imageSets.remove(index);

    for (; index < imageSets.size(); ++index) {
      HPImageSet otherImageSet = imageSets.get(index);
      otherImageSet.setImageSetNumber(index + 1);
      for (Iterator<HPDisplaySet> iter = getDisplaySetsOfImageSet(otherImageSet).iterator();
          iter.hasNext(); ) {
        iter.next().setImageSet(otherImageSet);
      }
    }

    return true;
  }

  public void removeAllImageSets() {
    removeAllSequenceItems(Tag.ImageSetsSequence);
    imageSets.clear();
    removeAllDisplaySets();
  }

  public List<HPScreenDefinition> getNominalScreenDefinitions() {
    return Collections.unmodifiableList(screenDefs);
  }

  public void addNominalScreenDefinition(HPScreenDefinition def) {
    if (def == null) {
      throw new NullPointerException();
    }

    dcmItems.ensureSequence(Tag.NominalScreenDefinitionSequence, 1).add(def.getAttributes());
    screenDefs.add(def);
  }

  public boolean removeNominalScreenDefinition(HPScreenDefinition def) {
    if (def == null) {
      throw new NullPointerException();
    }

    int index = screenDefs.indexOf(def);
    if (index == -1) {
      return false;
    }
    removeSequenceItem(Tag.NominalScreenDefinitionSequence, index);
    screenDefs.remove(index);
    return true;
  }

  public void removeAllNominalScreenDefinitions() {
    removeAllSequenceItems(Tag.NominalScreenDefinitionSequence);
    screenDefs.clear();
  }

  public int getNumberOfPresentationGroups() {
    return maxPresGroup;
  }

  public List<HPDisplaySet> getDisplaySetsOfPresentationGroup(int pgNo) {
    ArrayList<HPDisplaySet> result = new ArrayList<>(displaySets.size());
    for (HPDisplaySet ds : displaySets) {
      if (ds.getDisplaySetPresentationGroup() == pgNo) {
        result.add(ds);
      }
    }
    return result;
  }

  public List<HPDisplaySet> getDisplaySetsOfImageSet(HPImageSet is) {
    ArrayList<HPDisplaySet> result = new ArrayList<HPDisplaySet>(displaySets.size());
    for (HPDisplaySet ds : displaySets) {
      if (ds.getImageSet() == is) {
        result.add(ds);
      }
    }
    return result;
  }

  public String getDisplaySetPresentationGroupDescription(int pgNo) {
    for (int i = 0, n = displaySets.size(); i < n; i++) {
      HPDisplaySet ds = displaySets.get(i);
      if (ds.getDisplaySetPresentationGroup() == pgNo) {
        String desc = ds.getDisplaySetPresentationGroupDescription();
        if (desc != null) {
          return desc;
        }
      }
    }
    return null;
  }

  public List<HPDisplaySet> getDisplaySets() {
    return Collections.unmodifiableList(displaySets);
  }

  public HPDisplaySet addNewDisplaySet(HPImageSet imageSet, HPDisplaySet prototype) {
    if (imageSet == null) {
      throw new NullPointerException("imageSet");
    }
    if (!imageSets.contains(imageSet)) {
      throw new IllegalArgumentException("imageSet does not belongs to this HP object");
    }
    Attributes dcmobj = new Attributes();
    if (prototype != null) {
      dcmobj.addAll(prototype.getAttributes());
    }
    dcmobj.setInt(Tag.ImageSetNumber, VR.US, imageSet.getImageSetNumber());
    HPDisplaySet displaySet = createDisplaySet(dcmobj, imageSet);
    doAddDisplaySet(displaySet);
    return displaySet;
  }

  @Deprecated
  public void addDisplaySet(HPDisplaySet displaySet) {
    if (displaySet == null) {
      throw new NullPointerException("displaySet");
    }

    doAddDisplaySet(displaySet);
  }

  protected void doAddDisplaySet(HPDisplaySet displaySet) {
    displaySet.setDisplaySetNumber(displaySets.size() + 1);
    int group = displaySet.getDisplaySetPresentationGroup();
    if (group == 0) {
      group = Math.max(maxPresGroup, 1);
      displaySet.setDisplaySetPresentationGroup(group);
    }
    maxPresGroup = Math.max(maxPresGroup, group);
    dcmItems.ensureSequence(Tag.DisplaySetsSequence, 1).add(displaySet.getAttributes());
    displaySets.add(displaySet);
  }

  public boolean removeDisplaySet(HPDisplaySet displaySet) {
    if (displaySet == null) {
      throw new NullPointerException();
    }

    int index = displaySets.indexOf(displaySet);
    if (index == -1) {
      return false;
    }

    removeSequenceItem(Tag.DisplaySetsSequence, index);
    displaySets.remove(index);

    for (; index < displaySets.size(); ++index) {
      displaySets.get(index).setDisplaySetNumber(index + 1);
    }

    int sgi = 0;
    for (Iterator<HPScrollingGroup> iter = scrollingGroups.iterator(); iter.hasNext(); ++sgi) {
      HPScrollingGroup sg = iter.next();
      if (sg.removeDisplaySet(displaySet) && !sg.isValid()) {
        removeSequenceItem(Tag.SynchronizedScrollingSequence, sgi--);
        iter.remove();
      } else {
        sg.updateAttributes();
      }
    }

    int ngi = 0;
    for (Iterator<HPNavigationGroup> iter = navigationGroups.iterator(); iter.hasNext(); ++ngi) {
      HPNavigationGroup ng = iter.next();
      if (ng.removeReferenceDisplaySet(displaySet) && !ng.isValid()
          || ng.getNavigationDisplaySet() == displaySet) {
        if (ng.getNavigationDisplaySet() == displaySet) {
          ng.setNavigationDisplaySet(null);
        }
        removeSequenceItem(Tag.NavigationIndicatorSequence, ngi--);
        iter.remove();
      } else {
        ng.updateAttributes();
      }
    }

    return true;
  }

  public void removeAllDisplaySets() {
    removeAllSequenceItems(Tag.DisplaySetsSequence);
    displaySets.clear();
    removeAllScrollingGroups();
    removeAllNavigationGroups();
    maxPresGroup = 0;
  }

  public List<HPScrollingGroup> getScrollingGroups() {
    return maskNull(scrollingGroups);
  }

  public void addScrollingGroup(HPScrollingGroup scrollingGroup) {
    dcmItems
        .ensureSequence(Tag.SynchronizedScrollingSequence, 1)
        .add(scrollingGroup.getAttributes());
    scrollingGroups.add(scrollingGroup);
  }

  public boolean removeScrollingGroup(HPScrollingGroup scrollingGroup) {
    if (scrollingGroup == null) {
      throw new NullPointerException();
    }

    int index = scrollingGroups.indexOf(scrollingGroup);
    if (index == -1) {
      return false;
    }

    removeSequenceItem(Tag.SynchronizedScrollingSequence, index);
    scrollingGroups.remove(index);
    return true;
  }

  public void removeAllScrollingGroups() {
    dcmItems.remove(Tag.SynchronizedScrollingSequence);
    scrollingGroups.clear();
  }

  public List<HPNavigationGroup> getNavigationGroups() {
    return maskNull(navigationGroups);
  }

  private <T> List<T> maskNull(List<T> list) {
    return list == null ? Collections.<T>emptyList() : Collections.unmodifiableList(list);
  }

  public void addNavigationGroup(HPNavigationGroup navigationGroup) {
    dcmItems
        .ensureSequence(Tag.NavigationIndicatorSequence, 1)
        .add(navigationGroup.getAttributes());

    navigationGroups.add(navigationGroup);
  }

  public boolean removeNavigationGroup(HPNavigationGroup navigationGroup) {
    if (navigationGroup == null) {
      throw new NullPointerException();
    }

    int index = navigationGroups.indexOf(navigationGroup);
    if (index == -1) {
      return false;
    }

    removeSequenceItem(Tag.NavigationIndicatorSequence, index);
    navigationGroups.remove(index);
    return true;
  }

  public void removeAllNavigationGroups() {
    dcmItems.remove(Tag.NavigationIndicatorSequence);
    navigationGroups.clear();
  }

  private void initNavigationGroups() {
    Sequence nis = dcmItems.getSequence(Tag.NavigationIndicatorSequence);
    if (nis == null || nis.isEmpty()) {
      return;
    }

    for (Attributes ni : nis) {
      navigationGroups.add(createNavigationGroup(ni));
    }
  }

  private void initScrollingGroups() {
    Sequence ssq = dcmItems.getSequence(Tag.SynchronizedScrollingSequence);
    if (ssq == null || ssq.isEmpty()) {
      return;
    }

    for (Attributes value : ssq) {
      scrollingGroups.add(createScrollingGroup(value));
    }
  }

  private void initDisplaySets() {
    Sequence dssq = dcmItems.getSequence(Tag.DisplaySetsSequence);
    if (dssq == null || dssq.isEmpty()) {
      return;
    }

    for (Attributes ds : dssq) {
      if (ds.getInt(Tag.DisplaySetNumber, 0) != displaySets.size() + 1) {
        throw new IllegalArgumentException(
            "Missing or invalid (0072,0202) Display Set Number: "
                + ds.getString(Tag.DisplaySetNumber));
      }
      final int dspg = ds.getInt(Tag.DisplaySetPresentationGroup, 0);
      if (dspg == 0) {
        throw new IllegalArgumentException(
            "Missing or invalid (0072,0204) Display Set Presentation Group: "
                + ds.getString(Tag.DisplaySetPresentationGroup));
      }
      maxPresGroup = Math.max(maxPresGroup, dspg);
      HPImageSet is;
      try {
        is = imageSets.get(ds.getInt(Tag.ImageSetNumber, 0) - 1);
      } catch (IndexOutOfBoundsException e) {
        throw new IllegalArgumentException(
            "Missing or invalid (0072,0032) Image Set Number: " + ds.getString(Tag.ImageSetNumber));
      }
      displaySets.add(createDisplaySet(ds, is));
    }
  }

  private void initImageSets() {
    Sequence issq = dcmItems.getSequence(Tag.ImageSetsSequence);
    if (issq == null || issq.isEmpty()) {
      return;
    }

    for (Attributes is : issq) {
      Sequence isssq = is.getSequence(Tag.ImageSetSelectorSequence);
      if (isssq == null) {
        throw new IllegalArgumentException("Missing (0072,0022) Image Set Selector Sequence");
      }

      int isssqCount = isssq.size();
      List<HPSelector> selectors = new ArrayList<>(isssqCount);
      for (Attributes value : isssq) {
        selectors.add(HPSelectorFactory.createImageSetSelector(value));
      }
      Sequence tbissq = is.getSequence(Tag.TimeBasedImageSetsSequence);
      if (tbissq == null) {
        throw new IllegalArgumentException("Missing (0072,0030) Time Based Image Sets Sequence");
      }

      for (Attributes timeBasedSelector : tbissq) {
        if (timeBasedSelector.getInt(Tag.ImageSetNumber, 0) != imageSets.size() + 1) {
          throw new IllegalArgumentException(
              "Missing or invalid (0072,0032) Image Set Number: "
                  + timeBasedSelector.getString(Tag.ImageSetNumber));
        }
        imageSets.add(createImageSet(selectors, timeBasedSelector));
      }
    }
  }

  private void initNominalScreenDefinition() {
    Sequence nsdsq = dcmItems.getSequence(Tag.NominalScreenDefinitionSequence);
    if (nsdsq == null || nsdsq.isEmpty()) {
      return;
    }

    for (Attributes value : nsdsq) {
      screenDefs.add(createNominalScreenDefinition(value));
    }
  }

  private void initHangingProtocolDefinition() {
    Sequence defsq = dcmItems.getSequence(Tag.HangingProtocolDefinitionSequence);
    if (defsq == null || defsq.isEmpty()) {
      return;
    }

    for (Attributes value : defsq) {
      definitions.add(createHangingProtocolDefinition(value));
    }
  }

  public static HPSelectorCategoryService getHPSelectorSpi(String category) {
    return (HPSelectorCategoryService) getHPCategorySpi(HPSelectorCategoryService.class, category);
  }

  public static HPComparatorCategoryService getHPComparatorSpi(String category) {
    return (HPComparatorCategoryService)
        getHPCategorySpi(HPComparatorCategoryService.class, category);
  }

  private static <T extends HPCategoryService> HPCategoryService getHPCategorySpi(
      Class<T> serviceClass, final String category) {
    Iterator<HPCategoryService> iter = HPRegistry.getHPRegistry().getServiceProviders(serviceClass);
    return iter.hasNext() ? iter.next() : null;
  }

  public static String[] getSupportedHPSelectorCategories() {
    return getSupportedHPCategories(HPSelectorCategoryService.class);
  }

  public static String[] getSupportedHPComparatorCategories() {
    return getSupportedHPCategories(HPComparatorCategoryService.class);
  }

  private static <T extends HPCategoryService> String[] getSupportedHPCategories(
      Class<T> serviceClass) {
    Iterator<HPCategoryService> iter = HPRegistry.getHPRegistry().getServiceProviders(serviceClass);
    HashSet<String> set = new HashSet<>();
    while (iter.hasNext()) {
      HPCategoryService spi = iter.next();
      String ss = spi.getCategoryName();
      set.add(ss);
    }
    return set.toArray(new String[0]);
  }

  public String getSOPClassUID() {
    return dcmItems.getString(Tag.SOPClassUID);
  }

  public void setSOPClassUID(String uid) {
    dcmItems.setString(Tag.SOPClassUID, VR.UI, uid);
  }

  public String getSOPInstanceUID() {
    return dcmItems.getString(Tag.SOPInstanceUID);
  }

  public void setSOPInstanceUID(String uid) {
    dcmItems.setString(Tag.SOPInstanceUID, VR.UI, uid);
  }

  public String[] getSpecificCharacterSet() {
    return dcmItems.getStrings(Tag.SpecificCharacterSet);
  }

  public void setSpecificCharacterSet(String[] ss) {
    dcmItems.setString(Tag.SpecificCharacterSet, VR.CS, ss);
  }

  public Date getInstanceCreationDateTime() {
    return dcmItems.getDate(Tag.InstanceCreationDate, Tag.InstanceCreationTime);
  }

  public void setInstanceCreationDateTime(Date d) {
    dcmItems.setDate(Tag.InstanceCreationDate, VR.DA, d);
    dcmItems.setDate(Tag.InstanceCreationTime, VR.TM, d);
  }

  public String getInstanceCreatorUID() {
    return dcmItems.getString(Tag.InstanceCreatorUID);
  }

  public void setInstanceCreatorUID(String s) {
    dcmItems.setString(Tag.InstanceCreatorUID, VR.UI, s);
  }

  public String getRelatedGeneralSOPClassUID() {
    return dcmItems.getString(Tag.RelatedGeneralSOPClassUID);
  }

  public void setRelatedGeneralSOPClassUID(String s) {
    dcmItems.setString(Tag.RelatedGeneralSOPClassUID, VR.UI, s);
  }

  public String getOriginalSpecializedSOPClassUID() {
    return dcmItems.getString(Tag.OriginalSpecializedSOPClassUID);
  }

  public void setOriginalSpecializedSOPClassUID(String s) {
    dcmItems.setString(Tag.OriginalSpecializedSOPClassUID, VR.UI, s);
  }

  public String getInstanceNumber() {
    return dcmItems.getString(Tag.InstanceNumber);
  }

  public void setInstanceNumber(String s) {
    dcmItems.setString(Tag.InstanceNumber, VR.IS, s);
  }
}
