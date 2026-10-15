import { Department } from '@common/core/models';

export type LookupKind =
  | 'users'
  | 'departments'
  | 'assignment-groups'
  | 'releases'
  | 'configuration-items'
  | 'incidents'
  | 'problems'
  | 'clients';

export interface SignedInUser {
  name: string;
}

export interface BeadleDepartment extends Department {
  changeCount: number;
}

export interface Product {
  id: number;
  code: string;
  name: string;
  ownerTeam: string | null;
  contactEmail: string | null;
  departmentId: number | null;
  departmentName: string | null;
  version: number;
  updatedAt: string;
}

export interface ProductRequest {
  code: string | null;
  name: string;
  departmentId: number | null;
  ownerTeam: string | null;
  contactEmail: string | null;
  version: number | null;
}
