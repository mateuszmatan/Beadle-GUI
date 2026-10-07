import { ChangeProfile, ChangeTemplate, JiraIssue, ProductionChange } from '../changes/change-api';

export function changeTemplate(overrides: Partial<ChangeTemplate> = {}): ChangeTemplate {
  return {
    jiraProjectKey: 'CERT',
    configurationItem: 'CertScanner',
    assignmentGroup: 'Technology Architecture',
    type: 'NORMAL',
    category: 'Software',
    risk: 'MODERATE',
    impact: 'LOW',
    riskAssessment: 'Tested on QC.',
    approvers: ['Olivia Bennett', 'James Carter'],
    description: 'Watches TLS certificates.',
    implementationPlan: 'Deploy the services.',
    backoutPlan: 'Redeploy the previous release.',
    testPlan: 'Pipeline tests passed on QC.',
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

export function epic(key: string, summary: string, updated = '2026-09-20'): JiraIssue {
  return { key, summary, status: 'Done', epicKey: null, updated };
}

export function story(key: string, summary: string, epicKey: string): JiraIssue {
  return { key, summary, status: 'In Review', epicKey, updated: '2026-09-18' };
}

export function productionChange(overrides: Partial<ProductionChange> = {}): ProductionChange {
  return {
    id: 7,
    number: 'CHG0012345',
    productId: 1,
    productCode: 'CERT',
    productName: 'CertScanner',
    departmentName: 'Corporate Technology',
    window: { start: '2026-10-10T06:00:00Z', end: '2026-10-10T10:00:00Z' },
    shortDescription: 'CertScanner release: Expiry alerts',
    description: 'Production release of CertScanner (CERT).',
    template: changeTemplate(),
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
