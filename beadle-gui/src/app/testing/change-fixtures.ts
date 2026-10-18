import {
  ApprovalRole,
  ApprovalState,
  ChangeApproval,
  ChangeOptions,
  ChangeProfile,
  ChangeSchedule,
  ChangeTask,
  ChangeTemplate,
  ChangeUpdate,
  JiraIssue,
  JiraVersion,
  ProductionChange,
  Reminder,
  TaskDetails,
} from '../changes/change-api';
import OPTIONS from '../../testFixtures/resources/com/bbh/itss/beadle/frontend/api/change-options.json';

/** The answer lists of GET /api/changes/options: the file the browser suites answer with too. */
export function changeOptions(): ChangeOptions {
  return structuredClone(OPTIONS) as ChangeOptions;
}

export function changeTemplate(overrides: Partial<ChangeTemplate> = {}): ChangeTemplate {
  return {
    jiraProjectKey: 'CERT',
    requestedFor: null,
    requestedBy: null,
    businessService: null,
    assignmentGroup: 'Technology Architecture',
    category: 'Application',
    assignedTo: null,
    type: 'STANDARD',
    release: null,
    configurationItem: 'CertScanner',
    incident: null,
    directBusinessService: null,
    problem: null,
    risk: null,
    affectedClients: null,
    usersAffected: null,
    approvers: {
      businessApprover: null,
      l1Manager: 'Olivia Bennett',
      l2Manager: 'James Carter',
      supportApprover: 'Jane Smith',
    },
    downtime: false,
    timing: { installationStart: '18:00', installationHours: 2, validationHours: 1 },
    planning: {
      testSummary: 'Pipeline tests passed on QC.',
      implementationPlan: 'Deploy the services.',
      validationPlan: 'Run the smoke tests.',
      backoutPlan: 'Redeploy the previous release.',
      firstUsePlan: 'The business owner confirms the first use.',
    },
    privilegedAccess: { required: false, users: [] },
    riskAssessment: {
      bbhWorkgroups: 'Single',
      changeComplexity: 'Simple',
      bbhUsers: '5-25',
      validationComplexity: 'Simple',
      bbhApplications: 'Single',
      backoutTesting: 'Less than 30 minutes',
      clientsOutsideBbh: 'No clients',
      platformStatus: 'Existing',
      businessImpact: 'Low',
    },
    secureCodingTicket: null,
    secureCoding: {
      apoNumber: 'APO-12345',
      bitbucketUrl: 'https://bitbucket.bbh.com/projects/CERT/repos/cert',
      artifactLink: 'https://jenkins.bbh.com/job/CERT/job/cert-release/',
      qcApplicationLink: 'https://cert.qc.bbh.com',
    },
    ...overrides,
  };
}

export function taskDetails(
  shortDescription: string,
  description = `${shortDescription}.`,
  overrides: Partial<TaskDetails> = {},
): TaskDetails {
  return {
    assignmentGroup: 'Technology Architecture',
    assignedTo: null,
    configurationItem: null,
    platform: null,
    application: null,
    packages: null,
    backoutPackages: null,
    importance: '3 - Moderate',
    shortDescription,
    description,
    additionalComments: null,
    ...overrides,
  };
}

export function releaseDetails(
  shortDescription: string,
  description = `${shortDescription}.`,
  overrides: Partial<TaskDetails> = {},
): TaskDetails {
  return taskDetails(shortDescription, description, {
    assignmentGroup: 'Release Management',
    platform: 'None',
    importance: null,
    ...overrides,
  });
}

export function changeTask(overrides: Partial<ChangeTask> = {}): ChangeTask {
  return {
    number: 'CTASK0020001',
    details: taskDetails('Deploy CertScanner to production', 'Deploy the release of CertScanner.'),
    start: null,
    approval: 'NOT_APPROVED',
    approvers: ['Rebecca Lawson', 'Thomas Ashby'],
    reminder: null,
    state: 'OPEN',
    ...overrides,
  };
}

