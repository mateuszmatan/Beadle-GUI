import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  Signal,
  effect,
  inject,
  input,
  signal,
} from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { Dialog } from '@angular/cdk/dialog';
import { AbstractControl, FormControl, ReactiveFormsModule, Validators } from '@angular/forms';
import { SvgIconComponent } from 'angular-svg-icon';
import { BuildTool, DeployTarget } from '../core/models';
import { DsoCheckbox } from '../ui/checkbox';
import { FORM_FIELD } from '../ui/form-field';
import { filled } from './form-controls';
import { errorText } from './form-errors';
import { Lookup, openLookup, pickInto } from './lookup-dialog';

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
  step?: number;
  readonly?: boolean;
  lookup?: Lookup;
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
  imports: [ReactiveFormsModule, SvgIconComponent, DsoCheckbox, FORM_FIELD],
  changeDetection: ChangeDetectionStrategy.Eager,
  template: `
    @for (field of fields(); track field.key) {
      @let control = controlOf(field);
      @let mandatory = required(control);
      @if (field.kind === 'check') {
        <dso-checkbox [class]="span(field, 12)" [formControl]="control">
          <span [innerHTML]="label(field)"></span>
        </dso-checkbox>
      } @else {
        <dso-form-field [class]="span(field, 6)" [class.read-only]="field.readonly">
          <dso-label>{{ field.label }}</dso-label>
          @switch (field.kind) {
            @case ('area') {
              <textarea
                dsoInput
                rows="2"
                spellcheck="false"
                [formControl]="control"
                [required]="mandatory"
                [class.mono]="field.mono"
                [placeholder]="field.placeholder ?? ''"
              ></textarea>
            }
            @case ('select') {
              @if (field.multiple) {
                <select dsoInput multiple [formControl]="control" [required]="mandatory">
                  @for (option of field.options; track option.label) {
                    <option [ngValue]="option.value">{{ option.label }}</option>
                  }
                </select>
              } @else {
                <select dsoInput [formControl]="control" [required]="mandatory">
                  @for (option of field.options; track option.label) {
                    <option [ngValue]="option.value">{{ option.label }}</option>
                  }
                </select>
              }
            }
            @case ('number') {
              <input
                dsoInput
                type="number"
                [formControl]="control"
                [required]="mandatory"
                [attr.min]="field.min"
                [attr.max]="field.max"
                [attr.step]="field.step"
              />
            }
            @default {
              <input
                dsoInput
                autocomplete="off"
                [formControl]="control"
                [required]="mandatory"
                [class.mono]="field.mono"
                [placeholder]="field.placeholder ?? ''"
                [type]="field.type ?? 'text'"
                [attr.maxlength]="field.maxLength"
                [readonly]="field.readonly"
              />
            }
          }
          @if (field.lookup; as lookup) {
            <button
              dsoSuffix
              type="button"
              class="btn btn-link btn-icon lookup"
              [attr.aria-label]="'Find ' + field.label"
              (click)="find(field, lookup)"
            >
              <svg-icon name="search" />
            </button>
          }
          @if (field.code || field.hint) {
            <dso-hint><span [innerHTML]="hint(field)"></span></dso-hint>
          }
          <dso-error>{{ errorText(control, field.error) }}</dso-error>
        </dso-form-field>
      }
    }
  `,
  styles: `
    :host {
      display: contents;
    }

    select[multiple] option:checked {
      background: linear-gradient(var(--dso-navy), var(--dso-navy));
      color: #fff;
    }
  `,
})
export class Fields {
  readonly group = input.required<AbstractControl>();
  readonly fields = input.required<readonly Field[]>();

  private readonly dialog = inject(Dialog);
  private readonly destroyRef = inject(DestroyRef);

  protected readonly errorText = errorText;

  protected find(field: Field, lookup: Lookup): void {
    openLookup(this.dialog, { kind: lookup.kind, label: field.label })
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((item) => {
        if (item) {
          pickInto(this.group(), field.key, lookup, item);
        }
      });
  }

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
