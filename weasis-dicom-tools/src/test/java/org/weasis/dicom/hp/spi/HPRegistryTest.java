/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.hp.spi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import org.dcm4che3.data.Attributes;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.weasis.dicom.hp.HPComparator;
import org.weasis.dicom.hp.HPSelector;
import org.weasis.dicom.hp.plugins.AlongAxisComparatorService;
import org.weasis.dicom.hp.plugins.ByAcqTimeComparatorService;
import org.weasis.dicom.hp.plugins.ImagePlaneSelectorService;

@DisplayNameGeneration(ReplaceUnderscores.class)
class HPRegistryTest {

  // HPRegistry is a process-wide singleton; snapshot and restore its contents
  // around each test to keep cross-test state intact.
  private List<HPCategoryService> selectorsBackup;
  private List<HPCategoryService> comparatorsBackup;

  @BeforeEach
  void snapshot_registry() {
    selectorsBackup = collect(HPSelectorCategoryService.class);
    comparatorsBackup = collect(HPComparatorCategoryService.class);
  }

  @AfterEach
  void restore_registry() {
    HPRegistry r = HPRegistry.getHPRegistry();
    r.deregisterAll();
    selectorsBackup.forEach(r::registerServiceProvider);
    comparatorsBackup.forEach(r::registerServiceProvider);
  }

  @Nested
  class Singleton_Tests {

    @Test
    void getHPRegistry_returns_singleton_instance() {
      assertSame(HPRegistry.getHPRegistry(), HPRegistry.getHPRegistry());
    }

    @Test
    void built_in_providers_are_registered_at_construction() {
      List<String> selectorCategories = collectCategoryNames(HPSelectorCategoryService.class);
      List<String> comparatorCategories = collectCategoryNames(HPComparatorCategoryService.class);
      assertTrue(selectorCategories.contains("IMAGE_PLANE"));
      assertTrue(comparatorCategories.contains("ALONG_AXIS"));
      assertTrue(comparatorCategories.contains("BY_ACQ_TIME"));
    }
  }

  @Nested
  class Register_Deregister_Tests {

    @Test
    void registerServiceProvider_adds_to_matching_category() {
      HPRegistry r = HPRegistry.getHPRegistry();
      r.deregisterAll();

      AlongAxisComparatorService provider = new AlongAxisComparatorService();
      r.registerServiceProvider(provider);

      List<HPCategoryService> comparators = collect(HPComparatorCategoryService.class);
      assertTrue(comparators.contains(provider));
      // Selector list must not see the comparator-only provider.
      assertFalse(collect(HPSelectorCategoryService.class).contains(provider));
    }

    @Test
    void registerServiceProvider_null_throws() {
      assertThrows(
          IllegalArgumentException.class,
          () -> HPRegistry.getHPRegistry().registerServiceProvider(null));
    }

    @Test
    void deregisterServiceProvider_returns_true_when_provider_was_registered() {
      HPRegistry r = HPRegistry.getHPRegistry();
      ImagePlaneSelectorService provider = new ImagePlaneSelectorService();
      r.registerServiceProvider(provider);
      assertTrue(r.deregisterServiceProvider(provider, HPSelectorCategoryService.class));
      assertFalse(r.deregisterServiceProvider(provider, HPSelectorCategoryService.class));
    }

    @Test
    void deregisterServiceProvider_null_throws() {
      assertThrows(
          IllegalArgumentException.class,
          () ->
              HPRegistry.getHPRegistry()
                  .deregisterServiceProvider(null, HPSelectorCategoryService.class));
    }

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void deregisterServiceProvider_unknown_category_throws() {
      ImagePlaneSelectorService provider = new ImagePlaneSelectorService();
      HPRegistry r = HPRegistry.getHPRegistry();
      assertThrows(
          IllegalArgumentException.class,
          () ->
              r.deregisterServiceProvider(
                  (HPCategoryService) provider, (Class) UnregisteredCategory.class));
    }

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void deregisterServiceProvider_wrong_category_class_throws() {
      // The generic signature prevents this at compile time. Use raw types to drive the
      // runtime category.isInstance(provider) guard.
      ImagePlaneSelectorService provider = new ImagePlaneSelectorService();
      HPRegistry r = HPRegistry.getHPRegistry();
      r.registerServiceProvider(provider);
      assertThrows(
          ClassCastException.class,
          () ->
              r.deregisterServiceProvider(
                  (HPCategoryService) provider, (Class) HPComparatorCategoryService.class));
    }

    @Test
    void deregisterAll_for_category_clears_only_that_category() {
      HPRegistry r = HPRegistry.getHPRegistry();
      r.deregisterAll(HPComparatorCategoryService.class);
      assertTrue(collect(HPComparatorCategoryService.class).isEmpty());
      // Selectors should still hold their built-in provider.
      assertFalse(collect(HPSelectorCategoryService.class).isEmpty());
    }

