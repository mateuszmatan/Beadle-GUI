import { errorText } from '../shared/form-errors';
import { changeOptions, changeTemplate } from '../testing/change-fixtures';
import { RiskAssessment } from './change-api';
import {
  MAX_PRIVILEGED_USERS,
  applyTemplateProblems,
  riskOf,
  templateForm,
  toTemplate,
  withDefaults,
} from './change-template-model';

const NOTHING: RiskAssessment = {
  bbhWorkgroups: null,
  changeComplexity: null,
  bbhUsers: null,
  validationComplexity: null,
  bbhApplications: null,
  backoutTesting: null,
  clientsOutsideBbh: null,
  platformStatus: null,
  businessImpact: null,
};

describe('change template model', () => {
  it('gives the template it was built from back', () => {
    const template = changeTemplate({
      requestedFor: 'Grace Turner',
      requestedBy: 'Mateusz Matan',
      department: 'Corporate Technology',
      assignedTo: 'James Carter',
      type: 'EMERGENCY',
      release: 'CERT 4.2',
      incident: 'INC0012345',
      directBusinessService: 'Certificate Management',
      usersAffected: 'Operations',
      privilegedAccess: {
        required: true,
        users: [{ user: 'Jane Smith', account: 'adm_jsmith' }],
      },
      secureCodingTicket: 'SEC-12',
    });

    expect(toTemplate(templateForm(template))).toEqual(template);
  });

  it('trims the texts, sends blank optional ones as null and upper-cases the Jira project', () => {
    const form = templateForm(changeTemplate());
    form.patchValue({
      jiraProjectKey: ' cert ',
      requestedFor: ' Grace Turner ',
      assignmentGroup: ' Technology Architecture ',
      release: '  ',
      affectedClients: ' Funds ',
      secureCodingTicket: ' ',
      approvers: { l1Manager: ' ', businessApprover: ' Ann Lee ' },
      planning: { backoutPlan: ' Redeploy. ' },
      riskAssessment: { bbhUsers: null, platformStatus: 'New' },
    });

    expect(form.valid).toBe(true);
    const template = toTemplate(form);
    expect(template).toMatchObject({
      jiraProjectKey: 'CERT',
      requestedFor: 'Grace Turner',
      assignmentGroup: 'Technology Architecture',
      release: null,
      affectedClients: 'Funds',
      secureCodingTicket: null,
      risk: null,
      approvers: { l1Manager: null, l2Manager: 'James Carter', businessApprover: 'Ann Lee' },
    });
    expect(template.planning.backoutPlan).toBe('Redeploy.');
    expect(template.riskAssessment).toMatchObject({ bbhUsers: null, platformStatus: 'New' });
  });

  it('checks the values as the portal does', () => {
    const form = templateForm(changeTemplate());
    const c = form.controls;
    const invalid = () =>
      [
        c.jiraProjectKey,
        c.requestedFor,
        c.assignmentGroup,
        c.category,
        c.incident,
        c.timing.controls.installationStart,
        c.timing.controls.installationHours,
        c.timing.controls.validationHours,
        c.planning.controls.firstUsePlan,
        c.secureCodingTicket,
      ]
        .filter((control) => control.invalid)
        .map((control) => Object.keys(control.errors!)[0]);

    form.patchValue({
      jiraProjectKey: 'CERT-1',
      requestedFor: 'R'.repeat(201),
      assignmentGroup: '  ',
      category: '',
      incident: 'I'.repeat(41),
      timing: { installationStart: '25:00', installationHours: 0, validationHours: 73 },
      planning: { firstUsePlan: '' },
      secureCodingTicket: 'S'.repeat(41),
    });
    expect(invalid()).toEqual([
      'pattern',
      'bytes',
      'required',
      'required',
      'bytes',
      'pattern',
      'min',
      'max',
      'required',
      'bytes',
    ]);

    form.patchValue({
      jiraProjectKey: 'A',
      requestedFor: '',
      assignmentGroup: 'Ops',
      category: 'Network',
      incident: '',
      timing: { installationStart: '23:59', installationHours: 72, validationHours: 0 },
      planning: { firstUsePlan: 'Confirmed.' },
      secureCodingTicket: '',
    });
    expect(invalid()).toEqual([]);
    c.timing.controls.installationHours.setValue(null);
    expect(c.timing.controls.installationHours.hasError('required')).toBe(true);
  });

  it('counts the length of a text in bytes as the portal does', () => {
    const form = templateForm(changeTemplate());
    const c = form.controls;
    const access = c.privilegedAccess.controls;
    access.count.setValue(1);
    const account = access.users.at(0).controls.account;

    form.patchValue({
      requestedFor: 'é'.repeat(101),
      incident: ` ${'€'.repeat(13)}  `,
      planning: { backoutPlan: 'ü'.repeat(1001) },
      approvers: { l1Manager: 'ł'.repeat(100) },
    });
    account.setValue('ø'.repeat(101));

    expect(c.requestedFor.errors).toEqual({ bytes: { max: 200 } });
    expect(errorText(c.requestedFor)).toBe(
      'Too long: at most 200 characters, and accented letters and symbols count as two or three',
    );
    expect(c.incident.valid).toBe(true);
    expect(c.planning.controls.backoutPlan.hasError('bytes')).toBe(true);
    expect(c.approvers.controls.l1Manager.valid).toBe(true);
    expect(account.hasError('bytes')).toBe(true);
    c.incident.setValue('€'.repeat(14));
    expect(c.incident.hasError('bytes')).toBe(true);
  });

  it('asks for as many privileged accounts as chosen', () => {
    const form = templateForm(changeTemplate());
    const access = form.controls.privilegedAccess.controls;

    expect(access.count.value).toBe(0);
    expect(access.users.length).toBe(0);
    access.count.setValue(2);
    expect(access.users.length).toBe(2);
    expect(access.users.dirty).toBe(true);
    expect(form.valid).toBe(false);

    access.users.setValue([
      { user: ' Jane Smith ', account: 'adm_jsmith' },
      { user: 'Tom Brown', account: 'adm_tbrown' },
    ]);
    expect(form.valid).toBe(true);
    expect(toTemplate(form).privilegedAccess).toEqual({
      required: true,
      users: [
        { user: 'Jane Smith', account: 'adm_jsmith' },
        { user: 'Tom Brown', account: 'adm_tbrown' },
      ],
    });

    access.count.setValue(MAX_PRIVILEGED_USERS + 2);
    expect(access.users.length).toBe(MAX_PRIVILEGED_USERS);
    access.count.setValue(1);
    expect(access.users.getRawValue()).toEqual([{ user: ' Jane Smith ', account: 'adm_jsmith' }]);

    access.count.setValue(0);
    expect(form.valid).toBe(true);
    expect(toTemplate(form).privilegedAccess).toEqual({ required: false, users: [] });
  });

  it('counts the stored privileged accounts only while privileged access is required', () => {
    const users = [{ user: 'Jane Smith', account: 'adm_jsmith' }];

    expect(
      templateForm(changeTemplate({ privilegedAccess: { required: true, users } })).controls
        .privilegedAccess.controls.count.value,
    ).toBe(1);
    expect(
      templateForm(changeTemplate({ privilegedAccess: { required: false, users } })).controls
        .privilegedAccess.controls.users.length,
    ).toBe(0);
  });

  it('clears the direct business service when the affected CI is typed by hand', () => {
    const form = templateForm(changeTemplate({ directBusinessService: 'Certificate Management' }));
    const { configurationItem, directBusinessService } = form.controls;

    expect(directBusinessService.value).toBe('Certificate Management');
    configurationItem.setValue('CertScanner');
    expect(directBusinessService.value).toBe('Certificate Management');
    configurationItem.setValue('CertScanne');
    expect(directBusinessService.value).toBe('');
  });

  it('fills the empty request fields with the user who opens the change and the department', () => {
    expect(withDefaults(changeTemplate(), 'Mateusz Matan', 'Corporate Technology')).toMatchObject({
      requestedFor: 'Mateusz Matan',
      requestedBy: 'Mateusz Matan',
      assignedTo: 'Mateusz Matan',
      department: 'Corporate Technology',
    });
    expect(
      withDefaults(
        changeTemplate({
          requestedFor: 'Grace Turner',
          requestedBy: 'James Carter',
          assignedTo: 'Olivia Bennett',
          department: 'Operations',
        }),
        'Mateusz Matan',
        'Corporate Technology',
      ),
    ).toMatchObject({
      requestedFor: 'Grace Turner',
      requestedBy: 'James Carter',
      assignedTo: 'Olivia Bennett',
      department: 'Operations',
    });
    expect(withDefaults(changeTemplate(), null, null).requestedFor).toBeNull();
  });

  it('computes the risk from the answers', () => {
    const lists = changeOptions().risk;

    expect(riskOf(NOTHING, lists)).toBeNull();
    expect(riskOf({ ...NOTHING, bbhUsers: 'Unknown' }, lists)).toBeNull();
    expect(riskOf({ ...NOTHING, bbhWorkgroups: 'Single', bbhUsers: 'Less than 5' }, lists)).toBe(
      'Low',
    );
    expect(riskOf(changeTemplate().riskAssessment, lists)).toBe('Moderate');
    expect(riskOf({ ...NOTHING, bbhWorkgroups: 'Single', bbhUsers: 'All users' }, lists)).toBe(
      'High',
    );
    expect(riskOf({ ...NOTHING, platformStatus: 'Decommissioned' }, lists)).toBe('High');
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
      { field: 'template.riskAssessment.bbhUsers', message: 'is not one of the options' },
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
      server: 'is not one of the options',
    });
    expect(left).toEqual([
      { field: 'schedule.installationEnd', message: 'must be after the start' },
      { field: 'template.unknown', message: 'is odd' },
    ]);
  });
});
