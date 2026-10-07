import {
  ChangeProfile,
  ChangeSchedule,
  ChangeTemplate,
  JiraIssue,
  JiraVersion,
  ProductionChange,
} from '../changes/change-api';

export function changeTemplate(overrides: Partial<ChangeTemplate> = {}): ChangeTemplate {
  return {
    jiraProjectKey: 'CERT',
    assignmentGroup: 'Technology Architecture',
    category: 'Software',
    type: 'NORMAL',
    configurationItem: 'CertScanner',
    release: null,
    incident: null,
    problem: null,
    affectedClients: null,
    description: 'Watches TLS certificates.',
    approvers: { l1Manager: 'Olivia Bennett', l2Manager: 'James Carter', businessApprover: null },
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
      bbhWorkgroups: 1,
      bbhUsers: 10,
      bbhApplications: 1,
      clients: 0,
      clientsOutsideBbh: 0,
      businessImpact: 'Low',
      changeComplexity: 'Low',
      validationComplexity: 'Low',
      backoutTesting: 'Tested on QC, about 15 minutes',
      platformStatus: 'Existing platform',
    },
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
    departmentName: 'Corporate Technology',
    fixVersion: 'CERT 4.2',
    schedule: changeSchedule(),
    shortDescription: 'CertScanner CERT 4.2: Expiry alerts',
    description: 'Production release of CertScanner (CERT).',
    template: changeTemplate({ release: 'CERT 4.2' }),
    epicKeys: ['CERT-1'],
    storyKeys: ['CERT-2'],
    tasks: [
      {
        number: 'CTASK0020001',
        serviceName: 'gui',
        shortDescription: 'Deploy gui of CertScanner to production',
        description: 'Deploy gui of CertScanner.',
      },
    ],
    url: null,
    createdAt: '2026-10-07T09:00:00Z',
    ...overrides,
  };
}
