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
  releaseDetails,
  taskDetails,
} from '../testing/change-fixtures';
import { buttonOf, inputOf, text } from '../testing/dom';
import { ProductionChange } from './change-api';
import { ChangeDetail, MAX_POLLS, POLL_INTERVAL, updateText } from './change-detail';
import { momentText } from './change-model';
import { localInput } from './change-schedule-model';
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

  it('shows where a change is, when it installs, what it delivers, who approves, its tasks and the rest folded away', async () => {
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
          changeTask({
            details: releaseDetails(
              'Deploy CertScanner to production',
              'Deploy the release of CertScanner.',
              { platform: 'OpenShift', application: 'OCP' },
            ),
            start: '2026-10-10T06:01:00Z',
            approval: 'Requested',
          }),
          changeTask({
            number: 'CTASK0020002',
            state: 'CANCELED',
            details: taskDetails('Old one', 'Deploy the release of CertScanner.'),
          }),
          changeTask({
            number: null,
            details: taskDetails('Tell the users', 'Send the e-mail.', {
              assignedTo: 'Mateusz Matan',
            }),
          }),
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
    expect([...page().querySelectorAll('h1, h2')].map(text)).toEqual([
      'CHG0012345',
      'Where the change is',
      'The change at a glance',
      'Change tasks',
    ]);
    expect([...page().querySelectorAll('dso-change-summary h3')].map(text)).toEqual([
      'When it installs',
      'What it delivers',
      'Who approves',
      'Request details',
      'Risk assessment',
      'Privileged access',
      'Secure coding',
      'Planning',
    ]);
    expect([...page().querySelectorAll('dso-panel .panel-title')].map(text)).toEqual([
      'All ProTech fields',
      'Text sent to ProTech',
    ]);
    expect(
      [...page().querySelectorAll('dso-panel .accordion-button')].map((button) =>
        button.getAttribute('aria-expanded'),
      ),
    ).toEqual(['false', 'false']);
    expect(page().querySelectorAll('dso-workflow-progress li')).toHaveLength(8);
    expect(text(page().querySelector('dso-workflow-progress li[aria-current="step"]'))).toContain(
      'Primary Approval',
    );
    expect(text(page().querySelector('.now'))).toBe(
      'Primary Approval. Waiting for the L1 approver, Olivia Bennett, to approve the change in ProTech.',
    );
    expect(rows('Request details')).toEqual([
      'Change number: CHG0012345',
      'Approval: Requested',
      'Opened by: Mateusz Matan',
      'State: Primary Approval',
      'Requested for: not set',
      'Requested by: not set',
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
    expect(rows('What it delivers')).toEqual([
      'Product: CertScanner (CERT)',
      'Product department: Corporate Technology',
      'FixVersion: CERT 4.2',
      'Jira project: CERT',
      'Epics: CERT-1',
      'Stories: CERT-2',
    ]);
    expect(text(block('What it delivers')!.querySelector('.note'))).toBe(
      'FixVersion is the Jira release the change delivers.',
    );
    expect(rows('When it installs')).toEqual([
      expect.stringMatching(/^Installation: Sat, 10 Oct 2026, \d\d:00 to \d\d:00$/),
      expect.stringMatching(/^Validation: Sat, 10 Oct 2026, /),
      expect.stringMatching(/^First use: Mon, 12 Oct 2026, /),
      expect.stringMatching(/^Downtime: Sat, 10 Oct 2026, \d\d:00 to \d\d:00$/),
    ]);
    expect(text(block('When it installs')!.querySelector('.note'))).toBe(zoneNote);
    expect(rows('Who approves')).toEqual([
      'Business approver: not set',
      'L1 approver: Olivia Bennett',
      'L2 approver: James Carter',
    ]);
    expect(rows('Secure coding')).toEqual([
      'Secure coding ticket number: SEC-12',
      'APO number: APO-12345',
      'Bitbucket URL: https://bitbucket.bbh.com/projects/CERT/repos/cert',
      'Artifact link: https://jenkins.bbh.com/job/CERT/job/cert-release/',
      'QC application link: https://cert.qc.bbh.com',
    ]);
    expect(rows('Privileged access')).toEqual(['Jane Smith: adm_jsmith']);
    expect(rows('Risk assessment')).toContain('Number of BBH users impacted: 5-25');
    expect(
      [...block('Planning')!.querySelectorAll('h4')].map(
        (title) => `${text(title)}: ${text(title.nextElementSibling)}`,
      ),
    ).toContain('Validation plan: Run the smoke tests.');
    const tasks = [...page().querySelectorAll<HTMLElement>('dso-change-tasks-form .task-row')];
    expect(tasks.map((task) => text(task.querySelector('.task-head')))).toEqual([
      '1Release ManagementOpenNot done yet; waiting for approval.',
      '2Change taskCanceledCanceled; no longer part of the change.',
      '3Change taskOpenNot done yet; approval not requested yet.',
    ]);
    expect(inputOf(tasks[0], 'Change number').value).toBe('CHG0012345');
    expect(inputOf(tasks[0], 'Task start').value).toBe(localInput(new Date('2026-10-10T06:01:00Z')));
    expect(inputOf(tasks[0], 'Application').value).toBe('OCP');
    expect(inputOf(tasks[0], 'Description').value).toBe('Deploy the release of CertScanner.');
    expect(inputOf(tasks[2], 'Assigned to').value).toBe('Mateusz Matan');
    expect(inputOf(tasks[2], 'Number').placeholder).toBe('Given by ProTech when created');
    expect(
      [...page().querySelectorAll('dso-change-tasks-form input, dso-change-tasks-form textarea')].every(
        (field) => (field as HTMLInputElement).disabled,
      ),
    ).toBe(true);
    expect(page().querySelector('dso-change-tasks-form button')).toBeNull();
    expect(tasks[1].classList).toContain('canceled');
    expect(text(page().querySelector('dso-change-tasks-form')?.previousElementSibling)).toBe(
      'A change task (CTASK) is a piece of work inside the change, done by one team. ProTech asks for their approval in the CTask approval stage.',
    );
    expect(page().querySelector('.secure-coding-missing')).toBeNull();
    expect(text(page().querySelector('pre'))).toBe('Production release of CertScanner (CERT).');
    expect(text(page().querySelector('a[href="https://bbh.service-now.com/CHG0012345"]'))).toBe(
      'Open in ProTech',
    );
    expect(text(page().querySelector('a[href="/beadle/admin/products/1"]'))).toBe(
      'Open the product',
    );
    expect(text(page().querySelector('a[href="/beadle/changes/7/edit"]'))).toBe('Edit the change');
    expect(page().querySelector('.banner.update')).toBeNull();
  });

  it('says how to add the change tasks of a change without any', async () => {
    await show(productionChange({ tasks: [] }));

    expect(text(page().querySelector('.none'))).toBe(
      'No change tasks yet. Add them with Edit the change.',
    );
    expect(page().querySelector('dso-change-tasks-form')).toBeNull();
    fixture.destroy();

    await show(productionChange({ tasks: [], state: 'CLOSED' }));
    expect(text(page().querySelector('.none'))).toBe('None');
  });

  it('asks for the secure coding ticket of an open change of your department that has none', async () => {
    const unticketed = productionChange({
      template: changeTemplate({ secureCodingTicket: null }),
    });
    await show(unticketed);

    expect(text(page().querySelector('.secure-coding-missing span'))).toBe(
      'CHG0012345 has no secure coding ticket yet. Create it in CyberTrack, the Jira project SCP.',
    );
    expect(
      page().querySelector('.secure-coding-missing a')?.getAttribute('href'),
    ).toBe('/beadle/changes/7/secure-coding');
    fixture.destroy();

    await show(unticketed, 4);
    expect(page().querySelector('.secure-coding-missing')).toBeNull();
    fixture.destroy();

    await show(productionChange({ template: changeTemplate({ secureCodingTicket: null }), state: 'CLOSED' }));
    expect(page().querySelector('.secure-coding-missing')).toBeNull();
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
    expect(text(page().querySelector('.sync-problem > span'))).toBe(
      'ProTech could not be reached: timed out. Beadle shows what it last read from ProTech 1 hour ago. ' +
        'Try again in a moment; if it keeps failing, tell the portal administrator.',
    );
    expect(page().querySelector('.note.sync')).toBeNull();
    buttonOf(page(), 'Try again').click();
    await settle();
    http.expectOne('/api/changes/7').flush(productionChange({ syncedAt: '2026-10-08T09:31:00Z' }));
    await settle();
    expect(page().querySelector('.sync-problem')).toBeNull();
    expect(text(page().querySelector('.note.sync'))).toBe('Read from ProTech just now');
    fixture.destroy();

    await show(
      productionChange({ syncedAt: null, syncProblem: 'ProTech has no change CHG0012345.' }),
    );
    expect(text(page().querySelector('.sync-problem > span'))).toBe(
      'ProTech has no change CHG0012345. Beadle shows what it last read from ProTech. ' +
        'Try again in a moment; if it keeps failing, tell the portal administrator.',
    );
  });

  it('lets only the department of an open change edit it', async () => {
    await show(productionChange(), null);
    expect(buttonOf(page(), 'Edit the change').disabled).toBe(true);
    expect(text(page().querySelector('.edit-hint'))).toBe(
      'Choose your department in Changes to change it',
    );
    expect(buttonOf(page(), 'Edit the change').getAttribute('aria-describedby')).toBe('edit-hint');
    fixture.destroy();

    await show(productionChange(), 5);
    expect(text(page().querySelector('.edit-hint'))).toBe(
      'Only Corporate Technology can change it',
    );
    fixture.destroy();

    await show(productionChange({ state: 'CLOSED' }), 3);
    expect(buttonOf(page(), 'Edit the change')).toBeUndefined();
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
    expect(text(banner())).toContain('This page checks again every few seconds.');

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

  it('stops reading the change again after a while, says so and checks again on request', async () => {
    vi.useFakeTimers({ toFake: ['setTimeout', 'clearTimeout'] });
    const pending = productionChange({ update: changeUpdate() });
    TestBed.inject(PublishedChange).hand(pending);
    fixture = TestBed.createComponent(ChangeDetail);
    fixture.componentRef.setInput('id', 7);
    TestBed.tick();
    await vi.advanceTimersByTimeAsync(0);
    TestBed.tick();
    fixture.detectChanges();
    const banner = () => page().querySelector('.banner.update');
    expect(text(banner())).toContain('This page checks again every few seconds.');
    expect(buttonOf(page(), 'Check again')).toBeUndefined();

    for (let poll = 0; poll < MAX_POLLS; poll++) {
      await vi.advanceTimersByTimeAsync(POLL_INTERVAL);
      TestBed.tick();
      http.expectOne('/api/changes/7').flush(pending);
      TestBed.tick();
    }
    await vi.advanceTimersByTimeAsync(POLL_INTERVAL * 2);
    TestBed.tick();
    http.expectNone('/api/changes/7');
    fixture.detectChanges();
    expect(text(banner())).toContain(
      'ProTech is taking longer than usual, so this page stopped checking.',
    );

    buttonOf(page(), 'Check again').click();
    TestBed.tick();
    await vi.advanceTimersByTimeAsync(POLL_INTERVAL);
    TestBed.tick();
    http.expectOne('/api/changes/7').flush(pending);
    TestBed.tick();
    fixture.detectChanges();
    expect(text(banner())).toContain('This page checks again every few seconds.');
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
        'A minute later ProTech still held its own values, so Beadle shows those. ' +
        'Edit the change to send these values again.',
    );
    fixture.destroy();

    await show(
      productionChange({
        state: 'CLOSED',
        update: changeUpdate({ status: 'NOT_APPLIED', fields: ['schedule.installationStart'] }),
      }),
    );
    expect(text(page().querySelector('.banner.update'))).toBe(
      `ProTech did not apply the update of ${requested}: Installation start.`,
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

    expect(text(page().querySelector('.banner span'))).toBe(
      'The change could not be loaded: Change 99 does not exist',
    );
    expect(text(page().querySelector('.breadcrumb'))).toBe('Changes/Change');

    buttonOf(page(), 'Try again').click();
    await settle();
    http.expectOne('/api/changes/99').flush(productionChange({ id: 99 }));
    await settle();
    expect(page().querySelector('.banner')).toBeNull();
    expect(text(page().querySelector('h1'))).toBe('CHG0012345');
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

  it('shows only when, what and who in its key layout and the rest in its details layout', () => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    const fixture = TestBed.createComponent(ChangeSummary);
    const titles = (layout: 'key' | 'details') => {
      fixture.componentRef.setInput('layout', layout);
      fixture.detectChanges();
      return [...(fixture.nativeElement as HTMLElement).querySelectorAll('h3')].map(text);
    };
    fixture.componentRef.setInput('change', productionChange());

    expect(titles('key')).toEqual(['When it installs', 'What it delivers', 'Who approves']);
    expect(titles('details')).toEqual([
      'Request details',
      'Risk assessment',
      'Privileged access',
      'Secure coding',
      'Planning',
    ]);
  });

  it('says when no privileged access or downtime is needed', () => {
    const rows = render(productionChange());

    expect(rows('Privileged access')).toEqual(['Needed: No']);
    expect(rows('Schedule')).toContain('Downtime: No');
  });

  it('shows the approval of each state and a downtime without its window', () => {
    const approval = (state: ProductionChange['state']) =>
      render(productionChange({ state, number: null }))('Request details').slice(0, 2);

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
