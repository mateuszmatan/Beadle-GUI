import { DEFAULT_DIALOG_CONFIG, DialogConfig, DialogRef } from '@angular/cdk/dialog';
import { Directive, OnDestroy, OnInit, Provider, inject, input } from '@angular/core';
import { uniqueId } from './form-field';

@Directive({
  selector: '[beadleDialogClose]',
  host: { '(click)': 'ref?.close(result())' },
})
export class DialogClose {
  readonly result = input<unknown>(undefined, { alias: 'beadleDialogClose' });

  protected readonly ref = inject(DialogRef, { optional: true });
}

@Directive({
  selector: '[beadleDialogTitle]',
  host: { class: 'modal-title', '[id]': 'id' },
})
export class DialogTitle implements OnInit, OnDestroy {
  protected readonly id = uniqueId('beadle-dialog-title');
  private readonly container = inject(DialogRef, { optional: true })?.containerInstance as
    { _addAriaLabelledBy?(id: string): void; _removeAriaLabelledBy?(id: string): void } | undefined;

  ngOnInit(): void {
    Promise.resolve().then(() => this.container?._addAriaLabelledBy?.(this.id));
  }

  ngOnDestroy(): void {
    Promise.resolve().then(() => this.container?._removeAriaLabelledBy?.(this.id));
  }
}

export const DIALOG = [DialogClose, DialogTitle] as const;

export function provideDialogs(): Provider {
  return {
    provide: DEFAULT_DIALOG_CONFIG,
    useValue: {
      ...new DialogConfig(),
      panelClass: 'beadle-dialog',
      maxWidth: '92vw',
      maxHeight: '90vh',
      autoFocus: 'first-tabbable',
      restoreFocus: true,
    } satisfies DialogConfig,
  };
}
