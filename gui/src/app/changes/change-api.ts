import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

export type ChangeType = 'NORMAL' | 'STANDARD' | 'EMERGENCY';
export type ChangeRisk = 'LOW' | 'MODERATE' | 'HIGH';
export type ChangeImpact = 'LOW' | 'MEDIUM' | 'HIGH';

export interface ChangeTemplate {
  jiraProjectKey: string;
  configurationItem: string;
  assignmentGroup: string;
  type: ChangeType;
  category: string;
  risk: ChangeRisk;
  impact: ChangeImpact;
  riskAssessment: string | null;
  approvers: string[];
  description: string | null;
  implementationPlan: string;
  backoutPlan: string;
  testPlan: string;
}

export interface ChangeProfile {
  productId: number;
  productName: string;
  version: number | null;
  updatedAt: string | null;
  template: ChangeTemplate;
}

export interface JiraIssue {
  key: string;
  summary: string;
  status: string | null;
  epicKey: string | null;
  updated: string;
}

export interface ChangeTask {
  number: string | null;
  serviceName: string;
  shortDescription: string;
  description: string;
}

export interface ProductionChange {
  id: number | null;
  number: string | null;
  productId: number | null;
  productCode: string;
  productName: string;
  departmentName: string | null;
  window: { start: string; end: string };
  shortDescription: string;
  description: string;
  template: ChangeTemplate;
  epicKeys: string[];
  storyKeys: string[];
  tasks: ChangeTask[];
  url: string | null;
  createdAt: string | null;
}

export interface ChangeRequest {
  productId: number;
  serviceIds: number[];
  epicKeys: string[];
  storyKeys: string[];
  start: string | null;
  end: string | null;
  shortDescription?: string;
  description?: string;
}

export interface ChangeIntegrations {
  jiraConnected: boolean;
  serviceNowConnected: boolean;
}

export interface DateRange {
  from: string;
  to: string;
}

export const TYPES: { value: ChangeType; label: string }[] = [
  { value: 'NORMAL', label: 'Normal' },
  { value: 'STANDARD', label: 'Standard' },
  { value: 'EMERGENCY', label: 'Emergency' },
];

export const RISKS: { value: ChangeRisk; label: string }[] = [
  { value: 'LOW', label: 'Low' },
  { value: 'MODERATE', label: 'Moderate' },
  { value: 'HIGH', label: 'High' },
];

export const IMPACTS: { value: ChangeImpact; label: string }[] = [
  { value: 'LOW', label: 'Low' },
  { value: 'MEDIUM', label: 'Medium' },
  { value: 'HIGH', label: 'High' },
];

export function labelOf<T>(options: readonly { value: T; label: string }[], value: T): string {
  return options.find((option) => option.value === value)?.label ?? String(value);
}

const range = (dates: DateRange) => new HttpParams().set('from', dates.from).set('to', dates.to);

@Injectable({ providedIn: 'root' })
export class ChangesApi {
  private readonly http = inject(HttpClient);

  list(): Observable<ProductionChange[]> {
    return this.http.get<ProductionChange[]>('/api/changes');
  }

  get(id: number): Observable<ProductionChange> {
    return this.http.get<ProductionChange>(`/api/changes/${id}`);
  }

  integrations(): Observable<ChangeIntegrations> {
    return this.http.get<ChangeIntegrations>('/api/changes/integrations');
  }

  epics(productId: number, dates: DateRange): Observable<JiraIssue[]> {
    return this.http.get<JiraIssue[]>(`/api/products/${productId}/jira/epics`, {
      params: range(dates),
    });
  }

  stories(productId: number, epicKeys: string[], dates: DateRange): Observable<JiraIssue[]> {
    return this.http.get<JiraIssue[]>(`/api/products/${productId}/jira/stories`, {
      params: range(dates).set('epics', epicKeys.join(',')),
    });
  }

  preview(request: ChangeRequest): Observable<ProductionChange> {
    return this.http.post<ProductionChange>('/api/changes/preview', request);
  }

  raise(request: ChangeRequest): Observable<ProductionChange> {
    return this.http.post<ProductionChange>('/api/changes', request);
  }

  profile(productId: number): Observable<ChangeProfile> {
    return this.http.get<ChangeProfile>(`/api/products/${productId}/change-profile`);
  }

  saveProfile(
    productId: number,
    version: number | null,
    template: ChangeTemplate,
  ): Observable<ChangeProfile> {
    return this.http.put<ChangeProfile>(`/api/products/${productId}/change-profile`, {
      version,
      template,
    });
  }
}
