import { AbstractControl } from '@angular/forms';
import { LookupKind } from '../core/models';
import { Field, FieldOption, area, choice, count, line, mono } from '../shared/fields';
import { Lookup } from '../shared/lookup-dialog';
import { ProductionChange, STATES, approvalOf, labelOf } from './change-api';
import {
  JIRA_KEY_ERROR,
  MAX_PRIVILEGED_USERS,
  TIME_ERROR,
  TemplateForm,
} from './change-template-model';

export type SectionKey =
  'request' | 'jira' | 'approvals' | 'schedule' | 'planning' | 'privileged' | 'risk' | 'secure';

export interface Section {
  key: SectionKey;
  step: string;
  title: string;
  lead: string;
  adminLead?: string;
}

export interface Fact {
  label: string;
  value: string | null;
  placeholder?: string;
  mono?: boolean;
}

export const SECTIONS: readonly Section[] = [
  {
    key: 'request',
    step: 'Request data',
    title: 'Generic request data',
    lead: 'Who asks for the change, who works on it and what it touches.',
  },
  {
    key: 'jira',
    step: 'Jira',
    title: 'Jira',
    lead: 'Give the Jira FixVersion of the release and pick its epics and stories. They write the short description and the description of the change.',
    adminLead: 'The Jira project the epics and stories of a release come from.',
  },
  {
    key: 'approvals',
    step: 'Approval',
    title: 'Approval and Notification',
    lead: 'Who approves the change in ProTech, in this order.',
  },
  {
    key: 'schedule',
    step: 'Schedule',
    title: 'Schedule',
    lead: 'When the change is installed, validated and first used, and whether it brings downtime.',
    adminLead:
      'A new change starts on the release date of its FixVersion while it is ahead, otherwise on the next day, at this time, in local time.',
  },
  {
    key: 'planning',
    step: 'Planning',
    title: 'Planning',
    lead: 'How the change is tested, done, validated, backed out and first used.',
  },
  {
    key: 'privileged',
    step: 'Privileged access',
    title: 'Privileged access',
    lead: `The privileged accounts the change needs, up to ${MAX_PRIVILEGED_USERS}: the person, then the account.`,
  },
  {
    key: 'risk',
    step: 'Risk assessment',
    title: 'Risk assessment',
    lead: 'How far the change reaches and how hard it is. The answers give the risk of the change.',
  },
  {
    key: 'secure',
    step: 'Secure coding',
    title: 'Secure coding',
    lead: 'The secure coding ticket of the release.',
  },
];

export const NUMBER_PENDING = 'Given by ProTech when raised';

export function changeFacts(
  change: Pick<ProductionChange, 'number' | 'state' | 'openedBy'>,
): Fact[] {
  return [
    { label: 'Change number', value: change.number, placeholder: NUMBER_PENDING, mono: true },
    { label: 'Approval', value: approvalOf(change.state) },
    { label: 'Opened By', value: change.openedBy },
    { label: 'State', value: labelOf(STATES, change.state) },
  ];
}

export const OPENER_HINT = 'left empty: the user who opens the change';

const DEPARTMENT_HINT = 'left empty: the department of the product';

const find = (kind: LookupKind, more: Partial<Lookup> = {}) => ({ lookup: { kind, ...more } });

const person = (key: string, label: string, span = 6) => line(key, label, '', span, find('users'));

export const REQUEST_FIELDS: readonly Field[] = [
  person('requestedFor', 'Requested For'),
  person('requestedBy', 'Requested By'),
  line('department', 'Department', '', 6, find('departments')),
  line('assignmentGroup', 'Assignment group', '', 6, find('assignment-groups')),
  choice('category', 'Category', []),
  person('assignedTo', 'Assigned to'),
  choice('type', 'Type', []),
  line('release', 'Release', '', 6, { ...find('releases'), hint: 'left empty: the FixVersion' }),
  line('configurationItem', 'Affected CI', '', 6, {
    ...find('configuration-items', { detail: 'directBusinessService' }),
  }),
  mono('incident', 'Incident', '', 6, { ...find('incidents'), placeholder: 'INC0012345' }),
  line('directBusinessService', 'Direct business service', '', 6, {
    readonly: true,
    hint: 'from the Affected CI',
  }),
  mono('problem', 'Problem', '', 6, { ...find('problems'), placeholder: 'PRB0001234' }),
];

export const RISK = { key: 'risk', label: 'Risk', hint: 'from the risk assessment' };

export const CLOSING_FIELDS: readonly Field[] = [
  line('affectedClients', 'Affected clients', '', 6, find('clients', { append: true })),
  area('usersAffected', 'Users affected', '', 12),
];

