import { HttpErrorResponse } from '@angular/common/http';
import { FieldProblem } from './models';

const DEFAULT_REASONS = new Set(['OK', 'Unknown Error']);

export const RETRY = 'Try again in a moment; if it keeps failing, tell the portal administrator.';

export const UNREACHABLE =
  'The portal cannot be reached. Check your network connection and try again.';

interface Problem {
  detail?: unknown;
  errors?: unknown;
}

export function errorMessage(error: unknown): string {
  if (error instanceof HttpErrorResponse) {
    if (error.status === 0) {
      return UNREACHABLE;
    }
    const detail = problem(error)?.detail;
    if (typeof detail === 'string' && detail.trim()) {
      return detail;
    }
    const reason = error.statusText?.trim();
    const code =
      reason && !DEFAULT_REASONS.has(reason) ? `${error.status} ${reason}` : error.status;
    return `The portal could not complete the request (error ${code}). ${RETRY}`;
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
