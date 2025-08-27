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
import io.opentelemetry.sdk.trace.ReadableSpan;
import io.opentelemetry.api.trace.SpanContext;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Test case for SpanStorageService class with Spring event publishing.
 * @author GitHub Copilot
 */
@ExtendWith(MockitoExtension.class)
class SpanStorageServiceTest {

   @Mock
   private ApplicationEventPublisher eventPublisher;

   @Mock
   private ReadableSpan mockSpan;

   @Mock
   private SpanContext mockSpanContext;

   private SpanStorageService spanStorageService;

   @BeforeEach
   void setUp() {
      spanStorageService = new SpanStorageService(eventPublisher);
   }

   @Test
   void testStoreSpanPublishesEvent() {
      // Given
      String traceId = "test-trace-id";
      when(mockSpan.getSpanContext()).thenReturn(mockSpanContext);
      when(mockSpanContext.getTraceId()).thenReturn(traceId);
      when(mockSpan.getName()).thenReturn("test-span");

      // When
      spanStorageService.storeSpan(mockSpan);

      // Then
      ArgumentCaptor<SpanStoredEvent> eventCaptor = ArgumentCaptor.forClass(SpanStoredEvent.class);
      verify(eventPublisher).publishEvent(eventCaptor.capture());

      SpanStoredEvent publishedEvent = eventCaptor.getValue();
      assertNotNull(publishedEvent);
      assertEquals(mockSpan, publishedEvent.getSpan());
      assertEquals(traceId, publishedEvent.getTraceId());
      assertEquals(spanStorageService, publishedEvent.getSource());
   }

   @Test
   void testStoreSpanStoresInMemory() {
      // Given
      String traceId = "test-trace-id";
      when(mockSpan.getSpanContext()).thenReturn(mockSpanContext);
      when(mockSpanContext.getTraceId()).thenReturn(traceId);
      when(mockSpan.getName()).thenReturn("test-span");

      // When
      spanStorageService.storeSpan(mockSpan);

      // Then
      var storedSpans = spanStorageService.getSpansForTrace(traceId);
      assertEquals(1, storedSpans.size());
      assertEquals(mockSpan, storedSpans.get(0));
   }

   @Test
   void testGetTraceCountReturnsCorrectCount() {
      // Given
      String traceId1 = "trace-1";
      String traceId2 = "trace-2";

      ReadableSpan span1 = mock(ReadableSpan.class);
      SpanContext context1 = mock(SpanContext.class);
      when(span1.getSpanContext()).thenReturn(context1);
      when(context1.getTraceId()).thenReturn(traceId1);
      when(span1.getName()).thenReturn("span-1");

      ReadableSpan span2 = mock(ReadableSpan.class);
      SpanContext context2 = mock(SpanContext.class);
      when(span2.getSpanContext()).thenReturn(context2);
      when(context2.getTraceId()).thenReturn(traceId2);
      when(span2.getName()).thenReturn("span-2");

      // When
      spanStorageService.storeSpan(span1);
      spanStorageService.storeSpan(span2);

      // Then
      assertEquals(2, spanStorageService.getTraceCount());
   }
}
