import { ChangeDetectionStrategy, Component, computed, inject, input } from '@angular/core';
import { LookupKind } from '../core/models';
import {
  Field,
  FieldOption,
  Fields,
  area,
  choice,
  formRevision,
  line,
  mono,
} from '../shared/fields';
import { errorText } from '../shared/form-errors';
import { counted } from '../shared/formatting';
import { TASK_STATES, TaskState, labelOf } from './change-api';
import { ChangeOptionLists } from './change-options';
import {
  MAX_TASKS,
  TaskForm,
  TasksForm,
  addTask,
  canRemove,
  isClosed,
  isReleaseTask,
  removeTask,
  taskStatus,
} from './change-tasks-model';

const find = (kind: LookupKind) => ({ lookup: { kind } });

const FROM_THE_CHANGE: Record<string, string> = {
  changeNumber: 'The CHG number of the change',
  installationStart: 'From the change',
  installationEnd: 'From the change',
  start: 'A minute after the installation start',
};

const NUMBER = mono('number', 'Number', '', 6, { placeholder: 'Given by ProTech when created' });
const CHANGE = mono('changeNumber', 'Change number', '', 6);
const GROUP = line('details.assignmentGroup', 'Assignment group', '', 6, find('assignment-groups'));
const ASSIGNED = line('details.assignedTo', 'Assigned to', '', 6, find('users'));
const CI = line('details.configurationItem', 'Affected CI', '', 6, {
  ...find('configuration-items'),
  hint: 'The application; if left empty: the Affected CI of the change',
});
const APPROVAL = line('approval', 'Approval');
const FROM = line('installationStart', 'Installation start', '', 6, { type: 'datetime-local' });
const UNTIL = line('installationEnd', 'Installation end', '', 6, { type: 'datetime-local' });
const START = line('start', 'Task start', '', 6, {
  type: 'datetime-local',
  hint: 'At least a minute after the installation start, before its end',
});
const APPLICATION = line('details.application', 'Application', '', 6, {
  hint: 'OCP when the platform is OpenShift',
});
const PACKAGES = area('details.packages', 'Packages', '', 12);
const BACKOUT = area('details.backoutPackages', 'Backout packages', '', 12);
const SHORT = area('details.shortDescription', 'Short description', '', 12);
const DESCRIPTION = area('details.description', 'Description', '', 12);
const COMMENTS = area('details.additionalComments', 'Additional comments', '', 12);

const choices = (values: readonly string[] = []): FieldOption[] =>
  values.map((value) => ({ value, label: value }));

export function taskFields(
  release: boolean,
  inChange: boolean,
  platforms: readonly string[] = [],
  importances: readonly string[] = [],
): Field[] {
  const fields = release
    ? [
        NUMBER,
        CHANGE,
        GROUP,
        ASSIGNED,
        CI,
        APPROVAL,
        FROM,
        UNTIL,
        choice('details.platform', 'Platform', choices(platforms)),
        START,
        APPLICATION,
        PACKAGES,
        BACKOUT,
        SHORT,
        DESCRIPTION,
        COMMENTS,
      ]
    : [
        NUMBER,
        CHANGE,
        GROUP,
        ASSIGNED,
        choice('details.importance', 'Importance', choices(importances)),
        CI,
        APPROVAL,
        FROM,
        UNTIL,
        SHORT,
        DESCRIPTION,
        COMMENTS,
      ];
  return inChange
    ? fields
    : fields.map((field) =>
        FROM_THE_CHANGE[field.key]
          ? { ...field, type: 'text', hint: undefined, placeholder: FROM_THE_CHANGE[field.key] }
          : field,
      );
}

export const TASK_LABELS: Record<string, string> = Object.fromEntries(
  [...taskFields(true, true), ...taskFields(false, true)].map((field) => [
    field.key.replace(/^details\./, ''),
    field.label.toLowerCase(),
  ]),
);

