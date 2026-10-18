import { DOCUMENT } from '@angular/common';
import {
  HttpClient,
  HttpErrorResponse,
  provideHttpClient,
  withInterceptors,
  withNoXsrfProtection,
} from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { Dialog, DialogRef } from '@angular/cdk/dialog';
import { provideRouter } from '@angular/router';
import { Subject, firstValueFrom } from 'rxjs';
import {
  BROWSER_NAVIGATION,
  RETURN_KEY,
  SIGN_IN_PATH,
  XSRF_HEADER,
  afterSignOut,
  authInterceptor,
  pageBeforeSignIn,
} from './auth';
import { DRAFT_KEY, SESSION_ENDED, SessionEnd } from './session-end';

const COOKIE = 'XSRF-TOKEN';

describe('authInterceptor', () => {
  let http: HttpClient;
  let server: HttpTestingController;
  let navigate: ReturnType<typeof vi.fn>;

  beforeEach(() => {
    navigate = vi.fn();
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(withInterceptors([authInterceptor]), withNoXsrfProtection()),
        provideHttpClientTesting(),
        { provide: BROWSER_NAVIGATION, useValue: navigate },
      ],
    });
    http = TestBed.inject(HttpClient);
    server = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    server.verify();
    document.cookie = `${COOKIE}=; expires=Thu, 01 Jan 1970 00:00:00 GMT; path=/`;
    sessionStorage.clear();
    history.replaceState(null, '', '/');
  });

  const sent = (method: string, url: string) => server.expectOne({ method, url }).request;

  it('sends the token of the XSRF-TOKEN cookie with every change to Beadle, not with reads', () => {
    document.cookie = `${COOKIE}=cookie-token; path=/`;

    http.post('/api/products', {}).subscribe();
    http.put('/api/products/2', {}).subscribe();
    http.delete('/api/business-services/3').subscribe();
    http.post('/logout', null).subscribe();
    http.get('/api/products').subscribe();
    http.head('/api/products').subscribe();

    expect(sent('POST', '/api/products').headers.get(XSRF_HEADER)).toBe('cookie-token');
    expect(sent('PUT', '/api/products/2').headers.get(XSRF_HEADER)).toBe('cookie-token');
    expect(sent('DELETE', '/api/business-services/3').headers.get(XSRF_HEADER)).toBe(
      'cookie-token',
    );
    expect(sent('POST', '/logout').headers.get(XSRF_HEADER)).toBe('cookie-token');
    expect(sent('GET', '/api/products').headers.has(XSRF_HEADER)).toBe(false);
    expect(sent('HEAD', '/api/products').headers.has(XSRF_HEADER)).toBe(false);
  });

  it('keeps the token from other sites and leaves a header that is already set', () => {
    document.cookie = `${COOKIE}=cookie-token; path=/`;

    http.post('https://elsewhere.example/api/x', {}).subscribe();
    http.post('//elsewhere.example/api/x', {}).subscribe();
    http.post('/api/products', {}, { headers: { [XSRF_HEADER]: 'own' } }).subscribe();

    expect(sent('POST', 'https://elsewhere.example/api/x').headers.has(XSRF_HEADER)).toBe(false);
    expect(sent('POST', '//elsewhere.example/api/x').headers.has(XSRF_HEADER)).toBe(false);
    expect(sent('POST', '/api/products').headers.get(XSRF_HEADER)).toBe('own');
  });

  it('sends no header while Beadle has set no cookie, as when signing in is local', () => {
    http.post('/api/products', {}).subscribe();

    expect(sent('POST', '/api/products').headers.has(XSRF_HEADER)).toBe(false);
  });

  it('sends the browser to sign in with Ping when an API call answers 401, remembering the page', async () => {
    history.replaceState(null, '', '/changes/42?tab=tasks#approvals');
    const answer = firstValueFrom(http.get('/api/changes/42'));

    server.expectOne('/api/changes/42').flush(null, { status: 401, statusText: 'Unauthorized' });

    await expect(answer).rejects.toBeInstanceOf(HttpErrorResponse);
    expect(navigate).toHaveBeenCalledExactlyOnceWith(SIGN_IN_PATH);
    expect(sessionStorage.getItem(RETURN_KEY)).toBe('/changes/42?tab=tasks#approvals');
  });

  it('asks first when the open page has unsaved changes, keeping its draft until the user signs in again', async () => {
    history.replaceState(null, '', '/new-change');
    const answers = new Subject<boolean>();
    const open = vi
      .spyOn(TestBed.inject(Dialog), 'open')
      .mockReturnValue({ closed: answers } as unknown as DialogRef<unknown>);
    TestBed.inject(SessionEnd).shown({
      hasUnsavedChanges: () => true,
      draftName: 'new-change',
      draft: () => ({ step: 3 }),
    });
    const answer = firstValueFrom(http.get('/api/changes/preview'));

    server
      .expectOne('/api/changes/preview')
      .flush(null, { status: 401, statusText: 'Unauthorized' });

    await expect(answer).rejects.toBeInstanceOf(HttpErrorResponse);
    expect(open).toHaveBeenCalledWith(expect.anything(), {
      data: expect.objectContaining({ title: SESSION_ENDED }),
    });
    expect(navigate).not.toHaveBeenCalled();
    expect(JSON.parse(sessionStorage.getItem(DRAFT_KEY)!)).toEqual({
      page: 'new-change',
      draft: { step: 3 },
    });

    answers.next(true);
    expect(navigate).toHaveBeenCalledExactlyOnceWith(SIGN_IN_PATH);
    expect(sessionStorage.getItem(RETURN_KEY)).toBe('/new-change');
    vi.restoreAllMocks();
  });

  it('remembers no page when the browser is at the start anyway', async () => {
    const answer = firstValueFrom(http.get('api/me'));

    server.expectOne('api/me').flush(null, { status: 401, statusText: 'Unauthorized' });

    await expect(answer).rejects.toBeInstanceOf(HttpErrorResponse);
    expect(navigate).toHaveBeenCalledExactlyOnceWith(SIGN_IN_PATH);
    expect(sessionStorage.getItem(RETURN_KEY)).toBeNull();
  });

  it('still signs in when the tab cannot keep the page', async () => {
    history.replaceState(null, '', '/admin');
    vi.spyOn(Storage.prototype, 'setItem').mockImplementation(() => {
      throw new Error('Storage is off');
    });
    const answer = firstValueFrom(http.get('/api/products'));

    server.expectOne('/api/products').flush(null, { status: 401, statusText: 'Unauthorized' });

    await expect(answer).rejects.toBeInstanceOf(HttpErrorResponse);
    expect(navigate).toHaveBeenCalledExactlyOnceWith(SIGN_IN_PATH);
    vi.restoreAllMocks();
  });

  it.each([
    ['a 403 of Beadle', '/api/products', 403],
    ['a 500 of Beadle', '/api/products', 500],
    ['a 401 outside the API', '/assets/config.json', 401],
    ['a 401 of another site', 'https://elsewhere.example/api/me', 401],
  ])('passes %s on without signing in', async (_, url, status) => {
    const answer = firstValueFrom(http.get(url));

    server.expectOne(url).flush(null, { status, statusText: 'No' });

    await expect(answer).rejects.toMatchObject({ status });
    expect(navigate).not.toHaveBeenCalled();
  });
});

