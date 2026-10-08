import { FieldProblem } from '../core/models';
import { MOMENTS } from './change-model';
import { templateLabel } from './change-template-form';
import { TEMPLATE_PREFIX } from './change-template-model';

const REQUEST_LABELS: Record<string, string> = {
  productId: 'Product',
  departmentId: 'Department',
  fixVersion: 'FixVersion',
  epicKeys: 'Epics',
  storyKeys: 'Stories',
  shortDescription: 'Short description',
  description: 'Description',
  schedule: 'Schedule',
  tasks: 'Change tasks',
};

const TASK_PARTS: Record<string, string> = {
  shortDescription: 'short description',
  description: 'description',
};

export function requestLabel(field: string): string | null {
  if (field.startsWith(TEMPLATE_PREFIX)) {
    return templateLabel(field.slice(TEMPLATE_PREFIX.length));
  }
  const task = /^tasks\[(\d+)](?:\.(\w+))?$/.exec(field);
  if (task) {
    const part = TASK_PARTS[task[2]];
    return `Change task ${Number(task[1]) + 1}${part ? `: ${part}` : ''}`;
  }
  const moment = MOMENTS.find(({ key }) => field === `schedule.${key}`);
  return moment?.label ?? REQUEST_LABELS[field] ?? null;
}

export function problemText(problem: FieldProblem): string {
  const label = requestLabel(problem.field);
  return label ? `${label}: ${problem.message}` : problem.message;
}

export function fieldLabels(fields: readonly string[]): string {
  return fields.map((field) => requestLabel(field) ?? field).join(', ');
}
