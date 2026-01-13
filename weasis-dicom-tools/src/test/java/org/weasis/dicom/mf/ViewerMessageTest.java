/*
 * Copyright (c) 2024 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.mf;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EmptySource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.weasis.dicom.mf.ViewerMessage.Level;

@DisplayNameGeneration(ReplaceUnderscores.class)
class ViewerMessageTest {

  @Test
  void viewerMessage_with_all_parameters_creates_message() {
    var message = new ViewerMessage("Title", "Message content", ViewerMessage.Level.INFO);

    assertAll(
        () -> assertEquals("Title", message.title()),
        () -> assertEquals("Message content", message.message()),
        () -> assertEquals(ViewerMessage.Level.INFO, message.level()));
  }

  @Test
  void viewerMessage_with_null_parameters_throws_exception() {
    assertAll(
        () ->
            assertThrows(
                NullPointerException.class, () -> new ViewerMessage(null, "Message", Level.INFO)),
        () ->
            assertThrows(
                NullPointerException.class, () -> new ViewerMessage("Title", null, Level.INFO)),
        () ->
            assertThrows(
                NullPointerException.class,
                () -> new ViewerMessage("Title", "Message", (Level) null)));
  }

  @ParameterizedTest
  @EmptySource
  @ValueSource(strings = {"   ", "\t", "\n"})
  void viewerMessage_with_blank_title_throws_exception(String blankTitle) {
    assertThrows(
        IllegalArgumentException.class, () -> new ViewerMessage(blankTitle, "Message", Level.INFO));
  }

  @ParameterizedTest
  @ValueSource(strings = {"Title with spaces", "Very long title that exceeds normal length"})
  void viewerMessage_accepts_various_title_formats(String title) {
    var message = new ViewerMessage(title, "Message", ViewerMessage.Level.WARN);
    assertEquals(title, message.title());
  }

  @ParameterizedTest
  @ValueSource(
      strings = {"Short message", "Very long message with multiple sentences and details.", ""})
  void viewerMessage_accepts_various_message_formats(String messageContent) {
    var message = new ViewerMessage("Title", messageContent, ViewerMessage.Level.ERROR);
    assertEquals(messageContent, message.message());
  }

  @ParameterizedTest
  @EnumSource(ViewerMessage.Level.class)
  void viewerMessage_accepts_all_level_values(ViewerMessage.Level level) {
    var message = new ViewerMessage("Title", "Message", level);
    assertEquals(level, message.level());
  }

  @Test
  void viewerMessage_level_enum_has_expected_values() {
    var levels = ViewerMessage.Level.values();

    assertEquals(3, levels.length);
    assertArrayEquals(
        new ViewerMessage.Level[] {
          ViewerMessage.Level.INFO, ViewerMessage.Level.WARN, ViewerMessage.Level.ERROR
        },
        levels);
  }

  @Test
  void viewerMessage_is_record_with_proper_equality() {
    var message1 = new ViewerMessage("Title", "Message", ViewerMessage.Level.INFO);
    var message2 = new ViewerMessage("Title", "Message", ViewerMessage.Level.INFO);
    var message3 = new ViewerMessage("Different Title", "Message", ViewerMessage.Level.INFO);

    assertAll(
        () -> assertEquals(message1, message2),
        () -> assertEquals(message1.hashCode(), message2.hashCode()),
        () -> assertNotEquals(message1, message3),
        () -> assertEquals(message1, message1));
  }

  @Test
  void viewerMessage_toString_contains_all_components() {
    var message = new ViewerMessage("Test Title", "Test Message", ViewerMessage.Level.WARN);
    String string = message.toString();

    assertAll(
        () -> assertTrue(string.contains("Test Title")),
        () -> assertTrue(string.contains("Test Message")),
        () -> assertTrue(string.contains("Warning")));
  }

  // Tests for static factory methods
  @Test
  void info_factory_method_creates_info_message() {
    var message = ViewerMessage.info("Info Title", "Info Message");

    assertAll(
        () -> assertEquals("Info Title", message.title()),
        () -> assertEquals("Info Message", message.message()),
        () -> assertEquals(Level.INFO, message.level()));
  }

  @Test
  void warn_factory_method_creates_warning_message() {
    var message = ViewerMessage.warn("Warning Title", "Warning Message");

    assertAll(
        () -> assertEquals("Warning Title", message.title()),
        () -> assertEquals("Warning Message", message.message()),
        () -> assertEquals(Level.WARN, message.level()));
  }

  @Test
  void error_factory_method_creates_error_message() {
    var message = ViewerMessage.error("Error Title", "Error Message");

    assertAll(
        () -> assertEquals("Error Title", message.title()),
        () -> assertEquals("Error Message", message.message()),
        () -> assertEquals(Level.ERROR, message.level()));
  }

  @Test
  void fromException_creates_error_message_with_exception_details() {
    var exception = new RuntimeException("Test exception message");
    var message = ViewerMessage.fromException("Error occurred", exception);

    assertAll(
        () -> assertEquals("Error occurred", message.title()),
        () -> assertEquals("Test exception message", message.message()),
        () -> assertEquals(Level.ERROR, message.level()));
  }

  @Test
  void fromException_handles_null_exception_message() {
    var exception = new RuntimeException((String) null);
    var message = ViewerMessage.fromException("Error occurred", exception);

    assertAll(
        () -> assertEquals("Error occurred", message.title()),
        () -> assertEquals("RuntimeException", message.message()),
        () -> assertEquals(Level.ERROR, message.level()));
  }

  // Tests for convenience methods
  @Test
  void isProblem_returns_correct_values() {
    assertAll(
        () -> assertFalse(ViewerMessage.info("Title", "Message").isProblem()),
        () -> assertTrue(ViewerMessage.warn("Title", "Message").isProblem()),
        () -> assertTrue(ViewerMessage.error("Title", "Message").isProblem()));
  }

  @Test
  void isInfo_returns_correct_values() {
    assertAll(
        () -> assertTrue(ViewerMessage.info("Title", "Message").isInfo()),
        () -> assertFalse(ViewerMessage.warn("Title", "Message").isInfo()),
        () -> assertFalse(ViewerMessage.error("Title", "Message").isInfo()));
  }

  @Test
  void isWarning_returns_correct_values() {
    assertAll(
        () -> assertFalse(ViewerMessage.info("Title", "Message").isWarning()),
        () -> assertTrue(ViewerMessage.warn("Title", "Message").isWarning()),
        () -> assertFalse(ViewerMessage.error("Title", "Message").isWarning()));
  }

  @Test
  void isError_returns_correct_values() {
    assertAll(
        () -> assertFalse(ViewerMessage.info("Title", "Message").isError()),
        () -> assertFalse(ViewerMessage.warn("Title", "Message").isError()),
        () -> assertTrue(ViewerMessage.error("Title", "Message").isError()));
  }

  @Test
  void getDisplayText_formats_message_correctly() {
    assertAll(
        () ->
            assertEquals(
                "[Information] Info: Info message",
                ViewerMessage.info("Info", "Info message").getDisplayText()),
        () ->
            assertEquals(
                "[Warning] Warn: Warn message",
                ViewerMessage.warn("Warn", "Warn message").getDisplayText()),
        () ->
            assertEquals(
                "[Error] Err: Error message",
                ViewerMessage.error("Err", "Error message").getDisplayText()));
  }

  // Tests for Level enum methods
  @Test
  void level_getDisplayName_returns_correct_values() {
    assertAll(
        () -> assertEquals("Information", Level.INFO.getDisplayName()),
        () -> assertEquals("Warning", Level.WARN.getDisplayName()),
        () -> assertEquals("Error", Level.ERROR.getDisplayName()));
  }

  @Test
  void level_getXmlValue_returns_correct_values() {
    assertAll(
        () -> assertEquals("info", Level.INFO.getXmlValue()),
        () -> assertEquals("warning", Level.WARN.getXmlValue()),
        () -> assertEquals("error", Level.ERROR.getXmlValue()));
  }

  @Test
  void level_toString_returns_display_name() {
    assertAll(
        () -> assertEquals("Information", Level.INFO.toString()),
        () -> assertEquals("Warning", Level.WARN.toString()),
        () -> assertEquals("Error", Level.ERROR.toString()));
  }

  @Test
  void level_isProblem_returns_correct_values() {
    assertAll(
        () -> assertFalse(Level.INFO.isProblem()),
        () -> assertTrue(Level.WARN.isProblem()),
        () -> assertTrue(Level.ERROR.isProblem()));
  }

  @ParameterizedTest
  @ValueSource(strings = {"info", "INFO", "Info"})
  void level_fromXmlValue_parses_info_values(String xmlValue) {
    assertEquals(Level.INFO, Level.fromXmlValue(xmlValue));
  }

  @ParameterizedTest
  @ValueSource(strings = {"warning", "WARNING", "Warning", "warn", "WARN", "Warn"})
  void level_fromXmlValue_parses_warning_values(String xmlValue) {
    assertEquals(Level.WARN, Level.fromXmlValue(xmlValue));
  }

  @ParameterizedTest
  @ValueSource(strings = {"error", "ERROR", "Error", "err", "ERR", "Err"})
  void level_fromXmlValue_parses_error_values(String xmlValue) {
    assertEquals(Level.ERROR, Level.fromXmlValue(xmlValue));
  }

  @Test
  void level_fromXmlValue_returns_info_for_null() {
    assertEquals(Level.INFO, Level.fromXmlValue(null));
  }

  @ParameterizedTest
  @ValueSource(strings = {"unknown", "debug", "trace", "", "invalid"})
  void level_fromXmlValue_returns_info_for_unknown_values(String xmlValue) {
    assertEquals(Level.INFO, Level.fromXmlValue(xmlValue));
  }

  // Tests for XML constants
  @Test
  void xml_constants_have_expected_values() {
    assertAll(
        () -> assertEquals("Message", ViewerMessage.TAG_DOCUMENT_MSG),
        () -> assertEquals("title", ViewerMessage.MSG_ATTRIBUTE_TITLE),
        () -> assertEquals("description", ViewerMessage.MSG_ATTRIBUTE_DESC),
        () -> assertEquals("severity", ViewerMessage.MSG_ATTRIBUTE_LEVEL));
  }
}
