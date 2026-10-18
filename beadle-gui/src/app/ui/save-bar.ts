import { ChangeDetectionStrategy, Component, input, output } from '@angular/core';
import { RouterLink } from '@angular/router';
import { BeadleSpinner } from './loading';

/** Why a save was refused: the message, one line per field problem, and whether reloading helps. */
export interface Failure {
  message: string;
  problems: string[];
  reload: boolean;
}

/** The bar at the foot of an editor: the field problems, the outcome or Unsaved changes, Cancel and the submit. */
@Component({
  selector: 'beadle-save-bar',
  imports: [RouterLink, BeadleSpinner],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (failure()?.problems?.length) {
      <div class="banner danger problems-banner" role="alert">
        <ul class="problems">
          @for (problem of failure()!.problems; track problem) {
            <li>{{ problem }}</li>
          }
        </ul>
      </div>
    }
    <div class="save-bar">
      @if (failure(); as failed) {
        <span class="save-error" role="alert">{{ failed.message }}</span>
        @if (failed.reload) {
          <button type="button" class="btn btn-link" (click)="reload.emit()">Reload</button>
        }
      } @else if (dirty()) {
        <span class="muted">Unsaved changes</span>
      }
      <span class="spacer"></span>
      <a class="btn btn-link" [routerLink]="cancelLink()">Cancel</a>
      <button type="submit" class="btn btn-primary" [disabled]="saving()">
        @if (saving()) {
          <beadle-spinner />
        }
        {{ label() }}
      </button>
    </div>
  `,
})
export class SaveBar {
  readonly label = input.required<string>();
  readonly cancelLink = input.required<string | readonly (string | number)[]>();
  readonly failure = input<Failure | null>(null);
  readonly saving = input(false);
  readonly dirty = input(false);
  readonly reload = output();
}
