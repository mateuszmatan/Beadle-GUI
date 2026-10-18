import { Injectable, signal } from '@angular/core';

export const MY_BUSINESS_SERVICE_KEY = 'beadle.business-service';

function stored(): number | null {
  try {
    const value = localStorage.getItem(MY_BUSINESS_SERVICE_KEY);
    return value && /^\d+$/.test(value) ? Number(value) : null;
  } catch {
    return null;
  }
}

function store(id: number | null): void {
  try {
    if (id === null) {
      localStorage.removeItem(MY_BUSINESS_SERVICE_KEY);
    } else {
      localStorage.setItem(MY_BUSINESS_SERVICE_KEY, String(id));
    }
  } catch {
    return;
  }
}

@Injectable({ providedIn: 'root' })
export class MyBusinessService {
  private readonly id = signal(stored());

  readonly businessServiceId = this.id.asReadonly();

  choose(id: number | null): void {
    this.id.set(id);
    store(id);
  }
}
