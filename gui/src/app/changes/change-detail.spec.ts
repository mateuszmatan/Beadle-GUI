import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { MY_DEPARTMENT_KEY, MyDepartment } from '../beadle/my-department';
import {
  changeOptions,
  changeSchedule,
  changeTask,
  changeTemplate,
  changeUpdate,
  productionChange,
} from '../testing/change-fixtures';
import { buttonOf, text } from '../testing/dom';
import { ProductionChange } from './change-api';
import { ChangeDetail, MAX_POLLS, POLL_INTERVAL, updateText } from './change-detail';
import { momentText } from './change-model';
import { ChangeSummary } from './change-summary';
import { PublishedChange } from './published-change';

const zoneNote = `Times are in your time zone, ${Intl.DateTimeFormat().resolvedOptions().timeZone}.`;
const requested = momentText('2026-10-08T09:30:00Z');
const NOW = Date.parse('2026-10-08T09:31:00Z');

describe('the text of an update', () => {
  it('says what ProTech has not applied yet while the update waits', () => {
    expect(updateText(changeUpdate(), NOW)).toBe(
      `Your update of ${requested} is waiting for ProTech: Installation start, Change tasks.`,
    );
    expect(updateText(changeUpdate({ fields: [] }), NOW)).toBe(
      `Your update of ${requested} is waiting for ProTech.`,
    );
  });

  it('says who made the update ProTech applied and when Beadle checked it', () => {
    expect(updateText(changeUpdate({ status: 'APPLIED', fields: [] }), NOW)).toBe(
      `ProTech applied the update of ${requested} by Corporate Technology. Checked 1 minute ago.`,
    );
    expect(
      updateText(
        changeUpdate({ status: 'APPLIED', fields: [], departmentName: null, checkedAt: null }),
        NOW,
      ),
    ).toBe(`ProTech applied the update of ${requested}.`);
  });

  it('says what ProTech did not apply and why', () => {
    expect(
      updateText(
        changeUpdate({
          status: 'NOT_APPLIED',
          fields: ['schedule.installationStart', 'template.approvers', 'unknown'],
          message: 'A minute later ProTech still held its own values, so Beadle shows those.',
        }),
        NOW,
      ),
    ).toBe(
      `ProTech did not apply the update of ${requested}: Installation start, Approvers, unknown. ` +
        'A minute later ProTech still held its own values, so Beadle shows those.',
    );
    expect(updateText(changeUpdate({ status: 'NOT_APPLIED', fields: ['tasks'] }), NOW)).toBe(
      `ProTech did not apply the update of ${requested}: Change tasks.`,
    );
  });
});

