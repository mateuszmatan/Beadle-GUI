import { FormControl, Validators } from '@angular/forms';
import { errorText } from './form-errors';

describe('errorText', () => {
  it('is empty for a valid or missing control', () => {
    expect(errorText(new FormControl('x'))).toBe('');
    expect(errorText(null)).toBe('');
  });

  it('prefers the message the API reported', () => {
    const control = new FormControl('', Validators.required);
    control.setErrors({ required: true, server: 'is already used by PAYHUB' });
    expect(errorText(control)).toBe('is already used by PAYHUB');
  });

  it.each([
    [new FormControl('', Validators.required), 'Required'],
    [new FormControl('a b', Validators.pattern(/^\S+$/)), 'No spaces'],
    [new FormControl('not-an-email', Validators.email), 'Enter an e-mail address'],
    [new FormControl('abcdef', Validators.maxLength(3)), 'At most 3 characters'],
    [new FormControl('x', () => ({ maxLines: { max: 20 } })), 'At most 20 lines'],
    [
      new FormControl('x', () => ({ rule: 'Select at least one scanner' })),
      'Select at least one scanner',
    ],
    [new FormControl('x', () => ({ maxItems: { max: 20 } })), 'At most 20 entries'],
    [
      new FormControl('x', () => ({ columnLength: { max: 2000 } })),
      'Too long: at most 2000 characters in total',
    ],
    [
      new FormControl('x', () => ({ item: { value: 'ENV rd', message: 'Write NAME=value' } })),
      'Write NAME=value: ENV rd',
    ],
    [new FormControl(1.5, () => ({ integer: true })), 'Enter a whole number'],
    [new FormControl(0, Validators.min(1)), 'At least 1'],
    [new FormControl(101, Validators.max(100)), 'At most 100'],
    [new FormControl('x', () => ({ custom: true })), 'Invalid value'],
  ])('describes the validator error of %#', (control, text) => {
    expect(errorText(control, 'No spaces')).toBe(text);
  });
});
