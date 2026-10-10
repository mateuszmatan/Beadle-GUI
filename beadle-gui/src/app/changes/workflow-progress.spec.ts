import { TestBed } from '@angular/core/testing';
import { changeTemplate, productionChange } from '../testing/change-fixtures';
import { text } from '@common/testing/dom';
import { STATES, WorkflowStep } from './change-api';
import { windowText } from './change-model';
import { STATE_MEANINGS, WorkflowProgress, stateNow, stages } from './workflow-progress';

const escalated: WorkflowStep[] = [
  { state: 'DRAFT', enteredAt: '2026-10-07T09:00:00Z' },
  { state: 'BUSINESS_APPROVAL', enteredAt: '2026-10-07T09:02:00Z' },
  { state: 'PRIMARY_APPROVAL', enteredAt: '2026-10-07T09:04:00Z' },
  { state: 'SECONDARY_APPROVAL', enteredAt: '2026-10-07T09:06:00Z' },
  { state: 'CTASK_APPROVAL', enteredAt: '2026-10-07T09:08:00Z' },
  { state: 'IMPLEMENTATION', enteredAt: '2026-10-07T09:10:00Z' },
];

describe('workflow progress', () => {
  it('marks the stages reached, the current one, the skipped ones and the later ones', () => {
    expect(stages('IMPLEMENTATION', escalated).map((stage) => stage.status)).toEqual([
      'done',
      'done',
      'done',
      'done',
      'done',
      'skipped',
      'current',
      'later',
    ]);
    expect(stages('IMPLEMENTATION', escalated)[6].enteredAt).toBe('2026-10-07T09:10:00Z');
    expect(stages('IMPLEMENTATION', escalated)[5].enteredAt).toBeNull();
    expect(stages('DRAFT', escalated.slice(0, 1)).map((stage) => stage.label)).toEqual([
      'Draft',
      'Business Approval',
      'Primary Approval',
      'Secondary Approval',
      'CTask approval',
      'Escalated approval',
      'Implementation',
      'Closed',
    ]);
  });

  it('takes the last time a stage was entered and hides the time of a later stage', () => {
    const again: WorkflowStep[] = [
      { state: 'DRAFT', enteredAt: '2026-10-07T09:00:00Z' },
      { state: 'BUSINESS_APPROVAL', enteredAt: '2026-10-07T09:02:00Z' },
      { state: 'DRAFT', enteredAt: '2026-10-07T09:05:00Z' },
    ];

    const [draft, business] = stages('DRAFT', again);
    expect(draft).toMatchObject({ status: 'current', enteredAt: '2026-10-07T09:05:00Z' });
    expect(business).toMatchObject({ status: 'later', enteredAt: null });
  });

  it('shows the eight stages with the time each was entered', () => {
    const fixture = TestBed.createComponent(WorkflowProgress);
    fixture.componentRef.setInput('state', 'IMPLEMENTATION');
    fixture.componentRef.setInput('workflow', escalated);
    fixture.detectChanges();
    const items = [...(fixture.nativeElement as HTMLElement).querySelectorAll('li')];

    expect(items).toHaveLength(8);
    expect(items.map((item) => item.className)).toEqual([
      'done',
      'done',
      'done',
      'done',
      'done',
      'skipped',
      'current',
      'later',
    ]);
    expect(text(items[0])).toMatch(/^1Draft 7 Oct, \d\d:\d\d$/);
    expect(text(items[5])).toBe('6Escalated approval Skipped');
    expect(items[6].getAttribute('aria-current')).toBe('step');
    expect(text(items[7])).toBe('8Closed');
  });

  it('says in plain words what each state means and what the change waits for', () => {
    expect(STATES.map((state) => STATE_MEANINGS[state.value])).toEqual([
      'Not sent for approval yet',
      'Waiting for the business approver',
      'Waiting for the L1 approver',
      'Waiting for the L2 approver',
      'Waiting for its change tasks to be approved',
      'Short notice: waiting for an escalated approval',
      'Approved, installs in its window',
      'Done',
    ]);
    const change = productionChange();
    const now = (state: (typeof STATES)[number]['value'], template = change.template) =>
      stateNow({ ...change, state, template });
    const { installationStart, installationEnd } = change.schedule;

    expect(now('DRAFT')).toBe(
      'The change is written but not sent for approval yet. The business approver is asked first.',
    );
    expect(now('BUSINESS_APPROVAL')).toBe(
      'Waiting for the business approver to approve the change in ProTech.',
    );
    expect(now('SECONDARY_APPROVAL')).toBe(
      'Waiting for the L2 approver, James Carter, to approve the change in ProTech.',
    );
    expect(
      now(
        'PRIMARY_APPROVAL',
        changeTemplate({
          approvers: { businessApprover: null, l1Manager: null, l2Manager: null },
        }),
      ),
    ).toBe('Waiting for the L1 approver to approve the change in ProTech.');
    expect(now('CTASK_APPROVAL')).toBe('Waiting for the change tasks to be approved in ProTech.');
    expect(now('ESCALATED_APPROVAL')).toBe(
      'The installation is at short notice, so the change waits for an escalated approval in ProTech.',
    );
    expect(now('IMPLEMENTATION')).toBe(
      `Approved. The teams carry out the change tasks in the installation window, ${windowText(installationStart, installationEnd)}.`,
    );
    expect(now('CLOSED')).toBe(
      'Done. The change is closed in ProTech and can no longer be changed.',
    );
  });
});
