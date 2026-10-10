import { AbstractControl, FormArray, FormControl, FormGroup, ValidatorFn } from '@angular/forms';
import {
  addItem,
  applyProblemsAt,
  filled,
  optional,
  removeItem,
  setEnabled,
  text,
} from '@common/shared/form-controls';
import { ChangeSchedule, ChangeTask, TaskDetails, TaskRequest, TaskState } from './change-api';
import { fits } from './change-model';
import { fromLocal, localInput } from './change-schedule-model';
import { FieldProblem } from '@common/core/models';

export const MAX_TASKS = 50;
export const RELEASE_MANAGEMENT = 'Release Management';
export const NO_PLATFORM = 'None';
export const OPENSHIFT = 'OpenShift';
export const OCP = 'OCP';
export const MODERATE = '3 - Moderate';
export const NOT_YET_REQUESTED = 'Not Yet Requested';
const START_DELAY = 60 * 1000;
const TASKS_PREFIX = 'tasks';

export interface TaskWindow {
  changeNumber: string | null;
  installationStart: string;
  installationEnd: string;
}

export const isRelease = (group: string | null | undefined) =>
  !!group && group.toLowerCase().includes(RELEASE_MANAGEMENT.toLowerCase());

export function taskWindow(
  changeNumber: string | null,
  schedule: Pick<ChangeSchedule, 'installationStart' | 'installationEnd'> | null,
): TaskWindow {
  const local = (time: string | null | undefined) => (time ? localInput(new Date(time)) : '');
  return {
    changeNumber,
    installationStart: local(schedule?.installationStart),
    installationEnd: local(schedule?.installationEnd),
  };
}

const someTasks: ValidatorFn = (tasks) =>
  (tasks as FormArray).length ? null : { rule: 'Add at least one change task' };

const sibling = (control: AbstractControl, path: string) => control.parent?.get(path)?.value;

const startInWindow: ValidatorFn = (control) => {
  const from = fromLocal(sibling(control, 'installationStart'));
  if (!from || !isRelease(sibling(control, 'details.assignmentGroup'))) {
    return null;
  }
  const start = fromLocal(control.value);
  const until = fromLocal(sibling(control, 'installationEnd'));
  if (!start) {
    return { rule: 'Choose when the task starts' };
  }
  if (start.getTime() < from.getTime() + START_DELAY) {
    return { rule: 'At least a minute after the installation start' };
  }
  return until && start > until ? { rule: 'Not after the installation end' } : null;
};

function earliestStart(installationStart: string): string {
  const from = fromLocal(installationStart);
  return from ? localInput(new Date(from.getTime() + START_DELAY)) : '';
}

function detailsForm(details?: Partial<TaskDetails>) {
  return new FormGroup({
    assignmentGroup: text(details?.assignmentGroup, filled, fits(200)),
    assignedTo: text(details?.assignedTo, fits(200)),
    configurationItem: text(details?.configurationItem, fits(200)),
    platform: text(details?.platform ?? NO_PLATFORM),
    application: text(details?.application, fits(100)),
    packages: text(details?.packages, fits(2000)),
    backoutPackages: text(details?.backoutPackages, fits(2000)),
    importance: text(details?.importance ?? MODERATE),
    shortDescription: text(details?.shortDescription, filled, fits(160)),
    description: text(details?.description, filled, fits(4000)),
    additionalComments: text(details?.additionalComments, fits(2000)),
  });
}

function followPlatform(details: ReturnType<typeof detailsForm>): void {
  const { platform, application } = details.controls;
  const sync = () => {
    const openShift = platform.value === OPENSHIFT;
    if (openShift) {
      application.setValue(OCP, { emitEvent: false });
    } else if (application.disabled && application.value === OCP) {
      application.setValue('', { emitEvent: false });
    }
    setEnabled(application, !openShift);
  };
  platform.valueChanges.subscribe(sync);
  sync();
}

export function taskForm(task: Partial<ChangeTask> = {}, window: TaskWindow | null = null) {
  const details = detailsForm(task.details);
  const shown = (value: string | null | undefined) =>
    new FormControl({ value: value ?? '', disabled: true }, { nonNullable: true });
  const form = new FormGroup({
    number: new FormControl<string | null>({ value: task.number ?? null, disabled: true }),
    changeNumber: shown(window?.changeNumber),
    state: new FormControl<TaskState>(task.state ?? 'OPEN', { nonNullable: true }),
    approval: shown(task.approval ?? NOT_YET_REQUESTED),
    installationStart: shown(window?.installationStart),
    installationEnd: shown(window?.installationEnd),
    start: text(task.start ? localInput(new Date(task.start)) : '', startInWindow),
    details,
  });
  followPlatform(details);
  const group = details.controls.assignmentGroup;
  const earliest = () => earliestStart(form.controls.installationStart.value);
  group.valueChanges.subscribe(() => {
    if (isRelease(group.value) && !form.controls.start.value) {
      form.controls.start.setValue(earliest(), { emitEvent: false });
    }
    form.controls.start.updateValueAndValidity({ emitEvent: false });
  });
  if (isRelease(group.value) && !form.controls.start.value) {
    form.controls.start.setValue(earliest());
  }
  if (!window) {
    form.controls.start.disable();
  }
  if (form.controls.state.value === 'CLOSED') {
    form.disable();
  }
  return form;
}

