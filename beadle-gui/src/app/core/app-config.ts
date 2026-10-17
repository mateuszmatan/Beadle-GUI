import {
  ApplicationConfig,
  inject,
  provideAppInitializer,
  provideBrowserGlobalErrorListeners,
} from '@angular/core';
import {
  provideHttpClient,
  withFetch,
  withInterceptors,
  withNoXsrfProtection,
} from '@angular/common/http';
import {
  TitleStrategy,
  provideRouter,
  withComponentInputBinding,
  withInMemoryScrolling,
} from '@angular/router';
import { SvgIconRegistryService, provideAngularSvgIcon } from 'angular-svg-icon';
import { registerIcons } from '../ui/design-system';
import { provideDialogs } from '../ui/dialog';
import { authInterceptor } from './auth';
import { routes } from '../app.routes';
import { AppTitleStrategy } from './title-strategy';

export const appConfig: ApplicationConfig = {
  providers: [
    provideBrowserGlobalErrorListeners(),
    provideHttpClient(withFetch(), withInterceptors([authInterceptor]), withNoXsrfProtection()),
    provideRouter(
      routes,
      withComponentInputBinding(),
      withInMemoryScrolling({ scrollPositionRestoration: 'enabled' }),
    ),
    { provide: TitleStrategy, useClass: AppTitleStrategy },
    provideAngularSvgIcon(),
    provideDialogs(),
    provideAppInitializer(() => registerIcons(inject(SvgIconRegistryService))),
  ],
};
