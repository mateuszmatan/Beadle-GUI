import { DOCUMENT } from '@angular/common';
import {
  HttpErrorResponse,
  HttpInterceptorFn,
  HttpRequest,
  HttpXsrfTokenExtractor,
} from '@angular/common/http';
import { InjectionToken, inject } from '@angular/core';
import { Router, UrlTree } from '@angular/router';
import { catchError, throwError } from 'rxjs';

/** Where the browser signs in with Ping: Spring Security's OAuth2 login of the registration `ping`. */
export const SIGN_IN_PATH = '/oauth2/authorization/ping';
export const SIGN_OUT_PATH = '/logout';
export const XSRF_HEADER = 'X-XSRF-TOKEN';
/** The page to come back to after signing in, kept in the tab while the browser is at Ping. */
export const RETURN_KEY = 'beadle.signIn.returnTo';

/** Sends the browser to another address of Beadle, leaving the Angular app: the sign-in or a fresh start. */
export const BROWSER_NAVIGATION = new InjectionToken<(url: string) => void>('BROWSER_NAVIGATION', {
  providedIn: 'root',
  factory: () => {
    const location = inject(DOCUMENT).location;
    return (url: string) => location.assign(url);
  },
});

const changes = (request: HttpRequest<unknown>) =>
  request.method !== 'GET' && request.method !== 'HEAD';

const ownAddress = (url: string) => !/^[a-z][a-z0-9+.-]*:/i.test(url) && !url.startsWith('//');

const api = (url: string) => ownAddress(url) && /^\/?api\//.test(url);

/**
 * Sends the CSRF token Beadle keeps in the `XSRF-TOKEN` cookie as `X-XSRF-TOKEN` with every change to Beadle, and
 * sends the browser to the Ping sign-in when an API call answers 401 (no session, or one that ended), remembering the
 * page to come back to.
 */
export const authInterceptor: HttpInterceptorFn = (request, next) => {
  const token = inject(HttpXsrfTokenExtractor).getToken();
  const navigate = inject(BROWSER_NAVIGATION);
  const document = inject(DOCUMENT);
  const sent =
    token && changes(request) && ownAddress(request.url) && !request.headers.has(XSRF_HEADER)
      ? request.clone({ setHeaders: { [XSRF_HEADER]: token } })
      : request;
  return next(sent).pipe(
    catchError((error: unknown) => {
      if (error instanceof HttpErrorResponse && error.status === 401 && api(request.url)) {
        rememberPage(document);
        navigate(SIGN_IN_PATH);
      }
      return throwError(() => error);
    }),
  );
};

function rememberPage(document: Document): void {
  const { pathname, search, hash } = document.location;
  try {
    if (pathname !== '/') {
      document.defaultView?.sessionStorage.setItem(RETURN_KEY, pathname + search + hash);
    }
  } catch {
    // Without session storage the app starts on its first page after signing in.
  }
}

/**
 * The page the browser was on when it was sent to sign in, once: Ping brings it back to `/`. Only a path of this
 * application is taken.
 */
export function pageBeforeSignIn(): UrlTree | null {
  const document = inject(DOCUMENT);
  let page: string | null = null;
  try {
    const storage = document.defaultView?.sessionStorage;
    page = storage?.getItem(RETURN_KEY) ?? null;
    storage?.removeItem(RETURN_KEY);
  } catch {
    return null;
  }
  return page && page.startsWith('/') && !page.startsWith('//')
    ? inject(Router).parseUrl(page)
    : null;
}
