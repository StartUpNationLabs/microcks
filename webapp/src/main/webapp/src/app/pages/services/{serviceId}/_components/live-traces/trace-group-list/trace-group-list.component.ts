import { Component, Input } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Attributes, HrTime } from '@opentelemetry/api';
import { TraceGroup } from '../../../../../../services/trace-groups.service';
import { OnInit } from '@angular/core';

@Component({
  selector: 'app-trace-group-list',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './trace-group-list.component.html',
  styleUrls: ['./trace-group-list.component.css']
})
export class TraceGroupListComponent {
  @Input() traceGroups: TraceGroup[] = [];

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
}
