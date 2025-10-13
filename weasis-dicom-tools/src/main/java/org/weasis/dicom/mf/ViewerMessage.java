/*
 * Copyright (c) 2017-2019 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.mf;

import java.util.Locale;
import java.util.Objects;
import org.weasis.core.util.annotations.Generated;

/**
 * Immutable record representing a message for DICOM viewer applications. This record encapsulates
 * user-facing notifications, warnings, and error messages with structured severity levels and
 * internationalization support.
 *
 * <p>Viewer messages are commonly used for:
 *
 * <ul>
 *   <li>Query result notifications and status updates
 *   <li>Error reporting during DICOM operations
 *   <li>Warning messages for data quality issues
 *   <li>Information messages for user guidance
 * </ul>
 *
 * <p><strong>Usage Examples:</strong>
 *
 * <pre>{@code
 * // Create an informational message
 * ViewerMessage info = ViewerMessage.info("Query Complete", "Found 42 studies");
 *
 * // Create a warning message
 * ViewerMessage warning = ViewerMessage.warn("Data Quality", "Some images may be corrupted");
 *
 * // Create an error message
 * ViewerMessage error = ViewerMessage.error("Connection Failed", "Unable to connect to PACS");
 * }</pre>
 *
 * @param title the message title, must not be null or blank
 * @param message the detailed message content, must not be null
 * @param level the severity level, must not be null
 * @see QueryResult
 * @see AbstractQueryResult
 */
public record ViewerMessage(String title, String message, Level level) {

  // XML serialization constants
  public static final String TAG_DOCUMENT_MSG = "Message";
  public static final String MSG_ATTRIBUTE_TITLE = "title";
  public static final String MSG_ATTRIBUTE_DESC = "description";
  public static final String MSG_ATTRIBUTE_LEVEL = "severity";

  /**
   * Enumeration of message severity levels, ordered from least to most severe.
   *
   * <p>Severity levels follow standard logging conventions:
   *
   * <ul>
   *   <li>{@code INFO} - Informational messages for normal operations
   *   <li>{@code WARN} - Warning messages indicating potential issues
   *   <li>{@code ERROR} - Error messages indicating serious problems
   * </ul>
   */
  public enum Level {
    /** Informational message indicating normal operation */
    INFO("Information", "info"),

    /** Warning message indicating potential issues that don't prevent operation */
    WARN("Warning", "warning"),

    /** Error message indicating serious problems that may prevent operation */
    ERROR("Error", "error");

    private final String displayName;
    private final String xmlValue;

    Level(String displayName, String xmlValue) {
      this.displayName = displayName;
      this.xmlValue = xmlValue;
    }

    /**
     * Returns the human-readable display name for this level.
     *
     * @return the display name
     */
    public String getDisplayName() {
      return displayName;
    }

    /**
     * Returns the XML representation value for this level.
     *
     * @return the XML value
     */
    public String getXmlValue() {
      return xmlValue;
    }

    /**
     * Parses a level from its XML representation.
     *
     * @param xmlValue the XML value to parse
     * @return the corresponding Level, or INFO if not found
     */
    public static Level fromXmlValue(String xmlValue) {
      if (xmlValue == null) {
        return INFO;
      }
      return switch (xmlValue.toLowerCase(Locale.ROOT)) {
        case "warning", "warn" -> WARN;
        case "error", "err" -> ERROR;
        default -> INFO;
      };
    }

    /**
     * Checks if this level indicates a problem (WARN or ERROR).
     *
     * @return true if this is WARN or ERROR level
     */
    public boolean isProblem() {
      return this != INFO;
    }

    @Override
    public String toString() {
      return displayName;
    }
  }

  /**
   * Compact constructor with validation.
   *
   * @param title the message title, must not be null or blank
   * @param message the detailed message content, must not be null
   * @param level the severity level, must not be null
   * @throws NullPointerException if any parameter is null
   * @throws IllegalArgumentException if title is blank
   */
  public ViewerMessage {
    Objects.requireNonNull(title, "Message title cannot be null");
    Objects.requireNonNull(message, "Message content cannot be null");
    Objects.requireNonNull(level, "Message level cannot be null");

    if (title.isBlank()) {
      throw new IllegalArgumentException("Message title cannot be blank");
    }
  }

  /**
   * Creates an informational message.
   *
   * @param title the message title
   * @param message the message content
   * @return a new ViewerMessage with INFO level
   */
  public static ViewerMessage info(String title, String message) {
    return new ViewerMessage(title, message, Level.INFO);
  }

  /**
   * Creates a warning message.
   *
   * @param title the message title
   * @param message the message content
   * @return a new ViewerMessage with WARN level
   */
  public static ViewerMessage warn(String title, String message) {
    return new ViewerMessage(title, message, Level.WARN);
  }

  /**
   * Creates an error message.
   *
   * @param title the message title
   * @param message the message content
   * @return a new ViewerMessage with ERROR level
   */
  public static ViewerMessage error(String title, String message) {
    return new ViewerMessage(title, message, Level.ERROR);
  }

  /**
   * Creates a message from an exception with ERROR level.
   *
   * @param title the message title
   * @param exception the exception to extract message from
   * @return a new ViewerMessage with ERROR level
   */
  public static ViewerMessage fromException(String title, Exception exception) {
    String exceptionMessage = exception.getMessage();
    String message =
        exceptionMessage != null ? exceptionMessage : exception.getClass().getSimpleName();
    return error(title, message);
  }

  /**
   * Checks if this message indicates a problem (WARN or ERROR level).
   *
   * @return true if this is a warning or error message
   */
  public boolean isProblem() {
    return level.isProblem();
  }

  /**
   * Checks if this message is informational.
   *
   * @return true if this is an INFO level message
   */
  public boolean isInfo() {
    return level == Level.INFO;
  }

  /**
   * Checks if this message is a warning.
   *
   * @return true if this is a WARN level message
   */
  public boolean isWarning() {
    return level == Level.WARN;
  }

  /**
   * Checks if this message is an error.
   *
   * @return true if this is an ERROR level message
   */
  public boolean isError() {
    return level == Level.ERROR;
  }

  /**
   * Returns a formatted string representation suitable for display to users.
   *
   * @return formatted message string
   */
  public String getDisplayText() {
    return String.format("[%s] %s: %s", level.getDisplayName(), title, message);
  }

  @Override
  public String toString() {
    return getDisplayText();
  }

  @Generated
  @Deprecated(since = "5.34.0.4", forRemoval = true)
  public enum eLevel {
    INFO(Level.INFO),
    WARN(Level.WARN),
    ERROR(Level.ERROR);

    private final Level newLevel;

    eLevel(Level newLevel) {
      this.newLevel = newLevel;
    }

    public Level toLevel() {
      return newLevel;
    }

    @Generated
    public static eLevel fromLevel(Level level) {
      return switch (level) {
        case INFO -> INFO;
        case WARN -> WARN;
        case ERROR -> ERROR;
      };
    }
  }

  @Generated
  @Deprecated(since = "5.34.0.4", forRemoval = true)
  public ViewerMessage(String title, String message, eLevel level) {
    this(title, message, level.toLevel());
  }

  @Generated
  @Deprecated(since = "5.34.0.4", forRemoval = true)
  public eLevel eLevel() {
    return eLevel.fromLevel(level);
  }
}
