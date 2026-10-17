import {
  ChangeDetectionStrategy,
  Component,
  computed,
  inject,
  input,
  output,
  signal,
} from '@angular/core';
import { MyDepartment } from '../departments/my-department';
import { Notifier } from '../core/notifier';
import { errorMessage } from '../core/errors';
import { formatRelative } from '../shared/formatting';
import {
  APPROVAL_ROLES,
  APPROVAL_STATES,
  ApprovalRole,
  ApprovalState,
  ChangeState,
  ChangesApi,
  ProductionChange,
  Reminder,
  STATES,
  isOpen,
  labelOf,
} from './change-api';
import { GRID, GridColumn } from '../ui/grid';
import { activeTasks } from './change-model';

export interface ApprovalRow {
  key: string;
  task: boolean;
  label: string;
  detail: string;
  approvers: string[];
  state: ApprovalState;
  reminder: Reminder | null;
  target: { approval?: ApprovalRole; task?: string };
  remindable: boolean;
}

type ReminderScope = Pick<
  ProductionChange,
  'number' | 'departmentId' | 'departmentName' | 'syncProblem'
>;

const STAGES: Record<ApprovalRole, ChangeState> = {
  BUSINESS: 'BUSINESS_APPROVAL',
  L1: 'PRIMARY_APPROVAL',
  L2: 'SECONDARY_APPROVAL',
  SUPPORT: 'SUPPORT_APPROVAL',
};

const TONES: Record<ApprovalState, string> = {
  NOT_APPROVED: 'neutral',
  REQUESTED: 'warning',
  APPROVED: 'success',
};

export function approvalRows(change: Pick<ProductionChange, 'approvals' | 'tasks'>): ApprovalRow[] {
  const approvals = change.approvals.map(({ role, approver, state, reminder }): ApprovalRow => ({
    key: role,
    task: false,
    label: labelOf(APPROVAL_ROLES, role),
    detail: labelOf(STATES, STAGES[role]),
    approvers: approver ? [approver] : [],
    state,
    reminder,
    target: { approval: role },
    remindable: state !== 'APPROVED' && !!approver,
  }));
  const tasks = activeTasks(change.tasks)
    .filter((task) => task.number)
    .map(({ number, details, approvers, approval, reminder }): ApprovalRow => ({
      key: number!,
      task: true,
      label: number!,
      detail: details.assignmentGroup,
      approvers,
      state: approval,
      reminder,
      target: { task: number! },
      remindable: approval !== 'APPROVED' && approvers.length > 0,
    }));
  return [...approvals, ...tasks];
}

export function reminderHint(change: ReminderScope, departmentId: number | null): string | null {
  if (change.departmentId === null) {
    return `No department owns ${change.number}, so nobody can remind its approvers in Beadle`;
  }
  if (departmentId === null) {
    return 'Choose your department in Changes to remind the approvers';
  }
  if (departmentId !== change.departmentId) {
    return `Only ${change.departmentName ?? 'its department'} can remind its approvers`;
  }
  return change.syncProblem ? 'ProTech cannot be read just now, so no reminder can be sent' : null;
}

export function reminderText(reminder: Reminder, now = Date.now()): string {
  return `${reminder.sentTo.join(', ')}, ${formatRelative(reminder.sentAt, now)}`;
}

export function remindedNames(
  change: Pick<ProductionChange, 'approvals' | 'tasks'>,
  target: ApprovalRow['target'] = {},
): string[] {
  const reminders = [
    ...change.approvals.filter(
      ({ role }) => !target.task && (!target.approval || role === target.approval),
    ),
    ...change.tasks.filter(
      ({ number }) => !target.approval && (!target.task || number === target.task),
    ),
  ]
    .map((item) => item.reminder)
    .filter((reminder): reminder is Reminder => reminder !== null);
  const latest = reminders
    .map((reminder) => reminder.sentAt)
    .sort()
    .at(-1);
  return [
    ...new Set(
      reminders
        .filter((reminder) => reminder.sentAt === latest)
        .flatMap((reminder) => reminder.sentTo),
    ),
  ];
}

