import { Component, input } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { Route, Router, provideRouter, withComponentInputBinding } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { ADMIN_TABS } from '../core/sections';
import { text } from '../testing/dom';
import { AdminPage } from './admin-page';

@Component({ template: '<p class="tab-content">{{ tab() }}</p>' })
class TabContent {
  readonly tab = input('');
}

const ADMIN_ROUTE: Route = {
  path: 'admin',
  component: AdminPage,
  children: [
    { path: '', pathMatch: 'full', redirectTo: 'products' },
    ...ADMIN_TABS.map((tab) => ({
      path: tab.path.split('/').at(-1),
      component: TabContent,
      data: { tab: tab.label },
    })),
  ],
};

describe('AdminPage', () => {
  let harness: RouterTestingHarness;

  beforeEach(async () => {
    TestBed.configureTestingModule({
      providers: [provideRouter([ADMIN_ROUTE], withComponentInputBinding())],
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

  it('shows the heading, the description and the tabs of Beadle Admin', async () => {
    await harness.navigateByUrl('/admin/departments');

    expect(text(page().querySelector('h1'))).toBe('Beadle Admin');
    expect(text(page().querySelector('.page-header .page-description'))).toBe(
      "Departments, products and each product's change template: the answers every new change of the product starts with.",
    );
    expect(page().querySelector('nav.tab-bar')?.getAttribute('aria-label')).toBe('Beadle Admin');
    expect(tabs().map(text)).toEqual(['Departments', 'Products']);
    expect(tabs().map((link) => link.getAttribute('href'))).toEqual([
      '/admin/departments',
      '/admin/products',
    ]);
    expect(text(page().querySelector('.tab-content'))).toBe('Departments');
    expect(page().querySelector('svg-icon')).toBeNull();
  });

  it.each([
    ['/admin/departments', 'Departments'],
    ['/admin/products', 'Products'],
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

    tab('Departments').click();
    await harness.fixture.whenStable();

    expect(TestBed.inject(Router).url).toBe('/admin/departments');
    expect(active()).toEqual(['Departments']);
    expect(current()).toEqual(['Departments']);
    expect(text(page().querySelector('h1'))).toBe('Beadle Admin');
    expect(text(page().querySelector('.tab-content'))).toBe('Departments');
  });
});
