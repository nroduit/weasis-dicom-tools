/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.hp.filter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Collection;
import java.util.List;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.VR;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.weasis.dicom.macro.Code;

@DisplayNameGeneration(ReplaceUnderscores.class)
class FilterOpTest {

  @Nested
  class ValueOf_Tests {

    @ParameterizedTest
    @ValueSource(
        strings = {
          "MEMBER_OF",
          "NOT_MEMBER_OF",
          "RANGE_INCL",
          "RANGE_EXCL",
          "GREATER_OR_EQUAL",
          "LESS_OR_EQUAL",
          "GREATER_THAN",
          "LESS_THAN"
        })
    void valueOf_returns_singleton_with_matching_code_string(String code) {
      HPFilter f = FilterOp.valueOf(code);
      assertEquals(code, f.getCodeString());
    }

    @Test
    void valueOf_returns_same_instance_as_static_field() {
      assertEquals(FilterOp.MEMBER_OF, FilterOp.valueOf("MEMBER_OF"));
      assertEquals(FilterOp.NOT_MEMBER_OF, FilterOp.valueOf("NOT_MEMBER_OF"));
      assertEquals(FilterOp.RANGE_INCL, FilterOp.valueOf("RANGE_INCL"));
      assertEquals(FilterOp.RANGE_EXCL, FilterOp.valueOf("RANGE_EXCL"));
      assertEquals(FilterOp.GREATER_OR_EQUAL, FilterOp.valueOf("GREATER_OR_EQUAL"));
      assertEquals(FilterOp.LESS_OR_EQUAL, FilterOp.valueOf("LESS_OR_EQUAL"));
      assertEquals(FilterOp.GREATER_THAN, FilterOp.valueOf("GREATER_THAN"));
      assertEquals(FilterOp.LESS_THAN, FilterOp.valueOf("LESS_THAN"));
    }

    @Test
    void valueOf_throws_for_unknown_code() {
      assertThrows(IllegalArgumentException.class, () -> FilterOp.valueOf("UNKNOWN"));
      assertThrows(IllegalArgumentException.class, () -> FilterOp.valueOf(""));
    }
  }

  @Nested
  class HPFilter_API_Tests {

    @Test
    void numParams_and_isNumeric_match_filter_semantics() {
      assertEquals(0, FilterOp.MEMBER_OF.getNumParams());
      assertEquals(0, FilterOp.NOT_MEMBER_OF.getNumParams());
      assertEquals(2, FilterOp.RANGE_INCL.getNumParams());
      assertEquals(2, FilterOp.RANGE_EXCL.getNumParams());
      assertEquals(1, FilterOp.GREATER_OR_EQUAL.getNumParams());
      assertEquals(1, FilterOp.LESS_OR_EQUAL.getNumParams());
      assertEquals(1, FilterOp.GREATER_THAN.getNumParams());
      assertEquals(1, FilterOp.LESS_THAN.getNumParams());

      assertFalse(FilterOp.MEMBER_OF.isNumeric());
      assertFalse(FilterOp.NOT_MEMBER_OF.isNumeric());
      assertTrue(FilterOp.RANGE_INCL.isNumeric());
      assertTrue(FilterOp.GREATER_THAN.isNumeric());
    }

    @Test
    void range_and_comparator_filters_do_not_support_strings_or_codes() {
      // Default HPFilter.op(String[]/Collection<Code>) throws UnsupportedOperationException.
      HPFilter rangeIncl = FilterOp.RANGE_INCL;
      assertThrows(
          UnsupportedOperationException.class,
          () -> rangeIncl.op(new String[] {"a"}, 0, new String[] {"a"}));
      assertThrows(UnsupportedOperationException.class, () -> rangeIncl.op(List.of(), List.of()));

      HPFilter gt = FilterOp.GREATER_THAN;
      assertThrows(
          UnsupportedOperationException.class,
          () -> gt.op(new String[] {"a"}, 0, new String[] {"a"}));
      assertThrows(UnsupportedOperationException.class, () -> gt.op(List.of(), List.of()));
    }
  }

  @Nested
  class MemberOf_Tests {

    private final HPFilter op = FilterOp.MEMBER_OF;

    @Test
    void strings_any_match_when_valueNumber_zero() {
      assertTrue(op.op(new String[] {"A", "B", "C"}, 0, new String[] {"X", "B"}));
      assertFalse(op.op(new String[] {"A", "B"}, 0, new String[] {"X", "Y"}));
    }

