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
import io.opentelemetry.sdk.trace.ReadableSpan;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service for storing and retrieving OpenTelemetry spans organized by trace ID. This service provides thread-safe
 * storage with memory management to prevent memory leaks.
 *
 * @author GitHub Copilot
 */
@Service
public class SpanStorageService {

   private static final Logger log = LoggerFactory.getLogger(SpanStorageService.class);

   /**
    * Thread-safe HashMap storing lists of spans grouped by trace ID. Key: trace ID (String), Value: List of spans for
    * that trace
    */
   private final Map<String, List<ReadableSpan>> spansByTraceId = new ConcurrentHashMap<>();

   /**
    * Event publisher for notifying about new spans
    */
   private final ApplicationEventPublisher eventPublisher;

   /**
    * Maximum number of traces to keep in memory to prevent memory leaks. When this limit is exceeded, oldest traces are
    * removed.
    */
   private static final int MAX_TRACES = 1000;

   /**
    * Maximum number of spans to keep per trace to prevent memory issues.
    */
   private static final int MAX_SPANS_PER_TRACE = 100;

   @Autowired
   public SpanStorageService(ApplicationEventPublisher eventPublisher) {
      this.eventPublisher = eventPublisher;
   }


   /**
    * Stores a span in the service, grouped by trace ID.
    *
    * @param span The span to store
    */
   public void storeSpan(ReadableSpan span) {
      String traceId = span.getSpanContext().getTraceId();

      // Add the span to our collection
      spansByTraceId.computeIfAbsent(traceId, k -> new ArrayList<>()).add(span);

      // Limit spans per trace to prevent memory issues
      List<ReadableSpan> spans = spansByTraceId.get(traceId);
      if (spans.size() > MAX_SPANS_PER_TRACE) {
         // Remove oldest span (index 0) to cap memory; ArrayList has no removeFirst()
         spans.remove(0);
      }

      // Limit total number of traces to prevent memory leaks
      if (spansByTraceId.size() > MAX_TRACES) {
         // Remove oldest trace (this is a simple approach - you might want to use LRU)
         String oldestTraceId = spansByTraceId.keySet().iterator().next();
         spansByTraceId.remove(oldestTraceId);
      }

      // Publish event about the new span only if it is a root span (no parent)
      // get parent traceid
      if (!span.getParentSpanContext().isValid()) {
         eventPublisher.publishEvent(new SpanStoredEvent(this, span, traceId));
      }
   }

   /**
    * Retrieves all spans for a given trace ID.
    *
    * @param traceId The trace ID to look up
    * @return A list of spans for the given trace ID, or an empty list if no spans found
    */
   public List<ReadableSpan> getSpansForTrace(String traceId) {
      return spansByTraceId.getOrDefault(traceId, new ArrayList<>());
   }

   /**
    * Retrieves all current trace IDs that have spans stored.
    *
    * @return A set of all trace IDs currently stored in the service
    */
   public Set<String> getAllTraceIds() {
      return spansByTraceId.keySet();
   }

   /**
    * Retrieves the total number of traces currently stored.
    *
    * @return The number of traces in storage
    */
   public int getTraceCount() {
      return spansByTraceId.size();
   }

   /**
    * Retrieves the total number of spans across all traces.
    *
    * @return The total number of spans stored
    */
   public int getTotalSpanCount() {
      return spansByTraceId.values().stream().mapToInt(List::size).sum();
   }

   /**
    * Clears all stored spans and traces from memory. Useful for testing or when you want to reset the service state.
    */
   public void clearAll() {
      log.info("Clearing all stored spans and traces from SpanStorageService");
      spansByTraceId.clear();
   }

   /**
    * Removes spans for a specific trace ID from storage.
    *
    * @param traceId The trace ID to remove
    * @return true if the trace was found and removed, false otherwise
    */
   public boolean removeTrace(String traceId) {
      List<ReadableSpan> removed = spansByTraceId.remove(traceId);
      return removed != null;
   }


