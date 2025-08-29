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
package io.github.microcks.event;

import io.opentelemetry.sdk.trace.ReadableSpan;
import org.springframework.context.ApplicationEvent;

/**
 * Event raised when a new span is stored in the SpanStorageService. This event can be used by other components to react
 * to new span data in real-time.
 *
 *
 */
public class SpanStoredEvent extends ApplicationEvent {

   private final ReadableSpan span;
   private final String traceId;

   /**
    * Create a new span stored event.
    *
    * @param source  The object on which the event initially occurred
    * @param span    The span that was stored
    * @param traceId The trace ID associated with the span
    */
   public SpanStoredEvent(Object source, ReadableSpan span, String traceId) {
      super(source);
      this.span = span;
      this.traceId = traceId;
   }

   /**
    * Get the span that was stored.
    *
    * @return The stored span
    */
   public ReadableSpan getSpan() {
      return span;
   }

   /**
    * Get the trace ID associated with the span.
    *
    * @return The trace ID
    */
   public String getTraceId() {
      return traceId;
   }
}
