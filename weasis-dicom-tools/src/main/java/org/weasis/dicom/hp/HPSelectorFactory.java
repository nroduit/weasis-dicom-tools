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

import java.util.Arrays;
import java.util.Collection;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.ElementDictionary;
import org.dcm4che3.data.Sequence;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.VR;
import org.dcm4che3.util.TagUtils;
import org.weasis.dicom.geom.ImageOrientation.Plan;
import org.weasis.dicom.hp.filter.FilterOp;
import org.weasis.dicom.hp.filter.HPFilter;
import org.weasis.dicom.hp.plugins.ImagePlaneSelector;
import org.weasis.dicom.hp.spi.HPComparatorCategoryService;
import org.weasis.dicom.hp.spi.HPSelectorCategoryService;
import org.weasis.dicom.macro.Code;

/**
 * Factory for creating Image Set Selector and Display Set Filter from corresponding Sequence Items.
 *
 * @see HPSelector
 * @see HPComparator
 * @see HPSelectorCategoryService
 * @see HPComparatorCategoryService
 * @see ImagePlaneSelector
 * @see Code
 * @see FilterOp
 * @see CodeString
 * @see HangingProtocol
 */
public class HPSelectorFactory {

  /**
   * Selector Value Number constant for indicating that the frame number shall be used for indexing
   * the value of the Selector Attribute for filtering.
   */
  public static final int FRAME_INDEX = 0xffff;

  public static HPSelector createImageSetSelector(Attributes item) {
    String usageFlag = item.getString(Tag.ImageSetSelectorUsageFlag);
    if (usageFlag == null) {
      throw new IllegalArgumentException("Missing (0072,0024) Image Set Selector Usage Flag");
    }
    HPSelector sel = createAttributeValueSelector(item, isMatch(usageFlag), FilterOp.MEMBER_OF);
    sel = addSequencePointer(sel);
    sel = addFunctionalGroupPointer(sel);
    return sel;
  }

  private static boolean isMatch(String usageFlag) {
    if (usageFlag.equals(CodeString.MATCH)) {
      return true;
    }

    if (usageFlag.equals(CodeString.NO_MATCH)) {
      return false;
    }

    throw new IllegalArgumentException(
        "Invalid (0072,0024) Image Set Selector Usage Flag: " + usageFlag);
  }

  public static HPSelector createAttributeValueSelector(
      String usageFlag, String privateCreator, int tag, int valueNumber, VR vr, String[] values) {
    return createAttributeValueSelector(
        usageFlag, privateCreator, tag, valueNumber, vr, values, null);
  }

  public static HPSelector createAttributeValueSelector(
      String usageFlag, String privateCreator, int tag, int valueNumber, VR vr, int[] values) {
    return createAttributeValueSelector(
        usageFlag, privateCreator, tag, valueNumber, vr, values, null);
  }

  public static HPSelector createAttributeValueSelector(
      String usageFlag, String privateCreator, int tag, int valueNumber, VR vr, float[] values) {
    return createAttributeValueSelector(
        usageFlag, privateCreator, tag, valueNumber, vr, values, null);
  }

  public static HPSelector createAttributeValueSelector(
      String usageFlag, String privateCreator, int tag, int valueNumber, double[] values) {
    return createAttributeValueSelector(usageFlag, privateCreator, tag, valueNumber, values, null);
  }

  public static HPSelector createCodeValueSelector(
      String usageFlag, String privateCreator, int tag, int valueNumber, Code[] values) {
    return new CodeValueSelector(tag, privateCreator, valueNumber, usageFlag, null, values);
  }

  public static HPSelector createDisplaySetFilter(Attributes item) {
    if (item.containsValue(Tag.FilterByCategory)) {
      return HPSelectorFactory.createFilterByCategory(item);
    }
    HPSelector sel = createDisplaySetSelector(item);
    sel = addSequencePointer(sel);
    sel = addFunctionalGroupPointer(sel);
    return sel;
  }

