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

/**
 * Functional interface for processing and transforming objects of type T.
 *
 * <p>This interface represents an operation that takes a single input argument and returns a result
 * of the same type. It is typically used for in-place transformations or processing operations
 * where the input object may be modified or replaced.
 *
 * <p>Example usage:
 *
 * <pre>{@code
 * Editable<String> stringProcessor = s -> s.toUpperCase();
 * String result = stringProcessor.process("hello"); // Returns "HELLO"
 *
 * Editable<List<Integer>> listProcessor = list -> {
 *   list.sort(Integer::compareTo);
 *   return list;
 * };
 * }</pre>
 *
 * @param <T> the type of the input and output of the operation
 * @author Nicolas Roduit
 */
@FunctionalInterface
public interface Editable<T> {
  /**
   * Applies this operation to the given argument.
   *
   * @param input the input argument
   * @return the processed result
   */
  T process(T input);
}
