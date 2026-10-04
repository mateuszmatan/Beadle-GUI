import {
  AbstractControl,
  FormArray,
  FormControl,
  FormGroup,
  ValidatorFn,
  Validators,
} from '@angular/forms';
import { FieldProblem } from '../core/models';

export const HTTP_URL = /^https?:\/\/\S+$/;
export const HOST_NAME = /^[A-Za-z0-9.-]*$/;

export const text = (value: string | null | undefined = '', ...validators: ValidatorFn[]) =>
  new FormControl(value ?? '', { nonNullable: true, validators });

export const flag = (value: boolean | null | undefined, fallback = false) =>
  new FormControl(value ?? fallback, { nonNullable: true });

export const integer = (
  value: number | null | undefined,
  min: number,
  max: number,
  ...validators: ValidatorFn[]
) =>
  new FormControl<number | null>(value ?? null, {
    validators: [wholeNumber, Validators.min(min), Validators.max(max), ...validators],
  });

export function optional(value: string | null | undefined): string | null {
  const trimmed = value?.trim();
  return trimmed ? trimmed : null;
}

export function lines(value: string | null | undefined, distinct = true): string[] {
  return split(value, /\n/, distinct);
}

export function words(value: string | null | undefined, distinct = true): string[] {
  return split(value, /[\s,]+/, distinct);
}

export const joinLines = (values: readonly string[] | null | undefined) =>
  (values ?? []).join('\n');

export const joinWords = (values: readonly string[] | null | undefined, separator = ' ') =>
  (values ?? []).join(separator);

export const wholeNumber: ValidatorFn = (control) =>
  control.value === null || control.value === '' || Number.isInteger(control.value)
    ? null
    : { integer: true };

export function maxLines(max: number, distinct = true): ValidatorFn {
  return (control) => (lines(control.value, distinct).length > max ? { maxLines: { max } } : null);
}

export function maxWords(max: number, distinct = true): ValidatorFn {
  return (control) => (words(control.value, distinct).length > max ? { maxItems: { max } } : null);
}

export function eachItem(
  parse: (value: string) => string[],
  pattern: RegExp,
  message: string,
): ValidatorFn {
  return (control) => {
    const wrong = parse(control.value ?? '').find((item) => !pattern.test(item));
    return wrong === undefined ? null : { item: { value: wrong, message } };
  };
}

export function requiredWhen(
  condition: (siblings: Record<string, unknown>) => boolean,
  message?: string,
): ValidatorFn {
  return (control) => {
    const group = control.parent as FormGroup | null;
    return group && condition(group.getRawValue()) ? emptyError(control, message) : null;
  };
}

export function requireWhile(
  target: AbstractControl,
  condition: () => boolean,
  ...sources: AbstractControl[]
): void {
  const sync = () => {
    const required = condition();
    if (required && !target.hasValidator(Validators.required)) {
      target.addValidators(Validators.required);
    } else if (!required && target.hasValidator(Validators.required)) {
      target.removeValidators(Validators.required);
    }
    target.updateValueAndValidity({ emitEvent: false });
  };
  sources.forEach((source) => source.valueChanges.subscribe(sync));
  sync();
}

function emptyError(control: AbstractControl, message?: string) {
  const error = Validators.required(control);
  return error && message ? { rule: message } : error;
}

export function revalidateOnChange(source: AbstractControl, ...targets: AbstractControl[]): void {
  source.valueChanges.subscribe(() =>
    targets.forEach((target) => target.updateValueAndValidity({ emitEvent: false })),
  );
}

export function setEnabled(control: AbstractControl, enabled: boolean): void {
  if (enabled && control.disabled) {
    control.enable({ emitEvent: false });
  } else if (!enabled && control.enabled) {
    control.disable({ emitEvent: false });
  }
}

export function applyFieldProblems(
  form: AbstractControl,
  problems: FieldProblem[],
): FieldProblem[] {
  const unmatched: FieldProblem[] = [];
  const messages = new Map<AbstractControl, string[]>();
  for (const problem of problems) {
    const control = controlAt(form, problem.field);
    if (control) {
      messages.set(control, [...(messages.get(control) ?? []), problem.message]);
    } else {
      unmatched.push(problem);
    }
  }
  messages.forEach((list, control) => showServerError(control, [...new Set(list)].join('; ')));
  return unmatched;
}

const serverValidators = new WeakMap<AbstractControl, ValidatorFn>();

function showServerError(control: AbstractControl, message: string): void {
  const previous = serverValidators.get(control);
  if (previous) {
    control.removeValidators(previous);
  }
  const scope = control.parent ?? control;
  const own = snapshot(control.getRawValue());
  const around = snapshot(scope.getRawValue());
  const validator: ValidatorFn = (c) =>
    snapshot(c.getRawValue()) === own && snapshot(scope.getRawValue()) === around
      ? { server: message }
      : null;
  serverValidators.set(control, validator);
  control.addValidators(validator);
  control.updateValueAndValidity();
  control.markAsTouched();
}

function snapshot(value: unknown): string {
  return JSON.stringify(value) ?? '';
}

export function revalidateAll(control: AbstractControl): void {
  if (control instanceof FormGroup || control instanceof FormArray) {
    Object.values(control.controls).forEach((child: AbstractControl) => revalidateAll(child));
  }
  control.updateValueAndValidity({ onlySelf: true, emitEvent: false });
}

export function controlAt(form: AbstractControl, field: string): AbstractControl | null {
  const path = field.split('.').flatMap((segment) => {
    const match = /^([\w-]+)((?:\[[^\]]+])+)$/.exec(segment);
    if (!match) {
      return [segment];
    }
    const keys = [...match[2].matchAll(/\[([^\]]+)]/g)].map(([, key]) =>
      /^\d+$/.test(key) ? Number(key) : key,
    );
    return [match[1], ...keys];
  });
  for (let length = path.length; length > 0; length--) {
    const control = form.get(path.slice(0, length));
    if (control) {
      return control;
    }
  }
  return null;
}

function split(value: string | null | undefined, separator: RegExp, distinct: boolean): string[] {
  const values = (value ?? '')
    .split(separator)
    .map((item) => item.trim())
    .filter((item) => item.length > 0);
  return distinct ? [...new Set(values)] : values;
}
