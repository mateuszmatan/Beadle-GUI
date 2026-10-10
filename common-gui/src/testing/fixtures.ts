import { Department } from '../core/models';

export function department(overrides: Partial<Department> = {}): Department {
  return { id: 3, name: 'Corporate Technology', version: 0, productCount: 1, ...overrides };
}
