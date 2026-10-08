import { TestBed } from '@angular/core/testing';
import { text } from '../testing/dom';
import { WorkflowStep } from './change-api';
import { WorkflowProgress, stages } from './workflow-progress';

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
});
