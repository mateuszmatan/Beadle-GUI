import { AbstractControl } from '@angular/forms';

/** The message to show under a form field: the API's own message first, then the validator's. */
export function errorText(
  control: AbstractControl | null | undefined,
  patternMessage = 'Invalid format',
): string {
  const errors = control?.errors;
  if (!errors) {
    return '';
  }
  if (errors['server']) {
    return errors['server'];
  }
  if (errors['required']) {
    return 'Required';
  }
  if (errors['rule']) {
    return errors['rule'];
  }
  if (errors['pattern']) {
    return patternMessage;
  }
  if (errors['email']) {
    return 'Enter an e-mail address';
  }
  if (errors['maxlength']) {
    return `At most ${errors['maxlength'].requiredLength} characters`;
  }
  if (errors['maxLines']) {
    return `At most ${errors['maxLines'].max} lines`;
  }
  if (errors['maxItems']) {
    return `At most ${errors['maxItems'].max} entries`;
  }
  if (errors['item']) {
    return `${errors['item'].message}: ${errors['item'].value}`;
  }
  if (errors['integer']) {
    return 'Enter a whole number';
  }
  if (errors['min']) {
    return `At least ${errors['min'].min}`;
  }
  if (errors['max']) {
    return `At most ${errors['max'].max}`;
  }
  return 'Invalid value';
}
