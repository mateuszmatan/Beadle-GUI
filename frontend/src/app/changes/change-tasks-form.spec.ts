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

  async function render(form: TasksForm) {
    tasks = form;
    fixture = TestBed.createComponent(ChangeTasksForm);
    fixture.componentRef.setInput('tasks', tasks);
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
    expect(taskFields(false, false).map((field) => field.label)).toEqual([
      'Assignment group',
      'Assigned to',
      'Importance',
      'Affected CI',
      'Short description',
      'Description',
      'Additional comments',
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
    expect(labels(rows()[0])).not.toContain('Number');
    expect(labels(rows()[0])).not.toContain('Task start');
    expect(buttonOf(page(), 'Remove change task 1').disabled).toBe(true);
    expect(text(page().querySelector('.task-actions .muted'))).toBe('1 change task');

    const group = inputOf(rows()[0], 'Assignment group');
    group.value = 'Release Management';
    group.dispatchEvent(new Event('input'));
    fixture.detectChanges();
    expect(text(rows()[0].querySelector('.kind'))).toBe('Release Management');
    expect(labels(rows()[0])).toContain('Packages');

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

  it('asks for at least one template task', async () => {
    const empty = tasksForm([]);
    empty.markAsTouched();
    await render(empty);

    expect(text(page().querySelector('.choice-error'))).toBe('Add at least one change task');
  });
});
