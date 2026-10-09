import {
  ChangeDetectionStrategy,
  Component,
  effect,
  inject,
  signal,
  untracked,
} from '@angular/core';
import { rxResource, takeUntilDestroyed, toSignal } from '@angular/core/rxjs-interop';
import { DIALOG_DATA, Dialog, DialogRef } from '@angular/cdk/dialog';
import { AbstractControl, FormControl, ReactiveFormsModule } from '@angular/forms';
import { Observable, debounceTime, distinctUntilChanged, map } from 'rxjs';
import { LookupsApi } from '../core/api';
import { errorMessage } from '../core/errors';
import { LookupItem, LookupKind } from '../core/models';
import { DIALOG } from '../ui/dialog';
import { FORM_FIELD } from '../ui/form-field';
import { DsoLoading } from '../ui/loading';
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
  dialog: Dialog,
  data: LookupDialogData,
): Observable<LookupItem | undefined> {
  return dialog.open<LookupItem, LookupDialogData, LookupDialog>(LookupDialog, {
    data,
    width: '440px',
  }).closed;
}

@Component({
  selector: 'dso-lookup-dialog',
  imports: [ReactiveFormsModule, DIALOG, FORM_FIELD, DsoLoading],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="modal-header">
      <h2 dsoDialogTitle>Find {{ data.label }}</h2>
    </div>
    <div class="modal-body">
      <dso-form-field class="search">
        <dso-label>Search</dso-label>
        <input
          dsoInput
          autocomplete="off"
          [formControl]="query"
          (keydown.enter)="pickFirst($event)"
        />
      </dso-form-field>
      @if (results.isLoading()) {
        <dso-loading />
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
    </div>
    <div class="modal-footer">
      <button type="button" class="btn btn-link" dsoDialogClose>Cancel</button>
    </div>
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
  protected readonly data = inject<LookupDialogData>(DIALOG_DATA);
  private readonly ref = inject<DialogRef<LookupItem, LookupDialog>>(DialogRef);
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
  private readonly entered = signal<string | null>(null);

  constructor() {
    this.query.valueChanges.pipe(takeUntilDestroyed()).subscribe(() => this.entered.set(null));
    effect(() => {
      if (this.entered() === this.searched() && !this.results.isLoading()) {
        untracked(() => {
          this.entered.set(null);
          const first = this.results.hasValue() ? this.results.value()[0] : undefined;
          if (first) {
            this.pick(first);
          }
        });
      }
    });
  }

  protected pick(item: LookupItem): void {
    this.ref.close(item);
  }

  protected pickFirst(event: Event): void {
    event.preventDefault();
    this.entered.set(this.query.value.trim());
  }
}
