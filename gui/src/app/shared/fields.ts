import { ChangeDetectionStrategy, Component, Signal, effect, input, signal } from '@angular/core';
import { AbstractControl, FormControl, ReactiveFormsModule } from '@angular/forms';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { errorText } from './form-errors';

export interface FieldOption {
  value: unknown;
  label: string;
}

export interface Field {
  key: string;
  label: string;
  kind?: 'text' | 'area' | 'number' | 'select' | 'check';
  span?: number;
  code?: string;
  hint?: string;
  placeholder?: string;
  error?: string;
  mono?: boolean;
  options?: readonly FieldOption[];
  multiple?: boolean;
  min?: number;
  max?: number;
}

export const GRADLE_MAVEN_FLUTTER: FieldOption[] = [
  { value: 'GRADLE', label: 'Gradle' },
  { value: 'MAVEN', label: 'Maven' },
  { value: 'FLUTTER', label: 'Flutter' },
];

export const VM_OPENSHIFT: FieldOption[] = [
  { value: 'VM', label: 'Virtual machine' },
  { value: 'OPENSHIFT', label: 'OpenShift' },
];

export function fallback(value: string | number | null | undefined, lead = 'left empty: '): string {
  return value === null || value === undefined || value === '' ? '' : `${lead}${value}`;
}

export function formRevision(form: () => AbstractControl): Signal<number> {
  const revision = signal(0);
  effect((onCleanup) => {
    const subscription = form().events.subscribe(() => revision.update((value) => value + 1));
    onCleanup(() => subscription.unsubscribe());
  });
  return revision;
}

@Component({
  selector: 'dso-fields',
  imports: [
    ReactiveFormsModule,
    MatCheckboxModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
  ],
  changeDetection: ChangeDetectionStrategy.Eager,
  template: `
    @for (field of fields(); track field.key) {
      @let control = controlOf(field);
      @if (field.kind === 'check') {
        <mat-checkbox [class]="'span-' + (field.span ?? 12)" [formControl]="control"
          >{{ field.label }}
          @if (field.code) {
            (<code>{{ field.code }}</code
            >)
          }
        </mat-checkbox>
      } @else {
        <mat-form-field [class]="'span-' + (field.span ?? 6)">
          <mat-label>{{ field.label }}</mat-label>
          @switch (field.kind) {
            @case ('area') {
              <textarea
                matInput
                rows="2"
                spellcheck="false"
                [formControl]="control"
                [class.mono]="field.mono"
                [placeholder]="field.placeholder ?? ''"
              ></textarea>
            }
            @case ('select') {
              <mat-select
                [formControl]="control"
                [multiple]="field.multiple"
                canSelectNullableOptions
              >
                @for (option of field.options; track option.label) {
                  <mat-option [value]="option.value">{{ option.label }}</mat-option>
                }
              </mat-select>
            }
            @case ('number') {
              <input
                matInput
                type="number"
                [formControl]="control"
                [attr.min]="field.min"
                [attr.max]="field.max"
              />
            }
            @default {
              <input
                matInput
                autocomplete="off"
                [formControl]="control"
                [class.mono]="field.mono"
                [placeholder]="field.placeholder ?? ''"
              />
            }
          }
          @if (field.code || field.hint) {
            <mat-hint>
              @if (field.code) {
                <code>{{ field.code }}</code>
              }
              {{ suffix(field) }}</mat-hint
            >
          }
          <mat-error>{{ errorText(control, field.error) }}</mat-error>
        </mat-form-field>
      }
    }
  `,
  styles: `
    :host {
      display: contents;
    }
    mat-checkbox code {
      font-size: 11.5px;
    }
  `,
})
export class Fields {
  readonly group = input.required<AbstractControl>();
  readonly fields = input.required<readonly Field[]>();

  protected readonly errorText = errorText;

  protected controlOf(field: Field): FormControl {
    return this.group().get(field.key) as FormControl;
  }

  protected suffix(field: Field): string {
    return field.hint ? `${field.code ? ' · ' : ''}${field.hint}` : '';
  }
}
