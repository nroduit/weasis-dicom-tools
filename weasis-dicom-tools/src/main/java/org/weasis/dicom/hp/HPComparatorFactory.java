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

import java.util.Date;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Sequence;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.VR;
import org.weasis.dicom.hp.plugins.AlongAxisComparator;
import org.weasis.dicom.hp.plugins.ByAcqTimeComparator;
import org.weasis.dicom.hp.spi.HPComparatorCategoryService;

public class HPComparatorFactory {

  /**
   * Selector Value Number constant for indicating that the frame number shall be used for indexing
   * the value of the Selector Attribute for sorting.
   */
  public static final int FRAME_INDEX = 0xffff;

  public static HPComparator createHPComparator(Attributes sortingOp) {
    if (sortingOp.containsValue(Tag.SortByCategory)) {
      return HPComparatorFactory.createSortByCategory(sortingOp);
    }
    HPComparator cmp = new SortByAttribute(sortingOp);
    cmp = addSequencePointer(cmp);
    cmp = addFunctionalGroupPointer(cmp);
    return cmp;
  }

  private static HPComparator createSortByCategory(Attributes sortingOp) {
    HPComparatorCategoryService spi =
        HangingProtocol.getHPComparatorSpi(sortingOp.getString(Tag.SortByCategory));
    if (spi == null) {
      throw new IllegalArgumentException(
          "Unsupported Sort-by Category: " + sortingOp.getString(Tag.SortByCategory));
    }
    return spi.createHPComparator(sortingOp);
  }

  public static HPComparator createSortByAttribute(
      String privateCreator, int tag, int valueNumber, String sortingDirection) {
    return new SortByAttribute(privateCreator, tag, valueNumber, sortingDirection);
  }

  public static HPComparator createSortAlongAxis(String sortingDirection) {
    return new AlongAxisComparator(sortingDirection);
  }

  public static HPComparator createSortByAcqTime(String sortingDirection) {
    return new ByAcqTimeComparator(sortingDirection);
  }

  public static HPComparator addSequencePointer(
      String privateCreator, int tag, HPComparator comparator) {
    if (tag == 0) {
      return comparator;
    }

    if (comparator.getSelectorSequencePointer() != 0) {
      throw new IllegalArgumentException("Sequence Pointer already added");
    }

    if (comparator.getFunctionalGroupPointer() != 0) {
      throw new IllegalArgumentException("Functional Group Pointer already added");
    }

    comparator.getAttributes().setInt(Tag.SelectorSequencePointer, VR.AT, tag);
    if (privateCreator != null) {
      comparator
          .getAttributes()
          .setString(Tag.SelectorSequencePointerPrivateCreator, VR.LO, privateCreator);
    }
    return new Seq(privateCreator, tag, comparator);
  }

  public static HPComparator addFunctionalGroupPointer(
      String privCreator, int tag, HPComparator comparator) {
    if (tag == 0) {
      return comparator;
    }

    if (comparator.getFunctionalGroupPointer() != 0) {
      throw new IllegalArgumentException("Functional Group Pointer already added");
    }

    comparator.getAttributes().setInt(Tag.FunctionalGroupPointer, VR.AT, tag);
    if (privCreator != null) {
      comparator.getAttributes().setString(Tag.FunctionalGroupPrivateCreator, VR.LO, privCreator);
    }
    return new FctGrp(tag, privCreator, comparator);
  }

  private static HPComparator addSequencePointer(HPComparator cmp) {
    int tag = cmp.getSelectorSequencePointer();
    if (tag != 0) {
      String privCreator = cmp.getSelectorSequencePointerPrivateCreator();
      cmp = new Seq(privCreator, tag, cmp);
    }
    return cmp;
  }

  private static HPComparator addFunctionalGroupPointer(HPComparator cmp) {
    int tag = cmp.getFunctionalGroupPointer();
    if (tag != 0) {
      String privCreator = cmp.getFunctionalGroupPrivateCreator();
      cmp = new FctGrp(tag, privCreator, cmp);
    }
    return cmp;
  }

  private abstract static class AttributeComparator extends AbstractHPComparator {

    protected final int tag;

    protected final String privateCreator;

    AttributeComparator(int tag, String privateCreator) {
      if (tag == 0) {
        throw new IllegalArgumentException("tag: 0");
      }
      this.tag = tag;
      this.privateCreator = privateCreator;
    }
  }

