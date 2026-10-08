import { FormControl, FormGroup, ValidatorFn, Validators } from '@angular/forms';
import { FieldProblem } from '../core/models';
import { setEnabled, text } from '../shared/form-controls';
import { ChangeSchedule, ChangeTiming } from './change-api';
import { isoDate, timeOf } from './change-model';

export type TimingInput = { [K in keyof ChangeTiming]: ChangeTiming[K] | null };

export const MAX_HOURS = 168;

const HOUR = 3600 * 1000;
const MINUTE = 60 * 1000;

export const SCHEDULE_LABELS: Record<keyof ChangeSchedule, string> = {
  installationStart: 'Installation start',
  installationEnd: 'Installation end',
  validationStart: 'Validation start',
  validationEnd: 'Validation end',
  firstUsage: 'First use',
  downtimeStart: 'Downtime start',
  downtimeEnd: 'Downtime end',
};

export const SCHEDULE_PREFIX = 'schedule.';

export const onHours = (problem: FieldProblem): FieldProblem => ({
  ...problem,
  field: problem.field.replace(/^(schedule\.\w+)End$/, '$1Hours'),
});

export type Windows = Record<keyof ChangeSchedule, Date | null>;

export interface ScheduleInput {
  installationStart: string;
  installationHours: number | null;
  validationStart: string;
  validationHours: number | null;
  firstUsage: string;
  downtimeStart: string;
  downtimeHours: number | null;
}

const EMPTY: ScheduleInput = {
  installationStart: '',
  installationHours: null,
  validationStart: '',
  validationHours: null,
  firstUsage: '',
  downtimeStart: '',
  downtimeHours: null,
};

const MISSING: [keyof ChangeSchedule, string][] = [
  ['installationStart', 'Enter the date and time of the installation start'],
  ['installationEnd', 'Enter how many hours the installation takes'],
  ['validationStart', 'Enter the date and time of the post-install validation start'],
  ['validationEnd', 'Enter how many hours the post-install validation takes'],
  ['firstUsage', 'Enter the date and time of the first use'],
  ['downtimeStart', 'Enter when the downtime starts'],
  ['downtimeEnd', 'Enter how many hours the downtime takes'],
];

export const localInput = (time: Date) => `${isoDate(time)}T${timeOf(time)}`;

export function fromLocal(value: string | null | undefined): Date | null {
  const time = value ? new Date(value) : null;
  return time && !Number.isNaN(time.getTime()) ? time : null;
}

function later(start: Date | null, hours: number | null): Date | null {
  return start && hours !== null && Number.isFinite(hours)
    ? new Date(Math.round((start.getTime() + hours * HOUR) / MINUTE) * MINUTE)
    : null;
}

function hoursBetween(start: string, end: string | null): number | null {
  const span = end ? Date.parse(end) - Date.parse(start) : NaN;
  return Number.isNaN(span) ? null : Math.round((span / HOUR) * 100) / 100;
}

function localEnd(start: string, hours: number | null): string {
  const end = later(fromLocal(start), hours);
  return end ? localInput(end) : '';
}

const LINKS: [keyof ScheduleInput, (input: ScheduleInput) => string | number | null][] = [
  ['validationStart', (input) => localEnd(input.installationStart, input.installationHours)],
  ['firstUsage', (input) => localEnd(input.validationStart, input.validationHours)],
  ['downtimeStart', (input) => input.installationStart],
  ['downtimeHours', (input) => input.installationHours],
];

export function following(before: ScheduleInput, after: ScheduleInput): ScheduleInput {
  return LINKS.reduce(
    (next, [key, source]) =>
      after[key] === before[key] && before[key] === source(before)
        ? ({ ...next, [key]: source(next) } as ScheduleInput)
        : next,
    after,
  );
}

export function scheduleInput(schedule: ChangeSchedule): ScheduleInput {
  const local = (time: string) => localInput(new Date(time));
  const installationStart = local(schedule.installationStart);
  const installationHours = hoursBetween(schedule.installationStart, schedule.installationEnd);
  const downtime = schedule.downtimeStart;
  return {
    installationStart,
    installationHours,
    validationStart: local(schedule.validationStart),
    validationHours: hoursBetween(schedule.validationStart, schedule.validationEnd),
    firstUsage: local(schedule.firstUsage),
    downtimeStart: downtime ? local(downtime) : installationStart,
    downtimeHours: downtime ? hoursBetween(downtime, schedule.downtimeEnd) : installationHours,
  };
}

