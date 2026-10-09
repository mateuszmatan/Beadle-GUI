import { DIALOG_DATA } from '@angular/cdk/dialog';
import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { DIALOG } from '../ui/dialog';

export interface ConfirmDialogData {
  title: string;
  message: string;
  confirmLabel: string;
  danger?: boolean;
}

@Component({
  selector: 'dso-confirm-dialog',
  imports: [DIALOG],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="modal-header">
      <h2 dsoDialogTitle>{{ data.title }}</h2>
    </div>
    <div class="modal-body">
      <p class="message">{{ data.message }}</p>
    </div>
    <div class="modal-footer">
      <button type="button" class="btn btn-link" dsoDialogClose>Cancel</button>
      <button
        type="button"
        class="btn"
        [class.btn-primary]="!data.danger"
        [class.btn-danger]="data.danger"
        [dsoDialogClose]="true"
      >
        {{ data.confirmLabel }}
      </button>
    </div>
  `,
  styles: `
    .message {
      margin: 0;
      white-space: pre-line;
    }
  `,
})
export class ConfirmDialog {
  protected readonly data = inject<ConfirmDialogData>(DIALOG_DATA);
}
