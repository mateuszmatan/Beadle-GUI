import { AbstractControl } from '@angular/forms';

export function errorText(
  control: AbstractControl | null | undefined,
  patternMessage = 'Invalid format',
): string {
  const errors = control?.errors;
  if (!errors) {
    return '';
  }
  const messages: Record<string, string> = {
    server: errors['server'],
    required: 'Required',
    rule: errors['rule'],
    pattern: patternMessage,
    email: 'Enter an e-mail address',
    maxlength: `At most ${errors['maxlength']?.requiredLength} characters`,
    bytes: `Too long: at most ${errors['bytes']?.max} characters, and accented letters and symbols count as two or three`,
    integer: 'Enter a whole number',
    min: `At least ${errors['min']?.min}`,
    max: `At most ${errors['max']?.max}`,
  };
  const reason = Object.keys(messages).find((key) => errors[key] !== undefined);
  return reason ? messages[reason] : 'Invalid value';
}
