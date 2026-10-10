import { AdminArea, PortalSection, PortalTab } from '@common/core/sections';

export const CHANGES: PortalSection = {
  path: '/changes',
  label: 'Changes',
  heading: 'ProTech Changes',
  description:
    'The ProTech changes of your department and where each one is in its approval workflow. A change is read again from ProTech when you open it.',
};

export const NEW_CHANGE: PortalSection = {
  path: '/new-change',
  label: 'New Change',
  heading: 'New ProTech Change',
  description:
    "Raise a ProTech change for a production release in guided steps. The product's change template fills in the answers and Jira provides the scope.",
};

export const ADMIN: PortalSection = {
  path: '/admin',
  label: 'Admin',
  heading: 'Beadle Admin',
  description:
    "Departments, products and each product's change template: the answers every new change of the product starts with.",
};

export const SECTIONS: readonly PortalSection[] = [CHANGES, NEW_CHANGE, ADMIN];

export const ADMIN_DEPARTMENTS: PortalTab = { path: '/admin/departments', label: 'Departments' };
export const ADMIN_PRODUCTS: PortalTab = { path: '/admin/products', label: 'Products' };

export const BEADLE_ADMIN: AdminArea = {
  section: ADMIN,
  tabs: [ADMIN_DEPARTMENTS, ADMIN_PRODUCTS],
};

export function beadleProduct(id: number | string): (string | number)[] {
  return [ADMIN_PRODUCTS.path, id];
}

export function beadleChange(id: number | string, ...rest: string[]): (string | number)[] {
  return [CHANGES.path, id, ...rest];
}