  private static HPSelector createFilterByCategory(Attributes filterOp) {
    HPSelectorCategoryService spi =
        HangingProtocol.getHPSelectorSpi(filterOp.getString(Tag.FilterByCategory));
    if (spi == null) {
      throw new IllegalArgumentException(
          "Unsupported Filter-by Category: " + filterOp.getString(Tag.FilterByCategory));
    }
    return spi.createHPSelector(filterOp);
  }

  public static HPSelector createImagePlaneSelector(Plan[] imagePlanes) {
    return new ImagePlaneSelector(imagePlanes);
  }

  public static HPSelector createAttributePresenceSelector(
      String privateCreator, int tag, String filter) {
    return new AttributePresenceSelector(filter, tag, privateCreator);
  }

  public static HPSelector createAttributeValueSelector(
      String privateCreator, int tag, int valueNumber, VR vr, String[] values, HPFilter filterOp) {
    return createAttributeValueSelector(
        null, privateCreator, tag, valueNumber, vr, values, filterOp);
  }

  public static HPSelector createAttributeValueSelector(
      String privateCreator, int tag, int valueNumber, VR vr, int[] values, HPFilter filterOp) {
    return createAttributeValueSelector(
        null, privateCreator, tag, valueNumber, vr, values, filterOp);
  }

  public static HPSelector createAttributeValueSelector(
      String privateCreator, int tag, int valueNumber, VR vr, float[] values, HPFilter filterOp) {
    return createAttributeValueSelector(
        null, privateCreator, tag, valueNumber, vr, values, filterOp);
  }

  public static HPSelector createAttributeValueSelector(
      String privateCreator, int tag, int valueNumber, double[] values, HPFilter filterOp) {
    return createAttributeValueSelector(null, privateCreator, tag, valueNumber, values, filterOp);
  }

  public static HPSelector createCodeValueSelector(
      String privateCreator, int tag, int valueNumber, Code[] values, HPFilter filterOp) {
    return new CodeValueSelector(tag, privateCreator, valueNumber, null, filterOp, values);
  }

  public static HPSelector addSequencePointer(String privCreator, int tag, HPSelector selector) {
    if (tag == 0) {
      return selector;
    }

    AttributeSelector attrSel = (AttributeSelector) selector;

    if (selector.getSelectorSequencePointer() != null) {
      throw new IllegalArgumentException("Sequence Pointer already added");
    }

    if (selector.getFunctionalGroupPointer() != null) {
      throw new IllegalArgumentException("Functional Group Pointer already added");
    }

    selector.getAttributes().setInt(Tag.SelectorSequencePointer, VR.AT, tag);
    if (privCreator != null) {
      selector
          .getAttributes()
          .setString(Tag.SelectorSequencePointerPrivateCreator, VR.LO, privCreator);
    }

    return new Seq(tag, privCreator, attrSel);
  }

  public static HPSelector addFunctionalGroupPointer(
      String privCreator, int tag, HPSelector selector) {
    if (tag == 0) {
      return selector;
    }

    AttributeSelector attrSel = (AttributeSelector) selector;

    if (selector.getFunctionalGroupPointer() != null) {
      throw new IllegalArgumentException("Functional Group Pointer already added");
    }

    selector.getAttributes().setInt(Tag.FunctionalGroupPointer, VR.AT, tag);
    if (privCreator != null) {
      selector.getAttributes().setString(Tag.FunctionalGroupPrivateCreator, VR.LO, privCreator);
    }

    return new FctGrp(tag, privCreator, attrSel);
  }

  private static HPSelector addSequencePointer(HPSelector sel) {
    Integer seqTag = sel.getSelectorSequencePointer();
    if (seqTag != null && seqTag != 0) {
      String privCreator = sel.getSelectorSequencePointerPrivateCreator();
      sel = new Seq(seqTag, privCreator, (AttributeSelector) sel);
    }
    return sel;
  }

  private static HPSelector addFunctionalGroupPointer(HPSelector sel) {
    Integer fgTag = sel.getFunctionalGroupPointer();
    if (fgTag != null && fgTag != 0) {
      String privCreator = sel.getFunctionalGroupPrivateCreator();
      sel = new FctGrp(fgTag, privCreator, (AttributeSelector) sel);
    }
    return sel;
  }

