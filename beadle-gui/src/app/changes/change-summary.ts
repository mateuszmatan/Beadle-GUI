import { ChangeDetectionStrategy, Component, computed, inject, input } from '@angular/core';
import { ChangePlanning, ChangeType, ProductionChange, RiskQuestion } from './change-api';
import { momentText, windowText } from './change-model';
import { ChangeOptionLists } from './change-options';
import {
  Fact,
  RISK_FIELDS,
  SECTIONS,
  SectionKey,
  changeFacts,
  templateLabel,
} from './change-sections';
import { TIME_ZONE_NOTE } from '@common/shared/formatting';

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

type Rows = Omit<Block, 'title'>;

const PLANS: (keyof ChangePlanning)[] = [
  'testSummary',
  'implementationPlan',
  'validationPlan',
  'backoutPlan',
  'firstUsePlan',
];

const RISKS = RISK_FIELDS.map((field) => field.key as RiskQuestion);

const factRow = ({ label, value, placeholder, mono }: Fact): Row => ({
  term: label,
  value: value ?? placeholder ?? null,
  mono: mono && value !== null,
});

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

export type SummaryLayout = 'all' | 'key' | 'details';

type BlockKey = Exclude<SectionKey, 'planning'>;

const LAYOUTS: Record<SummaryLayout, BlockKey[][]> = {
  all: [['request'], ['jira', 'schedule', 'approvals'], ['risk', 'privileged']],
  key: [['schedule'], ['jira'], ['approvals']],
  details: [['request'], ['risk', 'privileged', 'secure']],
};

const KEY_TITLES: Partial<Record<BlockKey, string>> = {
  schedule: 'When it installs',
  jira: 'What it delivers',
  approvals: 'Who approves',
};

const keys = (list: readonly string[]) => (list.length ? list.join(' ') : 'None');

export function summaryColumns(
  change: ProductionChange,
  typeLabel: (type: ChangeType) => string,
  layout: SummaryLayout = 'all',
): Block[][] {
  const t = change.template;
  const s = change.schedule;
  const access = t.privilegedAccess;
  const blocks: Record<BlockKey, Rows> = {
    request: {
      rows: [
        ...changeFacts(change).map(factRow),
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
    jira: {
      rows: [
        { term: 'Product', value: `${change.productName} (${change.productCode})` },
        { term: 'Product department', value: change.departmentName },
        { term: 'FixVersion', value: change.fixVersion, mono: true },
        row('jiraProjectKey', t.jiraProjectKey, true),
        { term: 'Epics', value: keys(change.epicKeys), mono: true },
        { term: 'Stories', value: keys(change.storyKeys), mono: true },
      ],
      note: 'FixVersion is the Jira release the change delivers.',
    },
    schedule: {
      rows: [
        { term: 'Installation', value: windowText(s.installationStart, s.installationEnd) },
        { term: 'Validation', value: windowText(s.validationStart, s.validationEnd) },
        { term: 'First use', value: momentText(s.firstUsage) },
        { term: 'Downtime', value: downtimeText(change) },
      ],
      note: TIME_ZONE_NOTE,
    },
    approvals: {
      rows: [
        row('approvers.businessApprover', t.approvers.businessApprover),
        row('approvers.l1Manager', t.approvers.l1Manager),
        row('approvers.l2Manager', t.approvers.l2Manager),
      ],
      note: 'They approve the change in ProTech in this order.',
    },
    risk: {
      rows: RISKS.map((key) => row(`riskAssessment.${key}`, t.riskAssessment[key])),
    },
    privileged: {
      rows: access.required
        ? access.users.map((user) => ({ term: user.user, value: user.account, mono: true }))
        : [{ term: 'Needed', value: 'No' }],
    },
    secure: {
      rows: [
        row('secureCodingTicket', t.secureCodingTicket, true),
        row('secureCoding.apoNumber', t.secureCoding.apoNumber, true),
        row('secureCoding.bitbucketUrl', t.secureCoding.bitbucketUrl),
        row('secureCoding.artifactLink', t.secureCoding.artifactLink),
        row('secureCoding.qcApplicationLink', t.secureCoding.qcApplicationLink),
      ],
      note: 'The ticket lives in CyberTrack, the Jira project SCP.',
    },
  };
  const title = (key: BlockKey) =>
    (layout === 'key' && KEY_TITLES[key]) || SECTIONS.find((section) => section.key === key)!.title;
  return LAYOUTS[layout].map((column) =>
    column.map((key) => ({ title: title(key), ...blocks[key] })),
  );
}

@Component({
  selector: 'dso-change-summary',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="summary" [style.--columns]="columns().length">
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
      @if (layout() !== 'key') {
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
      }
    </div>
  `,
  styles: `
    :host {
      display: block;
    }

    .summary {
      display: grid;
      grid-template-columns: repeat(var(--columns), minmax(0, 1fr));
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
  readonly layout = input<SummaryLayout>('all');

  private readonly lists = inject(ChangeOptionLists);

  protected readonly columns = computed(() =>
    summaryColumns(this.change(), (type) => this.lists.typeLabel(type), this.layout()),
  );
  protected readonly plans = computed(() =>
    PLANS.map((key) => row(`planning.${key}`, this.change().template.planning[key])),
  );
}
