import { changeTemplate } from '../testing/change-fixtures';
import { NUMBER_PENDING, changeFacts, sectionControls, templateLabel } from './change-sections';
import { templateForm } from './change-template-model';

describe('change sections', () => {
  it('names the fields by their path', () => {
    expect(templateLabel('configurationItem')).toBe('Affected CI');
    expect(templateLabel('requestedFor')).toBe('Requested For');
    expect(templateLabel('risk')).toBe('Risk');
    expect(templateLabel('description')).toBeNull();
    expect(templateLabel('secureCodingTicket')).toBe('Secure coding ticket number');
    expect(templateLabel('approvers.l2Manager')).toBe('L2 approver');
    expect(templateLabel('timing.installationStart')).toBe('Installation start');
    expect(templateLabel('privilegedAccess.users')).toBe('Privileged accounts');
    expect(templateLabel('privilegedAccess.users[2].user')).toBe('Person');
    expect(templateLabel('privilegedAccess.users[2].account')).toBe('Privileged account');
    expect(templateLabel('riskAssessment.clientsOutsideBbh')).toBe(
      'Number of impacted clients outside BBH',
    );
    expect(templateLabel('approvers')).toBe('Approvers');
    expect(templateLabel('timing')).toBe('Schedule defaults');
    expect(templateLabel('privilegedAccess')).toBe('Privileged access');
    expect(templateLabel('riskAssessment')).toBe('Risk assessment');
    expect(templateLabel('unknown')).toBeNull();
  });

  it('states the number, approval, opener and state of a change, raised or not', () => {
    expect(
      changeFacts({ number: 'CHG0012345', state: 'PRIMARY_APPROVAL', openedBy: 'Grace Turner' }),
    ).toEqual([
      { label: 'Change number', value: 'CHG0012345', placeholder: NUMBER_PENDING, mono: true },
      { label: 'Approval', value: 'Requested' },
      { label: 'Opened By', value: 'Grace Turner' },
      { label: 'State', value: 'Primary Approval' },
    ]);
    expect(
      changeFacts({ number: null, state: 'DRAFT', openedBy: null }).map((fact) => fact.value),
    ).toEqual([null, 'Not Yet Requested', null, 'Draft']);
  });

  it('gives the controls each section shows', () => {
    const form = templateForm(changeTemplate());

    expect(sectionControls(form, 'request')).toContain(form.controls.usersAffected);
    expect(sectionControls(form, 'request')).not.toContain(form.controls.jiraProjectKey);
    expect(sectionControls(form, 'approvals')).toEqual([form.controls.approvers]);
    expect(sectionControls(form, 'schedule')).toEqual([
      form.controls.downtime,
      form.controls.timing,
    ]);
    expect(sectionControls(form, 'secure')).toEqual([form.controls.secureCodingTicket]);
  });
});
