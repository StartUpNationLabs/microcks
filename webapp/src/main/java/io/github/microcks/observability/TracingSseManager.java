/*
 * Copyright The Microcks Authors.
 * Licensed under the Apache License, Version 2.0
 */
package io.github.microcks.observability;

import io.github.microcks.event.SpanStoredEvent;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.sdk.trace.ReadableSpan;
import io.opentelemetry.sdk.trace.data.SpanData;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Encapsulates SSE subscription management for tracing streams.
 */
@Component
public class TracingSseManager {

   private final SpanStorageService spanStorageService;

   // SSE subscriptions keyed by service+operation to avoid O(N) scans on each event.
   // Conventions:
   // - Empty serviceName ("") matches any service for the given operation
   // - Empty operationName ("") matches any operation for the given service
   // - Both empty ("", "") matches any service and any operation (global)
   private final Map<ServiceOperationKey, CopyOnWriteArrayList<Subscription>> subscriptions = new ConcurrentHashMap<>();

   // Heartbeat scheduler to keep connections alive
   private final ScheduledExecutorService heartbeatScheduler = Executors.newScheduledThreadPool(1, r -> {
      Thread t = new Thread(r, "sse-heartbeat");
      t.setDaemon(true);
      return t;
   });

   TracingSseManager(SpanStorageService spanStorageService) {
      this.spanStorageService = spanStorageService;
   }

   void addSubscription(String serviceName, String operationName, SseEmitter emitter) {
      ServiceOperationKey key = new ServiceOperationKey(serviceName, operationName);
      Subscription subscription = new Subscription(emitter, key);

      subscriptions.computeIfAbsent(key, k -> new CopyOnWriteArrayList<>()).add(subscription);

      Runnable cleanup = () -> removeSubscription(subscription);
      emitter.onCompletion(cleanup);
      emitter.onTimeout(cleanup);
      emitter.onError(ex -> cleanup.run());
   }

   /**
    * Handle SpanStoredEvent asynchronously to avoid blocking the main thread. For each span stored, check if there are
    * any subscriptions matching the service and operation names. If matches are found, send the trace spans to the
    * corresponding subscribers.
    *
    * @param event the SpanStoredEvent containing the trace ID of the stored span
    */
   @EventListener
   @Async
   void handleSpanStoredEvent(SpanStoredEvent event) {
      String traceId = event.getTraceId();

      List<ReadableSpan> spansByTraceId = spanStorageService.getSpansForTrace(traceId);
      if (spansByTraceId == null || spansByTraceId.isEmpty()) {
         return;
      }

      Set<ServiceOperationKey> keysInTrace = extractKeysFromSpans(spansByTraceId);

      // Collect unique recipients so we don't send duplicates when multiple services/operations overlap.
      Set<Subscription> recipients = new HashSet<>();
      for (ServiceOperationKey key : keysInTrace) {
         // Exact match
         CopyOnWriteArrayList<Subscription> list = subscriptions.get(key);
         if (list != null && !list.isEmpty()) {
            recipients.addAll(list);
         }
         // Wildcard serviceName match: "" matches any service for this operation
         ServiceOperationKey wildcardKey = new ServiceOperationKey("", key.operationName());
         CopyOnWriteArrayList<Subscription> wildcardList = subscriptions.get(wildcardKey);
         if (wildcardList != null && !wildcardList.isEmpty()) {
            recipients.addAll(wildcardList);
         }
         // Wildcard operationName match: "" matches any operation for this service
         ServiceOperationKey wildcardOpKey = new ServiceOperationKey(key.serviceName(), "");
         CopyOnWriteArrayList<Subscription> wildcardOpList = subscriptions.get(wildcardOpKey);
         if (wildcardOpList != null && !wildcardOpList.isEmpty()) {
            recipients.addAll(wildcardOpList);
         }
         // Global wildcard: ("", "") matches any service and any operation
         CopyOnWriteArrayList<Subscription> globalList = subscriptions.get(new ServiceOperationKey("", ""));
         if (globalList != null && !globalList.isEmpty()) {
            recipients.addAll(globalList);
         }
      }

      for (Subscription sub : recipients) {
         sub.sendTrace(spansByTraceId);
      }
   }

   void cleanup() {
      subscriptions.values().forEach(list -> list.forEach(Subscription::cancel));
      subscriptions.clear();
      heartbeatScheduler.shutdownNow();
   }

   private void removeSubscription(Subscription subscription) {
      subscription.cancel();
      CopyOnWriteArrayList<Subscription> list = subscriptions.get(subscription.key);
      if (list != null) {
         list.remove(subscription);
         if (list.isEmpty()) {
            subscriptions.remove(subscription.key);
         }
      }
   }


   private Set<ServiceOperationKey> extractKeysFromSpans(List<ReadableSpan> spans) {
      Set<ServiceOperationKey> keys = new HashSet<>();
      for (ReadableSpan s : spans) {
         Map<AttributeKey<?>, Object> attributes = s.toSpanData().getAttributes().asMap();
         String svc = (String) attributes.get(AttributeKey.stringKey("service.name"));
         String op = (String) attributes.get(AttributeKey.stringKey("operation.name"));
         if (svc != null && op != null) {
            keys.add(new ServiceOperationKey(svc, op));
         }
      }
      return keys;
   }

   /**
    * subscription holder
    */
   private final class Subscription {
      private final SseEmitter emitter;
      private final ServiceOperationKey key;
      private volatile boolean cancelled = false;
      private final AtomicLong seq = new AtomicLong(0);
      private final ScheduledFuture<?> heartbeatTask;

      Subscription(SseEmitter emitter, ServiceOperationKey key) {
         this.emitter = emitter;
         this.key = key;
         this.heartbeatTask = heartbeatScheduler.scheduleAtFixedRate(() -> {
            if (cancelled)
               return;
            try {
               SseEmitter.SseEventBuilder hb = SseEmitter.event().name("heartbeat")
                     .id(String.valueOf(seq.incrementAndGet())).comment("keep-alive");
               emitter.send(hb);
            } catch (Exception e) {
               emitter.completeWithError(e);
               cancel();
            }
         }, 15, 15, TimeUnit.SECONDS);
      }

      void sendTrace(List<ReadableSpan> spansByTraceId) {
         if (cancelled)
            return;
         try {
            List<SpanData> data = new ArrayList<>(spansByTraceId.size());
            for (ReadableSpan rs : spansByTraceId) {
               data.add(rs.toSpanData());
            }
            SseEmitter.SseEventBuilder event = SseEmitter.event().name("trace").data(data)
                  .id(String.valueOf(seq.incrementAndGet())).reconnectTime(3000);
            emitter.send(event);
         } catch (Exception e) {
            emitter.completeWithError(e);
            cancel();
         }
      }

      void cancel() {
         cancelled = true;
         if (heartbeatTask != null && !heartbeatTask.isCancelled()) {
            heartbeatTask.cancel(true);
         }
      }
   }

   /**
    * Simple key
    */
   private record ServiceOperationKey(String serviceName, String operationName) {

   }
}