export const ADMIN_HINTS: Record<string, string> = {
  requestedFor: OPENER_HINT,
  requestedBy: OPENER_HINT,
  assignedTo: OPENER_HINT,
  department: DEPARTMENT_HINT,
};

export const JIRA_FIELDS: readonly Field[] = [
  mono('jiraProjectKey', 'Jira project', '', 6, { error: JIRA_KEY_ERROR }),
];

export const APPROVAL_FIELDS: readonly Field[] = [
  person('businessApprover', 'Business approver'),
  person('l1Manager', 'L1 approver'),
  person('l2Manager', 'L2 approver'),
];

const YES_NO: FieldOption[] = [
  { value: false, label: 'No' },
  { value: true, label: 'Yes' },
];

export const DOWNTIME_FIELDS: readonly Field[] = [choice('downtime', 'Downtime', YES_NO)];

export const TIMING_FIELDS: readonly Field[] = [
  line('installationStart', 'Installation start', '', 6, { type: 'time', error: TIME_ERROR }),
  count('installationHours', 'Installation hours', '', 6, { min: 1, max: 72 }),
  count('validationHours', 'Validation hours', '', 6, { min: 0, max: 72 }),
];

export const PLANNING_FIELDS: readonly Field[] = [
  area('testSummary', 'Test summary', '', 12),
  area('implementationPlan', 'Implementation plan', '', 12),
  area('validationPlan', 'Validation plan', '', 12),
  area('backoutPlan', 'Backout plan', '', 12),
  area('firstUsePlan', 'First use plan', '', 12),
];

const COUNTS: FieldOption[] = Array.from({ length: MAX_PRIVILEGED_USERS + 1 }, (_, n) => ({
  value: n,
  label: n ? String(n) : 'None',
}));

export const COUNT_FIELDS: readonly Field[] = [
  choice('count', 'How many privileged accounts', COUNTS),
];

export const USER_FIELDS: readonly Field[] = [
  person('user', 'Person', 12),
  mono('account', 'Privileged account', '', 12),
];

export const RISK_FIELDS: readonly Field[] = [
  choice('bbhWorkgroups', 'Number of BBH workgroups impacted', []),
  choice('changeComplexity', 'Complexity of the change', []),
  choice('bbhUsers', 'Number of BBH users impacted', []),
  choice('validationComplexity', 'Complexity of validation', []),
  choice('bbhApplications', 'Number of applications impacted', []),
  choice('backoutTesting', 'Backout testing & duration', []),
  choice('clientsOutsideBbh', 'Number of impacted clients outside BBH', []),
  choice('platformStatus', 'Platform status', []),
  choice('businessImpact', 'Business impact', []),
];

export const SECURE_FIELDS: readonly Field[] = [
  mono('secureCodingTicket', 'Secure coding ticket number', '', 6),
];

const GROUPS = [
  { key: 'approvers', label: 'Approvers' },
  { key: 'timing', label: 'Schedule defaults' },
  { key: 'planning', label: 'Planning' },
  { key: 'privilegedAccess', label: 'Privileged access' },
  { key: 'riskAssessment', label: 'Risk assessment' },
];

const LABELS: [string, readonly Pick<Field, 'key' | 'label'>[]][] = [
  [
    '',
    [
      ...JIRA_FIELDS,
      ...REQUEST_FIELDS,
      RISK,
      ...CLOSING_FIELDS,
      ...DOWNTIME_FIELDS,
      ...SECURE_FIELDS,
      ...GROUPS,
    ],
  ],
  ['approvers.', APPROVAL_FIELDS],
  ['timing.', TIMING_FIELDS],
  ['planning.', PLANNING_FIELDS],
  ['privilegedAccess.', [...COUNT_FIELDS, { key: 'users', label: 'Privileged accounts' }]],
  ['privilegedAccess.users.', USER_FIELDS],
  ['riskAssessment.', RISK_FIELDS],
];

export function templateLabel(path: string): string | null {
  const plain = path.replace(/\[\d+]/g, '');
  for (const [prefix, fields] of LABELS) {
    const found = fields.find((field) => prefix + field.key === plain);
    if (found) {
      return found.label;
    }
  }
  return null;
}

const CONTROLS: Record<SectionKey, readonly string[]> = {
  request: [...REQUEST_FIELDS, ...CLOSING_FIELDS].map((field) => field.key),
  jira: ['jiraProjectKey'],
  approvals: ['approvers'],
  schedule: ['downtime', 'timing'],
  planning: ['planning'],
  privileged: ['privilegedAccess'],
  risk: ['riskAssessment'],
  secure: ['secureCodingTicket'],
};

export function sectionControls(form: TemplateForm, section: SectionKey): AbstractControl[] {
  return CONTROLS[section].map((key) => form.get(key)!);
}
