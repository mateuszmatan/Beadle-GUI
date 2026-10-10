import { Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { Route, Router, Routes, provideRouter } from '@angular/router';
import { AdminPage } from '@common/admin/admin-page';
import { DepartmentsAdmin } from '@common/admin/departments-admin';
import { routes } from './app.routes';
import { BeadleProduct } from './beadle/beadle-product';
import { BeadleProducts } from './beadle/beadle-products';
import { DEPARTMENT_USAGE } from './beadle/department-usage';
import { ChangeDetail } from './changes/change-detail';
import { ChangeEdit } from './changes/change-edit';
import { ChangeSecureCoding } from './changes/change-secure-coding';
import { ChangeWizard } from './changes/change-wizard';
import { ChangesList } from './changes/changes-list';
import { BEADLE_ADMIN } from './core/sections';
import { department } from './testing/fixtures';

@Component({ template: '' })
class Blank {}

const stubbed = (list: Routes): Routes =>
  list.map(({ loadComponent, canDeactivate, children, ...route }) => ({
    ...route,
    ...(loadComponent ? { component: Blank } : {}),
    ...(children ? { children: stubbed(children) } : {}),
  }));

describe('routes', () => {
  const flattened = (list: Routes, parent = ''): [string, Route][] =>
    list.flatMap((route) => {
      const path = [parent, route.path].filter(Boolean).join('/');
      return [[path, route] as [string, Route], ...flattened(route.children ?? [], path)];
    });

  it('loads the page of every section lazily', async () => {
    const pages = await Promise.all(
      flattened(routes)
        .filter(([, route]) => route.loadComponent)
        .map(async ([path, route]) => [path, await route.loadComponent!()]),
    );

    expect(Object.fromEntries(pages)).toEqual({
      changes: ChangesList,
      'new-change': ChangeWizard,
      'changes/:id': ChangeDetail,
      'changes/:id/edit': ChangeEdit,
      'changes/:id/secure-coding': ChangeSecureCoding,
      'admin/products/:id': BeadleProduct,
      admin: AdminPage,
      'admin/departments': DepartmentsAdmin,
      'admin/products': BeadleProducts,
    });
  });

  it('gives the admin area its tabs and the departments what Beadle counts', () => {
    const data = Object.fromEntries(
      flattened(routes)
        .filter(([, route]) => route.data)
        .map(([path, route]) => [path, route.data]),
    );

    expect(data['admin']).toEqual({ area: BEADLE_ADMIN });
    expect(data['admin/departments']).toEqual({ usage: DEPARTMENT_USAGE });
  });

  it('guards the editors against leaving with unsaved changes', () => {
    expect(
      flattened(routes)
        .filter(([, route]) => route.canDeactivate)
        .map(([path]) => path),
    ).toEqual([
      'new-change',
      'changes/:id/edit',
      'changes/:id/secure-coding',
      'admin/products/:id',
    ]);
  });

  it.each([
    ['/', '/changes'],
    ['/changes/new', '/new-change'],
    ['/admin', '/admin/products'],
    ['/pipelines', '/changes'],
  ])('sends %s to %s', async (from, to) => {
    TestBed.configureTestingModule({ providers: [provideRouter(stubbed(routes))] });
    const router = TestBed.inject(Router);

    await router.navigateByUrl(from);

    expect(router.url).toBe(to);
  });
});

describe('DEPARTMENT_USAGE', () => {
  it('keeps a department with changes from being deleted', () => {
    expect(DEPARTMENT_USAGE.subject).toBe('its changes');
    expect(DEPARTMENT_USAGE.blocker!(department({ name: 'Custody', changeCount: 2 }))).toBe(
      'Custody cannot be deleted: it still has 2 changes.',
    );
    expect(DEPARTMENT_USAGE.blocker!(department({ changeCount: 0 }))).toBeNull();
  });
});
