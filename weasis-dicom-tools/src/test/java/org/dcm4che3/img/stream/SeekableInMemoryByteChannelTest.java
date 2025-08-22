/*
 * Copyright (c) 2024 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.dcm4che3.img.stream;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@DisplayNameGeneration(ReplaceUnderscores.class)
class SeekableInMemoryByteChannelTest {

  private static final byte[] EMPTY_ARRAY = new byte[0];
  private static final String HELLO_WORLD = "Hello, World!";
  private static final List<String> TEST_CHUNKS = List.of("First", "Second", "Third", "Fourth");

  @Nested
  class Constructor_Tests {

    @Test
    void default_constructor_creates_empty_channel() {
      try (var channel = new SeekableInMemoryByteChannel()) {

        assertEquals(0, channel.size());
        assertEquals(0, channel.position());
        assertTrue(channel.isOpen());
        assertTrue(channel.array().length >= 32);
      }
    }

    @Test
    void constructor_with_capacity_creates_empty_channel_with_specified_capacity() {
      int capacity = 100;
      try (var channel = new SeekableInMemoryByteChannel(capacity)) {

        assertEquals(0, channel.size());
        assertEquals(0, channel.position());
        assertTrue(channel.isOpen());
        assertEquals(capacity, channel.array().length);
      }
    }

    @Test
    void constructor_with_byte_array_creates_channel_with_data() {
      byte[] testData = createSequentialData(50);
      try (var channel = new SeekableInMemoryByteChannel(testData)) {

        assertEquals(50, channel.size());
        assertEquals(0, channel.position());
        assertTrue(channel.isOpen());
        assertArrayEquals(testData, Arrays.copyOf(channel.array(), 50));
      }
    }

    @Test
    void constructor_throws_exception_for_null_array() {
      assertThrows(NullPointerException.class, () -> new SeekableInMemoryByteChannel(null));
    }

    @Test
    void constructor_throws_exception_for_negative_capacity() {
      assertThrows(IllegalArgumentException.class, () -> new SeekableInMemoryByteChannel(-1));
    }

    @Test
    void constructor_with_zero_capacity_works() {
      try (var channel = new SeekableInMemoryByteChannel(0)) {

        assertEquals(0, channel.size());
        assertEquals(0, channel.array().length);
      }
    }

    @Test
    void constructor_with_empty_array_works() {
      try (var channel = new SeekableInMemoryByteChannel(EMPTY_ARRAY)) {

        assertEquals(0, channel.size());
        assertEquals(0, channel.array().length);
      }
    }
  }

  @Nested
  class Basic_Operations {

    @Test
    void write_and_read_simple_data() throws IOException {
      byte[] testData;
      ByteBuffer readBuffer;
      int read;
      try (var channel = new SeekableInMemoryByteChannel()) {
        testData = HELLO_WORLD.getBytes(StandardCharsets.UTF_8);

        int written = channel.write(ByteBuffer.wrap(testData));

        assertEquals(testData.length, written);
        assertEquals(testData.length, channel.size());
        assertEquals(testData.length, channel.position());

        channel.position(0);
        readBuffer = ByteBuffer.allocate(testData.length);
        read = channel.read(readBuffer);
      }

      assertEquals(testData.length, read);
      assertEquals(HELLO_WORLD, new String(readBuffer.array(), StandardCharsets.UTF_8));
    }

    @Test
    void multiple_writes_expand_buffer_correctly() throws IOException {
      StringBuilder expected;
      ByteBuffer readBuffer;
      try (var channel = new SeekableInMemoryByteChannel(10)) {
        expected = new StringBuilder();

        for (String chunk : TEST_CHUNKS) {
          channel.write(ByteBuffer.wrap(chunk.getBytes(StandardCharsets.UTF_8)));
          expected.append(chunk);
        }

        assertEquals(expected.length(), channel.size());

        channel.position(0);
        readBuffer = ByteBuffer.allocate((int) channel.size());
        channel.read(readBuffer);
      }
      assertEquals(expected.toString(), new String(readBuffer.array(), StandardCharsets.UTF_8));
    }

    @Test
    void read_returns_minus_one_at_eof() throws IOException {
      byte[] testData = createSequentialData(20);
      try (var channel = new SeekableInMemoryByteChannel(testData)) {

        var buffer = ByteBuffer.allocate(20);
        assertEquals(20, channel.read(buffer));

        buffer.clear();
        assertEquals(-1, channel.read(buffer));
      }
    }

    @Test
    void partial_read_when_buffer_larger_than_available_data() throws IOException {
      byte[] testData = createSequentialData(10);
      try (var channel = new SeekableInMemoryByteChannel(testData)) {

        var largeBuffer = ByteBuffer.allocate(20);
        int read = channel.read(largeBuffer);

        assertEquals(10, read);
        assertEquals(10, channel.position());
      }
    }

    @Test
    void read_with_no_remaining_buffer_capacity_returns_eof() throws IOException {
      try (var channel = new SeekableInMemoryByteChannel()) {
        channel.write(ByteBuffer.wrap(createSequentialData(10)));
        channel.position(0);

        var fullBuffer = ByteBuffer.allocate(0);
        assertEquals(-1, channel.read(fullBuffer));
        assertEquals(0, channel.position());
      }
    }
  }

  @Nested
  class Position_Management {

    @Test
    void position_can_be_set_within_bounds() throws IOException {
      byte[] testData = createSequentialData(100);
      try (var channel = new SeekableInMemoryByteChannel(testData)) {

        channel.position(50);
        assertEquals(50, channel.position());

        channel.position(0);
        assertEquals(0, channel.position());

        channel.position(100);
        assertEquals(100, channel.position());
      }
    }

    @Test
    void position_throws_exception_for_invalid_values() {
      try (var channel = new SeekableInMemoryByteChannel()) {

        assertThrows(IllegalArgumentException.class, () -> channel.position(-1));
        assertThrows(
            IllegalArgumentException.class, () -> channel.position(Integer.MAX_VALUE + 1L));
      }
    }

    @Test
    void position_can_be_set_beyond_current_size() throws IOException {
      try (var channel = new SeekableInMemoryByteChannel()) {
        channel.write(ByteBuffer.wrap(createSequentialData(10)));

        channel.position(50);
        assertEquals(50, channel.position());
        assertEquals(10, channel.size());

        channel.write(ByteBuffer.wrap("test".getBytes(StandardCharsets.UTF_8)));
        assertEquals(54, channel.size());
      }
    }

    @Test
    void read_and_write_update_position_correctly() throws IOException {
      try (var channel = new SeekableInMemoryByteChannel()) {
        byte[] testData = createSequentialData(20);

        channel.write(ByteBuffer.wrap(testData));
        assertEquals(20, channel.position());

        channel.position(5);
        var buffer = ByteBuffer.allocate(10);
        channel.read(buffer);
        assertEquals(15, channel.position());
      }
    }

    @Test
    void position_at_integer_max_value_works() throws IOException {
      try (var channel = new SeekableInMemoryByteChannel()) {
        channel.position(Integer.MAX_VALUE);
        assertEquals(Integer.MAX_VALUE, channel.position());
      }
    }
  }

  @Nested
  class Truncate_Operations {

    @Test
    void truncate_reduces_size_when_new_size_is_smaller() throws IOException {
      byte[] originalData;
      ByteBuffer buffer;
      try (var channel = new SeekableInMemoryByteChannel()) {
        originalData = createSequentialData(50);
        channel.write(ByteBuffer.wrap(originalData));

        channel.truncate(30);
        assertEquals(30, channel.size());

        channel.position(0);
        buffer = ByteBuffer.allocate(30);
        channel.read(buffer);
      }
      byte[] expected = Arrays.copyOf(originalData, 30);
      assertArrayEquals(expected, buffer.array());
    }

    @Test
    void truncate_does_not_increase_size() throws IOException {
      try (var channel = new SeekableInMemoryByteChannel()) {
        channel.write(ByteBuffer.wrap(createSequentialData(20)));

        channel.truncate(50);
        assertEquals(20, channel.size());
      }
    }

    @Test
    void truncate_adjusts_position_when_position_exceeds_new_size() throws IOException {
      try (var channel = new SeekableInMemoryByteChannel()) {
        channel.write(ByteBuffer.wrap(createSequentialData(50)));
        channel.position(40);

        channel.truncate(25);
        assertEquals(25, channel.size());
        assertEquals(25, channel.position());
      }
    }

    @Test
    void truncate_throws_exception_for_invalid_values() {
      try (var channel = new SeekableInMemoryByteChannel()) {

        assertThrows(IllegalArgumentException.class, () -> channel.truncate(-1));
        assertThrows(IllegalArgumentException.class, () -> channel.truncate(Long.MAX_VALUE));
      }
    }

    @Test
    void truncate_to_zero_creates_empty_channel() throws IOException {
      try (var channel = new SeekableInMemoryByteChannel()) {
        channel.write(ByteBuffer.wrap(createSequentialData(30)));

        channel.truncate(0);
        assertEquals(0, channel.size());
        assertEquals(0, channel.position());

        var buffer = ByteBuffer.allocate(10);
        assertEquals(-1, channel.read(buffer));
      }
    }

    @Test
    void truncate_to_current_size_has_no_effect() throws IOException {
      try (var channel = new SeekableInMemoryByteChannel()) {
        byte[] testData = createSequentialData(25);
        channel.write(ByteBuffer.wrap(testData));
        channel.position(15);

        channel.truncate(25);
        assertEquals(25, channel.size());
        assertEquals(15, channel.position());
      }
    }
  }

  @Nested
  class Channel_State_Management {

    @Test
    void new_channel_is_open() {
      try (var channel = new SeekableInMemoryByteChannel()) {
        assertTrue(channel.isOpen());
      }
    }

    @Test
    void closed_channel_reports_correct_state() {
      var channel = new SeekableInMemoryByteChannel();
      channel.close();
      assertFalse(channel.isOpen());
    }

    @Test
    void operations_throw_exception_on_closed_channel() {
      var channel = new SeekableInMemoryByteChannel();
      channel.close();

      var buffer = ByteBuffer.allocate(10);
      assertThrows(ClosedChannelException.class, () -> channel.read(buffer));
      assertThrows(ClosedChannelException.class, () -> channel.write(buffer));
      assertThrows(ClosedChannelException.class, () -> channel.position(0));
    }

    @Test
    void position_and_size_accessible_on_closed_channel() throws IOException {
      var channel = new SeekableInMemoryByteChannel();
      channel.write(ByteBuffer.wrap(createSequentialData(20)));
      channel.position(10);

      long expectedSize = channel.size();
      long expectedPosition = channel.position();
      channel.close();

      assertEquals(expectedSize, channel.size());
      assertEquals(expectedPosition, channel.position());
    }

    @Test
    void truncate_works_on_closed_channel() throws IOException {
      var channel = new SeekableInMemoryByteChannel();
      channel.write(ByteBuffer.wrap(createSequentialData(30)));
      channel.close();

      channel.truncate(15);
      assertEquals(15, channel.size());
    }

    @Test
    void multiple_closes_are_safe() {
      try (var channel = new SeekableInMemoryByteChannel()) {

        assertDoesNotThrow(
            () -> {
              channel.close();
              channel.close();
              channel.close();
            });

        assertFalse(channel.isOpen());
      }
    }
  }

  @Nested
  class Large_Data_Operations {

    @ParameterizedTest
    @ValueSource(ints = {1024, 8192, 65536, 1048576})
    void handle_various_data_sizes_efficiently(int size) throws IOException {
      try (var channel = new SeekableInMemoryByteChannel()) {
        var testData = createRandomData(size, 42L);

        channel.write(ByteBuffer.wrap(testData));
        assertEquals(size, channel.size());

        channel.position(0);
        var readData = new byte[size];
        int totalRead = 0;
        var buffer = ByteBuffer.allocate(Math.min(4096, size));

        while (totalRead < size) {
          buffer.clear();
          int read = channel.read(buffer);
          assertTrue(read > 0);
          buffer.flip();
          buffer.get(readData, totalRead, read);
          totalRead += read;
        }

        assertArrayEquals(testData, readData);
      }
    }

    @Test
    void buffer_growth_handles_multiple_resize_operations() throws IOException {
      try (var channel = new SeekableInMemoryByteChannel(8)) {
        int[] chunkSizes = {10, 50, 200, 800, 3200};
        int totalSize = 0;

        for (int chunkSize : chunkSizes) {
          byte[] chunk = createSequentialData(chunkSize);
          channel.write(ByteBuffer.wrap(chunk));
          totalSize += chunkSize;
          assertEquals(totalSize, channel.size());
        }

        channel.position(0);
        byte[] allData = new byte[totalSize];
        int read = channel.read(ByteBuffer.wrap(allData));
        assertEquals(totalSize, read);
        verifySequentialDataIntegrity(allData, chunkSizes);
      }
    }
  }

  @Nested
  class Edge_Cases_And_Error_Conditions {

    @Test
    void handle_empty_write_operations() throws IOException {
      try (var channel = new SeekableInMemoryByteChannel()) {
        var emptyBuffer = ByteBuffer.allocate(0);
        int written = channel.write(emptyBuffer);

        assertEquals(0, written);
        assertEquals(0, channel.size());
        assertEquals(0, channel.position());
      }
    }

    @Test
    void handle_write_at_various_positions() throws IOException {
      var channel = new SeekableInMemoryByteChannel();

      channel.position(0);
      channel.write(ByteBuffer.wrap("start".getBytes(StandardCharsets.UTF_8)));

      channel.position(10);
      channel.write(ByteBuffer.wrap("middle".getBytes(StandardCharsets.UTF_8)));

      channel.position(20);
      channel.write(ByteBuffer.wrap("end".getBytes(StandardCharsets.UTF_8)));

      assertEquals(23, channel.size());

      verifyDataAtPositions(channel);
    }

    @Test
    void array_method_returns_internal_array_reference() throws IOException {
      byte[] testData;
      byte[] internalArray;
      try (var channel = new SeekableInMemoryByteChannel()) {
        testData = createSequentialData(20);
        channel.write(ByteBuffer.wrap(testData));

        internalArray = channel.array();
      }
      assertNotNull(internalArray);
      assertTrue(internalArray.length >= 20);

      for (int i = 0; i < 20; i++) {
        assertEquals(testData[i], internalArray[i]);
      }
    }

    @Test
    void write_at_position_creates_sparse_data() throws IOException {
      ByteBuffer buffer;
      try (var channel = new SeekableInMemoryByteChannel()) {

        channel.position(100);
        byte[] data = "test".getBytes(StandardCharsets.UTF_8);
        channel.write(ByteBuffer.wrap(data));

        assertEquals(104, channel.size());

        // Check that positions 0-99 contain zeros
        channel.position(0);
        buffer = ByteBuffer.allocate(100);
        channel.read(buffer);
      }

      for (byte b : buffer.array()) {
        assertEquals(0, b);
      }
    }
  }

  @Nested
  class Performance_And_Memory_Tests {

    @Test
    void verify_exponential_growth_strategy() throws IOException {
      try (var channel = new SeekableInMemoryByteChannel(1)) {

        byte[] chunk = createSequentialData(100);
        for (int i = 0; i < 10; i++) {
          channel.write(ByteBuffer.wrap(chunk));
        }

        assertEquals(1000, channel.size());
        assertTrue(channel.array().length >= 1000);
        assertTrue(channel.array().length < 4000);
      }
    }

    @Test
    void verify_memory_efficiency_for_exact_size_allocations() {
      byte[] testData = createSequentialData(1000);
      try (var channel = new SeekableInMemoryByteChannel(testData)) {

        assertEquals(1000, channel.size());
        assertEquals(1000, channel.array().length);
      }
    }

    @Test
    void large_capacity_allocation_works() {
      int largeCapacity = 10_000_000;
      try (var channel = new SeekableInMemoryByteChannel(largeCapacity)) {

        assertEquals(0, channel.size());
        assertEquals(largeCapacity, channel.array().length);
      }
    }
  }

  // Utility methods

  private static byte[] createSequentialData(int size) {
    byte[] data = new byte[size];
    for (int i = 0; i < size; i++) {
      data[i] = (byte) (i % 256);
    }
    return data;
  }

  private static byte[] createRandomData(int size, long seed) {
    byte[] data = new byte[size];
    new Random(seed).nextBytes(data);
    return data;
  }

  private void verifySequentialDataIntegrity(byte[] allData, int[] chunkSizes) {
    int offset = 0;
    for (int chunkSize : chunkSizes) {
      for (int i = 0; i < chunkSize; i++) {
        byte expected = (byte) (i % 256);
        byte actual = allData[offset + i];
        assertEquals(
            expected,
            actual,
            "Mismatch at global index %d (chunk offset %d): expected %d, got %d"
                .formatted(offset + i, i, expected & 0xFF, actual & 0xFF));
      }
      offset += chunkSize;
    }
  }

  private void verifyDataAtPositions(SeekableInMemoryByteChannel channel) throws IOException {
    channel.position(0);
    var buffer = ByteBuffer.allocate(5);
    channel.read(buffer);
    assertEquals("start", new String(buffer.array(), StandardCharsets.UTF_8));

    channel.position(10);
    buffer = ByteBuffer.allocate(6);
    channel.read(buffer);
    assertEquals("middle", new String(buffer.array(), StandardCharsets.UTF_8));

    channel.position(20);
    buffer = ByteBuffer.allocate(3);
    channel.read(buffer);
    assertEquals("end", new String(buffer.array(), StandardCharsets.UTF_8));
  }
}