describe('ChangeDetail', () => {
  let http: HttpTestingController;
  let fixture: ComponentFixture<ChangeDetail>;

  const page = () => fixture.nativeElement as HTMLElement;

  async function settle() {
    TestBed.tick();
    await new Promise((resolve) => setTimeout(resolve));
    TestBed.tick();
    fixture.detectChanges();
  }

  async function show(change: ProductionChange | null, departmentId: number | null = 3) {
    TestBed.inject(MyDepartment).choose(departmentId);
    fixture = TestBed.createComponent(ChangeDetail);
    fixture.componentRef.setInput('id', 7);
    await settle();
    if (change) {
      http.expectOne('/api/changes/7').flush(change);
      await settle();
      http.match('/api/changes/options').forEach((request) => request.flush(changeOptions()));
      await settle();
    }
  }

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    });
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    vi.useRealTimers();
    http.match('/api/changes/options').forEach((request) => request.flush(changeOptions()));
    http.verify();
    localStorage.removeItem(MY_DEPARTMENT_KEY);
  });

  it('shows a change with its workflow, FixVersion, schedule, ProTech fields, tasks and description', async () => {
    await show(
      productionChange({
        url: 'https://bbh.service-now.com/CHG0012345',
        schedule: changeSchedule({
          downtimeStart: '2026-10-10T06:00:00Z',
          downtimeEnd: '2026-10-10T07:00:00Z',
        }),
        template: changeTemplate({
          release: 'CERT 4.2',
          incident: 'INC0012345',
          risk: 'Moderate',
          downtime: true,
          privilegedAccess: {
            required: true,
            users: [{ user: 'Jane Smith', account: 'adm_jsmith' }],
          },
          secureCodingTicket: 'SEC-12',
        }),
        tasks: [
          changeTask(),
          changeTask({ number: 'CTASK0020002', state: 'CANCELED', shortDescription: 'Old one' }),
          changeTask({ number: null, shortDescription: 'Tell the users' }),
        ],
      }),
    );
    const block = (title: string) =>
      [...page().querySelectorAll('dso-change-summary section')].find(
        (section) => text(section.querySelector('h3')) === title,
      );
    const rows = (title: string) =>
      [...block(title)!.querySelectorAll('dt')].map(
        (term) => `${text(term)}: ${text(term.nextElementSibling)}`,
      );

    expect(text(page().querySelector('h1'))).toBe('CHG0012345');
    expect(text(page().querySelector('.breadcrumb'))).toBe('Changes/CHG0012345');
    expect([...page().querySelectorAll('h1, h2, h3')].map((heading) => heading.tagName)).toEqual([
      'H1',
      'H2',
      'H2',
      'H3',
      'H3',
      'H3',
      'H3',
      'H3',
      'H3',
      'H3',
      'H3',
      'H2',
      'H2',
    ]);
    expect(page().querySelectorAll('dso-workflow-progress li')).toHaveLength(8);
    expect(text(page().querySelector('dso-workflow-progress li[aria-current="step"]'))).toContain(
      'Primary Approval',
    );
    expect(rows('Generic request data')).toEqual([
      'Change number: CHG0012345',
      'Approval: Requested',
      'Opened By: Mateusz Matan',
      'State: Primary Approval',
      'Requested For: not set',
      'Requested By: not set',
      'Department: not set',
      'Assignment group: Technology Architecture',
      'Category: Application',
      'Assigned to: not set',
      'Type: Standard',
      'Release: CERT 4.2',
      'Affected CI: CertScanner',
      'Incident: INC0012345',
      'Direct business service: not set',
      'Problem: not set',
      'Risk: Moderate',
      'Affected clients: not set',
      'Users affected: not set',
    ]);
    expect(rows('Jira')).toEqual([
      'Product: CertScanner (CERT)',
      'Product department: Corporate Technology',
      'FixVersion: CERT 4.2',
      'Jira project: CERT',
      'Jira: CERT-1 CERT-2',
    ]);
    expect(rows('Schedule')).toEqual([
      expect.stringMatching(/^Installation: Sat, 10 Oct 2026, \d\d:00 to \d\d:00$/),
      expect.stringMatching(/^Validation: Sat, 10 Oct 2026, /),
      expect.stringMatching(/^First use: Mon, 12 Oct 2026, /),
      expect.stringMatching(/^Downtime: Sat, 10 Oct 2026, \d\d:00 to \d\d:00$/),
    ]);
    expect(text(block('Schedule')!.querySelector('.note'))).toBe(zoneNote);
    expect(rows('Approval and Notification')).toEqual([
      'Business approver: not set',
      'L1 approver: Olivia Bennett',
      'L2 approver: James Carter',
    ]);
    expect(rows('Secure coding')).toEqual(['Secure coding ticket number: SEC-12']);
    expect(rows('Privileged access')).toEqual(['Jane Smith: adm_jsmith']);
    expect(rows('Risk assessment')).toContain('Number of BBH users impacted: 5-25');
    expect(
      [...block('Planning')!.querySelectorAll('h4')].map(
        (title) => `${text(title)}: ${text(title.nextElementSibling)}`,
      ),
    ).toContain('Validation plan: Run the smoke tests.');
    expect([...page().querySelectorAll('.tasks li')].map(text)).toEqual([
      'CTASK0020001OpenDeploy CertScanner to productionDeploy the release of CertScanner.',
      'CTASK0020002CanceledOld oneDeploy the release of CertScanner.',
      'not in ProTech yetOpenTell the usersDeploy the release of CertScanner.',
    ]);
    expect(page().querySelector('.tasks li.canceled')).not.toBeNull();
    expect(text(page().querySelector('pre'))).toBe('Production release of CertScanner (CERT).');
    expect(page().querySelector('a[href="https://bbh.service-now.com/CHG0012345"]')).not.toBeNull();
    expect(page().querySelector('a[href="/beadle/admin/products/1"]')).not.toBeNull();
    expect(page().querySelector('a[href="/beadle/changes/7/edit"]')).not.toBeNull();
    expect(page().querySelector('.banner.update')).toBeNull();
  });

  it('says when it read the change from ProTech and when ProTech could not be reached', async () => {
    vi.useFakeTimers({ toFake: ['Date'] });
    vi.setSystemTime(NOW);
    await show(productionChange({ syncedAt: '2026-10-08T09:30:50Z' }));
    expect(text(page().querySelector('.note.sync'))).toBe('Read from ProTech just now');
    fixture.destroy();

    await show(
      productionChange({
        syncedAt: '2026-10-08T08:31:00Z',
        syncProblem: 'ProTech could not be reached: timed out.',
      }),
    );
    expect(text(page().querySelector('.sync-problem'))).toBe(
      'ProTech could not be reached: timed out. Beadle shows what it last read from ProTech 1 hour ago.',
    );
    expect(page().querySelector('.note.sync')).toBeNull();
    fixture.destroy();

    await show(
      productionChange({ syncedAt: null, syncProblem: 'ProTech has no change CHG0012345.' }),
    );
    expect(text(page().querySelector('.sync-problem'))).toBe(
      'ProTech has no change CHG0012345. Beadle shows what it last read from ProTech.',
    );
  });

  it('lets only the department of an open change edit it', async () => {
    await show(productionChange(), null);
    expect(buttonOf(page(), 'Edit').disabled).toBe(true);
    expect(text(page().querySelector('.edit-hint'))).toBe(
      'Choose your department in Changes to change it',
    );
    fixture.destroy();

    await show(productionChange(), 5);
    expect(text(page().querySelector('.edit-hint'))).toBe(
      'Only Corporate Technology can change it',
    );
    fixture.destroy();

    await show(productionChange({ state: 'CLOSED' }), 3);
    expect(buttonOf(page(), 'Edit')).toBeUndefined();
    expect(page().querySelector('a[href="/beadle/changes/7/edit"]')).toBeNull();
  });

  it('reads the change again while ProTech has not applied the update', async () => {
    vi.useFakeTimers({ toFake: ['setTimeout', 'clearTimeout'] });
    TestBed.inject(PublishedChange).hand(productionChange({ update: changeUpdate() }));
    fixture = TestBed.createComponent(ChangeDetail);
    fixture.componentRef.setInput('id', 7);
    TestBed.tick();
    await vi.advanceTimersByTimeAsync(0);
    TestBed.tick();
    fixture.detectChanges();

    http.expectNone('/api/changes/7');
    const banner = () => page().querySelector('.banner.update');
    expect(banner()?.classList).toContain('info');
    expect(text(banner())).toContain('is waiting for ProTech: Installation start, Change tasks.');

    await vi.advanceTimersByTimeAsync(POLL_INTERVAL);
    TestBed.tick();
    http
      .expectOne('/api/changes/7')
      .flush(productionChange({ update: changeUpdate({ status: 'APPLIED', fields: [] }) }));
    await vi.advanceTimersByTimeAsync(0);
    TestBed.tick();
    fixture.detectChanges();

    expect(banner()?.classList).toContain('success');
    expect(text(banner())).toContain('ProTech applied the update of');
    await vi.advanceTimersByTimeAsync(POLL_INTERVAL * 2);
    http.expectNone('/api/changes/7');
  });

  it('keeps the change and reads it again when a read fails while ProTech has not applied the update', async () => {
    vi.useFakeTimers({ toFake: ['setTimeout', 'clearTimeout'] });
    TestBed.inject(PublishedChange).hand(productionChange({ update: changeUpdate() }));
    fixture = TestBed.createComponent(ChangeDetail);
    fixture.componentRef.setInput('id', 7);
    TestBed.tick();
    const banner = () => page().querySelector('.banner.update');

    await vi.advanceTimersByTimeAsync(POLL_INTERVAL);
    TestBed.tick();
    http
      .expectOne('/api/changes/7')
      .flush({ detail: 'Bad gateway' }, { status: 502, statusText: 'Bad Gateway' });
    await vi.advanceTimersByTimeAsync(0);
    TestBed.tick();
    fixture.detectChanges();
    expect(page().querySelector('h1')?.textContent).toContain('CHG');
    expect(banner()?.classList).toContain('info');

    await vi.advanceTimersByTimeAsync(POLL_INTERVAL);
    TestBed.tick();
    http
      .expectOne('/api/changes/7')
      .flush(productionChange({ update: changeUpdate({ status: 'APPLIED', fields: [] }) }));
    await vi.advanceTimersByTimeAsync(0);
    TestBed.tick();
    fixture.detectChanges();
    expect(banner()?.classList).toContain('success');
  });

  it('stops reading the change again after a while', async () => {
    vi.useFakeTimers({ toFake: ['setTimeout', 'clearTimeout'] });
    const pending = productionChange({ update: changeUpdate() });
    TestBed.inject(PublishedChange).hand(pending);
    fixture = TestBed.createComponent(ChangeDetail);
    fixture.componentRef.setInput('id', 7);
    TestBed.tick();

    for (let poll = 0; poll < MAX_POLLS; poll++) {
      await vi.advanceTimersByTimeAsync(POLL_INTERVAL);
      TestBed.tick();
      http.expectOne('/api/changes/7').flush(pending);
      TestBed.tick();
    }
    await vi.advanceTimersByTimeAsync(POLL_INTERVAL * 2);
    TestBed.tick();
    http.expectNone('/api/changes/7');
  });

  it('shows what ProTech did not apply', async () => {
    await show(
      productionChange({
        update: changeUpdate({
          status: 'NOT_APPLIED',
          fields: ['schedule.installationStart'],
          message: 'A minute later ProTech still held its own values, so Beadle shows those.',
        }),
      }),
    );

    const banner = page().querySelector('.banner.update');
    expect(banner?.classList).toContain('danger');
    expect(text(banner)).toBe(
      `ProTech did not apply the update of ${requested}: Installation start. ` +
        'A minute later ProTech still held its own values, so Beadle shows those.',
    );
  });

  it('says when a change cannot be found', async () => {
    fixture = TestBed.createComponent(ChangeDetail);
    fixture.componentRef.setInput('id', 99);
    await settle();
    http
      .expectOne('/api/changes/99')
      .flush({ detail: 'Change 99 does not exist' }, { status: 404, statusText: 'Not Found' });
    await settle();

    expect(text(page().querySelector('.banner'))).toBe('Change 99 does not exist');
    expect(text(page().querySelector('.breadcrumb'))).toBe('Changes/Change');
  });
});

