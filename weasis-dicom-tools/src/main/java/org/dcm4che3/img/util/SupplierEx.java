/*
 * Copyright (c) 2009-2021 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.dcm4che3.img.util;

import java.util.Objects;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * A functional interface representing a supplier that may throw a checked exception.
 *
 * <p>This interface is similar to {@link Supplier} but allows throwing checked exceptions. It
 * provides additional utility methods for composition, transformation, and error handling.
 *
 * @param <T> the type of results supplied by this supplier
 * @param <E> the type of exception that may be thrown
 * @author Nicolas Roduit
 */
@FunctionalInterface
public interface SupplierEx<T, E extends Exception> {

  /**
   * Gets a result, potentially throwing a checked exception.
   *
   * @return a result
   * @throws E if unable to supply a result
   */
  T get() throws E;

  /**
   * Returns a supplier that always returns the given value.
   *
   * @param <T> the type of the supplied value
   * @param <E> the exception type (not thrown)
   * @param value the value to supply
   * @return a supplier that always returns the given value
   */
  static <T, E extends Exception> SupplierEx<T, E> of(T value) {
    return () -> value;
  }

  /**
   * Converts this supplier to a standard {@link Supplier} by wrapping exceptions in
   * RuntimeException.
   *
   * @return a supplier that wraps exceptions in RuntimeException
   */
  default Supplier<T> unchecked() {
    return () -> {
      try {
        return get();
      } catch (Exception e) {
        throw new RuntimeException(e);
      }
    };
  }

  /**
   * Returns a supplier that applies the given function to the result of this supplier.
   *
   * @param <U> the type of the result of the mapping function
   * @param mapper the function to apply to the result
   * @return a supplier that applies the mapping function to this supplier's result
   * @throws NullPointerException if mapper is null
   */
  default <U> SupplierEx<U, E> map(Function<? super T, ? extends U> mapper) {
    Objects.requireNonNull(mapper, "Mapper function cannot be null");
    return () -> mapper.apply(get());
  }

  /**
   * Returns a supplier that returns the result of this supplier if it's not null, otherwise returns
   * the result of the given fallback supplier.
   *
   * @param fallback the fallback supplier to use if this supplier returns null
   * @return a supplier that provides fallback behavior for null values
   * @throws NullPointerException if fallback is null
   */
  default SupplierEx<T, E> orElse(SupplierEx<? extends T, ? extends E> fallback) {
    Objects.requireNonNull(fallback, "Fallback supplier cannot be null");
    return () -> {
      T result = get();
      return result != null ? result : fallback.get();
    };
  }

  /**
   * Returns a supplier that returns the result of this supplier if it's not null, otherwise returns
   * the given default value.
   *
   * @param defaultValue the default value to return if this supplier returns null
   * @return a supplier that provides fallback behavior for null values
   */
  default SupplierEx<T, E> orElse(T defaultValue) {
    return () -> {
      T result = get();
      return result != null ? result : defaultValue;
    };
  }

  /**
   * Creates a memoized version of this supplier that caches the result after first invocation.
   *
   * <p>This is thread-safe and ensures the original supplier is called only once, even under
   * concurrent access. Subsequent calls return the cached value without re-evaluation.
   *
   * @return a memoized supplier that caches the result of the first call
   */
  default SupplierEx<T, E> memoized() {
    return new MemoizedSupplierEx<>(this);
  }

  /** Thread-safe memoized supplier implementation. */
  final class MemoizedSupplierEx<T, E extends Exception> implements SupplierEx<T, E> {
    private final SupplierEx<T, E> original;
    private volatile boolean computed = false;
    private T cachedValue;
    private E cachedException;

    private MemoizedSupplierEx(SupplierEx<T, E> original) {
      this.original = Objects.requireNonNull(original, "Original supplier cannot be null");
    }

    @Override
    public T get() throws E {
      if (!computed) {
        synchronized (this) {
          if (!computed) {
            try {
              cachedValue = original.get();
            } catch (Exception e) {
              @SuppressWarnings("unchecked")
              E typedException = (E) e;
              cachedException = typedException;
            } finally {
              computed = true;
            }
          }
        }
      }

      if (cachedException != null) {
        throw cachedException;
      }
      return cachedValue;
    }
  }
}
