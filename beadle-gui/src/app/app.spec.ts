import { Component } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { APP_NAME } from '@common/core/app-name';
import { text } from '@common/testing/dom';
import { App } from './app';
import { SECTIONS } from './core/sections';

@Component({ template: '' })
class Blank {}

describe('App', () => {
  let fixture: ComponentFixture<App>;

  beforeEach(async () => {
    TestBed.configureTestingModule({
      imports: [App],
      providers: [
        provideRouter([{ path: '**', component: Blank }]),
        { provide: APP_NAME, useValue: 'Beadle' },
      ],
    });
    fixture = TestBed.createComponent(App);
    await fixture.whenStable();
  });

  const page = () => fixture.nativeElement as HTMLElement;
  const links = () => [...page().querySelectorAll<HTMLAnchorElement>('nav.menu a')];
  const active = () =>
    links()
      .filter((link) => link.classList.contains('active'))
      .map(text);

  it('names Beadle in its header and links only its own sections', () => {
    expect(page().querySelector('.brand-name')?.textContent).toBe('BBH Beadle');
    expect(links().map(text)).toEqual(['Changes', 'New Change', 'Admin']);
    expect(links().map((link) => link.getAttribute('href'))).toEqual([
      '/changes',
      '/new-change',
      '/admin',
    ]);
    expect(text(page())).not.toContain('DevSecOps');
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
    ]);
  });

  it.each([
    ['/changes/4/edit', 'Changes'],
    ['/new-change', 'New Change'],
    ['/admin/products/2', 'Admin'],
  ])('marks the section of %s', async (url, label) => {
    await TestBed.inject(Router).navigateByUrl(url);
    await fixture.whenStable();

    expect(active()).toEqual([label]);
  });
});