describe('ChangeSummary', () => {
  const render = (change: ProductionChange) => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    const fixture = TestBed.createComponent(ChangeSummary);
    fixture.componentRef.setInput('change', change);
    fixture.detectChanges();
    const blocks = [...(fixture.nativeElement as HTMLElement).querySelectorAll('section')];
    return (title: string) =>
      [
        ...blocks
          .find((block) => text(block.querySelector('h3')) === title)!
          .querySelectorAll('dt'),
      ].map((term) => `${text(term)}: ${text(term.nextElementSibling)}`);
  };

  it('says when no privileged access or downtime is needed', () => {
    const rows = render(productionChange());

    expect(rows('Privileged access')).toEqual(['Needed: No']);
    expect(rows('Schedule')).toContain('Downtime: No');
  });

  it('shows the approval of each state and a downtime without its window', () => {
    const approval = (state: ProductionChange['state']) =>
      render(productionChange({ state, number: null }))('Generic request data').slice(0, 2);

    expect(approval('DRAFT')).toEqual([
      'Change number: Given by ProTech when raised',
      'Approval: Not Yet Requested',
    ]);
    TestBed.resetTestingModule();
    expect(approval('IMPLEMENTATION')[1]).toBe('Approval: Approved');
    TestBed.resetTestingModule();
    expect(
      render(productionChange({ template: changeTemplate({ downtime: true }) }))('Schedule'),
    ).toContain('Downtime: Yes');
  });
});
