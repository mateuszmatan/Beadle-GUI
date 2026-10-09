import { ChangeDetectionStrategy, Component, input, output } from '@angular/core';

export type ToastKind = 'info' | 'error';

@Component({
  selector: 'dso-toast',
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: {
    class: 'toast show dso-toast',
    '[class.error]': "kind() === 'error'",
    '[attr.role]': "kind() === 'error' ? 'alert' : 'status'",
    'aria-live': 'polite',
  },
  template: `
    <div class="toast-body">{{ message() }}</div>
    @if (action(); as action) {
      <button type="button" class="btn btn-sm toast-action" (click)="closed.emit()">
        {{ action }}
      </button>
    }
  `,
})
export class Toast {
  readonly message = input.required<string>();
  readonly action = input<string>();
  readonly kind = input<ToastKind>('info');
  readonly closed = output();
}
