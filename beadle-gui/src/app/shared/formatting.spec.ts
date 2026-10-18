import { capitalized, counted, formatRelative } from './formatting';

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

describe('counted', () => {
  it.each([
    [0, '0 products'],
    [1, '1 product'],
    [12, '12 products'],
  ])('counts %s as %s', (count, text) => {
    expect(counted(count, 'product')).toBe(text);
  });

  it('takes an irregular plural', () => {
    expect(counted(1, 'recovery', 'recoveries')).toBe('1 recovery');
    expect(counted(3, 'recovery', 'recoveries')).toBe('3 recoveries');
  });
});

describe('capitalized', () => {
  it('starts a sentence with a capital and keeps the rest', () => {
    expect(capitalized('change')).toBe('Change');
    expect(capitalized('ProTech CTASK')).toBe('ProTech CTASK');
    expect(capitalized('')).toBe('');
  });
});
