import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { ChangeApprovers, ChangeState, ProductionChange, STATES, WorkflowStep } from './change-api';
import { windowText } from './change-model';

export type StageStatus = 'done' | 'current' | 'skipped' | 'later';

export interface Stage {
  state: ChangeState;
  label: string;
  status: StageStatus;
  enteredAt: string | null;
}

export const STATE_MEANINGS: Record<ChangeState, string> = {
  DRAFT: 'Not sent for approval yet',
  BUSINESS_APPROVAL: 'Waiting for the business approver',
  PRIMARY_APPROVAL: 'Waiting for the L1 approver',
  SECONDARY_APPROVAL: 'Waiting for the L2 approver',
  CTASK_APPROVAL: 'Waiting for its change tasks to be approved',
  ESCALATED_APPROVAL: 'Short notice: waiting for an escalated approval',
  IMPLEMENTATION: 'Approved, installs in its window',
  CLOSED: 'Done',
};

const APPROVERS: Partial<Record<ChangeState, [string, keyof ChangeApprovers]>> = {
  BUSINESS_APPROVAL: ['business approver', 'businessApprover'],
  PRIMARY_APPROVAL: ['L1 approver', 'l1Manager'],
  SECONDARY_APPROVAL: ['L2 approver', 'l2Manager'],
};

export function stateNow(
  change: Pick<ProductionChange, 'state' | 'template' | 'schedule'>,
): string {
  const approver = APPROVERS[change.state];
  if (approver) {
    const [role, key] = approver;
    const name = change.template.approvers[key];
    return `Waiting for the ${role}${name ? `, ${name},` : ''} to approve the change in ProTech.`;
  }
  const { installationStart, installationEnd } = change.schedule;
  switch (change.state) {
    case 'DRAFT':
      return 'The change is written but not sent for approval yet. The business approver is asked first.';
    case 'CTASK_APPROVAL':
      return 'Waiting for the change tasks to be approved in ProTech.';
    case 'ESCALATED_APPROVAL':
      return 'The installation is at short notice, so the change waits for an escalated approval in ProTech.';
    case 'IMPLEMENTATION':
      return `Approved. The teams carry out the change tasks in the installation window, ${windowText(installationStart, installationEnd)}.`;
    default:
      return 'Done. The change is closed in ProTech and can no longer be changed.';
  }
}

const ENTERED = new Intl.DateTimeFormat('en-GB', {
  day: 'numeric',
  month: 'short',
  hour: '2-digit',
  minute: '2-digit',
});

export function stages(state: ChangeState, workflow: readonly WorkflowStep[]): Stage[] {
  const current = STATES.findIndex((stage) => stage.value === state);
  return STATES.map(({ value, label }, index) => {
    const step = workflow.filter((entered) => entered.state === value).at(-1);
    const status: StageStatus =
      index === current ? 'current' : index > current ? 'later' : step ? 'done' : 'skipped';
    return {
      state: value,
      label,
      status,
      enteredAt: status === 'later' ? null : (step?.enteredAt ?? null),
    };
  });
}

@Component({
  selector: 'dso-workflow-progress',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <ol class="workflow" aria-label="Workflow in ProTech">
      @for (stage of stages(); track stage.state; let i = $index) {
        <li [class]="stage.status" [attr.aria-current]="stage.status === 'current' ? 'step' : null">
          <span class="number">{{ i + 1 }}</span>
          <span class="text">
            <span class="label">{{ stage.label }}</span>
            <span class="when">
              @if (stage.enteredAt) {
                {{ entered(stage.enteredAt) }}
              } @else if (stage.status === 'skipped') {
                Skipped
              }
            </span>
          </span>
        </li>
      }
    </ol>
  `,
  styles: `
    :host {
      display: block;
    }

    .workflow {
      display: grid;
      grid-template-columns: repeat(8, minmax(0, 1fr));
      gap: 6px;
      margin: 0;
      padding: 0;
      list-style: none;
    }

    li {
      display: flex;
      align-items: flex-start;
      gap: 6px;
      min-width: 0;
      padding: 6px 6px 4px;
      border-top: 3px solid var(--dso-border);
      color: var(--dso-muted);
      font-size: 12px;
    }

    .number {
      display: grid;
      flex-shrink: 0;
      place-items: center;
      width: 20px;
      height: 20px;
      border: 1px solid var(--dso-border);
      background: var(--dso-card);
      font-size: 11px;
      font-weight: 700;
    }

    .text {
      display: flex;
      flex-direction: column;
      min-width: 0;
    }

    .label {
      overflow-wrap: anywhere;
    }

    .when {
      min-height: 16px;
      font-size: 11px;
      white-space: nowrap;
    }

    .done {
      border-top-color: var(--dso-navy-light);
      color: var(--dso-navy);

      .number {
        border-color: var(--dso-navy-light);
        background: var(--dso-info-bg);
      }
    }

    .current {
      border-top-color: var(--dso-navy);
      background: var(--dso-info-bg);
      color: var(--dso-navy);
      font-weight: 600;

      .number {
        border-color: var(--dso-navy);
        background: var(--dso-navy);
        color: #fff;
      }

      .when {
        font-weight: 400;
      }
    }

    .skipped {
      border-top-style: dashed;

      .when {
        font-style: italic;
      }
    }

    @media (max-width: 1000px) {
      .workflow {
        grid-template-columns: repeat(4, minmax(0, 1fr));
      }
    }

    @media (max-width: 480px) {
      .workflow {
        grid-template-columns: repeat(2, minmax(0, 1fr));
      }
    }
  `,
})
export class WorkflowProgress {
  readonly state = input.required<ChangeState>();
  readonly workflow = input.required<readonly WorkflowStep[]>();

  protected readonly stages = computed(() => stages(this.state(), this.workflow()));

  protected entered(time: string): string {
    return ENTERED.format(new Date(time));
  }
}
