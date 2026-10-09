import { ChangeDetectionStrategy, Component } from '@angular/core';

@Component({
  selector: 'dso-loading',
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: {
    class: 'progress dso-loading',
    role: 'progressbar',
    'aria-label': 'Loading',
  },
  template: `<div class="progress-bar progress-bar-striped progress-bar-animated w-100"></div>`,
})
export class DsoLoading {}

@Component({
  selector: 'dso-spinner',
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: {
    class: 'spinner-border spinner-border-sm dso-spinner',
    role: 'status',
    'aria-label': 'Loading',
  },
  template: '',
})
export class DsoSpinner {}