@Component({
  selector: 'beadle-change-approvals',
  imports: [GRID],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="head">
      <h2>Approvals</h2>
      <span class="spacer"></span>
      @if (open()) {
        <button
          type="button"
          class="btn btn-outline-primary remind-all"
          [disabled]="!!allHint() || sending()"
          [attr.title]="allHint()"
          (click)="remind({})"
        >
          Remind everyone who has not approved
        </button>
      }
    </div>
    <p class="section-help">
      Every approval is Not Approved, Requested or Approved in ProTech. Each change task has its own
      approvers, who depend on its assignment group, and the change goes In Progress only once every
      change task is approved.
    </p>
    @if (open() && hint(); as message) {
      <p class="muted hint">{{ message }}</p>
    }
    @for (group of groups(); track group.title) {
      <h3>{{ group.title }}</h3>
      <beadle-grid [label]="group.title" [rows]="group.rows" [columns]="columns" [rowId]="rowKey">
        <ng-template beadleCell="approval" let-row>
          <span class="approval">
            <span class="name" [class.mono]="row.task">{{ row.label }}</span>
            <span class="muted">{{ row.detail }}</span>
          </span>
        </ng-template>
        <ng-template beadleCell="approvers" let-row>
          <span [class.muted]="!row.approvers.length">{{
            row.approvers.length ? row.approvers.join(', ') : 'Not named in ProTech'
          }}</span>
        </ng-template>
        <ng-template beadleCell="state" let-row>
          <span class="chip" [class]="tone(row.state)">{{ stateLabel(row.state) }}</span>
        </ng-template>
        <ng-template beadleCell="action" let-row>
          @if (open() && row.remindable) {
            <button
              type="button"
              class="btn btn-link remind"
              [disabled]="!!hint() || sending()"
              [attr.title]="hint() ?? 'Send ' + row.approvers.join(', ') + ' a reminder'"
              [attr.aria-label]="'Remind the approvers of ' + row.label"
              (click)="remind(row.target)"
            >
              Remind
            </button>
          }
        </ng-template>
      </beadle-grid>
    }
    @if (open() && !taskCount()) {
      <p class="no-tasks muted">
        No change tasks yet. The change needs at least one approved change task to go In Progress.
      </p>
    }
  `,
  styles: `
    :host {
      display: block;
    }

    .head {
      display: flex;
      align-items: center;
      gap: 8px;
      margin-bottom: 6px;

      h2 {
        margin: 0;
        font-size: 15px;
      }
    }

    .hint {
      margin: 0 0 6px;
      font-size: 12px;
    }

    h3 {
      margin: 10px 0 4px;
      font-size: 13px;
    }

    .approval {
      display: flex;
      flex-direction: column;
      line-height: 1.3;
      font-size: 11.5px;
    }

    .name {
      font-size: 13px;
      font-weight: 600;
    }

    .remind {
      --bs-btn-padding-x: 4px;
      --bs-btn-padding-y: 0;
      --bs-btn-font-size: 11.5px;
      --bs-btn-font-weight: 400;
    }

    .no-tasks {
      margin: 6px 0 0;
      font-size: 12.5px;
    }
  `,
})
export class ChangeApprovals {
  readonly change = input.required<ProductionChange>();
  readonly changed = output<ProductionChange>();

  private readonly api = inject(ChangesApi);
  private readonly notifier = inject(Notifier);
  private readonly myDepartment = inject(MyDepartment);

  protected readonly sending = signal(false);
  protected readonly open = computed(() => isOpen(this.change()));
  protected readonly rowKey = (row: ApprovalRow) => row.key;
  protected readonly columns: GridColumn<ApprovalRow>[] = [
    { key: 'approval', header: 'Approval', value: (row) => row.label, wrap: true, minWidth: 190 },
    {
      key: 'approvers',
      header: 'Approvers',
      value: (row) => row.approvers.join(', '),
      wrap: true,
      flex: 2,
      minWidth: 180,
    },
    { key: 'state', header: 'State', value: (row) => row.state, width: 130 },
    {
      key: 'reminder',
      header: 'Last reminder',
      value: (row) => (row.reminder ? reminderText(row.reminder) : ''),
      wrap: true,
      minWidth: 180,
    },
    { key: 'action', header: '', width: 100 },
  ];
  private readonly rows = computed(() => approvalRows(this.change()));
  protected readonly taskCount = computed(() => this.rows().filter((row) => row.task).length);
  protected readonly groups = computed(() => {
    const rows = this.rows();
    return [
      { title: 'Change approvals', rows: rows.filter((row) => !row.task) },
      { title: 'CTASK approvals', rows: rows.filter((row) => row.task) },
    ].filter((group) => group.rows.length);
  });
  protected readonly hint = computed(() =>
    reminderHint(this.change(), this.myDepartment.departmentId()),
  );
  protected readonly allHint = computed(
    () =>
      this.hint() ??
      (this.rows().some((row) => row.remindable)
        ? null
        : 'Everyone named on the change has approved it'),
  );

  protected tone(state: ApprovalState): string {
    return TONES[state];
  }

  protected stateLabel(state: ApprovalState): string {
    return labelOf(APPROVAL_STATES, state);
  }

  protected remind(target: ApprovalRow['target']): void {
    const departmentId = this.myDepartment.departmentId();
    const change = this.change();
    if (departmentId === null || change.id === null || this.sending()) {
      return;
    }
    this.sending.set(true);
    this.api.remind(change.id, { departmentId, ...target }).subscribe({
      next: (reminded) => {
        this.sending.set(false);
        this.changed.emit(reminded);
        this.notifier.success(`Reminder sent to ${remindedNames(reminded, target).join(', ')}`);
      },
      error: (error) => {
        this.sending.set(false);
        this.notifier.error(`The reminder could not be sent: ${errorMessage(error)}`);
      },
    });
  }
}
