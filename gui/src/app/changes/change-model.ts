import {
  ChangeRequest,
  ChangeSchedule,
  ChangeTemplate,
  ChangeTiming,
  JiraIssue,
  JiraVersion,
} from './change-api';

export type Moment = keyof ChangeSchedule;

export type Moments = Record<Moment, Date>;

export type MomentInputs = Record<Moment, { date: string; time: string }>;

export const MOMENTS: readonly { key: Moment; label: string }[] = [
  { key: 'installationStart', label: 'Installation start' },
  { key: 'installationEnd', label: 'Installation end' },
  { key: 'validationStart', label: 'Validation start' },
  { key: 'validationEnd', label: 'Validation end' },
  { key: 'firstUsage', label: 'First usage' },
];

export type TimingInput = { [K in keyof ChangeTiming]?: ChangeTiming[K] | null };

export type MomentValues = Partial<Record<Moment, Partial<{ date: string; time: string }>>>;

export interface ChangeScope {
  productId: number;
  serviceIds: readonly number[];
  fixVersion: string;
  epicKeys: readonly string[];
  storyKeys: readonly string[];
}

const HOUR = 3600 * 1000;

const pad = (value: number) => String(value).padStart(2, '0');

export function isoDate(day: Date): string {
  return `${day.getFullYear()}-${pad(day.getMonth() + 1)}-${pad(day.getDate())}`;
}

export function timeOf(time: Date): string {
  return `${pad(time.getHours())}:${pad(time.getMinutes())}`;
}

export function fromLocalInput(
  date: string | null | undefined,
  time: string | null | undefined,
): Date | null {
  if (!date || !time) {
    return null;
  }
  const value = new Date(`${date}T${time}`);
  return Number.isNaN(value.getTime()) ? null : value;
}

const later = (time: Date, hours: number) => new Date(time.getTime() + hours * HOUR);

export function plannedSchedule(day: string, timing: TimingInput): Moments | null {
  const start = fromLocalInput(day, timing.installationStart);
  const { installationHours: installation, validationHours: validation } = timing;
  if (!start || installation == null || validation == null) {
    return null;
  }
  const installationEnd = later(start, installation);
  const validationEnd = later(installationEnd, validation);
  return {
    installationStart: start,
    installationEnd,
    validationStart: installationEnd,
    validationEnd,
    firstUsage: validationEnd,
  };
}

export function eachMoment<T>(value: (key: Moment) => T): Record<Moment, T> {
  return Object.fromEntries(MOMENTS.map(({ key }) => [key, value(key)])) as Record<Moment, T>;
}

export function momentInputs(moments: Moments): MomentInputs {
  return eachMoment((key) => ({ date: isoDate(moments[key]), time: timeOf(moments[key]) }));
}

export function readMoments(inputs: MomentValues): Record<Moment, Date | null> {
  return eachMoment((key) => fromLocalInput(inputs[key]?.date, inputs[key]?.time));
}

export function scheduleProblem(moments: Record<Moment, Date | null>, now: Date): string | null {
  const missing = MOMENTS.find(({ key }) => !moments[key]);
  if (missing) {
    return `Enter the date and time of the ${missing.label.toLowerCase()}`;
  }
  const at = (key: Moment) => moments[key]!.getTime();
  if (at('installationStart') <= now.getTime()) {
    return 'The installation must start in the future';
  }
  if (at('installationEnd') <= at('installationStart')) {
    return 'The installation must end after it starts';
  }
  if (at('validationStart') < at('installationEnd')) {
    return 'The validation cannot start before the installation ends';
  }
  if (at('validationEnd') < at('validationStart')) {
    return 'The validation cannot end before it starts';
  }
  if (at('firstUsage') < at('validationEnd')) {
    return 'The first usage cannot be before the validation ends';
  }
  return null;
}

const DAY = new Intl.DateTimeFormat('en-GB', {
  weekday: 'short',
  day: 'numeric',
  month: 'short',
  year: 'numeric',
});
const TIME = new Intl.DateTimeFormat('en-GB', { hour: '2-digit', minute: '2-digit' });

export function momentText(time: Date | string): string {
  const value = new Date(time);
  return `${DAY.format(value)}, ${TIME.format(value)}`;
}

export function windowText(start: Date | string, end: Date | string): string {
  const from = new Date(start);
  const to = new Date(end);
  const sameDay = isoDate(from) === isoDate(to);
  return `${momentText(from)} to ${sameDay ? '' : DAY.format(to) + ', '}${TIME.format(to)}`;
}

export function versionText(version: JiraVersion): string {
  return [version.released ? 'released' : 'unreleased', version.releaseDate]
    .filter(Boolean)
    .join(' · ');
}

export function matchingVersions(versions: readonly JiraVersion[], typed: string): JiraVersion[] {
  const text = typed.trim().toLowerCase();
  return versions.some((version) => version.name.toLowerCase() === text)
    ? [...versions]
    : versions.filter((version) => version.name.toLowerCase().includes(text));
}

export function storiesFollowing(
  loaded: readonly JiraIssue[],
  chosen: readonly string[],
  seen: ReadonlySet<string>,
): string[] {
  return loaded
    .filter((story) => !seen.has(story.key) || chosen.includes(story.key))
    .map((story) => story.key);
}

export function toggled(keys: readonly string[], key: string, on: boolean): string[] {
  return on ? [...new Set([...keys, key])] : keys.filter((current) => current !== key);
}

export function approverNames(template: ChangeTemplate): string[] {
  const { l1Manager, l2Manager, businessApprover } = template.approvers;
  return [l1Manager, l2Manager, businessApprover].filter((name): name is string => !!name);
}

export function changeRequest(
  scope: ChangeScope,
  moments: Moments,
  template: ChangeTemplate,
): ChangeRequest {
  return {
    productId: scope.productId,
    serviceIds: [...scope.serviceIds],
    fixVersion: scope.fixVersion.trim(),
    epicKeys: [...scope.epicKeys],
    storyKeys: [...scope.storyKeys],
    schedule: eachMoment((key) => moments[key].toISOString()),
    template,
  };
}
