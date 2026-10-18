import { HttpErrorResponse } from '@angular/common/http';
import {
  ATTENTION,
  NEEDS_ATTENTION,
  failureOf,
  fieldLabels,
  problemText,
  requestLabel,
} from './change-problems';

const refused = (status: number, body: object) =>
  new HttpErrorResponse({ status, statusText: 'Refused', error: body });

describe('change problems', () => {
  it('names the fields of a change request', () => {
    expect(requestLabel('fixVersion')).toBe('FixVersion');
    expect(requestLabel('businessServiceId')).toBe('Business service');
    expect(requestLabel('schedule.firstUsage')).toBe('First use');
    expect(requestLabel('schedule.validationStart')).toBe('Post-install validation start');
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

describe('failureOf', () => {
  it('leads with what could not be done, lists the field problems by their labels and offers no reload', () => {
    expect(
      failureOf(
        refused(400, {
          detail: '2 fields are invalid',
          problems: [
            { field: 'schedule.validationEnd', message: 'must be after the start' },
            { field: 'tasks[0].details.shortDescription', message: 'is used twice' },
          ],
        }),
        'The update could not be published',
      ),
    ).toEqual({
      message: 'The update could not be published: 2 fields are invalid',
      problems: [
        'Validation end: must be after the start',
        'Change task 1: short description: is used twice',
      ],
      reload: false,
    });
  });

  it('offers to reload when someone else saved first, and only then', () => {
    expect(failureOf(refused(409, { detail: 'Changed meanwhile' }), 'Not saved')).toEqual({
      message: 'Not saved: Changed meanwhile',
      problems: [],
      reload: true,
    });
    expect(failureOf(refused(403, { detail: 'Only Custody' }), 'Not saved').reload).toBe(false);
    expect(NEEDS_ATTENTION).toEqual({ message: ATTENTION, problems: [], reload: false });
    expect(ATTENTION).toBe('Some fields need your attention.');
  });
});
