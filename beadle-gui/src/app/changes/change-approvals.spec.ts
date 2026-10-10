import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MyDepartment } from '@common/departments/my-department';
import { Notifier } from '@common/core/notifier';
import { changeApproval, changeTask, productionChange } from '../testing/change-fixtures';
import { buttonOf, text } from '@common/testing/dom';
import { ProductionChange } from './change-api';
import {
  ChangeApprovals,
  approvalRows,
  remindedNames,
  reminderHint,
  reminderText,
} from './change-approvals';

const SENT = '2026-10-08T09:30:00Z';
const NOW = Date.parse('2026-10-08T09:32:00Z');

describe('the approvals of a change', () => {
  it('lists the four change approvals and the approval of every change task ProTech holds', () => {
    const rows = approvalRows(
      productionChange({
        tasks: [
          changeTask({ approval: 'REQUESTED' }),
          changeTask({
            number: 'CTASK0020002',
            approval: 'APPROVED',
            approvers: ['Henry Collins'],
          }),
          changeTask({ number: 'CTASK0020003', state: 'CANCELED' }),
          changeTask({ number: 'CTASK0020004', approvers: [] }),
          changeTask({ number: null }),
        ],
      }),
    );

    expect(
      rows.map(({ label, detail, approvers, state, remindable }) => [
        label,
        detail,
        approvers.join(', '),
        state,
        remindable,
      ]),
    ).toEqual([
      ['Business approver', 'Business Approval', '', 'APPROVED', false],
      ['L1 approver', 'Primary Approval', 'Olivia Bennett', 'REQUESTED', true],
      ['L2 approver', 'Secondary Approval', 'James Carter', 'NOT_APPROVED', true],
      ['Support approver', 'Support Approval', 'Jane Smith', 'NOT_APPROVED', true],
      [
        'CTASK0020001',
        'Technology Architecture',
        'Rebecca Lawson, Thomas Ashby',
        'REQUESTED',
        true,
      ],
      ['CTASK0020002', 'Technology Architecture', 'Henry Collins', 'APPROVED', false],
      ['CTASK0020004', 'Technology Architecture', '', 'NOT_APPROVED', false],
    ]);
    expect(rows.map((row) => row.target)).toEqual([
      { approval: 'BUSINESS' },
      { approval: 'L1' },
      { approval: 'L2' },
      { approval: 'SUPPORT' },
      { task: 'CTASK0020001' },
      { task: 'CTASK0020002' },
      { task: 'CTASK0020004' },
    ]);
  });

  it('says why nobody can be reminded from here', () => {
    expect(reminderHint(productionChange(), 3)).toBeNull();
    expect(reminderHint(productionChange(), null)).toBe(
      'Choose your department in Changes to remind the approvers',
    );
    expect(reminderHint(productionChange(), 5)).toBe(
      'Only Corporate Technology can remind its approvers',
    );
    expect(reminderHint(productionChange({ departmentId: null }), 3)).toBe(
      'No department owns CHG0012345, so nobody can remind its approvers in Beadle',
    );
    expect(reminderHint(productionChange({ syncProblem: 'Read timed out' }), 3)).toBe(
      'ProTech cannot be read just now, so no reminder can be sent',
    );
  });

  it('says who was reminded last and when', () => {
    expect(reminderText({ sentAt: SENT, sentTo: ['Olivia Bennett'] }, NOW)).toBe(
      'Olivia Bennett, 2 minutes ago',
    );
    expect(
      remindedNames(
        productionChange({
          approvals: [
            changeApproval('L1', 'Olivia Bennett', 'REQUESTED', {
              sentAt: '2026-10-08T09:00:00Z',
              sentTo: ['Olivia Bennett'],
            }),
            changeApproval('L2', 'James Carter', 'NOT_APPROVED', {
              sentAt: SENT,
              sentTo: ['James Carter'],
            }),
          ],
          tasks: [
            changeTask({ reminder: { sentAt: SENT, sentTo: ['Rebecca Lawson', 'Thomas Ashby'] } }),
            changeTask({
              number: 'CTASK0020002',
              reminder: { sentAt: SENT, sentTo: ['Thomas Ashby'] },
            }),
          ],
        }),
      ),
    ).toEqual(['James Carter', 'Rebecca Lawson', 'Thomas Ashby']);
    const sameMoment = productionChange({
      approvals: [
        changeApproval('L2', 'James Carter', 'NOT_APPROVED', {
          sentAt: SENT,
          sentTo: ['James Carter'],
        }),
      ],
      tasks: [
        changeTask({ reminder: { sentAt: SENT, sentTo: ['Rebecca Lawson', 'Thomas Ashby'] } }),
        changeTask({
          number: 'CTASK0020002',
          reminder: { sentAt: SENT, sentTo: ['Henry Collins'] },
        }),
      ],
    });
    expect(remindedNames(sameMoment, { task: 'CTASK0020002' })).toEqual(['Henry Collins']);
    expect(remindedNames(sameMoment, { approval: 'L2' })).toEqual(['James Carter']);
    expect(remindedNames(sameMoment, { approval: 'L1' })).toEqual([]);
  });
});

