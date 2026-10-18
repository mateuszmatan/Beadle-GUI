import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { MyDepartment } from '../business-services/my-business-service';
import { Notifier } from '../core/notifier';
import {
  changeOptions,
  changeSchedule,
  changeTask,
  productionChange,
  releaseDetails,
  taskDetails,
} from '../testing/change-fixtures';
import { buttonOf, choose, enter, errorOf, fieldOf, inputOf, selectOf, text } from '../testing/dom';
import { ProductionChange } from './change-api';
import { ChangeEdit } from './change-edit';
import { PublishedChange } from './published-change';

describe('ChangeEdit', () => {
  let http: HttpTestingController;
  let fixture: ComponentFixture<ChangeEdit>;
  let navigate: ReturnType<typeof vi.spyOn>;

  const page = () => fixture.nativeElement as HTMLElement;
  const edit = () => fixture.componentInstance;
  const taskRows = () => [
    ...page().querySelectorAll<HTMLElement>('beadle-change-tasks-form .task-row'),
  ];
  const stored = productionChange({
    tasks: [
      changeTask(),
      changeTask({ number: 'CTASK0020002', state: 'CANCELED', details: taskDetails('Old one') }),
      changeTask({ number: 'CTASK0020003', state: 'CLOSED', details: taskDetails('Backup') }),
    ],
  });

  async function settle() {
    TestBed.tick();
    await new Promise((resolve) => setTimeout(resolve));
    TestBed.tick();
    fixture.detectChanges();
  }

  async function show(change: ProductionChange = stored, departmentId: number | null = 3) {
    TestBed.inject(MyDepartment).choose(departmentId);
    fixture = TestBed.createComponent(ChangeEdit);
    fixture.componentRef.setInput('id', 7);
    await settle();
    http.expectOne('/api/changes/7').flush(change);
    await settle();
    http.match('/api/changes/options').forEach((request) => request.flush(changeOptions()));
    await settle();
  }

  const texts = () => page().querySelector<HTMLElement>('beadle-change-texts')!;

  async function type(label: string, value: string, root: ParentNode = page()) {
    enter(root, label, value);
    await settle();
  }

  function refuse(status: number, body: object) {
    http
      .expectOne({ method: 'PUT', url: '/api/changes/7' })
      .flush(body, { status, statusText: 'Refused' });
    return settle();
  }

  async function publish() {
    buttonOf(page(), 'Publish the update to ProTech').click();
    await settle();
  }

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    });
    http = TestBed.inject(HttpTestingController);
    navigate = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
  });

  afterEach(() => {
    http.verify();
    TestBed.inject(MyDepartment).choose(null);
  });

  it('publishes the edited texts, ProTech fields and change tasks to ProTech', async () => {
    const success = vi.spyOn(TestBed.inject(Notifier), 'success');
    await show();

    expect(text(page().querySelector('h1'))).toBe('Edit CHG0012345');
    expect(text(page().querySelector('.breadcrumb'))).toBe('Changes/CHG0012345/Edit');
    expect(text(page().querySelector('.page-header p'))).toBe(
      'Change any value and publish it: the update goes to ProTech at once, and the change page ' +
        'then shows whether ProTech applied it. Fields marked * are required.',
    );
    expect([...page().querySelectorAll('h2')].map(text)).toEqual([
      'ProTech fields',
      'Change tasks',
      'Text sent to ProTech',
    ]);
    expect(inputOf(texts(), 'Short description').value).toBe('CertScanner CERT 4.2: Expiry alerts');
    expect(
      ['Short description', 'Description'].map(
        (label) => !!fieldOf(texts(), label)?.querySelector('.required-marker'),
      ),
    ).toEqual([true, true]);
    expect(fieldOf(page(), 'Jira project')).toBeNull();
    expect(inputOf(page(), 'Change number').value).toBe('CHG0012345');
    expect(inputOf(page(), 'Approval').value).toBe('Requested');
    expect(inputOf(page(), 'Opened by').value).toBe('Mateusz Matan');
    expect(inputOf(page(), 'State').value).toBe('Primary Approval');
    expect(selectOf(page(), 'Type').disabled).toBe(true);
    expect(inputOf(page(), 'Installation hours').value).toBe('2');
    expect(fieldOf(page(), 'Downtime start')).toBeNull();
    expect(taskRows().map((row) => inputOf(row, 'Number').value)).toEqual([
      'CTASK0020001',
      'CTASK0020003',
    ]);
    expect(edit().hasUnsavedChanges()).toBe(false);

    await type('Short description', ' CertScanner 4.2 ', texts());
    await type('Assignment group', 'Platform Team');
    choose(selectOf(page(), 'Downtime'), 'Yes');
    await settle();
    expect(inputOf(page(), 'Downtime start').value).toBe(
      inputOf(page(), 'Installation start').value,
    );
    buttonOf(page(), 'Add a change task').click();
    await settle();
    await type('Assignment group', 'Release Management', taskRows()[2]);
    await type('Short description', 'Tell the users', taskRows()[2]);
    await type('Description', 'Mail.', taskRows()[2]);
    expect(edit().hasUnsavedChanges()).toBe(true);
    await publish();

    const put = http.expectOne({ method: 'PUT', url: '/api/changes/7' });
    expect(edit().hasUnsavedChanges()).toBe(true);
    expect(put.request.body).toEqual({
      version: 4,
      departmentId: 3,
      shortDescription: 'CertScanner 4.2',
      description: 'Production release of CertScanner (CERT).',
      schedule: {
        installationStart: '2026-10-10T06:00:00.000Z',
        installationEnd: '2026-10-10T08:00:00.000Z',
        validationStart: '2026-10-10T08:00:00.000Z',
        validationEnd: '2026-10-10T09:00:00.000Z',
        firstUsage: '2026-10-12T08:00:00.000Z',
        downtimeStart: '2026-10-10T06:00:00.000Z',
        downtimeEnd: '2026-10-10T08:00:00.000Z',
      },
      template: { ...stored.template, assignmentGroup: 'Platform Team', downtime: true },
      tasks: [
        {
          number: 'CTASK0020001',
          details: taskDetails(
            'Deploy CertScanner to production',
            'Deploy the release of CertScanner.',
          ),
          start: null,
        },
        { number: 'CTASK0020003', details: taskDetails('Backup'), start: null },
        {
          number: null,
          details: releaseDetails('Tell the users', 'Mail.'),
          start: '2026-10-10T06:01:00.000Z',
        },
      ],
    });
    const saved = productionChange({ shortDescription: 'CertScanner 4.2', version: 5 });
    put.flush(saved);
    await settle();

    expect(TestBed.inject(PublishedChange).take(7)).toEqual(saved);
    expect(success).toHaveBeenCalledWith(
      'Your update of CHG0012345 is published to ProTech. This page shows when ProTech has applied it.',
    );
    expect(navigate).toHaveBeenCalledWith(['/changes', 7]);
    expect(edit().hasUnsavedChanges()).toBe(false);
  });

  it('refuses to publish while a field needs attention', async () => {
    await show();
    await type('Short description', ' ', texts());
    await type('Description', '', taskRows()[0]);
    await type('Installation start', '2020-01-01T10:00');
    await publish();

    http.expectNone({ method: 'PUT', url: '/api/changes/7' });
    expect(text(page().querySelector('.save-error'))).toBe('Some fields need your attention.');
    expect(errorOf(texts(), 'Short description')).toBe('Required');
    expect(errorOf(taskRows()[0], 'Description')).toBe('Required');
    expect(text(page().querySelector('beadle-change-schedule .choice-error'))).toBe(
      'The installation must start in the future',
    );
  });

  it('shows the installation window of the change on its tasks and follows a moved start', async () => {
    await show();

    expect(inputOf(taskRows()[0], 'Change number').value).toBe('CHG0012345');
    expect(inputOf(taskRows()[0], 'Installation start').value).toBe(
      inputOf(page(), 'Installation start').value,
    );
    await type('Installation start', '2026-10-11T10:00');

    expect(inputOf(taskRows()[0], 'Installation start').value).toBe('2026-10-11T10:00');
    expect(inputOf(taskRows()[0], 'Installation end').value).toBe('2026-10-11T12:00');
  });

  it('keeps an installation start that already passed when it is not moved', async () => {
    const running = productionChange({
      state: 'IMPLEMENTATION',
      schedule: changeSchedule({
        installationStart: '2020-01-01T08:00:00Z',
        installationEnd: '2020-01-01T10:00:00Z',
        validationStart: '2020-01-01T10:00:00Z',
        validationEnd: '2020-01-01T11:00:00Z',
        firstUsage: '2020-01-01T12:00:00Z',
      }),
    });
    await show(running);

    await type('Installation hours', '0');
    await publish();
    http.expectNone({ method: 'PUT', url: '/api/changes/7' });
    expect(text(page().querySelector('beadle-change-schedule .choice-error'))).toBe(
      'The installation must end after it starts',
    );
    await type('Installation hours', '2');
    await publish();
    const put = http.expectOne({ method: 'PUT', url: '/api/changes/7' });
    expect(put.request.body.schedule.installationStart).toBe('2020-01-01T08:00:00.000Z');
  });

  it('marks the problems ProTech or Beadle found on the fields and lists them', async () => {
    await show();
    await publish();
    await refuse(400, {
      detail: '2 fields are invalid',
      problems: [
        { field: 'schedule.validationEnd', message: 'must be after the start' },
        { field: 'tasks[0].details.shortDescription', message: 'is used twice' },
        { field: 'tasks', message: 'CTASK0020009 is closed in ProTech and cannot be removed' },
      ],
    });

    expect(text(page().querySelector('.save-error'))).toBe(
      'The update could not be published: 2 fields are invalid',
    );
    expect([...page().querySelectorAll('.problems li')].map(text)).toEqual([
      'Validation end: must be after the start',
      'Change task 1: short description: is used twice',
      'Change tasks: CTASK0020009 is closed in ProTech and cannot be removed',
    ]);
    expect(errorOf(taskRows()[0], 'Short description')).toBe('is used twice');
    expect(errorOf(page(), 'Validation hours')).toBe('must be after the start');
  });

  it('reloads a change that was changed meanwhile and publishes with its new version', async () => {
    await show();
    await publish();
    await refuse(409, { detail: 'The record was changed by someone else in the meantime.' });
    expect(text(page().querySelector('.save-error'))).toBe(
      'The update could not be published: The record was changed by someone else in the meantime.',
    );
    expect(navigate).not.toHaveBeenCalled();

    buttonOf(page(), 'Reload').click();
    await settle();
    http.expectOne('/api/changes/7').flush(productionChange({ version: 6 }));
    await settle();
    expect(page().querySelector('.save-error')).toBeNull();

    await publish();
    expect(http.expectOne({ method: 'PUT', url: '/api/changes/7' }).request.body.version).toBe(6);
  });

  it('does not offer a closed change or the change of another department', async () => {
    await show(productionChange({ state: 'CLOSED' }));
    expect(text(page().querySelector('.banner.refused'))).toBe(
      'CHG0012345 is closed in ProTech and can no longer be changedBack to the change',
    );
    expect(page().querySelector('form')).toBeNull();
    fixture.destroy();

    await show(stored, 5);
    expect(text(page().querySelector('.banner.refused span'))).toBe(
      'Only Corporate Technology can change it',
    );
    expect(page().querySelector('.banner.refused a')?.getAttribute('href')).toBe('/changes/7');
    expect(page().querySelector('form')).toBeNull();
  });
});
