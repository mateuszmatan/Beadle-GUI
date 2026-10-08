import { Injectable, signal } from '@angular/core';

export const MY_DEPARTMENT_KEY = 'dso.beadle.department';

function stored(): number | null {
  try {
    const value = localStorage.getItem(MY_DEPARTMENT_KEY);
    return value && /^\d+$/.test(value) ? Number(value) : null;
  } catch {
    return null;
  }
}

function store(id: number | null): void {
  try {
    if (id === null) {
      localStorage.removeItem(MY_DEPARTMENT_KEY);
    } else {
      localStorage.setItem(MY_DEPARTMENT_KEY, String(id));
    }
  } catch {
    return;
  }
}

@Injectable({ providedIn: 'root' })
export class MyDepartment {
  private readonly id = signal(stored());

  readonly departmentId = this.id.asReadonly();

  choose(id: number | null): void {
    this.id.set(id);
    store(id);
  }
}
