import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

export type ChangeType = 'NORMAL' | 'STANDARD' | 'EMERGENCY';

export interface ChangeApprovers {
  l1Manager: string | null;
  l2Manager: string | null;
  businessApprover: string | null;
}

export interface ChangeTiming {
  installationStart: string;
  installationHours: number;
  validationHours: number;
}

export interface ChangePlanning {
  testSummary: string;
  implementationPlan: string;
  validationPlan: string;
  backoutPlan: string;
  firstUsePlan: string;
}

export interface PrivilegedUser {
  user: string;
  account: string;
}

export interface PrivilegedAccess {
  required: boolean;
  users: PrivilegedUser[];
}

export interface RiskAssessment {
  bbhWorkgroups: number | null;
  bbhUsers: number | null;
  bbhApplications: number | null;
  clients: number | null;
  clientsOutsideBbh: number | null;
  businessImpact: string | null;
  changeComplexity: string | null;
  validationComplexity: string | null;
  backoutTesting: string | null;
  platformStatus: string | null;
}

export interface ChangeTemplate {
  jiraProjectKey: string;
  assignmentGroup: string;
  category: string;
  type: ChangeType;
  configurationItem: string;
  release: string | null;
  incident: string | null;
  problem: string | null;
  affectedClients: string | null;
  description: string | null;
  approvers: ChangeApprovers;
  downtime: boolean;
  timing: ChangeTiming;
  planning: ChangePlanning;
  privilegedAccess: PrivilegedAccess;
  riskAssessment: RiskAssessment;
}

export interface ChangeProfile {
  productId: number;
  productName: string;
  version: number | null;
  updatedAt: string | null;
  template: ChangeTemplate;
}

export interface ChangeProfileSummary {
  productId: number;
  productName: string;
  version: number;
  updatedAt: string;
}

export interface JiraVersion {
  name: string;
  released: boolean;
  releaseDate: string | null;
}

export interface JiraIssue {
  key: string;
  summary: string;
  status: string | null;
  epicKey: string | null;
  updated: string;
}

export interface ChangeSchedule {
  installationStart: string;
  installationEnd: string;
  validationStart: string;
  validationEnd: string;
  firstUsage: string;
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
  fixVersion: string;
  schedule: ChangeSchedule;
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
  fixVersion: string;
  epicKeys: string[];
  storyKeys: string[];
  schedule: ChangeSchedule;
  template: ChangeTemplate;
  shortDescription?: string;
  description?: string;
}

export interface ChangeIntegrations {
  jiraConnected: boolean;
  serviceNowConnected: boolean;
}

export const TYPES: { value: ChangeType; label: string }[] = [
  { value: 'NORMAL', label: 'Normal' },
  { value: 'STANDARD', label: 'Standard' },
  { value: 'EMERGENCY', label: 'Emergency' },
];

const LEVELS = ['Low', 'Medium', 'High'];

export const SUGGESTIONS = {
  businessImpact: LEVELS,
  changeComplexity: LEVELS,
  validationComplexity: LEVELS,
  platformStatus: ['Existing platform', 'New platform', 'Platform upgrade'],
} satisfies Partial<Record<keyof RiskAssessment, readonly string[]>>;

export function labelOf<T>(options: readonly { value: T; label: string }[], value: T): string {
  return options.find((option) => option.value === value)?.label ?? String(value);
}

function jira(fixVersion: string | null, project?: string, epicKeys?: readonly string[]) {
  let params = new HttpParams();
  if (fixVersion !== null) {
    params = params.set('fixVersion', fixVersion);
  }
  if (epicKeys) {
    params = params.set('epics', epicKeys.join(','));
  }
  return project ? params.set('project', project) : params;
}

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

  versions(productId: number, project?: string): Observable<JiraVersion[]> {
    return this.http.get<JiraVersion[]>(`/api/products/${productId}/jira/versions`, {
      params: jira(null, project),
    });
  }

  epics(productId: number, fixVersion: string, project?: string): Observable<JiraIssue[]> {
    return this.http.get<JiraIssue[]>(`/api/products/${productId}/jira/epics`, {
      params: jira(fixVersion, project),
    });
  }

  stories(
    productId: number,
    fixVersion: string,
    epicKeys: readonly string[],
    project?: string,
  ): Observable<JiraIssue[]> {
    return this.http.get<JiraIssue[]>(`/api/products/${productId}/jira/stories`, {
      params: jira(fixVersion, project, epicKeys),
    });
  }

  preview(request: ChangeRequest): Observable<ProductionChange> {
    return this.http.post<ProductionChange>('/api/changes/preview', request);
  }

  raise(request: ChangeRequest): Observable<ProductionChange> {
    return this.http.post<ProductionChange>('/api/changes', request);
  }

  profiles(): Observable<ChangeProfileSummary[]> {
    return this.http.get<ChangeProfileSummary[]>('/api/change-profiles');
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
