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
  return 'Invalid value';
}
