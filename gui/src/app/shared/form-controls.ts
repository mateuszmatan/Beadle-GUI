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
export const HOST_NAME = /^[A-Za-z0-9.-]*$/;
export const SHELL_SAFE = /^[A-Za-z0-9._/*+@:=,~-]*$/;
export const SHELL_SAFE_ERROR = 'Letters, digits and . _ / * + @ : = , ~ - only';
export const SHELL_SAFE_URL = /^https?:\/\/[A-Za-z0-9._/+@:=,~%-]+$/;
export const SHELL_SAFE_URL_ERROR =
  'An http or https URL with letters, digits and . _ / + @ : = , ~ % - only';
export const POWERSHELL_PATH = /^[A-Za-z0-9._/\\-]*$/;
export const POWERSHELL_PATH_ERROR = 'Letters, digits and . _ / \\ - only';
export const IMAGE_TAG = /^[A-Za-z0-9._:/@+-]*$/;
export const IMAGE_TAG_ERROR = 'Letters, digits and . _ : / @ + - only';
export const INT_MIN = -2_147_483_648;
export const INT_MAX = 2_147_483_647;

export const text = (value: string | null | undefined = '', ...validators: ValidatorFn[]) =>
  new FormControl(value ?? '', { nonNullable: true, validators });

export const max = (length: number) => Validators.maxLength(length);

export const url = (
  value: string | null | undefined,
  length = 1000,
  ...validators: ValidatorFn[]
) => text(value, ...validators, Validators.pattern(HTTP_URL), max(length));

export const shellSafe = (
  value: string | null | undefined,
  maxLength: number,
  ...validators: ValidatorFn[]
) => text(value, Validators.pattern(SHELL_SAFE), max(maxLength), ...validators);

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

export function lines(value: string | null | undefined, distinct = true): string[] {
  return split(value, /\n/, distinct);
}

export function words(value: string | null | undefined, distinct = true): string[] {
  return split(value, /[\s,]+/, distinct);
}

export function commaItems(value: string | null | undefined): string[] {
  return split(value, /[,\n]/, true);
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

export function requiredRule(message: string): ValidatorFn {
  return (control) => emptyError(control, message);
}

const utf8 = new TextEncoder();

export function fitsColumn(
  parse: (value: string) => string[],
  separator: string,
  max: number,
): ValidatorFn {
  return (control) =>
    utf8.encode(parse(control.value ?? '').join(separator)).length > max
      ? { columnLength: { max } }
      : null;
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
    if (required && !target.hasValidator(filled)) {
      target.addValidators(filled);
    } else if (!required && target.hasValidator(filled)) {
      target.removeValidators(filled);
    }
    target.updateValueAndValidity({ emitEvent: false });
  };
  sources.forEach((source) => source.valueChanges.subscribe(sync));
  sync();
}

function emptyError(control: AbstractControl, message?: string) {
  const error = filled(control);
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

export function passesValidators(control: AbstractControl): boolean {
  if (control.validator?.(control)) {
    return false;
  }
  if (control instanceof FormGroup || control instanceof FormArray) {
    return Object.values(control.controls).every((child: AbstractControl) =>
      passesValidators(child),
    );
  }
  return true;
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

export function moveItem(array: FormArray, from: number, to: number): void {
  const item = array.at(from);
  array.removeAt(from, { emitEvent: false });
  array.insert(to, item);
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

function split(value: string | null | undefined, separator: RegExp, distinct: boolean): string[] {
  const values = (value ?? '')
    .split(separator)
    .map((item) => item.trim())
    .filter((item) => item.length > 0);
  return distinct ? [...new Set(values)] : values;
}
