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
import java.util.Arrays;
import java.util.Random;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class SeekableInMemoryByteChannelTest {

  @Nested
  @DisplayName("Constructor Tests")
  class ConstructorTests {

    @Test
    @DisplayName("Default constructor creates empty channel")
    void defaultConstructorCreatesEmptyChannel() {
      SeekableInMemoryByteChannel channel = new SeekableInMemoryByteChannel();

      assertEquals(0, channel.size());
      assertEquals(0, channel.position());
      assertTrue(channel.isOpen());
      assertTrue(channel.array().length >= 32); // Should have default capacity
    }

    @Test
    @DisplayName("Constructor with capacity creates empty channel with specified capacity")
    void constructorWithCapacityCreatesEmptyChannel() {
      int capacity = 100;
      SeekableInMemoryByteChannel channel = new SeekableInMemoryByteChannel(capacity);

      assertEquals(0, channel.size());
      assertEquals(0, channel.position());
      assertTrue(channel.isOpen());
      assertEquals(capacity, channel.array().length);
    }

    @Test
    @DisplayName("Constructor with byte array creates channel with data")
    void constructorWithByteArrayCreatesChannelWithData() {
      byte[] testData = createTestData(50);
      SeekableInMemoryByteChannel channel = new SeekableInMemoryByteChannel(testData);

      assertEquals(50, channel.size());
      assertEquals(0, channel.position());
      assertTrue(channel.isOpen());
      assertArrayEquals(testData, Arrays.copyOf(channel.array(), 50));
    }

    @Test
    @DisplayName("Constructor throws exception for null array")
    void constructorThrowsExceptionForNullArray() {
      assertThrows(
          IllegalArgumentException.class, () -> new SeekableInMemoryByteChannel((byte[]) null));
    }

    @Test
    @DisplayName("Constructor throws exception for negative capacity")
    void constructorThrowsExceptionForNegativeCapacity() {
      assertThrows(IllegalArgumentException.class, () -> new SeekableInMemoryByteChannel(-1));
    }
  }

  @Nested
  @DisplayName("Basic Operations")
  class BasicOperationsTests {

    @Test
    @DisplayName("Write and read simple data")
    void writeAndReadSimpleData() throws IOException {
      SeekableInMemoryByteChannel channel = new SeekableInMemoryByteChannel();
      String testString = "Hello, World!";
      byte[] testData = testString.getBytes();

      // Write data
      int written = channel.write(ByteBuffer.wrap(testData));
      assertEquals(testData.length, written);
      assertEquals(testData.length, channel.size());
      assertEquals(testData.length, channel.position());

      // Read data back
      channel.position(0);
      ByteBuffer readBuffer = ByteBuffer.allocate(testData.length);
      int read = channel.read(readBuffer);

      assertEquals(testData.length, read);
      assertEquals(testString, new String(readBuffer.array()));
    }

    @Test
    @DisplayName("Multiple writes expand buffer correctly")
    void multipleWritesExpandBuffer() throws IOException {
      SeekableInMemoryByteChannel channel = new SeekableInMemoryByteChannel(10);

      // Write multiple chunks that exceed initial capacity
      String[] chunks = {"First", "Second", "Third", "Fourth"};
      StringBuilder expected = new StringBuilder();

      for (String chunk : chunks) {
        channel.write(ByteBuffer.wrap(chunk.getBytes()));
        expected.append(chunk);
      }

      assertEquals(expected.length(), channel.size());

      // Verify all data is readable
      channel.position(0);
      ByteBuffer readBuffer = ByteBuffer.allocate((int) channel.size());
      channel.read(readBuffer);
      assertEquals(expected.toString(), new String(readBuffer.array()));
    }

    @Test
    @DisplayName("Read returns -1 at EOF")
    void readReturnsMinusOneAtEOF() throws IOException {
      byte[] testData = createTestData(20);
      SeekableInMemoryByteChannel channel = new SeekableInMemoryByteChannel(testData);

      // Read all data
      ByteBuffer buffer = ByteBuffer.allocate(20);
      assertEquals(20, channel.read(buffer));

      // Try to read beyond EOF
      buffer.clear();
      assertEquals(-1, channel.read(buffer));
    }

    @Test
    @DisplayName("Partial read when buffer is larger than available data")
    void partialReadWhenBufferLargerThanData() throws IOException {
      byte[] testData = createTestData(10);
      SeekableInMemoryByteChannel channel = new SeekableInMemoryByteChannel(testData);

      ByteBuffer largeBuffer = ByteBuffer.allocate(20);
      int read = channel.read(largeBuffer);

      assertEquals(10, read);
      assertEquals(10, channel.position());
    }
  }

  @Nested
  @DisplayName("Position Management")
  class PositionManagementTests {

    @Test
    @DisplayName("Position can be set within bounds")
    void positionCanBeSetWithinBounds() throws IOException {
      byte[] testData = createTestData(100);
      SeekableInMemoryByteChannel channel = new SeekableInMemoryByteChannel(testData);

      channel.position(50);
      assertEquals(50, channel.position());

      channel.position(0);
      assertEquals(0, channel.position());

      channel.position(100);
      assertEquals(100, channel.position());
    }

    @Test
    @DisplayName("Position throws exception for invalid values")
    void positionThrowsExceptionForInvalidValues() {
      SeekableInMemoryByteChannel channel = new SeekableInMemoryByteChannel();

      assertThrows(IllegalArgumentException.class, () -> channel.position(-1));
      assertThrows(IllegalArgumentException.class, () -> channel.position(Integer.MAX_VALUE + 1L));
    }

    @Test
    @DisplayName("Position can be set beyond current size")
    void positionCanBeSetBeyondCurrentSize() throws IOException {
      SeekableInMemoryByteChannel channel = new SeekableInMemoryByteChannel();
      channel.write(ByteBuffer.wrap(createTestData(10)));

      // Set position beyond current size
      channel.position(50);
      assertEquals(50, channel.position());
      assertEquals(10, channel.size()); // Size unchanged

      // Write at this position
      channel.write(ByteBuffer.wrap("test".getBytes()));
      assertEquals(54, channel.size()); // Size now reflects the write
    }

    @Test
    @DisplayName("Read and write update position correctly")
    void readAndWriteUpdatePositionCorrectly() throws IOException {
      SeekableInMemoryByteChannel channel = new SeekableInMemoryByteChannel();
      byte[] testData = createTestData(20);

      // Write updates position
      channel.write(ByteBuffer.wrap(testData));
      assertEquals(20, channel.position());

      // Read updates position
      channel.position(5);
      ByteBuffer buffer = ByteBuffer.allocate(10);
      channel.read(buffer);
      assertEquals(15, channel.position());
    }
  }

  @Nested
  @DisplayName("Truncate Operations")
  class TruncateOperationsTests {

    @Test
    @DisplayName("Truncate reduces size when new size is smaller")
    void truncateReducesSizeWhenNewSizeIsSmaller() throws IOException {
      SeekableInMemoryByteChannel channel = new SeekableInMemoryByteChannel();
      channel.write(ByteBuffer.wrap(createTestData(50)));

      channel.truncate(30);
      assertEquals(30, channel.size());

      // Verify data integrity up to truncation point
      channel.position(0);
      ByteBuffer buffer = ByteBuffer.allocate(30);
      channel.read(buffer);
      byte[] expected = Arrays.copyOf(createTestData(50), 30);
      assertArrayEquals(expected, buffer.array());
    }

    @Test
    @DisplayName("Truncate does not increase size")
    void truncateDoesNotIncreaseSize() throws IOException {
      SeekableInMemoryByteChannel channel = new SeekableInMemoryByteChannel();
      channel.write(ByteBuffer.wrap(createTestData(20)));

      channel.truncate(50);
      assertEquals(20, channel.size()); // Size should remain unchanged
    }

    @Test
    @DisplayName("Truncate adjusts position when position exceeds new size")
    void truncateAdjustsPositionWhenPositionExceedsNewSize() throws IOException {
      SeekableInMemoryByteChannel channel = new SeekableInMemoryByteChannel();
      channel.write(ByteBuffer.wrap(createTestData(50)));
      channel.position(40);

      channel.truncate(25);
      assertEquals(25, channel.size());
      assertEquals(25, channel.position());
    }

    @Test
    @DisplayName("Truncate throws exception for invalid values")
    void truncateThrowsExceptionForInvalidValues() {
      SeekableInMemoryByteChannel channel = new SeekableInMemoryByteChannel();

      assertThrows(IllegalArgumentException.class, () -> channel.truncate(-1));
      assertThrows(IllegalArgumentException.class, () -> channel.truncate(Long.MAX_VALUE));
    }

    @Test
    @DisplayName("Truncate to zero creates empty channel")
    void truncateToZeroCreatesEmptyChannel() throws IOException {
      SeekableInMemoryByteChannel channel = new SeekableInMemoryByteChannel();
      channel.write(ByteBuffer.wrap(createTestData(30)));

      channel.truncate(0);
      assertEquals(0, channel.size());
      assertEquals(0, channel.position());

      // Should return EOF immediately
      ByteBuffer buffer = ByteBuffer.allocate(10);
      assertEquals(-1, channel.read(buffer));
    }
  }

  @Nested
  @DisplayName("Channel State Management")
  class ChannelStateManagementTests {

    @Test
    @DisplayName("New channel is open")
    void newChannelIsOpen() {
      SeekableInMemoryByteChannel channel = new SeekableInMemoryByteChannel();
      assertTrue(channel.isOpen());
    }

    @Test
    @DisplayName("Closed channel reports correct state")
    void closedChannelReportsCorrectState() {
      SeekableInMemoryByteChannel channel = new SeekableInMemoryByteChannel();
      channel.close();
      assertFalse(channel.isOpen());
    }

    @Test
    @DisplayName("Operations throw exception on closed channel")
    void operationsThrowExceptionOnClosedChannel() {
      SeekableInMemoryByteChannel channel = new SeekableInMemoryByteChannel();
      channel.close();

      ByteBuffer buffer = ByteBuffer.allocate(10);
      assertThrows(ClosedChannelException.class, () -> channel.read(buffer));
      assertThrows(ClosedChannelException.class, () -> channel.write(buffer));
      assertThrows(ClosedChannelException.class, () -> channel.position(0));
    }

    @Test
    @DisplayName("Position and size accessible on closed channel")
    void positionAndSizeAccessibleOnClosedChannel() throws IOException {
      SeekableInMemoryByteChannel channel = new SeekableInMemoryByteChannel();
      channel.write(ByteBuffer.wrap(createTestData(20)));
      channel.position(10);

      long expectedSize = channel.size();
      long expectedPosition = channel.position();

      channel.close();

      // These should not throw exceptions (contract violation)
      assertEquals(expectedSize, channel.size());
      assertEquals(expectedPosition, channel.position());
    }

    @Test
    @DisplayName("Truncate works on closed channel")
    void truncateWorksOnClosedChannel() throws IOException {
      SeekableInMemoryByteChannel channel = new SeekableInMemoryByteChannel();
      channel.write(ByteBuffer.wrap(createTestData(30)));
      channel.close();

      // Truncate should work even on closed channel (contract violation)
      assertDoesNotThrow(() -> channel.truncate(15));
      assertEquals(15, channel.size());
    }
  }

  @Nested
  @DisplayName("Large Data Operations")
  class LargeDataOperationsTests {

    @ParameterizedTest
    @ValueSource(ints = {1024, 8192, 65536, 1048576})
    @DisplayName("Handle various data sizes efficiently")
    void handleVariousDataSizesEfficiently(int size) throws IOException {
      SeekableInMemoryByteChannel channel = new SeekableInMemoryByteChannel();
      byte[] testData = createRandomData(size, 42L);

      // Write large data
      channel.write(ByteBuffer.wrap(testData));
      assertEquals(size, channel.size());

      // Read it back in chunks
      channel.position(0);
      byte[] readData = new byte[size];
      int totalRead = 0;
      ByteBuffer buffer = ByteBuffer.allocate(Math.min(4096, size));

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

    @Test
    @DisplayName("Buffer growth handles multiple resize operations")
    void bufferGrowthHandlesMultipleResizeOperations() throws IOException {
      SeekableInMemoryByteChannel channel = new SeekableInMemoryByteChannel(8);

      // Write progressively larger chunks to force multiple resizes
      int[] chunkSizes = {10, 50, 200, 800, 3200};
      int totalSize = 0;

      for (int chunkSize : chunkSizes) {
        byte[] chunk = createTestData(chunkSize);
        channel.write(ByteBuffer.wrap(chunk));
        totalSize += chunkSize;
        assertEquals(totalSize, channel.size());
      }

      // Verify all data is intact
      channel.position(0);
      byte[] allData = new byte[totalSize];
      int read = channel.read(ByteBuffer.wrap(allData));
      assertEquals(totalSize, read);

      // Verify data pattern integrity by checking each chunk separately
      int offset = 0;
      for (int chunkSize : chunkSizes) {
        for (int i = 0; i < chunkSize; i++) {
          byte expected = (byte) (i % 256);
          byte actual = allData[offset + i];
          assertEquals(
              expected,
              actual,
              String.format(
                  "Mismatch at global index %d (chunk offset %d): expected %d, got %d",
                  offset + i, i, expected & 0xFF, actual & 0xFF));
        }
        offset += chunkSize;
      }
    }
  }

  @Nested
  @DisplayName("Edge Cases and Error Conditions")
  class EdgeCasesTests {

    @Test
    @DisplayName("Handle empty write operations")
    void handleEmptyWriteOperations() throws IOException {
      SeekableInMemoryByteChannel channel = new SeekableInMemoryByteChannel();

      ByteBuffer emptyBuffer = ByteBuffer.allocate(0);
      int written = channel.write(emptyBuffer);

      assertEquals(0, written);
      assertEquals(0, channel.size());
      assertEquals(0, channel.position());
    }

    @Test
    @DisplayName("Handle empty read operations")
    void handleEmptyReadOperations() throws IOException {
      SeekableInMemoryByteChannel channel = new SeekableInMemoryByteChannel();
      channel.write(ByteBuffer.wrap(createTestData(10)));
      channel.position(0);

      ByteBuffer emptyBuffer = ByteBuffer.allocate(0);
      int read = channel.read(emptyBuffer);

      assertEquals(-1, read); // EOF
      assertEquals(0, channel.position()); // Position should not change
    }

    @Test
    @DisplayName("Handle write at various positions")
    void handleWriteAtVariousPositions() throws IOException {
      SeekableInMemoryByteChannel channel = new SeekableInMemoryByteChannel();

      // Write at position 0
      channel.position(0);
      channel.write(ByteBuffer.wrap("start".getBytes()));

      // Write at middle position
      channel.position(10);
      channel.write(ByteBuffer.wrap("middle".getBytes()));

      // Write at end
      channel.position(20);
      channel.write(ByteBuffer.wrap("end".getBytes()));

      assertEquals(23, channel.size());

      // Verify data at different positions
      channel.position(0);
      ByteBuffer buffer = ByteBuffer.allocate(5);
      channel.read(buffer);
      assertEquals("start", new String(buffer.array()));

      channel.position(10);
      buffer = ByteBuffer.allocate(6);
      channel.read(buffer);
      assertEquals("middle", new String(buffer.array()));

      channel.position(20);
      buffer = ByteBuffer.allocate(3);
      channel.read(buffer);
      assertEquals("end", new String(buffer.array()));
    }

    @Test
    @DisplayName("Array method returns internal array reference")
    void arrayMethodReturnsInternalArrayReference() throws IOException {
      SeekableInMemoryByteChannel channel = new SeekableInMemoryByteChannel();
      byte[] testData = createTestData(20);
      channel.write(ByteBuffer.wrap(testData));

      byte[] internalArray = channel.array();
      assertNotNull(internalArray);
      assertTrue(internalArray.length >= 20);

      // Verify the data is in the array
      for (int i = 0; i < 20; i++) {
        assertEquals(testData[i], internalArray[i]);
      }
    }
  }

  @Nested
  @DisplayName("Performance and Memory Tests")
  class PerformanceTests {

    @Test
    @DisplayName("Verify exponential growth strategy")
    void verifyExponentialGrowthStrategy() throws IOException {
      SeekableInMemoryByteChannel channel = new SeekableInMemoryByteChannel(1);

      // Write data that will cause multiple growth operations
      byte[] chunk = createTestData(100);
      for (int i = 0; i < 10; i++) {
        channel.write(ByteBuffer.wrap(chunk));
      }

      assertEquals(1000, channel.size());

      // Internal array should be larger than size due to exponential growth
      assertTrue(channel.array().length >= 1000);

      // But not excessively large (should be reasonable for exponential growth)
      assertTrue(channel.array().length < 4000);
    }

    @Test
    @DisplayName("Verify memory efficiency for exact size allocations")
    void verifyMemoryEfficiencyForExactSizeAllocations() {
      byte[] testData = createTestData(1000);
      SeekableInMemoryByteChannel channel = new SeekableInMemoryByteChannel(testData);

      assertEquals(1000, channel.size());
      assertEquals(1000, channel.array().length);
    }
  }

  // Test data generators
  private static byte[] createTestData(int size) {
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
}