  private static HPSelector createAttributeValueSelector(
      Attributes item, boolean match, HPFilter filterOp) {
    String vrStr = item.getString(Tag.SelectorAttributeVR);
    if (vrStr == null) {
      throw new IllegalArgumentException("Missing (0072,0050) Selector Attribute VR");
    }

    return switch (CodeString.getVR(vrStr)) {
      case AT -> new IntSelector(item, Tag.SelectorATValue, match, filterOp, VR.AT);
      case CS -> new Str(item, Tag.SelectorCSValue, match, filterOp, VR.CS);
      case DS -> new Flt(item, Tag.SelectorDSValue, match, filterOp, VR.DS);
      case FD -> new Dbl(item, Tag.SelectorFDValue, match, filterOp, VR.FD);
      case FL -> new Flt(item, Tag.SelectorFLValue, match, filterOp, VR.FL);
      case IS -> new IntSelector(item, Tag.SelectorISValue, match, filterOp, VR.IS);
      case LO -> new Str(item, Tag.SelectorLOValue, match, filterOp, VR.LO);
      case LT -> new Str(item, Tag.SelectorLTValue, match, filterOp, VR.LT);
      case PN -> new Str(item, Tag.SelectorPNValue, match, filterOp, VR.PN);
      case SH -> new Str(item, Tag.SelectorSHValue, match, filterOp, VR.SH);
      case SL -> new IntSelector(item, Tag.SelectorSLValue, match, filterOp, VR.SL);
      case SQ -> new CodeValueSelector(item, match, filterOp, VR.SQ);
      case SS -> new IntSelector(item, Tag.SelectorSSValue, match, filterOp, VR.SS);
      case ST -> new Str(item, Tag.SelectorSTValue, match, filterOp, VR.ST);
      case UL -> new UIntSelector(item, Tag.SelectorULValue, match, filterOp, VR.UL);
      case US -> new IntSelector(item, Tag.SelectorUSValue, match, filterOp, VR.US);
      case UT -> new Str(item, Tag.SelectorUTValue, match, filterOp, VR.UT);
      default -> throw new IllegalArgumentException("vr: " + vrStr);
    };
  }

  private static HPSelector createDisplaySetSelector(Attributes item) {
    if (item.containsValue(Tag.FilterByAttributePresence)) {
      return new AttributePresenceSelector(item);
    }

    String filterOp = item.getString(Tag.FilterByOperator);
    if (filterOp == null) {
      throw new IllegalArgumentException("Missing (0072,0406) Filter-by Operator");
    }
    try {
      HPFilter filter = FilterOp.valueOf(filterOp);
      String usageFlag = item.getString(Tag.ImageSetSelectorUsageFlag);
      return createAttributeValueSelector(
          item, usageFlag != null ? isMatch(usageFlag) : true, filter);
    } catch (IllegalArgumentException e) {
      throw new IllegalArgumentException("Illegal (0072,0406) Filter-by Operator: " + filterOp);
    }
  }

  private abstract static class AttributeSelector extends AbstractHPSelector {

    protected final int tag;

    protected final String privateCreator;

    protected final boolean match;

    AttributeSelector(int tag, String privateCreator, boolean match) {
      this.tag = tag;
      this.privateCreator = privateCreator;
      this.match = match;
    }

    public final boolean isMatchIfNotPresent() {
      return match;
    }
  }

  private static int getSelectorAttribute(Attributes item) {
    int tag = item.getInt(Tag.SelectorAttribute, 0);
    if (tag == 0) {
      throw new IllegalArgumentException("Missing (0072,0026) Selector Attribute");
    }
    return tag;
  }

  private abstract static class BaseAttributeSelector extends AttributeSelector {

    protected final Attributes item;

    BaseAttributeSelector(Attributes item, boolean match) {
      super(
          HPSelectorFactory.getSelectorAttribute(item),
          item.getString(Tag.SelectorAttributePrivateCreator),
          match);
      this.item = item;
    }

