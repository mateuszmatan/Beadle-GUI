import {
  changeSchedule,
  changeTask,
  releaseDetails,
  taskDetails,
} from '../testing/change-fixtures';
import { localInput } from './change-schedule-model';
import {
  MAX_TASKS,
  addTask,
  applyTaskProblems,
  canRemove,
  drafts,
  isRelease,
  isReleaseTask,
  nestedTaskProblems,
  removeTask,
  setTaskWindow,
  taskForm,
  taskStatus,
  taskWindow,
  tasksForm,
  toTaskDetails,
  toTaskRequests,
} from './change-tasks-model';

const local = (time: string) => localInput(new Date(time));
const window = () => taskWindow('CHG0012345', changeSchedule());

describe('change tasks model', () => {
  it('tells Release Management groups by their name', () => {
    expect(isRelease('Release Management')).toBe(true);
    expect(isRelease('EMEA release management')).toBe(true);
    expect(isRelease('Cloud Engineering')).toBe(false);
    expect(isRelease('')).toBe(false);
    expect(isRelease(null)).toBe(false);
  });

  it('takes the change number and the installation window of the change', () => {
    expect(window()).toEqual({
      changeNumber: 'CHG0012345',
      installationStart: local('2026-10-10T06:00:00Z'),
      installationEnd: local('2026-10-10T08:00:00Z'),
    });
    expect(taskWindow(null, null)).toEqual({
      changeNumber: null,
      installationStart: '',
      installationEnd: '',
    });
  });

  it('shows the change on every task and defaults a release task to a minute after the start', () => {
    const tasks = tasksForm(
      drafts([releaseDetails('Deploy it'), taskDetails('Tell them')]),
      window(),
    );
    const [release, other] = tasks.controls;

    expect(release.getRawValue()).toMatchObject({
      number: null,
      changeNumber: 'CHG0012345',
      approval: 'Not Approved',
      approvers: '',
      installationStart: local('2026-10-10T06:00:00Z'),
      installationEnd: local('2026-10-10T08:00:00Z'),
      start: local('2026-10-10T06:01:00Z'),
      state: 'OPEN',
    });
    expect(release.controls.changeNumber.disabled).toBe(true);
    expect(release.controls.approval.disabled).toBe(true);
    expect(other.controls.start.value).toBe('');
    expect(other.valid).toBe(true);
    expect(tasks.errors).toBeNull();
  });

  it('keeps the start of a release task inside the installation window', () => {
    const task = taskForm({ details: releaseDetails('Deploy it') }, window());
    const start = task.controls.start;

    start.setValue(local('2026-10-10T06:00:30Z'));
    expect(start.errors).toEqual({ rule: 'At least a minute after the installation start' });
    start.setValue(local('2026-10-10T08:01:00Z'));
    expect(start.errors).toEqual({ rule: 'Not after the installation end' });
    start.setValue('');
    expect(start.errors).toEqual({ rule: 'Choose when the task starts' });
    start.setValue(local('2026-10-10T08:00:00Z'));
    expect(start.valid).toBe(true);

    task.controls.details.controls.assignmentGroup.setValue('Cloud Engineering');
    start.setValue('');
    expect(start.valid).toBe(true);
    const template = taskForm({ details: releaseDetails('Deploy it') }).controls.start;
    expect(template.disabled).toBe(true);
    expect(template.errors).toBeNull();
  });

  it('switches the form with the group and fills the start when it becomes a release task', () => {
    const task = taskForm({ details: taskDetails('Deploy it') }, window());
    const group = task.controls.details.controls.assignmentGroup;

    expect(isReleaseTask(task)).toBe(false);
    group.setValue('Release Management');
    expect(isReleaseTask(task)).toBe(true);
    expect(task.controls.start.value).toBe(local('2026-10-10T06:01:00Z'));
    expect(task.controls.start.valid).toBe(true);
  });

  it('sets the application to OCP on OpenShift and frees it again on another platform', () => {
    const task = taskForm({
      details: releaseDetails('Deploy it', 'Deploy.', { application: 'cert' }),
    });
    const { platform, application } = task.controls.details.controls;

    expect(platform.value).toBe('None');
    expect(application.enabled).toBe(true);
    platform.setValue('OpenShift');
    expect(application.value).toBe('OCP');
    expect(application.disabled).toBe(true);
    platform.setValue('Distributed');
    expect(application.value).toBe('');
    expect(application.enabled).toBe(true);
    application.setValue('OCP');
    platform.setValue('Mainframe');
    expect(application.value).toBe('OCP');
  });

  it('starts empty tasks with platform None and importance 3 - Moderate', () => {
    const details = taskForm().controls.details;

    expect(details.getRawValue()).toMatchObject({ platform: 'None', importance: '3 - Moderate' });
    expect(details.controls.assignmentGroup.hasError('required')).toBe(true);
    expect(details.controls.shortDescription.hasError('required')).toBe(true);
    expect(details.controls.description.hasError('required')).toBe(true);
    details.controls.shortDescription.setValue('é'.repeat(81));
    expect(details.controls.shortDescription.hasError('bytes')).toBe(true);
  });

  it('sends only the fields of the kind of each task, trimmed, with the start of release tasks', () => {
    const tasks = tasksForm(
      [
        changeTask({
          details: releaseDetails(' Deploy it ', 'Deploy.', {
            assignedTo: ' ',
            platform: 'OpenShift',
            packages: 'cert-4.2.jar',
            importance: '1 - Critical',
          }),
          start: '2026-10-10T06:30:00Z',
        }),
        {
          details: taskDetails('Tell them', 'Send the e-mail.', {
            platform: 'Mainframe',
            packages: 'x',
          }),
        },
      ],
      window(),
    );

    expect(toTaskRequests(tasks)).toEqual([
      {
        number: 'CTASK0020001',
        start: '2026-10-10T06:30:00.000Z',
        details: releaseDetails('Deploy it', 'Deploy.', {
          platform: 'OpenShift',
          application: 'OCP',
          packages: 'cert-4.2.jar',
        }),
      },
      { number: null, start: null, details: taskDetails('Tell them', 'Send the e-mail.') },
    ]);
    expect(toTaskDetails(tasks)[1]).toEqual(taskDetails('Tell them', 'Send the e-mail.'));
  });

  it('follows a moved installation window', () => {
    const tasks = tasksForm(drafts([releaseDetails('Deploy it')]), window());
    setTaskWindow(
      tasks,
      taskWindow('CHG0012345', changeSchedule({ installationStart: '2026-10-10T07:00:00Z' })),
    );

    expect(tasks.at(0).controls.installationStart.value).toBe(local('2026-10-10T07:00:00Z'));
    expect(tasks.at(0).controls.start.errors).toEqual({
      rule: 'At least a minute after the installation start',
    });
    expect(tasks.window?.installationStart).toBe(local('2026-10-10T07:00:00Z'));

    const other = tasksForm(drafts([taskDetails('Tell them')]), window());
    setTaskWindow(
      other,
      taskWindow(
        'CHG0012345',
        changeSchedule({
          installationStart: '2026-10-11T07:00:00Z',
          installationEnd: '2026-10-11T09:00:00Z',
        }),
      ),
    );
    other.at(0).controls.details.controls.assignmentGroup.setValue('Release Management');
    expect(other.at(0).controls.start.value).toBe(local('2026-10-11T07:01:00Z'));
    expect(other.at(0).controls.start.valid).toBe(true);
  });

  it('keeps a closed task as it is and at least one template task', () => {
    const tasks = tasksForm(
      [changeTask({ state: 'CLOSED' }), changeTask({ number: null })],
      window(),
    );

    expect(tasks.at(0).disabled).toBe(true);
    expect(canRemove(tasks, 0)).toBe(false);
    removeTask(tasks, 0);
    expect(tasks.length).toBe(2);
    removeTask(tasks, 1);
    expect(tasks.length).toBe(1);
    expect(tasks.dirty).toBe(true);

    const open = tasksForm([changeTask()], window());
    removeTask(open, 0);
    expect(open.length).toBe(0);
    expect(open.errors).toBeNull();

    const template = tasksForm(drafts([taskDetails('Deploy it')]));
    removeTask(template, 0);
    expect(template.length).toBe(1);
    expect(tasksForm([]).errors).toEqual({ rule: 'Add at least one change task' });
  });

  it('adds empty tasks up to the limit, with the change of the list', () => {
    const tasks = tasksForm([], window());
    addTask(tasks);
    expect(tasks.at(0).controls.changeNumber.value).toBe('CHG0012345');
    expect(tasks.at(0).invalid).toBe(true);
    for (let i = tasks.length; i < MAX_TASKS + 3; i++) {
      addTask(tasks);
    }
    expect(tasks.length).toBe(MAX_TASKS);
  });

  it('marks the problems of the server on their task and its fields', () => {
    const tasks = tasksForm(
      drafts([taskDetails('Deploy it'), releaseDetails('Validate it')]),
      window(),
    );

    expect(
      applyTaskProblems(tasks, [
        { field: 'tasks[1].details.shortDescription', message: 'is used twice' },
        { field: 'tasks[1].start', message: 'must be inside the installation window' },
        { field: 'tasks', message: 'CTASK0020009 is closed in ProTech and cannot be removed' },
        { field: 'schedule', message: 'must not be null' },
      ]),
    ).toEqual([
      { field: 'schedule', message: 'must not be null' },
      { field: 'tasks', message: 'CTASK0020009 is closed in ProTech and cannot be removed' },
    ]);
    expect(tasks.at(1).controls.details.controls.shortDescription.errors).toEqual({
      server: 'is used twice',
    });
    expect(tasks.at(1).controls.start.errors).toEqual({
      server: 'must be inside the installation window',
    });
    expect(
      nestedTaskProblems([
        { field: 'tasks[2].assignmentGroup', message: 'must not be blank' },
        { field: 'tasks', message: 'add at least one change task' },
      ]),
    ).toEqual([
      { field: 'tasks[2].details.assignmentGroup', message: 'must not be blank' },
      { field: 'tasks', message: 'add at least one change task' },
    ]);
  });

  it('says in plain words where a task stands and its approval', () => {
    expect(taskStatus({ state: 'OPEN', approval: 'Not Approved' })).toBe(
      'Not done yet; not approved yet.',
    );
    expect(taskStatus({ state: 'OPEN', approval: 'Requested' })).toBe(
      'Not done yet; approval requested.',
    );
    expect(taskStatus({ state: 'WORK_IN_PROGRESS', approval: 'Approved' })).toBe(
      'Being carried out; approved.',
    );
    expect(taskStatus({ state: 'OPEN', approval: 'Escalated' })).toBe(
      'Not done yet; approval Escalated.',
    );
    expect(taskStatus({ state: 'CLOSED', approval: 'Approved' })).toBe('Done.');
    expect(taskStatus({ state: 'CANCELED', approval: 'Not Approved' })).toBe(
      'Canceled; no longer part of the change.',
    );
  });
});