    @Test
    void strings_specific_position_when_valueNumber_greater_than_zero() {
      assertTrue(op.op(new String[] {"A", "B", "C"}, 2, new String[] {"B"}));
      assertFalse(op.op(new String[] {"A", "B", "C"}, 1, new String[] {"B"}));
    }

    @Test
    void ints_any_match_when_valueNumber_zero() {
      assertTrue(op.op(new int[] {1, 2, 3}, 0, new int[] {9, 2}));
      assertFalse(op.op(new int[] {1, 2}, 0, new int[] {9}));
    }

    @Test
    void ints_specific_position() {
      assertTrue(op.op(new int[] {1, 2, 3}, 3, new int[] {3}));
      assertFalse(op.op(new int[] {1, 2, 3}, 1, new int[] {3}));
    }

    @Test
    void ints_with_long_params_any_match() {
      assertTrue(op.op(new int[] {1, 2}, 0, new long[] {2L, 99L}));
      assertFalse(op.op(new int[] {1, 2}, 0, new long[] {99L}));
    }

    @Test
    void ints_with_long_params_specific_position() {
      assertTrue(op.op(new int[] {1, 2}, 2, new long[] {2L}));
      assertFalse(op.op(new int[] {1, 2}, 1, new long[] {2L}));
    }

    @Test
    void floats_any_match() {
      assertTrue(op.op(new float[] {1.0f, 2.5f}, 0, new float[] {2.5f}));
      assertFalse(op.op(new float[] {1.0f, 2.5f}, 0, new float[] {9.0f}));
    }

    @Test
    void floats_specific_position() {
      assertTrue(op.op(new float[] {1.0f, 2.5f}, 2, new float[] {2.5f}));
      assertFalse(op.op(new float[] {1.0f, 2.5f}, 1, new float[] {2.5f}));
    }

    @Test
    void doubles_any_match() {
      assertTrue(op.op(new double[] {1.0, 2.5}, 0, new double[] {2.5}));
      assertFalse(op.op(new double[] {1.0, 2.5}, 0, new double[] {9.0}));
    }

    @Test
    void doubles_specific_position() {
      assertTrue(op.op(new double[] {1.0, 2.5}, 2, new double[] {2.5}));
      assertFalse(op.op(new double[] {1.0, 2.5}, 1, new double[] {2.5}));
    }

    @Test
    void codes_match_when_value_and_designator_equal() {
      Collection<Code> values = List.of(code("123", "SCT", null), code("999", "SCT", null));
      Collection<Code> params = List.of(code("123", "SCT", null));
      assertTrue(op.op(values, params));
    }

    @Test
    void codes_no_match_when_value_differs() {
      Collection<Code> values = List.of(code("123", "SCT", null));
      Collection<Code> params = List.of(code("999", "SCT", null));
      assertFalse(op.op(values, params));
    }

    @Test
    void codes_no_match_when_designator_differs() {
      Collection<Code> values = List.of(code("123", "DCM", null));
      Collection<Code> params = List.of(code("123", "SCT", null));
      assertFalse(op.op(values, params));
    }

    @Test
    void codes_ignore_version_when_either_side_omits_it() {
      Collection<Code> values = List.of(code("123", "SCT", "v1"));
      Collection<Code> params = List.of(code("123", "SCT", null));
      assertTrue(op.op(values, params));
    }

    @Test
    void codes_compare_version_when_both_present() {
      Collection<Code> v1 = List.of(code("123", "SCT", "v1"));
      Collection<Code> v2 = List.of(code("123", "SCT", "v2"));
      assertFalse(op.op(v1, v2));

      Collection<Code> sameVersion = List.of(code("123", "SCT", "v1"));
      assertTrue(op.op(v1, sameVersion));
    }
  }

  @Nested
  class NotMemberOf_Tests {

    private final HPFilter op = FilterOp.NOT_MEMBER_OF;

    @Test
    void strings_returns_true_when_no_value_matches() {
      assertTrue(op.op(new String[] {"A", "B"}, 0, new String[] {"X"}));
      assertFalse(op.op(new String[] {"A", "B"}, 0, new String[] {"A"}));
    }

    @Test
    void strings_specific_position() {
      assertTrue(op.op(new String[] {"A", "B"}, 1, new String[] {"B"}));
      assertFalse(op.op(new String[] {"A", "B"}, 2, new String[] {"B"}));
    }

    @Test
    void ints_returns_true_when_no_value_matches() {
      assertTrue(op.op(new int[] {1, 2}, 0, new int[] {9}));
      assertFalse(op.op(new int[] {1, 2}, 0, new int[] {2}));
    }

