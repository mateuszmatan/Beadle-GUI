import { BusinessService } from '../core/models';

export const NO_BUSINESS_SERVICE = 'No business service';

export interface BusinessServiceGroup<T, D extends BusinessService = BusinessService> {
  businessService: D | null;
  name: string;
  products: T[];
}

export function byBusinessService<
  T extends { businessServiceId: number | null },
  D extends BusinessService,
>(businessServices: readonly D[], products: readonly T[]): BusinessServiceGroup<T, D>[] {
  const groups = businessServices.map((businessService) => ({
    businessService,
    name: businessService.name,
    products: products.filter((product) => product.businessServiceId === businessService.id),
  }));
  const unassigned = products.filter(
    (product) =>
      !businessServices.some((businessService) => businessService.id === product.businessServiceId),
  );
  return unassigned.length
    ? [...groups, { businessService: null, name: NO_BUSINESS_SERVICE, products: unassigned }]
    : groups;
}
