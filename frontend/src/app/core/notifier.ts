import { Injectable, inject } from '@angular/core';
import { MatSnackBar } from '@angular/material/snack-bar';
import { errorMessage } from './errors';

@Injectable({ providedIn: 'root' })
export class Notifier {
  private readonly snackBar = inject(MatSnackBar);

  success(message: string): void {
    this.snackBar.open(message, 'OK', { duration: 4000 });
  }

  error(error: unknown): void {
    this.snackBar.open(errorMessage(error), 'Close', { duration: 10000, panelClass: 'snack-error' });
  }
}
