import { Routes } from '@angular/router';
import { unsavedChangesGuard } from '@common/core/unsaved-changes';
import { DEPARTMENT_USAGE } from './beadle/department-usage';
import {
  ADMIN,
  ADMIN_DEPARTMENTS,
  ADMIN_PRODUCTS,
  BEADLE_ADMIN,
  CHANGES,
  NEW_CHANGE,
} from './core/sections';

export const routes: Routes = [
  { path: '', pathMatch: 'full', redirectTo: 'changes' },
  {
    path: 'changes',
    title: CHANGES.heading,
    loadComponent: () => import('./changes/changes-list').then((m) => m.ChangesList),
  },
  { path: 'changes/new', redirectTo: 'new-change' },
  {
    path: 'new-change',
    title: NEW_CHANGE.heading,
    canDeactivate: [unsavedChangesGuard],
    loadComponent: () => import('./changes/change-wizard').then((m) => m.ChangeWizard),
  },
  {
    path: 'changes/:id',
    title: 'ProTech change',
    loadComponent: () => import('./changes/change-detail').then((m) => m.ChangeDetail),
  },
  {
    path: 'changes/:id/edit',
    title: 'Edit ProTech change',
    canDeactivate: [unsavedChangesGuard],
    loadComponent: () => import('./changes/change-edit').then((m) => m.ChangeEdit),
  },
  {
    path: 'changes/:id/secure-coding',
    title: 'Secure coding ticket',
    canDeactivate: [unsavedChangesGuard],
    loadComponent: () => import('./changes/change-secure-coding').then((m) => m.ChangeSecureCoding),
  },
  {
    path: 'admin/products/:id',
    title: `Product · ${ADMIN.heading}`,
    canDeactivate: [unsavedChangesGuard],
    loadComponent: () => import('./beadle/beadle-product').then((m) => m.BeadleProduct),
  },
  {
    path: 'admin',
    loadComponent: () => import('@common/admin/admin-page').then((m) => m.AdminPage),
    data: { area: BEADLE_ADMIN },
    children: [
      { path: '', pathMatch: 'full', redirectTo: 'products' },
      {
        path: 'departments',
        title: `${ADMIN_DEPARTMENTS.label} · ${ADMIN.heading}`,
        data: { usage: DEPARTMENT_USAGE },
        loadComponent: () =>
          import('@common/admin/departments-admin').then((m) => m.DepartmentsAdmin),
      },
      {
        path: 'products',
        title: `${ADMIN_PRODUCTS.label} · ${ADMIN.heading}`,
        loadComponent: () => import('./beadle/beadle-products').then((m) => m.BeadleProducts),
      },
    ],
  },
  { path: '**', redirectTo: 'changes' },
];
