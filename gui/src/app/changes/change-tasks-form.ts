import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { Field, Fields, area, line } from '../shared/fields';
import { errorText } from '../shared/form-errors';
import { counted } from '../shared/formatting';
import { TASK_STATES, TaskState, labelOf } from './change-api';
import {
  MAX_TASKS,
  TasksForm,
  addTask,
  canRemove,
  isClosed,
  removeTask,
} from './change-tasks-model';

const TASK: Field[] = [
  line('shortDescription', 'Short description', '', 12, { maxLength: 160 }),
  area('description', 'Description', '', 12),
];

@Component({
  selector: 'dso-change-tasks-form',
  imports: [MatButtonModule, Fields],
  changeDetection: ChangeDetectionStrategy.Eager,
  template: `
    @let list = tasks();
    <ol class="task-list">
      @for (task of list.controls; track task; let i = $index) {
        @let value = task.getRawValue();
        <li class="task-row" [class.closed]="isClosed(task)">
          <div class="task-head">
            <span class="index">{{ i + 1 }}</span>
            @if (numbers()) {
              <span class="mono number" [class.muted]="!value.number">{{
                value.number ?? 'New'
              }}</span>
              <span class="chip neutral">{{ stateLabel(value.state) }}</span>
              @if (isClosed(task)) {
                <span class="muted small">Closed in ProTech, so it stays as it is</span>
              }
            }
            <span class="spacer"></span>
            <button
              mat-button
              type="button"
              class="danger"
              [attr.aria-label]="'Remove change task ' + (i + 1)"
              [disabled]="!canRemove(list, i)"
              (click)="remove(i)"
            >
              Remove
            </button>
          </div>
          <div class="form-fields">
            <dso-fields [group]="task" [fields]="fields" />
          </div>
        </li>
      }
    </ol>
    @if (list.errors && (list.touched || list.dirty)) {
      <p class="choice-error" role="alert">{{ errorText(list) }}</p>
    }
    <div class="task-actions">
      <button mat-stroked-button type="button" [disabled]="list.length >= maxTasks" (click)="add()">
        Add a change task
      </button>
      <span class="muted">{{ counted(list.length, 'change task') }}</span>
    </div>
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
      padding: 2px 10px 0;
      border: 1px solid var(--dso-border);
      background: var(--dso-card);

      &.closed {
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

    .number {
      font-size: 12px;
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
  readonly numbers = input(false);

  protected readonly fields = TASK;
  protected readonly maxTasks = MAX_TASKS;
  protected readonly errorText = errorText;
  protected readonly counted = counted;
  protected readonly canRemove = canRemove;
  protected readonly isClosed = isClosed;

  protected stateLabel(state: TaskState): string {
    return labelOf(TASK_STATES, state);
  }

  protected add(): void {
    addTask(this.tasks());
  }

  protected remove(index: number): void {
    removeTask(this.tasks(), index);
  }
}
