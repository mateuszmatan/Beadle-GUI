import { department, productSummary } from '../testing/fixtures';
import { NOT_IN_A_DEPARTMENT, byDepartment, tally } from './departments';

describe('departments', () => {
  const custody = department({ id: 4, name: 'Custody' });

  it('groups the products by department in the order given and the rest last', () => {
    const cert = productSummary();
    const lost = productSummary({ id: 2, departmentId: 9 });
    const loose = productSummary({ id: 3, departmentId: null });

    const groups = byDepartment([department(), custody], [loose, cert, lost]);

    expect(
      groups.map((group) => [group.name, group.products.map((product) => product.id)]),
    ).toEqual([
      ['Corporate Technology', [1]],
      ['Custody', []],
      [NOT_IN_A_DEPARTMENT, [3, 2]],
    ]);
    expect(groups[2].department).toBeNull();
    expect(byDepartment([custody], []).map((group) => group.name)).toEqual(['Custody']);
  });

  it('words the tally of a department in the singular and plural', () => {
    const group = (overrides: Parameters<typeof department>[0]) => ({
      department: department(overrides),
      name: 'Custody',
      products: [],
    });

    expect(tally(group({ productCount: 1, pipelineCount: 1, activePipelineCount: 1 }))).toBe(
      '1 DevSecOps pipeline for 1 product',
    );
    expect(tally(group({ productCount: 3, pipelineCount: 7, activePipelineCount: 6 }))).toBe(
      '7 DevSecOps pipelines for 3 products · 6 active',
    );
  });

  it('sums the tally of the products without a department from their rows', () => {
    const products = [
      productSummary({ pipelineCount: 2, activePipelineCount: 2 }),
      productSummary({ pipelineCount: 0, activePipelineCount: 0 }),
    ];

    expect(tally({ department: null, name: NOT_IN_A_DEPARTMENT, products })).toBe(
      '2 DevSecOps pipelines for 2 products',
    );
  });
});
