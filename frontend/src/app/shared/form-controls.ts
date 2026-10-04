import { AbstractControl, FormControl, FormGroup, ValidatorFn, Validators } from '@angular/forms';
import { FieldProblem } from '../core/models';

/**
 * Building blocks of the portal's reactive forms: controls for the kinds of values the API takes, the
 * conversions between their text form and the API's lists, and the validators that repeat the API's rules.
 */

export const HTTP_URL = /^https?:\/\/\S+$/;
export const HOST_NAME = /^[A-Za-z0-9.-]*$/;

/** A text control; null and undefined start it empty. */
export const text = (value: string | null | undefined = '', ...validators: ValidatorFn[]) =>
  new FormControl(value ?? '', { nonNullable: true, validators });

export const flag = (value: boolean | null | undefined, fallback = false) =>
  new FormControl(value ?? fallback, { nonNullable: true });

/** A whole number between min and max, or empty. */
export const integer = (
  value: number | null | undefined,
  min: number,
  max: number,
  ...validators: ValidatorFn[]
) =>
  new FormControl<number | null>(value ?? null, {
    validators: [wholeNumber, Validators.min(min), Validators.max(max), ...validators],
  });

/** The trimmed text, or null when it is blank. */
export function optional(value: string | null | undefined): string | null {
  const trimmed = value?.trim();
  return trimmed ? trimmed : null;
}

/** One value per line, trimmed, without blank lines and, unless told otherwise, without repeats. */
export function lines(value: string | null | undefined, distinct = true): string[] {
  return split(value, /\n/, distinct);
}

/** Values separated by spaces or commas, such as Gradle tasks or Bitbucket reviewers. */
export function words(value: string | null | undefined, distinct = true): string[] {
  return split(value, /[\s,]+/, distinct);
}

export const joinLines = (values: readonly string[] | null | undefined) => (values ?? []).join('\n');

export const joinWords = (values: readonly string[] | null | undefined, separator = ' ') =>
  (values ?? []).join(separator);

export const wholeNumber: ValidatorFn = (control) =>
  control.value === null || control.value === '' || Number.isInteger(control.value)
    ? null
    : { integer: true };

export function maxLines(max: number, distinct = true): ValidatorFn {
  return (control) =>
    lines(control.value, distinct).length > max ? { maxLines: { max } } : null;
}

export function maxWords(max: number, distinct = true): ValidatorFn {
  return (control) =>
    words(control.value, distinct).length > max ? { maxItems: { max } } : null;
}

/** Every value of a list entered as text must match the pattern; the error names the first that does not. */
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

/**
 * Required while the sibling values meet the condition. The siblings are read from their controls, since the
 * group's value is updated only after a changed control has notified its listeners. With a message the error
 * is that message instead of a plain "Required".
 */
export function requiredWhen(
  condition: (siblings: Record<string, unknown>) => boolean,
  message?: string,
): ValidatorFn {
  return (control) => {
    const group = control.parent as FormGroup | null;
    return group && condition(group.getRawValue()) ? emptyError(control, message) : null;
  };
}

/**
 * Makes the target required while the condition holds, checked again whenever a source changes. The target
 * then carries {@link Validators.required} itself, so its form field shows the required marker.
 */
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

/**
 * Checks the targets again whenever the source changes. The targets are updated without events, so a source
 * that contains a target does not notify itself again; the source's own change notifies the form afterwards.
 */
export function revalidateOnChange(source: AbstractControl, ...targets: AbstractControl[]): void {
  source.valueChanges.subscribe(() =>
    targets.forEach((target) => target.updateValueAndValidity({ emitEvent: false })),
  );
}

/** Enables or disables a control, which takes it out of the form's validity, without events. */
export function setEnabled(control: AbstractControl, enabled: boolean): void {
  if (enabled && control.disabled) {
    control.enable({ emitEvent: false });
  } else if (!enabled && control.enabled) {
    control.disable({ emitEvent: false });
  }
}

/**
 * Shows each field problem the API reported on its control, for example {@code services[2].build.javaPath};
 * returns the problems that match no control so they can be listed separately.
 */
export function applyFieldProblems(
  form: AbstractControl,
  problems: FieldProblem[],
): FieldProblem[] {
  const unmatched: FieldProblem[] = [];
  for (const problem of problems) {
    const control = controlAt(form, problem.field);
    if (control) {
      control.setErrors({ ...control.errors, server: problem.message });
      control.markAsTouched();
    } else {
      unmatched.push(problem);
    }
  }
  return unmatched;
}

/**
 * The control a field path names, or the nearest one above it, for example a list element's list. Paths index
 * lists by number and maps by key, as in {@code services[0].testJobs[1].job} or {@code limits[SAST].maxHigh}.
 */
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