  private static class SortByAttribute extends AttributeComparator {

    private final Attributes sortingOp;

    private final int valueNumber;

    private final int sign;

    SortByAttribute(String privateCreator, int tag, int valueNumber, String sortingDirection) {
      super(tag, privateCreator);
      if (valueNumber == 0) {
        throw new IllegalArgumentException("valueNumber = 0");
      }
      this.valueNumber = valueNumber;
      this.sign = CodeString.sortingDirectionToSign(sortingDirection);
      sortingOp = new Attributes();
      sortingOp.setInt(Tag.SelectorAttribute, VR.AT, tag);
      if (privateCreator != null) {
        sortingOp.setString(Tag.SelectorAttributePrivateCreator, VR.LO, privateCreator);
      }
      sortingOp.setInt(Tag.SelectorValueNumber, VR.US, valueNumber);
      sortingOp.setString(Tag.SortingDirection, VR.CS, sortingDirection);
    }

    SortByAttribute(Attributes sortingOp) {
      super(
          getSelectorAttribute(sortingOp),
          sortingOp.getString(Tag.SelectorAttributePrivateCreator));
      this.valueNumber = sortingOp.getInt(Tag.SelectorValueNumber, 0);
      if (valueNumber == 0) {
        throw new IllegalArgumentException(
            "Missing or invalid (0072,0028) Selector Value Number: "
                + sortingOp.getString(Tag.SelectorValueNumber));
      }
      String cs = sortingOp.getString(Tag.SortingDirection);
      if (cs == null) {
        throw new IllegalArgumentException("Missing (0072,0604) Sorting Direction");
      }
      this.sign = CodeString.sortingDirectionToSign(cs);
      this.sortingOp = sortingOp;
    }

    private static int getSelectorAttribute(Attributes sortingOp) {
      int tag = sortingOp.getInt(Tag.SelectorAttribute, 0);
      if (tag == 0) {
        throw new IllegalArgumentException("Missing (0072,0026) Selector Attribute");
      }
      return tag;
    }

    public Attributes getAttributes() {
      return sortingOp;
    }

    public int compare(Attributes o1, int frame1, Attributes o2, int frame2) {
      VR e1 = o1.getVR(privateCreator, tag);
      if (e1 == null) {
        return 0;
      }
      VR e2 = o2.getVR(privateCreator, tag);
      if (e2 == null) {
        return 0;
      }
      if (!e1.equals(e2)) {
        return 0;
      }
      int i1 = frame1;
      int i2 = frame2;
      if (valueNumber != FRAME_INDEX) {
        i1 = i2 = valueNumber;
      }

      return switch (e1) {
        case AE, AS, CS, LO, LT, PN, SH, ST, UI, UT ->
            sign
                * compareStrings(
                    o1.getStrings(privateCreator, tag), i1,
                    o2.getStrings(privateCreator, tag), i2);
        case AT, UL, US ->
            sign
                * compareIntegers(
                    o1.getInts(privateCreator, tag), i1, o2.getInts(privateCreator, tag), i2);
        case DA, DT, TM ->
            sign
                * compareDates(
                    o1.getDates(privateCreator, tag), i1, o2.getDates(privateCreator, tag), i2);
        case DS, FL ->
            sign
                * compareFloats(
                    o1.getFloats(privateCreator, tag), i1, o2.getFloats(privateCreator, tag), i2);
        case FD ->
            sign
                * compareDoubles(
                    o1.getDoubles(privateCreator, tag), i1, o2.getDoubles(privateCreator, tag), i2);
        case IS, SL, SS ->
            sign
                * compareInts(
                    o1.getInts(privateCreator, tag), i1, o2.getInts(privateCreator, tag), i2);
        case SQ ->
            sign
                * compareAttributes(
                    o1.getNestedDataset(privateCreator, tag),
                    o2.getNestedDataset(privateCreator, tag));
        default ->
            // no sort if VR = OB, OF, OW or UN
            0;
      };
    }
  }

  private static int compareAttributes(Attributes c1, Attributes c2) {
    if (c1 == null || c2 == null) {
      return 0;
    }
    String v1 = c1.getString(Tag.CodeValue);
    String v2 = c2.getString(Tag.CodeValue);
    if (v1 == null || v2 == null) {
      return 0;
    }
    return v1.compareTo(v2);
  }

