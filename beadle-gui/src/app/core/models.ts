export const PRODUCT_CODE = /^[A-Z][A-Z0-9_-]{1,49}$/;

export interface FieldProblem {
  field: string;
  message: string;
}

export interface BusinessService {
  id: number;
  name: string;
  version: number;
  productCount: number;
  changeCount: number;
}

export type Department = BusinessService;

export interface BusinessServiceRequest {
  name: string;
  version: number | null;
}

export type DepartmentRequest = BusinessServiceRequest;

export interface LookupItem {
  value: string;
  detail: string | null;
}

export type LookupKind =
  | 'users'
  | 'business-services'
  | 'departments'
  | 'assignment-groups'
  | 'releases'
  | 'configuration-items'
  | 'incidents'
  | 'problems'
  | 'clients';

export interface SignedInUser {
  name: string;
  email: string | null;
  roles?: string[];
  departmentNames?: string[];
  admin?: boolean;
}

/** What Beadle answers to signing out with Ping: where the browser ends its session at Ping too. */
export interface SignedOut {
  redirectUrl: string;
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
