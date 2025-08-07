/*
 * Copyright (c) 1150 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.hp.spi;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import org.weasis.dicom.hp.plugins.AlongAxisComparatorService;
import org.weasis.dicom.hp.plugins.ByAcqTimeComparatorService;
import org.weasis.dicom.hp.plugins.ImagePlaneSelectorService;

// TODO transform as an OSGI service
public class HPRegistry {

  private static final List<Class<? extends HPCategoryService>> initialCategories =
      List.of(HPComparatorCategoryService.class, HPSelectorCategoryService.class);

  private static final HPRegistry registry = new HPRegistry();

  private final Map<Class<? extends HPCategoryService>, List<HPCategoryService>> categoryMap =
      new HashMap<>();

  private HPRegistry() {
    initialCategories.forEach(c -> categoryMap.put(c, new ArrayList<>()));
    registerServiceProviders();
  }

  public static HPRegistry getHPRegistry() {
    return registry;
  }

  private void registerServiceProviders() {
    registerServiceProvider(new ImagePlaneSelectorService());
    registerServiceProvider(new AlongAxisComparatorService());
    registerServiceProvider(new ByAcqTimeComparatorService());
  }

  public <C extends HPCategoryService> void deregisterAll(Class<C> category) {
    List<HPCategoryService> reg = categoryMap.get(category);
    if (reg == null) {
      throw new IllegalArgumentException("category unknown!");
    }
    reg.clear();
  }

  public void deregisterAll() {
    for (List<HPCategoryService> reg : categoryMap.values()) {
      reg.clear();
    }
  }

  public <C extends HPCategoryService> void registerServiceProvider(C provider) {
    if (provider == null) {
      throw new IllegalArgumentException("provider == null!");
    }
    categoryMap.forEach(
        (k, v) -> {
          if (k.isInstance(provider)) {
            v.add(provider);
          }
        });
  }

  public <C extends HPCategoryService> boolean deregisterServiceProvider(
      C provider, Class<C> category) {
    if (provider == null) {
      throw new IllegalArgumentException("provider == null!");
    }
    List<HPCategoryService> reg = categoryMap.get(category);
    if (reg == null) {
      throw new IllegalArgumentException("category unknown!");
    }
    if (!category.isInstance(provider)) {
      throw new ClassCastException();
    }
    return reg.remove(provider);
  }

  public <C extends HPCategoryService> Iterator<HPCategoryService> getServiceProviders(
      Class<C> category) {
    List<HPCategoryService> reg = categoryMap.get(category);
    if (reg == null) {
      throw new IllegalArgumentException("category unknown!");
    }
    return reg.iterator();
  }
}
