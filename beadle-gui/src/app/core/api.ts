import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, map } from 'rxjs';
import { Product, ProductRequest, SignedInUser } from './models';

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
export class UserApi {
  private readonly http = inject(HttpClient);

  me(): Observable<SignedInUser> {
    return this.http.get<SignedInUser>('/api/me');
  }
}
