import { Component, Input, OnDestroy, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ReadableSpan } from '@opentelemetry/sdk-trace-base';
import { Attributes, HrTime, SpanStatusCode } from '@opentelemetry/api';
import { Subscription } from 'rxjs';

import { TracingService } from '../../../../../services/tracing.service';

@Component({
  selector: 'app-live-traces',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './live-traces.component.html',
  styleUrls: ['./live-traces.component.css']
})
export class LiveTracesComponent implements OnInit, OnDestroy {
  @Input() serviceName!: string;
  @Input() operationName!: string;
  @Input() autoConnect = false;
  @Input() maxItems = 200;

  connected = false;
  error = '';
  spans: ReadableSpan[] = [];
  // Events grouped by trace, retaining spans only for context (not rendered)
  traceGroups: {
    traceId: string;
    spans: ReadableSpan[];
    events: {
      traceId: string;
      spanId: string;
      spanName: string;
      name: string;
      time?: HrTime;
      attributes?: Attributes;
    }[];
    open: boolean;
  hasError?: boolean;
  errorMessage?: string;
    startTime?: HrTime; // first event time
    endTime?: HrTime;   // last event time
  }[] = [];

  private sub?: Subscription;
  private seenKeys = new Set<string>();
  private traceIndex = new Map<string, number>();

  constructor(private tracing: TracingService) {}

  ngOnInit(): void {
    if (this.autoConnect) {
      this.connect();
    }
  }

  ngOnDestroy(): void {
    this.disconnect();
  }

  connect(): void {
    if (this.connected) return;
    this.connected = true;
    this.error = '';
    this.spans = [];
  this.seenKeys.clear();
  this.traceGroups = [];
  this.traceIndex.clear();

    this.sub = this.tracing.streamSpans(this.serviceName, this.operationName).subscribe({
      next: (spans: ReadableSpan[]) => {
        if (!Array.isArray(spans) || spans.length === 0) return;
        const fresh: ReadableSpan[] = [];
        for (const s of spans) {
          const ctx = s.spanContext();
          const key = `${ctx.traceId}/${ctx.spanId}`;
          if (!this.seenKeys.has(key)) {
            this.seenKeys.add(key);
            fresh.push(s);
          }
        }
        if (fresh.length > 0) {
          // Update flat history (newest first)
          this.spans = [...fresh, ...this.spans];
          if (this.spans.length > this.maxItems) this.spans.length = this.maxItems;

          // Update grouped view by traceId, collecting events only
          for (const s of fresh) {
            const tid = s.spanContext().traceId;
            let idx = this.traceIndex.get(tid);
            if (idx === undefined) {
              this.traceGroups.unshift({ traceId: tid, spans: [], events: [], open: false, hasError: false });
              this.traceIndex.set(tid, 0);
              // Shift existing indices
              for (let i = 1; i < this.traceGroups.length; i++) {
                this.traceIndex.set(this.traceGroups[i].traceId, i);
              }
              idx = 0;
            }
            const group = this.traceGroups[idx];
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
              };
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
        }
      },
      error: (err: any) => {
        this.error = err?.message || 'Stream error';
        this.connected = false;
      }
    })
  }

  disconnect(): void {
    if (this.sub) {
      this.sub.unsubscribe();
      this.sub = undefined;
    }
    this.connected = false;
  }

  clear(): void {
    this.spans = [];
    this.seenKeys.clear();
    this.traceGroups = [];
    this.traceIndex.clear();
  }

  trackBySpan(index: number, s: ReadableSpan): string {
    const ctx = s.spanContext();
    return `${ctx.traceId}/${ctx.spanId}`;
  }

  trackByTrace(index: number, g: { traceId: string }): string {
    return g.traceId;
  }

  toggleGroup(g: { open: boolean }): void {
    g.open = !g.open;
  }

  trackByEvent(index: number, e: { traceId: string; spanId: string; name: string; time?: HrTime }): string {
    const t = e.time ? `${e.time[0]}-${e.time[1]}` : 'no-time';
    return `${e.traceId}/${e.spanId}/${e.name}/${t}`;
  }

  getEventTitle(e: { name: string; attributes?: Attributes }): string {
    const raw = (e.attributes as any)?.['message'];
    if (raw === undefined || raw === null) return e.name;
    if (typeof raw === 'string') return raw;
    try {
      return JSON.stringify(raw);
    } catch {
      return String(raw);
    }
  }

  hasNonMessageAttributes(attrs?: Attributes): boolean {
    if (!attrs) return false;
    for (const k in attrs) {
      if (Object.prototype.hasOwnProperty.call(attrs, k) && k !== 'message') return true;
    }
    return false;
  }

  formatHrTime(t?: HrTime): string {
  if (!t) return '';
  const [sec, nsec] = t;
  if (!Number.isFinite(sec) || !Number.isFinite(nsec) || (sec === 0 && nsec === 0)) return '';
  const msEpoch = sec * 1000 + Math.floor(nsec / 1_000_000);
  const d = new Date(msEpoch);
  const hh = String(d.getHours()).padStart(2, '0');
  const mm = String(d.getMinutes()).padStart(2, '0');
  const ss = String(d.getSeconds()).padStart(2, '0');
  const ms = String(d.getMilliseconds()).padStart(3, '0');
  return `${hh}:${mm}:${ss}.${ms}`;
  }

  get totalEvents(): number {
    return this.traceGroups.reduce((acc, g) => acc + (g.events?.length || 0), 0);
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
