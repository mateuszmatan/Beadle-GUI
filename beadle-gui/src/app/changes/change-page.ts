import { ChangeDetectionStrategy, Component, ResourceRef, input } from '@angular/core';
import { RouterLink } from '@angular/router';
import { errorMessage } from '../core/errors';
import { CHANGES, beadleChange } from '../core/sections';
import { BeadleLoading } from '../ui/loading';
import { ProductionChange } from './change-api';

/** The frame of a page about one change: its breadcrumb, the loading bar and why it could not be loaded. */
@Component({
  selector: 'beadle-change-page',
  imports: [RouterLink, BeadleLoading],
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { class: 'page' },
  template: `
    <nav class="breadcrumb" aria-label="Breadcrumb">
      <a [routerLink]="section.path">{{ section.label }}</a>
      <span class="sep" aria-hidden="true">/</span>
      @let shown = change().hasValue() ? change().value() : undefined;
      @if (crumb()) {
        @if (shown) {
          <a [routerLink]="changeLink(shown.id!)">{{ shown.number }}</a>
          <span class="sep" aria-hidden="true">/</span>
        }
        <span>{{ crumb() }}</span>
      } @else {
        <span>{{ shown?.number ?? 'Change' }}</span>
      }
    </nav>
    @if (change().isLoading()) {
      <beadle-loading />
    }
    @if (change().error(); as error) {
      <div class="banner" role="alert">
        <span>The change could not be loaded: {{ errorMessage(error) }}</span>
        <span class="spacer"></span>
        <button type="button" class="btn btn-link" (click)="change().reload()">Try again</button>
      </div>
    }
    <ng-content />
  `,
  styles: `
    :host {
      display: block;
    }
  `,
})
export class ChangePage {
  readonly change = input.required<ResourceRef<ProductionChange | undefined>>();
  /** The page below the change, linked from the breadcrumb; none on the change page itself. */
  readonly crumb = input<string>();

  protected readonly section = CHANGES;
  protected readonly changeLink = beadleChange;
  protected readonly errorMessage = errorMessage;
}
