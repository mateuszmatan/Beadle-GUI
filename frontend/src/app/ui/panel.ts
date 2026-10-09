import {
  ChangeDetectionStrategy,
  Component,
  Directive,
  TemplateRef,
  contentChild,
  inject,
  linkedSignal,
  model,
} from '@angular/core';
import { NgTemplateOutlet } from '@angular/common';
import { uniqueId } from './form-field';

@Directive({ selector: 'ng-template[dsoPanelContent]' })
export class DsoPanelContent {
  readonly template = inject(TemplateRef);
}

@Component({
  selector: 'dso-panel',
  exportAs: 'dsoPanel',
  imports: [NgTemplateOutlet],
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { class: 'accordion-item dso-panel', '[class.expanded]': 'expanded()' },
  template: `
    <h3 class="accordion-header">
      <button
        type="button"
        class="accordion-button"
        [class.collapsed]="!expanded()"
        [id]="headerId"
        [attr.aria-expanded]="expanded()"
        [attr.aria-controls]="bodyId"
        (click)="toggle()"
      >
        <ng-content select="dso-panel-header" />
      </button>
    </h3>
    <div
      class="accordion-collapse collapse"
      role="region"
      [class.show]="expanded()"
      [id]="bodyId"
      [attr.aria-labelledby]="headerId"
    >
      <div class="accordion-body">
        <ng-content />
        @if (rendered()) {
          <ng-container *ngTemplateOutlet="lazy()?.template ?? null" />
        }
      </div>
    </div>
  `,
})
export class DsoPanel {
  readonly expanded = model(false);

  protected readonly headerId = uniqueId('dso-panel-header');
  protected readonly bodyId = uniqueId('dso-panel-body');
  protected readonly lazy = contentChild(DsoPanelContent);
  protected readonly rendered = linkedSignal<boolean, boolean>({
    source: this.expanded,
    computation: (expanded, previous) => expanded || !!previous?.value,
  });

  toggle(): void {
    this.expanded.update((expanded) => !expanded);
  }
}

@Component({
  selector: 'dso-panel-header',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: '<ng-content />',
})
export class DsoPanelHeader {}

export const PANEL = [DsoPanel, DsoPanelHeader, DsoPanelContent] as const;
