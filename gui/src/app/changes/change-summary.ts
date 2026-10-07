import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { ChangePlanning, ProductionChange, RiskAssessment, TYPES, labelOf } from './change-api';
import { TIME_ZONE_NOTE, momentText, windowText } from './change-model';
import { templateLabel } from './change-template-form';

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

const RISKS: (keyof RiskAssessment)[] = [
  'bbhWorkgroups',
  'bbhUsers',
  'bbhApplications',
  'clients',
  'clientsOutsideBbh',
  'businessImpact',
  'changeComplexity',
  'validationComplexity',
  'platformStatus',
  'backoutTesting',
];

const row = (path: string, value: string | number | null, mono = false): Row => ({
  term: templateLabel(path) ?? path,
  value: value === null ? null : String(value),
  mono,
});

export function summaryColumns(change: ProductionChange): Block[][] {
  const t = change.template;
  const s = change.schedule;
  const access = t.privilegedAccess;
  return [
    [
      {
        title: 'Change',
        rows: [
          { term: 'Product', value: `${change.productName} (${change.productCode})` },
          { term: 'Department', value: change.departmentName },
          { term: 'FixVersion', value: change.fixVersion, mono: true },
          { term: 'Type', value: `${labelOf(TYPES, t.type)} · ${t.category}` },
          row('assignmentGroup', t.assignmentGroup),
          row('configurationItem', t.configurationItem),
          row('release', t.release),
          row('incident', t.incident, true),
          row('problem', t.problem, true),
          row('affectedClients', t.affectedClients),
          row('jiraProjectKey', t.jiraProjectKey, true),
          { term: 'Jira', value: [...change.epicKeys, ...change.storyKeys].join(' '), mono: true },
        ],
      },
    ],
    [
      {
        title: 'Schedule',
        rows: [
          { term: 'Installation', value: windowText(s.installationStart, s.installationEnd) },
          { term: 'Validation', value: windowText(s.validationStart, s.validationEnd) },
          { term: 'First usage', value: momentText(s.firstUsage) },
          { term: 'Downtime', value: t.downtime ? 'Yes' : 'No' },
        ],
        note: TIME_ZONE_NOTE,
      },
      {
        title: 'Approvers',
        rows: [
          row('approvers.l1Manager', t.approvers.l1Manager),
          row('approvers.l2Manager', t.approvers.l2Manager),
          row('approvers.businessApprover', t.approvers.businessApprover),
        ],
      },
      {
        title: 'Privileged access',
        rows: access.required
          ? access.users.map((user) => ({ term: user.user, value: user.account, mono: true }))
          : [{ term: 'Needed', value: 'No' }],
      },
    ],
    [
      {
        title: 'Risk assessment',
        rows: RISKS.map((key) => row(`riskAssessment.${key}`, t.riskAssessment[key])),
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
      grid-template-columns: repeat(auto-fit, minmax(260px, 1fr));
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
  `,
})
export class ChangeSummary {
  readonly change = input.required<ProductionChange>();

  protected readonly columns = computed(() => summaryColumns(this.change()));
  protected readonly plans = computed(() =>
    PLANS.map((key) => row(`planning.${key}`, this.change().template.planning[key])),
  );
}
