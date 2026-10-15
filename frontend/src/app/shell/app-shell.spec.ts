import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { Component } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { APP_NAME } from '../core/app-name';
import { BROWSER_NAVIGATION } from '../core/auth';
import { Notifier } from '../core/notifier';
import { PortalSection } from '../core/sections';
import { text, toast } from '../testing/dom';
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
  let http: HttpTestingController;
  let navigate: ReturnType<typeof vi.fn>;

  beforeEach(async () => {
    navigate = vi.fn();
    TestBed.configureTestingModule({
      imports: [AppShell],
      providers: [
        provideRouter([{ path: '**', component: Blank }]),
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: APP_NAME, useValue: 'Test Portal' },
        { provide: BROWSER_NAVIGATION, useValue: navigate },
      ],
    });
    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(AppShell);
    fixture.componentRef.setInput('sections', [
      section('/orders', 'Orders'),
      section('/admin', 'Admin'),
    ]);
    fixture.detectChanges();
  });

  afterEach(() => {
    http.verify();
    TestBed.inject(Notifier).dismiss();
  });

  const page = () => fixture.nativeElement as HTMLElement;
  const links = () => [...page().querySelectorAll<HTMLAnchorElement>('nav.menu a')];
  const active = () =>
    links()
      .filter((link) => link.classList.contains('active'))
      .map(text);
  const userName = () => page().querySelector<HTMLElement>('.account .user-name');
  const signOut = () => page().querySelector<HTMLButtonElement>('.account button.sign-out');

  async function signedIn(user: object = { name: 'Ann Smith', email: 'ann.smith@bbh.com' }) {
    http.expectOne({ method: 'GET', url: '/api/me' }).flush(user);
    await fixture.whenStable();
  }

  it('names the app in its header and links every section in order', async () => {
    await signedIn();

    expect(page().querySelector('.brand-name')?.textContent).toBe('BBH Test Portal');
    expect(page().querySelector('.brand')?.getAttribute('href')).toBe('/');
    expect(links().map(text)).toEqual(['Orders', 'Admin']);
    expect(links().map((link) => link.getAttribute('href'))).toEqual(['/orders', '/admin']);
    expect(page().querySelector('header svg-icon, header svg')).toBeNull();
    expect(text(page().querySelector('header'))).not.toContain('What Orders is for');
  });

  it('marks the section of the current page, also on the pages below it', async () => {
    await signedIn();
    await TestBed.inject(Router).navigateByUrl('/admin/products/2/edit');
    await fixture.whenStable();

    expect(active()).toEqual(['Admin']);
    expect(links()[1].getAttribute('aria-current')).toBe('page');

    await TestBed.inject(Router).navigateByUrl('/orders');
    await fixture.whenStable();

    expect(active()).toEqual(['Orders']);
  });

  it('has an empty footer apart from the year mark', async () => {
    await signedIn();

    expect(page().querySelector('footer')?.textContent?.trim()).toBe('BBH 2026');
  });

  it('shows the signed-in user with a Sign out control once Beadle has named them', async () => {
    expect(page().querySelector('.account')).toBeNull();

    await signedIn();

    expect(text(userName())).toBe('Ann Smith');
    expect(userName()?.getAttribute('title')).toBe('ann.smith@bbh.com');
    expect(text(signOut())).toBe('Sign out');
    expect(signOut()?.disabled).toBe(false);
  });

  it('shows the name without a title when Beadle knows no email address', async () => {
    await signedIn({ name: 'Mateusz Matan', email: null });

    expect(text(userName())).toBe('Mateusz Matan');
    expect(userName()?.hasAttribute('title')).toBe(false);
  });

  it('shows no user while Beadle cannot name one', async () => {
    http
      .expectOne('/api/me')
      .flush({ detail: 'Down' }, { status: 503, statusText: 'Service Unavailable' });
    await fixture.whenStable();

    expect(page().querySelector('.account')).toBeNull();
    expect(links().map(text)).toEqual(['Orders', 'Admin']);
  });

  it('signs out at Beadle and then starts again from the first page', async () => {
    await signedIn();

    signOut()!.click();
    signOut()!.click();
    await fixture.whenStable();

    expect(signOut()?.disabled).toBe(true);
    expect(signOut()?.getAttribute('aria-busy')).toBe('true');
    const request = http.expectOne({ method: 'POST', url: '/logout' });
    expect(request.request.body).toBeNull();
    expect(navigate).not.toHaveBeenCalled();

    request.flush(null, { status: 204, statusText: 'No Content' });
    await fixture.whenStable();

    expect(navigate).toHaveBeenCalledExactlyOnceWith('/');
  });

  it('signs out at Ping too when Beadle answers where Ping ends the session', async () => {
    await signedIn();

    signOut()!.click();
    http.expectOne({ method: 'POST', url: '/logout' }).flush({
      redirectUrl:
        'https://ping.bbh.com/idp/init_logout.openid?id_token_hint=t&post_logout_redirect_uri=https://beadle/',
    });
    await fixture.whenStable();

    expect(navigate).toHaveBeenCalledExactlyOnceWith(
      'https://ping.bbh.com/idp/init_logout.openid?id_token_hint=t&post_logout_redirect_uri=https://beadle/',
    );
  });

  it('says so and stays signed in when signing out fails', async () => {
    await signedIn();

    signOut()!.click();
    http
      .expectOne({ method: 'POST', url: '/logout' })
      .flush(null, { status: 403, statusText: 'Forbidden' });
    await fixture.whenStable();

    expect(navigate).not.toHaveBeenCalled();
    expect(signOut()?.disabled).toBe(false);
    expect(signOut()?.hasAttribute('aria-busy')).toBe(false);
    expect(text(toast())).toContain(
      'You could not be signed out. The portal could not complete the request (error 403 Forbidden).',
    );
  });
});