describe('ChangeApprovals', () => {
  let http: HttpTestingController;
  let fixture: ComponentFixture<ChangeApprovals>;
  let changed: ProductionChange[];

  const page = () => fixture.nativeElement as HTMLElement;
  const rows = () =>
    [...page().querySelectorAll('tbody tr')].map((row) => [...row.children].map(text));
  const remindAll = () => buttonOf(page(), 'Remind everyone who has not approved');

  async function show(
    change: ProductionChange = productionChange(),
    departmentId: number | null = 3,
  ) {
    TestBed.inject(MyDepartment).choose(departmentId);
    fixture = TestBed.createComponent(ChangeApprovals);
    fixture.componentRef.setInput('change', change);
    fixture.componentInstance.changed.subscribe((value) => changed.push(value));
    fixture.detectChanges();
    await fixture.whenStable();
  }

  async function settle() {
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
  }

  beforeEach(() => {
    changed = [];
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    http.verify();
    TestBed.inject(MyDepartment).choose(null);
  });

  it('shows every approval with its approvers, state and last reminder, and a discreet button for each awaited one', async () => {
    await show(
      productionChange({
        tasks: [
          changeTask({
            approval: 'REQUESTED',
            reminder: {
              sentAt: new Date(Date.now() - 120_000).toISOString(),
              sentTo: ['Rebecca Lawson', 'Thomas Ashby'],
            },
          }),
        ],
      }),
    );

    expect(text(page().querySelector('h2'))).toBe('Approvals');
    expect(rows()).toEqual([
      ['Business approverBusiness Approval', 'Not named in ProTech', 'Approved', '', ''],
      ['L1 approverPrimary Approval', 'Olivia Bennett', 'Requested', '', 'Remind'],
      ['L2 approverSecondary Approval', 'James Carter', 'Not Approved', '', 'Remind'],
      ['Support approverSupport Approval', 'Jane Smith', 'Not Approved', '', 'Remind'],
      ['CTASK approvals'],
      [
        'CTASK0020001Technology Architecture',
        'Rebecca Lawson, Thomas Ashby',
        'Requested',
        'Rebecca Lawson, Thomas Ashby, 2 minutes ago',
        'Remind',
      ],
    ]);
    expect(
      [...page().querySelectorAll('.chip')].map((chip) =>
        chip.className.replace('chip', '').trim(),
      ),
    ).toEqual(['success', 'warning', 'neutral', 'neutral', 'warning']);
    expect(
      [...page().querySelectorAll('button.remind')].map((button) =>
        button.getAttribute('aria-label'),
      ),
    ).toEqual([
      'Remind the approvers of L1 approver',
      'Remind the approvers of L2 approver',
      'Remind the approvers of Support approver',
      'Remind the approvers of CTASK0020001',
    ]);
    expect(buttonOf(page(), 'Remind the approvers of CTASK0020001').title).toBe(
      'Send Rebecca Lawson, Thomas Ashby a reminder',
    );
    expect(remindAll().disabled).toBe(false);
    expect(page().querySelector('.hint')).toBeNull();
    expect(page().querySelector('.no-tasks')).toBeNull();
  });

  it('reminds the approvers of one approval and shows who got the reminder', async () => {
    const success = vi.spyOn(TestBed.inject(Notifier), 'success');
    await show();

    buttonOf(page(), 'Remind the approvers of L1 approver').click();
    await settle();
    const request = http.expectOne('/api/changes/7/reminders');
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual({ departmentId: 3, approval: 'L1' });
    expect(remindAll().disabled).toBe(true);
    const reminded = productionChange({
      approvals: [
        changeApproval('BUSINESS', null, 'APPROVED'),
        changeApproval('L1', 'Olivia Bennett', 'REQUESTED', {
          sentAt: new Date().toISOString(),
          sentTo: ['Olivia Bennett'],
        }),
        changeApproval('L2', 'James Carter'),
        changeApproval('SUPPORT', 'Jane Smith'),
      ],
    });
    request.flush(reminded);
    await settle();

    expect(changed).toEqual([reminded]);
    expect(success).toHaveBeenCalledWith('Reminder sent to Olivia Bennett');
    expect(remindAll().disabled).toBe(false);
  });

  it('reminds everyone who has not approved yet with one button, or a change task on its own', async () => {
    const error = vi.spyOn(TestBed.inject(Notifier), 'error');
    await show();

    remindAll().click();
    await settle();
    const all = http.expectOne('/api/changes/7/reminders');
    expect(all.request.body).toEqual({ departmentId: 3 });
    all.flush(
      { title: 'Conflict', detail: 'ProTech has nobody to remind on CHG0012345' },
      { status: 409, statusText: 'Conflict' },
    );
    await settle();
    expect(error).toHaveBeenCalledWith(
      'The reminder could not be sent: ProTech has nobody to remind on CHG0012345',
    );
    expect(changed).toEqual([]);

    buttonOf(page(), 'Remind the approvers of CTASK0020001').click();
    await settle();
    const one = http.expectOne('/api/changes/7/reminders');
    expect(one.request.body).toEqual({ departmentId: 3, task: 'CTASK0020001' });
    one.flush(productionChange());
    await settle();
    expect(changed).toHaveLength(1);
  });

  it('keeps the buttons for the department of the change while ProTech can be read', async () => {
    await show(productionChange(), 5);

    expect(text(page().querySelector('.hint'))).toBe(
      'Only Corporate Technology can remind its approvers',
    );
    expect(remindAll().disabled).toBe(true);
    expect(remindAll().title).toBe('Only Corporate Technology can remind its approvers');
    expect(
      [...page().querySelectorAll<HTMLButtonElement>('button.remind')].every(
        (button) => button.disabled,
      ),
    ).toBe(true);
    remindAll().click();
    expect(http.match('/api/changes/7/reminders')).toEqual([]);
  });

  it('has nobody to remind once everyone approved or the change is closed, and says a change needs change tasks', async () => {
    const approved = productionChange({
      state: 'IMPLEMENTATION',
      approvals: (['BUSINESS', 'L1', 'L2', 'SUPPORT'] as const).map((role) =>
        changeApproval(role, 'Olivia Bennett', 'APPROVED'),
      ),
      tasks: [changeTask({ approval: 'APPROVED' })],
    });
    await show(approved);

    expect(page().querySelectorAll('button.remind')).toHaveLength(0);
    expect(remindAll().disabled).toBe(true);
    expect(remindAll().title).toBe('Everyone named on the change has approved it');
    fixture.destroy();

    await show(productionChange({ tasks: [] }));
    expect(text(page().querySelector('.no-tasks'))).toBe(
      'No change tasks yet. The change needs at least one approved change task to go In Progress.',
    );
    expect(rows().some((row) => row[0] === 'CTASK approvals')).toBe(false);
    fixture.destroy();

    await show(productionChange({ state: 'CLOSED', tasks: [] }));
    expect(page().querySelector('button')).toBeNull();
    expect(page().querySelector('.no-tasks')).toBeNull();
  });
});
