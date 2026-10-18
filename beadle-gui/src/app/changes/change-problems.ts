import { HttpErrorResponse } from '@angular/common/http';
import { errorMessage, fieldProblems } from '../core/errors';
import { FieldProblem } from '../core/models';
import { Failure } from '../ui/save-bar';
import { SCHEDULE_LABELS } from './change-schedule-model';
import { SECURE_CODING_FIELDS, templateLabel } from './change-sections';
import { TASK_LABELS } from './change-tasks-form';
import { TEMPLATE_PREFIX } from './change-template-model';

export const ATTENTION = 'Some fields need your attention.';

export const NEEDS_ATTENTION: Failure = { message: ATTENTION, problems: [], reload: false };

const REQUEST_LABELS: Record<string, string> = {
  productId: 'Product',
  businessServiceId: 'Business service',
  fixVersion: 'FixVersion',
  epicKeys: 'Epics',
  storyKeys: 'Stories',
  shortDescription: 'Short description',
  description: 'Description',
  schedule: 'Schedule',
  tasks: 'Change tasks',
  ...Object.fromEntries(SECURE_CODING_FIELDS.map((field) => [field.key, field.label])),
};

export function requestLabel(field: string): string | null {
  if (field.startsWith(TEMPLATE_PREFIX)) {
    return templateLabel(field.slice(TEMPLATE_PREFIX.length));
  }
  const task = /^tasks\[(\d+)](?:\.details)?(?:\.(\w+))?$/.exec(field);
  if (task) {
    const part = TASK_LABELS[task[2]];
    return `Change task ${Number(task[1]) + 1}${part ? `: ${part}` : ''}`;
  }
  const moment = Object.entries(SCHEDULE_LABELS).find(([key]) => field === `schedule.${key}`);
  return moment?.[1] ?? REQUEST_LABELS[field] ?? null;
}

export function problemText(problem: FieldProblem): string {
  const label = requestLabel(problem.field);
  return label ? `${label}: ${problem.message}` : problem.message;
}

export function fieldLabels(fields: readonly string[]): string {
  return fields.map((field) => requestLabel(field) ?? field).join(', ');
}

/** The failure of a refused save; a 409 means someone else saved first, so the page offers to reload. */
export function failureOf(error: unknown, prefix: string): Failure {
  return {
    message: `${prefix}: ${errorMessage(error)}`,
    problems: fieldProblems(error).map(problemText),
    reload: error instanceof HttpErrorResponse && error.status === 409,
  };
}
