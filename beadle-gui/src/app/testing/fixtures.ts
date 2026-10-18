import { ActionHistoryPage, ActionRecord } from '../action-history/action-history-model';
import { BusinessService, Product } from '../core/models';

export function businessService(overrides: Partial<BusinessService> = {}): BusinessService {
  return {
    id: 3,
    name: 'Corporate Technology',
    version: 0,
    productCount: 1,
    changeCount: 0,
    ...overrides,
  };
}

export function product(overrides: Partial<Product> = {}): Product {
  return {
    id: 1,
    code: 'CERT',
    name: 'CertScanner',
    ownerTeam: 'Technology Architecture',
    contactEmail: 'arch@bbh.com',
    businessServiceId: 3,
    businessServiceName: 'Corporate Technology',
    version: 3,
    updatedAt: '2026-10-04T08:00:00Z',
    ...overrides,
  };
}

export function actionRecord(overrides: Partial<ActionRecord> = {}): ActionRecord {
  return {
    id: 41,
    at: '2026-10-15T09:00:05Z',
    user: 'Mateusz Matan',
    action: 'CHANGE_RAISED',
    objectType: 'CHANGE',
    objectId: 7,
    objectKey: 'CHG0031001',
    details: '{"command":{"productId":1,"fixVersion":"CERT 4.2"}}',
    outcome: 'SUCCESS',
    message: null,
    correlationId: '6f1c2a9e-3b7d-4c55-9a51-2f0e8d4b7c10',
    durationMs: 812,
    ...overrides,
  };
}

export function actionPage(
  content: ActionRecord[],
  overrides: Partial<ActionHistoryPage> = {},
): ActionHistoryPage {
  return { content, page: 0, size: 50, total: content.length, ...overrides };
}
