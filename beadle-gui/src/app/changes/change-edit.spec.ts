import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { MyDepartment } from '@common/departments/my-department';
import { Notifier } from '@common/core/notifier';
import {
  changeOptions,
  changeSchedule,
  changeTask,
  productionChange,
  releaseDetails,
  taskDetails,
} from '../testing/change-fixtures';
import { buttonOf, fieldOf, inputOf, selectOf, text } from '@common/testing/dom';
import { ProductionChange } from './change-api';
import { ChangeEdit, STALE } from './change-edit';
import { PublishedChange } from './published-change';

describe('ChangeEdit', () => {
  let http: HttpTestingController;
  let fixture: ComponentFixture<ChangeEdit>;
  let navigate: ReturnType<typeof vi.spyOn>;

  const page = () => fixture.nativeElement as HTMLElement;
  const edit = () => fixture.componentInstance;
  const form = () => edit()['form']()!;
  const taskRows = () => [
    ...page().querySelectorAll<HTMLElement>('dso-change-tasks-form .task-row'),
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

  const texts = () => page().querySelector<HTMLElement>('section.texts')!;

  async function type(label: string, value: string, root: ParentNode = page()) {
    const input = inputOf(root, label);
    input.value = value;
    input.dispatchEvent(new Event('input'));
    await settle();
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
    form().controls.template.controls.downtime.setValue(true);
    await settle();
    expect(inputOf(page(), 'Downtime start').value).toBe(
      inputOf(page(), 'Installation start').value,
    );
    buttonOf(page(), 'Add a change task').click();
    await settle();
    form().controls.tasks.at(2).controls.details.patchValue({
      assignmentGroup: 'Release Management',
      shortDescription: 'Tell the users',
      description: 'Mail.',
    });
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
    form().controls.tasks.at(0).controls.details.controls.description.setValue('');
    form().controls.schedule.controls.installationStart.setValue('2020-01-01T10:00');
    await publish();

    http.expectNone({ method: 'PUT', url: '/api/changes/7' });
    expect(text(page().querySelector('.save-error'))).toBe('Some fields need your attention.');
    expect(text(fieldOf(texts(), 'Short description')?.querySelector('dso-error'))).toBe(
      'Required',
    );
    expect(text(page().querySelector('dso-change-schedule .choice-error'))).toBe(
      'The installation must start in the future',
    );
  });

  it('counts the length of the texts in bytes, as ProTech and the checks do', async () => {
    await show();
    const counter = (label: string) =>
      text(fieldOf(texts(), label)?.querySelector('dso-hint.length-hint'));
    await type('Short description', 'Überweisung – 4.2', texts());
    await type('Description', 'Ü', texts());

    expect(counter('Short description')).toBe('20 / 160');
    expect(counter('Description')).toBe('2 / 4000');

    await type('Short description', 'é'.repeat(80) + 'x', texts());
    expect(counter('Short description')).toBe('161 / 160');
    expect(form().controls.shortDescription.errors).toEqual({ bytes: { max: 160 } });
  });

  it('shows the installation window of the change on its tasks and follows a moved start', async () => {
    await show();

    expect(inputOf(taskRows()[0], 'Change number').value).toBe('CHG0012345');
    expect(inputOf(taskRows()[0], 'Installation start').value).toBe(
      inputOf(page(), 'Installation start').value,
    );
    form().controls.schedule.controls.installationStart.setValue('2026-10-11T10:00');
    await settle();

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

    expect(form().controls.schedule.valid).toBe(true);
    form().controls.schedule.controls.installationHours.setValue(0);
    expect(form().controls.schedule.errors).toEqual({
      rule: 'The installation must end after it starts',
    });
  });

  it('marks the problems ProTech or Beadle found on the fields and lists them', async () => {
    await show();
    await publish();
    http.expectOne({ method: 'PUT', url: '/api/changes/7' }).flush(
      {
        detail: '2 fields are invalid',
        errors: [
          { field: 'schedule.validationEnd', message: 'must be after the start' },
          { field: 'tasks[0].details.shortDescription', message: 'is used twice' },
          { field: 'tasks', message: 'CTASK0020009 is closed in ProTech and cannot be removed' },
        ],
      },
      { status: 400, statusText: 'Bad Request' },
    );
    await settle();

    expect(text(page().querySelector('.save-error'))).toBe(
      'The update could not be published: 2 fields are invalid',
    );
    expect([...page().querySelectorAll('.problems li')].map(text)).toEqual([
      'Validation end: must be after the start',
      'Change task 1: short description: is used twice',
      'Change tasks: CTASK0020009 is closed in ProTech and cannot be removed',
    ]);
    expect(form().controls.tasks.at(0).controls.details.controls.shortDescription.errors).toEqual({
      server: 'is used twice',
    });
    expect(form().controls.schedule.controls.validationHours.errors).toEqual({
      server: 'must be after the start',
    });
    expect(buttonOf(page(), 'Reload')).toBeUndefined();
  });

  it('offers to reload a change that was changed meanwhile', async () => {
    await show();
    await publish();
    http.expectOne({ method: 'PUT', url: '/api/changes/7' }).flush(
      {
        detail:
          'The record was changed by someone else in the meantime. Reload it and apply your change again.',
      },
      { status: 409, statusText: 'Conflict' },
    );
    await settle();

    expect(text(page().querySelector('.save-error'))).toBe(STALE);
    buttonOf(page(), 'Reload').click();
    await settle();
    http.expectOne('/api/changes/7').flush(productionChange({ version: 6 }));
    await settle();
    expect(page().querySelector('.save-error')).toBeNull();

    await publish();
    const put = http.expectOne({ method: 'PUT', url: '/api/changes/7' });
    expect(put.request.body.version).toBe(6);
    put.flush(
      { detail: 'ProTech does not change a closed change' },
      { status: 409, statusText: 'Conflict' },
    );
    await settle();
    expect(text(page().querySelector('.save-error'))).toBe(
      'The update could not be published: ProTech does not change a closed change',
    );
    expect(buttonOf(page(), 'Reload')).toBeDefined();
  });

  it('says why ProTech or Beadle refused the update', async () => {
    await show();
    await publish();
    http
      .expectOne({ method: 'PUT', url: '/api/changes/7' })
      .flush(
        { detail: 'Only Corporate Technology can change CHG0012345' },
        { status: 403, statusText: 'Forbidden' },
      );
    await settle();

    expect(text(page().querySelector('.save-error'))).toBe(
      'The update could not be published: Only Corporate Technology can change CHG0012345',
    );
    expect(buttonOf(page(), 'Reload')).toBeUndefined();
    expect(navigate).not.toHaveBeenCalled();
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

  it('says when the change cannot be loaded', async () => {
    TestBed.inject(MyDepartment).choose(3);
    fixture = TestBed.createComponent(ChangeEdit);
    fixture.componentRef.setInput('id', 7);
    await settle();
    http
      .expectOne('/api/changes/7')
      .flush({ detail: 'Change 7 does not exist' }, { status: 404, statusText: 'Not Found' });
    await settle();

    expect(text(page().querySelector('.banner span'))).toBe(
      'The change could not be loaded: Change 7 does not exist',
    );
    expect(text(page().querySelector('.breadcrumb'))).toBe('Changes/Edit');

    buttonOf(page(), 'Try again').click();
    await settle();
    http.expectOne('/api/changes/7').flush(stored);
    await settle();
    http.match('/api/changes/options').forEach((request) => request.flush(changeOptions()));
    await settle();
    expect(page().querySelector('.banner')).toBeNull();
    expect(text(page().querySelector('h1'))).toBe('Edit CHG0012345');
  });
});
