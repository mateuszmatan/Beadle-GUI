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
    maxLines: `At most ${errors['maxLines']?.max} lines`,
    maxItems: `At most ${errors['maxItems']?.max} entries`,
    columnLength: `Too long: at most ${errors['columnLength']?.max} characters in total`,
    bytes: `Too long: at most ${errors['bytes']?.max} characters, and accented letters and symbols count as two or three`,
    item: `${errors['item']?.message}: ${errors['item']?.value}`,
    integer: 'Enter a whole number',
    min: `At least ${errors['min']?.min}`,
    max: `At most ${errors['max']?.max}`,
  };
  const reason = Object.keys(messages).find((key) => errors[key] !== undefined);
  return reason ? messages[reason] : 'Invalid value';
}
