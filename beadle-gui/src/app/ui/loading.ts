import { ChangeDetectionStrategy, Component } from '@angular/core';

@Component({
  selector: 'beadle-loading',
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: {
    class: 'progress beadle-loading',
    role: 'progressbar',
    'aria-label': 'Loading',
  },
  template: `<div class="progress-bar progress-bar-striped progress-bar-animated w-100"></div>`,
})
export class BeadleLoading {}

@Component({
  selector: 'beadle-spinner',
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: {
    class: 'spinner-border spinner-border-sm beadle-spinner',
    role: 'status',
    'aria-label': 'Loading',
  },
  template: '',
})
export class BeadleSpinner {}
