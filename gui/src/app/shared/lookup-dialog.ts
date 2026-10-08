import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { rxResource, toSignal } from '@angular/core/rxjs-interop';
import { AbstractControl, FormControl, ReactiveFormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import {
  MAT_DIALOG_DATA,
  MatDialog,
  MatDialogModule,
  MatDialogRef,
} from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { Observable, debounceTime, distinctUntilChanged, map } from 'rxjs';
import { LookupsApi } from '../core/api';
import { errorMessage } from '../core/errors';
import { LookupItem, LookupKind } from '../core/models';
import { commaItems } from './form-controls';

export const LOOKUP_DELAY = 250;

export interface Lookup {
  kind: LookupKind;
  append?: boolean;
  detail?: string;
}

export interface LookupDialogData {
  kind: LookupKind;
  label: string;
}

export function appended(list: string | null | undefined, item: string): string {
  return [...new Set([...commaItems(list), item])].join(', ');
}

export function pickInto(
  group: AbstractControl,
  key: string,
  lookup: Lookup,
  item: LookupItem,
): void {
  const control = group.get(key)!;
  control.setValue(lookup.append ? appended(control.value, item.value) : item.value);
  if (lookup.detail) {
    group.get(lookup.detail)?.setValue(item.detail ?? '');
  }
  control.markAsDirty();
  control.markAsTouched();
}

export function openLookup(
  dialog: MatDialog,
  data: LookupDialogData,
): Observable<LookupItem | undefined> {
  return dialog
    .open<LookupDialog, LookupDialogData, LookupItem>(LookupDialog, {
      data,
      width: '440px',
      maxWidth: '92vw',
    })
    .afterClosed();
}

@Component({
  selector: 'dso-lookup-dialog',
  imports: [
    ReactiveFormsModule,
    MatButtonModule,
    MatDialogModule,
    MatFormFieldModule,
    MatInputModule,
    MatProgressBarModule,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <h2 mat-dialog-title>Find {{ data.label }}</h2>
    <mat-dialog-content>
      <mat-form-field class="search">
        <mat-label>Search</mat-label>
        <input
          matInput
          autocomplete="off"
          [formControl]="query"
          (keydown.enter)="pickFirst($event)"
        />
      </mat-form-field>
      @if (results.isLoading()) {
        <mat-progress-bar mode="indeterminate" />
      }
      @if (results.error(); as error) {
        <p class="choice-error" role="alert">{{ errorMessage(error) }}</p>
      }
      @if (results.hasValue()) {
        <ul class="results" [attr.aria-label]="'Found ' + data.label">
          @for (item of results.value(); track $index) {
            <li>
              <button type="button" (click)="pick(item)">
                <span class="value">{{ item.value }}</span>
                @if (item.detail) {
                  <span class="detail">{{ item.detail }}</span>
                }
              </button>
            </li>
          } @empty {
            <li class="empty">
              {{ searched() ? 'Nothing matches "' + searched() + '".' : 'Nothing to choose from.' }}
            </li>
          }
        </ul>
      }
    </mat-dialog-content>
    <mat-dialog-actions align="end">
      <button mat-button mat-dialog-close>Cancel</button>
    </mat-dialog-actions>
  `,
  styles: `
    .search {
      width: 100%;
      margin-top: 6px;
    }

    .results {
      max-height: 320px;
      margin: 4px 0 0;
      padding: 0;
      overflow-y: auto;
      border: 1px solid var(--dso-border);
      list-style: none;

      li + li {
        border-top: 1px solid var(--dso-border);
      }

      button {
        display: flex;
        flex-direction: column;
        width: 100%;
        padding: 5px 10px;
        border: 0;
        background: none;
        color: inherit;
        font: inherit;
        text-align: left;
        cursor: pointer;

        &:hover,
        &:focus-visible {
          background: var(--dso-info-bg);
          outline: none;
        }
      }

      .value {
        font-weight: 600;
        color: var(--dso-navy);
      }

      .detail {
        font-size: 12px;
        color: var(--dso-muted);
      }

      .empty {
        padding: 6px 10px;
        color: var(--dso-muted);
      }
    }
  `,
})
export class LookupDialog {
  protected readonly data = inject<LookupDialogData>(MAT_DIALOG_DATA);
  private readonly ref = inject<MatDialogRef<LookupDialog, LookupItem>>(MatDialogRef);
  private readonly api = inject(LookupsApi);

  protected readonly errorMessage = errorMessage;
  protected readonly query = new FormControl('', { nonNullable: true });
  protected readonly searched = toSignal(
    this.query.valueChanges.pipe(
      debounceTime(LOOKUP_DELAY),
      map((text) => text.trim()),
      distinctUntilChanged(),
    ),
    { initialValue: '' },
  );
  protected readonly results = rxResource({
    params: () => ({ text: this.searched() }),
    stream: ({ params }) => this.api.find(this.data.kind, params.text),
  });

  protected pick(item: LookupItem): void {
    this.ref.close(item);
  }

  protected pickFirst(event: Event): void {
    event.preventDefault();
    const first = this.results.hasValue() ? this.results.value()[0] : undefined;
    if (first) {
      this.pick(first);
    }
  }
}
