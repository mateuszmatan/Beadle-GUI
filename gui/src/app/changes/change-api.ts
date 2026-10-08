import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

export type ChangeType = 'NORMAL' | 'STANDARD' | 'EMERGENCY';

export type ChangeState =
  | 'DRAFT'
  | 'BUSINESS_APPROVAL'
  | 'PRIMARY_APPROVAL'
  | 'SECONDARY_APPROVAL'
  | 'CTASK_APPROVAL'
  | 'ESCALATED_APPROVAL'
  | 'IMPLEMENTATION'
  | 'CLOSED';

export type TaskState = 'OPEN' | 'WORK_IN_PROGRESS' | 'CLOSED' | 'CANCELED';

export type UpdateStatus = 'PENDING' | 'APPLIED' | 'NOT_APPLIED';

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
  tasks: TaskText[];
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

export interface TaskText {
  shortDescription: string;
  description: string;
}

export interface ChangeTask extends TaskText {
  number: string | null;
  state: TaskState;
}

export interface WorkflowStep {
  state: ChangeState;
  enteredAt: string;
}

export interface ChangeUpdate {
  status: UpdateStatus;
  requestedAt: string;
  departmentName: string | null;
  fields: string[];
  message: string | null;
  checkedAt: string | null;
}

export interface ProductionChange {
  id: number | null;
  number: string | null;
  productId: number | null;
  productCode: string;
  productName: string;
  departmentId: number | null;
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
  state: ChangeState;
  workflow: WorkflowStep[];
  syncedAt: string | null;
  syncProblem: string | null;
  update: ChangeUpdate | null;
  version: number | null;
  createdAt: string | null;
}

export interface ChangeRequest {
  productId: number;
  fixVersion: string;
  epicKeys: string[];
  storyKeys: string[];
  schedule: ChangeSchedule;
  template: ChangeTemplate;
  tasks: TaskText[];
  shortDescription?: string;
  description?: string;
}

export interface EditedTask extends TaskText {
  number: string | null;
}

export interface ChangeEditRequest {
  version: number;
  departmentId: number;
  shortDescription: string;
  description: string;
  schedule: ChangeSchedule;
  template: ChangeTemplate;
  tasks: EditedTask[];
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

export const STATES: { value: ChangeState; label: string }[] = [
  { value: 'DRAFT', label: 'Draft' },
  { value: 'BUSINESS_APPROVAL', label: 'Business Approval' },
  { value: 'PRIMARY_APPROVAL', label: 'Primary Approval' },
  { value: 'SECONDARY_APPROVAL', label: 'Secondary Approval' },
  { value: 'CTASK_APPROVAL', label: 'CTask approval' },
  { value: 'ESCALATED_APPROVAL', label: 'Escalated approval' },
  { value: 'IMPLEMENTATION', label: 'Implementation' },
  { value: 'CLOSED', label: 'Closed' },
];

export const TASK_STATES: { value: TaskState; label: string }[] = [
  { value: 'OPEN', label: 'Open' },
  { value: 'WORK_IN_PROGRESS', label: 'Work in progress' },
  { value: 'CLOSED', label: 'Closed' },
  { value: 'CANCELED', label: 'Canceled' },
];

export const isOpen = (change: Pick<ProductionChange, 'state'>) => change.state !== 'CLOSED';

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

  list(departmentId?: number): Observable<ProductionChange[]> {
    const params =
      departmentId === undefined
        ? undefined
        : new HttpParams().set('departmentId', String(departmentId));
    return this.http.get<ProductionChange[]>('/api/changes', { params });
  }

  get(id: number): Observable<ProductionChange> {
    return this.http.get<ProductionChange>(`/api/changes/${id}`);
  }

  update(id: number, request: ChangeEditRequest): Observable<ProductionChange> {
    return this.http.put<ProductionChange>(`/api/changes/${id}`, request);
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
    tasks: TaskText[],
  ): Observable<ChangeProfile> {
    return this.http.put<ChangeProfile>(`/api/products/${productId}/change-profile`, {
      version,
      template,
      tasks,
    });
  }
}