   /**
    * Retrieves spans across all traces that match all specified attributes.
    *
    * @param requiredAttributes Map of attribute key -> expected value
    * @return A list of spans across all traces that match all the provided attributes
    */
   public List<ReadableSpan> querySpansAcrossAllTracesByAttributes(Map<AttributeKey<?>, Object> requiredAttributes) {
      List<ReadableSpan> allSpans = spansByTraceId.values().stream().flatMap(List::stream).toList();
      if (requiredAttributes == null || requiredAttributes.isEmpty()) {
         return allSpans;
      }
      return allSpans.stream()
            .filter(span -> requiredAttributes.entrySet().stream().allMatch(
                  entry -> valuesEqualAttr(span.toSpanData().getAttributes().get(entry.getKey()), entry.getValue())))
            .toList();
   }

   /**
    * Retrieves trace IDs that have spans matching all specified attributes.
    *
    * @param requiredAttributes Map of attribute key -> expected value
    * @return A List of trace IDs that have spans matching all the provided attributes sorted by recency (most recent
    *         first)
    */
   public List<String> queryTraceIdsBySpanAttributes(Map<AttributeKey<?>, Object> requiredAttributes) {
      if (requiredAttributes == null || requiredAttributes.isEmpty()) {
         return new ArrayList<>(spansByTraceId.keySet());
      }
      return spansByTraceId.entrySet().stream()
            .filter(entry -> entry.getValue().stream()
                  .anyMatch(span -> requiredAttributes.entrySet().stream()
                        .allMatch(reqAttr -> valuesEqualAttr(span.toSpanData().getAttributes().get(reqAttr.getKey()),
                              reqAttr.getValue()))))
            .map(Map.Entry::getKey)
            // Sort by recency - most recent first
            .sorted((id1, id2) -> {
               List<ReadableSpan> spans1 = spansByTraceId.get(id1);
               List<ReadableSpan> spans2 = spansByTraceId.get(id2);
               if (spans1.isEmpty() || spans2.isEmpty())
                  return 0;
               long endTime1 = spans1.stream().mapToLong(s -> s.toSpanData().getEndEpochNanos()).max().orElse(0);
               long endTime2 = spans2.stream().mapToLong(s -> s.toSpanData().getEndEpochNanos()).max().orElse(0);
               return Long.compare(endTime2, endTime1); // Descending order
            }).toList();
   }


   public static boolean valuesEqualAttr(Object actual, Object expected) {
      if (actual == null && expected == null)
         return true;
      if (actual == null || expected == null)
         return false;
      if (actual.getClass().isAssignableFrom(expected.getClass())
            || expected.getClass().isAssignableFrom(actual.getClass())) {
         return actual.equals(expected);
      }
      // Fallback: compare string representations to allow comparing numbers to strings, etc.
      return String.valueOf(actual).equals(String.valueOf(expected));
   }

   /**
    * Gets a summary of the current state of the service.
    *
    * @return A map containing statistics about the service state
    */
   public Map<String, Object> getServiceStats() {
      Map<String, Object> stats = new HashMap<>();
      stats.put("totalTraces", getTraceCount());
      stats.put("totalSpans", getTotalSpanCount());
      stats.put("maxTracesLimit", MAX_TRACES);
      stats.put("maxSpansPerTraceLimit", MAX_SPANS_PER_TRACE);

      // Add memory usage info
      if (!spansByTraceId.isEmpty()) {
         int minSpansInTrace = spansByTraceId.values().stream().mapToInt(List::size).min().orElse(0);
         int maxSpansInTrace = spansByTraceId.values().stream().mapToInt(List::size).max().orElse(0);
         double avgSpansPerTrace = spansByTraceId.values().stream().mapToInt(List::size).average().orElse(0.0);

         stats.put("minSpansInTrace", minSpansInTrace);
         stats.put("maxSpansInTrace", maxSpansInTrace);
         stats.put("avgSpansPerTrace", avgSpansPerTrace);
      }

      return stats;
   }
}
