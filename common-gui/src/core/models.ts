export const PRODUCT_CODE = /^[A-Z][A-Z0-9_-]{1,49}$/;

export interface FieldProblem {
  field: string;
  message: string;
}

export interface Department {
  id: number;
  name: string;
  version: number;
  productCount: number;
}

export interface DepartmentRequest {
  name: string;
  version: number | null;
}

export interface LookupItem {
  value: string;
  detail: string | null;
}
