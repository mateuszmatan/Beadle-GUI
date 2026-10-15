import { actionPage, actionRecord } from '../testing/fixtures';
import {
  NO_FILTERS,
  actionText,
  atText,
  dayStart,
  detailsText,
  durationText,
  lastPage,
  objectText,
  outcomeText,
  queryOf,
  rangeText,
  reversed,
} from './action-history-model';

describe('the action history model', () => {
  it('names actions and outcomes as people read them, and unknown ones as they came', () => {
    expect(actionText('CHANGE_SYNCED')).toBe('Change synced from ProTech');
    expect(actionText('CALLBACK_INTERNAL')).toBe('Beadle-Internal callback');
    expect(actionText('SOMETHING_NEW')).toBe('SOMETHING_NEW');
    expect(outcomeText('FAILURE')).toBe('Failure');
    expect(outcomeText('LATER')).toBe('LATER');
  });

  it('names the object by its type and its key, else its id', () => {
    expect(objectText(actionRecord())).toBe('Change CHG0031001');
    expect(
      objectText(actionRecord({ objectType: 'CHANGE_PROFILE', objectId: 12, objectKey: null })),
    ).toBe('Change template of product #12');
    expect(
      objectText(actionRecord({ objectType: 'TICKET', objectId: null, objectKey: 'SCP-1' })),
    ).toBe('Ticket SCP-1');
    expect(
      objectText(actionRecord({ objectType: 'PRODUCT', objectId: null, objectKey: null })),
    ).toBe('Product');
    expect(objectText(actionRecord({ objectType: null, objectId: null, objectKey: null }))).toBe(
      '–',
    );
  });

  it('writes the moment to the second and the duration in milliseconds or seconds', () => {
    const at = '2026-10-15T09:00:05Z';
    const local = new Date(at);
    expect(atText(at)).toContain('15 Oct 2026');
    expect(atText(at)).toContain(`${String(local.getHours()).padStart(2, '0')}:00:05`);
    expect(durationText(812)).toBe('812 ms');
    expect(durationText(5012)).toBe('5.0 s');
    expect(durationText(61_400)).toBe('61 s');
  });

  it('pretty-prints JSON details and shows trimmed or plain ones as recorded', () => {
    expect(detailsText('{"code":"CERT","tags":[1]}')).toBe(
      '{\n  "code": "CERT",\n  "tags": [\n    1\n  ]\n}',
    );
    expect(detailsText('{"code":"CER...')).toBe('{"code":"CER...');
    expect(detailsText(null)).toBe('');
  });

  it('turns days of the calendar into the instants where they start', () => {
    expect(dayStart('2026-10-15')).toBe(new Date(2026, 9, 15).toISOString());
    expect(dayStart('2026-10-31', 1)).toBe(new Date(2026, 10, 1).toISOString());
    expect(dayStart('')).toBeUndefined();
    expect(dayStart('15/10/2026')).toBeUndefined();
  });

  it('finds a range whose last day comes before the first', () => {
    expect(reversed({ from: '2026-10-15', to: '2026-10-14' })).toBe(true);
    expect(reversed({ from: '2026-10-15', to: '2026-10-15' })).toBe(false);
    expect(reversed({ from: '', to: '2026-10-14' })).toBe(false);
    expect(reversed({ from: '2026-10-15', to: '' })).toBe(false);
  });

  it('asks only for the filters in use, the last day in full', () => {
    expect(queryOf(NO_FILTERS, 0, 50)).toEqual({ page: 0, size: 50 });
    expect(
      queryOf(
        {
          user: 'Ann Lee',
          action: 'PRODUCT_CREATED',
          objectKey: '  chg003 ',
          outcome: 'FAILURE',
          from: '2026-10-14',
          to: '2026-10-15',
        },
        2,
        100,
      ),
    ).toEqual({
      user: 'Ann Lee',
      action: 'PRODUCT_CREATED',
      objectKey: 'chg003',
      outcome: 'FAILURE',
      from: new Date(2026, 9, 14).toISOString(),
      to: new Date(2026, 9, 16).toISOString(),
      page: 2,
      size: 100,
    });
  });

  it('says which rows a page holds and which page is the last', () => {
    const rows = [actionRecord(), actionRecord({ id: 40 })];
    expect(rangeText(actionPage(rows, { page: 2, size: 25, total: 52 }))).toBe('Rows 51–52 of 52');
    expect(rangeText(actionPage([], { total: 0 }))).toBe('No rows');
    expect(rangeText(actionPage([], { page: 4, total: 1 }))).toBe('1 row, none on this page');
    expect(lastPage(actionPage(rows, { size: 25, total: 52 }))).toBe(2);
    expect(lastPage(actionPage(rows, { size: 25, total: 50 }))).toBe(1);
    expect(lastPage(actionPage([], { total: 0 }))).toBe(0);
  });
});
