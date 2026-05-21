/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.junit;

import java.lang.annotation.Annotation;
import java.util.Locale;
import java.util.Optional;
import java.util.TimeZone;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ExtensionContext.Namespace;
import org.junit.jupiter.api.extension.ExtensionContext.Store;
import org.junit.platform.commons.support.AnnotationSupport;

/**
 * Replaces junit-pioneer's {@code @DefaultLocale} / {@code @DefaultTimeZone}. Method-level
 * annotations override class-level; class-level annotations walk the enclosing-class chain so
 * {@code @Nested} tests inherit the outer setting.
 */
class LocaleTimeZoneExtension implements BeforeEachCallback, AfterEachCallback {

  private static final Namespace NAMESPACE = Namespace.create(LocaleTimeZoneExtension.class);
  private static final String LOCALE_KEY = "saved-locale";
  private static final String TIMEZONE_KEY = "saved-timezone";

  @Override
  public void beforeEach(ExtensionContext context) {
    Store store = context.getStore(NAMESPACE);
    findAnnotation(context, DefaultLocale.class)
        .ifPresent(
            loc -> {
              store.put(LOCALE_KEY, Locale.getDefault());
              Locale.setDefault(toLocale(loc));
            });
    findAnnotation(context, DefaultTimeZone.class)
        .ifPresent(
            tz -> {
              store.put(TIMEZONE_KEY, TimeZone.getDefault());
              TimeZone.setDefault(TimeZone.getTimeZone(tz.value()));
            });
  }

  @Override
  public void afterEach(ExtensionContext context) {
    Store store = context.getStore(NAMESPACE);
    Locale savedLocale = store.remove(LOCALE_KEY, Locale.class);
    if (savedLocale != null) {
      Locale.setDefault(savedLocale);
    }
    TimeZone savedTimeZone = store.remove(TIMEZONE_KEY, TimeZone.class);
    if (savedTimeZone != null) {
      TimeZone.setDefault(savedTimeZone);
    }
  }

  private static Locale toLocale(DefaultLocale loc) {
    Locale.Builder builder = new Locale.Builder();
    if (!loc.language().isEmpty()) {
      builder.setLanguage(loc.language());
    }
    if (!loc.country().isEmpty()) {
      builder.setRegion(loc.country());
    }
    if (!loc.variant().isEmpty()) {
      builder.setVariant(loc.variant());
    }
    return builder.build();
  }

  private static <A extends Annotation> Optional<A> findAnnotation(
      ExtensionContext context, Class<A> type) {
    Optional<A> onElement =
        context.getElement().flatMap(e -> AnnotationSupport.findAnnotation(e, type));
    if (onElement.isPresent()) {
      return onElement;
    }
    ExtensionContext current = context;
    while (current != null) {
      Optional<A> onClass =
          current.getTestClass().flatMap(cls -> AnnotationSupport.findAnnotation(cls, type));
      if (onClass.isPresent()) {
        return onClass;
      }
      current = current.getParent().orElse(null);
    }
    return Optional.empty();
  }
}
