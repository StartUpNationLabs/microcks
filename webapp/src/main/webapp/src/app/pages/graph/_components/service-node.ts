import { Component, Output, EventEmitter, ChangeDetectionStrategy } from '@angular/core';
import { Vflow, CustomDynamicNodeComponent } from 'ngx-vflow';

export interface ServiceNode {
  label: string;
}

@Component({
  template: `
    <div class="service-node" >
      {{ data()?.label }}
        <handle type="source" position="bottom" />
        <handle type="target" position="top" />
    </div>
  `,
  styles: [
    `
      .service-node {
            width: 100px;
            height: 50px;
            border: 1px solid gray;
            border-radius: 5px;
            display: flex;
            align-items: center;
            justify-content: center;
            background-color: lightgreen; 
        }

    `,
  ],
  imports: [Vflow],
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ServiceNodeComponent extends CustomDynamicNodeComponent<ServiceNode> {

}