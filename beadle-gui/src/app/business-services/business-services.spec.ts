import { businessService } from '../testing/fixtures';
import { NO_BUSINESS_SERVICE, byBusinessService } from './business-services';

describe('business-services', () => {
  const custody = businessService({ id: 4, name: 'Custody' });
  const product = (id: number, businessServiceId: number | null) => ({ id, businessServiceId });

  it('groups the products by business service in the order given and the rest last', () => {
    const groups = byBusinessService(
      [businessService(), custody],
      [product(3, null), product(1, 3), product(2, 9)],
    );

    expect(
      groups.map((group) => [group.name, group.products.map((product) => product.id)]),
    ).toEqual([
      ['Corporate Technology', [1]],
      ['Custody', []],
      [NO_BUSINESS_SERVICE, [3, 2]],
    ]);
    expect(groups[2].businessService).toBeNull();
    expect(byBusinessService([custody], []).map((group) => group.name)).toEqual(['Custody']);
  });
});
