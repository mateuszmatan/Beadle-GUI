import { HttpErrorResponse } from '@angular/common/http';
import { errorMessage, fieldProblems } from './errors';

describe('errorMessage', () => {
  it('uses the detail of the problem the API returned', () => {
    const error = new HttpErrorResponse({
      status: 409,
      error: { detail: 'Product code CERT is already used by CertScanner' },
    });
    expect(errorMessage(error)).toBe('Product code CERT is already used by CertScanner');
  });

  it('explains a request that never reached the API', () => {
    expect(errorMessage(new HttpErrorResponse({ status: 0 }))).toBe(
      'The portal API cannot be reached.',
    );
  });

  it('falls back to the status without a problem detail', () => {
    expect(errorMessage(new HttpErrorResponse({ status: 502, statusText: 'Bad Gateway' }))).toBe(
      '502 Bad Gateway',
    );
  });

  it('shows other errors as their message', () => {
    expect(errorMessage(new Error('boom'))).toBe('boom');
    expect(errorMessage('plain')).toBe('plain');
  });
});

describe('fieldProblems', () => {
  it('lists the field problems of a 400 response', () => {
    const errors = [{ field: 'services[0].name', message: 'is already used' }];
    expect(fieldProblems(new HttpErrorResponse({ status: 400, error: { errors } }))).toEqual(
      errors,
    );
  });

  it('is empty for other responses', () => {
    expect(fieldProblems(new HttpErrorResponse({ status: 409, error: { errors: [] } }))).toEqual(
      [],
    );
    expect(
      fieldProblems(new HttpErrorResponse({ status: 400, error: { detail: 'Malformed request' } })),
    ).toEqual([]);
    expect(fieldProblems(new Error('boom'))).toEqual([]);
  });
});