@Component({
  selector: 'dso-change-tasks-form',
  imports: [Fields],
  changeDetection: ChangeDetectionStrategy.Eager,
  template: `
    @let list = tasks();
    <ol class="task-list">
      @for (task of list.controls; track task; let i = $index) {
        <li
          class="task-row"
          [class.closed]="isClosed(task)"
          [class.canceled]="task.controls.state.value === 'CANCELED'"
        >
          <div class="task-head">
            <span class="index">{{ i + 1 }}</span>
            <span class="kind">{{
              isReleaseTask(task) ? 'Release Management' : 'Change task'
            }}</span>
            @if (inChange()) {
              <span class="chip neutral">{{ stateLabel(task.controls.state.value) }}</span>
            }
            @if (readonly()) {
              <span class="muted small">{{ status(task) }}</span>
            } @else if (isClosed(task)) {
              <span class="muted small">Closed in ProTech, so it stays as it is</span>
            }
            <span class="spacer"></span>
            @if (!readonly()) {
              <button
                type="button"
                class="btn btn-link danger"
                [attr.aria-label]="'Remove change task ' + (i + 1)"
                [disabled]="!canRemove(list, i)"
                (click)="remove(i)"
              >
                Remove
              </button>
            }
          </div>
          <div class="form-fields">
            <dso-fields [group]="task" [fields]="fieldsOf(task)" />
          </div>
        </li>
      }
    </ol>
    @if (list.errors && (list.touched || list.dirty)) {
      <p class="choice-error" role="alert">{{ errorText(list) }}</p>
    }
    @if (!readonly()) {
      <div class="task-actions">
        <button
          type="button"
          class="btn btn-outline-primary"
          [disabled]="list.length >= maxTasks"
          (click)="add()"
        >
          Add a change task
        </button>
        <span class="muted">{{ counted(list.length, 'change task') }}</span>
      </div>
    }
  `,
  styles: `
    :host {
      display: block;
    }

    .task-list {
      display: flex;
      flex-direction: column;
      gap: 6px;
      margin: 0;
      padding: 0;
      list-style: none;
    }

    .task-row {
      padding: 2px 10px 6px;
      border: 1px solid var(--dso-border);
      background: var(--dso-card);

      &.closed,
      &.canceled {
        background: var(--dso-surface);
      }
    }

    .task-head {
      display: flex;
      flex-wrap: wrap;
      align-items: center;
      gap: 4px 8px;
      min-height: 32px;
      font-size: 12px;
    }

    .kind {
      font-weight: 600;
    }

    .task-actions {
      display: flex;
      align-items: center;
      gap: 10px;
      margin-top: 8px;
      font-size: 12px;
    }
  `,
})
export class ChangeTasksForm {
  readonly tasks = input.required<TasksForm>();
  readonly readonly = input(false);

  private readonly lists = inject(ChangeOptionLists);
  private readonly revision = formRevision(() => this.tasks());

  protected readonly maxTasks = MAX_TASKS;
  protected readonly errorText = errorText;
  protected readonly counted = counted;
  protected readonly canRemove = canRemove;
  protected readonly isClosed = isClosed;
  protected readonly isReleaseTask = isReleaseTask;
  protected readonly inChange = computed(() => this.tasks().window !== null);
  private readonly fieldSets = computed(() => {
    const options = this.lists.options();
    const inChange = this.inChange();
    const readonly = this.readonly();
    const of = (release: boolean) =>
      taskFields(release, inChange, options?.platforms, options?.importances).map((field) =>
        readonly ? { ...field, lookup: undefined } : field,
      );
    return { release: of(true), other: of(false) };
  });

  protected fieldsOf(task: TaskForm): Field[] {
    this.revision();
    const sets = this.fieldSets();
    return isReleaseTask(task) ? sets.release : sets.other;
  }

  protected stateLabel(state: TaskState): string {
    return labelOf(TASK_STATES, state);
  }

  protected status(task: TaskForm): string {
    return taskStatus(task.getRawValue());
  }

  protected add(): void {
    addTask(this.tasks());
  }

  protected remove(index: number): void {
    removeTask(this.tasks(), index);
  }
}
