import { Component, Input, OnDestroy, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ReadableSpan } from '@opentelemetry/sdk-trace-base';
import { Attributes, HrTime, SpanStatusCode } from '@opentelemetry/api';
import { Subscription } from 'rxjs';

import { TracingService } from '../../../../../services/tracing.service';
import { TraceGroupsService, TraceGroup } from '../../../../../services/trace-groups.service';

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
  // View of groups comes from service
  traceGroups: TraceGroup[] = [];

  private sub?: Subscription;
  private groupsSub?: Subscription;
  private seenKeys = new Set<string>();

  constructor(private tracing: TracingService, private traceGroupsService: TraceGroupsService) {}

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
    // Subscribe to groups store for this service/op
    this.groupsSub?.unsubscribe();
    this.groupsSub = this.traceGroupsService
      .selectGroups(this.serviceName, this.operationName)
      .subscribe(groups => (this.traceGroups = groups));

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

          // Delegate grouped update to service
          this.traceGroupsService.upsertSpans(this.serviceName, this.operationName, fresh);
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
    if (this.groupsSub) {
      this.groupsSub.unsubscribe();
      this.groupsSub = undefined;
    }
    this.connected = false;
  }

  clear(): void {
    this.spans = [];
    this.seenKeys.clear();
  this.traceGroups = [];
  this.traceGroupsService.clear(this.serviceName, this.operationName);
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
  // Kept for the component's own time formatting etc; store has its own copies as well.
  private toNanos(t?: HrTime): bigint {
    if (!t) return -1n;
    const [s, ns] = t;
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
