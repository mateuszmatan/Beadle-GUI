import { FormControl } from '@angular/forms';
import { changeSchedule } from '../testing/change-fixtures';
import {
  ScheduleInput,
  Windows,
  following,
  fromLocal,
  localInput,
  onHours,
  plannedDay,
  plannedInput,
  scheduleForm,
  scheduleInput,
  scheduleOf,
  scheduleProblem,
  scheduleValue,
  windowsOf,
} from './change-schedule-model';

const at = (text: string) => new Date(text);
const local = (text: string) => localInput(new Date(text));

const PLANNED: ScheduleInput = {
  installationStart: '2026-10-20T18:00',
  installationHours: 2,
  validationStart: '2026-10-20T20:00',
  validationHours: 1,
  firstUsage: '2026-10-20T21:00',
  downtimeStart: '2026-10-20T18:00',
  downtimeHours: 2,
};

describe('change schedule model', () => {
  it('writes a moment for a date and time input and reads it back', () => {
    expect(localInput(at('2026-03-04T05:06:00'))).toBe('2026-03-04T05:06');
    expect(fromLocal('2026-03-04T05:06')).toEqual(at('2026-03-04T05:06:00'));
    expect(fromLocal('not a date')).toBeNull();
    expect(fromLocal('')).toBeNull();
    expect(fromLocal(null)).toBeNull();
  });

  it('plans on the release date while it is ahead, otherwise on the next day', () => {
    const now = new Date(2026, 9, 9, 10, 30);
    expect(plannedDay('2026-10-20', now)).toBe('2026-10-20');
    expect(plannedDay('2026-10-09', now)).toBe('2026-10-09');
    expect(plannedDay('2026-10-01', now)).toBe('2026-10-10');
    expect(plannedDay(null, now)).toBe('2026-10-10');
    expect(plannedDay(null, new Date(2026, 9, 31, 23, 0))).toBe('2026-11-01');
  });

  it('plans the windows from the release date and the timing defaults', () => {
    expect(
      plannedInput('2026-10-20', {
        installationStart: '18:00',
        installationHours: 2,
        validationHours: 1,
      }),
    ).toEqual(PLANNED);
    expect(
      plannedInput('2026-10-20', {
        installationStart: '22:30',
        installationHours: 3,
        validationHours: 0,
      }).firstUsage,
    ).toBe('2026-10-21T01:30');
    expect(
      plannedInput('', { installationStart: '18:00', installationHours: 2, validationHours: 1 }),
    ).toMatchObject({ installationStart: '', validationStart: '', firstUsage: '' });
    expect(
      plannedInput('2026-10-20', {
        installationStart: '18:00',
        installationHours: null,
        validationHours: 1,
      }),
    ).toMatchObject({ validationStart: '', firstUsage: '' });
  });

  it('moves the later windows with the installation until they are changed by hand', () => {
    const moved = following(PLANNED, { ...PLANNED, installationStart: '2026-10-21T19:00' });

    expect(moved).toEqual({
      ...PLANNED,
      installationStart: '2026-10-21T19:00',
      validationStart: '2026-10-21T21:00',
      firstUsage: '2026-10-21T22:00',
      downtimeStart: '2026-10-21T19:00',
    });
    expect(following(moved, { ...moved, installationHours: 3.5 })).toMatchObject({
      validationStart: '2026-10-21T22:30',
      firstUsage: '2026-10-21T23:30',
      downtimeHours: 3.5,
    });

    const own = { ...PLANNED, validationStart: '2026-10-22T08:00', downtimeHours: 1 };
    expect(following(own, { ...own, installationStart: '2026-10-21T19:00' })).toEqual({
      ...own,
      installationStart: '2026-10-21T19:00',
      downtimeStart: '2026-10-21T19:00',
    });
  });

  it('fills the inputs of a stored schedule with the installation window as the downtime', () => {
    const schedule = changeSchedule();

    expect(scheduleInput(schedule)).toEqual({
      installationStart: local(schedule.installationStart),
      installationHours: 2,
      validationStart: local(schedule.validationStart),
      validationHours: 1,
      firstUsage: local(schedule.firstUsage),
      downtimeStart: local(schedule.installationStart),
      downtimeHours: 2,
    });
    expect(
      scheduleInput(
        changeSchedule({
          downtimeStart: '2026-10-10T06:30:00Z',
          downtimeEnd: '2026-10-10T07:15:00Z',
        }),
      ),
    ).toMatchObject({ downtimeStart: local('2026-10-10T06:30:00Z'), downtimeHours: 0.75 });
  });

  it('turns the inputs into the windows, rounded to the minute, and the stored schedule', () => {
    const windows = windowsOf({ ...PLANNED, validationHours: 1.009 }, true);

    expect(windows).toEqual({
      installationStart: at('2026-10-20T18:00:00'),
      installationEnd: at('2026-10-20T20:00:00'),
      validationStart: at('2026-10-20T20:00:00'),
      validationEnd: at('2026-10-20T21:01:00'),
      firstUsage: at('2026-10-20T21:00:00'),
      downtimeStart: at('2026-10-20T18:00:00'),
      downtimeEnd: at('2026-10-20T20:00:00'),
    });
    expect(windowsOf(PLANNED, false)).toMatchObject({ downtimeStart: null, downtimeEnd: null });
    expect(windowsOf({ ...PLANNED, installationHours: null }, false).installationEnd).toBeNull();
    expect(scheduleOf(windowsOf(PLANNED, false))).toEqual({
      installationStart: at('2026-10-20T18:00:00').toISOString(),
      installationEnd: at('2026-10-20T20:00:00').toISOString(),
      validationStart: at('2026-10-20T20:00:00').toISOString(),
      validationEnd: at('2026-10-20T21:00:00').toISOString(),
      firstUsage: at('2026-10-20T21:00:00').toISOString(),
      downtimeStart: null,
      downtimeEnd: null,
    });
  });

  it('says what is wrong with the order of the times', () => {
    const now = at('2026-10-07T09:30:00');
    const fine = windowsOf(PLANNED, true);
    const problem = (key: keyof Windows, value: string | null, downtime = true, upcoming = true) =>
      scheduleProblem({ ...fine, [key]: value ? at(value) : null }, downtime, now, upcoming);

    expect(scheduleProblem(fine, true, now)).toBeNull();
    expect(problem('installationStart', null)).toBe(
      'Enter the date and time of the installation start',
    );
    expect(problem('validationEnd', null)).toBe(
      'Enter how many hours the post-install validation takes',
    );
    expect(problem('downtimeStart', null)).toBe('Enter when the downtime starts');
    expect(problem('downtimeEnd', null, false)).toBeNull();
    expect(problem('installationStart', '2026-10-07T09:00:00')).toBe(
      'The installation must start in the future',
    );
    expect(problem('installationStart', '2026-10-07T09:00:00', true, false)).toBeNull();
    expect(problem('installationEnd', '2026-10-20T18:00:00')).toBe(
      'The installation must end after it starts',
    );
    expect(problem('validationStart', '2026-10-20T19:59:00')).toBe(
      'The validation cannot start before the installation ends',
    );
    expect(problem('firstUsage', '2026-10-20T20:59:00')).toBe(
      'The first use cannot be before the validation ends',
    );
    expect(problem('downtimeEnd', '2026-10-20T18:00:00')).toBe(
      'The downtime must end after it starts',
    );
    expect(problem('downtimeEnd', '2026-10-20T18:00:00', false)).toBeNull();
  });

  it('asks for the downtime window only while there is a downtime', () => {
    const downtime = new FormControl(false, { nonNullable: true });
    const form = scheduleForm(downtime);
    const c = form.controls;

    expect(c.downtimeStart.disabled).toBe(true);
    expect(form.errors).toEqual({ rule: 'Enter the date and time of the installation start' });
    form.patchValue({
      ...PLANNED,
      installationStart: '2099-10-20T18:00',
      downtimeStart: '2099-10-20T18:00',
    });
    expect(form.errors).toEqual({
      rule: 'The validation cannot start before the installation ends',
    });
    form.patchValue({ validationStart: '2099-10-20T20:00', firstUsage: '2099-10-20T21:00' });
    expect(form.valid).toBe(true);

    downtime.setValue(true);
    expect(c.downtimeStart.enabled).toBe(true);
    expect(c.downtimeStart.value).toBe('2099-10-20T18:00');
    c.downtimeHours.setValue(0);
    expect(form.errors).toEqual({ rule: 'The downtime must end after it starts' });
    c.downtimeHours.setValue(1.5);
    expect(form.valid).toBe(true);
    expect(scheduleValue(form, true).downtimeEnd).toBe(at('2099-10-20T19:30:00').toISOString());

    downtime.setValue(false);
    expect(c.downtimeHours.disabled).toBe(true);
    expect(scheduleValue(form, false)).toMatchObject({ downtimeStart: null, downtimeEnd: null });
  });

  it('keeps the validation and the downtime following the installation in the form', () => {
    const form = scheduleForm(new FormControl(true, { nonNullable: true }));
    form.setValue(PLANNED);

    form.controls.installationStart.setValue('2099-01-05T07:00');
    expect(form.getRawValue()).toEqual({
      ...PLANNED,
      installationStart: '2099-01-05T07:00',
      validationStart: '2099-01-05T09:00',
      firstUsage: '2099-01-05T10:00',
      downtimeStart: '2099-01-05T07:00',
    });
    form.controls.downtimeStart.setValue('2099-01-05T08:00');
    form.controls.installationStart.setValue('2099-01-06T07:00');
    expect(form.controls.downtimeStart.value).toBe('2099-01-05T08:00');
    form.controls.installationHours.setValue(169);
    expect(form.controls.installationHours.hasError('max')).toBe(true);
  });

  it('lets a stored schedule in the past stay until its installation start moves', () => {
    const stored = changeSchedule({
      installationStart: '2020-10-10T06:00:00Z',
      installationEnd: '2020-10-10T08:00:00Z',
      validationStart: '2020-10-10T08:00:00Z',
      validationEnd: '2020-10-10T09:00:00Z',
      firstUsage: '2020-10-12T08:00:00Z',
    });
    const form = scheduleForm(new FormControl(false, { nonNullable: true }), stored);

    expect(form.valid).toBe(true);
    expect(scheduleValue(form, false)).toEqual({
      ...stored,
      installationStart: '2020-10-10T06:00:00.000Z',
      installationEnd: '2020-10-10T08:00:00.000Z',
      validationStart: '2020-10-10T08:00:00.000Z',
      validationEnd: '2020-10-10T09:00:00.000Z',
      firstUsage: '2020-10-12T08:00:00.000Z',
    });
    form.controls.installationStart.setValue(local('2020-10-11T06:00:00Z'));
    expect(form.errors).toEqual({ rule: 'The installation must start in the future' });
  });

  it('puts a refused end on the hours input of its window', () => {
    const problem = (field: string) => ({ field, message: 'must be after the start' });

    expect(onHours(problem('schedule.installationEnd'))).toEqual(
      problem('schedule.installationHours'),
    );
    expect(onHours(problem('schedule.downtimeEnd'))).toEqual(problem('schedule.downtimeHours'));
    expect(onHours(problem('schedule.firstUsage'))).toEqual(problem('schedule.firstUsage'));
    expect(onHours(problem('template.release'))).toEqual(problem('template.release'));
  });
});