    @Test
    void ints_specific_position() {
      assertTrue(op.op(new int[] {1, 2}, 1, new int[] {2}));
      assertFalse(op.op(new int[] {1, 2}, 2, new int[] {2}));
    }

    @Test
    void ints_with_long_params() {
      assertTrue(op.op(new int[] {1, 2}, 0, new long[] {9L}));
      assertFalse(op.op(new int[] {1, 2}, 0, new long[] {2L}));
      assertTrue(op.op(new int[] {1, 2}, 1, new long[] {2L}));
      assertFalse(op.op(new int[] {1, 2}, 2, new long[] {2L}));
    }

    @Test
    void floats() {
      assertTrue(op.op(new float[] {1.0f, 2.5f}, 0, new float[] {9.0f}));
      assertFalse(op.op(new float[] {1.0f, 2.5f}, 0, new float[] {2.5f}));
      assertTrue(op.op(new float[] {1.0f, 2.5f}, 1, new float[] {2.5f}));
      assertFalse(op.op(new float[] {1.0f, 2.5f}, 2, new float[] {2.5f}));
    }

    @Test
    void doubles() {
      assertTrue(op.op(new double[] {1.0, 2.5}, 0, new double[] {9.0}));
      assertFalse(op.op(new double[] {1.0, 2.5}, 0, new double[] {2.5}));
      assertTrue(op.op(new double[] {1.0, 2.5}, 1, new double[] {2.5}));
      assertFalse(op.op(new double[] {1.0, 2.5}, 2, new double[] {2.5}));
    }

    @Test
    void codes_returns_true_when_no_code_matches() {
      Collection<Code> values = List.of(code("123", "SCT", null));
      Collection<Code> params = List.of(code("999", "SCT", null));
      assertTrue(op.op(values, params));

      Collection<Code> matchingParams = List.of(code("123", "SCT", null));
      assertFalse(op.op(values, matchingParams));
    }
  }

  @Nested
  class RangeIncl_Tests {

    private final HPFilter op = FilterOp.RANGE_INCL;

    @Test
    void ints_returns_true_only_when_all_values_inside() {
      assertTrue(op.op(new int[] {5, 6, 7}, 0, new int[] {5, 7}));
      assertFalse(op.op(new int[] {5, 8}, 0, new int[] {5, 7}));
    }

    @Test
    void ints_specific_position_checks_only_that_value() {
      assertTrue(op.op(new int[] {99, 6}, 2, new int[] {5, 7}));
      assertFalse(op.op(new int[] {6, 99}, 2, new int[] {5, 7}));
    }

    @Test
    void ints_with_long_params() {
      assertTrue(op.op(new int[] {5, 6}, 0, new long[] {5L, 7L}));
      assertFalse(op.op(new int[] {5, 8}, 0, new long[] {5L, 7L}));
      assertTrue(op.op(new int[] {99, 6}, 2, new long[] {5L, 7L}));
      assertFalse(op.op(new int[] {6, 99}, 2, new long[] {5L, 7L}));
    }

    @Test
    void floats() {
      assertTrue(op.op(new float[] {1.5f, 2.5f}, 0, new float[] {1.0f, 3.0f}));
      assertFalse(op.op(new float[] {1.5f, 3.5f}, 0, new float[] {1.0f, 3.0f}));
      assertTrue(op.op(new float[] {99f, 1.5f}, 2, new float[] {1.0f, 3.0f}));
      assertFalse(op.op(new float[] {1.5f, 99f}, 2, new float[] {1.0f, 3.0f}));
    }

    @Test
    void doubles() {
      assertTrue(op.op(new double[] {1.5, 2.5}, 0, new double[] {1.0, 3.0}));
      assertFalse(op.op(new double[] {1.5, 3.5}, 0, new double[] {1.0, 3.0}));
      assertTrue(op.op(new double[] {99.0, 1.5}, 2, new double[] {1.0, 3.0}));
      assertFalse(op.op(new double[] {1.5, 99.0}, 2, new double[] {1.0, 3.0}));
    }
  }

  @Nested
  class RangeExcl_Tests {

    private final HPFilter op = FilterOp.RANGE_EXCL;

    @Test
    void ints_returns_true_only_when_all_values_outside() {
      assertTrue(op.op(new int[] {1, 9}, 0, new int[] {3, 5}));
      assertFalse(op.op(new int[] {1, 4}, 0, new int[] {3, 5}));
    }

    @Test
    void ints_specific_position() {
      assertTrue(op.op(new int[] {4, 9}, 2, new int[] {3, 5}));
      assertFalse(op.op(new int[] {9, 4}, 2, new int[] {3, 5}));
    }