    BaseAttributeSelector(int tag, String privateCreator, boolean match) {
      super(tag, privateCreator, match);
      item = new Attributes();
      item.setInt(Tag.SelectorAttribute, VR.AT, tag);
      if (privateCreator != null) {
        item.setString(Tag.SelectorAttributePrivateCreator, VR.LO, privateCreator);
      }
    }

    public final Attributes getAttributes() {
      return item;
    }
  }

  private static boolean isPresent(String val) {
    if (val.equals(CodeString.PRESENT)) {
      return true;
    }
    if (val.equals(CodeString.NOT_PRESENT)) {
      return false;
    }
    throw new IllegalArgumentException("Illegal (0072,0404) Filter-by Attribute Presence: " + val);
  }

  private static class AttributePresenceSelector extends BaseAttributeSelector {

    AttributePresenceSelector(Attributes item) {
      super(item, !isPresent(item.getString(Tag.FilterByAttributePresence)));
    }

    AttributePresenceSelector(String filter, int tag, String privateCreator) {
      super(tag, privateCreator, !isPresent(filter));
      item.setString(Tag.FilterByAttributePresence, VR.CS, filter);
    }

    public boolean matches(Attributes dcmobj, int frame) {
      return dcmobj.containsValue(privateCreator, tag) != match;
    }
  }

  private abstract static class AttributeValueSelector extends BaseAttributeSelector {

    protected final int valueNumber;

    protected final HPFilter filterOp;

    protected final VR vr;

    AttributeValueSelector(Attributes item, boolean match, HPFilter filterOp, VR vr) {
      super(item, match);
      this.valueNumber = item.getInt(Tag.SelectorValueNumber, 0);
      this.filterOp = filterOp;
      this.vr = vr;
    }

    AttributeValueSelector(
        int tag,
        String privateCreator,
        int valueNumber,
        String usageFlag,
        HPFilter filterOp,
        VR vr) {
      super(tag, privateCreator, usageFlag == null || isMatch(usageFlag));
      this.valueNumber = valueNumber;
      this.filterOp = filterOp != null ? filterOp : FilterOp.MEMBER_OF;
      this.vr = vr;
      item.setInt(Tag.SelectorValueNumber, VR.US, valueNumber);
      if (filterOp != null) {
        item.setString(Tag.FilterByOperator, VR.CS, filterOp.getCodeString());
      }
      item.setString(Tag.SelectorAttributeVR, VR.CS, vr.toString());
      if (usageFlag != null) {
        item.setString(Tag.ImageSetSelectorUsageFlag, VR.CS, usageFlag);
      }
    }
  }

  private static HPSelector createAttributeValueSelector(
      String usageFlag,
      String privateCreator,
      int tag,
      int valueNumber,
      VR vr,
      String[] values,
      HPFilter filterOp) {

    VR vr1 = vr != null ? vr : ElementDictionary.vrOf(tag, privateCreator);
    int valueTag =
        switch (vr1) {
          case CS -> Tag.SelectorCSValue;
          case LO -> Tag.SelectorLOValue;
          case LT -> Tag.SelectorLTValue;
          case PN -> Tag.SelectorPNValue;
          case SH -> Tag.SelectorSHValue;
          case ST -> Tag.SelectorSTValue;
          case UT -> Tag.SelectorUTValue;
          default -> throw new IllegalArgumentException("vr: " + vr);
        };
    return new Str(tag, privateCreator, valueNumber, valueTag, usageFlag, filterOp, vr, values);
  }

  private static class Str extends AttributeValueSelector {

    protected final String[] params;

    Str(Attributes item, int valueTag, boolean match, HPFilter filterOp, VR vr) {
      super(item, match, filterOp, vr);
      if (filterOp.isNumeric()) {
        throw new IllegalArgumentException(
            "Filter-by Operator: "
                + item.getString(Tag.FilterByOperator)
                + " conflicts with non-numeric VR: "
                + item.getString(Tag.SelectorAttributeVR));
      }

      this.params = item.getStrings(valueTag);
      if (params == null || params.length == 0) {
        throw new IllegalArgumentException("Missing " + TagUtils.toString(valueTag));
      }
    }

