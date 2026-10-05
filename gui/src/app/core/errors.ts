import { HttpErrorResponse } from '@angular/common/http';
import { FieldProblem } from './models';

const DEFAULT_REASONS = new Set(['OK', 'Unknown Error']);

interface Problem {
  detail?: unknown;
  errors?: unknown;
}

export function errorMessage(error: unknown): string {
  if (error instanceof HttpErrorResponse) {
    if (error.status === 0) {
      return 'The portal API cannot be reached.';
    }
    const detail = problem(error)?.detail;
    if (typeof detail === 'string' && detail.trim()) {
      return detail;
    }
    const reason = error.statusText?.trim();
    return reason && !DEFAULT_REASONS.has(reason)
      ? `${error.status} ${reason}`
      : `The request failed with status ${error.status}`;
  }
  return error instanceof Error ? error.message : String(error);
}

export function fieldProblems(error: unknown): FieldProblem[] {
  if (error instanceof HttpErrorResponse && error.status === 400) {
    const errors = problem(error)?.errors;
    return Array.isArray(errors) ? (errors as FieldProblem[]) : [];
  }
  return [];
}

function problem(error: HttpErrorResponse): Problem | null {
  const body: unknown = error.error;
  if (typeof body === 'string') {
    try {
      return asProblem(JSON.parse(body));
    } catch {
      return null;
    }
  }
  return asProblem(body);
}

function asProblem(body: unknown): Problem | null {
  return body && typeof body === 'object' && !Array.isArray(body) ? (body as Problem) : null;
}
