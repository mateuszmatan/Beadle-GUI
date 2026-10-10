import { department } from '../testing/fixtures';
import { NOT_IN_A_DEPARTMENT, byDepartment } from './departments';

describe('departments', () => {
  const custody = department({ id: 4, name: 'Custody' });
  const product = (id: number, departmentId: number | null) => ({ id, departmentId });

  it('groups the products by department in the order given and the rest last', () => {
    const groups = byDepartment(
      [department(), custody],
      [product(3, null), product(1, 3), product(2, 9)],
    );

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
});
