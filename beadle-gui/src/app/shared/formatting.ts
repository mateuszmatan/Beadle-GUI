import { Pipe, PipeTransform } from '@angular/core';

export const TIME_ZONE_NOTE = `Times are in your time zone, ${Intl.DateTimeFormat().resolvedOptions().timeZone}.`;

export function capitalized(text: string): string {
  return text.charAt(0).toUpperCase() + text.slice(1);
}

export function counted(count: number, noun: string, plural = `${noun}s`): string {
  return `${count} ${count === 1 ? noun : plural}`;
}

const relativeTime = new Intl.RelativeTimeFormat('en', { numeric: 'auto' });

export function formatRelative(iso: string | null | undefined, now: number = Date.now()): string {
  if (!iso) {
    return '–';
  }
  const diffSeconds = Math.round((new Date(iso).getTime() - now) / 1000);
  const abs = Math.abs(diffSeconds);
  if (abs < 45) {
    return 'just now';
  }
  if (abs < 3600) {
    return relativeTime.format(Math.round(diffSeconds / 60), 'minute');
  }
  if (abs < 86400) {
    return relativeTime.format(Math.round(diffSeconds / 3600), 'hour');
  }
  if (abs < 86400 * 45) {
    return relativeTime.format(Math.round(diffSeconds / 86400), 'day');
  }
  return relativeTime.format(Math.round(diffSeconds / (86400 * 30)), 'month');
}

@Pipe({ name: 'relative' })
export class RelativeTimePipe implements PipeTransform {
  transform(iso: string | null | undefined): string {
    return formatRelative(iso);
  }
}
