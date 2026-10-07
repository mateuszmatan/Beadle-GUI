import { changeTemplate } from '../testing/change-fixtures';
import {
  MAX_PRIVILEGED_USERS,
  addPrivilegedUser,
  applyTemplateProblems,
  templateForm,
  toTemplate,
} from './change-template-model';

describe('change template model', () => {
  it('gives the template it was built from back', () => {
    const template = changeTemplate({
      release: 'CERT 4.2',
      incident: 'INC0012345',
      privilegedAccess: {
        required: true,
        users: [{ user: 'Jane Smith', account: 'adm_jsmith' }],
      },
    });

    expect(toTemplate(templateForm(template))).toEqual(template);
  });

  it('trims the texts, sends blank optional ones as null and upper-cases the Jira project', () => {
    const form = templateForm(changeTemplate());
    form.patchValue({
      jiraProjectKey: ' cert ',
      assignmentGroup: ' Technology Architecture ',
      release: '  ',
      affectedClients: ' Funds ',
      approvers: { l1Manager: ' ', businessApprover: ' Ann Lee ' },
      planning: { backoutPlan: ' Redeploy. ' },
      riskAssessment: { bbhUsers: null, businessImpact: ' ', platformStatus: 'New platform ' },
    });

    expect(form.valid).toBe(true);
    const template = toTemplate(form);
    expect(template).toMatchObject({
      jiraProjectKey: 'CERT',
      assignmentGroup: 'Technology Architecture',
      release: null,
      affectedClients: 'Funds',
      approvers: { l1Manager: null, l2Manager: 'James Carter', businessApprover: 'Ann Lee' },
    });
    expect(template.planning.backoutPlan).toBe('Redeploy.');
    expect(template.riskAssessment).toMatchObject({
      bbhUsers: null,
      businessImpact: null,
      platformStatus: 'New platform',
    });
  });

  it('checks the values as the portal does', () => {
    const form = templateForm(changeTemplate());
    const c = form.controls;
    const invalid = () =>
      [
        c.jiraProjectKey,
        c.assignmentGroup,
        c.incident,
        c.timing.controls.installationStart,
        c.timing.controls.installationHours,
        c.timing.controls.validationHours,
        c.planning.controls.firstUsePlan,
        c.riskAssessment.controls.bbhWorkgroups,
        c.riskAssessment.controls.clients,
      ]
        .filter((control) => control.invalid)
        .map((control) => Object.keys(control.errors!)[0]);

    form.patchValue({
      jiraProjectKey: 'CERT-1',
      assignmentGroup: '  ',
      incident: 'I'.repeat(41),
      timing: { installationStart: '25:00', installationHours: 0, validationHours: 73 },
      planning: { firstUsePlan: '' },
      riskAssessment: { bbhWorkgroups: -1, clients: 1.5 },
    });
    expect(invalid()).toEqual([
      'pattern',
      'required',
      'maxlength',
      'pattern',
      'min',
      'max',
      'required',
      'min',
      'integer',
    ]);

    form.patchValue({
      jiraProjectKey: 'A',
      assignmentGroup: 'Ops',
      incident: '',
      timing: { installationStart: '23:59', installationHours: 72, validationHours: 0 },
      planning: { firstUsePlan: 'Confirmed.' },
      riskAssessment: { bbhWorkgroups: 0, clients: null },
    });
    expect(invalid()).toEqual([]);
    c.timing.controls.installationHours.setValue(null);
    expect(c.timing.controls.installationHours.hasError('required')).toBe(true);
  });

  it('asks for the privileged users only while privileged access is needed', () => {
    const form = templateForm(changeTemplate());
    const access = form.controls.privilegedAccess.controls;

    expect(access.users.disabled).toBe(true);
    access.required.setValue(true);
    expect(access.users.length).toBe(1);
    expect(access.users.enabled).toBe(true);
    expect(form.valid).toBe(false);

    access.users.at(0).setValue({ user: 'Jane Smith', account: 'adm_jsmith' });
    expect(form.valid).toBe(true);
    access.users.removeAt(0);
    expect(access.users.errors).toEqual({ rule: 'Add at least one user' });

    for (let i = 0; i < MAX_PRIVILEGED_USERS + 2; i++) {
      addPrivilegedUser(form);
    }
    expect(access.users.length).toBe(MAX_PRIVILEGED_USERS);
    expect(access.users.dirty).toBe(true);

    access.required.setValue(false);
    expect(access.users.disabled).toBe(true);
    expect(form.valid).toBe(true);
    expect(toTemplate(form).privilegedAccess).toEqual({ required: false, users: [] });
  });

  it('marks the fields the portal refused and hands back the problems of other fields', () => {
    const form = templateForm(
      changeTemplate({
        privilegedAccess: {
          required: true,
          users: [
            { user: 'Jane Smith', account: 'adm_jsmith' },
            { user: 'Tom Brown', account: 'adm_tbrown' },
          ],
        },
      }),
    );

    const left = applyTemplateProblems(form, [
      { field: 'template.planning.backoutPlan', message: 'must not be blank' },
      { field: 'template.privilegedAccess.users[1].account', message: 'is not an admin account' },
      { field: 'template.riskAssessment.bbhUsers', message: 'must be at least 0' },
      { field: 'template.unknown', message: 'is odd' },
      { field: 'schedule.installationEnd', message: 'must be after the start' },
    ]);

    expect(form.controls.planning.controls.backoutPlan.errors).toEqual({
      server: 'must not be blank',
    });
    expect(form.controls.privilegedAccess.controls.users.at(1).controls.account.errors).toEqual({
      server: 'is not an admin account',
    });
    expect(form.controls.riskAssessment.controls.bbhUsers.errors).toEqual({
      server: 'must be at least 0',
    });
    expect(left).toEqual([
      { field: 'schedule.installationEnd', message: 'must be after the start' },
      { field: 'template.unknown', message: 'is odd' },
    ]);
  });
});
