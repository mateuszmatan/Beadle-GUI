import { ValidatorFn } from '@angular/forms';
import { fitsColumn } from '../shared/form-controls';
import {
  ChangeRequest,
  ChangeSchedule,
  ChangeTask,
  ChangeTemplate,
  JiraIssue,
  JiraVersion,
  ProductionChange,
} from './change-api';

export interface ChangeScope {
  productId: number;
  fixVersion: string;
  epicKeys: readonly string[];
  storyKeys: readonly string[];
}

const pad = (value: number) => String(value).padStart(2, '0');

export function isoDate(day: Date): string {
  return `${day.getFullYear()}-${pad(day.getMonth() + 1)}-${pad(day.getDate())}`;
}

export function timeOf(time: Date): string {
  return `${pad(time.getHours())}:${pad(time.getMinutes())}`;
}

export function fits(length: number): ValidatorFn {
  const column = fitsColumn((value) => [value.trim()], '', length);
  return (control) => (column(control) ? { bytes: { max: length } } : null);
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

export const TIME_ZONE_NOTE = `Times are in your time zone, ${Intl.DateTimeFormat().resolvedOptions().timeZone}.`;

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
  const { businessApprover, l1Manager, l2Manager } = template.approvers;
  return [businessApprover, l1Manager, l2Manager].filter((name): name is string => !!name);
}

export function changeRequest(
  scope: ChangeScope,
  schedule: ChangeSchedule,
  template: ChangeTemplate,
): ChangeRequest {
  return {
    productId: scope.productId,
    fixVersion: scope.fixVersion.trim(),
    epicKeys: [...scope.epicKeys],
    storyKeys: [...scope.storyKeys],
    schedule,
    template,
  };
}

export const activeTasks = (tasks: readonly ChangeTask[]) =>
  tasks.filter((task) => task.state !== 'CANCELED');

export const PENDING_HINT =
  'The last update is still waiting for ProTech; change it again once ProTech has applied it';

export function editHint(
  change: Pick<ProductionChange, 'number' | 'departmentId' | 'departmentName' | 'update'>,
  departmentId: number | null,
): string | null {
  if (change.departmentId === null) {
    return `No department owns ${change.number}, so it cannot be changed in Beadle`;
  }
  if (departmentId === null) {
    return 'Choose your department in Changes to change it';
  }
  if (departmentId !== change.departmentId) {
    return `Only ${change.departmentName ?? 'its department'} can change it`;
  }
  return change.update?.status === 'PENDING' ? PENDING_HINT : null;
}
