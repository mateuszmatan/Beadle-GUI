import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

export type ChangeType = 'STANDARD' | 'EMERGENCY' | 'BUSINESS_CRITICAL' | 'MODEL';

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
  businessApprover: string | null;
  l1Manager: string | null;
  l2Manager: string | null;
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
  bbhWorkgroups: string | null;
  changeComplexity: string | null;
  bbhUsers: string | null;
  validationComplexity: string | null;
  bbhApplications: string | null;
  backoutTesting: string | null;
  clientsOutsideBbh: string | null;
  platformStatus: string | null;
  businessImpact: string | null;
}

export type RiskQuestion = keyof RiskAssessment;

export interface SecureCoding {
  apoNumber: string | null;
  bitbucketUrl: string | null;
  artifactLink: string | null;
  qcApplicationLink: string | null;
}

export interface ChangeTemplate {
  jiraProjectKey: string;
  requestedFor: string | null;
  requestedBy: string | null;
  department: string | null;
  assignmentGroup: string;
  category: string;
  assignedTo: string | null;
  type: ChangeType;
  release: string | null;
  configurationItem: string;
  incident: string | null;
  directBusinessService: string | null;
  problem: string | null;
  risk: string | null;
  affectedClients: string | null;
  usersAffected: string | null;
  approvers: ChangeApprovers;
  downtime: boolean;
  timing: ChangeTiming;
  planning: ChangePlanning;
  privilegedAccess: PrivilegedAccess;
  riskAssessment: RiskAssessment;
  secureCodingTicket: string | null;
  secureCoding: SecureCoding;
}

export interface TypeOption {
  value: ChangeType;
  label: string;
}

export interface ChangeOptions {
  categories: string[];
  types: TypeOption[];
  risk: Record<RiskQuestion, string[]>;
  platforms: string[];
  importances: string[];
  releaseManagement: string;
}

export interface ChangeProfile {
  productId: number;
  productName: string;
  version: number | null;
  updatedAt: string | null;
  template: ChangeTemplate;
  tasks: TaskDetails[];
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
  downtimeStart: string | null;
  downtimeEnd: string | null;
}

export interface TaskDetails {
  assignmentGroup: string;
  assignedTo: string | null;
  configurationItem: string | null;
  platform: string | null;
  application: string | null;
  packages: string | null;
  backoutPackages: string | null;
  importance: string | null;
  shortDescription: string;
  description: string;
  additionalComments: string | null;
}

export interface TaskRequest {
  number: string | null;
  details: TaskDetails;
  start: string | null;
}

export interface ChangeTask extends TaskRequest {
  approval: string;
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
  openedBy: string | null;
}

export interface ChangeRequest {
  productId: number;
  fixVersion: string;
  epicKeys: string[];
  storyKeys: string[];
  schedule: ChangeSchedule;
  template: ChangeTemplate;
  shortDescription?: string;
  description?: string;
}

export interface ChangeEditRequest {
  version: number;
  departmentId: number;
  shortDescription: string;
  description: string;
  schedule: ChangeSchedule;
  template: ChangeTemplate;
  tasks: TaskRequest[];
}

export interface ChangeTasksRequest {
  version: number;
  departmentId: number;
  tasks: TaskRequest[];
}

export interface SecureCodingRequest {
  version: number;
  departmentId: number;
  apoNumber: string;
  implementationDate: string;
  bitbucketUrl: string;
  artifactLink: string;
  qcApplicationLink: string;
}

export interface ChangeIntegrations {
  jiraConnected: boolean;
  serviceNowConnected: boolean;
  cyberTrackConnected: boolean;
}

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

export function approvalOf(state: ChangeState): string {
  switch (state) {
    case 'DRAFT':
      return 'Not Yet Requested';
    case 'IMPLEMENTATION':
    case 'CLOSED':
      return 'Approved';
    default:
      return 'Requested';
  }
}

export function labelOf<T>(options: readonly { value: T; label: string }[], value: T): string {
  return options.find((option) => option.value === value)?.label ?? String(value);
}

function jira(fixVersion: string, epicKeys?: readonly string[]) {
  const params = new HttpParams().set('fixVersion', fixVersion);
  return epicKeys ? params.set('epics', epicKeys.join(',')) : params;
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

  createTasks(id: number, request: ChangeTasksRequest): Observable<ProductionChange> {
    return this.http.post<ProductionChange>(`/api/changes/${id}/tasks`, request);
  }

  createSecureCodingTicket(id: number, request: SecureCodingRequest): Observable<ProductionChange> {
    return this.http.post<ProductionChange>(`/api/changes/${id}/secure-coding`, request);
  }

  integrations(): Observable<ChangeIntegrations> {
    return this.http.get<ChangeIntegrations>('/api/changes/integrations');
  }

  options(): Observable<ChangeOptions> {
    return this.http.get<ChangeOptions>('/api/changes/options');
  }

  versions(productId: number): Observable<JiraVersion[]> {
    return this.http.get<JiraVersion[]>(`/api/products/${productId}/jira/versions`);
  }

  epics(productId: number, fixVersion: string): Observable<JiraIssue[]> {
    return this.http.get<JiraIssue[]>(`/api/products/${productId}/jira/epics`, {
      params: jira(fixVersion),
    });
  }

  stories(
    productId: number,
    fixVersion: string,
    epicKeys: readonly string[],
  ): Observable<JiraIssue[]> {
    return this.http.get<JiraIssue[]>(`/api/products/${productId}/jira/stories`, {
      params: jira(fixVersion, epicKeys),
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
    tasks: TaskDetails[],
  ): Observable<ChangeProfile> {
    return this.http.put<ChangeProfile>(`/api/products/${productId}/change-profile`, {
      version,
      template,
      tasks,
    });
  }
}
