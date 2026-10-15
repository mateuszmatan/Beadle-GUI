import { BeadleDepartment, Product } from '../core/models';

export function department(overrides: Partial<BeadleDepartment> = {}): BeadleDepartment {
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
    departmentId: 3,
    departmentName: 'Corporate Technology',
    version: 3,
    updatedAt: '2026-10-04T08:00:00Z',
    ...overrides,
  };
}
