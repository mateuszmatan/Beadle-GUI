import { ChangeDetectionStrategy, Component, computed, inject, input } from '@angular/core';
import {
  ChangePlanning,
  ChangeType,
  ProductionChange,
  RiskQuestion,
  STATES,
  approvalOf,
  labelOf,
} from './change-api';
import { TIME_ZONE_NOTE, momentText, windowText } from './change-model';
import { ChangeOptionLists } from './change-options';
import { NUMBER_PENDING, RISK_FIELDS, templateLabel } from './change-sections';

interface Row {
  term: string;
  value: string | null;
  mono?: boolean;
}

interface Block {
  title: string;
  rows: Row[];
  note?: string;
}

const PLANS: (keyof ChangePlanning)[] = [
  'testSummary',
  'implementationPlan',
  'validationPlan',
  'backoutPlan',
  'firstUsePlan',
];

const RISKS = RISK_FIELDS.map((field) => field.key as RiskQuestion);

const row = (path: string, value: string | null, mono = false): Row => ({
  term: templateLabel(path) ?? path,
  value,
  mono,
});

function downtimeText(change: ProductionChange): string {
  const { downtimeStart, downtimeEnd } = change.schedule;
  if (!change.template.downtime) {
    return 'No';
  }
  return downtimeStart && downtimeEnd ? windowText(downtimeStart, downtimeEnd) : 'Yes';
}

export function summaryColumns(
  change: ProductionChange,
  typeLabel: (type: ChangeType) => string,
): Block[][] {
  const t = change.template;
  const s = change.schedule;
  const access = t.privilegedAccess;
  return [
    [
      {
        title: 'Generic request data',
        rows: [
          { term: 'Change number', value: change.number ?? NUMBER_PENDING, mono: !!change.number },
          { term: 'Approval', value: approvalOf(change.state) },
          { term: 'Opened By', value: change.openedBy },
          { term: 'State', value: labelOf(STATES, change.state) },
          row('requestedFor', t.requestedFor),
          row('requestedBy', t.requestedBy),
          row('department', t.department),
          row('assignmentGroup', t.assignmentGroup),
          row('category', t.category),
          row('assignedTo', t.assignedTo),
          { term: 'Type', value: typeLabel(t.type) },
          row('release', t.release),
          row('configurationItem', t.configurationItem),
          row('incident', t.incident, true),
          row('directBusinessService', t.directBusinessService),
          row('problem', t.problem, true),
          row('risk', t.risk),
          row('affectedClients', t.affectedClients),
          row('usersAffected', t.usersAffected),
        ],
      },
    ],
    [
      {
        title: 'Jira',
        rows: [
          { term: 'Product', value: `${change.productName} (${change.productCode})` },
          { term: 'Product department', value: change.departmentName },
          { term: 'FixVersion', value: change.fixVersion, mono: true },
          row('jiraProjectKey', t.jiraProjectKey, true),
          { term: 'Jira', value: [...change.epicKeys, ...change.storyKeys].join(' '), mono: true },
        ],
      },
      {
        title: 'Schedule',
        rows: [
          { term: 'Installation', value: windowText(s.installationStart, s.installationEnd) },
          { term: 'Validation', value: windowText(s.validationStart, s.validationEnd) },
          { term: 'First use', value: momentText(s.firstUsage) },
          { term: 'Downtime', value: downtimeText(change) },
        ],
        note: TIME_ZONE_NOTE,
      },
      {
        title: 'Approval and Notification',
        rows: [
          row('approvers.businessApprover', t.approvers.businessApprover),
          row('approvers.l1Manager', t.approvers.l1Manager),
          row('approvers.l2Manager', t.approvers.l2Manager),
        ],
      },
    ],
    [
      {
        title: 'Risk assessment',
        rows: RISKS.map((key) => row(`riskAssessment.${key}`, t.riskAssessment[key])),
      },
      {
        title: 'Privileged access',
        rows: access.required
          ? access.users.map((user) => ({ term: user.user, value: user.account, mono: true }))
          : [{ term: 'Needed', value: 'No' }],
      },
      {
        title: 'Secure coding',
        rows: [row('secureCodingTicket', t.secureCodingTicket, true)],
      },
    ],
  ];
}

@Component({
  selector: 'dso-change-summary',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="summary">
      @for (column of columns(); track $index) {
        <div class="column">
          @for (block of column; track block.title) {
            <section>
              <h3>{{ block.title }}</h3>
              <dl class="rows">
                @for (row of block.rows; track $index) {
                  <dt>{{ row.term }}</dt>
                  <dd
                    [class.mono]="row.mono && row.value !== null"
                    [class.muted]="row.value === null"
                    [textContent]="row.value ?? 'not set'"
                  ></dd>
                }
              </dl>
              @if (block.note) {
                <p class="note">{{ block.note }}</p>
              }
            </section>
          }
        </div>
      }
      <section class="wide">
        <h3>Planning</h3>
        <div class="plans">
          @for (plan of plans(); track plan.term) {
            <div>
              <h4>{{ plan.term }}</h4>
              <p class="text">{{ plan.value }}</p>
            </div>
          }
        </div>
      </section>
    </div>
  `,
  styles: `
    :host {
      display: block;
    }

    .summary {
      display: grid;
      grid-template-columns: repeat(3, minmax(0, 1fr));
      gap: 12px 24px;
    }

    .column {
      display: flex;
      flex-direction: column;
      gap: 12px;
    }

    .wide {
      grid-column: 1 / -1;
    }

    h3 {
      margin: 0 0 4px;
      font-size: 14px;
    }

    dd {
      overflow-wrap: anywhere;
      white-space: pre-wrap;
    }

    .plans {
      display: grid;
      grid-template-columns: repeat(auto-fill, minmax(260px, 1fr));
      gap: 8px 24px;

      h4 {
        margin: 0 0 2px;
        font-size: 12.5px;
      }
    }

    .text {
      margin: 0;
      font-size: 12.5px;
      white-space: pre-wrap;
    }

    @media (max-width: 1000px) {
      .summary {
        grid-template-columns: minmax(0, 1fr);
      }
    }
  `,
})
export class ChangeSummary {
  readonly change = input.required<ProductionChange>();

  private readonly lists = inject(ChangeOptionLists);

  protected readonly columns = computed(() =>
    summaryColumns(this.change(), (type) => this.lists.typeLabel(type)),
  );
  protected readonly plans = computed(() =>
    PLANS.map((key) => row(`planning.${key}`, this.change().template.planning[key])),
  );
}
