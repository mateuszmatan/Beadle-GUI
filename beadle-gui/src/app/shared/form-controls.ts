import {
  AbstractControl,
  FormArray,
  FormControl,
  FormGroup,
  ValidatorFn,
  Validators,
} from '@angular/forms';
import { FieldProblem } from '../core/models';

export const HTTP_URL = /^https?:\/\/[^\s$`"\\]+$/;
export const HTTP_URL_ERROR =
  'An http or https URL without spaces, double quotes, backslashes, $ or backticks';

export const text = (value: string | null | undefined = '', ...validators: ValidatorFn[]) =>
  new FormControl(value ?? '', { nonNullable: true, validators });

export const max =
  (length: number): ValidatorFn =>
  (control) => {
    const value = String(control.value ?? '');
    if (value.length > length) {
      return { maxlength: { requiredLength: length, actualLength: value.length } };
    }
    return byteLength(value) > length ? { bytes: { max: length } } : null;
  };

export const url = (
  value: string | null | undefined,
  length = 1000,
  ...validators: ValidatorFn[]
) => text(value, ...validators, Validators.pattern(HTTP_URL), max(length));

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

export const filled: ValidatorFn = (control) =>
  optional(String(control.value ?? '')) ? null : { required: true };

export function commaItems(value: string | null | undefined): string[] {
  const items = (value ?? '')
    .split(/[,\n]/)
    .map((item) => item.trim())
    .filter((item) => item.length > 0);
  return [...new Set(items)];
}

export const wholeNumber: ValidatorFn = (control) =>
  control.value === null || control.value === '' || Number.isInteger(control.value)
    ? null
    : { integer: true };

const utf8 = new TextEncoder();

export const byteLength = (value: string) => utf8.encode(value).length;

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
  scopeOf: (field: string) => AbstractControl | null = () => null,
): FieldProblem[] {
  const unmatched: FieldProblem[] = [];
  const messages = new Map<AbstractControl, { list: string[]; scope: AbstractControl | null }>();
  for (const problem of problems) {
    const control = controlAt(form, problem.field);
    if (control) {
      const entry = messages.get(control) ?? { list: [], scope: null };
      entry.list.push(problem.message);
      entry.scope ??= scopeOf(problem.field);
      messages.set(control, entry);
    } else {
      unmatched.push(problem);
    }
  }
  messages.forEach(({ list, scope }, control) =>
    showServerError(control, [...new Set(list)].join('; '), scope ?? control.parent ?? control),
  );
  return unmatched;
}

export function applyProblemsAt(
  form: AbstractControl,
  prefix: string,
  problems: FieldProblem[],
): FieldProblem[] {
  const own = problems.filter((problem) => problem.field.startsWith(prefix));
  const unmatched = applyFieldProblems(
    form,
    own.map((problem) => ({ ...problem, field: problem.field.slice(prefix.length) })),
  );
  return [
    ...problems.filter((problem) => !own.includes(problem)),
    ...unmatched.map((problem) => ({ ...problem, field: prefix + problem.field })),
  ];
}

const serverValidators = new WeakMap<AbstractControl, ValidatorFn>();

function showServerError(control: AbstractControl, message: string, scope: AbstractControl): void {
  const previous = serverValidators.get(control);
  if (previous) {
    control.removeValidators(previous);
  }
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

export type Sent<T> = { [K in keyof T]: string extends T[K] ? string : Exclude<T[K], null> };

export function sent<T extends object>(value: T): Sent<T> {
  return Object.fromEntries(
    Object.entries(value).map(([key, item]) => [
      key,
      typeof item === 'string' ? optional(item) : item,
    ]),
  ) as Sent<T>;
}

export function addItem<T extends AbstractControl>(
  array: FormArray<T>,
  item: T,
  index = array.length,
): void {
  array.insert(index, item);
  revalidateAll(item);
  array.updateValueAndValidity({ emitEvent: false });
  array.markAsDirty();
}

export function removeItem(array: FormArray, index: number): void {
  array.removeAt(index);
  array.markAsDirty();
}

export function revalidateAll(control: AbstractControl): void {
  if (control instanceof FormGroup || control instanceof FormArray) {
    Object.values(control.controls).forEach((child: AbstractControl) => revalidateAll(child));
  }
  control.updateValueAndValidity({ onlySelf: true, emitEvent: false });
}

export function controlAt(form: AbstractControl, field: string): AbstractControl | null {
  const path = field.split('.').flatMap((segment) => {
    const match = /^([\w-]*)((?:\[[^\]]+])+)$/.exec(segment);
    if (!match) {
      return [segment];
    }
    const keys = [...match[2].matchAll(/\[([^\]]+)]/g)].map(([, key]) =>
      /^\d+$/.test(key) ? Number(key) : key,
    );
    return match[1] ? [match[1], ...keys] : keys;
  });
  for (let length = path.length; length > 0; length--) {
    const control = form.get(path.slice(0, length));
    if (control) {
      return control;
    }
  }
  return null;
}
