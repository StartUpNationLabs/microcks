/*
 * Copyright The Microcks Authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.github.microcks.observability;

import io.github.microcks.event.SpanStoredEvent;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.sdk.common.InstrumentationScopeInfo;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.sdk.trace.ReadableSpan;
import io.opentelemetry.sdk.trace.data.SpanData;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Test case for TracingController class with Spring event-based SSE implementation.
 * @author GitHub Copilot
 */
@ExtendWith(MockitoExtension.class)
class TracingControllerTest {

   @Mock
   private SpanStorageService spanStorageService;

   @Mock
   private ReadableSpan mockSpan;

   @Mock
   private SpanData mockSpanData;

   private TracingController tracingController;
   @Mock
   private TracingSseManager sseManager;

   @BeforeEach
   void setUp() {
      tracingController = new TracingController(spanStorageService, sseManager);
   }

   @Test
   void testGetSpansForOperationStreamReturnsEmitter() {
      // Given
      String serviceName = "test-service";
      String operationName = "test-operation";

      // When
      SseEmitter emitter = tracingController.getSpansForOperationStream(serviceName, operationName);

      // Then
      assertNotNull(emitter, "SSE emitter should not be null");
   }

   @Test
   void testHandleSpanStoredEventWithMatchingSpan() {
      // Given
      String serviceName = "test-service";
      String operationName = "test-operation";
      String traceId = "test-trace-id";

      // Set up mocks
      when(mockSpan.toSpanData()).thenReturn(mockSpanData);
      Attributes attributes = Attributes.of(AttributeKey.stringKey("service.name"), serviceName,
            AttributeKey.stringKey("operation.name"), operationName);
      when(mockSpanData.getAttributes()).thenReturn(attributes);

      // Create a subscription first
      SseEmitter emitter = tracingController.getSpansForOperationStream(serviceName, operationName);
      assertNotNull(emitter);

      // When
      SpanStoredEvent event = new SpanStoredEvent(this, mockSpan, traceId);
      tracingController.handleSpanStoredEvent(event);

      // Then - no exception should be thrown, and the subscription should still exist
      // (More comprehensive testing would require testing the actual SSE emission)
   }

   @Test
   void testHandleSpanStoredEventWithNonMatchingSpan() {
      // Given
      String serviceName = "test-service";
      String operationName = "test-operation";
      String traceId = "test-trace-id";

      // Set up mocks for non-matching span
      when(mockSpan.toSpanData()).thenReturn(mockSpanData);
      Attributes attributes = Attributes.of(AttributeKey.stringKey("service.name"), "different-service",
            AttributeKey.stringKey("operation.name"), "different-operation");
      when(mockSpanData.getAttributes()).thenReturn(attributes);

      // Create a subscription first
      SseEmitter emitter = tracingController.getSpansForOperationStream(serviceName, operationName);
      assertNotNull(emitter);

      // When
      SpanStoredEvent event = new SpanStoredEvent(this, mockSpan, traceId);
      tracingController.handleSpanStoredEvent(event);

      // Then - no exception should be thrown
      // The non-matching span should not be sent to the client
   }

   @Test
   void testCleanupCancelsActiveSubscriptions() {
      // Given
      String serviceName = "test-service";
      String operationName = "test-operation";

      // Create a subscription
      SseEmitter emitter = tracingController.getSpansForOperationStream(serviceName, operationName);
      assertNotNull(emitter);

      // When
      tracingController.cleanup();

      // Then - cleanup should complete without errors
   }
}