    @Test
    void ints_with_long_params() {
      assertTrue(op.op(new int[] {1, 9}, 0, new long[] {3L, 5L}));
      assertFalse(op.op(new int[] {4}, 0, new long[] {3L, 5L}));
      assertTrue(op.op(new int[] {4, 9}, 2, new long[] {3L, 5L}));
      assertFalse(op.op(new int[] {9, 4}, 2, new long[] {3L, 5L}));
    }

    @Test
    void floats() {
      assertTrue(op.op(new float[] {0.5f, 4.0f}, 0, new float[] {1.0f, 3.0f}));
      assertFalse(op.op(new float[] {0.5f, 2.0f}, 0, new float[] {1.0f, 3.0f}));
      assertTrue(op.op(new float[] {2.0f, 0.5f}, 2, new float[] {1.0f, 3.0f}));
      assertFalse(op.op(new float[] {0.5f, 2.0f}, 2, new float[] {1.0f, 3.0f}));
    }

    @Test
    void doubles() {
      assertTrue(op.op(new double[] {0.5, 4.0}, 0, new double[] {1.0, 3.0}));
      assertFalse(op.op(new double[] {0.5, 2.0}, 0, new double[] {1.0, 3.0}));
      assertTrue(op.op(new double[] {2.0, 0.5}, 2, new double[] {1.0, 3.0}));
      assertFalse(op.op(new double[] {0.5, 2.0}, 2, new double[] {1.0, 3.0}));
    }
  }

  @Nested
  class GreaterOrEqual_Tests {

    private final HPFilter op = FilterOp.GREATER_OR_EQUAL;

    @Test
    void ints_all_values_must_be_ge() {
      assertTrue(op.op(new int[] {5, 6}, 0, new int[] {5}));
      assertFalse(op.op(new int[] {4, 6}, 0, new int[] {5}));
    }

    @Test
    void ints_specific_position() {
      assertTrue(op.op(new int[] {1, 5}, 2, new int[] {5}));
      assertFalse(op.op(new int[] {1, 4}, 2, new int[] {5}));
    }

    @Test
    void ints_with_long_params() {
      assertTrue(op.op(new int[] {5}, 0, new long[] {5L}));
      assertFalse(op.op(new int[] {4}, 0, new long[] {5L}));
      assertTrue(op.op(new int[] {1, 5}, 2, new long[] {5L}));
      assertFalse(op.op(new int[] {5, 1}, 2, new long[] {5L}));
    }

    @Test
    void floats() {
      assertTrue(op.op(new float[] {5.0f, 5.0f}, 0, new float[] {5.0f}));
      assertFalse(op.op(new float[] {4.99f}, 0, new float[] {5.0f}));
      assertTrue(op.op(new float[] {1.0f, 5.0f}, 2, new float[] {5.0f}));
      assertFalse(op.op(new float[] {5.0f, 1.0f}, 2, new float[] {5.0f}));
    }

    @Test
    void doubles() {
      assertTrue(op.op(new double[] {5.0, 5.0}, 0, new double[] {5.0}));
      assertFalse(op.op(new double[] {4.99}, 0, new double[] {5.0}));
      assertTrue(op.op(new double[] {1.0, 5.0}, 2, new double[] {5.0}));
      assertFalse(op.op(new double[] {5.0, 1.0}, 2, new double[] {5.0}));
    }
  }

  @Nested
  class LessOrEqual_Tests {

    private final HPFilter op = FilterOp.LESS_OR_EQUAL;

    @Test
    void ints_all_values_must_be_le() {
      assertTrue(op.op(new int[] {1, 5}, 0, new int[] {5}));
      assertFalse(op.op(new int[] {1, 6}, 0, new int[] {5}));
    }

    @Test
    void ints_specific_position() {
      assertTrue(op.op(new int[] {99, 5}, 2, new int[] {5}));
      assertFalse(op.op(new int[] {5, 99}, 2, new int[] {5}));
    }

    @Test
    void ints_with_long_params() {
      assertTrue(op.op(new int[] {5}, 0, new long[] {5L}));
      assertFalse(op.op(new int[] {6}, 0, new long[] {5L}));
      assertTrue(op.op(new int[] {99, 5}, 2, new long[] {5L}));
      assertFalse(op.op(new int[] {5, 99}, 2, new long[] {5L}));
    }