export function changeApproval(
  role: ApprovalRole,
  approver: string | null,
  state: ApprovalState = 'NOT_APPROVED',
  reminder: Reminder | null = null,
): ChangeApproval {
  return { role, approver, state, reminder };
}

export function changeUpdate(overrides: Partial<ChangeUpdate> = {}): ChangeUpdate {
  return {
    status: 'PENDING',
    requestedAt: '2026-10-08T09:30:00Z',
    businessServiceName: 'Corporate Technology',
    fields: ['schedule.installationStart', 'tasks'],
    message: null,
    checkedAt: '2026-10-08T09:30:05Z',
    ...overrides,
  };
}

export function changeProfile(overrides: Partial<ChangeProfile> = {}): ChangeProfile {
  return {
    productId: 1,
    productName: 'CertScanner',
    version: 2,
    updatedAt: '2026-10-05T12:00:00Z',
    template: changeTemplate(),
    tasks: [
      releaseDetails('Deploy CertScanner to production', 'Deploy the release of CertScanner.'),
      taskDetails('Validate CertScanner in production', 'Run the smoke tests of CertScanner.'),
    ],
    ...overrides,
  };
}

export function jiraVersion(
  name: string,
  released = false,
  releaseDate: string | null = null,
): JiraVersion {
  return { name, released, releaseDate };
}

export function epic(key: string, summary: string, updated = '2026-09-20'): JiraIssue {
  return { key, summary, status: 'Done', epicKey: null, updated };
}

export function story(key: string, summary: string, epicKey: string): JiraIssue {
  return { key, summary, status: 'In Review', epicKey, updated: '2026-09-18' };
}

export function changeSchedule(overrides: Partial<ChangeSchedule> = {}): ChangeSchedule {
  return {
    installationStart: '2026-10-10T06:00:00Z',
    installationEnd: '2026-10-10T08:00:00Z',
    validationStart: '2026-10-10T08:00:00Z',
    validationEnd: '2026-10-10T09:00:00Z',
    firstUsage: '2026-10-12T08:00:00Z',
    downtimeStart: null,
    downtimeEnd: null,
    ...overrides,
  };
}

export function productionChange(overrides: Partial<ProductionChange> = {}): ProductionChange {
  return {
    id: 7,
    number: 'CHG0012345',
    productId: 1,
    productCode: 'CERT',
    productName: 'CertScanner',
    businessServiceId: 3,
    businessServiceName: 'Corporate Technology',
    fixVersion: 'CERT 4.2',
    schedule: changeSchedule(),
    shortDescription: 'CertScanner CERT 4.2: Expiry alerts',
    description: 'Production release of CertScanner (CERT).',
    template: changeTemplate({ release: 'CERT 4.2' }),
    epicKeys: ['CERT-1'],
    storyKeys: ['CERT-2'],
    tasks: [changeTask()],
    url: null,
    state: 'PRIMARY_APPROVAL',
    workflow: [
      { state: 'DRAFT', enteredAt: '2026-10-07T09:00:00Z' },
      { state: 'BUSINESS_APPROVAL', enteredAt: '2026-10-07T09:02:00Z' },
      { state: 'PRIMARY_APPROVAL', enteredAt: '2026-10-07T09:04:00Z' },
    ],
    approvals: [
      changeApproval('BUSINESS', null, 'APPROVED'),
      changeApproval('L1', 'Olivia Bennett', 'REQUESTED'),
      changeApproval('L2', 'James Carter'),
      changeApproval('SUPPORT', 'Jane Smith'),
    ],
    syncedAt: '2026-10-08T09:00:00Z',
    syncProblem: null,
    update: null,
    version: 4,
    createdAt: '2026-10-07T09:00:00Z',
    openedBy: 'Mateusz Matan',
    ...overrides,
  };
}
