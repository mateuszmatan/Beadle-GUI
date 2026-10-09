import { FormArray, FormControl, FormGroup, ValidatorFn } from '@angular/forms';
import { FieldProblem } from '../core/models';
import { addItem, applyProblemsAt, filled, removeItem, text } from '../shared/form-controls';
import { ChangeTask, EditedTask, TaskState, TaskText } from './change-api';
import { fits } from './change-model';

export const MAX_TASKS = 50;
const TASKS_PREFIX = 'tasks';

const someTasks: ValidatorFn = (tasks) =>
  (tasks as FormArray).length ? null : { rule: 'Add at least one change task' };

export function taskForm(task?: TaskText & Partial<ChangeTask>) {
  const form = new FormGroup({
    number: new FormControl<string | null>(task?.number ?? null),
    state: new FormControl<TaskState>(task?.state ?? 'OPEN', { nonNullable: true }),
    shortDescription: text(task?.shortDescription, filled, fits(160)),
    description: text(task?.description, filled, fits(4000)),
  });
  if (form.controls.state.value === 'CLOSED') {
    form.disable();
  }
  return form;
}

export type TaskForm = ReturnType<typeof taskForm>;

export type TasksForm = FormArray<TaskForm>;

export function tasksForm(tasks: readonly (TaskText & Partial<ChangeTask>)[]): TasksForm {
  return new FormArray(tasks.map(taskForm), [someTasks]);
}

export function isClosed(task: TaskForm): boolean {
  return task.controls.state.value === 'CLOSED';
}

export function canRemove(tasks: TasksForm, index: number): boolean {
  return tasks.length > 1 && !isClosed(tasks.at(index));
}

export function addTask(tasks: TasksForm): void {
  if (tasks.length < MAX_TASKS) {
    addItem(tasks, taskForm());
  }
}

export function removeTask(tasks: TasksForm, index: number): void {
  if (canRemove(tasks, index)) {
    removeItem(tasks, index);
  }
}

export function toTaskTexts(tasks: TasksForm): TaskText[] {
  return tasks.getRawValue().map((task) => ({
    shortDescription: task.shortDescription.trim(),
    description: task.description.trim(),
  }));
}

export function toEditedTasks(tasks: TasksForm): EditedTask[] {
  return tasks.getRawValue().map((task) => ({
    number: task.number,
    shortDescription: task.shortDescription.trim(),
    description: task.description.trim(),
  }));
}

export function applyTaskProblems(tasks: TasksForm, problems: FieldProblem[]): FieldProblem[] {
  return applyProblemsAt(tasks, TASKS_PREFIX, problems);
}
