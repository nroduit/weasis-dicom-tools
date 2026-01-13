/*
 * Copyright (c) 2025 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.dcm4che3.imageio.codec.mpeg;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.UID;
import org.dcm4che3.data.VR;
import org.dcm4che3.imageio.codec.XPEGParserException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MPEG2ParserTest {

  @Mock private SeekableByteChannel mockChannel;

  @Test
  @DisplayName("getCodeStreamPosition should always return 0")
  void testGetCodeStreamPosition_ReturnsZero() throws IOException {
    // Given: A valid MPEG2 sequence with 512x480 resolution
    setupValidMPEG2Sequence(512, 480, 2, 4); // 4:3 aspect ratio, 25 fps

    MPEG2Parser parser = new MPEG2Parser(mockChannel);

    // When & Then
    assertEquals(0, parser.getCodeStreamPosition());
  }

  @Test
  @DisplayName("getPositionAfterAPPSegments should always return -1")
  void testGetPositionAfterAPPSegments_ReturnsMinusOne() throws IOException {
    setupValidMPEG2Sequence(720, 576, 2, 4);

    MPEG2Parser parser = new MPEG2Parser(mockChannel);

    assertEquals(-1L, parser.getPositionAfterAPPSegments());
  }

  @Test
  @DisplayName("getMP4FileType should always return null")
  void testGetMP4FileType_ReturnsNull() throws IOException {
    setupValidMPEG2Sequence(352, 288, 1, 3);

    MPEG2Parser parser = new MPEG2Parser(mockChannel);

    assertNull(parser.getMP4FileType());
  }

  @Nested
  @DisplayName("Transfer Syntax UID Tests")
  class TransferSyntaxTests {

    @Test
    @DisplayName("Should return MPEG2MPML for SD resolution and low frame rate")
    void testGetTransferSyntaxUID_SDLR_Unfragmented() throws IOException {
      // 720x576, 25 fps (frameRate=4)
      setupValidMPEG2Sequence(720, 576, 2, 4);

      MPEG2Parser parser = new MPEG2Parser(mockChannel);

      assertEquals(UID.MPEG2MPML, parser.getTransferSyntaxUID(false));
    }

    @Test
    @DisplayName("Should return MPEG2MPMLF for SD resolution and low frame rate (fragmented)")
    void testGetTransferSyntaxUID_SDLR_Fragmented() throws IOException {
      setupValidMPEG2Sequence(720, 576, 2, 4);

      MPEG2Parser parser = new MPEG2Parser(mockChannel);

      assertEquals(UID.MPEG2MPMLF, parser.getTransferSyntaxUID(true));
    }

    @Test
    @DisplayName("Should return MPEG2MPHL for HD resolution or high frame rate")
    void testGetTransferSyntaxUID_HD_Unfragmented() throws IOException {
      // 1920x1080 (HD resolution)
      setupValidMPEG2Sequence(1920, 1080, 3, 6);

      MPEG2Parser parser = new MPEG2Parser(mockChannel);

      assertEquals(UID.MPEG2MPHL, parser.getTransferSyntaxUID(false));
    }

    @Test
    @DisplayName("Should return MPEG2MPHLF for HD resolution or high frame rate (fragmented)")
    void testGetTransferSyntaxUID_HD_Fragmented() throws IOException {
      setupValidMPEG2Sequence(1920, 1080, 3, 6);

      MPEG2Parser parser = new MPEG2Parser(mockChannel);

      assertEquals(UID.MPEG2MPHLF, parser.getTransferSyntaxUID(true));
    }

    @Test
    @DisplayName("Should return MPEG2MPHL for high frame rate even with SD resolution")
    void testGetTransferSyntaxUID_HighFrameRate() throws IOException {
      // 720x576 but high frame rate (frameRate=6)
      setupValidMPEG2Sequence(720, 576, 2, 6);

      MPEG2Parser parser = new MPEG2Parser(mockChannel);

      assertEquals(UID.MPEG2MPHL, parser.getTransferSyntaxUID(false));
    }
  }

  @Nested
  @DisplayName("Attributes Generation Tests")
  class AttributesTests {

    @Test
    @DisplayName("Should generate correct attributes for 512x480 4:3 video")
    void testGetAttributes_512x480_4to3() throws IOException {
      setupValidMPEG2Sequence(512, 480, 2, 4); // 4:3 aspect ratio, 25 fps

      MPEG2Parser parser = new MPEG2Parser(mockChannel);
      Attributes attrs = parser.getAttributes(null);

      assertNotNull(attrs);
      assertEquals(480, attrs.getInt(Tag.Rows, 0));
      assertEquals(512, attrs.getInt(Tag.Columns, 0));
      assertEquals(30, attrs.getInt(Tag.CineRate, 0));
      assertArrayEquals(new String[] {"4", "3"}, attrs.getStrings(Tag.PixelAspectRatio));
    }

    @Test
    @DisplayName("Should generate correct attributes for 1920x1080 16:9 video")
    void testGetAttributes_1920x1080_16to9() throws IOException {
      setupValidMPEG2Sequence(1920, 1080, 3, 5); // 16:9 aspect ratio, 29.97 fps

      MPEG2Parser parser = new MPEG2Parser(mockChannel);
      Attributes attrs = parser.getAttributes(null);

      assertEquals(1080, attrs.getInt(Tag.Rows, 0));
      assertEquals(1920, attrs.getInt(Tag.Columns, 0));
      assertEquals(30, attrs.getInt(Tag.CineRate, 0));
      assertArrayEquals(new String[] {"16", "9"}, attrs.getStrings(Tag.PixelAspectRatio));
    }

    @Test
    @DisplayName("Should use provided attributes object when not null")
    void testGetAttributes_WithExistingAttributes() throws IOException {
      setupValidMPEG2Sequence(720, 576, 1, 3);

      MPEG2Parser parser = new MPEG2Parser(mockChannel);
      Attributes existing = new Attributes();
      existing.setString(Tag.PatientName, VR.PN, "Test Patient");

      Attributes result = parser.getAttributes(existing);

      assertSame(existing, result);
      assertEquals("Test Patient", result.getString(Tag.PatientName));
      assertEquals(576, result.getInt(Tag.Rows, 0));
    }

    @Test
    @DisplayName("Should handle different aspect ratio types")
    void testGetAttributes_DifferentAspectRatios() throws IOException {
      // Test 1:1 aspect ratio
      setupValidMPEG2Sequence(512, 512, 1, 3);
      MPEG2Parser parser1 = new MPEG2Parser(mockChannel);
      assertArrayEquals(
          new String[] {"1", "1"}, parser1.getAttributes(null).getStrings(Tag.PixelAspectRatio));
    }
  }

  @Nested
  @DisplayName("Error Handling Tests")
  class ErrorHandlingTests {

    @Test
    @DisplayName("Should throw exception when sequence header not found")
    void testConstructor_SequenceHeaderNotFound() throws IOException {
      setupInvalidSequence();

      assertThrows(XPEGParserException.class, () -> new MPEG2Parser(mockChannel));
    }

    @Test
    @DisplayName("Should throw exception for invalid start code")
    void testConstructor_InvalidStartCode() throws IOException {
      when(mockChannel.read(any(ByteBuffer.class)))
          .thenAnswer(
              invocation -> {
                ByteBuffer buf = invocation.getArgument(0);
                buf.putInt(0xFFFFFFFF); // Invalid start code
                return 4;
              });
      when(mockChannel.position()).thenReturn(0L);

      assertThrows(XPEGParserException.class, () -> new MPEG2Parser(mockChannel));
    }

    @Test
    @DisplayName("Should throw exception when GOP header not found")
    void testConstructor_GOPHeaderNotFound() throws IOException {
      setupSequenceWithoutGOP();

      assertThrows(XPEGParserException.class, () -> new MPEG2Parser(mockChannel));
    }
  }

  @Test
  @DisplayName("Should throw exception when sequence header not found in video packet")
  void testConstructor_SequenceHeaderNotFoundInVideoPacket() throws IOException {
    when(mockChannel.read(any(ByteBuffer.class)))
        .thenAnswer(
            invocation -> {
              ByteBuffer buf = invocation.getArgument(0);

              if (buf.limit() == 4) {
                buf.putInt(0x000001E0); // Video stream
                return 4;
              } else if (buf.limit() == 2) {
                buf.putShort((short) 10); // Short packet
                return 2;
              } else if (buf.limit() == 3) {
                // Never return sequence start code - just fill with invalid data
                buf.put((byte) 0xFF);
                buf.put((byte) 0xFF);
                buf.put((byte) 0xFF);
                return 3;
              }
              return 0;
            });

    lenient().when(mockChannel.position()).thenReturn(0L);
    lenient().when(mockChannel.position(anyLong())).thenReturn(mockChannel);

    assertThrows(XPEGParserException.class, () -> new MPEG2Parser(mockChannel));
  }

  @Test
  @DisplayName("Should find sequence header within video packet")
  void testConstructor_FindSequenceHeaderInVideoPacket() throws IOException {
    when(mockChannel.read(any(ByteBuffer.class)))
        .thenAnswer(
            invocation -> {
              ByteBuffer buf = invocation.getArgument(0);

              if (buf.limit() == 4) {
                // First read: return video stream (triggers findSequenceHeader)
                buf.putInt(0x000001E0);
                return 4;
              } else if (buf.limit() == 2) {
                // Packet length
                buf.putShort((short) 20);
                return 2;
              } else if (buf.limit() == 3) {
                // Search within packet - return sequence start code
                buf.put((byte) 0x00);
                buf.put((byte) 0x00);
                buf.put((byte) 0x01);
                return 3;
              } else if (buf.limit() == 1) {
                // Stream ID - return sequence header ID
                buf.put((byte) 0xB3); // SEQUENCE_HEADER_STREAM_ID
                return 1;
              } else if (buf.limit() == 7) {
                // Sequence data
                buf.put((byte) 0x20); // 512 width
                buf.put((byte) 0x01);
                buf.put((byte) 0xE0); // 480 height
                buf.put((byte) 0x24); // aspect 2, frame rate 4
                buf.put((byte) 0x00);
                buf.put((byte) 0x00);
                buf.put((byte) 0x00);
                return 7;
              } else if (buf.limit() == 8162) {
                // GOP search - put GOP header at position 100
                buf.position(100);
                buf.put((byte) 0x00);
                buf.put((byte) 0x00);
                buf.put((byte) 0x01);
                buf.put((byte) 0xB8); // GOP_HEADER_STREAM_ID
                buf.put((byte) 0x7C); // 1 hour duration
                buf.put((byte) 0x00);
                buf.put((byte) 0x00);
                buf.put((byte) 0x00);
                buf.limit(108);
                buf.position(108);
                return 8;
              }
              return 0;
            });

    lenient().when(mockChannel.position()).thenReturn(0L);
    lenient().when(mockChannel.position(anyLong())).thenReturn(mockChannel);
    lenient().when(mockChannel.size()).thenReturn(8162L);

    // This should successfully create parser by finding sequence header in video packet
    MPEG2Parser parser = new MPEG2Parser(mockChannel);
    assertNotNull(parser);
  }

  /** Sets up a valid MPEG2 sequence with specified parameters. */
  private void setupValidMPEG2Sequence(int width, int height, int aspectRatio, int frameRate)
      throws IOException {
    // Use lenient stubbing to avoid strict argument matching issues
    lenient().when(mockChannel.position()).thenReturn(0L);
    lenient().when(mockChannel.position(anyLong())).thenReturn(mockChannel);
    lenient().when(mockChannel.size()).thenReturn(8162L);
    when(mockChannel.read(any(ByteBuffer.class)))
        .thenAnswer(
            invocation -> {
              ByteBuffer buf = invocation.getArgument(0);
              if (buf.limit() == 4) {
                // Return sequence header start code
                buf.putInt(0x000001B3);
                return 4;
              } else if (buf.limit() == 7) {
                // Return sequence data: width, height, aspect ratio, frame rate
                buf.put((byte) ((width >> 4) & 0xFF));
                buf.put((byte) (((width & 0x0F) << 4) | ((height >> 8) & 0x0F)));
                buf.put((byte) (height & 0xFF));
                buf.put((byte) ((aspectRatio << 4) | (frameRate & 0x0F)));
                buf.put((byte) 0x00);
                buf.put((byte) 0x00);
                buf.put((byte) 0x00);
                return 7;
              } else if (buf.limit() == 8162) {
                // For GOP search - return GOP header with 1 hour duration
                int gopPos = 8150;
                buf.position(gopPos);
                buf.put((byte) 0x00);
                buf.put((byte) 0x00);
                buf.put((byte) 0x01);
                buf.put((byte) 0xB8); // GOP header
                buf.put((byte) 0x7C); // 1 hour (bits 7-2)
                buf.put((byte) 0x00); // 0 minutes, 0 seconds
                buf.put((byte) 0x00);
                buf.put((byte) 0x00);
                buf.limit(gopPos + 8);
                buf.position(gopPos + 8);
                return 8;
              }
              return 0;
            });
  }

  private void setupInvalidSequence() throws IOException {
    // Simple approach - just return invalid start code immediately
    when(mockChannel.read(any(ByteBuffer.class)))
        .thenAnswer(
            invocation -> {
              ByteBuffer buf = invocation.getArgument(0);
              if (buf.limit() == 4) {
                buf.putInt(0xFFFFFFFF); // Invalid start code
                return 4;
              }
              // Fill other buffers with zeros
              while (buf.hasRemaining()) {
                buf.put((byte) 0x00);
              }
              return buf.position();
            });

    lenient().when(mockChannel.position()).thenReturn(0L);
  }

  private void setupSequenceWithoutGOP() throws IOException {
    lenient().when(mockChannel.position()).thenReturn(0L);
    lenient().when(mockChannel.position(anyLong())).thenReturn(mockChannel);
    lenient().when(mockChannel.size()).thenReturn(1000L);

    when(mockChannel.read(any(ByteBuffer.class)))
        .thenAnswer(
            invocation -> {
              ByteBuffer buf = invocation.getArgument(0);
              if (buf.limit() == 4) {
                buf.putInt(0x000001B3); // Sequence header start code
              } else if (buf.limit() == 7) {
                // Valid sequence data: 512x480, aspect ratio 2, frame rate 4
                buf.put((byte) 0x20);
                buf.put((byte) 0x01);
                buf.put((byte) 0xE0);
                buf.put((byte) 0x24);
                buf.put((byte) 0x00);
                buf.put((byte) 0x00);
                buf.put((byte) 0x00);
              } else {
                // Fill with zeros for GOP search (no GOP header)
                while (buf.hasRemaining()) {
                  buf.put((byte) 0x00);
                }
              }
              return buf.position();
            });
  }
}