    @Test
    void deregisterAll_for_category_unknown_throws() {
      assertThrows(
          IllegalArgumentException.class,
          () -> HPRegistry.getHPRegistry().deregisterAll(UnregisteredCategory.class));
    }

    @Test
    void deregisterAll_clears_every_category() {
      HPRegistry r = HPRegistry.getHPRegistry();
      r.deregisterAll();
      assertTrue(collect(HPSelectorCategoryService.class).isEmpty());
      assertTrue(collect(HPComparatorCategoryService.class).isEmpty());
    }

    @Test
    void getServiceProviders_unknown_category_throws() {
      assertThrows(
          IllegalArgumentException.class,
          () -> HPRegistry.getHPRegistry().getServiceProviders(UnregisteredCategory.class));
    }
  }

  @Nested
  class HPCategoryService_Tests {

    @Test
    void setProperty_and_getProperty_default_throw_for_unsupported_name() {
      HPCategoryService bare = new BareCategoryService("X");
      assertThrows(IllegalArgumentException.class, () -> bare.setProperty("anything", 1));
      assertThrows(IllegalArgumentException.class, () -> bare.getProperty("anything"));
    }

    @Test
    void getCategoryName_returns_constructor_value() {
      assertEquals("X", new BareCategoryService("X").getCategoryName());
    }
  }

  @Nested
  class ServiceConfigurationError_Tests {

    @Test
    void message_constructor_preserves_message() {
      ServiceConfigurationError e = new ServiceConfigurationError("boom");
      assertEquals("boom", e.getMessage());
    }

    @Test
    void cause_constructor_preserves_cause() {
      RuntimeException root = new RuntimeException("root");
      ServiceConfigurationError e = new ServiceConfigurationError(root);
      assertSame(root, e.getCause());
    }
  }

  /** Anonymous category used to drive the "unknown category" error paths. */
  private abstract static class UnregisteredCategory extends HPCategoryService {
    protected UnregisteredCategory(String category) {
      super(category);
    }
  }

  /** Concrete subtype to exercise the default property accessors on {@link HPCategoryService}. */
  private static class BareCategoryService extends HPCategoryService {
    BareCategoryService(String category) {
      super(category);
    }
  }

  private static List<HPCategoryService> collect(Class<? extends HPCategoryService> category) {
    Iterator<HPCategoryService> it = HPRegistry.getHPRegistry().getServiceProviders(category);
    List<HPCategoryService> result = new ArrayList<>();
    it.forEachRemaining(result::add);
    return result;
  }

  private static List<String> collectCategoryNames(Class<? extends HPCategoryService> category) {
    return collect(category).stream().map(HPCategoryService::getCategoryName).toList();
  }

  // Sanity checks that built-in providers still produce working artefacts after re-registration.
  @Test
  void built_in_comparator_service_creates_comparator_for_sort_direction() {
    HPRegistry r = HPRegistry.getHPRegistry();
    r.deregisterAll();
    r.registerServiceProvider(new ByAcqTimeComparatorService());

    HPComparatorCategoryService svc =
        (HPComparatorCategoryService) collect(HPComparatorCategoryService.class).get(0);
    Attributes sortOp = new Attributes();
    sortOp.setString(org.dcm4che3.data.Tag.SortingDirection, org.dcm4che3.data.VR.CS, "INCREASING");
    HPComparator cmp = svc.createHPComparator(sortOp);
    assertEquals("BY_ACQ_TIME", svc.getCategoryName());
    assertTrue(cmp instanceof org.weasis.dicom.hp.plugins.ByAcqTimeComparator);
  }

  @Test
  void built_in_selector_service_creates_selector_for_image_plane() {
    HPRegistry r = HPRegistry.getHPRegistry();
    r.deregisterAll();
    r.registerServiceProvider(new ImagePlaneSelectorService());

    HPSelectorCategoryService svc =
        (HPSelectorCategoryService) collect(HPSelectorCategoryService.class).get(0);
    Attributes filterOp = new Attributes();
    filterOp.setString(org.dcm4che3.data.Tag.SelectorAttributeVR, org.dcm4che3.data.VR.CS, "CS");
    filterOp.setString(
        org.dcm4che3.data.Tag.SelectorCSValue, org.dcm4che3.data.VR.CS, "TRANSVERSE");
    HPSelector sel = svc.createHPSelector(filterOp);
    assertEquals("IMAGE_PLANE", svc.getCategoryName());
    assertTrue(sel instanceof org.weasis.dicom.hp.plugins.ImagePlaneSelector);
  }
}
