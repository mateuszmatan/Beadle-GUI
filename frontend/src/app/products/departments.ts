import { Department, ProductSummary } from '../core/models';
import { counted } from '../shared/formatting';

export const NOT_IN_A_DEPARTMENT = 'Not in a department';

export interface DepartmentGroup<T = ProductSummary> {
  department: Department | null;
  name: string;
  products: T[];
}

export function byDepartment<T extends { departmentId: number | null }>(
  departments: readonly Department[],
  products: readonly T[],
): DepartmentGroup<T>[] {
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

export function tally(group: DepartmentGroup): string {
  const sum = (count: (product: ProductSummary) => number) =>
    group.products.reduce((total, product) => total + count(product), 0);
  const { productCount, pipelineCount, activePipelineCount } = group.department ?? {
    productCount: group.products.length,
    pipelineCount: sum((product) => product.pipelineCount),
    activePipelineCount: sum((product) => product.activePipelineCount),
  };
  const text = `${counted(pipelineCount, 'DevSecOps pipeline')} for ${counted(productCount, 'product')}`;
  return activePipelineCount < pipelineCount ? `${text} · ${activePipelineCount} active` : text;
}
