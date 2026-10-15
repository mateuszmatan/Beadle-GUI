import { inject, provideEnvironmentInitializer } from '@angular/core';
import { SvgIconRegistryService, SvgLoader, provideAngularSvgIcon } from 'angular-svg-icon';
import { of } from 'rxjs';
import { registerIcons } from './app/ui/design-system';

export default [
  provideAngularSvgIcon({ loader: { provide: SvgLoader, useValue: { getSvg: () => of('') } } }),
  provideEnvironmentInitializer(() => registerIcons(inject(SvgIconRegistryService))),
];