describe('pageBeforeSignIn', () => {
  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideRouter([])] });
  });

  afterEach(() => {
    sessionStorage.clear();
    vi.restoreAllMocks();
  });

  const page = () => TestBed.runInInjectionContext(() => pageBeforeSignIn())?.toString() ?? null;

  it('gives the remembered page once', () => {
    sessionStorage.setItem(RETURN_KEY, '/admin/products/2?x=1');

    expect(page()).toBe('/admin/products/2?x=1');
    expect(page()).toBeNull();
  });

  it.each(['//elsewhere.example/', 'https://elsewhere.example/', 'changes'])(
    'takes only a path of Beadle, not %s',
    (remembered) => {
      sessionStorage.setItem(RETURN_KEY, remembered);

      expect(page()).toBeNull();
      expect(sessionStorage.getItem(RETURN_KEY)).toBeNull();
    },
  );

  it('gives nothing when the tab keeps no storage', () => {
    vi.spyOn(Storage.prototype, 'getItem').mockImplementation(() => {
      throw new Error('Storage is off');
    });

    expect(page()).toBeNull();
  });
});

describe('BROWSER_NAVIGATION', () => {
  it('leaves the app for the address', () => {
    const assign = vi.fn();
    const withLocation = new Proxy(document, {
      get: (real, key) => {
        if (key === 'location') {
          return { assign };
        }
        const value: unknown = Reflect.get(real, key);
        return typeof value === 'function' ? value.bind(real) : value;
      },
    });
    TestBed.configureTestingModule({ providers: [{ provide: DOCUMENT, useValue: withLocation }] });

    TestBed.inject(BROWSER_NAVIGATION)(SIGN_IN_PATH);

    expect(assign).toHaveBeenCalledExactlyOnceWith(SIGN_IN_PATH);
  });
});

describe('afterSignOut', () => {
  it("goes to Ping's end of the session or to a page of Beadle", () => {
    expect(afterSignOut('https://ping.bbh.com/idp/init_logout.openid?id_token_hint=t')).toBe(
      'https://ping.bbh.com/idp/init_logout.openid?id_token_hint=t',
    );
    expect(afterSignOut('/')).toBe('/');
    expect(afterSignOut('/changes')).toBe('/changes');
  });

  it('starts again from the first page without an answer, or with one that is no web address', () => {
    expect(afterSignOut(null)).toBe('/');
    expect(afterSignOut(undefined)).toBe('/');
    expect(afterSignOut('')).toBe('/');
    expect(afterSignOut('javascript:alert(1)')).toBe('/');
    expect(afterSignOut('//elsewhere.example/')).toBe('/');
  });
});
