import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { Component } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { APP_NAME } from './core/app-name';
import { BROWSER_NAVIGATION } from './core/auth';
import { text } from './testing/dom';
import { App } from './app';
import { SECTIONS } from './core/sections';

@Component({ template: '' })
class Blank {}

describe('App', () => {
  let fixture: ComponentFixture<App>;
  let http: HttpTestingController;

  beforeEach(async () => {
    TestBed.configureTestingModule({
      imports: [App],
      providers: [
        provideRouter([{ path: '**', component: Blank }]),
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: APP_NAME, useValue: 'Beadle' },
        { provide: BROWSER_NAVIGATION, useValue: vi.fn() },
      ],
    });
    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(App);
    fixture.detectChanges();
    http.expectOne('/api/me').flush({ name: 'Mateusz Matan', email: null });
    await fixture.whenStable();
  });

  afterEach(() => http.verify());

  const page = () => fixture.nativeElement as HTMLElement;
  const links = () => [...page().querySelectorAll<HTMLAnchorElement>('nav.menu a')];
  const active = () =>
    links()
      .filter((link) => link.classList.contains('active'))
      .map(text);

  it('names Beadle in its header and links only its own sections', () => {
    expect(page().querySelector('.brand-name')?.textContent).toBe('BBH Beadle');
    expect(links().map(text)).toEqual(['Changes', 'New Change', 'Admin', 'Action History']);
    expect(links().map((link) => link.getAttribute('href'))).toEqual([
      '/changes',
      '/new-change',
      '/admin',
      '/action-history',
    ]);
  });

  it('shows who is signed in next to the sections', () => {
    expect(text(page().querySelector('header .account'))).toBe('Mateusz Matan Sign out');
  });

  it('gives the sections their ProTech headings', () => {
    expect(SECTIONS.map((section) => [section.heading, section.description])).toEqual([
      [
        'ProTech Changes',
        'The ProTech changes of your department and where each one is in its approval workflow. A change is read again from ProTech when you open it.',
      ],
      [
        'New ProTech Change',
        "Raise a ProTech change for a production release in guided steps. The product's change template fills in the answers and Jira provides the scope.",
      ],
      [
        'Beadle Admin',
        "Departments, products and each product's change template: the answers every new change of the product starts with.",
      ],
      [
        'Action History',
        'What people did in Beadle and what Beadle-ProTech and Beadle-Internal reported back, newest first, with the request and the outcome of every action.',
      ],
    ]);
  });

  it.each([
    ['/changes/4/edit', 'Changes'],
    ['/new-change', 'New Change'],
    ['/admin/products/2', 'Admin'],
    ['/action-history', 'Action History'],
  ])('marks the section of %s', async (url, label) => {
    await TestBed.inject(Router).navigateByUrl(url);
    await fixture.whenStable();

    expect(active()).toEqual([label]);
  });
});
