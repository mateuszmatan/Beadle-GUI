import { Component, input } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { Route, Router, provideRouter, withComponentInputBinding } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { AdminArea, BEADLE_ADMINISTRATION, DEVSECOPS_ADMIN } from '../core/sections';
import { text } from '../testing/dom';
import { AdminPage } from './admin-page';

@Component({ template: '<p class="tab-content">{{ tab() }}</p>' })
class TabContent {
  readonly tab = input('');
}

function adminRoute(area: AdminArea): Route {
  return {
    path: area.section.path.slice(1),
    component: AdminPage,
    data: { area },
    children: [
      { path: '', pathMatch: 'full', redirectTo: 'products' },
      ...area.tabs.map((tab) => ({
        path: tab.path.split('/').at(-1),
        component: TabContent,
        data: { tab: tab.label },
      })),
    ],
  };
}

describe('AdminPage', () => {
  let harness: RouterTestingHarness;

  beforeEach(async () => {
    TestBed.configureTestingModule({
      providers: [
        provideRouter(
          [adminRoute(DEVSECOPS_ADMIN), adminRoute(BEADLE_ADMINISTRATION)],
          withComponentInputBinding(),
        ),
      ],
    });
    harness = await RouterTestingHarness.create();
  });

  const page = () => harness.routeNativeElement!;
  const tabs = () => [...page().querySelectorAll<HTMLAnchorElement>('nav.tab-bar a')];
  const tab = (label: string) => tabs().find((link) => text(link) === label)!;
  const active = () =>
    tabs()
      .filter((link) => link.classList.contains('active'))
      .map(text);
  const current = () =>
    tabs()
      .filter((link) => link.getAttribute('aria-current') === 'page')
      .map(text);

  it('shows the heading, the description and the tabs of DevSecOps Admin', async () => {
    await harness.navigateByUrl('/admin/departments');

    expect(text(page().querySelector('h1'))).toBe('DevSecOps Admin');
    expect(text(page().querySelector('.page-header .page-description'))).toBe(
      'Departments, products, services, the template of a new service and the DSOEnhanced library defaults',
    );
    expect(page().querySelector('nav.tab-bar')?.getAttribute('aria-label')).toBe('DevSecOps Admin');
    expect(tabs().map(text)).toEqual([
      'Departments',
      'Products',
      'Service template',
      'Library defaults',
    ]);
    expect(tabs().map((link) => link.getAttribute('href'))).toEqual([
      '/admin/departments',
      '/admin/products',
      '/admin/template',
      '/admin/settings',
    ]);
    expect(text(page().querySelector('.tab-content'))).toBe('Departments');
    expect(page().querySelector('mat-icon')).toBeNull();
  });

  it.each([
    ['/admin/departments', 'Departments'],
    ['/admin/products', 'Products'],
    ['/admin/template', 'Service template'],
    ['/admin/settings', 'Library defaults'],
  ])('marks the tab of %s as the current page', async (url, label) => {
    await harness.navigateByUrl(url);

    expect(active()).toEqual([label]);
    expect(current()).toEqual([label]);
    expect(text(page().querySelector('.tab-content'))).toBe(label);
  });

  it('opens on the products tab', async () => {
    await harness.navigateByUrl('/admin');

    expect(TestBed.inject(Router).url).toBe('/admin/products');
    expect(active()).toEqual(['Products']);
  });

  it('moves the mark to the tab that is clicked', async () => {
    await harness.navigateByUrl('/admin/products');

    tab('Library defaults').click();
    await harness.fixture.whenStable();

    expect(TestBed.inject(Router).url).toBe('/admin/settings');
    expect(active()).toEqual(['Library defaults']);
    expect(current()).toEqual(['Library defaults']);
    expect(text(page().querySelector('h1'))).toBe('DevSecOps Admin');
    expect(text(page().querySelector('.tab-content'))).toBe('Library defaults');
  });

  it('shows Beadle Admin with its departments and products tabs', async () => {
    await harness.navigateByUrl('/beadle/admin');

    expect(TestBed.inject(Router).url).toBe('/beadle/admin/products');
    expect(text(page().querySelector('h1'))).toBe('Beadle Admin');
    expect(text(page().querySelector('.page-description'))).toBe(
      'Departments, products and the change template of each product',
    );
    expect(tabs().map(text)).toEqual(['Departments', 'Products']);
    expect(tabs().map((link) => link.getAttribute('href'))).toEqual([
      '/beadle/admin/departments',
      '/beadle/admin/products',
    ]);
    expect(active()).toEqual(['Products']);
  });
});