export function plannedInput(day: string, timing: TimingInput): ScheduleInput {
  const start = day && timing.installationStart ? `${day}T${timing.installationStart}` : '';
  const validationStart = localEnd(start, timing.installationHours);
  return {
    installationStart: start,
    installationHours: timing.installationHours,
    validationStart,
    validationHours: timing.validationHours,
    firstUsage: localEnd(validationStart, timing.validationHours),
    downtimeStart: start,
    downtimeHours: timing.installationHours,
  };
}

export function windowsOf(input: ScheduleInput, downtime: boolean): Windows {
  const installationStart = fromLocal(input.installationStart);
  const validationStart = fromLocal(input.validationStart);
  const downtimeStart = downtime ? fromLocal(input.downtimeStart) : null;
  return {
    installationStart,
    installationEnd: later(installationStart, input.installationHours),
    validationStart,
    validationEnd: later(validationStart, input.validationHours),
    firstUsage: fromLocal(input.firstUsage),
    downtimeStart,
    downtimeEnd: later(downtimeStart, input.downtimeHours),
  };
}

export function scheduleProblem(
  windows: Windows,
  downtime: boolean,
  now: Date,
  upcoming = true,
): string | null {
  const missing = MISSING.find(
    ([key]) => !windows[key] && (downtime || !key.startsWith('downtime')),
  );
  if (missing) {
    return missing[1];
  }
  const at = (key: keyof ChangeSchedule) => windows[key]!.getTime();
  if (upcoming && at('installationStart') <= now.getTime()) {
    return 'The installation must start in the future';
  }
  if (at('installationEnd') <= at('installationStart')) {
    return 'The installation must end after it starts';
  }
  if (at('validationStart') < at('installationEnd')) {
    return 'The validation cannot start before the installation ends';
  }
  if (at('firstUsage') < at('validationEnd')) {
    return 'The first use cannot be before the validation ends';
  }
  if (downtime && at('downtimeEnd') <= at('downtimeStart')) {
    return 'The downtime must end after it starts';
  }
  return null;
}

export function scheduleOf(windows: Windows): ChangeSchedule {
  return Object.fromEntries(
    Object.entries(windows).map(([key, time]) => [key, time?.toISOString() ?? null]),
  ) as unknown as ChangeSchedule;
}

const hours = (value: number | null) =>
  new FormControl<number | null>(value, {
    validators: [Validators.required, Validators.min(0), Validators.max(MAX_HOURS)],
  });

function scheduleRule(downtime: FormControl<boolean>, stored?: ChangeSchedule): ValidatorFn {
  return (group) => {
    const windows = windowsOf(group.getRawValue(), downtime.value);
    const moved =
      !stored || windows.installationStart?.getTime() !== Date.parse(stored.installationStart);
    const problem = scheduleProblem(windows, downtime.value, new Date(), moved);
    return problem ? { rule: problem } : null;
  };
}

export function scheduleForm(downtime: FormControl<boolean>, stored?: ChangeSchedule) {
  const input = stored ? scheduleInput(stored) : EMPTY;
  const form = new FormGroup(
    {
      installationStart: text(input.installationStart),
      installationHours: hours(input.installationHours),
      validationStart: text(input.validationStart),
      validationHours: hours(input.validationHours),
      firstUsage: text(input.firstUsage),
      downtimeStart: text(input.downtimeStart),
      downtimeHours: hours(input.downtimeHours),
    },
    { validators: scheduleRule(downtime, stored) },
  );
  let before = form.getRawValue();
  form.valueChanges.subscribe(() => {
    const after = form.getRawValue();
    const next = following(before, after);
    before = next;
    if (LINKS.some(([key]) => next[key] !== after[key])) {
      form.patchValue(next);
    }
  });
  const sync = (on: boolean) => {
    setEnabled(form.controls.downtimeStart, on);
    setEnabled(form.controls.downtimeHours, on);
    form.updateValueAndValidity();
  };
  downtime.valueChanges.subscribe(sync);
  sync(downtime.value);
  return form;
}

export type ScheduleForm = ReturnType<typeof scheduleForm>;

export function scheduleValue(form: ScheduleForm, downtime: boolean): ChangeSchedule {
  return scheduleOf(windowsOf(form.getRawValue(), downtime));
}