    Str(
        int tag,
        String privateCreator,
        int valueNumber,
        int valueTag,
        String usageFlag,
        HPFilter filterOp,
        VR vr,
        String[] values) {
      super(tag, privateCreator, valueNumber, usageFlag, filterOp, vr);
      this.params = values.clone();
      item.setString(valueTag, vr, values);
    }

    public boolean matches(Attributes dcmobj, int frame) {
      String[] values = dcmobj.getStrings(privateCreator, tag, vr);
      if (values == null || values.length < Math.max(valueNumber, 1)) {
        return match;
      }
      return filterOp.op(values, valueNumber, params);
    }
  }

  public static HPSelector createAttributeValueSelector(
      String usageFlag,
      String privateCreator,
      int tag,
      int valueNumber,
      VR vr,
      int[] values,
      HPFilter filterOp) {
    VR vr1 = vr != null ? vr : ElementDictionary.vrOf(tag, privateCreator);
    int valueTag;
    boolean uint = false;
    switch (vr1) {
      case AT:
        valueTag = Tag.SelectorATValue;
        break;
      case IS:
        valueTag = Tag.SelectorISValue;
        break;
      case SL:
        valueTag = Tag.SelectorSLValue;
        break;
      case SS:
        valueTag = Tag.SelectorSSValue;
        break;
      case UL:
        valueTag = Tag.SelectorULValue;
        uint = true;
        break;
      case US:
        valueTag = Tag.SelectorUSValue;
        break;
      default:
        throw new IllegalArgumentException("vr: " + vr);
    }
    return uint
        ? new UIntSelector(
            tag, privateCreator, valueNumber, valueTag, usageFlag, filterOp, vr, values)
        : new IntSelector(
            tag, privateCreator, valueNumber, valueTag, usageFlag, filterOp, vr, values);
  }

  private static class IntSelector extends AttributeValueSelector {

    private final int[] params;

    IntSelector(Attributes item, int valueTag, boolean match, HPFilter filterOp, VR vr) {
      super(item, match, filterOp, vr);
      this.params = item.getInts(valueTag);
      if (params == null || params.length == 0) {
        throw new IllegalArgumentException("Missing " + TagUtils.toString(valueTag));
      }
      if (filterOp.isNumeric() && filterOp.getNumParams() != params.length) {
        throw new IllegalArgumentException("Illegal Number of values: " + item.getString(valueTag));
      }
    }

    IntSelector(
        int tag,
        String privateCreator,
        int valueNumber,
        int valueTag,
        String usageFlag,
        HPFilter filterOp,
        VR vr,
        int[] values) {
      super(tag, privateCreator, valueNumber, usageFlag, filterOp, vr);
      this.params = values.clone();
      item.setInt(valueTag, vr, values);
    }

    public boolean matches(Attributes dcmobj, int frame) {
      int[] values = dcmobj.getInts(privateCreator, tag, vr);
      if (values == null
          || values.length
              < (valueNumber == 0 ? 1 : valueNumber == FRAME_INDEX ? frame : valueNumber)) {
        return match;
      }
      return filterOp.op(values, valueNumber == FRAME_INDEX ? frame : valueNumber, params);
    }
  }

  private static long[] toLong(int[] is) {
    long[] ls = new long[is.length];
    for (int i = 0; i < is.length; i++) {
      ls[i] = is[i] & 0xffffffffL;
    }
    return ls;
  }

  private static class UIntSelector extends AttributeValueSelector {

    private final long[] params;

    UIntSelector(Attributes item, int valueTag, boolean match, HPFilter filterOp, VR vr) {
      super(item, match, filterOp, vr);
      int[] tmp = item.getInts(valueTag);
      if (tmp == null || tmp.length == 0) {
        throw new IllegalArgumentException("Missing " + TagUtils.toString(valueTag));
      }
      if (filterOp.isNumeric() && filterOp.getNumParams() != tmp.length) {
        throw new IllegalArgumentException("Illegal Number of values: " + item.getString(valueTag));
      }
      this.params = toLong(tmp);
    }

