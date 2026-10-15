import { Component } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { APP_NAME } from '../core/app-name';
import { PortalSection } from '../core/sections';
import { text } from '../testing/dom';
import { AppShell } from './app-shell';

@Component({ template: '' })
class Blank {}

const section = (path: string, label: string): PortalSection => ({
  path,
  label,
  heading: `Heading of ${label}`,
  description: `What ${label} is for`,
});

describe('AppShell', () => {
  let fixture: ComponentFixture<AppShell>;

  beforeEach(async () => {
    TestBed.configureTestingModule({
      imports: [AppShell],
      providers: [
        provideRouter([{ path: '**', component: Blank }]),
        { provide: APP_NAME, useValue: 'Test Portal' },
      ],
    });
    fixture = TestBed.createComponent(AppShell);
    fixture.componentRef.setInput('sections', [
      section('/orders', 'Orders'),
      section('/admin', 'Admin'),
    ]);
    await fixture.whenStable();
  });

  const page = () => fixture.nativeElement as HTMLElement;
  const links = () => [...page().querySelectorAll<HTMLAnchorElement>('nav.menu a')];
  const active = () =>
    links()
      .filter((link) => link.classList.contains('active'))
      .map(text);

  it('names the app in its header and links every section in order', () => {
    expect(page().querySelector('.brand-name')?.textContent).toBe('BBH Test Portal');
    expect(page().querySelector('.brand')?.getAttribute('href')).toBe('/');
    expect(links().map(text)).toEqual(['Orders', 'Admin']);
    expect(links().map((link) => link.getAttribute('href'))).toEqual(['/orders', '/admin']);
    expect(page().querySelector('header svg-icon, header svg')).toBeNull();
    expect(text(page().querySelector('header'))).not.toContain('What Orders is for');
  });

  it('marks the section of the current page, also on the pages below it', async () => {
    await TestBed.inject(Router).navigateByUrl('/admin/products/2/edit');
    await fixture.whenStable();

    expect(active()).toEqual(['Admin']);
    expect(links()[1].getAttribute('aria-current')).toBe('page');

    await TestBed.inject(Router).navigateByUrl('/orders');
    await fixture.whenStable();

    expect(active()).toEqual(['Orders']);
  });

  it('has an empty footer apart from the year mark', () => {
    expect(page().querySelector('footer')?.textContent?.trim()).toBe('BBH 2026');
  });
});