    @Test
    void floats() {
      assertTrue(op.op(new float[] {1.0f, 5.0f}, 0, new float[] {5.0f}));
      assertFalse(op.op(new float[] {5.01f}, 0, new float[] {5.0f}));
      assertTrue(op.op(new float[] {99f, 5.0f}, 2, new float[] {5.0f}));
      assertFalse(op.op(new float[] {5.0f, 99f}, 2, new float[] {5.0f}));
    }

    @Test
    void doubles() {
      assertTrue(op.op(new double[] {1.0, 5.0}, 0, new double[] {5.0}));
      assertFalse(op.op(new double[] {5.01}, 0, new double[] {5.0}));
      assertTrue(op.op(new double[] {99.0, 5.0}, 2, new double[] {5.0}));
      assertFalse(op.op(new double[] {5.0, 99.0}, 2, new double[] {5.0}));
    }
  }

  @Nested
  class GreaterThan_Tests {

    private final HPFilter op = FilterOp.GREATER_THAN;

    @Test
    void ints_strict() {
      assertTrue(op.op(new int[] {6, 7}, 0, new int[] {5}));
      assertFalse(op.op(new int[] {5, 6}, 0, new int[] {5}));
      assertTrue(op.op(new int[] {1, 6}, 2, new int[] {5}));
      assertFalse(op.op(new int[] {1, 5}, 2, new int[] {5}));
    }

    @Test
    void ints_with_long_params() {
      assertTrue(op.op(new int[] {6}, 0, new long[] {5L}));
      assertFalse(op.op(new int[] {5}, 0, new long[] {5L}));
      assertTrue(op.op(new int[] {1, 6}, 2, new long[] {5L}));
      assertFalse(op.op(new int[] {6, 5}, 2, new long[] {5L}));
    }

    @Test
    void floats() {
      assertTrue(op.op(new float[] {5.01f}, 0, new float[] {5.0f}));
      assertFalse(op.op(new float[] {5.0f}, 0, new float[] {5.0f}));
      assertTrue(op.op(new float[] {1.0f, 5.01f}, 2, new float[] {5.0f}));
      assertFalse(op.op(new float[] {5.01f, 5.0f}, 2, new float[] {5.0f}));
    }

    @Test
    void doubles() {
      assertTrue(op.op(new double[] {5.01}, 0, new double[] {5.0}));
      assertFalse(op.op(new double[] {5.0}, 0, new double[] {5.0}));
      assertTrue(op.op(new double[] {1.0, 5.01}, 2, new double[] {5.0}));
      assertFalse(op.op(new double[] {5.01, 5.0}, 2, new double[] {5.0}));
    }
  }

  @Nested
  class LessThan_Tests {

    private final HPFilter op = FilterOp.LESS_THAN;

    @Test
    void ints_strict() {
      assertTrue(op.op(new int[] {1, 2}, 0, new int[] {5}));
      assertFalse(op.op(new int[] {1, 5}, 0, new int[] {5}));
      assertTrue(op.op(new int[] {99, 1}, 2, new int[] {5}));
      assertFalse(op.op(new int[] {1, 5}, 2, new int[] {5}));
    }

    @Test
    void ints_with_long_params() {
      assertTrue(op.op(new int[] {1}, 0, new long[] {5L}));
      assertFalse(op.op(new int[] {5}, 0, new long[] {5L}));
      assertTrue(op.op(new int[] {99, 1}, 2, new long[] {5L}));
      assertFalse(op.op(new int[] {1, 5}, 2, new long[] {5L}));
    }

    @Test
    void floats() {
      assertTrue(op.op(new float[] {1.0f}, 0, new float[] {5.0f}));
      assertFalse(op.op(new float[] {5.0f}, 0, new float[] {5.0f}));
      assertTrue(op.op(new float[] {99f, 1.0f}, 2, new float[] {5.0f}));
      assertFalse(op.op(new float[] {1.0f, 5.0f}, 2, new float[] {5.0f}));
    }

    @Test
    void doubles() {
      assertTrue(op.op(new double[] {1.0}, 0, new double[] {5.0}));
      assertFalse(op.op(new double[] {5.0}, 0, new double[] {5.0}));
      assertTrue(op.op(new double[] {99.0, 1.0}, 2, new double[] {5.0}));
      assertFalse(op.op(new double[] {1.0, 5.0}, 2, new double[] {5.0}));
    }
  }

  private static Code code(String value, String designator, String version) {
    Code c = new Code();
    c.getAttributes().setString(Tag.CodeValue, VR.SH, value);
    c.getAttributes().setString(Tag.CodingSchemeDesignator, VR.SH, designator);
    if (version != null) {
      c.getAttributes().setString(Tag.CodingSchemeVersion, VR.SH, version);
    }
    return c;
  }
}