    UIntSelector(
        int tag,
        String privateCreator,
        int valueNumber,
        int valueTag,
        String usageFlag,
        HPFilter filterOp,
        VR vr,
        int[] values) {
      super(tag, privateCreator, valueNumber, usageFlag, filterOp, vr);
      this.params = toLong(values);
      item.setInt(valueTag, vr, values);
    }

    public boolean matches(Attributes dcmobj, int frame) {
      int[] values = dcmobj.getInts(privateCreator, tag, vr);
      if (values == null
          || values.length
              < (valueNumber == 0 ? 1 : valueNumber == FRAME_INDEX ? frame : valueNumber)) {
        return match;
      }
      return filterOp.op(values, valueNumber == FRAME_INDEX ? frame : valueNumber, params);
    }
  }

  private static HPSelector createAttributeValueSelector(
      String usageFlag,
      String privateCreator,
      int tag,
      int valueNumber,
      VR vr,
      float[] values,
      HPFilter filterOp) {
    VR vr1 = vr != null ? vr : ElementDictionary.vrOf(tag, privateCreator);
    int valueTag =
        switch (vr1) {
          case DS -> Tag.SelectorDSValue;
          case FL -> Tag.SelectorFLValue;
          default -> throw new IllegalArgumentException("vr: " + vr);
        };
    return new Flt(tag, privateCreator, valueNumber, valueTag, usageFlag, filterOp, vr, values);
  }

  private static class Flt extends AttributeValueSelector {

    private final float[] params;

    Flt(Attributes item, int valueTag, boolean match, HPFilter filterOp, VR vr) {
      super(item, match, filterOp, vr);
      this.params = item.getFloats(valueTag);
      if (params == null || params.length == 0) {
        throw new IllegalArgumentException("Missing " + TagUtils.toString(valueTag));
      }
      if (filterOp.isNumeric() && filterOp.getNumParams() != params.length) {
        throw new IllegalArgumentException("Illegal Number of values: " + item.getString(valueTag));
      }
    }

    Flt(
        int tag,
        String privateCreator,
        int valueNumber,
        int valueTag,
        String usageFlag,
        HPFilter filterOp,
        VR vr,
        float[] values) {
      super(tag, privateCreator, valueNumber, usageFlag, filterOp, vr);
      this.params = values.clone();
      item.setFloat(valueTag, vr, values);
    }

    public boolean matches(Attributes dcmobj, int frame) {
      float[] values = dcmobj.getFloats(privateCreator, tag, vr);
      if (values == null
          || values.length
              < (valueNumber == 0 ? 1 : valueNumber == FRAME_INDEX ? frame : valueNumber)) {
        return match;
      }
      return filterOp.op(values, valueNumber == FRAME_INDEX ? frame : valueNumber, params);
    }
  }

  private static HPSelector createAttributeValueSelector(
      String usageFlag,
      String privateCreator,
      int tag,
      int valueNumber,
      double[] values,
      HPFilter filterOp) {
    return new Dbl(
        tag, privateCreator, valueNumber, Tag.SelectorFDValue, usageFlag, filterOp, VR.FD, values);
  }

  private static class Dbl extends AttributeValueSelector {

    private final double[] params;

    Dbl(Attributes item, int valueTag, boolean match, HPFilter filterOp, VR vr) {
      super(item, match, filterOp, vr);
      this.params = item.getDoubles(valueTag);
      if (params == null || params.length == 0) {
        throw new IllegalArgumentException("Missing " + TagUtils.toString(valueTag));
      }
      if (filterOp.isNumeric() && filterOp.getNumParams() != params.length) {
        throw new IllegalArgumentException("Illegal Number of values: " + item.getString(valueTag));
      }
    }

    Dbl(
        int tag,
        String privateCreator,
        int valueNumber,
        int valueTag,
        String usageFlag,
        HPFilter filterOp,
        VR vr,
        double[] values) {
      super(tag, privateCreator, valueNumber, usageFlag, filterOp, vr);
      this.params = values.clone();
      item.setDouble(valueTag, vr, values);
    }

