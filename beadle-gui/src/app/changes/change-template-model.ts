import { FormArray, FormControl, FormGroup, Validators } from '@angular/forms';
import { distinctUntilChanged, skip, startWith } from 'rxjs';
import {
  addItem,
  applyProblemsAt,
  filled,
  flag,
  integer,
  removeItem,
  sent,
  text,
  url,
} from '@common/shared/form-controls';
import {
  ChangeOptions,
  ChangeTemplate,
  ChangeType,
  PrivilegedUser,
  RiskAssessment,
  RiskQuestion,
} from './change-api';
import { fits } from './change-model';
import { FieldProblem } from '@common/core/models';

export const JIRA_KEY = /^\s*[A-Za-z][A-Za-z0-9_]{0,9}\s*$/;
export const JIRA_KEY_ERROR = '1 to 10 letters, digits or _, starting with a letter';
export const TIME = /^([01]\d|2[0-3]):[0-5]\d$/;
export const TIME_ERROR = 'A time like 18:00';
export const MAX_PRIVILEGED_USERS = 7;
export const TEMPLATE_PREFIX = 'template.';
export const LINK_MAX = 500;

const required = (value: string | null | undefined, length: number) =>
  text(value, filled, fits(length));

const answer = (value: string | null) => new FormControl<string | null>(value);

export function privilegedUserForm(user?: PrivilegedUser) {
  return new FormGroup({
    user: required(user?.user, 200),
    account: required(user?.account, 200),
  });
}

export type PrivilegedUserForm = ReturnType<typeof privilegedUserForm>;

export function templateForm(template: ChangeTemplate) {
  const {
    approvers,
    timing,
    planning,
    privilegedAccess,
    riskAssessment: risk,
    secureCoding,
  } = template;
  const users = privilegedAccess.required ? privilegedAccess.users : [];
  const form = new FormGroup({
    jiraProjectKey: text(template.jiraProjectKey, filled, Validators.pattern(JIRA_KEY)),
    requestedFor: text(template.requestedFor, fits(200)),
    requestedBy: text(template.requestedBy, fits(200)),
    department: text(template.department, fits(100)),
    assignmentGroup: required(template.assignmentGroup, 200),
    category: text(template.category, filled),
    assignedTo: text(template.assignedTo, fits(200)),
    type: new FormControl<ChangeType>(template.type, { nonNullable: true }),
    release: text(template.release, fits(100)),
    configurationItem: required(template.configurationItem, 200),
    incident: text(template.incident, fits(40)),
    directBusinessService: text(template.directBusinessService, fits(200)),
    problem: text(template.problem, fits(40)),
    affectedClients: text(template.affectedClients, fits(2000)),
    usersAffected: text(template.usersAffected, fits(2000)),
    approvers: new FormGroup({
      businessApprover: text(approvers.businessApprover, fits(200)),
      l1Manager: text(approvers.l1Manager, fits(200)),
      l2Manager: text(approvers.l2Manager, fits(200)),
    }),
    downtime: flag(template.downtime),
    timing: new FormGroup({
      installationStart: text(timing.installationStart, filled, Validators.pattern(TIME)),
      installationHours: integer(timing.installationHours, 1, 72, Validators.required),
      validationHours: integer(timing.validationHours, 0, 72, Validators.required),
    }),
    planning: new FormGroup({
      testSummary: required(planning.testSummary, 2000),
      implementationPlan: required(planning.implementationPlan, 2000),
      validationPlan: required(planning.validationPlan, 2000),
      backoutPlan: required(planning.backoutPlan, 2000),
      firstUsePlan: required(planning.firstUsePlan, 2000),
    }),
    privilegedAccess: new FormGroup({
      count: new FormControl(users.length, { nonNullable: true }),
      users: new FormArray(users.map(privilegedUserForm)),
    }),
    riskAssessment: new FormGroup({
      bbhWorkgroups: answer(risk.bbhWorkgroups),
      changeComplexity: answer(risk.changeComplexity),
      bbhUsers: answer(risk.bbhUsers),
      validationComplexity: answer(risk.validationComplexity),
      bbhApplications: answer(risk.bbhApplications),
      backoutTesting: answer(risk.backoutTesting),
      clientsOutsideBbh: answer(risk.clientsOutsideBbh),
      platformStatus: answer(risk.platformStatus),
      businessImpact: answer(risk.businessImpact),
    }),
    secureCodingTicket: text(template.secureCodingTicket, fits(40)),
    secureCoding: new FormGroup({
      apoNumber: text(secureCoding.apoNumber, fits(40)),
      bitbucketUrl: url(secureCoding.bitbucketUrl, LINK_MAX),
      artifactLink: url(secureCoding.artifactLink, LINK_MAX),
      qcApplicationLink: url(secureCoding.qcApplicationLink, LINK_MAX),
    }),
  });
  const access = form.controls.privilegedAccess.controls;
  access.count.valueChanges.subscribe((count) => resizeUsers(access.users, count));
  const { configurationItem, directBusinessService } = form.controls;
  configurationItem.valueChanges
    .pipe(startWith(configurationItem.value), distinctUntilChanged(), skip(1))
    .subscribe(() => directBusinessService.setValue(''));
  return form;
}

export type TemplateForm = ReturnType<typeof templateForm>;

function resizeUsers(users: FormArray<PrivilegedUserForm>, count: number): void {
  const wanted = Math.min(Math.max(count, 0), MAX_PRIVILEGED_USERS);
  while (users.length < wanted) {
    addItem(users, privilegedUserForm());
  }
  while (users.length > wanted) {
    removeItem(users, users.length - 1);
  }
}

export function toTemplate(form: TemplateForm): ChangeTemplate {
  const { approvers, timing, planning, privilegedAccess, riskAssessment, secureCoding, ...fields } =
    form.getRawValue();
  const users = privilegedAccess.users.map((user) => ({
    user: user.user.trim(),
    account: user.account.trim(),
  }));
  return {
    ...sent(fields),
    jiraProjectKey: fields.jiraProjectKey.trim().toUpperCase(),
    risk: null,
    approvers: sent(approvers),
    timing: {
      installationStart: timing.installationStart,
      installationHours: timing.installationHours ?? 0,
      validationHours: timing.validationHours ?? 0,
    },
    planning: sent(planning),
    privilegedAccess: { required: users.length > 0, users },
    riskAssessment,
    secureCoding: sent(secureCoding),
  };
}

export function withDefaults(
  template: ChangeTemplate,
  openedBy: string | null,
  department: string | null,
): ChangeTemplate {
  return {
    ...template,
    requestedFor: template.requestedFor ?? openedBy,
    requestedBy: template.requestedBy ?? openedBy,
    assignedTo: template.assignedTo ?? openedBy,
    department: template.department ?? department,
  };
}

export function riskOf(assessment: RiskAssessment, lists: ChangeOptions['risk']): string | null {
  const answers = (Object.keys(lists) as RiskQuestion[])
    .map((question) => ({
      at: lists[question].indexOf(assessment[question] ?? ''),
      last: lists[question].length - 1,
    }))
    .filter(({ at }) => at >= 0);
  if (!answers.length) {
    return null;
  }
  if (answers.some(({ at, last }) => at === last)) {
    return 'High';
  }
  return answers.some(({ at }) => at > 0) ? 'Moderate' : 'Low';
}

export function applyTemplateProblems(
  form: TemplateForm,
  problems: FieldProblem[],
): FieldProblem[] {
  return applyProblemsAt(form, TEMPLATE_PREFIX, problems);
}
