import { changeTask, taskText } from '../testing/change-fixtures';
import {
  MAX_TASKS,
  addTask,
  applyTaskProblems,
  canRemove,
  removeTask,
  tasksForm,
  toEditedTasks,
  toTaskTexts,
} from './change-tasks-model';

describe('change tasks model', () => {
  it('reads the texts of the tasks back trimmed, with their numbers when edited', () => {
    const tasks = tasksForm([changeTask(), taskText('Validate it', 'Run the smoke tests.')]);
    tasks.at(1).controls.shortDescription.setValue(' Validate it again ');

    expect(toTaskTexts(tasks)).toEqual([
      taskText('Deploy CertScanner to production', 'Deploy the release of CertScanner.'),
      taskText('Validate it again', 'Run the smoke tests.'),
    ]);
    expect(toEditedTasks(tasks)).toEqual([
      {
        number: 'CTASK0020001',
        ...taskText('Deploy CertScanner to production', 'Deploy the release of CertScanner.'),
      },
      { number: null, ...taskText('Validate it again', 'Run the smoke tests.') },
    ]);
    expect(tasks.at(1).controls.state.value).toBe('OPEN');
  });

  it('asks for the texts of every task and at least one task', () => {
    const tasks = tasksForm([taskText(' ', '')]);
    expect(tasks.at(0).controls.shortDescription.hasError('required')).toBe(true);
    expect(tasks.at(0).controls.description.hasError('required')).toBe(true);
    tasks.at(0).controls.shortDescription.setValue('é'.repeat(81));
    expect(tasks.at(0).controls.shortDescription.hasError('columnLength')).toBe(true);

    expect(tasksForm([]).errors).toEqual({ rule: 'Add at least one change task' });
  });

  it('keeps a closed task as it is and the last task in the list', () => {
    const tasks = tasksForm([changeTask({ state: 'CLOSED' }), changeTask({ number: null })]);

    expect(tasks.at(0).disabled).toBe(true);
    expect(canRemove(tasks, 0)).toBe(false);
    expect(canRemove(tasks, 1)).toBe(true);
    removeTask(tasks, 0);
    expect(tasks.length).toBe(2);
    removeTask(tasks, 1);
    expect(tasks.length).toBe(1);
    expect(tasks.dirty).toBe(true);
    expect(toEditedTasks(tasks)).toEqual([
      {
        number: 'CTASK0020001',
        ...taskText('Deploy CertScanner to production', 'Deploy the release of CertScanner.'),
      },
    ]);

    const single = tasksForm([taskText('Deploy it')]);
    removeTask(single, 0);
    expect(single.length).toBe(1);
  });

  it('adds empty tasks up to the limit', () => {
    const tasks = tasksForm([taskText('Deploy it')]);
    addTask(tasks);
    expect(tasks.length).toBe(2);
    expect(tasks.at(1).invalid).toBe(true);
    for (let i = tasks.length; i < MAX_TASKS + 3; i++) {
      addTask(tasks);
    }
    expect(tasks.length).toBe(MAX_TASKS);
  });

  it('marks the problems of the server on their task', () => {
    const tasks = tasksForm([taskText('Deploy it'), taskText('Validate it')]);

    expect(
      applyTaskProblems(tasks, [
        { field: 'tasks[1].shortDescription', message: 'is used twice' },
        { field: 'tasks', message: 'CTASK0020009 is closed in ProTech and cannot be removed' },
        { field: 'schedule', message: 'must not be null' },
      ]),
    ).toEqual([
      { field: 'schedule', message: 'must not be null' },
      { field: 'tasks', message: 'CTASK0020009 is closed in ProTech and cannot be removed' },
    ]);
    expect(tasks.at(1).controls.shortDescription.errors).toEqual({ server: 'is used twice' });
  });
});
