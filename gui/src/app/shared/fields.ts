import { ChangeDetectionStrategy, Component, Signal, effect, input, signal } from '@angular/core';
import { AbstractControl, FormControl, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { BuildTool, DeployTarget } from '../core/models';
import { filled } from './form-controls';
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
  type?: string;
  maxLength?: number;
  options?: readonly FieldOption[];
  multiple?: boolean;
  min?: number;
  max?: number;
}

export const TOOL_LABELS: Record<BuildTool, string> = {
  GRADLE: 'Gradle',
  MAVEN: 'Maven',
  FLUTTER: 'Flutter',
};

export const TARGET_LABELS: Record<DeployTarget, string> = {
  VM: 'Virtual machine',
  OPENSHIFT: 'OpenShift',
};

const optionsOf = (labels: Record<string, string>): FieldOption[] =>
  Object.entries(labels).map(([value, label]) => ({ value, label }));

export const GRADLE_MAVEN_FLUTTER = optionsOf(TOOL_LABELS);
export const VM_OPENSHIFT = optionsOf(TARGET_LABELS);

export const tristate = (unset: string, yes: string, no: string): FieldOption[] => [
  { value: null, label: unset },
  { value: true, label: yes },
  { value: false, label: no },
];

type More = Partial<Field>;

const of = (key: string, label: string, code: string, span: number, more: More): Field => ({
  key,
  label,
  span,
  ...(code ? { code } : {}),
  ...more,
});

export const line = (key: string, label: string, code = '', span = 6, more: More = {}) =>
  of(key, label, code, span, more);

export const mono = (key: string, label: string, code = '', span = 6, more: More = {}) =>
  of(key, label, code, span, { mono: true, ...more });

export const area = (key: string, label: string, code = '', span = 6, more: More = {}) =>
  of(key, label, code, span, { kind: 'area', ...more });

export const check = (key: string, label: string, code = '', span = 12, more: More = {}) =>
  of(key, label, code, span, { kind: 'check', ...more });

export const count = (key: string, label: string, code = '', span = 6, more: More = {}) =>
  of(key, label, code, span, { kind: 'number', ...more });

export const choice = (
  key: string,
  label: string,
  options: readonly FieldOption[],
  code = '',
  span = 6,
  more: More = {},
) => of(key, label, code, span, { kind: 'select', options, ...more });

const ESCAPED: Record<string, string> = { '&': '&amp;', '<': '&lt;', '>': '&gt;' };

export function chips(text: string): string {
  return text
    .replace(/[&<>]/g, (character) => ESCAPED[character])
    .replace(/`([^`]+)`/g, '<code>$1</code>');
}

export function defaulted(value: string | null | undefined, missing = ''): More {
  return { placeholder: value ?? '', hint: value ? `left empty: ${value}` : missing };
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
      @let mandatory = required(control);
      @if (field.kind === 'check') {
        <mat-checkbox [class]="span(field, 12)" [formControl]="control">
          <span [innerHTML]="label(field)"></span>
        </mat-checkbox>
      } @else {
        <mat-form-field [class]="span(field, 6)">
          <mat-label>{{ field.label }}</mat-label>
          @switch (field.kind) {
            @case ('area') {
              <textarea
                matInput
                rows="2"
                spellcheck="false"
                [formControl]="control"
                [required]="mandatory"
                [class.mono]="field.mono"
                [placeholder]="field.placeholder ?? ''"
              ></textarea>
            }
            @case ('select') {
              <mat-select
                [formControl]="control"
                [required]="mandatory"
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
                [required]="mandatory"
                [attr.min]="field.min"
                [attr.max]="field.max"
              />
            }
            @default {
              <input
                matInput
                autocomplete="off"
                [formControl]="control"
                [required]="mandatory"
                [class.mono]="field.mono"
                [placeholder]="field.placeholder ?? ''"
                [attr.type]="field.type"
                [attr.maxlength]="field.maxLength"
              />
            }
          }
          @if (field.code || field.hint) {
            <mat-hint [innerHTML]="hint(field)"></mat-hint>
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
  `,
})
export class Fields {
  readonly group = input.required<AbstractControl>();
  readonly fields = input.required<readonly Field[]>();

  protected readonly errorText = errorText;

  protected controlOf(field: Field): FormControl {
    return this.group().get(field.key) as FormControl;
  }

  protected required(control: AbstractControl): boolean {
    return control.hasValidator(filled) || control.hasValidator(Validators.required);
  }

  protected span(field: Field, fallback: number): string {
    const span = field.span ?? fallback;
    return span ? `span-${span}` : '';
  }

  protected label(field: Field): string {
    return field.code
      ? `${chips(field.label)} (${chips('`' + field.code + '`')})`
      : chips(field.label);
  }

  protected hint(field: Field): string {
    const code = field.code ? chips(`\`${field.code}\``) : '';
    const hint = field.hint ? chips(field.hint) : '';
    return code && hint ? `${code} · ${hint}` : code || hint;
  }
}
