import { inject, provideEnvironmentInitializer } from '@angular/core';
import { SvgIconRegistryService, SvgLoader, provideAngularSvgIcon } from 'angular-svg-icon';
import { of } from 'rxjs';
import { fakeHighcharts } from './app/testing/highcharts';
import { HIGHCHARTS } from './app/ui/chart';
import { registerIcons } from './app/ui/design-system';

export default [
  { provide: HIGHCHARTS, useValue: fakeHighcharts },
  provideAngularSvgIcon({ loader: { provide: SvgLoader, useValue: { getSvg: () => of('') } } }),
  provideEnvironmentInitializer(() => registerIcons(inject(SvgIconRegistryService))),
];
