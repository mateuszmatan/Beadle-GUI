import { formatDuration, formatRelative } from './formatting';

describe('formatDuration', () => {
  it.each([
    [null, '–'],
    [undefined, '–'],
    [Number.NaN, '–'],
    [0, '0s'],
    [44.6, '45s'],
    [60, '1m'],
    [725, '12m 5s'],
    [3600, '1h'],
    [12_000, '3h 20m'],
    [172_800, '2d'],
    [187_200, '2d 4h'],
  ])('shows %s seconds as %s', (seconds, text) => {
    expect(formatDuration(seconds)).toBe(text);
  });
});

describe('formatRelative', () => {
  const now = Date.parse('2026-10-04T12:00:00Z');

  it.each([
    [null, '–'],
    ['2026-10-04T11:59:30Z', 'just now'],
    ['2026-10-04T11:55:00Z', '5 minutes ago'],
    ['2026-10-04T09:00:00Z', '3 hours ago'],
    ['2026-10-03T12:00:00Z', 'yesterday'],
    ['2026-09-30T12:00:00Z', '4 days ago'],
    ['2026-07-01T12:00:00Z', '3 months ago'],
  ])('shows %s as %s', (iso, text) => {
    expect(formatRelative(iso, now)).toBe(text);
  });
});
