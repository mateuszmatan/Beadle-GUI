import { Pipe, PipeTransform } from '@angular/core';

export function formatDuration(seconds: number | null | undefined): string {
  if (seconds === null || seconds === undefined || Number.isNaN(seconds)) {
    return '–';
  }
  const s = Math.round(seconds);
  if (s < 60) {
    return `${s}s`;
  }
  const minutes = Math.floor(s / 60);
  if (minutes < 60) {
    const rest = s % 60;
    return rest ? `${minutes}m ${rest}s` : `${minutes}m`;
  }
  const hours = Math.floor(minutes / 60);
  if (hours < 48) {
    const rest = minutes % 60;
    return rest ? `${hours}h ${rest}m` : `${hours}h`;
  }
  const days = Math.floor(hours / 24);
  const rest = hours % 24;
  return rest ? `${days}d ${rest}h` : `${days}d`;
}

export function formatRelative(iso: string | null | undefined, now: number = Date.now()): string {
  if (!iso) {
    return '–';
  }
  const diffSeconds = Math.round((new Date(iso).getTime() - now) / 1000);
  const abs = Math.abs(diffSeconds);
  const format = new Intl.RelativeTimeFormat('en', { numeric: 'auto' });
  if (abs < 45) {
    return 'just now';
  }
  if (abs < 3600) {
    return format.format(Math.round(diffSeconds / 60), 'minute');
  }
  if (abs < 86400) {
    return format.format(Math.round(diffSeconds / 3600), 'hour');
  }
  if (abs < 86400 * 45) {
    return format.format(Math.round(diffSeconds / 86400), 'day');
  }
  return format.format(Math.round(diffSeconds / (86400 * 30)), 'month');
}

@Pipe({ name: 'duration' })
export class DurationPipe implements PipeTransform {
  transform(seconds: number | null | undefined): string {
    return formatDuration(seconds);
  }
}

@Pipe({ name: 'relative' })
export class RelativeTimePipe implements PipeTransform {
  transform(iso: string | null | undefined): string {
    return formatRelative(iso);
  }
}

export function maskKey(key: string): string {
  if (key.length <= 12) {
    return '•'.repeat(key.length);
  }
  const hidden = key.slice(8, -4).replace(/[^-]/g, '•');
  return key.slice(0, 8) + hidden + key.slice(-4);
}

@Pipe({ name: 'maskKey' })
export class MaskKeyPipe implements PipeTransform {
  transform(key: string): string {
    return maskKey(key);
  }
}