export type TaskForm = ReturnType<typeof taskForm>;

export class TasksForm extends FormArray<TaskForm> {
  constructor(
    tasks: TaskForm[],
    public window: TaskWindow | null,
  ) {
    super(tasks, window ? [] : [someTasks]);
  }
}

export function tasksForm(
  tasks: readonly Partial<ChangeTask>[],
  window: TaskWindow | null = null,
): TasksForm {
  return new TasksForm(
    tasks.map((task) => taskForm(task, window)),
    window,
  );
}

export const drafts = (details: readonly TaskDetails[]): Partial<ChangeTask>[] =>
  details.map((task) => ({ details: task }));

export function setTaskWindow(tasks: TasksForm, window: TaskWindow): void {
  tasks.window = window;
  tasks.controls.forEach((task) => {
    task.patchValue(
      {
        changeNumber: window.changeNumber ?? '',
        installationStart: window.installationStart,
        installationEnd: window.installationEnd,
      },
      { emitEvent: false },
    );
    task.controls.start.updateValueAndValidity({ emitEvent: false });
  });
}

export const isClosed = (task: TaskForm) => task.controls.state.value === 'CLOSED';

export const isReleaseTask = (task: TaskForm) =>
  isRelease(task.controls.details.controls.assignmentGroup.value);

export function canRemove(tasks: TasksForm, index: number): boolean {
  return (tasks.window !== null || tasks.length > 1) && !isClosed(tasks.at(index));
}

export function addTask(tasks: TasksForm): void {
  if (tasks.length < MAX_TASKS) {
    addItem(tasks, taskForm({}, tasks.window));
  }
}

export function removeTask(tasks: TasksForm, index: number): void {
  if (canRemove(tasks, index)) {
    removeItem(tasks, index);
  }
}

function detailsOf(task: TaskForm): TaskDetails {
  const value = task.controls.details.getRawValue();
  const release = isRelease(value.assignmentGroup);
  const kept = (keep: boolean, item: string) => (keep ? optional(item) : null);
  return {
    assignmentGroup: value.assignmentGroup.trim(),
    assignedTo: optional(value.assignedTo),
    configurationItem: optional(value.configurationItem),
    platform: kept(release, value.platform),
    application: kept(release, value.application),
    packages: kept(release, value.packages),
    backoutPackages: kept(release, value.backoutPackages),
    importance: kept(!release, value.importance),
    shortDescription: value.shortDescription.trim(),
    description: value.description.trim(),
    additionalComments: optional(value.additionalComments),
  };
}

function startOf(task: TaskForm): string | null {
  const start = isReleaseTask(task) ? fromLocal(task.controls.start.value) : null;
  return start ? start.toISOString() : null;
}

export const toTaskDetails = (tasks: TasksForm): TaskDetails[] => tasks.controls.map(detailsOf);

export const toTaskRequests = (tasks: TasksForm): TaskRequest[] =>
  tasks.controls.map((task) => ({
    number: task.controls.number.value,
    details: detailsOf(task),
    start: startOf(task),
  }));

export function applyTaskProblems(tasks: TasksForm, problems: FieldProblem[]): FieldProblem[] {
  return applyProblemsAt(tasks, TASKS_PREFIX, problems);
}

export const nestedTaskProblems = (problems: FieldProblem[]): FieldProblem[] =>
  problems.map((problem) => ({
    ...problem,
    field: problem.field.replace(/^(tasks\[\d+])\.(\w+)$/, '$1.details.$2'),
  }));

const APPROVALS: Record<string, string> = {
  [NOT_YET_REQUESTED]: 'approval not requested yet',
  Requested: 'waiting for approval',
  Approved: 'approved',
  Rejected: 'approval rejected',
  'Not Required': 'no approval needed',
};

export function taskStatus(task: Pick<ChangeTask, 'state' | 'approval'>): string {
  const approval = APPROVALS[task.approval] ?? `approval ${task.approval}`;
  switch (task.state) {
    case 'OPEN':
      return `Not done yet; ${approval}.`;
    case 'WORK_IN_PROGRESS':
      return `Being carried out; ${approval}.`;
    case 'CLOSED':
      return 'Done.';
    case 'CANCELED':
      return 'Canceled; no longer part of the change.';
  }
}
