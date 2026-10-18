import { Routes } from '@angular/router';
import { pageBeforeSignIn } from './core/auth';
import { unsavedChangesGuard } from './core/unsaved-changes';
import {
  ACTION_HISTORY,
  ADMIN,
  ADMIN_DEPARTMENTS,
  ADMIN_PRODUCTS,
  CHANGES,
  NEW_CHANGE,
} from './core/sections';

export const routes: Routes = [
  { path: '', pathMatch: 'full', redirectTo: () => pageBeforeSignIn() ?? 'changes' },
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
    loadComponent: () => import('./products/product-page').then((m) => m.BeadleProduct),
  },
  {
    path: 'admin',
    loadComponent: () => import('./admin/admin-page').then((m) => m.AdminPage),
    children: [
      { path: '', pathMatch: 'full', redirectTo: 'products' },
      {
        path: 'departments',
        title: `${ADMIN_DEPARTMENTS.label} · ${ADMIN.heading}`,
        loadComponent: () =>
          import('./business-services/business-services-admin').then((m) => m.DepartmentsAdmin),
      },
      {
        path: 'products',
        title: `${ADMIN_PRODUCTS.label} · ${ADMIN.heading}`,
        loadComponent: () => import('./products/products-page').then((m) => m.BeadleProducts),
      },
    ],
  },
  {
    path: 'action-history',
    title: ACTION_HISTORY.heading,
    loadComponent: () => import('./action-history/action-history').then((m) => m.ActionHistory),
  },
  { path: '**', redirectTo: 'changes' },
];
