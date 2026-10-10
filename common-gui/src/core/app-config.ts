import {
  ApplicationConfig,
  inject,
  provideAppInitializer,
  provideBrowserGlobalErrorListeners,
} from '@angular/core';
import { provideHttpClient, withFetch } from '@angular/common/http';
import {
  Routes,
  TitleStrategy,
  provideRouter,
  withComponentInputBinding,
  withInMemoryScrolling,
} from '@angular/router';
import { SvgIconRegistryService, provideAngularSvgIcon } from 'angular-svg-icon';
import { registerIcons } from '../ui/design-system';
import { provideDialogs } from '../ui/dialog';
import { APP_NAME } from './app-name';
import { PortalTitleStrategy } from './title-strategy';

export function appConfig(name: string, routes: Routes): ApplicationConfig {
  return {
    providers: [
      { provide: APP_NAME, useValue: name },
      provideBrowserGlobalErrorListeners(),
      provideHttpClient(withFetch()),
      provideRouter(
        routes,
        withComponentInputBinding(),
        withInMemoryScrolling({ scrollPositionRestoration: 'enabled' }),
      ),
      { provide: TitleStrategy, useClass: PortalTitleStrategy },
      provideAngularSvgIcon(),
      provideDialogs(),
      provideAppInitializer(() => registerIcons(inject(SvgIconRegistryService))),
    ],
  };
}