    public boolean matches(Attributes dcmobj, int frame) {
      double[] values = dcmobj.getDoubles(privateCreator, tag, vr);
      if (values == null
          || values.length
              < (valueNumber == 0 ? 1 : valueNumber == FRAME_INDEX ? frame : valueNumber)) {
        return match;
      }
      return filterOp.op(values, valueNumber == FRAME_INDEX ? frame : valueNumber, params);
    }
  }

  private static class CodeValueSelector extends AttributeValueSelector {

    private final Collection<Code> params;

    CodeValueSelector(Attributes item, boolean match, HPFilter filterOp, VR vr) {
      super(item, match, filterOp, vr);
      if (filterOp.isNumeric()) {
        throw new IllegalArgumentException(
            "Filter-by Operator: "
                + item.getString(Tag.FilterByOperator)
                + " conflicts with non-numeric VR: SQ");
      }
      this.params = Code.toCodeMacros(item.getSequence(Tag.SelectorCodeSequenceValue));
      if (params.isEmpty()) {
        throw new IllegalArgumentException("Missing (0072,0080) Selector Code Sequence Value");
      }
    }

    CodeValueSelector(
        int tag,
        String privateCreator,
        int valueNumber,
        String usageFlag,
        HPFilter filterOp,
        Code[] values) {
      super(tag, privateCreator, valueNumber, usageFlag, filterOp, VR.SQ);
      this.params = Arrays.asList(values);
    }

    public boolean matches(Attributes dcmobj, int frame) {
      Sequence values = dcmobj.getSequence(privateCreator, tag);
      if (values == null || values.isEmpty()) {
        return match;
      }
      return filterOp.op(Code.toCodeMacros(values), params);
    }
  }

  private abstract static class AttributeSelectorDecorator extends AttributeSelector {

    protected final HPSelector selector;

    AttributeSelectorDecorator(int tag, String privateCreator, boolean match, HPSelector selector) {
      super(tag, privateCreator, match);
      this.selector = selector;
    }

    public Attributes getAttributes() {
      return selector.getAttributes();
    }
  }

  private static class Seq extends AttributeSelectorDecorator {

    Seq(int tag, String privateCreator, AttributeSelector selector) {
      super(tag, privateCreator, selector.isMatchIfNotPresent(), selector);
    }

    public boolean matches(Attributes dcmobj, int frame) {
      Sequence values1 = dcmobj.getSequence(privateCreator, tag);
      if (values1 == null || values1.isEmpty()) {
        return match;
      }
      for (Attributes attributes : values1) {
        if (selector.matches(attributes, frame)) {
          return true;
        }
      }
      return false;
    }
  }

  private static class FctGrp extends AttributeSelectorDecorator {

    FctGrp(int tag, String privateCreator, AttributeSelector selector) {
      super(tag, privateCreator, selector.isMatchIfNotPresent(), selector);
    }

    public boolean matches(Attributes dcmobj, int frame) {
      Attributes sharedFctGrp = dcmobj.getNestedDataset(Tag.SharedFunctionalGroupsSequence);
      if (sharedFctGrp != null) {
        Sequence fctGrp = sharedFctGrp.getSequence(privateCreator, tag);
        if (fctGrp != null) {
          return matches(fctGrp, frame);
        }
      }
      Sequence frameFctGrpSeq = dcmobj.getSequence(Tag.PerFrameFunctionalGroupsSequence);
      if (frameFctGrpSeq == null) {
        return match;
      }
      if (frame != 0) {
        return op(frameFctGrpSeq.get(frame - 1), frame);
      }
      for (Attributes attributes : frameFctGrpSeq) {
        if (op(attributes, frame)) {
          return true;
        }
      }
      return false;
    }

    private boolean op(Attributes frameFctGrp, int frame) {
      if (frameFctGrp == null) {
        return match;
      }
      Sequence fctGrp = frameFctGrp.getSequence(privateCreator, tag);
      if (fctGrp == null) {
        return match;
      }
      return matches(fctGrp, frame);
    }

    private boolean matches(Sequence fctGrp, int frame) {
      for (Attributes attributes : fctGrp) {
        if (selector.matches(attributes, frame)) return true;
      }
      return false;
    }
  }
}
