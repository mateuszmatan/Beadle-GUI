import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import {
  changeOptions,
  changeSchedule,
  changeTask,
  releaseDetails,
  taskDetails,
} from '../testing/change-fixtures';
import { buttonOf, fieldOf, inputOf, text } from '../testing/dom';
import { ChangeTasksForm, TASK_LABELS, taskFields } from './change-tasks-form';
import { TasksForm, drafts, taskWindow, tasksForm } from './change-tasks-model';

describe('ChangeTasksForm', () => {
  let http: HttpTestingController;
  let fixture: ComponentFixture<ChangeTasksForm>;
  let tasks: TasksForm;

  const page = () => fixture.nativeElement as HTMLElement;
  const rows = () => [...page().querySelectorAll<HTMLElement>('.task-row')];
  const labels = (row: HTMLElement) =>
    [...row.querySelectorAll('dso-label')].map((label) => text(label));
  const window = () => taskWindow('CHG0012345', changeSchedule());

  async function render(form: TasksForm, readonly = false) {
    tasks = form;
    fixture = TestBed.createComponent(ChangeTasksForm);
    fixture.componentRef.setInput('tasks', tasks);
    fixture.componentRef.setInput('readonly', readonly);
    fixture.detectChanges();
    http.match('/api/changes/options').forEach((request) => request.flush(changeOptions()));
    fixture.detectChanges();
    await fixture.whenStable();
  }

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('lays the release and the other change task fields out in their two columns', () => {
    expect(taskFields(true, true).map((field) => field.label)).toEqual([
      'Number',
      'Change number',
      'Assignment group',
      'Assigned to',
      'Affected CI',
      'Approval',
      'Installation start',
      'Installation end',
      'Platform',
      'Task start',
      'Application',
      'Packages',
      'Backout packages',
      'Short description',
      'Description',
      'Additional comments',
    ]);
    expect(taskFields(false, true).map((field) => field.label)).toEqual([
      'Number',
      'Change number',
      'Assignment group',
      'Assigned to',
      'Importance',
      'Affected CI',
      'Approval',
      'Installation start',
      'Installation end',
      'Short description',
      'Description',
      'Additional comments',
    ]);
    expect(
      taskFields(true, true)
        .filter((field) => field.kind === 'area')
        .map((field) => field.span),
    ).toEqual([12, 12, 12, 12, 12]);
    expect(
      taskFields(true, false)
        .filter((field) => field.placeholder)
        .map((field) => [field.label, field.type, field.placeholder]),
    ).toEqual([
      ['Number', undefined, 'Given by ProTech when created'],
      ['Change number', 'text', 'The CHG number of the change'],
      ['Installation start', 'text', 'From the change'],
      ['Installation end', 'text', 'From the change'],
      ['Task start', 'text', 'A minute after the installation start'],
    ]);
    expect(TASK_LABELS['assignmentGroup']).toBe('assignment group');
    expect(TASK_LABELS['start']).toBe('task start');
  });

  it('shows the change, the approval and the window on every task of a change', async () => {
    await render(
      tasksForm(
        [
          changeTask({ details: releaseDetails('Deploy it'), start: '2026-10-10T06:01:00Z' }),
          changeTask({ number: 'CTASK0020002', state: 'WORK_IN_PROGRESS' }),
          { details: taskDetails('Tell them') },
        ],
        window(),
      ),
    );

    expect(rows().map((row) => text(row.querySelector('.task-head')))).toEqual([
      '1Release ManagementOpen Remove',
      '2Change taskWork in progress Remove',
      '3Change taskOpen Remove',
    ]);
    expect(labels(rows()[0])).toContain('Platform');
    expect(labels(rows()[0])).not.toContain('Importance');
    expect(labels(rows()[1])).toContain('Importance');
    expect(labels(rows()[1])).not.toContain('Packages');
    expect(inputOf(rows()[0], 'Change number').value).toBe('CHG0012345');
    expect(inputOf(rows()[0], 'Change number').disabled).toBe(true);
    expect(inputOf(rows()[0], 'Approval').value).toBe('Not Yet Requested');
    expect(inputOf(rows()[2], 'Number').placeholder).toBe('Given by ProTech when created');
    expect(fieldOf(rows()[0], 'Assignment group')!.querySelector('.lookup')).not.toBeNull();
    expect(fieldOf(rows()[0], 'Assigned to')!.querySelector('.lookup')).not.toBeNull();
    expect(buttonOf(page(), 'Remove change task 1').disabled).toBe(false);
  });

  it('switches the fields when the group changes and edits, adds and removes tasks', async () => {
    await render(tasksForm(drafts([taskDetails('Deploy it', 'Deploy the release.')])));

    expect(rows()).toHaveLength(1);
    expect(labels(rows()[0])).toContain('Number');
    expect(labels(rows()[0])).not.toContain('Task start');
    expect(inputOf(rows()[0], 'Change number').placeholder).toBe('The CHG number of the change');
    expect(inputOf(rows()[0], 'Installation start').disabled).toBe(true);
    expect(buttonOf(page(), 'Remove change task 1').disabled).toBe(true);
    expect(text(page().querySelector('.task-actions .muted'))).toBe('1 change task');

    const group = inputOf(rows()[0], 'Assignment group');
    group.value = 'Release Management';
    group.dispatchEvent(new Event('input'));
    fixture.detectChanges();
    expect(text(rows()[0].querySelector('.kind'))).toBe('Release Management');
    expect(labels(rows()[0])).toContain('Packages');
    expect(inputOf(rows()[0], 'Task start').placeholder).toBe('A minute after the installation start');
    expect(inputOf(rows()[0], 'Task start').disabled).toBe(true);

    buttonOf(page(), 'Add a change task').click();
    fixture.detectChanges();
    expect(rows()).toHaveLength(2);
    expect(text(page().querySelector('.task-actions .muted'))).toBe('2 change tasks');
    const short = inputOf(rows()[1], 'Short description');
    short.value = 'Validate it';
    short.dispatchEvent(new Event('input'));
    expect(tasks.at(1).controls.details.controls.shortDescription.value).toBe('Validate it');

    buttonOf(page(), 'Remove change task 1').click();
    fixture.detectChanges();
    expect(rows()).toHaveLength(1);
    expect(tasks.at(0).controls.details.controls.shortDescription.value).toBe('Validate it');
  });

  it('keeps a closed task as it is', async () => {
    await render(
      tasksForm([changeTask({ state: 'CLOSED' }), changeTask({ number: null })], window()),
    );

    expect(text(rows()[0].querySelector('.task-head'))).toBe(
      '1Change taskClosedClosed in ProTech, so it stays as it is Remove',
    );
    expect(rows()[0].classList).toContain('closed');
    expect(buttonOf(page(), 'Remove change task 1').disabled).toBe(true);
    expect(inputOf(rows()[0], 'Short description').disabled).toBe(true);
  });

  it('shows every field of every task read-only with where it stands and no way to change it', async () => {
    const form = tasksForm(
      [
        changeTask({
          details: releaseDetails('Deploy it'),
          start: '2026-10-10T06:01:00Z',
          approval: 'Requested',
        }),
        changeTask({ number: 'CTASK0020002', state: 'CANCELED' }),
      ],
      window(),
    );
    form.disable();
    await render(form, true);

    expect(rows().map((row) => text(row.querySelector('.task-head')))).toEqual([
      '1Release ManagementOpenNot done yet; waiting for approval.',
      '2Change taskCanceledCanceled; no longer part of the change.',
    ]);
    expect(rows()[1].classList).toContain('canceled');
    expect(labels(rows()[0])).toEqual(taskFields(true, true).map((field) => field.label));
    expect(labels(rows()[1])).toEqual(taskFields(false, true).map((field) => field.label));
    expect(inputOf(rows()[0], 'Change number').value).toBe('CHG0012345');
    expect(inputOf(rows()[0], 'Short description').value).toBe('Deploy it');
    expect(inputOf(rows()[1], 'Number').value).toBe('CTASK0020002');
    expect([...page().querySelectorAll('input, textarea, select')].every((field) =>
      (field as HTMLInputElement).disabled,
    )).toBe(true);
    expect(page().querySelector('.lookup')).toBeNull();
    expect(page().querySelector('button')).toBeNull();
  });

  it('asks for at least one template task', async () => {
    const empty = tasksForm([]);
    empty.markAsTouched();
    await render(empty);

    expect(text(page().querySelector('.choice-error'))).toBe('Add at least one change task');
  });
});
