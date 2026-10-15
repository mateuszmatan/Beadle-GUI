import { inject } from '@angular/core';
import { Dialog } from '@angular/cdk/dialog';
import { CanDeactivateFn } from '@angular/router';
import { map } from 'rxjs';
import { ConfirmDialog, ConfirmDialogData } from '../shared/confirm-dialog';

export interface HasUnsavedChanges {
  hasUnsavedChanges(): boolean;
}

export const unsavedChangesGuard: CanDeactivateFn<HasUnsavedChanges> = (page) => {
  if (!page.hasUnsavedChanges()) {
    return true;
  }
  return inject(Dialog)
    .open<boolean, ConfirmDialogData, ConfirmDialog>(ConfirmDialog, {
      data: {
        title: 'Discard your changes?',
        message: 'The changes on this page have not been saved.',
        confirmLabel: 'Discard',
        danger: true,
      },
    })
    .closed.pipe(map((discard) => discard === true));
};
