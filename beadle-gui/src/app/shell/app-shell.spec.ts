import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { Component } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { BROWSER_NAVIGATION } from '../core/auth';
import { Notifier } from '../core/notifier';
import { SECTIONS } from '../core/sections';
import { SessionEnd } from '../core/session-end';
import { text, toast } from '../testing/dom';
import { AppShell } from './app-shell';

@Component({ template: '' })
class Blank {}

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
        { provide: BROWSER_NAVIGATION, useValue: navigate },
      ],
    });
    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(AppShell);
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
  const userName = () => page().querySelector<HTMLElement>('.user-badge .user-name');
  const signOut = () => page().querySelector<HTMLButtonElement>('.user-badge button.sign-out');

  async function signedIn(user: object = { name: 'Ann Smith', email: 'ann.smith@bbh.com' }) {
    http.expectOne({ method: 'GET', url: '/api/me' }).flush(user);
    await fixture.whenStable();
  }

  it('tells the end of the session which page is open, so it can keep the unsaved changes of that page', async () => {
    const sessionEnd = TestBed.inject(SessionEnd);
    const shown = vi.spyOn(sessionEnd, 'shown');
    const hidden = vi.spyOn(sessionEnd, 'hidden');
    await signedIn();
    TestBed.inject(Router).resetConfig([
      { path: 'changes', component: Blank },
      { path: 'admin', component: Blank },
    ]);

    await TestBed.inject(Router).navigateByUrl('/changes');
    await fixture.whenStable();
    const orders = shown.mock.lastCall?.[0];
    await TestBed.inject(Router).navigateByUrl('/admin');
    await fixture.whenStable();

    expect(orders).toBeInstanceOf(Blank);
    expect(hidden).toHaveBeenCalledWith(orders);
    expect(shown.mock.lastCall?.[0]).toBeInstanceOf(Blank);
    expect(shown.mock.lastCall?.[0]).not.toBe(orders);
  });

  it('names Beadle in its header and links every section in order', async () => {
    await signedIn();

    expect(page().querySelector('.brand-name')?.textContent).toBe('BBH Beadle');
    expect(page().querySelector('.brand')?.getAttribute('href')).toBe('/');
    expect(links().map(text)).toEqual(['Changes', 'New Change', 'Admin', 'Action History']);
    expect(links().map((link) => link.getAttribute('href'))).toEqual(SECTIONS.map((s) => s.path));
    expect(page().querySelector('header svg-icon, header svg')).toBeNull();
    expect(text(page().querySelector('header'))).not.toContain(SECTIONS[0].description);
  });

  it.each([
    ['/changes/4/edit', 'Changes'],
    ['/new-change', 'New Change'],
    ['/admin/products/2', 'Admin'],
    ['/action-history', 'Action History'],
  ])('marks the section of %s', async (url, label) => {
    await signedIn();
    await TestBed.inject(Router).navigateByUrl(url);
    await fixture.whenStable();

    expect(active()).toEqual([label]);
    expect(
      links()
        .find((link) => text(link) === label)
        ?.getAttribute('aria-current'),
    ).toBe('page');
  });

  it('has an empty footer apart from the year mark', async () => {
    await signedIn();

    expect(page().querySelector('footer')?.textContent?.trim()).toBe('BBH 2026');
  });

  it('shows the signed-in user with a Sign out control once Beadle has named them', async () => {
    expect(page().querySelector('.user-badge')).toBeNull();

    await signedIn();

    expect(text(userName())).toBe('Ann Smith');
    expect(page().querySelector('.user-badge')?.getAttribute('title')).toBe('ann.smith@bbh.com');
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

    expect(page().querySelector('.user-badge')).toBeNull();
    expect(links()).toHaveLength(4);
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
      'You could not be signed out. Beadle could not complete the request (error 403 Forbidden).',
    );
  });
});
