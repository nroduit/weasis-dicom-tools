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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SeekableInMemoryByteChannelTest {

  @Test
  @DisplayName("Verify position and size returns correct value after write operation")
  void shouldReturnCorrectSizeAfterWrite() throws IOException {
    try (SeekableInMemoryByteChannel channel = new SeekableInMemoryByteChannel()) {
      ByteBuffer buffer = ByteBuffer.wrap("test".getBytes());
      channel.write(buffer);
      assertEquals(4, channel.size());
      assertEquals(4, channel.position());

      channel.position(0);
      ByteBuffer readBuffer = ByteBuffer.allocate(4);
      channel.read(readBuffer);
      assertEquals("test", new String(readBuffer.array()));

      channel.close();
      assertFalse(channel.isOpen());
    }
  }

  @Test
  @DisplayName("Verify write operation throws exception after close operation")
  void shouldThrowExceptionAfterCloseOnWrite() {
    try (SeekableInMemoryByteChannel channel = new SeekableInMemoryByteChannel()) {
      channel.close();
      ByteBuffer buffer = ByteBuffer.wrap("test".getBytes());
      assertThrows(IOException.class, () -> channel.write(buffer));
    }
  }

  @Test
  @DisplayName("Verify truncate reduces size when newSize is less than current size")
  void shouldReduceSizeWhenNewSizeIsLess() throws IOException {
    try (SeekableInMemoryByteChannel channel = new SeekableInMemoryByteChannel(10)) {
      channel.truncate(5);
      assertEquals(5, channel.size());

      channel.truncate(5);
      assertEquals(5, channel.size());

      channel.truncate(10);
      assertEquals(5, channel.size());

      channel.position(10);
      channel.truncate(5);
      assertEquals(5, channel.position());

      channel.position(5);
      channel.truncate(10);
      assertEquals(5, channel.position());

      assertThrows(IllegalArgumentException.class, () -> channel.truncate(-1));

      assertThrows(IllegalArgumentException.class, () -> channel.truncate(Long.MAX_VALUE));
    }
  }
}
