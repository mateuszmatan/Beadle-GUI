import { DEFAULT_DIALOG_CONFIG, DialogConfig, DialogRef } from '@angular/cdk/dialog';
import { Directive, OnInit, Provider, inject, input } from '@angular/core';
import { uniqueId } from './form-field';

@Directive({
  selector: '[dsoDialogClose]',
  host: { '(click)': 'ref?.close(result())' },
})
export class DialogClose {
  readonly result = input<unknown>(undefined, { alias: 'dsoDialogClose' });

  protected readonly ref = inject(DialogRef, { optional: true });
}

@Directive({
  selector: '[dsoDialogTitle]',
  host: { class: 'modal-title', '[id]': 'id' },
})
export class DialogTitle implements OnInit {
  protected readonly id = uniqueId('dso-dialog-title');
  private readonly ref = inject(DialogRef, { optional: true });

  ngOnInit(): void {
    const container = this.ref?.containerInstance as { _addAriaLabelledBy?(id: string): void };
    container?._addAriaLabelledBy?.(this.id);
  }
}

export const DIALOG = [DialogClose, DialogTitle] as const;

export function provideDialogs(): Provider {
  return {
    provide: DEFAULT_DIALOG_CONFIG,
    useValue: {
      panelClass: 'dso-dialog',
      maxWidth: '92vw',
      maxHeight: '90vh',
      autoFocus: 'first-tabbable',
      restoreFocus: true,
    } satisfies DialogConfig,
  };
}
