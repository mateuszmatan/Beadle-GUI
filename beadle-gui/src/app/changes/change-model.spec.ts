import {
  changeSchedule,
  changeTask,
  changeTemplate,
  changeUpdate,
  jiraVersion,
  productionChange,
  story,
} from '../testing/change-fixtures';
import {
  activeTasks,
  approverNames,
  changeRequest,
  editHint,
  isoDate,
  matchingVersions,
  momentText,
  storiesFollowing,
  timeOf,
  toggled,
  versionText,
  windowText,
} from './change-model';

const at = (text: string) => new Date(text);

describe('change model', () => {
  it('writes the local date and time of a moment', () => {
    expect(isoDate(at('2026-03-04T05:06:00'))).toBe('2026-03-04');
    expect(timeOf(at('2026-03-04T05:06:00'))).toBe('05:06');
  });

  it('shows a time, a window on one day once and a window over several days with both days', () => {
    expect(momentText(at('2026-10-10T06:00:00'))).toBe('Sat, 10 Oct 2026, 06:00');
    expect(windowText(at('2026-10-10T06:00:00'), at('2026-10-10T10:00:00'))).toBe(
      'Sat, 10 Oct 2026, 06:00 to 10:00',
    );
    expect(windowText(at('2026-10-10T22:00:00'), at('2026-10-11T02:00:00'))).toBe(
      'Sat, 10 Oct 2026, 22:00 to Sun, 11 Oct 2026, 02:00',
    );
  });

  it('describes the FixVersions and offers the ones matching the typed text', () => {
    const versions = [
      jiraVersion('CERT 4.2', false, '2026-10-20'),
      jiraVersion('CERT 4.3'),
      jiraVersion('CERT 4.1', true, '2026-09-01'),
    ];

    expect(versionText(versions[0])).toBe('unreleased · 2026-10-20');
    expect(versionText(versions[1])).toBe('unreleased');
    expect(versionText(versions[2])).toBe('released · 2026-09-01');
    expect(matchingVersions(versions, '').map((v) => v.name)).toEqual([
      'CERT 4.2',
      'CERT 4.3',
      'CERT 4.1',
    ]);
    expect(matchingVersions(versions, ' 4.3').map((v) => v.name)).toEqual(['CERT 4.3']);
    expect(matchingVersions(versions, 'cert 4.1')).toHaveLength(3);
  });

  it('chooses newly loaded stories and keeps the ones the user turned off unchosen', () => {
    const loaded = [story('CERT-2', 'A', 'CERT-1'), story('CERT-3', 'B', 'CERT-1')];

    expect(storiesFollowing(loaded, [], new Set())).toEqual(['CERT-2', 'CERT-3']);
    expect(storiesFollowing(loaded, ['CERT-3'], new Set(['CERT-2', 'CERT-3']))).toEqual(['CERT-3']);
    expect(toggled(['A'], 'B', true)).toEqual(['A', 'B']);
    expect(toggled(['A', 'B'], 'B', true)).toEqual(['A', 'B']);
    expect(toggled(['A', 'B'], 'A', false)).toEqual(['B']);
  });

  it('names the approvers that are set, the business approver first and the support approver last', () => {
    expect(approverNames(changeTemplate())).toEqual([
      'Olivia Bennett',
      'James Carter',
      'Jane Smith',
    ]);
    expect(
      approverNames(
        changeTemplate({
          approvers: {
            businessApprover: 'Ann',
            l1Manager: 'Olivia Bennett',
            l2Manager: null,
            supportApprover: null,
          },
        }),
      ),
    ).toEqual(['Ann', 'Olivia Bennett']);
    expect(
      approverNames(
        changeTemplate({
          approvers: {
            l1Manager: null,
            l2Manager: null,
            businessApprover: 'Ann',
            supportApprover: null,
          },
        }),
      ),
    ).toEqual(['Ann']);
  });

  it('asks for a change with the scope, the times and the confirmed template, without tasks', () => {
    const schedule = changeSchedule();
    const template = changeTemplate();

    expect(
      changeRequest(
        {
          productId: 1,
          fixVersion: ' CERT 4.2 ',
          epicKeys: ['CERT-1'],
          storyKeys: ['CERT-2'],
        },
        schedule,
        template,
      ),
    ).toEqual({
      productId: 1,
      fixVersion: 'CERT 4.2',
      epicKeys: ['CERT-1'],
      storyKeys: ['CERT-2'],
      schedule,
      template,
    });
  });

  it('keeps the tasks ProTech has not canceled', () => {
    const canceled = changeTask({ number: 'CTASK0020002', state: 'CANCELED' });
    const closed = changeTask({ number: 'CTASK0020003', state: 'CLOSED' });

    expect(activeTasks([changeTask(), canceled, closed])).toEqual([changeTask(), closed]);
  });

  it('says why a change cannot be edited by the chosen department', () => {
    const change = productionChange();

    expect(editHint(change, 3)).toBeNull();
    expect(editHint(change, null)).toBe('Choose your department in Changes to change it');
    expect(editHint(change, 5)).toBe('Only Corporate Technology can change it');
    expect(editHint({ ...change, departmentName: null }, 5)).toBe(
      'Only its department can change it',
    );
    expect(editHint({ ...change, departmentId: null }, 3)).toBe(
      'No department owns CHG0012345, so nobody can change it in Beadle',
    );
    expect(editHint({ ...change, update: changeUpdate() }, 3)).toBe(
      'The last update is still waiting for ProTech; change it again once ProTech has applied it',
    );
    expect(editHint({ ...change, update: changeUpdate({ status: 'APPLIED' }) }, 3)).toBeNull();
  });
});
