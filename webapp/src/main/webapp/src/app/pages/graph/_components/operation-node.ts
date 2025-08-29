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
    <div class="operation-node">
      <div class="op-header">
        <div class="title">{{ data()?.label }}</div>
      </div>
      <app-trace-group-list [traceGroups]="(traceGroups$ | async) ?? []"></app-trace-group-list>
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
      }
      .op-header { margin-bottom: 6px; }
      .op-header .title { font-weight: 600; }
      .op-header .svc { color: #666; }

    `,
  ],
  imports: [Vflow, CommonModule, TraceGroupListComponent],
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class OperationNodeComponent extends CustomDynamicNodeComponent<OperationNode> {
  traceGroups$?: Observable<TraceGroup[]>;

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
}