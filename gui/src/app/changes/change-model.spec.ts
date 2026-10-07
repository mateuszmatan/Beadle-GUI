import { epic, story } from '../testing/change-fixtures';
import {
  changeRequest,
  fromLocalInput,
  isoDate,
  localInput,
  presetWindow,
  recentDays,
  storiesFollowing,
  storiesText,
  toggled,
  windowProblem,
  windowText,
} from './change-model';

const at = (text: string) => new Date(text);

describe('change model', () => {
  it('suggests tonight after business hours, or tomorrow night when it is already evening', () => {
    expect(presetWindow('tonight', at('2026-10-07T09:30:00'))).toEqual({
      start: at('2026-10-07T20:00:00'),
      end: at('2026-10-07T23:00:00'),
    });
    expect(presetWindow('tonight', at('2026-10-07T19:30:00'))).toEqual({
      start: at('2026-10-08T20:00:00'),
      end: at('2026-10-08T23:00:00'),
    });
  });

  it('suggests the next Saturday morning for the weekend and leaves another time to the user', () => {
    expect(presetWindow('weekend', at('2026-10-07T09:30:00'))).toEqual({
      start: at('2026-10-10T06:00:00'),
      end: at('2026-10-10T10:00:00'),
    });
    expect(presetWindow('weekend', at('2026-10-10T05:00:00'))?.start).toEqual(
      at('2026-10-17T06:00:00'),
    );
    expect(presetWindow('custom', at('2026-10-07T09:30:00'))).toBeNull();
  });

  it('writes dates for the date and time inputs and reads them back', () => {
    expect(isoDate(at('2026-03-04T05:06:00'))).toBe('2026-03-04');
    expect(localInput(at('2026-03-04T05:06:00'))).toBe('2026-03-04T05:06');
    expect(fromLocalInput('2026-03-04T05:06')).toEqual(at('2026-03-04T05:06:00'));
    expect(fromLocalInput('')).toBeNull();
    expect(fromLocalInput('not a date')).toBeNull();
    expect(recentDays(at('2026-10-07T09:30:00'))).toEqual({ from: '2026-07-09', to: '2026-10-07' });
    expect(recentDays(at('2026-10-07T09:30:00'), 7)).toEqual({ from: '2026-09-30', to: '2026-10-07' });
  });

  it('says what is wrong with a change window', () => {
    const now = at('2026-10-07T09:30:00');
    const window = (start: string, end: string) => ({ start: at(start), end: at(end) });

    expect(windowProblem(null, now)).toBe('Choose when the change starts and ends');
    expect(windowProblem(window('2026-10-07T09:00:00', '2026-10-07T11:00:00'), now)).toBe(
      'The change must start in the future',
    );
    expect(windowProblem(window('2026-10-08T09:00:00', '2026-10-08T09:00:00'), now)).toBe(
      'The change must end after it starts',
    );
    expect(windowProblem(window('2026-10-08T09:00:00', '2026-10-15T10:00:00'), now)).toBe(
      'A change window may last at most 7 days',
    );
    expect(windowProblem(window('2026-10-08T09:00:00', '2026-10-15T09:00:00'), now)).toBeNull();
  });

  it('shows a window on one day once and a window over several days with both days', () => {
    expect(windowText(at('2026-10-10T06:00:00'), at('2026-10-10T10:00:00'))).toBe(
      'Sat, 10 Oct 2026, 06:00 to 10:00',
    );
    expect(windowText(at('2026-10-10T22:00:00'), at('2026-10-11T02:00:00'))).toBe(
      'Sat, 10 Oct 2026, 22:00 to Sun, 11 Oct 2026, 02:00',
    );
  });

  it('chooses newly loaded stories and keeps the ones the user turned off unchosen', () => {
    const loaded = [story('CERT-2', 'A', 'CERT-1'), story('CERT-3', 'B', 'CERT-1')];

    expect(storiesFollowing(loaded, [], new Set())).toEqual(['CERT-2', 'CERT-3']);
    expect(storiesFollowing(loaded, ['CERT-3'], new Set(['CERT-2', 'CERT-3']))).toEqual(['CERT-3']);
    expect(storiesText(1)).toBe('1 story');
    expect(storiesText(0)).toBe('0 stories');
    expect(toggled(['A'], 'B', true)).toEqual(['A', 'B']);
    expect(toggled(['A', 'B'], 'B', true)).toEqual(['A', 'B']);
    expect(toggled(['A', 'B'], 'A', false)).toEqual(['B']);
  });

  it('asks for a change with the chosen services, Jira keys and window', () => {
    const window = { start: new Date('2026-10-10T06:00:00Z'), end: new Date('2026-10-10T10:00:00Z') };

    expect(changeRequest(1, [10, 11], [epic('CERT-1', 'A').key], ['CERT-2'], window)).toEqual({
      productId: 1,
      serviceIds: [10, 11],
      epicKeys: ['CERT-1'],
      storyKeys: ['CERT-2'],
      start: '2026-10-10T06:00:00.000Z',
      end: '2026-10-10T10:00:00.000Z',
    });
    expect(changeRequest(1, [], [], [], null)).toMatchObject({ start: null, end: null });
  });
});
