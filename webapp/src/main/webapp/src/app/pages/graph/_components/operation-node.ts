import { Component, Output, EventEmitter, ChangeDetectionStrategy } from '@angular/core';
import { Vflow, CustomDynamicNodeComponent } from 'ngx-vflow';
import { CommonModule } from '@angular/common';
import { Observable } from 'rxjs';
import { TraceGroupsService, TraceGroup } from '../../../services/trace-groups.service';
import { TraceGroupListComponent } from '../../services/{serviceId}/_components/live-traces/trace-group-list/trace-group-list.component';

export interface OperationNode {
  label: string;
  serviceName: string;
}

@Component({
  template: `
    <div class="operation-node" [class.collapsed]="!tracesOpen">
      <ng-container *ngIf="traceGroups$ | async as groups">
        <div class="op-header" (click)="toggleTraces()" title="Toggle traces">
          <div class="left">
            <span class="caret" [class.open]="tracesOpen"></span>
            <div class="title">{{ data()?.label }}</div>
          </div>
          <div class="right">
             <span class="muted">{{ groups.length }} trace(s)</span>
            <span class="sep">•</span>
            <span [class.text-danger]="countErrors(groups) > 0">{{ countErrors(groups) }} error(s)</span>
          </div>
        </div>
        <div class="op-body" *ngIf="tracesOpen">
          <app-trace-group-list [traceGroups]="groups"></app-trace-group-list>
        </div>
      </ng-container>
      <handle type="target" position="top" />
    </div>
  `,
  styles: [
    `
      .operation-node {
        width: 920px;
        max-height: 380px;
        border: 1px solid #ccc;
        border-radius: 8px;
        display: block;
        background-color: #fff7f7;
        overflow: auto;
        padding: 6px;
        transition: width 120ms ease-in-out;
      }
      .operation-node.collapsed { width: 420px; }
      .op-header { margin-bottom: 6px; display: flex; align-items: center; justify-content: space-between; cursor: pointer; }
      .op-header .left { display: flex; align-items: center; gap: 6px; }
      .op-header .title { font-weight: 600; }
      .op-header .svc { color: #666; }
      .op-header .muted { color: #777; }
      .op-header .sep { color: #999; margin: 0 6px; }
      .caret { display: inline-block; width: 0; height: 0; vertical-align: middle; border-top: 6px solid transparent; border-bottom: 6px solid transparent; border-left: 6px solid #444; }
      .caret.open { transform: rotate(90deg); }
      .text-danger { color: #b02a37; }

    `,
  ],
  imports: [Vflow, CommonModule, TraceGroupListComponent],
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class OperationNodeComponent extends CustomDynamicNodeComponent<OperationNode> {
  traceGroups$?: Observable<TraceGroup[]>;
  tracesOpen = false;

  constructor(private traceGroupsService: TraceGroupsService) {
    super();
  }

  override ngOnInit(): void {
    super.ngOnInit();
    const svc = this.data()?.serviceName ?? '';
    const op = this.data()?.label ?? '';
    console.log('OperationNodeComponent initialized with service:', svc, 'operation:', op);
    this.traceGroups$ = this.traceGroupsService.selectGroups(svc, op);
    this.traceGroups$.subscribe(groups => {
      console.log('OperationNodeComponent traceGroups:', groups);
    });
  }

  toggleTraces(): void {
    this.tracesOpen = !this.tracesOpen;
  }

  countErrors(groups: TraceGroup[] | null | undefined): number {
    if (!groups || groups.length === 0) return 0;
    return groups.reduce((acc, g) => acc + (g.hasError ? 1 : 0), 0);
  }
}