  private static int compareInts(int[] v1, int i1, int[] v2, int i2) {
    if (v1 == null || v2 == null || v1.length < i1 || v2.length < i2) {
      return 0;
    }
    return v1[i1 - 1] - v2[i2 - 1];
  }

  private static int compareDoubles(double[] v1, int i1, double[] v2, int i2) {
    if (v1 == null || v2 == null || v1.length < i1 || v2.length < i2) {
      return 0;
    }
    double d = v1[i1 - 1] - v2[i2 - 1];
    return (d < 0) ? -1 : (d > 0) ? 1 : 0;
  }

  private static int compareFloats(float[] v1, int i1, float[] v2, int i2) {
    if (v1 == null || v2 == null || v1.length < i1 || v2.length < i2) {
      return 0;
    }
    float d = v1[i1 - 1] - v2[i2 - 1];
    return (d < 0) ? -1 : (d > 0) ? 1 : 0;
  }

  private static int compareDates(Date[] v1, int i1, Date[] v2, int i2) {
    if (v1 == null || v2 == null || v1.length < i1 || v2.length < i2) {
      return 0;
    }
    return v1[i1 - 1].compareTo(v2[i2 - 1]);
  }

  private static int compareIntegers(int[] v1, int i1, int[] v2, int i2) {
    if (v1 == null || v2 == null || v1.length < i1 || v2.length < i2) {
      return 0;
    }
    long d = (v1[i1 - 1] & 0xffffffffL) - (v2[i2 - 1] & 0xffffffffL);
    return (d < 0) ? -1 : (d > 0) ? 1 : 0;
  }

  private static int compareStrings(String[] v1, int i1, String[] v2, int i2) {
    if (v1 == null || v2 == null || v1.length < i1 || v2.length < i2) {
      return 0;
    }
    return v1[i1 - 1].compareTo(v2[i2 - 1]);
  }

  private abstract static class AttributeComparatorDecorator extends AttributeComparator {

    protected final HPComparator cmp;

    AttributeComparatorDecorator(int tag, String privateCreator, HPComparator cmp) {
      super(tag, privateCreator);
      this.cmp = cmp;
    }

    public Attributes getAttributes() {
      return cmp.getAttributes();
    }
  }

  private static class Seq extends AttributeComparatorDecorator {

    Seq(String privateCreator, int tag, HPComparator cmp) {
      super(tag, privateCreator, cmp);
    }

    public int compare(Attributes o1, int frame1, Attributes o2, int frame2) {
      Attributes v1 = o1.getNestedDataset(privateCreator, tag);
      if (v1 == null) {
        return 0;
      }
      Attributes v2 = o2.getNestedDataset(privateCreator, tag);
      if (v2 == null) {
        return 0;
      }
      return cmp.compare(v1, frame1, v2, frame2);
    }
  }

  private static class FctGrp extends AttributeComparatorDecorator {

    FctGrp(int tag, String privateCreator, HPComparator cmp) {
      super(tag, privateCreator, cmp);
    }

    public int compare(Attributes o1, int frame1, Attributes o2, int frame2) {
      Attributes fg1 = fctGrp(o1, frame1);
      if (fg1 == null) {
        return 0;
      }
      Attributes fg2 = fctGrp(o1, frame1);
      if (fg2 == null) {
        return 0;
      }
      return cmp.compare(fg1, frame1, fg2, frame2);
    }

    private Attributes fctGrp(Attributes o, int frame) {
      Attributes sharedFctGrp = o.getNestedDataset(Tag.SharedFunctionalGroupsSequence);
      if (sharedFctGrp != null) {
        Attributes fctGrp = sharedFctGrp.getNestedDataset(privateCreator, tag);
        if (fctGrp != null) {
          return fctGrp;
        }
      }
      Sequence frameFctGrpSeq = o.getSequence(Tag.PerFrameFunctionalGroupsSequence);
      if (frameFctGrpSeq == null || frameFctGrpSeq.size() < frame) {
        return null;
      }
      Attributes frameFctGrp = frameFctGrpSeq.get(frame - 1);
      return frameFctGrp.getNestedDataset(privateCreator, tag);
    }
  }
}
