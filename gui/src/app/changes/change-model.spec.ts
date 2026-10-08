import {
  changeSchedule,
  changeTask,
  changeTemplate,
  changeUpdate,
  jiraVersion,
  productionChange,
  story,
  taskText,
} from '../testing/change-fixtures';
import {
  Moments,
  activeTasks,
  approverNames,
  changeRequest,
  editHint,
  fromLocalInput,
  isoDate,
  matchingVersions,
  momentInputs,
  momentText,
  plannedSchedule,
  readMoments,
  scheduleForm,
  scheduleOf,
  scheduleProblem,
  storiesFollowing,
  timeOf,
  toggled,
  versionText,
  windowText,
} from './change-model';

const at = (text: string) => new Date(text);

describe('change model', () => {
  it('turns the timing defaults into the five times of an installation date', () => {
    const planned = plannedSchedule('2026-10-20', {
      installationStart: '18:00',
      installationHours: 2,
      validationHours: 1,
    });

    expect(planned).toEqual({
      installationStart: at('2026-10-20T18:00:00'),
      installationEnd: at('2026-10-20T20:00:00'),
      validationStart: at('2026-10-20T20:00:00'),
      validationEnd: at('2026-10-20T21:00:00'),
      firstUsage: at('2026-10-20T21:00:00'),
    });
    expect(
      plannedSchedule('2026-10-20', {
        installationStart: '22:30',
        installationHours: 3,
        validationHours: 0,
      })?.validationEnd,
    ).toEqual(at('2026-10-21T01:30:00'));
    expect(plannedSchedule('', { installationStart: '18:00', installationHours: 2 })).toBeNull();
    expect(
      plannedSchedule('2026-10-20', { installationStart: '', installationHours: 2 }),
    ).toBeNull();
    expect(
      plannedSchedule('2026-10-20', {
        installationStart: '18:00',
        installationHours: 2,
        validationHours: null,
      }),
    ).toBeNull();
  });

  it('writes the times for the date and time inputs and reads them back', () => {
    const planned = plannedSchedule('2026-03-04', {
      installationStart: '05:06',
      installationHours: 1,
      validationHours: 1,
    })!;
    const inputs = momentInputs(planned);

    expect(inputs.installationStart).toEqual({ date: '2026-03-04', time: '05:06' });
    expect(inputs.firstUsage).toEqual({ date: '2026-03-04', time: '07:06' });
    expect(readMoments(inputs)).toEqual(planned);
    expect(readMoments({ installationStart: { date: '2026-03-04', time: '' } })).toEqual({
      installationStart: null,
      installationEnd: null,
      validationStart: null,
      validationEnd: null,
      firstUsage: null,
    });
    expect(isoDate(at('2026-03-04T05:06:00'))).toBe('2026-03-04');
    expect(timeOf(at('2026-03-04T05:06:00'))).toBe('05:06');
    expect(fromLocalInput('2026-03-04', '05:06')).toEqual(at('2026-03-04T05:06:00'));
    expect(fromLocalInput('not a date', '05:06')).toBeNull();
    expect(fromLocalInput(null, '05:06')).toBeNull();
  });

  it('says what is wrong with the order of the times', () => {
    const now = at('2026-10-07T09:30:00');
    const times = (...values: string[]) => ({
      installationStart: at(values[0]),
      installationEnd: at(values[1]),
      validationStart: at(values[2]),
      validationEnd: at(values[3]),
      firstUsage: at(values[4]),
    });
    const fine = [
      '2026-10-20T18:00:00',
      '2026-10-20T20:00:00',
      '2026-10-20T20:00:00',
      '2026-10-20T21:00:00',
      '2026-10-21T08:00:00',
    ];
    const changed = (index: number, value: string) =>
      times(...fine.map((time, i) => (i === index ? value : time)));

    expect(scheduleProblem(times(...fine), now)).toBeNull();
    expect(scheduleProblem({ ...times(...fine), validationEnd: null }, now)).toBe(
      'Enter the date and time of the validation end',
    );
    expect(scheduleProblem(changed(0, '2026-10-07T09:00:00'), now)).toBe(
      'The installation must start in the future',
    );
    expect(scheduleProblem(changed(0, '2026-10-07T09:00:00'), now, false)).toBeNull();
    expect(scheduleProblem(changed(1, '2026-10-20T18:00:00'), now)).toBe(
      'The installation must end after it starts',
    );
    expect(scheduleProblem(changed(2, '2026-10-20T19:59:00'), now)).toBe(
      'The validation cannot start before the installation ends',
    );
    expect(scheduleProblem(changed(3, '2026-10-20T19:59:00'), now)).toBe(
      'The validation cannot end before it starts',
    );
    expect(scheduleProblem(changed(4, '2026-10-20T20:59:00'), now)).toBe(
      'The first usage cannot be before the validation ends',
    );
  });

  it('shows a time, a window on one day once and a window over several days with both days', () => {
    expect(momentText(at('2026-10-10T06:00:00'))).toBe('Sat, 10 Oct 2026, 06:00');
    expect(windowText(at('2026-10-10T06:00:00'), at('2026-10-10T10:00:00'))).toBe(
      'Sat, 10 Oct 2026, 06:00 to 10:00',
    );
    expect(windowText(at('2026-10-10T22:00:00'), at('2026-10-11T02:00:00'))).toBe(
      'Sat, 10 Oct 2026, 22:00 to Sun, 11 Oct 2026, 02:00',
    );
  });

  it('describes the FixVersions and offers the ones matching the typed text', () => {
    const versions = [
      jiraVersion('CERT 4.2', false, '2026-10-20'),
      jiraVersion('CERT 4.3'),
      jiraVersion('CERT 4.1', true, '2026-09-01'),
    ];

    expect(versionText(versions[0])).toBe('unreleased · 2026-10-20');
    expect(versionText(versions[1])).toBe('unreleased');
    expect(versionText(versions[2])).toBe('released · 2026-09-01');
    expect(matchingVersions(versions, '').map((v) => v.name)).toEqual([
      'CERT 4.2',
      'CERT 4.3',
      'CERT 4.1',
    ]);
    expect(matchingVersions(versions, ' 4.3').map((v) => v.name)).toEqual(['CERT 4.3']);
    expect(matchingVersions(versions, 'cert 4.1')).toHaveLength(3);
  });

  it('chooses newly loaded stories and keeps the ones the user turned off unchosen', () => {
    const loaded = [story('CERT-2', 'A', 'CERT-1'), story('CERT-3', 'B', 'CERT-1')];

    expect(storiesFollowing(loaded, [], new Set())).toEqual(['CERT-2', 'CERT-3']);
    expect(storiesFollowing(loaded, ['CERT-3'], new Set(['CERT-2', 'CERT-3']))).toEqual(['CERT-3']);
    expect(toggled(['A'], 'B', true)).toEqual(['A', 'B']);
    expect(toggled(['A', 'B'], 'B', true)).toEqual(['A', 'B']);
    expect(toggled(['A', 'B'], 'A', false)).toEqual(['B']);
  });

  it('names the approvers that are set', () => {
    expect(approverNames(changeTemplate())).toEqual(['Olivia Bennett', 'James Carter']);
    expect(
      approverNames(
        changeTemplate({
          approvers: { l1Manager: null, l2Manager: null, businessApprover: 'Ann' },
        }),
      ),
    ).toEqual(['Ann']);
  });

  it('asks for a change with the scope, the times and the confirmed template', () => {
    const planned = plannedSchedule('2026-10-20', {
      installationStart: '18:00',
      installationHours: 2,
      validationHours: 1,
    })!;
    const template = changeTemplate();

    expect(
      changeRequest(
        {
          productId: 1,
          fixVersion: ' CERT 4.2 ',
          epicKeys: ['CERT-1'],
          storyKeys: ['CERT-2'],
        },
        planned,
        template,
        [taskText('Deploy it')],
      ),
    ).toEqual({
      productId: 1,
      fixVersion: 'CERT 4.2',
      epicKeys: ['CERT-1'],
      storyKeys: ['CERT-2'],
      schedule: {
        installationStart: at('2026-10-20T18:00:00').toISOString(),
        installationEnd: at('2026-10-20T20:00:00').toISOString(),
        validationStart: at('2026-10-20T20:00:00').toISOString(),
        validationEnd: at('2026-10-20T21:00:00').toISOString(),
        firstUsage: at('2026-10-20T21:00:00').toISOString(),
      },
      template,
      tasks: [taskText('Deploy it')],
    });
  });

  it('fills the schedule inputs of a stored schedule and writes them back', () => {
    const schedule = changeSchedule();
    const form = scheduleForm(schedule);

    expect(form.controls.installationStart.getRawValue().date).toBe(
      isoDate(new Date(schedule.installationStart)),
    );
    expect(scheduleOf(readMoments(form.getRawValue()) as Moments)).toEqual({
      installationStart: '2026-10-10T06:00:00.000Z',
      installationEnd: '2026-10-10T08:00:00.000Z',
      validationStart: '2026-10-10T08:00:00.000Z',
      validationEnd: '2026-10-10T09:00:00.000Z',
      firstUsage: '2026-10-12T08:00:00.000Z',
    });
    expect(scheduleForm().getRawValue().firstUsage).toEqual({ date: '', time: '' });
  });

  it('keeps the tasks ProTech has not canceled', () => {
    const canceled = changeTask({ number: 'CTASK0020002', state: 'CANCELED' });
    const closed = changeTask({ number: 'CTASK0020003', state: 'CLOSED' });

    expect(activeTasks([changeTask(), canceled, closed])).toEqual([changeTask(), closed]);
  });

  it('says why a change cannot be edited by the chosen department', () => {
    const change = productionChange();

    expect(editHint(change, 3)).toBeNull();
    expect(editHint(change, null)).toBe('Choose your department in Changes to change it');
    expect(editHint(change, 5)).toBe('Only Corporate Technology can change it');
    expect(editHint({ ...change, departmentName: null }, 5)).toBe(
      'Only its department can change it',
    );
    expect(editHint({ ...change, departmentId: null }, 3)).toBe(
      'No department owns CHG0012345, so it cannot be changed in Beadle',
    );
    expect(editHint({ ...change, update: changeUpdate() }, 3)).toBe(
      'The last update is still waiting for ProTech; change it again once ProTech has applied it',
    );
    expect(editHint({ ...change, update: changeUpdate({ status: 'APPLIED' }) }, 3)).toBeNull();
  });
});
