import { HttpErrorResponse } from '@angular/common/http';
import { FieldProblem } from './models';

export function errorMessage(error: unknown): string {
  if (error instanceof HttpErrorResponse) {
    if (error.status === 0) {
      return 'The portal API cannot be reached.';
    }
    const body = error.error;
    if (body && typeof body === 'object' && typeof body.detail === 'string') {
      return body.detail;
    }
    return `${error.status} ${error.statusText}`;
  }
  return error instanceof Error ? error.message : String(error);
}

export function fieldProblems(error: unknown): FieldProblem[] {
  if (
    error instanceof HttpErrorResponse &&
    error.status === 400 &&
    Array.isArray(error.error?.errors)
  ) {
    return error.error.errors as FieldProblem[];
  }
  return [];
}
