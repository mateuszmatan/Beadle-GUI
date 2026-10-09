import { Overlay, OverlayRef } from '@angular/cdk/overlay';
import { ComponentPortal } from '@angular/cdk/portal';
import { Injectable, inject } from '@angular/core';
import { Toast, ToastKind } from '../ui/toast';
import { errorMessage } from './errors';

@Injectable({ providedIn: 'root' })
export class Notifier {
  private readonly overlay = inject(Overlay);
  private shown: OverlayRef | null = null;
  private timer?: ReturnType<typeof setTimeout>;

  success(message: string): void {
    this.show(message, 'info', 4000, 'OK');
  }

  info(message: string): void {
    this.show(message, 'info', 2000);
  }

  error(error: unknown): void {
    this.show(errorMessage(error), 'error', 10000, 'Close');
  }

  dismiss(): void {
    clearTimeout(this.timer);
    this.shown?.dispose();
    this.shown = null;
  }

  private show(message: string, kind: ToastKind, duration: number, action?: string): void {
    this.dismiss();
    const shown = this.overlay.create({
      positionStrategy: this.overlay.position().global().centerHorizontally().bottom('24px'),
      panelClass: 'dso-toast-pane',
    });
    const toast = shown.attach(new ComponentPortal(Toast));
    toast.setInput('message', message);
    toast.setInput('kind', kind);
    toast.setInput('action', action);
    toast.instance.closed.subscribe(() => this.dismiss());
    this.shown = shown;
    this.timer = setTimeout(() => this.dismiss(), duration);
  }
}
