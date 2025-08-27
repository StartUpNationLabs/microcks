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

import io.opentelemetry.sdk.trace.data.SpanData;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.EventListener;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Async;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import javax.annotation.PreDestroy;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * REST controller for accessing trace and span information stored by the SpanStorageService. Provides endpoints to
 * retrieve traces, spans, and storage statistics.
 *
 * @author GitHub Copilot
 */
@RestController
@RequestMapping("/api/traces")
public class TracingController {

   private final SpanStorageService spanStorageService;
   private final TracingSseManager sseManager;

   @Autowired
   public TracingController(SpanStorageService spanStorageService, TracingSseManager sseManager) {
      this.spanStorageService = spanStorageService;
      this.sseManager = sseManager;
   }


   /**
    * Cleanup SSE resources when the controller is destroyed.
    */
   @PreDestroy
   public void cleanup() {
      sseManager.cleanup();
   }

   /**
    * Get all trace IDs currently stored.
    *
    * @return Set of trace IDs
    */
   @GetMapping
   public ResponseEntity<Set<String>> getAllTraceIds() {
      return ResponseEntity.ok(spanStorageService.getAllTraceIds());
   }

   /**
    * Get all spans for a specific trace ID.
    *
    * @param traceId The trace ID to look up
    * @return List of spans for the trace
    */
   @GetMapping("/{traceId}/spans")
   public ResponseEntity<List<SpanData>> getSpansForTrace(@PathVariable("traceId") String traceId) {
      List<ReadableSpan> spans = spanStorageService.getSpansForTrace(traceId);
      if (spans.isEmpty()) {
         return ResponseEntity.notFound().build();
      }
      return ResponseEntity.ok(spans.stream().map(ReadableSpan::toSpanData).toList());
   }

   @GetMapping("/operations/spans")
   public ResponseEntity<List<List<ReadableSpan>>> getSpansForOperation(@RequestParam("serviceName") String serviceName,
         @RequestParam("operationName") String operationName) {
      List<String> traceIds = spanStorageService
            .queryTraceIdsBySpanAttributes(Map.of(io.opentelemetry.api.common.AttributeKey.stringKey("service.name"),
                  serviceName, io.opentelemetry.api.common.AttributeKey.stringKey("operation.name"), operationName));
      if (traceIds.isEmpty()) {
         return ResponseEntity.notFound().build();
      }

      List<List<ReadableSpan>> spansByTraceId = traceIds.stream().map(spanStorageService::getSpansForTrace).toList();


      return ResponseEntity.ok(spansByTraceId);
   }

   /**
    * Get storage statistics.
    *
    * @return Map containing statistics about the storage service
    */
   @GetMapping("/stats")
   public ResponseEntity<Map<String, Object>> getStorageStats() {
      return ResponseEntity.ok(spanStorageService.getServiceStats());
   }

   /**
    * Clear all stored traces and spans.
    *
    * @return Success message
    */
   @DeleteMapping
   public ResponseEntity<String> clearAllTraces() {
      spanStorageService.clearAll();
      return ResponseEntity.ok("All traces and spans have been cleared");
   }

   /**
    * Remove a specific trace and its spans.
    *
    * @param traceId The trace ID to remove
    * @return Success or not found response
    */
   @DeleteMapping("/{traceId}")
   public ResponseEntity<String> removeTrace(@PathVariable("traceId") String traceId) {
      boolean removed = spanStorageService.removeTrace(traceId);
      if (removed) {
         return ResponseEntity.ok("Trace " + traceId + " has been removed");
      } else {
         return ResponseEntity.notFound().build();
      }
   }

   /**
    * Get spans for an operation in real-time using Server-Sent Events. This endpoint streams new spans as they become
    * available.
    *
    * @param serviceName   The service name to filter spans by
    * @param operationName The operation name to filter spans by
    * @return SseEmitter for real-time span updates
    */
   @GetMapping(value = "/operations/spans/stream", produces = "text/event-stream")
   public SseEmitter getSpansForOperationStream(@RequestParam("serviceName") String serviceName,
         @RequestParam("operationName") String operationName) {
      SseEmitter emitter = new SseEmitter(Duration.ofMinutes(30).toMillis());
      sseManager.addSubscription(serviceName, operationName, emitter);
      return emitter;
   }

}
