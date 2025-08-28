import { Component, Output, EventEmitter, ChangeDetectionStrategy } from '@angular/core';
import { Vflow, CustomDynamicNodeComponent } from 'ngx-vflow';

export interface ExternalUserNode {
    label: string;
}

@Component({
  template: `
        <div class="external-user-node" >
            External User
        <handle type="source" position="bottom" />
    </div>
  `,
  styles: [
    `
      .external-user-node {
            width: 100px;
            height: 50px;
            border: 1px solid gray;
            border-radius: 5px;
            display: flex;
            align-items: center;
            justify-content: center;
            background-color: lightblue;
        }

    `,
  ],
  imports: [Vflow],
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ExternalUserNodeComponent extends CustomDynamicNodeComponent<ExternalUserNode> {

}