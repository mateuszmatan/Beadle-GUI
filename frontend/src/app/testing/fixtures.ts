import { department as plainDepartment } from '@common/testing/fixtures';
import { BeadleDepartment, Product } from '../core/models';

export function department(overrides: Partial<BeadleDepartment> = {}): BeadleDepartment {
  return { ...plainDepartment(), changeCount: 0, ...overrides };
}

export function product(overrides: Partial<Product> = {}): Product {
  return {
    id: 1,
    code: 'CERT',
    name: 'CertScanner',
    ownerTeam: 'Technology Architecture',
    contactEmail: 'arch@bbh.com',
    departmentId: 3,
    departmentName: 'Corporate Technology',
    version: 3,
    updatedAt: '2026-10-04T08:00:00Z',
    ...overrides,
  };
}
