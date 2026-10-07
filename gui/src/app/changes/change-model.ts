import { Choice } from '../beadle/onboarding-model';
import { ChangeRequest, DateRange, JiraIssue } from './change-api';

export type WindowChoice = 'tonight' | 'weekend' | 'custom';

export interface ChangeWindow {
  start: Date;
  end: Date;
}

export const WINDOWS: readonly Choice<WindowChoice>[] = [
  {
    value: 'tonight',
    label: 'Tonight',
    description: 'After business hours, 20:00 to 23:00',
  },
  {
    value: 'weekend',
    label: 'This weekend',
    description: 'Saturday morning, 06:00 to 10:00',
  },
  {
    value: 'custom',
    label: 'Another time',
    description: 'Choose the start and the end yourself',
  },
];

export const JIRA_DAYS = 90;

function at(day: Date, hours: number): Date {
  const time = new Date(day);
  time.setHours(hours, 0, 0, 0);
  return time;
}

function addDays(day: Date, days: number): Date {
  const moved = new Date(day);
  moved.setDate(moved.getDate() + days);
  return moved;
}

export function presetWindow(choice: WindowChoice, now: Date): ChangeWindow | null {
  if (choice === 'tonight') {
    const day = now.getHours() < 19 ? now : addDays(now, 1);
    return { start: at(day, 20), end: at(day, 23) };
  }
  if (choice === 'weekend') {
    const saturday = addDays(now, (6 - now.getDay() + 7) % 7 || 7);
    return { start: at(saturday, 6), end: at(saturday, 10) };
  }
  return null;
}

const pad = (value: number) => String(value).padStart(2, '0');

export function isoDate(day: Date): string {
  return `${day.getFullYear()}-${pad(day.getMonth() + 1)}-${pad(day.getDate())}`;
}

export function localInput(time: Date): string {
  return `${isoDate(time)}T${pad(time.getHours())}:${pad(time.getMinutes())}`;
}

export function fromLocalInput(value: string | null | undefined): Date | null {
  if (!value) {
    return null;
  }
  const time = new Date(value);
  return Number.isNaN(time.getTime()) ? null : time;
}

export function recentDays(now: Date, days = JIRA_DAYS): DateRange {
  return { from: isoDate(addDays(now, -days)), to: isoDate(now) };
}

export function windowProblem(window: ChangeWindow | null, now: Date): string | null {
  if (!window) {
    return 'Choose when the change starts and ends';
  }
  if (window.start.getTime() <= now.getTime()) {
    return 'The change must start in the future';
  }
  if (window.end.getTime() <= window.start.getTime()) {
    return 'The change must end after it starts';
  }
  if (window.end.getTime() - window.start.getTime() > 7 * 24 * 3600 * 1000) {
    return 'A change window may last at most 7 days';
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

export function windowText(start: Date | string, end: Date | string): string {
  const from = new Date(start);
  const to = new Date(end);
  const sameDay = isoDate(from) === isoDate(to);
  return `${DAY.format(from)}, ${TIME.format(from)} to ${sameDay ? '' : DAY.format(to) + ', '}${TIME.format(to)}`;
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

export function changeRequest(
  productId: number,
  serviceIds: readonly number[],
  epicKeys: readonly string[],
  storyKeys: readonly string[],
  window: ChangeWindow | null,
): ChangeRequest {
  return {
    productId,
    serviceIds: [...serviceIds],
    epicKeys: [...epicKeys],
    storyKeys: [...storyKeys],
    start: window?.start.toISOString() ?? null,
    end: window?.end.toISOString() ?? null,
  };
}
