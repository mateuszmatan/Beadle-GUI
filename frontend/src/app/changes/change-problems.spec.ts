import { fieldLabels, problemText, requestLabel } from './change-problems';

describe('change problems', () => {
  it('names the fields of a change request', () => {
    expect(requestLabel('fixVersion')).toBe('FixVersion');
    expect(requestLabel('departmentId')).toBe('Department');
    expect(requestLabel('schedule.firstUsage')).toBe('First use');
    expect(requestLabel('schedule.downtimeEnd')).toBe('Downtime end');
    expect(requestLabel('template.approvers.l2Manager')).toBe('L2 approver');
    expect(requestLabel('template.secureCodingTicket')).toBe('Secure coding ticket number');
    expect(requestLabel('template.approvers')).toBe('Approvers');
    expect(requestLabel('template.riskAssessment')).toBe('Risk assessment');
    expect(requestLabel('tasks')).toBe('Change tasks');
    expect(requestLabel('tasks[0].shortDescription')).toBe('Change task 1: short description');
    expect(requestLabel('tasks[2].description')).toBe('Change task 3: description');
    expect(requestLabel('tasks[1]')).toBe('Change task 2');
    expect(requestLabel('tasks[1].number')).toBe('Change task 2: number');
    expect(requestLabel('tasks[1].details.assignmentGroup')).toBe(
      'Change task 2: assignment group',
    );
    expect(requestLabel('tasks[0].start')).toBe('Change task 1: task start');
    expect(requestLabel('tasks[3].details.backoutPackages')).toBe(
      'Change task 4: backout packages',
    );
    expect(requestLabel('tasks[1].odd')).toBe('Change task 2');
    expect(requestLabel('somethingElse')).toBeNull();
  });

  it('puts the field before the problem when it knows the field', () => {
    expect(problemText({ field: 'tasks', message: 'must not be empty' })).toBe(
      'Change tasks: must not be empty',
    );
    expect(problemText({ field: 'odd', message: 'is odd' })).toBe('is odd');
  });

  it('lists the fields ProTech has not applied', () => {
    expect(fieldLabels(['shortDescription', 'template.planning', 'odd'])).toBe(
      'Short description, Planning, odd',
    );
  });
});
