import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, map } from 'rxjs';
import { SIGN_OUT_PATH } from './auth';
import {
  Department,
  DepartmentRequest,
  LookupItem,
  Product,
  ProductRequest,
  SignedInUser,
} from './models';

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
export class ProductsApi {
  private readonly http = inject(HttpClient);

  list(search = ''): Observable<Product[]> {
    const text = search.trim();
    return this.http.get<Product[]>('/api/products', { params: text ? { search: text } : {} });
  }

  get(id: number): Observable<Product> {
    return this.http.get<Product>(`/api/products/${id}`);
  }

  create(request: ProductRequest): Observable<Product> {
    return this.http.post<Product>('/api/products', request);
  }

  update(id: number, request: ProductRequest): Observable<Product> {
    return this.http.put<Product>(`/api/products/${id}`, request);
  }

  delete(id: number): Observable<void> {
    return this.http.delete<void>(`/api/products/${id}`);
  }

  suggestCode(name: string): Observable<string> {
    return this.http
      .get<{ code: string }>('/api/products/code-suggestion', { params: { name } })
      .pipe(map((suggestion) => suggestion.code));
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

@Injectable({ providedIn: 'root' })
export class UserApi {
  private readonly http = inject(HttpClient);

  me(): Observable<SignedInUser> {
    return this.http.get<SignedInUser>('/api/me');
  }

  /** Ends the session at Beadle; Spring Security answers 204 and the CSRF interceptor sends the token. */
  signOut(): Observable<void> {
    return this.http.post<void>(SIGN_OUT_PATH, null);
  }
}
