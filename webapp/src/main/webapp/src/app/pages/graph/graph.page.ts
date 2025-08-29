import { Component, OnDestroy, OnInit, signal, viewChild } from '@angular/core';
import * as d3h from 'd3-hierarchy';
import { DynamicNode, Edge, VflowComponent, Vflow, ComponentDynamicNode } from 'ngx-vflow';
import { Subscription } from 'rxjs';
import { ServiceOverview, TraceGroupsService } from '../../services/trace-groups.service';
import { TracingService } from '../../services/tracing.service';
import { ReadableSpan } from '@opentelemetry/sdk-trace-base';
import { ExternalUserNodeComponent } from './_components/external-user-node';
import { ServiceNodeComponent } from './_components/service-node';
import { OperationNodeComponent } from './_components/operation-node';

@Component({
  selector: 'app-graph-page',
  templateUrl: './graph.page.html',
  styleUrls: ['./graph.page.css'],
  standalone: true,
  imports: [Vflow]
})
export class GraphPageComponent implements OnInit, OnDestroy {
  vflow = viewChild.required(VflowComponent);

  protected nodes: DynamicNode[] = [];
  protected edges: Edge[] = [];
  private sub?: Subscription; // overview subscription
  private streamSub?: Subscription; // spans stream subscription
  // Per-node metadata (kind etc.) for styling/logic
  private nodeMeta = new Map<string, { kind: 'app'|'service'|'operation'; angle?: number; radius?: number; parentId?: string }>();
  // Layout params
  private baseRadius = 500; // distance of services from app
  private opRingRadius = 120; // additional radius for operations around service
  // Dedup + optional local history (not rendered here)
  private seenKeys = new Set<string>();
  private spans: ReadableSpan[] = [];
  private maxItems = 200;

  constructor(private traceGroups: TraceGroupsService, private tracing: TracingService) {}

  ngOnInit(): void {
    // Subscribe to live overview of services/operations
    this.sub = this.traceGroups.selectOverview().subscribe((overview: ServiceOverview[]) => {
      this.rebuildGraph(overview);
    });

    // Also stream all spans (empty filters) and feed the groups store
    this.streamSub = this.tracing.streamSpans('', '').subscribe({
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
          // Keep a small local history (optional)
          this.spans = [...fresh, ...this.spans];
          if (this.spans.length > this.maxItems) this.spans.length = this.maxItems;
          // Update the grouped store (service/op derived from span attributes)
          this.traceGroups.upsertSpans(fresh);
        }
      },
      error: () => {
        // Silent for graph; overview will just stop updating
      }
    });
  }

  ngOnDestroy(): void {
    this.sub?.unsubscribe();
  this.streamSub?.unsubscribe();
  }

  private rebuildGraph(overview: ServiceOverview[]) {
    this.nodeMeta.clear();
    const { nodes, edges } = this.buildClusterGraph(overview);
    this.nodes = nodes;
    this.edges = edges;
    // Avoid fitView on a single-point graph (zero-size bbox -> NaN transforms)
    // and ensure positions are finite before fitting.
    const hasFinite = this.nodes.every(n => Number.isFinite(n.point().x) && Number.isFinite(n.point().y));
    if (this.nodes.length > 1 && hasFinite) {
      setTimeout(() => this.vflow().fitView({ duration: 300 }), 50);
    }
  }

  // Build a hierarchical radial layout using d3-hierarchy cluster
  private buildClusterGraph(overview: ServiceOverview[]): { nodes: DynamicNode[]; edges: Edge[] } {
    // Build data tree
    const data: any = {
      id: 'app',
      kind: 'app',
      label: 'App',
      type: ExternalUserNodeComponent,
      children: overview.map((svc) => ({
        id: `svc:${svc.serviceName}`,
        kind: 'service',
        label: svc.serviceName,
        children: (svc.operations || []).map((op) => ({
          id: `op:${svc.serviceName}::${op}`,
          kind: 'operation',
          label: op,
        })),
      })),
    };
    console.log(data);

    const root = d3h.hierarchy(data);
    // Prepare cluster with full angle; radius is max ring we want
    const maxRadius = this.baseRadius + this.opRingRadius;
    const cluster = d3h.cluster<any>().size([0.5 * Math.PI, maxRadius]).separation((a, b) => (a.parent == b.parent ? 1 : 2) / a.depth);
    cluster(root);

  const nodes: ComponentDynamicNode<any>[] = [];
    const edges: Edge[] = [];

    // Convert polar cluster positions to Cartesian using fixed depth rings
    // depth 0: 0 (app), depth 1: baseRadius (services), depth 2+: base+opRing (operations)
    root.descendants().forEach((nd) => {
  let angle = ((nd.x as number * 180 / Math.PI - 90)+90) * Math.PI / 180;
  let r = nd.y as number;
  if (!Number.isFinite(angle)) angle = 0;
  if (!Number.isFinite(r)) r = 0;
  const x = Math.cos(angle) * r;
  const y = Math.sin(angle) * r;

      const id: string = nd.data.id;
      const kind: 'app'|'service'|'operation' = nd.data.kind;
      const label: string = nd.data.label;

      this.nodeMeta.set(id, { kind });
      const payload: any = { label, kind };
      if (kind === 'operation') {
        const parentLabel: string | undefined = (nd.parent && (nd.parent.data as any)?.label) as string | undefined;
        const parsedSvc = id.startsWith('op:') ? id.slice(3).split('::')[0] : undefined;
        payload.serviceName = parentLabel ?? parsedSvc ?? '';
      }
      nodes.push({
        id,
        point: signal({ x, y }),
        type: this.nodeFactory(kind),
        data: signal(payload),
        draggable: signal(false),
      });
    });

    // Build edges from hierarchy links
    root.links().forEach((lnk) => {
      const sid = (lnk.source.data as any).id as string;
      const tid = (lnk.target.data as any).id as string;
      edges.push({ id: `${sid}->${tid}`, source: sid, target: tid , type: 'template'});
    });

    return { nodes, edges };
  }


  private nodeFactory(kind: string) {
    switch (kind) {
      case 'app':
        return ExternalUserNodeComponent;
      case 'service':
        return ServiceNodeComponent;
      case 'operation':
        return OperationNodeComponent;
      default:
        throw new Error(`Unknown node kind: ${kind}`);
    }
  }

}
 
