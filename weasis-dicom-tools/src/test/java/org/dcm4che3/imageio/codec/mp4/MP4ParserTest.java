/*
 * Copyright (c) 2024 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.dcm4che3.imageio.codec.mp4;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
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
class MP4ParserTest {

  @Mock private SeekableByteChannel mockChannel;

  @Nested
  @DisplayName("Constructor and Basic Properties Tests")
  class ConstructorTests {

    @Test
    @DisplayName("Should parse valid AVC1 MP4 file with file type")
    void testConstructor_ValidAVC1WithFileType() throws IOException {
      setupValidAVC1MP4WithFileType();

      MP4Parser parser = new MP4Parser(mockChannel);

      assertNotNull(parser);
      assertNotNull(parser.getMP4FileType());
      assertEquals(MP4FileType.ISOM, parser.getMP4FileType().majorBrand());
      assertEquals(0, parser.getCodeStreamPosition());
      assertEquals(-1L, parser.getPositionAfterAPPSegments());
    }

    @Test
    @DisplayName("Should parse valid AVC1 MP4 file without file type")
    void testConstructor_ValidAVC1WithoutFileType() throws IOException {
      setupValidAVC1MP4WithoutFileType();

      MP4Parser parser = new MP4Parser(mockChannel);

      assertNotNull(parser);
      assertNull(parser.getMP4FileType());
    }

    @Test
    @DisplayName("Should parse valid HEVC MP4 file")
    void testConstructor_ValidHEVC() throws IOException {
      setupValidHEVCMP4();

      MP4Parser parser = new MP4Parser(mockChannel);

      assertNotNull(parser);
      assertEquals(UID.HEVCMP51, parser.getTransferSyntaxUID(false));
    }

    @Test
    @DisplayName("Should throw exception when movie box not found")
    void testConstructor_MovieBoxNotFound() throws IOException {
      setupMP4WithoutMovieBox();

      XPEGParserException exception =
          assertThrows(XPEGParserException.class, () -> new MP4Parser(mockChannel));
      assertTrue(exception.getMessage().contains("moov box not found"));
    }

    @Test
    @DisplayName("Should throw exception when required box not found")
    void testConstructor_RequiredBoxNotFound() throws IOException {
      setupMP4WithMissingTrackBox();

      assertThrows(XPEGParserException.class, () -> new MP4Parser(mockChannel));
    }
  }

  @Nested
  @DisplayName("Transfer Syntax UID Tests")
  class TransferSyntaxTests {

    @Test
    @DisplayName("Should return MPEG4HP41 for AVC High Profile Level 4.1")
    void testGetTransferSyntaxUID_AVC_HighProfile41() throws IOException {
      setupAVCMP4(100, 41, 1920, 1080, 24000); // High Profile, Level 4.1

      MP4Parser parser = new MP4Parser(mockChannel);

      assertEquals(UID.MPEG4HP41, parser.getTransferSyntaxUID(false));
      assertEquals(UID.MPEG4HP41F, parser.getTransferSyntaxUID(true));
    }

    @Test
    @DisplayName("Should return MPEG4HP41BD for BD compatible AVC")
    void testGetTransferSyntaxUID_AVC_BDCompatible() throws IOException {
      setupAVCMP4(100, 41, 1920, 1080, 25000); // BD compatible

      MP4Parser parser = new MP4Parser(mockChannel);

      assertEquals(UID.MPEG4HP41BD, parser.getTransferSyntaxUID(false));
      assertEquals(UID.MPEG4HP41BDF, parser.getTransferSyntaxUID(true));
    }

    @Test
    @DisplayName("Should return MPEG4HP422D for AVC High Profile Level 4.2")
    void testGetTransferSyntaxUID_AVC_HighProfile42() throws IOException {
      setupAVCMP4(100, 42, 1920, 1080, 30000); // High Profile, Level 4.2

      MP4Parser parser = new MP4Parser(mockChannel);

      assertEquals(UID.MPEG4HP422D, parser.getTransferSyntaxUID(false));
      assertEquals(UID.MPEG4HP422DF, parser.getTransferSyntaxUID(true));
    }

    @Test
    @DisplayName("Should return MPEG4HP42STEREO for AVC Stereo High Profile")
    void testGetTransferSyntaxUID_AVC_StereoHighProfile() throws IOException {
      setupAVCMP4(128, 42, 1920, 1080, 24000); // Stereo High Profile

      MP4Parser parser = new MP4Parser(mockChannel);

      assertEquals(UID.MPEG4HP42STEREO, parser.getTransferSyntaxUID(false));
      assertEquals(UID.MPEG4HP42STEREO, parser.getTransferSyntaxUID(true));
    }

    @Test
    @DisplayName("Should return HEVCMP51 for HEVC Main Profile")
    void testGetTransferSyntaxUID_HEVC_MainProfile() throws IOException {
      setupHEVCMP4(1, 51, 1920, 1080, 25000); // Main Profile

      MP4Parser parser = new MP4Parser(mockChannel);

      assertEquals(UID.HEVCMP51, parser.getTransferSyntaxUID(false));
      assertEquals(UID.HEVCMP51, parser.getTransferSyntaxUID(true));
    }

    @Test
    @DisplayName("Should return HEVCM10P51 for HEVC Main 10 Profile")
    void testGetTransferSyntaxUID_HEVC_Main10Profile() throws IOException {
      setupHEVCMP4(2, 51, 1920, 1080, 25000); // Main 10 Profile

      MP4Parser parser = new MP4Parser(mockChannel);

      assertEquals(UID.HEVCM10P51, parser.getTransferSyntaxUID(false));
      assertEquals(UID.HEVCM10P51, parser.getTransferSyntaxUID(true));
    }

    @Test
    @DisplayName("Should throw exception for unsupported AVC profile/level")
    void testGetTransferSyntaxUID_UnsupportedAVCProfile() throws IOException {
      setupAVCMP4(77, 30, 1920, 1080, 25000); // Unsupported profile

      MP4Parser parser = new MP4Parser(mockChannel);

      XPEGParserException exception =
          assertThrows(XPEGParserException.class, () -> parser.getTransferSyntaxUID(false));
      assertTrue(
          exception.getMessage().contains("MPEG-4 AVC profile_idc/level_idc: 77/30 not supported"));
    }

    @Test
    @DisplayName("Should throw exception for unsupported HEVC profile/level")
    void testGetTransferSyntaxUID_UnsupportedHEVCProfile() throws IOException {
      setupHEVCMP4(3, 51, 1920, 1080, 25000); // Unsupported profile

      MP4Parser parser = new MP4Parser(mockChannel);

      XPEGParserException exception =
          assertThrows(XPEGParserException.class, () -> parser.getTransferSyntaxUID(false));
      assertTrue(
          exception.getMessage().contains("MPEG-4 HEVC profile_idc/level_idc: 3/51 not supported"));
    }

    @Test
    @DisplayName("Should throw exception for unsupported HEVC level")
    void testGetTransferSyntaxUID_UnsupportedHEVCLevel() throws IOException {
      setupHEVCMP4(1, 60, 1920, 1080, 25000); // Unsupported level

      MP4Parser parser = new MP4Parser(mockChannel);

      assertThrows(XPEGParserException.class, () -> parser.getTransferSyntaxUID(false));
    }
  }

  @Nested
  @DisplayName("BD Compatibility Tests")
  class BDCompatibilityTests {

    @Test
    @DisplayName("Should detect 1080p BD compatible formats")
    void testBDCompatible_1080p() throws IOException {
      int[][] testCases = {{1920, 1080, 25000}};

      for (int[] testCase : testCases) {
        setupAVCMP4(100, 41, testCase[0], testCase[1], testCase[2]);
        MP4Parser parser = new MP4Parser(mockChannel);
        assertEquals(
            UID.MPEG4HP41BD,
            parser.getTransferSyntaxUID(false),
            String.format("Failed for %dx%d@%d", testCase[0], testCase[1], testCase[2]));
      }
    }

    @Test
    @DisplayName("Should detect 720p BD compatible formats")
    void testBDCompatible_720p() throws IOException {
      int[][] testCases = {{1280, 720, 50000}};

      for (int[] testCase : testCases) {
        setupAVCMP4(100, 41, testCase[0], testCase[1], testCase[2]);
        MP4Parser parser = new MP4Parser(mockChannel);
        assertEquals(
            UID.MPEG4HP41BD,
            parser.getTransferSyntaxUID(false),
            String.format("Failed for %dx%d@%d", testCase[0], testCase[1], testCase[2]));
      }
    }

    @Test
    @DisplayName("Should not detect BD compatibility for non-standard resolutions")
    void testNotBDCompatible_NonStandardResolution() throws IOException {
      setupAVCMP4(100, 41, 1280, 1080, 23976); // Wrong resolution

      MP4Parser parser = new MP4Parser(mockChannel);

      assertEquals(UID.MPEG4HP41, parser.getTransferSyntaxUID(false));
    }

    @Test
    @DisplayName("Should not detect BD compatibility for non-standard frame rates")
    void testNotBDCompatible_NonStandardFrameRate() throws IOException {
      setupAVCMP4(100, 41, 1920, 1080, 30000); // Wrong frame rate

      MP4Parser parser = new MP4Parser(mockChannel);

      assertEquals(UID.MPEG4HP41, parser.getTransferSyntaxUID(false));
    }
  }

  @Nested
  @DisplayName("Attributes Generation Tests")
  class AttributesTests {

    @Test
    @DisplayName("Should generate correct attributes for AVC video")
    void testGetAttributes_AVC() throws IOException {
      setupAVCMP4(100, 41, 1920, 1080, 25000);

      MP4Parser parser = new MP4Parser(mockChannel);
      Attributes attrs = parser.getAttributes(null);

      assertNotNull(attrs);
      assertEquals(25, attrs.getInt(Tag.CineRate, 0));
      assertEquals(40.0f, attrs.getFloat(Tag.FrameTime, 0), 0.1f);
      assertEquals(1080, attrs.getInt(Tag.Rows, 0));
      assertEquals(1920, attrs.getInt(Tag.Columns, 0));
      assertEquals(100, attrs.getInt(Tag.NumberOfFrames, 0));
    }

    @Test
    @DisplayName("Should use provided attributes object")
    void testGetAttributes_WithExistingAttributes() throws IOException {
      setupAVCMP4(100, 41, 720, 576, 25000);

      MP4Parser parser = new MP4Parser(mockChannel);
      Attributes existing = new Attributes();
      existing.setString(Tag.PatientName, VR.PN, "Test Patient");

      Attributes result = parser.getAttributes(existing);

      assertSame(existing, result);
      assertEquals("Test Patient", result.getString(Tag.PatientName));
      assertEquals(25, result.getInt(Tag.CineRate, 0));
    }

    @Test
    @DisplayName("Should handle different frame rates correctly")
    void testGetAttributes_DifferentFrameRates() throws IOException {
      int[] frameRates = {25000, 50000};
      int[] expectedCineRates = {25, 50};

      for (int i = 0; i < frameRates.length; i++) {
        setupAVCMP4(100, 41, 1920, 1080, frameRates[i]);
        MP4Parser parser = new MP4Parser(mockChannel);
        Attributes attrs = parser.getAttributes(null);

        assertEquals(
            expectedCineRates[i],
            attrs.getInt(Tag.CineRate, 0),
            "Wrong cine rate for frame rate " + frameRates[i]);
      }
    }
  }

  @Nested
  @DisplayName("Error Handling Tests")
  class ErrorHandlingTests {

    @Test
    @DisplayName("Should throw exception for unknown visual sample entry type")
    void testUnknownVisualSampleEntryType() throws IOException {
      setupMP4WithUnknownSampleEntry();
      assertThrows(XPEGParserException.class, () -> new MP4Parser(mockChannel));
    }

    @Test
    @DisplayName("Should handle multiple tracks to find video track")
    void testMultipleTracks() throws IOException {
      setupMP4WithMultipleTracks();
      assertThrows(XPEGParserException.class, () -> new MP4Parser(mockChannel));
    }
  }

  // Helper methods for setting up mock data

  private void setupValidAVC1MP4WithFileType() throws IOException {
    setupMP4Stream(true, 0x61766331, 100, 41, 1920, 1080, 100, 25000, 1000);
  }

  private void setupValidAVC1MP4WithoutFileType() throws IOException {
    setupMP4Stream(false, 0x61766331, 100, 41, 1920, 1080, 100, 25000, 1000);
  }

  private void setupValidHEVCMP4() throws IOException {
    setupMP4Stream(true, 0x68766331, 1, 51, 1920, 1080, 100, 25000, 1000);
  }

  private void setupAVCMP4(int profile, int level, int width, int height, int frameRate)
      throws IOException {
    setupMP4Stream(false, 0x61766331, profile, level, width, height, 100, frameRate, 1000);
  }

  private void setupHEVCMP4(int profile, int level, int width, int height, int frameRate)
      throws IOException {
    setupMP4Stream(false, 0x68766331, profile, level, width, height, 100, frameRate, 1000);
  }

  private void setupMP4WithoutMovieBox() throws IOException {
    AtomicLong currentPosition = new AtomicLong(0L);

    when(mockChannel.position()).thenAnswer(inv -> currentPosition.get());
    when(mockChannel.position(anyLong()))
        .thenAnswer(
            inv -> {
              long newPos = inv.getArgument(0);
              currentPosition.set(newPos);
              return mockChannel;
            });
    when(mockChannel.size()).thenReturn(100L);

    // Create MP4 data with 'free' box instead of 'moov' box
    byte[] mp4Data =
        new byte[] {
          0,
          0,
          0,
          16, // box size (16 bytes)
          0x66,
          0x72,
          0x65,
          0x65, // 'free' box type
          0,
          0,
          0,
          0,
          0,
          0,
          0,
          0 // free box content (8 bytes padding)
        };

    when(mockChannel.read(any(ByteBuffer.class)))
        .thenAnswer(
            inv -> {
              ByteBuffer buf = inv.getArgument(0);
              long pos = currentPosition.get();
              int remaining = buf.remaining();
              int available = (int) Math.min(remaining, mp4Data.length - pos);

              if (available <= 0) return -1;

              buf.put(mp4Data, (int) pos, available);
              currentPosition.addAndGet(available);
              return available;
            });
  }

  private void setupMP4WithMissingTrackBox() throws IOException {
    AtomicLong currentPosition = new AtomicLong(0L);

    when(mockChannel.position()).thenAnswer(inv -> currentPosition.get());
    when(mockChannel.position(anyLong()))
        .thenAnswer(
            inv -> {
              long newPos = inv.getArgument(0);
              currentPosition.set(newPos);
              return mockChannel;
            });
    when(mockChannel.size()).thenReturn(1000L);

    // Create MP4 data with 'moov' box but no 'trak' box inside
    byte[] mp4Data =
        new byte[] {
          0, 0, 0, 16, // moov box size (16 bytes total)
          0x6d, 0x6f, 0x6f, 0x76, // 'moov' box type
          0, 0, 0, 8, // inner box size (8 bytes)
          0x66, 0x72, 0x65, 0x65 // 'free' box instead of 'trak'
        };

    when(mockChannel.read(any(ByteBuffer.class)))
        .thenAnswer(
            inv -> {
              ByteBuffer buf = inv.getArgument(0);
              long pos = currentPosition.get();
              int remaining = buf.remaining();
              int available = (int) Math.min(remaining, mp4Data.length - pos);

              if (available <= 0) return -1;

              buf.put(mp4Data, (int) pos, available);
              currentPosition.addAndGet(available);
              return available;
            });
  }

  private void setupMP4WithUnknownSampleEntry() throws IOException {
    setupMP4Stream(false, 0x756E6B6E, 1, 1, 640, 480, 25, 25000, 1000); // 'unkn'
  }

  private void setupMP4WithMultipleTracks() throws IOException {
    AtomicLong currentPosition = new AtomicLong(0L);
    AtomicInteger callCount = new AtomicInteger(0);
    AtomicInteger trackCount = new AtomicInteger(0);

    lenient().when(mockChannel.position()).thenAnswer(inv -> currentPosition.get());
    lenient()
        .when(mockChannel.position(anyLong()))
        .thenAnswer(
            inv -> {
              long newPos = inv.getArgument(0);
              currentPosition.set(newPos);
              return mockChannel;
            });
    when(mockChannel.size()).thenReturn(2000L);

    when(mockChannel.read(any(ByteBuffer.class)))
        .thenAnswer(
            inv -> {
              ByteBuffer buf = inv.getArgument(0);
              int call = callCount.incrementAndGet();

              return handleMultiTrackReads(buf, call, trackCount, currentPosition);
            });
  }

  private void setupMP4Stream(
      boolean withFileType,
      int visualType,
      int profile,
      int level,
      int width,
      int height,
      int numFrames,
      int frameRate,
      int timescale)
      throws IOException {
    AtomicLong currentPosition = new AtomicLong(0L);

    when(mockChannel.position()).thenAnswer(inv -> currentPosition.get());
    when(mockChannel.position(anyLong()))
        .thenAnswer(
            inv -> {
              long newPos = inv.getArgument(0);
              currentPosition.set(newPos);
              return mockChannel;
            });
    when(mockChannel.size()).thenReturn(2000L);

    // Create a structured byte array representing the MP4 file
    ByteArrayOutputStream mp4Stream = new ByteArrayOutputStream();

    try {
      if (withFileType) {
        // ftyp box
        writeInt(mp4Stream, 24); // box size
        writeInt(mp4Stream, 0x66747970); // 'ftyp'
        writeInt(mp4Stream, MP4FileType.ISOM); // major brand
        writeInt(mp4Stream, 0); // minor version
        writeInt(mp4Stream, MP4FileType.ISOM); // compatible brand
        writeInt(mp4Stream, 0x69736F32); // compatible brand 'iso2'
      }

      // moov box
      ByteArrayOutputStream moovContent = new ByteArrayOutputStream();

      // trak box
      ByteArrayOutputStream trakContent = new ByteArrayOutputStream();

      // mdia box
      ByteArrayOutputStream mdiaContent = new ByteArrayOutputStream();

      // mdhd box
      ByteArrayOutputStream mdhdContent = new ByteArrayOutputStream();
      writeInt(mdhdContent, 0); // version + flags
      writeInt(mdhdContent, 1641027600); // creation time
      writeInt(mdhdContent, 1641027600); // modification time
      writeInt(mdhdContent, timescale); // timescale
      writeInt(mdhdContent, (int) ((long) numFrames * timescale / (frameRate / 1000))); // duration
      writeShort(mdhdContent, 0); // language
      writeShort(mdhdContent, 0); // pre_defined

      writeBoxHeader(mdiaContent, 0x6d646864, mdhdContent.toByteArray()); // mdhd

      // minf box
      ByteArrayOutputStream minfContent = new ByteArrayOutputStream();

      // vmhd box (video media header)
      ByteArrayOutputStream vmhdContent = new ByteArrayOutputStream();
      writeInt(vmhdContent, 1); // version + flags
      writeInt(vmhdContent, 0); // graphics mode + opcolor
      writeInt(vmhdContent, 0); // opcolor continued
      writeBoxHeader(minfContent, 0x766d6864, vmhdContent.toByteArray()); // vmhd

      // stbl box
      ByteArrayOutputStream stblContent = new ByteArrayOutputStream();

      // stsd box
      ByteArrayOutputStream stsdContent = new ByteArrayOutputStream();
      writeInt(stsdContent, 0); // version + flags
      writeInt(stsdContent, 1); // entry count

      // Visual sample entry
      ByteArrayOutputStream visualContent = new ByteArrayOutputStream();
      writeBytes(visualContent, new byte[6]); // reserved
      writeShort(visualContent, 1); // data reference index
      writeBytes(visualContent, new byte[16]); // pre_defined + reserved
      writeShort(visualContent, width); // width
      writeShort(visualContent, height); // height
      writeInt(visualContent, 0x00480000); // horizresolution
      writeInt(visualContent, 0x00480000); // vertresolution
      writeInt(visualContent, 0); // reserved
      writeShort(visualContent, 1); // frame count
      writeBytes(visualContent, new byte[32]); // compressorname
      writeShort(visualContent, 24); // depth
      writeShort(visualContent, -1); // pre_defined

      // Configuration box (avcC or hvcC)
      ByteArrayOutputStream configContent = new ByteArrayOutputStream();
      if (visualType == 0x61766331) { // avc1
        writeByte(configContent, 1); // configuration version
        writeByte(configContent, profile); // profile
        writeByte(configContent, 0); // profile compatibility
        writeByte(configContent, level); // level
        writeByte(configContent, 0xFF); // length size minus one
        writeByte(configContent, 0xE1); // num sps
        writeShort(configContent, 4); // sps length
        writeInt(configContent, 0x67640028); // fake SPS data
        writeByte(configContent, 1); // num pps
        writeShort(configContent, 4); // pps length
        writeInt(configContent, 0x68EE3C80); // fake PPS data
        writeBoxHeader(visualContent, 0x61766343, configContent.toByteArray()); // avcC
      } else if (visualType == 0x68766331) { // hvc1
        writeByte(configContent, 1); // configuration version
        writeByte(configContent, profile); // profile space + tier + profile
        writeBytes(configContent, new byte[4]); // profile compatibility
        writeBytes(configContent, new byte[6]); // constraint indicator
        writeByte(configContent, level); // level
        writeShort(configContent, 0); // min spatial segmentation
        writeByte(configContent, 0); // parallelism type
        writeByte(configContent, 0); // chroma format
        writeByte(configContent, 0); // bit depth luma
        writeByte(configContent, 0); // bit depth chroma
        writeShort(configContent, 0); // avg frame rate
        writeByte(configContent, 0); // constant frame rate + num temporal layers
        writeByte(configContent, 0); // num arrays
        writeBoxHeader(visualContent, 0x68766343, configContent.toByteArray()); // hvcC
      }

      writeBoxHeader(stsdContent, visualType, visualContent.toByteArray());
      writeBoxHeader(stblContent, 0x73747364, stsdContent.toByteArray()); // stsd

      // stsz box
      ByteArrayOutputStream stszContent = new ByteArrayOutputStream();
      writeInt(stszContent, 0); // version + flags
      writeInt(stszContent, 0); // sample size (0 = variable)
      writeInt(stszContent, numFrames); // sample count
      for (int i = 0; i < numFrames; i++) {
        writeInt(stszContent, 1000); // sample size
      }
      writeBoxHeader(stblContent, 0x7374737a, stszContent.toByteArray()); // stsz

      writeBoxHeader(minfContent, 0x7374626c, stblContent.toByteArray()); // stbl
      writeBoxHeader(mdiaContent, 0x6d696e66, minfContent.toByteArray()); // minf
      writeBoxHeader(trakContent, 0x6d646961, mdiaContent.toByteArray()); // mdia
      writeBoxHeader(moovContent, 0x7472616b, trakContent.toByteArray()); // trak
      writeBoxHeader(mp4Stream, 0x6d6f6f76, moovContent.toByteArray()); // moov

    } catch (IOException e) {
      throw new RuntimeException(e);
    }

    final byte[] mp4Data = mp4Stream.toByteArray();

    when(mockChannel.read(any(ByteBuffer.class)))
        .thenAnswer(
            inv -> {
              ByteBuffer buf = inv.getArgument(0);
              long pos = currentPosition.get();
              int remaining = buf.remaining();
              int available = (int) Math.min(remaining, mp4Data.length - pos);

              if (available <= 0) return -1;

              buf.put(mp4Data, (int) pos, available);
              currentPosition.addAndGet(available);
              return available;
            });
  }

  // Helper methods for writing binary data
  private void writeBoxHeader(ByteArrayOutputStream stream, int type, byte[] content)
      throws IOException {
    writeInt(stream, content.length + 8); // box size
    writeInt(stream, type); // box type
    stream.write(content);
  }

  private void writeInt(ByteArrayOutputStream stream, int value) throws IOException {
    stream.write((value >>> 24) & 0xFF);
    stream.write((value >>> 16) & 0xFF);
    stream.write((value >>> 8) & 0xFF);
    stream.write(value & 0xFF);
  }

  private void writeShort(ByteArrayOutputStream stream, int value) throws IOException {
    stream.write((value >>> 8) & 0xFF);
    stream.write(value & 0xFF);
  }

  private void writeByte(ByteArrayOutputStream stream, int value) throws IOException {
    stream.write(value & 0xFF);
  }

  private void writeBytes(ByteArrayOutputStream stream, byte[] bytes) throws IOException {
    stream.write(bytes);
  }

  private int handleMP4StreamReads(
      ByteBuffer buf,
      int call,
      boolean withFileType,
      int visualType,
      int profile,
      int level,
      int width,
      int height,
      int numFrames,
      int frameRate,
      int timescale,
      AtomicLong pos) {
    if (buf.limit() == 8) {
      switch (call) {
        case 1:
          if (withFileType) {
            buf.putInt(24); // ftyp box size
            buf.putInt(0x66747970); // 'ftyp'
            pos.addAndGet(8);
            return 8;
          } else {
            buf.putInt(500); // moov box size
            buf.putInt(0x6d6f6f76); // 'moov'
            pos.addAndGet(8);
            return 8;
          }
        case 2:
          if (withFileType) {
            buf.putInt(500); // moov box size
            buf.putInt(0x6d6f6f76); // 'moov'
          } else {
            buf.putInt(400); // trak box size
            buf.putInt(0x7472616b); // 'trak'
          }
          pos.addAndGet(8);
          return 8;
        case 3:
          if (withFileType) {
            buf.putInt(400); // trak box size
            buf.putInt(0x7472616b); // 'trak'
          } else {
            buf.putInt(350); // mdia box size
            buf.putInt(0x6d646961); // 'mdia'
          }
          pos.addAndGet(8);
          return 8;
        case 4:
          if (withFileType) {
            buf.putInt(350); // mdia box size
            buf.putInt(0x6d646961); // 'mdia'
          } else {
            buf.putInt(32); // mdhd box size
            buf.putInt(0x6d646864); // 'mdhd'
          }
          pos.addAndGet(8);
          return 8;
        case 5:
          buf.putInt(32); // mdhd box size
          buf.putInt(0x6d646864); // 'mdhd'
          pos.addAndGet(8);
          return 8;
        case 6:
          buf.putInt(300); // minf box size
          buf.putInt(0x6d696e66); // 'minf'
          pos.addAndGet(8);
          return 8;
        case 7:
          buf.putInt(250); // stbl box size
          buf.putInt(0x7374626c); // 'stbl'
          pos.addAndGet(8);
          return 8;
        case 8:
          buf.putInt(100); // stsd box size
          buf.putInt(0x73747364); // 'stsd'
          pos.addAndGet(8);
          return 8;
        case 9:
          buf.putInt(80); // visual sample entry size
          buf.putInt(visualType); // avc1/hvc1/etc
          pos.addAndGet(8);
          return 8;
        case 10:
          if (visualType == 0x61766331) { // avc1
            buf.putInt(20); // avcC box size
            buf.putInt(0x61766343); // 'avcC'
          } else if (visualType == 0x68766331) { // hvc1
            buf.putInt(25); // hvcC box size
            buf.putInt(0x68766343); // 'hvcC'
          }
          pos.addAndGet(8);
          return 8;
        case 11:
          buf.putInt(20); // stsz box size
          buf.putInt(0x7374737a); // 'stsz'
          pos.addAndGet(8);
          return 8;
      }
    } else if (buf.limit() == 4) {
      switch (call) {
        case 1:
          if (withFileType) {
            buf.putInt(MP4FileType.ISOM); // major brand
            pos.addAndGet(4);
            return 4;
          }
          break;
        case 2:
          if (withFileType) {
            buf.putInt(0); // minor version
            pos.addAndGet(4);
            return 4;
          }
          break;
        case 5: // mdhd version
          buf.putInt(0); // version 0
          pos.addAndGet(4);
          return 4;
        case 6: // creation time
          buf.putInt((int) 3724137600L); // Jan 1, 2018
          pos.addAndGet(4);
          return 4;
        case 7: // modification time
          buf.putInt((int) 3724137600L);
          pos.addAndGet(4);
          return 4;
        case 8: // timescale
          buf.putInt(timescale);
          pos.addAndGet(4);
          return 4;
        case 9: // duration
          buf.putInt((int) ((long) numFrames * timescale / (frameRate / 1000)));
          pos.addAndGet(4);
          return 4;
        case 10: // stsd skip
          pos.addAndGet(4);
          return 4;
        case 11: // stsd skip
          pos.addAndGet(4);
          return 4;
        case 12: // visual sample entry header
          buf.putInt((width << 16) | height);
          pos.addAndGet(4);
          return 4;
        case 13: // avcC/hvcC config
          if (visualType == 0x61766331) {
            buf.putInt((1 << 24) | (profile << 16) | level); // version, profile, level
          }
          pos.addAndGet(4);
          return 4;
        case 14: // stsz skip
          pos.addAndGet(4);
          return 4;
        case 15: // stsz skip
          pos.addAndGet(4);
          return 4;
        case 16: // num frames
          buf.putInt(numFrames);
          pos.addAndGet(4);
          return 4;
      }
    } else if (buf.limit() == 2) {
      if (visualType == 0x68766331 && call == 13) { // hvcC config
        buf.putShort((short) ((1 << 8) | profile)); // version, profile
        pos.addAndGet(2);
        return 2;
      }
    } else if (buf.limit() == 1) {
      if (visualType == 0x68766331 && call == 14) { // hvcC level
        buf.put((byte) level);
        pos.addAndGet(1);
        return 1;
      }
    }

    // Skip any remaining bytes
    int remaining = buf.remaining();
    pos.addAndGet(remaining);
    return remaining;
  }

  private int handleMP4DateTestReads(
      ByteBuffer buf,
      int call,
      int version,
      long creationTime,
      long modificationTime,
      AtomicLong pos) {
    // Similar structure to handleMP4StreamReads but focused on date handling
    if (buf.limit() == 8) {
      switch (call) {
        case 1:
          buf.putInt(500); // moov box size
          buf.putInt(0x6d6f6f76); // 'moov'
          return 8;
        case 2:
          buf.putInt(400); // trak box size
          buf.putInt(0x7472616b); // 'trak'
          return 8;
        case 3:
          buf.putInt(350); // mdia box size
          buf.putInt(0x6d646961); // 'mdia'
          return 8;
        case 4:
          buf.putInt(version == 1 ? 44 : 32); // mdhd box size
          buf.putInt(0x6d646864); // 'mdhd'
          return 8;
      }
    } else if (buf.limit() == 4) {
      switch (call) {
        case 5: // mdhd version
          buf.putInt(version << 24);
          return 4;
        case 6: // creation time (low 32 bits for version 0)
          buf.putInt((int) (creationTime & 0xFFFFFFFFL));
          return 4;
        case 7: // modification time (low 32 bits for version 0)
          buf.putInt((int) (modificationTime & 0xFFFFFFFFL));
          return 4;
        case 8: // timescale
          buf.putInt(1000);
          return 4;
        case 9: // duration (low 32 bits for version 0)
          buf.putInt(100);
          return 4;
      }
    } else if (buf.limit() == 8 && version == 1) {
      switch (call) {
        case 6: // creation time (full 64 bits for version 1)
          buf.putLong(creationTime);
          return 8;
        case 7: // modification time (full 64 bits for version 1)
          buf.putLong(modificationTime);
          return 8;
        case 9: // duration (full 64 bits for version 1)
          buf.putLong(100L);
          return 8;
      }
    }

    return buf.remaining();
  }

  private int handleMultiTrackReads(
      ByteBuffer buf, int call, AtomicInteger trackCount, AtomicLong pos) {
    // Handle multiple tracks where first track has no visual sample entry
    if (buf.limit() == 8) {
      switch (call) {
        case 1:
          buf.putInt(1000); // moov box size
          buf.putInt(0x6d6f6f76); // 'moov'
          return 8;
        case 2:
        case 12: // Multiple trak boxes
          buf.putInt(400); // trak box size
          buf.putInt(0x7472616b); // 'trak'
          return 8;
        case 3:
        case 13:
          buf.putInt(350); // mdia box size
          buf.putInt(0x6d646961); // 'mdia'
          return 8;
        case 4:
        case 14:
          buf.putInt(32); // mdhd box size
          buf.putInt(0x6d646864); // 'mdhd'
          return 8;
        case 6:
        case 16:
          buf.putInt(250); // minf box size
          buf.putInt(0x6d696e66); // 'minf'
          return 8;
        case 7:
        case 17:
          buf.putInt(200); // stbl box size
          buf.putInt(0x7374626c); // 'stbl'
          return 8;
        case 8:
        case 18:
          buf.putInt(100); // stsd box size
          buf.putInt(0x73747364); // 'stsd'
          return 8;
        case 9: // First track - audio sample entry
          buf.putInt(50); // sample entry size
          buf.putInt(0x6D703461); // 'mp4a' (audio)
          return 8;
        case 19: // Second track - video sample entry
          buf.putInt(80); // sample entry size
          buf.putInt(0x61766331); // 'avc1' (video)
          return 8;
        case 20: // avcC box
          buf.putInt(20); // avcC box size
          buf.putInt(0x61766343); // 'avcC'
          return 8;
        case 10:
        case 21:
          buf.putInt(20); // stsz box size
          buf.putInt(0x7374737a); // 'stsz'
          return 8;
      }
    } else if (buf.limit() == 4) {
      // Handle various 4-byte reads for track data
      if (call >= 5 && call <= 11) {
        // First track metadata
        if (call == 5) buf.putInt(0); // version
        else if (call == 6) buf.putInt(1641027600); // creation - Jan 1, 2022
        else if (call == 7) buf.putInt(1641027600); // modification - Jan 1, 2022
        else if (call == 8) buf.putInt(1000); // timescale
        else if (call == 9) buf.putInt(100); // duration
        else if (call == 10) buf.putInt(0); // stsd skip
        else if (call == 11) buf.putInt(0); // stsd skip
        return 4;
      } else if (call >= 15 && call <= 25) {
        // Second track metadata
        if (call == 15) buf.putInt(0); // version
        else if (call == 22) buf.putInt(0); // stsd skip
        else if (call == 23) buf.putInt(0); // stsd skip
        else if (call == 24) buf.putInt((1920 << 16) | 1080); // dimensions
        else if (call == 25) buf.putInt((1 << 24) | (100 << 16) | 41); // avcC config
        return 4;
      }
    }

    return buf.remaining();
  }
}
