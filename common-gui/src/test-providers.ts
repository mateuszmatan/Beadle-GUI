import { inject, provideEnvironmentInitializer } from '@angular/core';
import { SvgIconRegistryService, SvgLoader, provideAngularSvgIcon } from 'angular-svg-icon';
import { of } from 'rxjs';
import { fakeHighcharts } from './testing/highcharts';
import { HIGHCHARTS } from './ui/chart';
import { registerIcons } from './ui/design-system';

export default [
  { provide: HIGHCHARTS, useValue: fakeHighcharts },
  provideAngularSvgIcon({ loader: { provide: SvgLoader, useValue: { getSvg: () => of('') } } }),
  provideEnvironmentInitializer(() => registerIcons(inject(SvgIconRegistryService))),
];
