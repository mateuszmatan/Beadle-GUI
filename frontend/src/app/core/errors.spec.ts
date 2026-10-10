import { HttpErrorResponse } from '@angular/common/http';
import { RETRY, errorMessage, fieldProblems } from './errors';

describe('errorMessage', () => {
  it('uses the detail of the problem the API returned', () => {
    const error = new HttpErrorResponse({
      status: 409,
      error: { detail: 'Product code CERT is already used by CertScanner' },
    });
    expect(errorMessage(error)).toBe('Product code CERT is already used by CertScanner');
  });

  it('reads the detail of a problem a text endpoint returned as a string', () => {
    const error = new HttpErrorResponse({
      status: 404,
      statusText: '',
      error: JSON.stringify({ title: 'Not Found', detail: 'Pipeline 12 was not found' }),
    });
    expect(errorMessage(error)).toBe('Pipeline 12 was not found');
  });

  it('names the status when the body is no problem and the reason phrase is empty', () => {
    expect(errorMessage(new HttpErrorResponse({ status: 404, error: 'not json' }))).toBe(
      `The portal could not complete the request (error 404). ${RETRY}`,
    );
    expect(
      errorMessage(new HttpErrorResponse({ status: 500, statusText: ' ', error: '[1]' })),
    ).toBe(`The portal could not complete the request (error 500). ${RETRY}`);
    expect(
      errorMessage(new HttpErrorResponse({ status: 500, error: JSON.stringify({ detail: ' ' }) })),
    ).toBe(`The portal could not complete the request (error 500). ${RETRY}`);
  });

  it('explains a request that never reached the API', () => {
    expect(errorMessage(new HttpErrorResponse({ status: 0 }))).toBe(
      'The portal cannot be reached. Check your network connection and try again.',
    );
  });

  it('falls back to the status without a problem detail', () => {
    expect(errorMessage(new HttpErrorResponse({ status: 502, statusText: 'Bad Gateway' }))).toBe(
      `The portal could not complete the request (error 502 Bad Gateway). ${RETRY}`,
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

  it('reads the field problems of a 400 response given as text', () => {
    const errors = [{ field: 'name', message: 'must not be blank' }];
    expect(
      fieldProblems(new HttpErrorResponse({ status: 400, error: JSON.stringify({ errors }) })),
    ).toEqual(errors);
    expect(fieldProblems(new HttpErrorResponse({ status: 400, error: '{broken' }))).toEqual([]);
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
