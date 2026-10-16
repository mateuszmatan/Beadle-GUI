import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { Action, ActionHistoryPage, ActionHistoryQuery } from './action-history-model';

const PATH = '/api/action-history';

@Injectable({ providedIn: 'root' })
export class ActionHistoryApi {
  private readonly http = inject(HttpClient);

  find(query: ActionHistoryQuery): Observable<ActionHistoryPage> {
    const params = Object.fromEntries(
      Object.entries(query).filter(([, value]) => value !== undefined && value !== ''),
    ) as Record<string, string | number>;
    return this.http.get<ActionHistoryPage>(PATH, { params });
  }

  users(): Observable<string[]> {
    return this.http.get<string[]>(`${PATH}/users`);
  }

  actions(): Observable<Action[]> {
    return this.http.get<Action[]>(`${PATH}/actions`);
  }
}
