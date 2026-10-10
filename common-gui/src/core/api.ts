import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { Department, DepartmentRequest, LookupItem } from './models';

@Injectable({ providedIn: 'root' })
export class DepartmentsApi {
  private readonly http = inject(HttpClient);

  list<D extends Department = Department>(): Observable<D[]> {
    return this.http.get<D[]>('/api/departments');
  }

  create(name: string): Observable<Department> {
    return this.http.post<Department>('/api/departments', { name });
  }

  rename(id: number, request: DepartmentRequest): Observable<Department> {
    return this.http.put<Department>(`/api/departments/${id}`, request);
  }

  delete(id: number): Observable<void> {
    return this.http.delete<void>(`/api/departments/${id}`);
  }
}

@Injectable({ providedIn: 'root' })
export class LookupsApi {
  private readonly http = inject(HttpClient);

  find(kind: string, query: string): Observable<LookupItem[]> {
    const text = query.trim();
    return this.http.get<LookupItem[]>(`/api/lookups/${kind}`, {
      params: text ? { q: text } : {},
    });
  }
}
