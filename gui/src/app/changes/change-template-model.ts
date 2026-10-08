import { FormArray, FormControl, FormGroup, ValidatorFn, Validators } from '@angular/forms';
import { FieldProblem } from '../core/models';
import {
  INT_MAX,
  addItem,
  applyProblemsAt,
  filled,
  flag,
  integer,
  max,
  optional,
  sent,
  setEnabled,
  text,
} from '../shared/form-controls';
import { ChangeTemplate, ChangeType, PrivilegedUser } from './change-api';

export const JIRA_KEY = /^\s*[A-Za-z][A-Za-z0-9_]{0,9}\s*$/;
export const JIRA_KEY_ERROR = '1 to 10 letters, digits or _, starting with a letter';
export const TIME = /^([01]\d|2[0-3]):[0-5]\d$/;
export const TIME_ERROR = 'A time like 18:00';
export const MAX_PRIVILEGED_USERS = 7;
export const TEMPLATE_PREFIX = 'template.';

const required = (value: string | null | undefined, length: number) =>
  text(value, filled, max(length));

const count = (value: number | null) => integer(value, 0, INT_MAX);

const someUsers: ValidatorFn = (users) =>
  (users as FormArray).length ? null : { rule: 'Add at least one user' };

export function privilegedUserForm(user?: PrivilegedUser) {
  return new FormGroup({
    user: required(user?.user, 200),
    account: required(user?.account, 200),
  });
}

export type PrivilegedUserForm = ReturnType<typeof privilegedUserForm>;

export function templateForm(template: ChangeTemplate) {
  const { approvers, timing, planning, privilegedAccess, riskAssessment: risk } = template;
  const form = new FormGroup({
    jiraProjectKey: text(template.jiraProjectKey, filled, Validators.pattern(JIRA_KEY)),
    assignmentGroup: required(template.assignmentGroup, 200),
    category: required(template.category, 100),
    type: new FormControl<ChangeType>(template.type, { nonNullable: true }),
    configurationItem: required(template.configurationItem, 200),
    release: text(template.release, max(100)),
    incident: text(template.incident, max(40)),
    problem: text(template.problem, max(40)),
    affectedClients: text(template.affectedClients, max(2000)),
    description: text(template.description, max(2000)),
    approvers: new FormGroup({
      l1Manager: text(approvers.l1Manager, max(200)),
      l2Manager: text(approvers.l2Manager, max(200)),
      businessApprover: text(approvers.businessApprover, max(200)),
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
      required: flag(privilegedAccess.required),
      users: new FormArray(privilegedAccess.users.map(privilegedUserForm), [someUsers]),
    }),
    riskAssessment: new FormGroup({
      bbhWorkgroups: count(risk.bbhWorkgroups),
      bbhUsers: count(risk.bbhUsers),
      bbhApplications: count(risk.bbhApplications),
      clients: count(risk.clients),
      clientsOutsideBbh: count(risk.clientsOutsideBbh),
      businessImpact: text(risk.businessImpact, max(100)),
      changeComplexity: text(risk.changeComplexity, max(100)),
      validationComplexity: text(risk.validationComplexity, max(100)),
      backoutTesting: text(risk.backoutTesting, max(2000)),
      platformStatus: text(risk.platformStatus, max(100)),
    }),
  });
  const access = form.controls.privilegedAccess.controls;
  const follow = (on: boolean) => {
    if (on && !access.users.length) {
      access.users.push(privilegedUserForm(), { emitEvent: false });
    }
    setEnabled(access.users, on);
  };
  access.required.valueChanges.subscribe(follow);
  follow(access.required.value);
  return form;
}

export type TemplateForm = ReturnType<typeof templateForm>;

export function addPrivilegedUser(form: TemplateForm): void {
  const users = form.controls.privilegedAccess.controls.users;
  if (users.length < MAX_PRIVILEGED_USERS) {
    addItem(users, privilegedUserForm());
  }
}

export function toTemplate(form: TemplateForm): ChangeTemplate {
  const value = form.getRawValue();
  const access = value.privilegedAccess;
  return {
    jiraProjectKey: value.jiraProjectKey.trim().toUpperCase(),
    assignmentGroup: value.assignmentGroup.trim(),
    category: value.category.trim(),
    type: value.type,
    configurationItem: value.configurationItem.trim(),
    release: optional(value.release),
    incident: optional(value.incident),
    problem: optional(value.problem),
    affectedClients: optional(value.affectedClients),
    description: optional(value.description),
    approvers: sent(value.approvers),
    downtime: value.downtime,
    timing: {
      installationStart: value.timing.installationStart,
      installationHours: value.timing.installationHours ?? 0,
      validationHours: value.timing.validationHours ?? 0,
    },
    planning: sent(value.planning),
    privilegedAccess: {
      required: access.required,
      users: access.required
        ? access.users.map((user) => ({ user: user.user.trim(), account: user.account.trim() }))
        : [],
    },
    riskAssessment: sent(value.riskAssessment),
  };
}

export function applyTemplateProblems(
  form: TemplateForm,
  problems: FieldProblem[],
): FieldProblem[] {
  return applyProblemsAt(form, TEMPLATE_PREFIX, problems);
}
