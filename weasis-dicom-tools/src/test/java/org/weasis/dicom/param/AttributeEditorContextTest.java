/*
 * Copyright (c) 2017-2019 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.param;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.awt.Color;
import java.awt.Rectangle;
import java.awt.Shape;
import java.util.List;
import org.dcm4che3.data.UID;
import org.dcm4che3.img.op.MaskArea;
import org.dcm4che3.img.util.Editable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.weasis.opencv.data.PlanarImage;

@DisplayNameGeneration(ReplaceUnderscores.class)
@ExtendWith(MockitoExtension.class)
class AttributeEditorContextTest {

  private static final String TEST_TSUID = UID.ExplicitVRLittleEndian;
  private static final String TEST_AET = "TEST_AET";
  private static final String TEST_HOSTNAME = "localhost";
  private static final Integer TEST_PORT = 11112;

  @Mock private DicomNode mockSourceNode;
  @Mock private DicomNode mockDestinationNode;

  private DicomNode realSourceNode;
  private DicomNode realDestinationNode;
  private AttributeEditorContext context;

  @BeforeEach
  void setUp() {
    realSourceNode = new DicomNode("SOURCE_AET", "source.example.com", 11112);
    realDestinationNode = new DicomNode("DEST_AET", "dest.example.com", 11113);
    context = new AttributeEditorContext(TEST_TSUID, realSourceNode, realDestinationNode);
  }

  @Nested
  class Construction {

    @Test
    void creates_context_with_valid_parameters() {
      var newContext = new AttributeEditorContext(TEST_TSUID, realSourceNode, realDestinationNode);

      assertEquals(TEST_TSUID, newContext.getTsuid());
      assertEquals(realSourceNode, newContext.getSourceNode());
      assertEquals(realDestinationNode, newContext.getDestinationNode());
      assertEquals(AttributeEditorContext.Abort.NONE, newContext.getAbort());
      assertNull(newContext.getAbortMessage());
      assertNotNull(newContext.getProperties());
      assertTrue(newContext.getProperties().isEmpty());
      assertNull(newContext.getMaskArea());
    }

    @Test
    void initializes_properties_as_empty_collection() {
      var properties = context.getProperties();

      assertNotNull(properties);
      assertTrue(properties.isEmpty());
    }
  }

  @Nested
  class Abort_Management {

    @Test
    void has_default_abort_none() {
      assertEquals(AttributeEditorContext.Abort.NONE, context.getAbort());
      assertFalse(context.shouldAbort());
    }

    @ParameterizedTest
    @EnumSource(AttributeEditorContext.Abort.class)
    void sets_abort_status(AttributeEditorContext.Abort abort) {
      context.setAbort(abort);

      assertEquals(abort, context.getAbort());
      assertEquals(abort != AttributeEditorContext.Abort.NONE, context.shouldAbort());
    }

    @Test
    void handles_null_abort_status() {
      context.setAbort(AttributeEditorContext.Abort.FILE_EXCEPTION);
      context.setAbort(null);

      assertEquals(AttributeEditorContext.Abort.NONE, context.getAbort());
      assertFalse(context.shouldAbort());
    }

    @Test
    void sets_abort_message() {
      var message = "Connection failed";
      context.setAbortMessage(message);

      assertEquals(message, context.getAbortMessage());
    }

    @Test
    void handles_null_abort_message() {
      context.setAbortMessage("Initial message");
      context.setAbortMessage(null);

      assertNull(context.getAbortMessage());
    }

    @Test
    void sets_abort_with_message() {
      var abort = AttributeEditorContext.Abort.CONNECTION_EXCEPTION;
      var message = "Network error occurred";

      context.setAbort(abort, message);

      assertEquals(abort, context.getAbort());
      assertEquals(message, context.getAbortMessage());
      assertTrue(context.shouldAbort());
    }

    @Test
    void sets_abort_with_null_message() {
      context.setAbort(AttributeEditorContext.Abort.FILE_EXCEPTION, null);

      assertEquals(AttributeEditorContext.Abort.FILE_EXCEPTION, context.getAbort());
      assertNull(context.getAbortMessage());
    }
  }

  @Nested
  class Abort_Enum {

    @Test
    void none_abort_means_continue() {
      var abort = AttributeEditorContext.Abort.NONE;

      assertEquals("NONE", abort.name());
    }

    @Test
    void file_exception_abort_skips_current_file() {
      var abort = AttributeEditorContext.Abort.FILE_EXCEPTION;

      assertEquals("FILE_EXCEPTION", abort.name());
    }

    @Test
    void connection_exception_abort_terminates_association() {
      var abort = AttributeEditorContext.Abort.CONNECTION_EXCEPTION;

      assertEquals("CONNECTION_EXCEPTION", abort.name());
    }

    @Test
    void has_three_abort_values() {
      var values = AttributeEditorContext.Abort.values();

      assertEquals(3, values.length);
      assertEquals(AttributeEditorContext.Abort.NONE, values[0]);
      assertEquals(AttributeEditorContext.Abort.FILE_EXCEPTION, values[1]);
      assertEquals(AttributeEditorContext.Abort.CONNECTION_EXCEPTION, values[2]);
    }
  }

  @Nested
  class Node_Information {

    @Test
    void returns_source_node() {
      assertEquals(realSourceNode, context.getSourceNode());
    }

    @Test
    void returns_destination_node() {
      assertEquals(realDestinationNode, context.getDestinationNode());
    }

    @Test
    void returns_transfer_syntax_uid() {
      assertEquals(TEST_TSUID, context.getTsuid());
    }
  }

  @Nested
  class Mask_Area_Management {

    @Test
    void has_null_mask_area_by_default() {
      assertNull(context.getMaskArea());
    }

    @Test
    void sets_mask_area() {
      List<Shape> shapes = List.of(new Rectangle(0, 0, 100, 100));
      var maskArea = new MaskArea(shapes, Color.BLACK);

      context.setMaskArea(maskArea);

      assertEquals(maskArea, context.getMaskArea());
    }

    @Test
    void handles_null_mask_area() {
      List<Shape> shapes = List.of(new Rectangle(0, 0, 50, 50));
      var maskArea = new MaskArea(shapes);
      context.setMaskArea(maskArea);

      context.setMaskArea(null);

      assertNull(context.getMaskArea());
    }

    @Test
    void returns_editable_image_with_mask() {
      List<Shape> shapes = List.of(new Rectangle(10, 10, 80, 80));
      var maskArea = new MaskArea(shapes, Color.RED);
      context.setMaskArea(maskArea);

      Editable<PlanarImage> editable = context.getEditable();

      assertNotNull(editable);
    }

    @Test
    void returns_null_editable_without_mask() {
      Editable<PlanarImage> editable = context.getEditable();
      assertNull(editable);
    }
  }

  @Nested
  class Properties_Management {

    @Test
    void returns_mutable_properties() {
      var properties = context.getProperties();

      assertNotNull(properties);
      assertEquals(0, properties.size());

      properties.setProperty("test.key", "test.value");
      assertEquals("test.value", properties.getProperty("test.key"));
    }

    @Test
    void properties_persist_across_calls() {
      context.getProperties().setProperty("persistent.key", "persistent.value");

      assertEquals("persistent.value", context.getProperties().getProperty("persistent.key"));
    }

    @Test
    void properties_are_same_instance() {
      var properties1 = context.getProperties();
      var properties2 = context.getProperties();

      assertSame(properties1, properties2);
    }
  }

  @Nested
  class Pixel_Processing_Detection {

    @Test
    void has_no_pixel_processing_by_default() {
      assertFalse(context.hasPixelProcessing());
    }

    @Test
    void has_pixel_processing_with_mask_area() {
      List<Shape> shapes = List.of(new Rectangle(0, 0, 100, 100));
      var maskArea = new MaskArea(shapes);
      context.setMaskArea(maskArea);

      assertTrue(context.hasPixelProcessing());
    }

    @Test
    void has_pixel_processing_with_defacing_enabled() {
      context.getProperties().setProperty("defacing", "true");

      assertTrue(context.hasPixelProcessing());
    }

    @ParameterizedTest
    @ValueSource(strings = {"true", "TRUE"})
    void has_pixel_processing_with_truthy_defacing_values(String value) {
      context.getProperties().setProperty("defacing", value);

      assertTrue(context.hasPixelProcessing());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"false", "FALSE", "no", "NO", "0", "off", "OFF", "   "})
    void has_no_pixel_processing_with_falsy_defacing_values(String value) {
      if (value != null) {
        context.getProperties().setProperty("defacing", value);
      }

      assertFalse(context.hasPixelProcessing());
    }

    @Test
    void has_pixel_processing_with_both_mask_and_defacing() {
      List<Shape> shapes = List.of(new Rectangle(20, 20, 60, 60));
      var maskArea = new MaskArea(shapes, Color.BLUE);
      context.setMaskArea(maskArea);
      context.getProperties().setProperty("defacing", "true");

      assertTrue(context.hasPixelProcessing());
    }
  }

  @Nested
  class String_Representation {

    @Test
    void has_meaningful_string_representation() {
      var toString = context.toString();

      assertNotNull(toString);
      assertTrue(toString.contains("AttributeEditorContext"));
      assertTrue(toString.contains(TEST_TSUID));
      assertTrue(toString.contains("sourceNode="));
      assertTrue(toString.contains("destinationNode="));
      assertTrue(toString.contains("abort=NONE"));
      assertTrue(toString.contains("propertiesCount=0"));
    }

    @Test
    void includes_abort_message_in_string_when_present() {
      var message = "Test abort message";
      context.setAbort(AttributeEditorContext.Abort.FILE_EXCEPTION, message);

      var toString = context.toString();

      assertTrue(toString.contains("abort=FILE_EXCEPTION"));
      assertTrue(toString.contains("abortMessage='" + message + "'"));
    }

    @Test
    void includes_mask_area_in_string_when_present() {
      List<Shape> shapes = List.of(new Rectangle(0, 0, 50, 50));
      var maskArea = new MaskArea(shapes);
      context.setMaskArea(maskArea);

      var toString = context.toString();

      assertTrue(toString.contains("maskArea=" + maskArea.toString()));
    }

    @Test
    void shows_properties_count_in_string() {
      context.getProperties().setProperty("prop1", "value1");
      context.getProperties().setProperty("prop2", "value2");

      var toString = context.toString();

      assertTrue(toString.contains("propertiesCount=2"));
    }
  }

  @Nested
  class Integration_Scenarios {

    @Test
    void typical_usage_scenario_without_processing() {
      // Typical context for simple attribute editing without pixel processing
      var sourceNode = new DicomNode("WORKSTATION", "10.0.0.1", 11112);
      var destNode = new DicomNode("PACS", "10.0.0.100", 104);
      var ctx = new AttributeEditorContext(UID.ImplicitVRLittleEndian, sourceNode, destNode);

      ctx.getProperties().setProperty("anonymize", "true");
      ctx.getProperties().setProperty("institution", "Test Hospital");

      assertEquals(UID.ImplicitVRLittleEndian, ctx.getTsuid());
      assertEquals("WORKSTATION", ctx.getSourceNode().getAet());
      assertEquals("PACS", ctx.getDestinationNode().getAet());
      assertFalse(ctx.hasPixelProcessing());
      assertFalse(ctx.shouldAbort());
      assertEquals("true", ctx.getProperties().getProperty("anonymize"));
    }

    @Test
    void typical_usage_scenario_with_processing() {
      // Scenario with pixel processing for anonymization
      List<Shape> shapes =
          List.of(
              new Rectangle(10, 10, 100, 50), // Name area
              new Rectangle(10, 70, 150, 30) // ID area
              );
      var maskArea = new MaskArea(shapes, Color.BLACK);

      context.setMaskArea(maskArea);
      context.getProperties().setProperty("defacing", "true");
      context.getProperties().setProperty("reason", "patient anonymization");

      assertTrue(context.hasPixelProcessing());
      assertNotNull(context.getEditable());
      assertEquals("patient anonymization", context.getProperties().getProperty("reason"));
    }

    @Test
    void error_handling_scenario() {
      // Scenario where processing fails and needs to abort
      var errorMessage = "Failed to process patient data: network timeout";

      context.setAbort(AttributeEditorContext.Abort.CONNECTION_EXCEPTION, errorMessage);
      context
          .getProperties()
          .setProperty("error.timestamp", String.valueOf(System.currentTimeMillis()));

      assertTrue(context.shouldAbort());
      assertEquals(AttributeEditorContext.Abort.CONNECTION_EXCEPTION, context.getAbort());
      assertEquals(errorMessage, context.getAbortMessage());
      assertNotNull(context.getProperties().getProperty("error.timestamp"));
    }
  }
}
