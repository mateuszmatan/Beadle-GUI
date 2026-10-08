import { Injectable } from '@angular/core';
import { ProductionChange } from './change-api';

@Injectable({ providedIn: 'root' })
export class PublishedChange {
  private change: ProductionChange | null = null;

  hand(change: ProductionChange): void {
    this.change = change;
  }

  take(id: number): ProductionChange | null {
    const change = this.change?.id === id ? this.change : null;
    this.change = null;
    return change;
  }
}
