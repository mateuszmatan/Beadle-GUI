import { Department } from '../core/models';

export const NOT_IN_A_DEPARTMENT = 'Not in a department';

export interface DepartmentGroup<T, D extends Department = Department> {
  department: D | null;
  name: string;
  products: T[];
}

export function byDepartment<T extends { departmentId: number | null }, D extends Department>(
  departments: readonly D[],
  products: readonly T[],
): DepartmentGroup<T, D>[] {
  const groups = departments.map((department) => ({
    department,
    name: department.name,
    products: products.filter((product) => product.departmentId === department.id),
  }));
  const unassigned = products.filter(
    (product) => !departments.some((department) => department.id === product.departmentId),
  );
  return unassigned.length
    ? [...groups, { department: null, name: NOT_IN_A_DEPARTMENT, products: unassigned }]
    : groups;
}
