import { Injectable } from '@angular/core';
import { BehaviorSubject, Observable } from 'rxjs';
import { Attributes, HrTime, SpanStatusCode } from '@opentelemetry/api';
import { ReadableSpan } from '@opentelemetry/sdk-trace-base';

export interface TraceEvent {
  traceId: string;
  spanId: string;
  spanName: string;
  name: string;
  time?: HrTime;
  attributes?: Attributes;
}

export interface TraceGroup {
  traceId: string;
  spans: ReadableSpan[];
  events: TraceEvent[];
  open: boolean;
  hasError?: boolean;
  errorMessage?: string;
  startTime?: HrTime; // first event time
  endTime?: HrTime;   // last event time
}

@Injectable({ providedIn: 'root' })
export class TraceGroupsService {
  private groupsByKey = new Map<string, TraceGroup[]>();
  private subjByKey = new Map<string, BehaviorSubject<TraceGroup[]>>();
  private indexByKey = new Map<string, Map<string, number>>(); // key -> (traceId -> idx)
  private aggregateByService = new Map<string, BehaviorSubject<TraceGroup[]>>();

  // Compose a stable key for service/op pair
  private key(serviceName: string, operationName?: string): string {
    return `${serviceName}::${operationName ?? ''}`;
  }

  // Public API
  selectGroups(serviceName: string, operationName?: string): Observable<TraceGroup[]> {
    const k = this.key(serviceName, operationName);
    let subj = this.subjByKey.get(k);
    if (!subj) {
      subj = new BehaviorSubject<TraceGroup[]>([]);
      this.subjByKey.set(k, subj);
      this.groupsByKey.set(k, []);
      this.indexByKey.set(k, new Map());
    }
    return subj.asObservable();
  }

  // Observe all groups for a service across all operations
  selectAllForService(serviceName: string): Observable<TraceGroup[]> {
    let subj = this.aggregateByService.get(serviceName);
    if (!subj) {
      subj = new BehaviorSubject<TraceGroup[]>(this.getSnapshotForService(serviceName));
      this.aggregateByService.set(serviceName, subj);
    }
    // Ensure latest aggregate is pushed
    subj.next(this.getSnapshotForService(serviceName));
    return subj.asObservable();
  }

  getSnapshot(serviceName: string, operationName?: string): TraceGroup[] {
    const k = this.key(serviceName, operationName);
    return (this.groupsByKey.get(k) ?? []).slice();
  }

  clear(serviceName: string, operationName?: string): void {
    const k = this.key(serviceName, operationName);
    this.groupsByKey.set(k, []);
    this.indexByKey.set(k, new Map());
    this.subjByKey.get(k)?.next([]);
  this.recomputeAggregate(serviceName);
  }

  // Append fresh spans to the store for a given service/op
  upsertSpans(serviceName: string, operationName: string | undefined, spans: ReadableSpan[]): void {
    if (!Array.isArray(spans) || spans.length === 0) return;
    const k = this.key(serviceName, operationName);
    if (!this.groupsByKey.has(k)) {
      this.groupsByKey.set(k, []);
      this.subjByKey.set(k, new BehaviorSubject<TraceGroup[]>([]));
      this.indexByKey.set(k, new Map());
    }

    const groups = this.groupsByKey.get(k)!;
    const index = this.indexByKey.get(k)!;

    for (const s of spans) {
      const tid = s.spanContext().traceId;
      let idx = index.get(tid);
      if (idx === undefined) {
        groups.unshift({ traceId: tid, spans: [], events: [], open: false, hasError: false });
        // Rebuild index: new item at 0, shift others
        index.clear();
        for (let i = 0; i < groups.length; i++) {
          index.set(groups[i].traceId, i);
        }
        idx = 0;
      }
      const group = groups[idx];
      // Keep span (not rendered) for context if needed
      group.spans.unshift(s);
      // Ensure spans are ordered by their start time (ascending)
      group.spans.sort((a, b) => {
        const na = this.toNanos(a.startTime);
        const nb = this.toNanos(b.startTime);
        return na < nb ? -1 : na > nb ? 1 : 0;
      });
      // If span has error, mark group and remember error message
      if (s.status?.code === SpanStatusCode.ERROR) {
        group.hasError = true;
        if (!group.errorMessage) {
          group.errorMessage = s.status?.message || 'Error';
        }
      }
      // Extract span events and add them newest-first in group
      const evts = Array.isArray(s.events) ? s.events : [];
      for (let i = evts.length - 1; i >= 0; i--) {
        const e = evts[i] as any;
        const evt = {
          traceId: tid,
          spanId: s.spanContext().spanId,
          spanName: s.name,
          name: e?.name,
          time: e?.time as HrTime,
          attributes: e?.attributes as Attributes,
        } as TraceEvent;
        group.events.unshift(evt);
        // Update group start/end bounds using event times
        group.startTime = this.minHrTime(group.startTime, evt.time);
        group.endTime = this.maxHrTime(group.endTime, evt.time);
      }
      // Sort events by event time (descending: newest first). Invalid times go last.
      group.events.sort((a, b) => {
        const na = this.toNanos(a.time as HrTime | undefined);
        const nb = this.toNanos(b.time as HrTime | undefined);
        if (na === nb) return 0;
        if (na < 0n && nb < 0n) return 0;
        if (na < 0n) return -1; // a invalid -> after b
        if (nb < 0n) return 1; // b invalid -> after a
        return nb < na ? 1 : -1;
      });
    }

    // Emit updated array reference
    this.subjByKey.get(k)!.next(groups);
    this.recomputeAggregate(serviceName);
  }

  // Snapshots/queries
  getSnapshotForService(serviceName: string): TraceGroup[] {
    const prefix = `${serviceName}::`;
    const out: TraceGroup[] = [];
    for (const [k, arr] of this.groupsByKey.entries()) {
      if (k.startsWith(prefix)) {
        out.push(...arr);
      }
    }
    return out.slice();
  }

  // Internal: recompute aggregate observable for a service if any listeners exist
  private recomputeAggregate(serviceName: string): void {
    const subj = this.aggregateByService.get(serviceName);
    if (subj) {
      subj.next(this.getSnapshotForService(serviceName));
    }
  }

  // Helpers to compare HrTime
  private toNanos(t?: HrTime): bigint {
    if (!t) return -1n;
    const [s, ns] = t;
    // Treat [0,0] or any non-finite as invalid
    if (!Number.isFinite(s) || !Number.isFinite(ns) || (s === 0 && ns === 0)) return -1n;
    return BigInt(Math.trunc(s)) * 1_000_000_000n + BigInt(Math.trunc(ns));
  }
  private minHrTime(a: HrTime | undefined, b: HrTime | undefined): HrTime | undefined {
    const an = this.toNanos(a);
    const bn = this.toNanos(b);
    if (an < 0n) return b;
    if (bn < 0n) return a;
    return an <= bn ? a : b;
  }
  private maxHrTime(a: HrTime | undefined, b: HrTime | undefined): HrTime | undefined {
    const an = this.toNanos(a);
    const bn = this.toNanos(b);
    if (an < 0n) return b;
    if (bn < 0n) return a;
    return an >= bn ? a : b;
  }
}
