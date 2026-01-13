/*
 * Copyright (c) 2025 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.dcm4che3.img.util;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.*;

import java.time.format.DateTimeParseException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

/**
 * Tests for {@link SupplierEx#memoized()}.
 *
 * <p>The tests verify that the memoized supplier:
 *
 * <ul>
 *   <li>Caches successful results and avoids repeated evaluation.
 *   <li>Caches thrown exceptions and rethrows the same exception on subsequent calls.
 *   <li>Does not attempt to recompute after a failure.
 * </ul>
 */
class SupplierExTest {

  @Test
  void memoized_doesNotReevaluateAfterFailure() {
    AtomicInteger counter = new AtomicInteger(0);
    SupplierEx<String, RuntimeException> original =
        () -> {
          counter.incrementAndGet();
          throw new RuntimeException("failure");
        };

    SupplierEx<String, RuntimeException> memo = original.memoized();

    RuntimeException ex = assertThrows(RuntimeException.class, memo::get);
    assertEquals("failure", ex.getMessage());
    assertEquals(1, counter.get());

    RuntimeException ex2 = assertThrows(RuntimeException.class, memo::get);
    assertEquals("failure", ex2.getMessage());
    assertEquals(1, counter.get());
  }

  @Nested
  @TestInstance(TestInstance.Lifecycle.PER_CLASS)
  class Memoization_Tests {

    @Test
    void should_return_same_value_on_multiple_invocations() throws Exception {
      var counter = new AtomicInteger(0);
      SupplierEx<Integer, Exception> original = counter::incrementAndGet;
      var memoized = original.memoized();

      int firstResult = memoized.get();
      assertEquals(1, firstResult);

      // Subsequent calls should return the same value
      for (int i = 0; i < 10; i++) {
        assertEquals(firstResult, memoized.get());
      }

      // Counter should only be incremented once
      assertEquals(1, counter.get());
    }

    @Test
    void should_be_thread_safe() throws Exception {
      var invocationCount = new AtomicInteger(0);
      var resultValue = new AtomicInteger(0);

      SupplierEx<Integer, Exception> original =
          () -> {
            invocationCount.incrementAndGet();
            // Simulate some computation time
            Thread.sleep(10);
            return resultValue.incrementAndGet();
          };

      var memoized = original.memoized();

      ExecutorService executor = Executors.newFixedThreadPool(10);
      var futures =
          IntStream.range(0, 100)
              .mapToObj(
                  i ->
                      CompletableFuture.supplyAsync(
                          () -> {
                            try {
                              return memoized.get();
                            } catch (Exception e) {
                              throw new RuntimeException(e);
                            }
                          },
                          executor))
              .toList();

      // All futures should complete with the same value
      var expectedValue = futures.get(0).get();
      for (var future : futures) {
        assertEquals(expectedValue, future.get());
      }

      // Original supplier should only be called once
      assertEquals(1, invocationCount.get());

      executor.shutdown();
      assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));
    }

    @Test
    void should_propagate_and_cache_exceptions() {
      var invocationCount = new AtomicInteger(0);
      SupplierEx<String, RuntimeException> failingSupplier =
          () -> {
            invocationCount.incrementAndGet();
            throw new RuntimeException("Test exception");
          };

      var memoized = failingSupplier.memoized();

      // First call should throw
      var firstException = assertThrows(RuntimeException.class, memoized::get);
      assertEquals("Test exception", firstException.getMessage());

      // Second call should throw the same cached exception
      var secondException = assertThrows(RuntimeException.class, memoized::get);
      assertEquals("Test exception", secondException.getMessage());

      // Original supplier should only be called once
      assertEquals(1, invocationCount.get());
    }

    @Test
    void should_handle_null_values() throws Exception {
      var invocationCount = new AtomicInteger(0);
      SupplierEx<String, Exception> nullSupplier =
          () -> {
            invocationCount.incrementAndGet();
            return null;
          };
      var memoized = nullSupplier.memoized();

      assertNull(memoized.get());
      assertNull(memoized.get()); // Should return cached null

      assertEquals(1, invocationCount.get());
    }

    @Test
    void should_maintain_generic_exception_type() {
      SupplierEx<String, DateTimeParseException> supplier =
          () -> {
            throw new DateTimeParseException("Invalid date", "20231301", 6);
          };

      var memoized = supplier.memoized();

      assertThrows(DateTimeParseException.class, memoized::get);
    }
  }
}
