import {
  ChangeDetectionStrategy,
  Component,
  computed,
  inject,
  input,
  output,
  signal,
} from '@angular/core';
import { MyDepartment } from '@common/departments/my-department';
import { Notifier } from '@common/core/notifier';
import { errorMessage } from '@common/core/errors';
import { formatRelative } from '@common/shared/formatting';
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
  selector: 'dso-change-approvals',
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
    <div class="table-scroll">
      <table class="approvals-table">
        <thead>
          <tr>
            <th scope="col">Approval</th>
            <th scope="col">Approvers</th>
            <th scope="col">State</th>
            <th scope="col">Last reminder</th>
            <th scope="col"><span class="visually-hidden">Reminder</span></th>
          </tr>
        </thead>
        <tbody>
          @for (row of rows(); track row.key; let first = $first) {
            @if (row.task && (first || !rows()[$index - 1].task)) {
              <tr class="group">
                <th scope="rowgroup" colspan="5">CTASK approvals</th>
              </tr>
            }
            <tr [class.task]="row.task">
              <th scope="row">
                <span class="name" [class.mono]="row.task">{{ row.label }}</span>
                <span class="detail muted">{{ row.detail }}</span>
              </th>
              <td [class.muted]="!row.approvers.length">
                {{ row.approvers.length ? row.approvers.join(', ') : 'Not named in ProTech' }}
              </td>
              <td>
                <span class="chip" [class]="tones[row.state]">{{ stateLabel(row.state) }}</span>
              </td>
              <td class="reminder muted">
                @if (row.reminder; as reminder) {
                  {{ reminderText(reminder) }}
                }
              </td>
              <td class="action">
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
              </td>
            </tr>
          }
        </tbody>
      </table>
    </div>
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

    .approvals-table {
      width: 100%;
      border-collapse: collapse;
      font-size: 12.5px;

      th,
      td {
        padding: 4px 8px 4px 0;
        border-bottom: 1px solid var(--dso-border);
        text-align: left;
        vertical-align: top;
      }

      thead th {
        color: var(--dso-muted);
        font-size: 12px;
        font-weight: 600;
      }

      tbody th {
        font-weight: 600;
      }

      .group th {
        padding-top: 10px;
        color: var(--dso-navy);
        font-size: 12px;
      }
    }

    .detail {
      display: block;
      font-size: 11.5px;
      font-weight: 400;
    }

    .reminder {
      font-size: 12px;
    }

    .action {
      width: 1%;
      text-align: right;
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

  protected readonly tones = TONES;
  protected readonly reminderText = reminderText;
  protected readonly sending = signal(false);
  protected readonly open = computed(() => isOpen(this.change()));
  protected readonly rows = computed(() => approvalRows(this.change()));
  protected readonly taskCount = computed(() => this.rows().filter((row) => row.task).length);
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
